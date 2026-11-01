package com.privateai.multimodal

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.IOException
import java.io.OutputStream

/**
 * Captures the microphone inside our own foreground service and pipes it to the speech recognizer.
 * The system recognizer records on its own behalf and is muted ("silenced") whenever our app is not
 * on screen; audio recorded here keeps flowing because our foreground service owns the capture.
 * The mic is held only between [start] and [stop].
 */
class PcmMicSource {
    companion object {
        private const val TAG = "PcmMicSource"
        const val SAMPLE_RATE = 16_000
        private const val CHUNK_BYTES = 640 // 20 ms of 16-bit mono
        private const val PREROLL_CHUNKS = 50 // 1 s, fits the 64 KB pipe so replaying it never blocks
    }

    private val lock = Any()
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private var sink: OutputStream? = null
    private val preroll = ArrayDeque<ByteArray>()

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        synchronized(lock) {
            if (running) return true
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) return false
            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer, CHUNK_BYTES * 8)
                )
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord creation failed", e)
                return false
            }
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                return false
            }
            try {
                rec.startRecording()
            } catch (e: Exception) {
                Log.w(TAG, "startRecording failed", e)
                rec.release()
                return false
            }
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                rec.release()
                return false
            }
            record = rec
            running = true
            preroll.clear()
            thread = Thread({ pump(rec) }, "jarvis-pcm").also {
                it.isDaemon = true
                it.start()
            }
            Log.i(TAG, "Microphone capture started")
            return true
        }
    }

    private fun pump(rec: AudioRecord) {
        val buffer = ByteArray(CHUNK_BYTES)
        while (running) {
            val n = rec.read(buffer, 0, CHUNK_BYTES)
            if (n < 0) break
            if (n == 0) continue
            val chunk = buffer.copyOf(n)
            val out = synchronized(lock) {
                preroll.addLast(chunk)
                if (preroll.size > PREROLL_CHUNKS) preroll.removeFirst()
                sink
            }
            if (out != null) {
                try {
                    out.write(chunk)
                } catch (e: IOException) {
                    synchronized(lock) { if (sink === out) endSessionLocked() }
                }
            }
        }
    }

    /** Opens a pipe for one recognition session, starting with the audio captured since the last one ended. */
    fun openSession(): ParcelFileDescriptor? = synchronized(lock) {
        if (!running) return null
        try {
            sink?.close()
        } catch (_: IOException) {
        }
        sink = null
        val pipe = ParcelFileDescriptor.createPipe()
        val out = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        try {
            preroll.forEach { out.write(it) }
        } catch (e: IOException) {
            pipe[0].close()
            out.close()
            return null
        }
        sink = out
        pipe[0]
    }

    /** Ends the current session's pipe; audio captured from now on is kept for the next session. */
    fun endSession() {
        synchronized(lock) { endSessionLocked() }
    }

    private fun endSessionLocked() {
        try {
            sink?.close()
        } catch (_: IOException) {
        }
        sink = null
        preroll.clear()
    }

    fun stop() {
        val rec: AudioRecord?
        val worker: Thread?
        synchronized(lock) {
            if (!running) return
            running = false
            endSessionLocked()
            rec = record
            worker = thread
            record = null
            thread = null
        }
        try {
            rec?.stop()
        } catch (_: IllegalStateException) {
        }
        try {
            worker?.join(500)
        } catch (_: InterruptedException) {
        }
        rec?.release()
        Log.i(TAG, "Microphone capture stopped")
    }
}
