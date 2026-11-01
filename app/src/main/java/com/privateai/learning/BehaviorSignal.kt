package com.privateai.learning

import com.privateai.profile.PreferenceCategory
import kotlinx.serialization.Serializable

/**
 * Represents a single observed behavioral signal from user interaction.
 *
 * This is NOT a memory (what the assistant knows about the user).
 * This is a behavioral observation (how the user prefers the assistant to behave).
 *
 * Signals are accumulated by BehaviorAccumulator and, once sufficient evidence
 * exists, promoted to inferred preferences via UserProfileManager.
 */
@Serializable
data class BehaviorSignal(
    /** Which preference category this signal relates to. */
    val category: PreferenceCategory,

    /** Human-readable description of the detected behavior. */
    val description: String,

    /** What triggered this signal (e.g., keyword pattern, correction). */
    val source: SignalSource,

    /** Optional topic context (e.g., "DSA", "DevOps", "general"). */
    val topic: String = "general",

    /** Timestamp of observation. */
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Source/strength classification for a behavioral signal.
 */
@Serializable
enum class SignalSource {
    /** Detected from user message keyword patterns. Weakest signal. */
    KEYWORD_PATTERN,

    /** User explicitly corrected the assistant's behavior. Strong signal. */
    EXPLICIT_CORRECTION,

    /** User explicitly stated a preference. Strongest signal. */
    EXPLICIT_STATEMENT
}

/**
 * Accumulated evidence for a specific behavioral preference.
 *
 * Tracks how many times a compatible signal has been observed and
 * manages confidence based on evidence count and contradictions.
 */
@Serializable
data class AccumulatedEvidence(
    /** Which preference category. */
    val category: PreferenceCategory,

    /** Canonical description of the preference. */
    val description: String,

    /** Optional topic context. */
    val topic: String = "general",

    /** Number of compatible signals observed. */
    val evidenceCount: Int = 1,

    /** Current confidence based on evidence rules. */
    val confidence: Float = 0.15f,

    /** Number of contradictory signals observed. */
    val contradictionCount: Int = 0,

    /** Whether this evidence has been promoted to an inferred preference. */
    val promoted: Boolean = false,

    /** First observation timestamp. */
    val firstSeen: Long = System.currentTimeMillis(),

    /** Most recent observation timestamp. */
    val lastSeen: Long = System.currentTimeMillis()
)
