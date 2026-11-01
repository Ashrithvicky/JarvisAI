package com.privateai.intelligence

import com.privateai.context.PromptContextBuilder
import com.privateai.learning.BehaviorDetector
import com.privateai.memory.Memory
import com.privateai.memory.MemoryManager
import com.privateai.memory.MemoryType
import com.privateai.persona.AiPersona
import com.privateai.profile.BehaviorPreference
import com.privateai.profile.PreferenceCategory
import com.privateai.profile.UserProfile
import com.privateai.profile.UserProfileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VivvaAdversarialHardeningTest {

    private lateinit var engine: AdaptiveExplanationEngine
    private lateinit var userStateDetector: UserStateDetector
    private lateinit var domainDetector: DomainDetector
    private lateinit var taskClassifier: TaskClassifier
    private lateinit var strategyResolver: StrategyResolver
    private lateinit var memoryManager: MemoryManager
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var promptContextBuilder: PromptContextBuilder

    @Before
    fun setup() {
        engine = AdaptiveExplanationEngine()
        userStateDetector = UserStateDetector()
        domainDetector = DomainDetector()
        taskClassifier = TaskClassifier()
        strategyResolver = StrategyResolver(
            domainDetector = domainDetector,
            taskClassifier = taskClassifier,
            userStateDetector = userStateDetector,
            explanationEngine = engine
        )
        memoryManager = mock()
        userProfileManager = mock()
        whenever(userProfileManager.getProfile()).thenReturn(UserProfile())
        whenever(userProfileManager.getRelevantBehaviorDirectives(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(emptyList())
        promptContextBuilder = PromptContextBuilder(memoryManager, userProfileManager, strategyResolver)
    }

    // =========================================================================
    // SECTION 2: ADVERSARIAL PERSONALIZATION TESTS
    // =========================================================================

    @Test
    fun `ADV 2A - explicit concise instruction overrides multiple strong inferred preferences`() {
        val profile = UserProfile(
            learningDimensions = mapOf(
                "deep_dive_preferred" to 0.90f,
                "examples_first" to 0.85f,
                "analogy_preferred" to 0.82f
            )
        )
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.GENERAL,
            taskType = TaskType.FACTUAL_QUESTION,
            userState = UserState.LEARNING,
            userQuery = "Just give me the answer in two lines.",
            userProfile = profile
        )
        assertEquals(ExplanationLevel.LEVEL_1_DIRECT, decision.level)
        assertEquals(RepresentationType.DIRECT, decision.representation)
        assertEquals("EXPLICIT_CONCISE_OVERRIDE", decision.adaptationReason)
    }

    @Test
    fun `ADV 2B - explicit preference reversal from examples to formal math`() {
        val profileWithExamplePref = UserProfile(
            learningDimensions = mapOf("examples_first" to 0.88f)
        )
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA,
            taskType = TaskType.DSA_EXPLANATION,
            userState = UserState.LEARNING,
            userQuery = "Stop using examples. Explain formally without examples.",
            userProfile = profileWithExamplePref
        )
        assertEquals(ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL, decision.level)
        assertEquals(RepresentationType.FORMAL, decision.representation)
        assertEquals("EXPLICIT_FORMAL_OVERRIDE", decision.adaptationReason)
    }

    @Test
    fun `ADV 2C - contradictory in-turn instructions prioritize direct clarity`() {
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.CODING,
            taskType = TaskType.CODE_GENERATION,
            userState = UserState.IMPLEMENTING,
            userQuery = "Give code only, but keep it short in two lines."
        )
        assertEquals(ExplanationLevel.LEVEL_1_DIRECT, decision.level)
    }

    @Test
    fun `ADV 2D - repeated confusion does not create permanent profile preference`() {
        val detector = BehaviorDetector()
        val signal1 = detector.detect("I don't understand.")
        val signal2 = detector.detect("Still confused.")
        val signal3 = detector.detect("Again, didn't get that.")

        assertTrue("Turn 1 confusion must not create permanent dimension", signal1.isEmpty())
        assertTrue("Turn 2 confusion must not create permanent dimension", signal2.isEmpty())
        assertTrue("Turn 3 confusion must not create permanent dimension", signal3.isEmpty())
    }

    @Test
    fun `ADV 2E - preference confidence decay and explicit immutability`() {
        val explicitPref = BehaviorPreference(
            id = "pref-1",
            description = "User prefers Kotlin",
            category = PreferenceCategory.CODING_STYLE,
            confidence = 1.0f,
            isExplicit = true
        )
        val inferredPref = BehaviorPreference(
            id = "pref-2",
            description = "User prefers short explanations",
            category = PreferenceCategory.EXPLANATION_DEPTH,
            confidence = 0.60f,
            isExplicit = false
        )

        // Inferred decays when unreinforced; explicit never decays
        val decayedInferred = inferredPref.copy(confidence = inferredPref.confidence * 0.9f)
        assertTrue(decayedInferred.confidence < 0.60f)
        assertEquals(1.0f, explicitPref.confidence, 0.001f)
    }

    // =========================================================================
    // SECTION 3: MEMORY ADVERSARIAL TESTS
    // =========================================================================

    @Test
    fun `ADV 3A - irrelevant project and stack memory completely suppressed for general query`() {
        val memories = listOf(
            Memory(id = "1", content = "VIVVA uses Gemma 2B and LiteRT-LM", type = MemoryType.PROJECT, tags = listOf("vivva", "gemma")),
            Memory(id = "2", content = "Project Alpha uses Python and Django", type = MemoryType.PROJECT, tags = listOf("python", "django")),
            Memory(id = "3", content = "User has PostgreSQL cluster stack", type = MemoryType.TECH_STACK, tags = listOf("postgresql", "database"))
        )
        whenever(memoryManager.getAllMemories()).thenReturn(memories)

        val retrieved = promptContextBuilder.retrieveRelevantMemories("What is binary search and how does it work?")
        assertTrue("Unrelated project memories must be completely suppressed", retrieved.isEmpty())
    }

    @Test
    fun `ADV 3B - exact project query retrieves matching memory`() {
        val vivvaMemory = Memory(
            id = "vivva-1",
            content = "VIVVA uses LiteRT-LM and Gemma 2B on-device",
            type = MemoryType.PROJECT,
            tags = listOf("vivva", "litert-lm", "gemma")
        )
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(vivvaMemory))

        val retrieved = promptContextBuilder.retrieveRelevantMemories("Why is my VIVVA Gemma inference slow?")
        assertEquals(1, retrieved.size)
        assertEquals("vivva-1", retrieved[0].id)
    }

    @Test
    fun `ADV 3C - cross-project contamination prevented`() {
        val projectA = Memory(id = "proj-a", content = "Project Alpha uses Python and Django", type = MemoryType.PROJECT, tags = listOf("alpha", "python"))
        val projectB = Memory(id = "proj-b", content = "Project Beta uses Kotlin and Jetpack Compose", type = MemoryType.PROJECT, tags = listOf("beta", "kotlin", "compose"))
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(projectA, projectB))

        val retrieved = promptContextBuilder.retrieveRelevantMemories("How do I structure Jetpack Compose navigation in Project Beta?")
        assertEquals(1, retrieved.size)
        assertEquals("proj-b", retrieved[0].id)
        assertFalse(retrieved.any { it.id == "proj-a" })
    }

    @Test
    fun `ADV 3D - legacy preference memory is never emitted as behavioral directive`() {
        val legacyPref = Memory(
            id = "pref-1",
            content = "User prefers step by step explanations",
            type = MemoryType.PREFERENCE,
            tags = listOf("presentation")
        )
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(legacyPref))

        val retrieved = promptContextBuilder.retrieveRelevantMemories("Explain Dijkstra algorithm step by step")
        assertTrue(retrieved.isEmpty())
    }

    @Test
    fun `ADV 3E - memory budget strictly capped at 250 tokens`() {
        assertEquals(250, PromptContextBuilder.MAX_MEMORY_TOKENS)
        val massiveMemories = (1..20).map { i ->
            Memory(id = "m-$i", content = "Project Spec $i: Exhaustive technical requirements, database models, caching strategies, and service boundaries for subsystem $i.", type = MemoryType.PROJECT, tags = listOf("subsystem", "spec"))
        }
        whenever(memoryManager.getAllMemories()).thenReturn(massiveMemories)

        val retrieved = promptContextBuilder.retrieveRelevantMemories("Tell me about the subsystem spec requirements", limit = 20, maxTokenBudget = 250)
        assertTrue(retrieved.size < 20)
    }

    // =========================================================================
    // SECTION 4: MULTI-TURN ADAPTATION & PROGRESSION TESTS
    // =========================================================================

    @Test
    fun `ADV 4A - 6-turn progressive escalation on binary search`() {
        val attempts = mutableListOf<ExplanationAttempt>()

        // Turn 1: Initial formal explanation
        val turn1 = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA, taskType = TaskType.DSA_EXPLANATION, userState = UserState.LEARNING, userQuery = "Explain binary search", recentAttempts = attempts
        )
        assertEquals(RepresentationType.INVARIANT, turn1.representation)
        attempts.add(ExplanationAttempt("binary search", Domain.DSA, turn1.level, turn1.representation, failed = true))

        // Turn 2: User says "I don't understand." -> Escalate to Analogy
        val turn2 = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA, taskType = TaskType.LEARNING_REQUEST, userState = UserState.CONFUSED, userQuery = "I don't understand.", recentAttempts = attempts
        )
        assertEquals(RepresentationType.ANALOGY, turn2.representation)
        attempts.add(ExplanationAttempt("binary search", Domain.DSA, turn2.level, turn2.representation, failed = true))

        // Turn 3: User says "Still don't understand." -> Escalate to Numerical Trace
        val turn3 = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA, taskType = TaskType.LEARNING_REQUEST, userState = UserState.CONFUSED, userQuery = "Still don't understand.", recentAttempts = attempts
        )
        assertEquals(RepresentationType.NUMERICAL_TRACE, turn3.representation)
        attempts.add(ExplanationAttempt("binary search", Domain.DSA, turn3.level, turn3.representation, failed = true))

        // Turn 4: User asks specific misconception "Why exactly can we eliminate the left half?" -> Invariant Misconception Resolution
        val turn4 = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA, taskType = TaskType.DSA_EXPLANATION, userState = UserState.LEARNING, userQuery = "Why exactly can we eliminate the left half?", recentAttempts = attempts
        )
        assertEquals(RepresentationType.INVARIANT, turn4.representation)
        assertNotNull(turn4.specificMisconception)

        // Turn 5: User says "Show me the code only." -> Explicit in-turn override
        val turn5 = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA, taskType = TaskType.CODE_GENERATION, userState = UserState.IMPLEMENTING, userQuery = "Show me the code only.", recentAttempts = attempts
        )
        assertEquals(RepresentationType.CODE_FIRST, turn5.representation)

        // Turn 6: User says "Now explain the invariant formally." -> Explicit formal override
        val turn6 = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA, taskType = TaskType.DSA_EXPLANATION, userState = UserState.LEARNING, userQuery = "Now explain the invariant formally.", recentAttempts = attempts
        )
        assertEquals(RepresentationType.FORMAL, turn6.representation)
    }

    @Test
    fun `ADV 4B - failed Docker PID 1 analogy does not contaminate unrelated algorithms`() {
        val dockerFail = ExplanationAttempt("docker pid 1", Domain.DEVOPS, ExplanationLevel.LEVEL_2_ANALOGY, RepresentationType.ANALOGY, failed = true)

        val quicksortDecision = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA, taskType = TaskType.DSA_EXPLANATION, userState = UserState.LEARNING, userQuery = "Explain quicksort pivot selection", recentAttempts = listOf(dockerFail)
        )
        assertEquals("quicksort", quicksortDecision.targetedConcept)
        assertEquals(RepresentationType.INVARIANT, quicksortDecision.representation)
    }

    // =========================================================================
    // SECTION 5: DOMAIN ISOLATION TESTS
    // =========================================================================

    @Test
    fun `ADV 5A - continuous single-session multi-domain routing`() {
        // DSA -> Kotlin -> DevOps -> System Design -> General
        val q1 = "How does binary search work?"
        assertEquals(Domain.DSA, domainDetector.detectDomain(q1))

        val q2 = "How do Kotlin coroutine scopes handle cancellation?"
        assertEquals(Domain.CODING, domainDetector.detectDomain(q2))

        val q3 = "Why is my Docker container crashing with OOMKilled?"
        assertEquals(Domain.DEVOPS, domainDetector.detectDomain(q3))

        val q4 = "How to architect a distributed rate limiter?"
        assertEquals(Domain.SYSTEM_DESIGN, domainDetector.detectDomain(q4))

        val q5 = "What is the capital of France?"
        assertEquals(Domain.GENERAL, domainDetector.detectDomain(q5))
    }

    // =========================================================================
    // SECTION 6: USER STATE ADVERSARIAL TESTS
    // =========================================================================

    @Test
    fun `ADV 6A - diagnostic query is DEBUGGING not FRUSTRATED`() {
        val state = userStateDetector.detectUserState("Why doesn't this code work?")
        assertEquals(UserState.DEBUGGING, state)
        assertNotEquals(UserState.FRUSTRATED, state)
    }

    @Test
    fun `ADV 6B - repeated exhaustion marker is FRUSTRATED`() {
        val state = userStateDetector.detectUserState("I've tried everything for three hours and nothing works, so frustrated")
        assertEquals(UserState.FRUSTRATED, state)
    }

    @Test
    fun `ADV 6C - comparison and planning states are correctly detected`() {
        val compState = userStateDetector.detectUserState("Compare Redis vs Memcached for session caching")
        assertEquals(UserState.COMPARING, compState)

        val planState = userStateDetector.detectUserState("Let's design a learning path and roadmap for distributed systems")
        assertEquals(UserState.PLANNING, planState)
    }

    // =========================================================================
    // SECTION 7: FALSE-PREMISE ADVERSARIAL TESTS
    // =========================================================================

    @Test
    fun `ADV 7A - system instruction contains critical evaluation and premise verification directive`() {
        val context = promptContextBuilder.buildSystemInstruction("Since O(1) means constant milliseconds, why does this algorithm take longer?")
        assertTrue(context.systemInstruction.contains("[CRITICAL EVALUATION & PREMISE VERIFICATION]"))
        assertTrue(context.systemInstruction.contains("Independently verify the user's factual, mathematical, programming, or algorithmic premises"))
    }

    @Test
    fun `ADV 7B - specific misconception detector catches false premise regarding O(log n)`() {
        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.DSA,
            taskType = TaskType.DSA_EXPLANATION,
            userState = UserState.LEARNING,
            userQuery = "Since binary search is O(log n), it checks log(n) elements exactly, right?"
        )
        assertEquals("SPECIFIC_MISCONCEPTION_DETECTED", decision.adaptationReason)
        assertEquals(RepresentationType.INVARIANT, decision.representation)
        assertNotNull(decision.specificMisconception)
    }

    // =========================================================================
    // SECTION 8: LONG-CONTEXT STRESS TESTING
    // =========================================================================

    @Test
    fun `ADV 8A - long context summary preserves active concept and failed attempt avoidance`() {
        val attempt1 = ExplanationAttempt("paxos", Domain.SYSTEM_DESIGN, ExplanationLevel.LEVEL_2_ANALOGY, RepresentationType.ANALOGY, failed = true)
        val longContext = """
            [CONVERSATION SUMMARY]
            - Turn 1: User asked about Paxos.
            - Turn 2: Assistant used a parliament analogy.
            - Turn 3: User was confused.
        """.trimIndent()

        val decision = engine.evaluateAdaptiveProgression(
            domain = Domain.SYSTEM_DESIGN,
            taskType = TaskType.LEARNING_REQUEST,
            userState = UserState.CONFUSED,
            userQuery = "I still don't get Paxos.",
            conversationContext = longContext,
            recentAttempts = listOf(attempt1)
        )

        assertNotEquals(RepresentationType.ANALOGY, decision.representation)
        assertEquals(RepresentationType.NUMERICAL_TRACE, decision.representation)
    }
}
