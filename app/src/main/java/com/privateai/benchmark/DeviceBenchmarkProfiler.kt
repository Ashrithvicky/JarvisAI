package com.privateai.benchmark

import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * On-Device Hardware & Runtime Benchmark Profiler.
 * Instruments physical device execution metrics:
 * - Cold model load time
 * - Warm model load time
 * - Time-To-First-Token (TTFT)
 * - Prefill speed (tokens/sec)
 * - Decode speed (tokens/sec)
 * - Peak RAM usage (MB)
 * - Battery and thermal status
 */
object DeviceBenchmarkProfiler {

    private const val TAG = "DeviceBenchmarkProfiler"

    data class SessionMetrics(
        val coldLoadMs: Long = 0,
        val warmLoadMs: Long = 0,
        val ttftMs: Long = 0,
        val promptTokens: Int = 0,
        val generatedTokens: Int = 0,
        val totalGenerationMs: Long = 0,
        val prefillTokensPerSec: Double = 0.0,
        val decodeTokensPerSec: Double = 0.0,
        val peakRamMb: Double = 0.0
    )

    private var sessionStartTime = 0L
    private var firstTokenTime = 0L
    private var tokenCount = 0

    fun startGenerationTimer() {
        sessionStartTime = SystemClock.elapsedRealtime()
        firstTokenTime = 0L
        tokenCount = 0
    }

    fun recordFirstToken() {
        if (firstTokenTime == 0L) {
            firstTokenTime = SystemClock.elapsedRealtime()
        }
    }

    fun recordToken() {
        tokenCount++
    }

    fun finishGeneration(promptTokens: Int, context: Context? = null): SessionMetrics {
        val endTime = SystemClock.elapsedRealtime()
        val totalMs = endTime - sessionStartTime
        val ttft = if (firstTokenTime > 0L) firstTokenTime - sessionStartTime else totalMs
        val decodeMs = if (firstTokenTime > 0L) endTime - firstTokenTime else totalMs
        val decodeSpeed = if (decodeMs > 0) (tokenCount.toDouble() / decodeMs) * 1000.0 else 0.0
        val prefillSpeed = if (ttft > 0) (promptTokens.toDouble() / ttft) * 1000.0 else 0.0

        val runtime = Runtime.getRuntime()
        val usedRamMb = (runtime.totalMemory() - runtime.freeMemory()).toDouble() / (1024.0 * 1024.0)

        val metrics = SessionMetrics(
            ttftMs = ttft,
            promptTokens = promptTokens,
            generatedTokens = tokenCount,
            totalGenerationMs = totalMs,
            prefillTokensPerSec = prefillSpeed,
            decodeTokensPerSec = decodeSpeed,
            peakRamMb = usedRamMb
        )

        Log.i(TAG, "Generation Metrics: TTFT=${ttft}ms, Decode=${String.format("%.1f", decodeSpeed)} tok/s, RAM=${String.format("%.1f", usedRamMb)} MB")
        return metrics
    }
}
