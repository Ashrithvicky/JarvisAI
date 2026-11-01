package com.privateai.profile

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Categories of assistant behavior and presentation preferences.
 */
@Serializable
enum class PreferenceCategory {
    EXPLANATION_DEPTH,
    CODING_STYLE,
    PROBLEM_SOLVING,
    DETAIL_LEVEL,
    TEACHING_APPROACH,
    LEARNING_STYLE,
    COMMUNICATION_TONE
}

/**
 * Represents a learned or explicitly stated behavioral preference.
 */
@Serializable
data class BehaviorPreference(
    val id: String = UUID.randomUUID().toString(),
    val category: PreferenceCategory,
    val description: String,
    val confidence: Float = 1.0f,
    val isExplicit: Boolean = true,
    /** Topic scope: "general", "DSA", "DevOps", etc. Defaults to "general" for backward compat. */
    val topic: String = "general",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Represents a specific correction the user provided to guide future responses.
 */
@Serializable
data class UserCorrection(
    val id: String = UUID.randomUUID().toString(),
    val topic: String,
    val correction: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Tracks a concept the user repeatedly struggled with and the explanation styles attempted.
 */
@Serializable
data class StruggledConcept(
    val concept: String,
    val domain: String,
    val failedAttempts: Int = 1,
    val lastExplanationLevel: String = "LEVEL_2_ANALOGY",
    val successfulLevel: String? = null,
    val confidence: Float = 0.7f,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Complete User Behavior & Preference Profile.
 *
 * Distinct from conversational memory:
 * - Conversational Memory = factual knowledge/context about the user or tasks.
 * - User Profile = how the assistant should formulate explanations, code, and reasoning.
 */
@Serializable
data class UserProfile(
    val explicitPreferences: List<BehaviorPreference> = emptyList(),
    val inferredPreferences: List<BehaviorPreference> = emptyList(),
    val userCorrections: List<UserCorrection> = emptyList(),
    val learningDimensions: Map<String, Float> = emptyMap(),
    val struggledConcepts: List<StruggledConcept> = emptyList()
)
