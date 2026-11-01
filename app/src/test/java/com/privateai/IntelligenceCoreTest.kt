package com.privateai

import com.privateai.context.PromptContextBuilder
import com.privateai.intelligence.Domain
import com.privateai.intelligence.DomainDetector
import com.privateai.intelligence.StrategyResolver
import com.privateai.intelligence.TaskClassifier
import com.privateai.intelligence.TaskType
import com.privateai.memory.Memory
import com.privateai.memory.MemoryManager
import com.privateai.memory.MemoryType
import com.privateai.profile.UserProfileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class IntelligenceCoreTest {

    private lateinit var domainDetector: DomainDetector
    private lateinit var taskClassifier: TaskClassifier
    private lateinit var strategyResolver: StrategyResolver
    private lateinit var memoryManager: MemoryManager
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var promptContextBuilder: PromptContextBuilder

    @Before
    fun setup() {
        domainDetector = DomainDetector()
        taskClassifier = TaskClassifier()
        strategyResolver = StrategyResolver(domainDetector, taskClassifier)
        memoryManager = mock()
        userProfileManager = mock()
        promptContextBuilder = PromptContextBuilder(
            memoryManager = memoryManager,
            userProfileManager = userProfileManager,
            strategyResolver = strategyResolver
        )
    }

    // 1. Binary search question -> DSA strategy
    @Test
    fun `binary search question maps to DSA domain and DSA_EXPLANATION task`() {
        val query = "Why does binary search work?"
        val domain = domainDetector.detectDomain(query)
        val task = taskClassifier.classifyTask(query, domain)
        val strategy = strategyResolver.resolveStrategy(query)

        assertEquals(Domain.DSA, domain)
        assertEquals(TaskType.DSA_EXPLANATION, task)
        assertEquals("DSA_EXPLANATION", strategy.strategyName)
        assertTrue(strategy.systemPrompt.contains("[DSA & ALGORITHM REASONING DIRECTIVE]"))
    }

    // 2. Docker failure -> DevOps troubleshooting strategy
    @Test
    fun `docker container exit maps to DEVOPS domain and DEVOPS_TROUBLESHOOTING task`() {
        val query = "Why is my Docker container exiting immediately with code 0?"
        val domain = domainDetector.detectDomain(query)
        val task = taskClassifier.classifyTask(query, domain)
        val strategy = strategyResolver.resolveStrategy(query)

        assertEquals(Domain.DEVOPS, domain)
        assertEquals(TaskType.DEVOPS_TROUBLESHOOTING, task)
        assertEquals("DEVOPS_TROUBLESHOOTING", strategy.strategyName)
        assertTrue(strategy.systemPrompt.contains("[DEVOPS & CLOUD INFRASTRUCTURE DIRECTIVE]"))
    }

    // 3. Food delivery architecture -> System Design strategy
    @Test
    fun `food delivery architecture maps to SYSTEM_DESIGN domain and task`() {
        val query = "Design an online food delivery system like DoorDash."
        val domain = domainDetector.detectDomain(query)
        val task = taskClassifier.classifyTask(query, domain)
        val strategy = strategyResolver.resolveStrategy(query)

        assertEquals(Domain.SYSTEM_DESIGN, domain)
        assertEquals(TaskType.SYSTEM_DESIGN, task)
        assertEquals("SYSTEM_DESIGN_TEACHING", strategy.strategyName)
        assertTrue(strategy.systemPrompt.contains("[DISTRIBUTED SYSTEM DESIGN DIRECTIVE]"))
    }

    // 4. NullPointerException -> Coding/troubleshooting debugging strategy
    @Test
    fun `null pointer exception maps to CODING domain and DEBUGGING task`() {
        val query = "Why am I getting a NullPointerException when accessing user.profile.name in Kotlin?"
        val domain = domainDetector.detectDomain(query)
        val task = taskClassifier.classifyTask(query, domain)
        val strategy = strategyResolver.resolveStrategy(query)

        assertEquals(Domain.CODING, domain)
        assertEquals(TaskType.DEBUGGING, task)
        assertEquals("CODE_DEBUGGING", strategy.strategyName)
        assertTrue(strategy.systemPrompt.contains("[SOFTWARE ENGINEERING & CODE QUALITY DIRECTIVE]"))
    }

    // 5. Simple factual question -> General strategy
    @Test
    fun `capital of Japan maps to GENERAL domain and FACTUAL_QUESTION task`() {
        val query = "What's the capital of Japan?"
        val domain = domainDetector.detectDomain(query)
        val task = taskClassifier.classifyTask(query, domain)
        val strategy = strategyResolver.resolveStrategy(query)

        assertEquals(Domain.GENERAL, domain)
        assertEquals(TaskType.FACTUAL_QUESTION, task)
        assertEquals("DIRECT_FACTUAL_ANSWER", strategy.strategyName)
        assertTrue(strategy.systemPrompt.contains("[DIRECT & APPROACHABLE ASSISTANT DIRECTIVE]"))
    }

    // 6. "What is Kubernetes?" -> DevOps concept strategy
    @Test
    fun `what is kubernetes maps to DEVOPS domain and CONCEPT_EXPLANATION task`() {
        val query = "What is Kubernetes?"
        val domain = domainDetector.detectDomain(query)
        val task = taskClassifier.classifyTask(query, domain)
        val strategy = strategyResolver.resolveStrategy(query)

        assertEquals(Domain.DEVOPS, domain)
        assertEquals(TaskType.CONCEPT_EXPLANATION, task)
        assertEquals("DEVOPS_CONFIGURATION", strategy.strategyName)
        assertTrue(strategy.systemPrompt.contains("[DEVOPS & CLOUD INFRASTRUCTURE DIRECTIVE]"))
    }

    // 7 & 8. Universal Premise Verification is always present
    @Test
    fun `premise verification directive is universally present in assembled context`() {
        val dsaQuery = "How do I implement binary search?"
        val context = promptContextBuilder.buildSystemInstruction(dsaQuery)

        assertTrue(context.systemInstruction.contains("[CRITICAL EVALUATION & PREMISE VERIFICATION]"))
        assertTrue(context.systemInstruction.contains("Independently verify the user's factual, mathematical, programming, or algorithmic premises"))
        assertNotNull(context.resolvedStrategy)
        assertEquals(Domain.DSA, context.resolvedStrategy?.domain)
    }

    // 9 & 10. Topic-aware profile preferences
    @Test
    fun `relevant profile directives are injected into prompt and topic filtered`() {
        val dsaQuery = "Solve two sum in Python"
        whenever(userProfileManager.getRelevantBehaviorDirectives(dsaQuery)).thenReturn(
            listOf("User prefers step-by-step mathematical intuition before code.")
        )

        val context = promptContextBuilder.buildSystemInstruction(dsaQuery)
        assertTrue(context.systemInstruction.contains("[USER PREFERRED ASSISTANT BEHAVIOR]"))
        assertTrue(context.systemInstruction.contains("User prefers step-by-step mathematical intuition before code."))
    }

    // 11. Conversational misunderstanding triggers Adaptive Simplified Teaching
    @Test
    fun `conversational confusion triggers adaptive simplified teaching strategy`() {
        val followUpQuery = "I don't understand what search space means."
        val conversationHistory = "Assistant: Binary search cuts the search space in half at every step."

        val strategy = strategyResolver.resolveStrategy(followUpQuery, conversationHistory)
        assertEquals(TaskType.LEARNING_REQUEST, strategy.taskType)
        assertTrue(strategy.isSimplifiedTeaching)
        assertEquals("ADAPTIVE_SIMPLIFIED_TEACHING", strategy.strategyName)
        assertTrue(strategy.systemPrompt.contains("[ADAPTIVE SIMPLIFIED TEACHING DIRECTIVE]"))
        assertTrue(strategy.systemPrompt.contains("Use a clear, intuitive real-world analogy"))
    }

    // 12. Memory integration
    @Test
    fun `relevant memories are retrieved and injected`() {
        val query = "Write a Python web scraper"
        val memory = Memory(
            id = "mem1",
            content = "User stack uses BeautifulSoup and requests library for scraping",
            type = MemoryType.TECH_STACK,
            tags = listOf("python", "web"),
            createdAt = 1000L,
            updatedAt = 1000L
        )
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(memory))

        val context = promptContextBuilder.buildSystemInstruction(query)
        assertTrue(context.systemInstruction.contains("[USER KNOWLEDGE & RELEVANT PROJECT CONTEXT]"))
        assertTrue(context.systemInstruction.contains("User stack uses BeautifulSoup and requests library"))
        assertEquals(1, context.injectedMemories.size)
    }
}
