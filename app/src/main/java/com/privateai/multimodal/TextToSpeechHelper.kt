package com.privateai.multimodal

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * On-Device Text-To-Speech (TTS) manager for Jarvis assistant voice output.
 * Fully offline, using Android system TTS engine.
 */
class TextToSpeechHelper(
    private val context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TextToSpeechHelper"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    var isSpeaking = false
        private set

    /** Invoked when an utterance finishes or fails. */
    var onSpeakFinished: (() -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate TextToSpeech", e)
            onInitComplete?.invoke(false)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isInitialized = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    onSpeakFinished?.invoke()
                }

                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    onSpeakFinished?.invoke()
                }
            })
            Log.i(TAG, "TTS initialized successfully (isInitialized=$isInitialized)")
            onInitComplete?.invoke(isInitialized)
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
            isInitialized = false
            onInitComplete?.invoke(false)
        }
    }

    /**
     * Speaks the given text aloud.
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH): Boolean {
        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS not initialized")
            return false
        }
        val cleanText = stripMarkdown(text)
        val utteranceId = "vivva_utt_${System.currentTimeMillis()}"
        val res = tts?.speak(cleanText, queueMode, null, utteranceId)
        return res == TextToSpeech.SUCCESS
    }

    /**
     * Stops any ongoing speech synthesis.
     */
    fun stop() {
        if (isInitialized) {
            tts?.stop()
            isSpeaking = false
        }
    }

    /**
     * Releases TTS resources.
     */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            isSpeaking = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }

    private fun stripMarkdown(markdown: String): String {
        return markdown
            .replace(Regex("```[\\s\\S]*?```"), "Code snippet omitted for speech.")
            .replace(Regex("`[^`]*`"), "")
            .replace(Regex("[#*_~>]+"), "")
            .trim()
    }
}
