package com.privateai.intelligence

import com.privateai.persona.AiPersona
import com.privateai.profile.BehaviorPreference
import com.privateai.profile.PreferenceCategory
import com.privateai.profile.UserProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VivvaBehavioralEvaluationTest {

    private lateinit var userStateDetector: UserStateDetector
    private lateinit var explanationEngine: AdaptiveExplanationEngine
    private lateinit var strategyResolver: StrategyResolver
    private lateinit var domainDetector: DomainDetector
    private lateinit var taskClassifier: TaskClassifier

    @Before
    fun setup() {
        userStateDetector = UserStateDetector()
        explanationEngine = AdaptiveExplanationEngine()
        domainDetector = DomainDetector()
        taskClassifier = TaskClassifier()
        strategyResolver = StrategyResolver(
            domainDetector = domainDetector,
            taskClassifier = taskClassifier,
            userStateDetector = userStateDetector,
            explanationEngine = explanationEngine
        )
    }

    @Test
    fun `eval 1 - user state detection accuracy across cognitive states`() {
        assertEquals(UserState.CONFUSED, userStateDetector.detectUserState("I don't understand what search space means"))
        assertEquals(UserState.CONFUSED, userStateDetector.detectUserState("No, that's not what I meant"))
        assertEquals(UserState.DEEP_DIVE, userStateDetector.detectUserState("I already understand the basics. Go deeper into memory layout."))
        assertEquals(UserState.DEBUGGING, userStateDetector.detectUserState("Why is my app crashing with NullPointerException?"))
        assertEquals(UserState.IMPLEMENTING, userStateDetector.detectUserState("Write the code in Kotlin"))
        assertEquals(UserState.VERIFYING, userStateDetector.detectUserState("Is my approach right? I'm using two pointers."))
        assertEquals(UserState.COMPARING, userStateDetector.detectUserState("What is the difference between Docker and Kubernetes?"))
        assertEquals(UserState.PLANNING, userStateDetector.detectUserState("Give me a study plan for system design"))
        assertEquals(UserState.FRUSTRATED, userStateDetector.detectUserState("Still failing after 10 tries, this is driving me crazy!"))
        // Conservative check: standard error query should NOT be classified as frustrated
        assertNotEquals(UserState.FRUSTRATED, userStateDetector.detectUserState("Why is this query failing with syntax error?"))
    }

    @Test
    fun `eval 2 - adaptive explanation level selection`() {
        // Direct for factual
        val levelFactual = explanationEngine.selectExplanationLevel(
            domain = Domain.GENERAL,
            taskType = TaskType.FACTUAL_QUESTION,
            userState = UserState.GENERAL_CONVERSATION
        )
        assertEquals(ExplanationLevel.LEVEL_1_DIRECT, levelFactual)

        // Analogy for new concepts
        val levelNewConcept = explanationEngine.selectExplanationLevel(
            domain = Domain.GENERAL,
            taskType = TaskType.LEARNING_REQUEST,
            userState = UserState.NEW_CONCEPT
        )
        assertEquals(ExplanationLevel.LEVEL_2_ANALOGY, levelNewConcept)

        // Advanced for deep dives
        val levelDeepDive = explanationEngine.selectExplanationLevel(
            domain = Domain.SYSTEM_DESIGN,
            taskType = TaskType.SYSTEM_DESIGN,
            userState = UserState.DEEP_DIVE
        )
        assertEquals(ExplanationLevel.LEVEL_5_ADVANCED, levelDeepDive)
    }

    @Test
    fun `eval 3 - confusion recovery escalates to micro-step simulation with non-repetition`() {
        // First confusion: Level 2 Analogy
        val strategy1 = strategyResolver.resolveStrategy(
            userQuery = "I don't understand how binary search works",
            conversationContext = null
        )
        assertTrue(strategy1.isSimplifiedTeaching)
        assertEquals(UserState.CONFUSED, strategy1.userState)
        assertEquals(ExplanationLevel.LEVEL_2_ANALOGY, strategy1.explanationLevel)
        assertTrue(strategy1.systemPrompt.contains("NON-REPETITION"))

        // Second confusion after previous analogy: Escalate to Level 3 Micro-step
        val strategy2 = strategyResolver.resolveStrategy(
            userQuery = "I still don't understand it",
            conversationContext = "Assistant: Think of a dictionary as an analogy for binary search."
        )
        assertTrue(strategy2.isSimplifiedTeaching)
        assertEquals(UserState.CONFUSED, strategy2.userState)
        assertEquals(ExplanationLevel.LEVEL_3_MICRO_STEP, strategy2.explanationLevel)
        assertTrue(strategy2.systemPrompt.contains("NON-REPETITION"))
    }

    @Test
    fun `eval 4 - seamless cross-domain transitions in a single chat`() {
        // Turn 1: DSA
        val turn1 = strategyResolver.resolveStrategy("Explain binary search")
        assertEquals(Domain.DSA, turn1.domain)

        // Turn 2: Coding Implementation
        val turn2 = strategyResolver.resolveStrategy(
            userQuery = "Okay, now show Kotlin code for this",
            conversationContext = "User: Explain binary search\nAssistant: Binary search divides sorted array in half."
        )
        assertEquals(Domain.CODING, turn2.domain)
        assertEquals(UserState.IMPLEMENTING, turn2.userState)

        // Turn 3: DevOps Transition
        val turn3 = strategyResolver.resolveStrategy(
            userQuery = "How do I deploy this service with Docker?",
            conversationContext = "User: Show Kotlin code\nAssistant: Here is the code."
        )
        assertEquals(Domain.DEVOPS, turn3.domain)
    }

    @Test
    fun `eval 5 - explicit preferences override inferred preferences`() {
        val explicitPref = BehaviorPreference(
            category = PreferenceCategory.EXPLANATION_DEPTH,
            description = "Always provide concise, direct explanations",
            confidence = 1.0f,
            isExplicit = true
        )
        val inferredPref = BehaviorPreference(
            category = PreferenceCategory.EXPLANATION_DEPTH,
            description = "Provide detailed step-by-step traces",
            confidence = 0.6f,
            isExplicit = false
        )
        val profile = UserProfile(
            explicitPreferences = listOf(explicitPref),
            inferredPreferences = listOf(inferredPref)
        )

        val level = explanationEngine.selectExplanationLevel(
            domain = Domain.GENERAL,
            taskType = TaskType.GENERAL_CONVERSATION,
            userState = UserState.LEARNING,
            userProfile = profile
        )
        assertEquals(ExplanationLevel.LEVEL_1_DIRECT, level)
    }

    @Test
    fun `eval 6 - anti-pattern test - factual query does not receive bloated algorithm tutorial`() {
        val strategy = strategyResolver.resolveStrategy("What is the capital of Japan?")
        assertEquals(Domain.GENERAL, strategy.domain)
        assertEquals(TaskType.FACTUAL_QUESTION, strategy.taskType)
        assertEquals(ExplanationLevel.LEVEL_1_DIRECT, strategy.explanationLevel)
        assertEquals("DIRECT_FACTUAL_ANSWER", strategy.strategyName)
        assertFalse(strategy.systemPrompt.contains("Big-O"))
    }

    @Test
    fun `eval 7 - anti-pattern test - debugging delivers root-cause diagnostics not generic advice`() {
        val strategy = strategyResolver.resolveStrategy("NullPointerException in UserViewModel.kt line 42")
        assertEquals(Domain.CODING, strategy.domain)
        assertEquals(TaskType.DEBUGGING, strategy.taskType)
        assertEquals(UserState.DEBUGGING, strategy.userState)
        assertEquals(ExplanationLevel.LEVEL_1_DIRECT, strategy.explanationLevel)
        assertTrue(strategy.systemPrompt.contains("root cause"))
    }

    @Test
    fun `eval 8 - anti-pattern test - false premise verification is universally enforced`() {
        val strategy = strategyResolver.resolveStrategy("Since O(1) space always means O(1) time, should we always use it?")
        assertEquals(Domain.DSA, strategy.domain)
        assertEquals(ExplanationLevel.LEVEL_4_FORMAL_TECHNICAL, strategy.explanationLevel)
    }

    @Test
    fun `eval 9 - anti-pattern test - struggled concept avoids repeating previous failed level`() {
        val struggled = com.privateai.profile.StruggledConcept(
            concept = "recursion",
            domain = "DSA",
            failedAttempts = 2,
            lastExplanationLevel = "LEVEL_2_ANALOGY"
        )
        val profile = UserProfile(
            struggledConcepts = listOf(struggled)
        )
        val level = explanationEngine.selectExplanationLevel(
            domain = Domain.DSA,
            taskType = TaskType.DSA_EXPLANATION,
            userState = UserState.CONFUSED,
            userProfile = profile,
            conversationContext = "Assistant: Recursion is like Russian nesting dolls."
        )
        // Escalates past Level 2 Analogy to Level 3 Micro-step
        assertEquals(ExplanationLevel.LEVEL_3_MICRO_STEP, level)
    }
}
