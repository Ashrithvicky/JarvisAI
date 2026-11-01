package com.privateai.intelligence

import com.privateai.profile.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MultiTurnAdaptationTest {

    private lateinit var engine: AdaptiveExplanationEngine
    private lateinit var userStateDetector: UserStateDetector
    private lateinit var strategyResolver: StrategyResolver

    @Before
    fun setup() {
        engine = AdaptiveExplanationEngine()
        userStateDetector = UserStateDetector()
        strategyResolver = StrategyResolver(
            userStateDetector = userStateDetector,
            explanationEngine = engine
        )
    }

    @Test
    fun `TEST 1 - initial explanation selects an appropriate baseline level`() {
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA,
            taskType = TaskType.DSA_EXPLANATION,
            userState = UserState.LEARNING,
            userQuery = "Explain binary search"
        )
        assertEquals(ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL, decision.level)
        assertEquals(RepresentationType.INVARIANT, decision.representation)
    }

    @Test
    fun `TEST 2 - user confusion changes representation from formal to analogy`() {
        val attempt1 = ExplanationAttempt(
            concept = "binary search",
            domain = Domain.DSA,
            level = ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL,
            representation = RepresentationType.FORMAL,
            failed = true
        )

        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA,
            taskType = TaskType.LEARNING_REQUEST,
            userState = UserState.CONFUSED,
            userQuery = "I don't understand.",
            conversationContext = "Binary search divides the interval in half",
            recentAttempts = listOf(attempt1)
        )

        assertEquals(ExplanationLevel.LEVEL_2_ANALOGY, decision.level)
        assertEquals(RepresentationType.ANALOGY, decision.representation)
        assertEquals(RepresentationType.FORMAL, decision.failedRepresentationToAvoid)
        assertNotNull(decision.nonRepetitionDirective)
    }

    @Test
    fun `TEST 3 - repeated confusion does not repeat analogy and escalates to numerical trace`() {
        val attempt1 = ExplanationAttempt(
            concept = "binary search",
            domain = Domain.DSA,
            level = ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL,
            representation = RepresentationType.FORMAL,
            failed = true
        )
        val attempt2 = ExplanationAttempt(
            concept = "binary search",
            domain = Domain.DSA,
            level = ExplanationLevel.LEVEL_2_ANALOGY,
            representation = RepresentationType.ANALOGY,
            failed = true
        )

        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA,
            taskType = TaskType.LEARNING_REQUEST,
            userState = UserState.CONFUSED,
            userQuery = "Still don't understand.",
            conversationContext = "Think of it like searching for a word in a dictionary",
            recentAttempts = listOf(attempt1, attempt2)
        )

        assertEquals(ExplanationLevel.LEVEL_3_MICRO_STEP, decision.level)
        assertEquals(RepresentationType.NUMERICAL_TRACE, decision.representation)
        assertEquals(RepresentationType.ANALOGY, decision.failedRepresentationToAvoid)
        assertTrue(decision.nonRepetitionDirective!!.contains("Do NOT repeat the previous analogy"))
    }

    @Test
    fun `TEST 4 - formal to analogy progression works`() {
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA,
            taskType = TaskType.LEARNING_REQUEST,
            userState = UserState.CONFUSED,
            userQuery = "I'm confused",
            conversationContext = "Binary search O(log n)"
        )
        assertEquals(ExplanationLevel.LEVEL_2_ANALOGY, decision.level)
        assertEquals(RepresentationType.ANALOGY, decision.representation)
    }

    @Test
    fun `TEST 5 - analogy to micro-step progression works`() {
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA,
            taskType = TaskType.LEARNING_REQUEST,
            userState = UserState.CONFUSED,
            userQuery = "Still confused",
            conversationContext = "Imagine a dictionary analogy where you open the middle page"
        )
        assertEquals(ExplanationLevel.LEVEL_3_MICRO_STEP, decision.level)
        assertEquals(RepresentationType.NUMERICAL_TRACE, decision.representation)
    }

    @Test
    fun `TEST 6 - micro-step to invariant resolution when user asks specific why`() {
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA,
            taskType = TaskType.DSA_EXPLANATION,
            userState = UserState.LEARNING,
            userQuery = "Why can we eliminate the other half?",
            conversationContext = "Step 1: check index 4. Step 2: check index 2."
        )
        assertEquals(ExplanationLevel.LEVEL_3_MICRO_STEP, decision.level)
        assertEquals(RepresentationType.INVARIANT, decision.representation)
        assertEquals("SPECIFIC_MISCONCEPTION_DETECTED", decision.adaptationReason)
        assertNotNull(decision.specificMisconception)
    }

    @Test
    fun `TEST 7 - explicit current instruction overrides adaptive progression`() {
        val attempt1 = ExplanationAttempt(
            concept = "binary search",
            domain = Domain.DSA,
            level = ExplanationLevel.LEVEL_2_ANALOGY,
            representation = RepresentationType.ANALOGY,
            failed = true
        )

        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA,
            taskType = TaskType.CODE_GENERATION,
            userState = UserState.IMPLEMENTING,
            userQuery = "Just show me the code only.",
            recentAttempts = listOf(attempt1)
        )

        assertEquals(ExplanationLevel.LEVEL_1_DIRECT, decision.level)
        assertEquals(RepresentationType.CODE_FIRST, decision.representation)
    }

    @Test
    fun `TEST 8 - explicit concise request overrides deep dive profile`() {
        val profile = UserProfile(
            learningDimensions = mapOf("deep_dive_preferred" to 0.95f)
        )
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.DEVOPS,
            taskType = TaskType.DEVOPS_TROUBLESHOOTING,
            userState = UserState.DEBUGGING,
            userQuery = "Why is container crashing? Two lines only please.",
            userProfile = profile
        )
        assertEquals(ExplanationLevel.LEVEL_1_DIRECT, decision.level)
        assertEquals(RepresentationType.DIRECT, decision.representation)
    }

    @Test
    fun `TEST 9 - diagnostic question classifies as DEBUGGING not FRUSTRATED`() {
        val state = userStateDetector.detectUserState("Why doesn't this work?")
        assertEquals(UserState.DEBUGGING, state)
        assertNotEquals(UserState.FRUSTRATED, state)
    }

    @Test
    fun `TEST 10 - temporary confusion does not permanently corrupt profile`() {
        val detector = com.privateai.learning.BehaviorDetector()
        val signals = detector.detect("Still don't understand.")
        assertTrue(signals.isEmpty())
    }

    @Test
    fun `TEST 11 - specific misconception is distinguished from generic confusion`() {
        val genericState = userStateDetector.detectUserState("I don't understand.")
        assertEquals(UserState.CONFUSED, genericState)

        val misconceptionDecision = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA,
            taskType = TaskType.DSA_EXPLANATION,
            userState = UserState.LEARNING,
            userQuery = "So binary search checks log(n) elements?"
        )
        assertNotNull(misconceptionDecision.specificMisconception)
        assertTrue(misconceptionDecision.specificMisconception!!.contains("O(log n)"))
    }

    @Test
    fun `TEST 12 - previous explanation representation is tracked`() {
        val attempt = ExplanationAttempt(
            concept = "quicksort",
            domain = Domain.DSA,
            level = ExplanationLevel.LEVEL_2_ANALOGY,
            representation = RepresentationType.ANALOGY,
            failed = true
        )
        assertEquals(RepresentationType.ANALOGY, attempt.representation)
        assertTrue(attempt.failed)
    }

    @Test
    fun `TEST 13 - same representation cannot immediately be selected again after failure`() {
        val attempt = ExplanationAttempt(
            concept = "quicksort",
            domain = Domain.DSA,
            level = ExplanationLevel.LEVEL_2_ANALOGY,
            representation = RepresentationType.ANALOGY,
            failed = true
        )
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA,
            taskType = TaskType.LEARNING_REQUEST,
            userState = UserState.CONFUSED,
            userQuery = "Still confused about quicksort",
            recentAttempts = listOf(attempt)
        )
        assertNotEquals(RepresentationType.ANALOGY, decision.representation)
        assertEquals(RepresentationType.NUMERICAL_TRACE, decision.representation)
    }

    @Test
    fun `TEST 14 - different domains do not inherit failed explanation representations`() {
        val dsaAttempt = ExplanationAttempt(
            concept = "binary search",
            domain = Domain.DSA,
            level = ExplanationLevel.LEVEL_2_ANALOGY,
            representation = RepresentationType.ANALOGY,
            failed = true
        )

        // Next turn is Docker DevOps
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.DEVOPS,
            taskType = TaskType.DEVOPS_TROUBLESHOOTING,
            userState = UserState.DEBUGGING,
            userQuery = "Why is my Docker container crashing?",
            recentAttempts = listOf(dsaAttempt)
        )
        assertEquals(ExplanationLevel.LEVEL_1_DIRECT, decision.level)
        assertFalse(decision.nonRepetitionDirective?.contains("binary search") ?: false)
    }

    @Test
    fun `TEST 15 - multiple concepts in a conversation do not corrupt each other's attempt history`() {
        val binarySearchAttempt = ExplanationAttempt(
            concept = "binary search",
            domain = Domain.DSA,
            level = ExplanationLevel.LEVEL_2_ANALOGY,
            representation = RepresentationType.ANALOGY,
            failed = true
        )

        val coroutineDecision = engine.evaluateAdaptiveProgression(
            domain = Domain.CODING,
            taskType = TaskType.CODE_GENERATION,
            userState = UserState.LEARNING,
            userQuery = "How do Kotlin coroutines work under the hood?",
            recentAttempts = listOf(binarySearchAttempt)
        )
        // Coroutine explanation is evaluated cleanly without inheriting binary search failed analogy state
        assertEquals("kotlin coroutines", coroutineDecision.targetedConcept)
        assertNotEquals(RepresentationType.NUMERICAL_TRACE, coroutineDecision.representation)
    }

    @Test
    fun `TEST 16 - long conversation context pruning preserves active concept adaptation state`() {
        val attempt = ExplanationAttempt(
            concept = "paxos",
            domain = Domain.SYSTEM_DESIGN,
            level = ExplanationLevel.LEVEL_2_ANALOGY,
            representation = RepresentationType.ANALOGY,
            failed = true
        )
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.SYSTEM_DESIGN,
            taskType = TaskType.LEARNING_REQUEST,
            userState = UserState.CONFUSED,
            userQuery = "I still don't understand Paxos consensus.",
            conversationContext = "[CONVERSATION SUMMARY OF EARLIER TURNS]\n- User asked: Paxos",
            recentAttempts = listOf(attempt)
        )
        assertEquals(RepresentationType.NUMERICAL_TRACE, decision.representation)
    }

    @Test
    fun `TEST 17 - COMPARING state is correctly detected`() {
        val state = userStateDetector.detectUserState("Compare Redis vs PostgreSQL for caching")
        assertEquals(UserState.COMPARING, state)
    }

    @Test
    fun `TEST 18 - PLANNING state is correctly detected`() {
        val state = userStateDetector.detectUserState("Let's design a distributed notification service roadmap")
        assertEquals(UserState.PLANNING, state)
    }

    @Test
    fun `TEST 19 - GENERAL_CONVERSATION state is correctly detected`() {
        val state = userStateDetector.detectUserState("Hello! How are you doing today?")
        assertEquals(UserState.GENERAL_CONVERSATION, state)
    }

    @Test
    fun `TEST 20 - current explicit user instruction has highest priority over profile and historical adaptation`() {
        val profile = UserProfile(
            learningDimensions = mapOf(
                "intuition_first" to 0.95f,
                "analogy_preferred" to 0.90f
            )
        )
        val failedAttempt = ExplanationAttempt(
            concept = "binary search",
            domain = Domain.DSA,
            level = ExplanationLevel.LEVEL_2_ANALOGY,
            representation = RepresentationType.ANALOGY,
            failed = true
        )

        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA,
            taskType = TaskType.CODE_GENERATION,
            userState = UserState.IMPLEMENTING,
            userQuery = "Just give me the code only without explanations.",
            userProfile = profile,
            recentAttempts = listOf(failedAttempt)
        )
        assertEquals(ExplanationLevel.LEVEL_1_DIRECT, decision.level)
        assertEquals(RepresentationType.CODE_FIRST, decision.representation)
    }

    // ========== Adversarial Tests ==========

    @Test
    fun `ADVERSARIAL A - repetition trap prevented when previous restaurant analogy failed`() {
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.SYSTEM_DESIGN,
            taskType = TaskType.LEARNING_REQUEST,
            userState = UserState.CONFUSED,
            userQuery = "Still don't get it.",
            conversationContext = "Imagine a restaurant analogy with waiters and chefs handling orders"
        )
        assertNotEquals(RepresentationType.ANALOGY, decision.representation)
        assertEquals(RepresentationType.NUMERICAL_TRACE, decision.representation)
        assertTrue(decision.nonRepetitionDirective!!.contains("Do NOT repeat the previous analogy"))
    }

    @Test
    fun `ADVERSARIAL B - preference trap prevented when preferred analogy failed`() {
        val profile = UserProfile(
            learningDimensions = mapOf("analogy_preferred" to 0.95f)
        )
        val failedAnalogy = ExplanationAttempt(
            concept = "raft",
            domain = Domain.SYSTEM_DESIGN,
            level = ExplanationLevel.LEVEL_2_ANALOGY,
            representation = RepresentationType.ANALOGY,
            failed = true
        )
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.SYSTEM_DESIGN,
            taskType = TaskType.LEARNING_REQUEST,
            userState = UserState.CONFUSED,
            userQuery = "Explain it differently.",
            userProfile = profile,
            recentAttempts = listOf(failedAnalogy)
        )
        assertNotEquals(RepresentationType.ANALOGY, decision.representation)
    }

    @Test
    fun `ADVERSARIAL C - false frustration prevented on API debugging query`() {
        val state = userStateDetector.detectUserState("Why doesn't this API call work?")
        assertEquals(UserState.DEBUGGING, state)
        assertNotEquals(UserState.FRUSTRATED, state)
    }

    @Test
    fun `ADVERSARIAL D - explicit override forces two lines despite deep dive profile`() {
        val profile = UserProfile(
            learningDimensions = mapOf("deep_dive_preferred" to 0.95f)
        )
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.GENERAL,
            taskType = TaskType.FACTUAL_QUESTION,
            userState = UserState.LEARNING,
            userQuery = "Two lines only.",
            userProfile = profile
        )
        assertEquals(ExplanationLevel.LEVEL_1_DIRECT, decision.level)
    }

    @Test
    fun `ADVERSARIAL E - misconception on O(log n) elements checked is corrected directly`() {
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA,
            taskType = TaskType.DSA_EXPLANATION,
            userState = UserState.LEARNING,
            userQuery = "So binary search is O(log n) because it checks log(n) elements?"
        )
        assertEquals("SPECIFIC_MISCONCEPTION_DETECTED", decision.adaptationReason)
        assertEquals(RepresentationType.INVARIANT, decision.representation)
        assertTrue(decision.nonRepetitionDirective!!.contains("specific misconception"))
    }

    @Test
    fun `ADVERSARIAL F - domain isolation between binary search and Docker`() {
        val binarySearchFail = ExplanationAttempt(
            concept = "binary search",
            domain = Domain.DSA,
            level = ExplanationLevel.LEVEL_2_ANALOGY,
            representation = RepresentationType.ANALOGY,
            failed = true
        )
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.DEVOPS,
            taskType = TaskType.DEVOPS_TROUBLESHOOTING,
            userState = UserState.DEBUGGING,
            userQuery = "Why is my Docker container exiting with code 137?",
            recentAttempts = listOf(binarySearchFail)
        )
        assertEquals("docker", decision.targetedConcept)
        assertEquals(ExplanationLevel.LEVEL_1_DIRECT, decision.level)
    }

    @Test
    fun `ADVERSARIAL G - multiple concepts do not cross-contaminate tiers`() {
        val attemptDSA = ExplanationAttempt(
            concept = "binary search",
            domain = Domain.DSA,
            level = ExplanationLevel.LEVEL_2_ANALOGY,
            representation = RepresentationType.ANALOGY,
            failed = true
        )
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.CODING,
            taskType = TaskType.CODE_GENERATION,
            userState = UserState.LEARNING,
            userQuery = "How does Kotlin Flow work?",
            recentAttempts = listOf(attemptDSA)
        )
        assertEquals(RepresentationType.CODE_FIRST, decision.representation)
    }
}
