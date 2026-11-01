package com.privateai.intelligence

/**
 * Pedagogical representation types used by VIVVA to explain concepts.
 */
enum class RepresentationType {
    DIRECT,
    ANALOGY,
    EXAMPLE,
    MICRO_STEP,
    NUMERICAL_TRACE,
    VISUAL_TRACE,
    CODE_FIRST,
    FORMAL,
    INVARIANT,
    TRADEOFF,
    REAL_WORLD_SCENARIO
}

/**
 * Captures a single explanation attempt during active conversation for real-time pedagogical adaptation.
 *
 * TEMPORARY CONVERSATIONAL STATE:
 * - Kept in active conversation scope, not dumped into permanent long-term memory.
 * - Used to prevent representation repetition and escalate pedagogical clarity.
 */
data class ExplanationAttempt(
    val concept: String,
    val domain: Domain,
    val level: ExplanationLevel,
    val representation: RepresentationType,
    val turnIndex: Int = 0,
    val failed: Boolean = false,
    val specificMisconception: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Result of adaptive reasoning over state, task, domain, profile, and previous attempts.
 */
data class AdaptiveDecision(
    val level: ExplanationLevel,
    val representation: RepresentationType,
    val failedRepresentationToAvoid: RepresentationType? = null,
    val targetedConcept: String? = null,
    val specificMisconception: String? = null,
    val adaptationReason: String? = null,
    val nonRepetitionDirective: String? = null
)
