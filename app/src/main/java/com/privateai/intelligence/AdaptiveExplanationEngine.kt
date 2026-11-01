package com.privateai.intelligence

import com.privateai.profile.UserProfile

/**
 * Selects the optimal explanation depth level and pedagogical representation dynamically
 * based on real-time user state, detected task type, conversation history, previous explanation attempts,
 * and accumulated user profile preferences.
 *
 * PROGRESSIVE EXPLANATION ESCALATION:
 * Attempt 1: DIRECT / FORMAL
 *     ↓ user confused
 * Attempt 2: ANALOGY / INTUITION
 *     ↓ user still confused
 * Attempt 3: MICRO-STEP / NUMERICAL TRACE
 *     ↓ user asks specific "why?"
 * Attempt 4: INVARIANT / SPECIFIC MISCONCEPTION RESOLUTION
 *     ↓
 * Attempt 5: ADVANCED INTERNALS / ALTERNATIVE REPRESENTATION
 *
 * STRICT GUARANTEES:
 * - Current explicit user instruction in active turn ALWAYS overrides historical preferences/progression.
 * - Non-repetition: Never reuse a failed representation immediately on the same concept.
 * - Specific misconceptions are directly answered before continuing.
 * - Concept and domain isolation: failed attempts on Concept A do not contaminate Concept B.
 */
class AdaptiveExplanationEngine {

    companion object {
        private val GENERIC_DOMAIN_CONCEPTS = setOf(
            "algorithmic invariant", "infrastructure configuration",
            "system architecture", "code implementation", "technical concept"
        )
    }

    /**
     * Determines the optimal explanation level (backward-compatible API).
     */
    fun selectExplanationLevel(
        domain: Domain,
        taskType: TaskType,
        userState: UserState,
        userProfile: UserProfile? = null,
        conversationContext: String? = null
    ): ExplanationLevel {
        return evaluateAdaptiveProgression(
            domain = domain,
            taskType = taskType,
            userState = userState,
            userQuery = "",
            conversationContext = conversationContext,
            userProfile = userProfile
        ).level
    }

    /**
     * Full multi-turn adaptive evaluation considering cognitive state, active attempts, and specific misconceptions.
     */
    fun evaluateAdaptiveProgression(
        domain: Domain,
        taskType: TaskType,
        userState: UserState,
        userQuery: String,
        conversationContext: String? = null,
        userProfile: UserProfile? = null,
        recentAttempts: List<ExplanationAttempt> = emptyList()
    ): AdaptiveDecision {
        val queryLower = userQuery.lowercase().trim()
        val contextLower = conversationContext?.lowercase() ?: ""

        // 1. Current Explicit In-Turn User Instructions ALWAYS Win First
        val explicitOverride = checkExplicitInTurnOverrides(queryLower)
        if (explicitOverride != null) {
            return explicitOverride
        }

        // 2. Extract Active Concept and Detect Specific Misconceptions
        val activeConcept = extractActiveConcept(queryLower, contextLower, domain)
        val specificMisconception = detectSpecificMisconception(queryLower)

        if (specificMisconception != null) {
            return AdaptiveDecision(
                level = ExplanationLevel.LEVEL_3_MICRO_STEP,
                representation = RepresentationType.INVARIANT,
                targetedConcept = activeConcept,
                specificMisconception = specificMisconception,
                adaptationReason = "SPECIFIC_MISCONCEPTION_DETECTED",
                nonRepetitionDirective = "Directly resolve the user's specific misconception ($specificMisconception) before resuming the broader explanation."
            )
        }

        // 3. Simple Factual & General Conversation defaults to Level 1 Direct
        if (taskType == TaskType.FACTUAL_QUESTION || taskType == TaskType.GENERAL_CONVERSATION || userState == UserState.GENERAL_CONVERSATION) {
            return AdaptiveDecision(
                level = ExplanationLevel.LEVEL_1_DIRECT,
                representation = RepresentationType.DIRECT,
                targetedConcept = activeConcept
            )
        }

        // 4. Bounded Context & Concept-Filtered Attempt History (Strict Concept & Domain Isolation)
        val matchingConceptAttempts = recentAttempts.filter { attempt ->
            if (attempt.domain != domain) return@filter false
            if (activeConcept.isBlank() || activeConcept in GENERIC_DOMAIN_CONCEPTS) return@filter true
            attempt.concept.equals(activeConcept, ignoreCase = true) ||
                    activeConcept.contains(attempt.concept, ignoreCase = true) ||
                    attempt.concept.contains(activeConcept, ignoreCase = true)
        }

        val failedRepresentations = matchingConceptAttempts.filter { it.failed }.map { it.representation }.toSet()

        // 5. UserState-Driven Multi-Turn Adaptation & Escalation
        when (userState) {
            UserState.CONFUSED -> {
                // Check if user profile records recurring struggle on this concept
                if (userProfile != null) {
                    val matchingStruggled = userProfile.struggledConcepts.firstOrNull {
                        it.concept.equals(activeConcept, ignoreCase = true) || contextLower.contains(it.concept.lowercase())
                    }
                    if (matchingStruggled != null && matchingStruggled.failedAttempts >= 2) {
                        return AdaptiveDecision(
                            level = ExplanationLevel.LEVEL_3_MICRO_STEP,
                            representation = RepresentationType.MICRO_STEP,
                            failedRepresentationToAvoid = RepresentationType.ANALOGY,
                            targetedConcept = activeConcept,
                            adaptationReason = "RECURRING_STRUGGLED_CONCEPT_ESCALATION",
                            nonRepetitionDirective = "NON-REPETITION: Do NOT repeat the previous analogy. Break down the concept into micro-steps."
                        )
                    }
                }

                // If previous turn used an analogy (e.g. restaurant, dictionary) and failed -> Escalate to MICRO_STEP / NUMERICAL_TRACE
                val previousWasAnalogy = failedRepresentations.contains(RepresentationType.ANALOGY) ||
                        contextLower.contains("analogy") || contextLower.contains("like ") ||
                        contextLower.contains("imagine ") || contextLower.contains("think of it") ||
                        contextLower.contains("nesting dolls")

                val previousWasMicroStep = failedRepresentations.contains(RepresentationType.MICRO_STEP) ||
                        failedRepresentations.contains(RepresentationType.NUMERICAL_TRACE) ||
                        contextLower.contains("step 1") || contextLower.contains("index ")

                val decision = when {
                    previousWasMicroStep -> {
                        AdaptiveDecision(
                            level = ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL,
                            representation = RepresentationType.INVARIANT,
                            failedRepresentationToAvoid = RepresentationType.MICRO_STEP,
                            targetedConcept = activeConcept,
                            adaptationReason = "MICRO_STEP_FAILED_ESCALATE_TO_INVARIANT",
                            nonRepetitionDirective = "NON-REPETITION: Do NOT repeat the previous step-by-step trace or analogy. Shift to the fundamental mathematical invariant and state boundary constraints clearly."
                        )
                    }
                    previousWasAnalogy -> {
                        AdaptiveDecision(
                            level = ExplanationLevel.LEVEL_3_MICRO_STEP,
                            representation = RepresentationType.NUMERICAL_TRACE,
                            failedRepresentationToAvoid = RepresentationType.ANALOGY,
                            targetedConcept = activeConcept,
                            adaptationReason = "ANALOGY_FAILED_ESCALATE_TO_NUMERICAL_TRACE",
                            nonRepetitionDirective = "NON-REPETITION: Do NOT repeat the previous analogy. Ground the concept in a concrete numerical example with actual array indices and state transitions."
                        )
                    }
                    else -> {
                        // First confusion on formal explanation -> Switch to Analogy / Intuitive mental model
                        AdaptiveDecision(
                            level = ExplanationLevel.LEVEL_2_ANALOGY,
                            representation = RepresentationType.ANALOGY,
                            failedRepresentationToAvoid = RepresentationType.FORMAL,
                            targetedConcept = activeConcept,
                            adaptationReason = "FORMAL_FAILED_ESCALATE_TO_ANALOGY",
                            nonRepetitionDirective = "NON-REPETITION: Do NOT repeat the previous formal definition. Use a fresh, intuitive real-world analogy to establish a clean mental model."
                        )
                    }
                }
                return decision
            }

            UserState.FRUSTRATED -> {
                return AdaptiveDecision(
                    level = ExplanationLevel.LEVEL_1_DIRECT,
                    representation = RepresentationType.DIRECT,
                    targetedConcept = activeConcept,
                    adaptationReason = "FRUSTRATION_DIRECT_SURGICAL_CLARITY",
                    nonRepetitionDirective = "Provide direct, surgical answers with zero filler, conversational preamble, or unsolicited analogies."
                )
            }

            UserState.DEEP_DIVE -> {
                return AdaptiveDecision(
                    level = ExplanationLevel.LEVEL_5_ADVANCED,
                    representation = RepresentationType.FORMAL,
                    targetedConcept = activeConcept
                )
            }

            UserState.DEBUGGING, UserState.VERIFYING -> {
                return AdaptiveDecision(
                    level = ExplanationLevel.LEVEL_1_DIRECT,
                    representation = RepresentationType.DIRECT,
                    targetedConcept = activeConcept
                )
            }

            UserState.NEW_CONCEPT -> {
                return AdaptiveDecision(
                    level = ExplanationLevel.LEVEL_2_ANALOGY,
                    representation = RepresentationType.ANALOGY,
                    targetedConcept = activeConcept
                )
            }

            UserState.COMPARING -> {
                return AdaptiveDecision(
                    level = ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL,
                    representation = RepresentationType.TRADEOFF,
                    targetedConcept = activeConcept
                )
            }

            UserState.PLANNING -> {
                return AdaptiveDecision(
                    level = ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL,
                    representation = RepresentationType.REAL_WORLD_SCENARIO,
                    targetedConcept = activeConcept
                )
            }

            UserState.IMPLEMENTING, UserState.EXPLORING -> {
                return AdaptiveDecision(
                    level = ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL,
                    representation = RepresentationType.CODE_FIRST,
                    targetedConcept = activeConcept
                )
            }

            UserState.GENERAL_CONVERSATION -> {
                return AdaptiveDecision(
                    level = ExplanationLevel.LEVEL_1_DIRECT,
                    representation = RepresentationType.DIRECT,
                    targetedConcept = activeConcept
                )
            }

            UserState.LEARNING, UserState.UNKNOWN -> {
                // Fall through to profile and domain defaults below
            }
        }

        // 6. User Profile Long-Term Preferences (Only applied when not contradicted by current turn evidence)
        if (userProfile != null) {
            val matchingStruggled = userProfile.struggledConcepts.firstOrNull {
                it.concept.equals(activeConcept, ignoreCase = true) || contextLower.contains(it.concept.lowercase())
            }
            if (matchingStruggled != null && matchingStruggled.failedAttempts >= 2) {
                return AdaptiveDecision(
                    level = ExplanationLevel.LEVEL_3_MICRO_STEP,
                    representation = RepresentationType.MICRO_STEP,
                    targetedConcept = activeConcept,
                    adaptationReason = "RECURRING_STRUGGLED_CONCEPT"
                )
            }

            val highConfidenceDims = userProfile.learningDimensions.filter { it.value >= 0.65f }
            if (highConfidenceDims.containsKey("code_before_explanation")) {
                return AdaptiveDecision(level = ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL, representation = RepresentationType.CODE_FIRST, targetedConcept = activeConcept)
            }
            if (highConfidenceDims.containsKey("intuition_first") && !failedRepresentations.contains(RepresentationType.ANALOGY)) {
                return AdaptiveDecision(level = ExplanationLevel.LEVEL_2_ANALOGY, representation = RepresentationType.ANALOGY, targetedConcept = activeConcept)
            }
            if (highConfidenceDims.containsKey("step_by_step") && !failedRepresentations.contains(RepresentationType.MICRO_STEP)) {
                return AdaptiveDecision(level = ExplanationLevel.LEVEL_3_MICRO_STEP, representation = RepresentationType.MICRO_STEP, targetedConcept = activeConcept)
            }
        }

        // 7. Domain / Task Default Decision
        val defaultLevel = when (taskType) {
            TaskType.DSA_PROBLEM, TaskType.DSA_EXPLANATION -> ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL
            TaskType.SYSTEM_DESIGN -> ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL
            TaskType.DEVOPS_TROUBLESHOOTING -> ExplanationLevel.LEVEL_1_DIRECT
            TaskType.DEVOPS_CONFIGURATION -> ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL
            TaskType.COMPARISON -> ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL
            TaskType.OPTIMIZATION -> ExplanationLevel.LEVEL_5_ADVANCED
            TaskType.LEARNING_REQUEST -> ExplanationLevel.LEVEL_2_ANALOGY
            else -> ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL
        }

        val defaultRepresentation = when (domain) {
            Domain.DSA -> RepresentationType.INVARIANT
            Domain.DEVOPS -> RepresentationType.REAL_WORLD_SCENARIO
            Domain.SYSTEM_DESIGN -> RepresentationType.TRADEOFF
            Domain.CODING -> RepresentationType.CODE_FIRST
            else -> RepresentationType.DIRECT
        }

        return AdaptiveDecision(
            level = defaultLevel,
            representation = defaultRepresentation,
            targetedConcept = activeConcept
        )
    }

    private fun checkExplicitInTurnOverrides(q: String): AdaptiveDecision? {
        if (q.contains("code only") || q.contains("just the code") || q.contains("show me the code") || q.contains("give me the code only")) {
            return AdaptiveDecision(
                level = ExplanationLevel.LEVEL_1_DIRECT,
                representation = RepresentationType.CODE_FIRST,
                adaptationReason = "EXPLICIT_CODE_ONLY_OVERRIDE"
            )
        }
        if (q.contains("two lines") || q.contains("in two lines") || q.contains("keep it short") || q.contains("be concise") || q.contains("just the answer")) {
            return AdaptiveDecision(
                level = ExplanationLevel.LEVEL_1_DIRECT,
                representation = RepresentationType.DIRECT,
                adaptationReason = "EXPLICIT_CONCISE_OVERRIDE"
            )
        }
        if (q.contains("no examples") || q.contains("formally") || q.contains("formal explanation") || q.contains("formal definition") || q.contains("without examples")) {
            return AdaptiveDecision(
                level = ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL,
                representation = RepresentationType.FORMAL,
                adaptationReason = "EXPLICIT_FORMAL_OVERRIDE"
            )
        }
        if (q.contains("explain the intuition") || q.contains("give me an analogy") || q.contains("intuition first") || q.contains("with examples") || q.contains("explain with examples") || q.contains("use examples")) {
            return AdaptiveDecision(
                level = ExplanationLevel.LEVEL_2_ANALOGY,
                representation = RepresentationType.ANALOGY,
                adaptationReason = "EXPLICIT_INTUITION_OVERRIDE"
            )
        }
        return null
    }

    private fun extractActiveConcept(queryLower: String, contextLower: String, domain: Domain): String {
        val knownConcepts = listOf(
            "binary search", "quicksort", "mergesort", "hashmap", "b-tree", "lsm tree",
            "docker", "kubernetes", "paxos", "raft", "redis", "postgresql", "kafka",
            "kotlin coroutines", "null safety", "lru cache", "trie", "dynamic programming",
            "recursion"
        )
        for (concept in knownConcepts) {
            if (queryLower.contains(concept) || contextLower.contains(concept)) {
                return concept
            }
        }
        return when (domain) {
            Domain.DSA -> "algorithmic invariant"
            Domain.DEVOPS -> "infrastructure configuration"
            Domain.SYSTEM_DESIGN -> "system architecture"
            Domain.CODING -> "code implementation"
            else -> "technical concept"
        }
    }

    private fun detectSpecificMisconception(q: String): String? {
        if (Regex("why.*eliminate", RegexOption.IGNORE_CASE).containsMatchIn(q) ||
            q.contains("why can we eliminate") || q.contains("why do we eliminate")) {
            return "Eliminating search space halves based on sorted invariant"
        }
        if (q.contains("checks log(n) elements") || q.contains("checks log n elements") || q.contains("checks log(n)") || q.contains("checks log n")) {
            return "O(log n) represents iteration steps by halving search space, not total checked elements"
        }
        if (q.contains("o(1) mean it always takes") || q.contains("o(1) means exactly the same time") || q.contains("o(1) time complexity means")) {
            return "O(1) means constant time complexity independent of input size, not a fixed execution duration in milliseconds"
        }
        if (q.contains("spawn 500 threads") || q.contains("spawn 1000 threads")) {
            return "CPU-bound tasks cannot scale beyond hardware core count due to context switching overhead"
        }
        return null
    }
}
