package com.privateai.multimodal

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Continuous speech-to-text dictation. Listening stays on across pauses until [stopListening]
 * is called (toggle on / toggle off). Partial text is streamed live via [onPartial].
 */
class SpeechInputHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onPartial: (String) -> Unit = {},
    private val silenceMs: () -> Long = { 6000L },
    private val pcmSource: PcmMicSource? = null
) {
    companion object {
        private const val TAG = "SpeechInputHelper"
        private const val RESTART_DELAY_MS = 250L
        private const val BACKOFF_DELAY_MS = 1_000L
        private const val SESSION_SOFT_LIMIT_MS = 6_000L
        private const val SESSION_HARD_LIMIT_MS = 3_000L
        private const val MAX_SILENT_RESTARTS = 8
    }

    private val handler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var active = false
    private var silentRestarts = 0
    private var onDeviceUsable = true
    private var pcmReady = false
    private var sessionPfd: ParcelFileDescriptor? = null
    private var lastPartial: String? = null

    val isListening: Boolean get() = active

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (active) return
        if (!isAvailable()) {
            onError("Speech recognition is not available on this device.")
            return
        }
        active = true
        silentRestarts = 0
        pcmReady = pcmSource != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && pcmSource.start()
        onListeningStateChanged(true)
        beginSession()
    }

    fun stopListening() {
        val wasActive = active
        active = false
        handler.removeCallbacksAndMessages(null)
        releaseRecognizer()
        closeSessionPfd()
        pcmSource?.stop()
        pcmReady = false
        if (wasActive) onListeningStateChanged(false)
    }

    private fun releaseRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing speech recognizer", e)
        }
        speechRecognizer = null
    }

    private fun scheduleRestart(delayMs: Long = RESTART_DELAY_MS) {
        if (!active) return
        handler.postDelayed({ if (active) beginSession() }, delayMs)
    }

    private fun failAndStop(message: String) {
        stopListening()
        onError(message)
    }

    private fun beginSession() {
        // Destroying and recreating the recognizer on every restart makes the service drop with error 11.
        speechRecognizer?.let { existing ->
            try {
                startOn(existing)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Reuse failed, recreating recognizer", e)
                releaseRecognizer()
            }
        }
        try {
            val useOnDevice = onDeviceUsable &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            val recognizer = if (useOnDevice) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            speechRecognizer = recognizer.apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Speech began (ownAudio=${sessionPfd != null})")
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        Log.d(TAG, "Speech ended")
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}

                    override fun onPartialResults(partialResults: Bundle?) {
                        val text = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        Log.d(TAG, "Partial: '$text'")
                        if (sessionPfd != null && !text.isNullOrBlank()) {
                            lastPartial = text
                            handler.removeCallbacks(settlePartial)
                            handler.postDelayed(settlePartial, silenceMs().coerceIn(1_200L, 3_000L))
                        }
                        if (active && !text.isNullOrBlank()) onPartial(text)
                    }

                    override fun onResults(results: Bundle?) {
                        cancelWatchdog()
                        pcmSource?.endSession()
                        closeSessionPfd()
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        Log.d(TAG, "Final: '$text'")
                        if (!text.isNullOrBlank()) {
                            silentRestarts = 0
                            onResult(text)
                        }
                        scheduleRestart()
                    }

                    override fun onError(error: Int) {
                        cancelWatchdog()
                        val wasOwnAudio = sessionPfd != null
                        // A client error on our own audio means the recognizer rejected it; use its own mic.
                        if (error == SpeechRecognizer.ERROR_CLIENT && sessionPfd != null) {
                            pcmReady = false
                            pcmSource?.stop()
                        }
                        pcmSource?.endSession()
                        closeSessionPfd()
                        if (!active) return
                        Log.w(TAG, "Recognizer error $error (silentRestarts=$silentRestarts)")
                        // Busy/disconnected/client errors need a pause or the restarts keep colliding.
                        val needsBackoff = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                            error == SpeechRecognizer.ERROR_CLIENT ||
                            error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                            error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS
                        if (needsBackoff) {
                            onDeviceUsable = false
                            releaseRecognizer()
                        }
                        // Offline language pack missing: fall back to the default recognizer.
                        if (useOnDevice && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)) {
                            onDeviceUsable = false
                            releaseRecognizer()
                            scheduleRestart()
                            return
                        }
                        when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                failAndStop("Microphone permission required")
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                // Pause in speech: keep the mic on, as long as it is not looping forever.
                                if (!wasOwnAudio && ++silentRestarts > MAX_SILENT_RESTARTS) {
                                    failAndStop("Stopped listening: no speech heard")
                                } else {
                                    scheduleRestart()
                                }
                            }
                            else -> {
                                if (++silentRestarts > MAX_SILENT_RESTARTS) {
                                    failAndStop("Speech recognition error ($error)")
                                } else {
                                    scheduleRestart(if (needsBackoff) BACKOFF_DELAY_MS else RESTART_DELAY_MS)
                                }
                            }
                        }
                    }
                })
            }

            speechRecognizer?.let { startOn(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            failAndStop("Error starting speech recognition: ${e.message}")
        }
    }

    private fun startOn(recognizer: SpeechRecognizer) {
        // The recognizer parcels the intent asynchronously, so the descriptor must outlive this call.
        closeSessionPfd()
        val pfd = if (pcmReady) pcmSource?.openSession() else null
        sessionPfd = pfd
        try {
            recognizer.startListening(buildIntent(pfd))
            // Fed audio may never trigger the recognizer's own end-of-speech, so force a result.
            if (pfd != null) handler.postDelayed(softStop, SESSION_SOFT_LIMIT_MS)
        } catch (e: Exception) {
            closeSessionPfd()
            throw e
        }
    }

    private val softStop = Runnable {
        if (!active) return@Runnable
        Log.w(TAG, "Session still open after ${SESSION_SOFT_LIMIT_MS}ms, asking recognizer to finish")
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "stopListening failed", e)
        }
        handler.postDelayed(hardReset, SESSION_HARD_LIMIT_MS)
    }

    private val hardReset = Runnable {
        if (!active) return@Runnable
        Log.w(TAG, "Recognizer unresponsive, recreating")
        pcmSource?.endSession()
        closeSessionPfd()
        releaseRecognizer()
        scheduleRestart()
    }

    private fun cancelWatchdog() {
        handler.removeCallbacks(softStop)
        handler.removeCallbacks(hardReset)
        handler.removeCallbacks(settlePartial)
        lastPartial = null
    }

    // Fed audio gets no endpointing from the recognizer, so a pause after the last partial ends the phrase.
    private val settlePartial = Runnable {
        val text = lastPartial
        if (!active || text.isNullOrBlank()) return@Runnable
        cancelWatchdog()
        Log.d(TAG, "Settled: '$text'")
        pcmSource?.endSession()
        closeSessionPfd()
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "cancel failed", e)
        }
        silentRestarts = 0
        onResult(text)
        scheduleRestart()
    }

    private fun closeSessionPfd() {
        try {
            sessionPfd?.close()
        } catch (_: Exception) {
        }
        sessionPfd = null
    }

    private fun buildIntent(pfd: ParcelFileDescriptor? = null) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        if (pfd != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, PcmMicSource.SAMPLE_RATE)
        }
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs())
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs())
    }
}
