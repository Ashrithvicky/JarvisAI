package com.privateai.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps this app's own logcat output, uncaught crashes and the reason the previous process died in
 * files under the app's storage (Android/data/<package>/files/logs). Logs never leave the device.
 */
object AppLogger {
    private const val MAX_BYTES = 2L * 1024 * 1024
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()
    private var file: File? = null
    private var writer: FileWriter? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        if (file != null) return
        appContext = context.applicationContext
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs").apply { mkdirs() }
        file = File(dir, "jarvis.log")
        write("=== Process ${Process.myPid()} started (Android ${Build.VERSION.RELEASE}, ${Build.MODEL}) ===")
        installCrashHandler()
        Thread({ logPreviousExits(context) }, "jarvis-exitinfo").start()
        Thread({ pumpLogcat() }, "jarvis-logcat").apply { isDaemon = true }.start()
    }

    fun write(line: String) {
        synchronized(lock) {
            val target = file ?: return
            try {
                if (target.length() > MAX_BYTES) {
                    writer?.close()
                    writer = null
                    val old = File(target.parentFile, "jarvis.1.log")
                    old.delete()
                    target.renameTo(old)
                }
                val w = writer ?: FileWriter(target, true).also { writer = it }
                w.write("${stamp.format(Date())} $line\n")
                w.flush()
            } catch (_: Exception) {
                writer = null
            }
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
            write("CRASH in thread ${thread.name}: $trace")
            previous?.uncaughtException(thread, error)
        }
    }

    // Android kills a process silently (low memory, native crash); the system records why.
    private fun logPreviousExits(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val am = context.getSystemService(ActivityManager::class.java)
            am.getHistoricalProcessExitReasons(context.packageName, 0, 5).forEach {
                write(
                    "PREVIOUS EXIT: pid=${it.pid} at=${stamp.format(Date(it.timestamp))} reason=${it.reason} " +
                        "status=${it.status} importance=${it.importance} rssKb=${it.rss} desc=${it.description}"
                )
            }
        } catch (e: Exception) {
            write("Could not read exit reasons: ${e.message}")
        }
    }

    private fun pumpLogcat() {
        try {
            val proc = ProcessBuilder("logcat", "-v", "threadtime", "--pid=${Process.myPid()}")
                .redirectErrorStream(true)
                .start()
            proc.inputStream.bufferedReader().useLines { lines ->
                lines.filterNot { it.contains("ImeTracker") || it.contains("Choreographer") }
                    .forEach { write(it) }
            }
        } catch (e: Exception) {
            write("Logcat capture unavailable: ${e.message}")
        }
    }

    fun memorySnapshot(): String {
        val context = appContext ?: return "mem n/a"
        val rt = Runtime.getRuntime()
        val info = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val pssMb = android.os.Debug.getPss() / 1024
        return "mem pss=${pssMb}MB heap=${(rt.totalMemory() - rt.freeMemory()) shr 20}MB " +
            "systemAvail=${info.availMem shr 20}MB lowMemory=${info.lowMemory}"
    }
}
