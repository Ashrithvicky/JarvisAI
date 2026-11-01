package com.privateai.multimodal

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Records 16 kHz mono PCM16 audio (what the model's audio encoder expects) and returns it as WAV bytes. */
class WavRecorder {

    companion object {
        private const val TAG = "WavRecorder"
        const val SAMPLE_RATE = 16000
        const val MAX_SECONDS = 30

        fun toWav(pcm: ByteArray): ByteArray {
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
                putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(pcm.size)
            }
            return header.array() + pcm
        }
    }

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val pcm = ByteArrayOutputStream()
    @Volatile private var running = false

    val isRecording: Boolean get() = running

    /** Starts capturing; [onLimitReached] is called on the main thread if the time cap ends the clip. */
    @SuppressLint("MissingPermission")
    fun start(onLimitReached: () -> Unit): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord init failed", e)
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        pcm.reset()
        record = rec
        running = true
        rec.startRecording()
        thread = Thread {
            val buf = ByteArray(minBuf)
            val maxBytes = SAMPLE_RATE * 2 * MAX_SECONDS
            while (running && pcm.size() < maxBytes) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) pcm.write(buf, 0, n)
            }
            if (running) Handler(Looper.getMainLooper()).post(onLimitReached)
        }.also { it.start() }
        return true
    }

    /** Stops capturing and returns WAV bytes, or null if nothing was recorded. */
    fun stop(): ByteArray? {
        if (record == null) return null
        running = false
        try { thread?.join(1000) } catch (_: InterruptedException) {}
        try {
            record?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stop failed", e)
        }
        record?.release()
        record = null
        thread = null
        val data = pcm.toByteArray()
        return if (data.size < SAMPLE_RATE / 2) null else toWav(data)
    }
}
