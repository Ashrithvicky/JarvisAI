package com.privateai.learning

import com.privateai.profile.PreferenceCategory
import com.privateai.profile.UserProfileManager

/**
 * Accumulates behavioral signals over time and promotes them to inferred
 * preferences once sufficient evidence exists.
 *
 * EVIDENCE RULES (deterministic):
 *
 * 1. A single KEYWORD_PATTERN signal starts with confidence 0.15.
 *    This is too low to be promoted or injected.
 *
 * 2. Each additional compatible signal increases evidence count
 *    and raises confidence:
 *      count=1 → 0.15
 *      count=2 → 0.30
 *      count=3 → 0.45 (eligible for promotion at ≥ PROMOTION_THRESHOLD)
 *
 * 3. EXPLICIT_CORRECTION signals count as 2 evidence units
 *    (one correction = significant behavioral intent).
 *
 * 4. EXPLICIT_STATEMENT signals immediately promote with high confidence (0.80).
 *
 * 5. Contradictory signals (same category, opposing description)
 *    reduce the existing evidence's confidence by CONTRADICTION_PENALTY.
 *    If contradictions exceed evidence, confidence drops below promotion threshold.
 *
 * 6. Explicit preferences (from UserProfileManager) always outrank
 *    inferred preferences — the accumulator never overwrites explicit prefs.
 *
 * PERSISTENCE:
 * Evidence is held in memory during the session and periodically flushed.
 * Promoted preferences are persisted via UserProfileManager's encrypted storage.
 */
class BehaviorAccumulator(
    private val userProfileManager: UserProfileManager
) {
    companion object {
        /** Minimum confidence to promote evidence to an inferred preference. */
        const val PROMOTION_THRESHOLD = 0.40f

        /** Confidence increment per compatible KEYWORD_PATTERN signal. */
        const val KEYWORD_CONFIDENCE_INCREMENT = 0.15f

        /** Evidence units added per EXPLICIT_CORRECTION signal. */
        const val CORRECTION_EVIDENCE_MULTIPLIER = 2

        /** Confidence assigned to EXPLICIT_STATEMENT signals. */
        const val EXPLICIT_STATEMENT_CONFIDENCE = 0.80f

        /** Confidence penalty per contradictory signal. */
        const val CONTRADICTION_PENALTY = 0.10f

        /** Maximum number of accumulated evidence entries to prevent unbounded growth. */
        const val MAX_EVIDENCE_ENTRIES = 50
    }

    /**
     * In-memory evidence store. Keyed by canonical description for deduplication.
     * Thread-safe via @Synchronized on mutating methods.
     */
    private val evidenceStore = mutableMapOf<String, AccumulatedEvidence>()

    /**
     * Known contradictory preference pairs.
     * If the user says "detailed" and then "concise", these conflict.
     */
    private val contradictionPairs: Map<String, String> = mapOf(
        "Provide detailed, thorough explanations" to "Provide concise, brief answers",
        "Provide concise, brief answers" to "Provide detailed, thorough explanations"
    )

    /**
     * Process a list of behavioral signals detected from a user message.
     * Accumulates evidence and promotes preferences when thresholds are met.
     *
     * This method is lightweight and non-blocking.
     * It should be called AFTER the response is sent, not before.
     */
    @Synchronized
    fun processSignals(signals: List<BehaviorSignal>) {
        if (signals.isEmpty()) return

        for (signal in signals) {
            accumulateSignal(signal)
        }

        // Promote any evidence that has crossed the threshold
        promoteEligibleEvidence()

        // Prevent unbounded growth
        trimEvidence()
    }

    /**
     * Accumulate a single signal into the evidence store.
     */
    private fun accumulateSignal(signal: BehaviorSignal) {
        val key = signal.description
        val now = System.currentTimeMillis()

        // Check for contradictions first
        val contradictoryKey = contradictionPairs[key]
        if (contradictoryKey != null && evidenceStore.containsKey(contradictoryKey)) {
            val existing = evidenceStore[contradictoryKey]!!
            val penalizedConfidence = (existing.confidence - CONTRADICTION_PENALTY).coerceAtLeast(0.0f)
            evidenceStore[contradictoryKey] = existing.copy(
                confidence = penalizedConfidence,
                contradictionCount = existing.contradictionCount + 1,
                lastSeen = now
            )
        }

        val existing = evidenceStore[key]

        if (existing != null) {
            // Accumulate additional evidence
            val evidenceUnits = when (signal.source) {
                SignalSource.EXPLICIT_CORRECTION -> CORRECTION_EVIDENCE_MULTIPLIER
                SignalSource.EXPLICIT_STATEMENT -> 1
                SignalSource.KEYWORD_PATTERN -> 1
            }
            val newCount = existing.evidenceCount + evidenceUnits
            val newConfidence = when (signal.source) {
                SignalSource.EXPLICIT_STATEMENT -> EXPLICIT_STATEMENT_CONFIDENCE
                else -> (newCount * KEYWORD_CONFIDENCE_INCREMENT).coerceAtMost(0.90f)
            }

            evidenceStore[key] = existing.copy(
                evidenceCount = newCount,
                confidence = newConfidence,
                lastSeen = now
            )
        } else {
            // New evidence entry
            val initialConfidence = when (signal.source) {
                SignalSource.EXPLICIT_STATEMENT -> EXPLICIT_STATEMENT_CONFIDENCE
                SignalSource.EXPLICIT_CORRECTION -> CORRECTION_EVIDENCE_MULTIPLIER * KEYWORD_CONFIDENCE_INCREMENT
                SignalSource.KEYWORD_PATTERN -> KEYWORD_CONFIDENCE_INCREMENT
            }
            val initialCount = when (signal.source) {
                SignalSource.EXPLICIT_CORRECTION -> CORRECTION_EVIDENCE_MULTIPLIER
                else -> 1
            }

            evidenceStore[key] = AccumulatedEvidence(
                category = signal.category,
                description = signal.description,
                topic = signal.topic,
                evidenceCount = initialCount,
                confidence = initialConfidence,
                firstSeen = now,
                lastSeen = now
            )
        }
    }

    /**
     * Promote evidence entries that have crossed the promotion threshold
     * to inferred preferences via UserProfileManager.
     */
    private fun promoteEligibleEvidence() {
        for ((key, evidence) in evidenceStore) {
            if (evidence.promoted) continue
            if (evidence.confidence < PROMOTION_THRESHOLD) continue

            // Check if an explicit preference already exists for this category+description.
            // Explicit always outranks inferred — do not overwrite.
            val profile = userProfileManager.getProfile()
            val hasExplicitConflict = profile.explicitPreferences.any {
                it.category == evidence.category
            }

            // Still promote as inferred even if explicit exists for same category —
            // UserProfileManager.getRelevantBehaviorDirectives prioritizes explicit.
            val result = userProfileManager.recordInferredPreference(
                category = evidence.category,
                description = evidence.description,
                confidence = evidence.confidence,
                topic = evidence.topic
            )

            if (result.isSuccess) {
                evidenceStore[key] = evidence.copy(promoted = true)
            }
        }
    }

    /**
     * Trim evidence store to prevent unbounded growth.
     * Removes oldest, lowest-confidence entries when limit is exceeded.
     */
    private fun trimEvidence() {
        if (evidenceStore.size <= MAX_EVIDENCE_ENTRIES) return

        val toRemove = evidenceStore.entries
            .sortedWith(compareBy({ it.value.promoted }, { it.value.confidence }, { it.value.lastSeen }))
            .take(evidenceStore.size - MAX_EVIDENCE_ENTRIES)
            .map { it.key }

        for (key in toRemove) {
            evidenceStore.remove(key)
        }
    }

    /**
     * Returns a snapshot of current accumulated evidence (for testing/inspection).
     */
    @Synchronized
    fun getEvidenceSnapshot(): List<AccumulatedEvidence> {
        return evidenceStore.values.toList()
    }

    /**
     * Clears all accumulated evidence. Does NOT clear promoted preferences
     * (those are managed by UserProfileManager).
     */
    @Synchronized
    fun clearEvidence() {
        evidenceStore.clear()
    }

    /**
     * Returns the current evidence for a specific description, or null.
     */
    @Synchronized
    fun getEvidence(description: String): AccumulatedEvidence? {
        return evidenceStore[description]
    }
}
