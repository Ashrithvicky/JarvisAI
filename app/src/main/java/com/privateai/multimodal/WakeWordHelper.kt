package com.privateai.multimodal

import android.content.Context
import android.util.Log

/**
 * Foreground Wake-Word Detector for "Hey Jarvis".
 * Detects the activation phrase when audio transcription is received in the active app.
 */
class WakeWordHelper(
    private val onWakeWordDetected: () -> Unit
) {
    companion object {
        private const val TAG = "WakeWordHelper"
        private val WAKE_PHRASES = listOf(
            "hey jarvis",
            "hi jarvis",
            "hello jarvis",
            "ok jarvis",
            "jarvis"
        )

        private val WAKE_REGEX = Regex("^\\W*((hey|ok|okay|hello|hi)\\s+)?([\\p{L}]+)\\b[\\s,.!?]*", RegexOption.IGNORE_CASE)
        private val TARGETS = listOf("jarvis")
        private val ALIASES = setOf("jervis", "jarvas", "jarves", "charvis", "harvis", "garvis", "travis", "jarvice", "service", "jarbis")

        private fun distance(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1).also { it[0] = i }
                for (j in 1..b.length) {
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                }
                prev = cur
            }
            return prev[b.length]
        }

        private fun sounds(word: String, greeted: Boolean): Boolean {
            val w = word.lowercase()
            if (w in TARGETS) return true
            if (w.length < 3) return false
            if (w in ALIASES) return greeted
            return TARGETS.any { distance(w, it) <= 1 }
        }

        /** Returns the text after a leading wake phrase ("" if only the phrase was said), or null if absent. */
        fun commandAfterWakePhrase(text: String): String? {
            val trimmed = text.trim()
            val m = WAKE_REGEX.find(trimmed) ?: return null
            val greeted = m.groupValues[2].isNotEmpty()
            if (!sounds(m.groupValues[3], greeted)) return null
            return trimmed.substring(m.range.last + 1).trim()
        }
    }

    /**
     * Checks if transcribed speech matches the wake phrase.
     */
    fun processTranscribedText(text: String): Boolean {
        val normalized = text.lowercase().trim()
        val matched = WAKE_PHRASES.any { phrase -> normalized.contains(phrase) }
        if (matched) {
            Log.i(TAG, "Foreground wake word detected: '$text'")
            onWakeWordDetected()
            return true
        }
        return false
    }
}
