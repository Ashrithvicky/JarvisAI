package com.privateai.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.BatteryManager
import com.privateai.monitor.BatteryAlerts
import com.privateai.monitor.SecurityAudit
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.privateai.PrivateAIApplication
import com.privateai.control.PhoneAction
import com.privateai.control.PhoneController
import com.privateai.monitor.VivvaAccessibilityService
import com.privateai.multimodal.SpeechInputHelper
import com.privateai.multimodal.TextToSpeechHelper
import com.privateai.multimodal.WakeWordHelper
import com.privateai.ui.MainActivity
import java.util.Calendar

/**
 * Hands-free mode: listens for "Jarvis", greets, captures one command and hands it to the chat UI.
 * Also speaks occasional goal-based nudges. Uses the device recognizer (offline preferred, no INTERNET permission).
 */
class VivvaVoiceService : Service() {

    companion object {
        private const val TAG = "VivvaVoiceService"
        private const val CHANNEL_ID = "jarvis_voice_quiet"
        private const val NOTIFICATION_ID = 4201
        private const val GREETING = "Yes boss?"
        private const val RESTART_DELAY_MS = 100L
        private const val SESSION_MS = 15_000L
        private const val WAKE_GAP_MS = 1_200L
        private const val COMMAND_GAP_MS = 3_500L
        private const val WAKE_CONFIRM_MS = 700L
        private const val MIC_BUSY_RETRY_MS = 3_000L
        private const val DAY_MS = 24 * 60 * 60_000L
        private const val AUDIT_CHECK_MS = 60 * 60_000L
        private const val AUDIT_FIRST_DELAY_MS = 60_000L

        @Volatile
        var isRunning = false
            private set

        /** Set while in-app dictation owns the recognizer; the wake-word listener must stay off. */
        @Volatile
        var appMicInUse = false
            private set

        @Volatile
        private var instance: VivvaVoiceService? = null

        fun setAppMicInUse(inUse: Boolean) {
            appMicInUse = inUse
            instance?.let { svc ->
                svc.handler.post { if (inUse) svc.stt?.stopListening() else svc.scheduleListen(MIC_BUSY_RETRY_MS) }
            }
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, VivvaVoiceService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VivvaVoiceService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeechHelper? = null
    private var stt: SpeechInputHelper? = null
    private var sessionUntil = 0L
    private val sessionActive get() = System.currentTimeMillis() < sessionUntil

    private fun extendSession() {
        sessionUntil = System.currentTimeMillis() + SESSION_MS
    }

    private val listenRunnable = Runnable {
        if (!isRunning) return@Runnable
        if (micBusy()) {
            Log.d(TAG, "Mic busy (appMic=$appMicInUse), retrying")
            stt?.stopListening()
            scheduleListen(MIC_BUSY_RETRY_MS)
        } else if (tts?.isSpeaking == true) {
            scheduleListen()
        } else {
            Log.d(TAG, "Wake listener starting (isListening=${stt?.isListening})")
            stt?.startListening()
        }
    }

    // Calls and VoIP change the audio mode; other apps recording use a non-voice-recognition source.
    private fun micBusy(): Boolean {
        if (appMicInUse) return true
        val am = getSystemService(AudioManager::class.java)
        if (am.mode != AudioManager.MODE_NORMAL) return true
        // Hotword/echo/radio sources (1997-1999) belong to the system and do not block the recognizer.
        return am.activeRecordingConfigurations.any {
            it.clientAudioSource != MediaRecorder.AudioSource.VOICE_RECOGNITION && it.clientAudioSource !in 1997..1999
        }
    }

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            if (micBusy()) stt?.stopListening()
        }
    }

    private var lastBattery = -1
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0) return
            val percent = level * 100 / scale
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            BatteryAlerts.check(lastBattery, percent, charging)?.let { if (!micBusy()) speak(it) }
            lastBattery = percent
        }
    }

    private val auditRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            runAuditIfDue()
            handler.postDelayed(this, AUDIT_CHECK_MS)
        }
    }

    private fun runAuditIfDue() {
        val prefs = getSharedPreferences("jarvis_audit", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (now - prefs.getLong("last", 0L) < DAY_MS || sessionActive || ProactiveNudges.isQuietHour(hour)) return
        Thread {
            val summary = SecurityAudit.spokenSummary(SecurityAudit.findings(SecurityAudit.collect(this)))
            prefs.edit().putLong("last", now).apply()
            summary?.let { handler.post { if (isRunning && !micBusy()) speak(it) } }
        }.start()
    }

    private val nudgeRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (!ProactiveNudges.isQuietHour(hour) && !sessionActive && tts?.isSpeaking != true) {
                val memories = (application as PrivateAIApplication).appContainer.memoryManager.getAllMemories()
                speak(ProactiveNudges.compose(memories))
            }
            handler.postDelayed(this, ProactiveNudges.nextDelayMillis())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeechHelper(this).also { it.onSpeakFinished = { handler.post { if (sessionActive) extendSession(); scheduleListen() } } }
        stt = SpeechInputHelper(
            context = this,
            onResult = ::handleSpeech,
            onError = { message ->
                if (message.contains("permission", ignoreCase = true)) {
                    Log.w(TAG, "Microphone permission missing; stopping")
                    stopSelf()
                } else {
                    scheduleListen()
                }
            },
            onListeningStateChanged = {},
            onPartial = ::handlePartial,
            silenceMs = { if (sessionActive) COMMAND_GAP_MS else WAKE_GAP_MS },
            pcmSource = com.privateai.multimodal.PcmMicSource()
        )
        getSystemService(AudioManager::class.java).registerAudioRecordingCallback(recordingCallback, handler)
        ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private val wakeRunnable = Runnable {
        if (!isRunning || sessionActive) return@Runnable
        extendSession()
        speak(GREETING)
    }

    // Wake on the partial transcript so the greeting does not wait for the recognizer's end-of-speech.
    private fun handlePartial(text: String) {
        if (sessionActive) {
            extendSession()
            return
        }
        val command = WakeWordHelper.commandAfterWakePhrase(text)
        handler.removeCallbacks(wakeRunnable)
        if (command != null && command.isBlank()) handler.postDelayed(wakeRunnable, WAKE_CONFIRM_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        )
        // A null intent means Android restarted us after a crash or kill; listening starts only on the user's toggle.
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isRunning) {
            isRunning = true
            instance = this
            scheduleListen()
            handler.postDelayed(nudgeRunnable, ProactiveNudges.nextDelayMillis())
            handler.postDelayed(auditRunnable, AUDIT_FIRST_DELAY_MS)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        instance = null
        getSystemService(AudioManager::class.java).unregisterAudioRecordingCallback(recordingCallback)
        unregisterReceiver(batteryReceiver)
        stt?.stopListening()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun scheduleListen(delayMs: Long = RESTART_DELAY_MS) {
        handler.removeCallbacks(listenRunnable)
        handler.postDelayed(listenRunnable, delayMs)
    }

    private fun speak(text: String) {
        stt?.stopListening()
        handler.removeCallbacks(listenRunnable)
        if (tts?.speak(text) != true) scheduleListen()
    }

    private fun handleSpeech(text: String) {
        Log.d(TAG, "Heard: '$text' (sessionActive=$sessionActive)")
        handler.removeCallbacks(wakeRunnable)
        if (micBusy()) {
            scheduleListen(MIC_BUSY_RETRY_MS)
            return
        }
        val wakeCommand = WakeWordHelper.commandAfterWakePhrase(text)
        val command = when {
            sessionActive -> (wakeCommand ?: text).trim()
            wakeCommand == null -> {
                scheduleListen()
                return
            }
            else -> wakeCommand
        }
        extendSession()
        if (command.isBlank()) {
            speak(GREETING)
        } else {
            submit(command)
            scheduleListen()
        }
    }

    private fun submit(command: String) {
        PhoneAction.parse(command)?.let { action ->
            val service = VivvaAccessibilityService.instance
            val reply = when (action) {
                PhoneAction.GoBack, PhoneAction.GoHome, PhoneAction.ShowRecents, is PhoneAction.OpenApp -> {
                    if (service == null) {
                        speak("I don't have access to do that, boss. Turn on the Jarvis accessibility service in Settings.")
                        return
                    }
                    if (service.perform(action)) "" else null
                }
                else -> PhoneController.perform(service ?: this, action)
            }
            when {
                reply == null -> speak("Sorry boss, I couldn't do that.")
                reply.isNotEmpty() -> speak(reply)
            }
            return
        }
        if (isAppInForeground()) {
            VoiceCommandBus.submit(command)
        } else {
            speak("I don't have access to answer that while the app is closed, boss.")
        }
    }

    private fun isAppInForeground(): Boolean {
        val info = android.app.ActivityManager.RunningAppProcessInfo()
        android.app.ActivityManager.getMyMemoryState(info)
        return info.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).deleteNotificationChannel("vivva_voice")
            val channel = NotificationChannel(CHANNEL_ID, "Jarvis hands-free", NotificationManager.IMPORTANCE_MIN)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis is listening for \"Jarvis\"")
            .setContentText("Hands-free mode is on. Audio is processed on this device.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .build()
    }
}
