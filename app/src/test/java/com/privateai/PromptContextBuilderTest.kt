package com.privateai

import com.privateai.context.PromptContextBuilder
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PromptContextBuilderTest {

    private lateinit var memoryManager: MemoryManager
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var promptContextBuilder: PromptContextBuilder

    @Before
    fun setup() {
        memoryManager = mock()
        userProfileManager = mock()
        whenever(userProfileManager.getProfile()).thenReturn(UserProfile())
        whenever(userProfileManager.getRelevantBehaviorDirectives(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(emptyList())
        promptContextBuilder = PromptContextBuilder(memoryManager, userProfileManager)
    }

    @Test
    fun `TEST 1 - relevant PROJECT memory is retrieved for matching project query`() {
        val vivvaMemory = Memory(
            id = "proj-1",
            content = "User is building VIVVA on-device AI assistant with Kotlin and LiteRT-LM",
            type = MemoryType.PROJECT,
            tags = listOf("vivva", "android", "litert-lm")
        )
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(vivvaMemory))

        val result = promptContextBuilder.retrieveRelevantMemories("How should I structure my VIVVA inference pipeline?")
        assertEquals(1, result.size)
        assertEquals("proj-1", result[0].id)
    }

    @Test
    fun `TEST 2 - relevant TECH_STACK memory is retrieved for matching technology query`() {
        val techMemory = Memory(
            id = "tech-1",
            content = "User's application stack uses Kotlin Coroutines, Jetpack Compose, and LiteRT-LM",
            type = MemoryType.TECH_STACK,
            tags = listOf("kotlin", "coroutines", "compose")
        )
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(techMemory))

        val result = promptContextBuilder.retrieveRelevantMemories("How do I cancel a Kotlin coroutine job in Compose?")
        assertEquals(1, result.size)
        assertEquals("tech-1", result[0].id)
    }

    @Test
    fun `TEST 3 - irrelevant PROJECT memory is suppressed for unrelated query`() {
        val vivvaMemory = Memory(
            id = "proj-1",
            content = "User is building VIVVA on-device AI assistant",
            type = MemoryType.PROJECT,
            tags = listOf("vivva", "android")
        )
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(vivvaMemory))

        val result = promptContextBuilder.retrieveRelevantMemories("Explain quicksort algorithm and its time complexity")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `TEST 4 - simple factual query suppresses unrelated memories`() {
        val memories = listOf(
            Memory(id = "1", content = "User works on VIVVA", type = MemoryType.PROJECT, tags = listOf("vivva")),
            Memory(id = "2", content = "User uses Kotlin and Android", type = MemoryType.TECH_STACK, tags = listOf("kotlin", "android"))
        )
        whenever(memoryManager.getAllMemories()).thenReturn(memories)

        val result = promptContextBuilder.retrieveRelevantMemories("What is the capital of Japan?")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `TEST 5 - generic keyword overlap does not cause false retrieval`() {
        val genericMemory = Memory(
            id = "gen-1",
            content = "User wants to make good progress on software development",
            type = MemoryType.GOAL,
            tags = listOf("development")
        )
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(genericMemory))

        // "explain how to make a binary search tree" contains "make", but score is below MIN_MEMORY_RELEVANCE_SCORE
        val result = promptContextBuilder.retrieveRelevantMemories("Explain how to make a binary search tree")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `TEST 6 - preference memory is not emitted by MemoryManager as behavioral directive`() {
        val legacyPrefMemory = Memory(
            id = "legacy-pref",
            content = "User prefers step-by-step explanations",
            type = MemoryType.PREFERENCE,
            tags = listOf("learning")
        )
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(legacyPrefMemory))

        val result = promptContextBuilder.retrieveRelevantMemories("Explain dynamic programming step by step")
        // PREFERENCE is filtered out from MemoryManager retrieval
        assertTrue(result.isEmpty())
    }

    @Test
    fun `TEST 7 - UserProfileManager remains responsible for learning preferences`() {
        whenever(memoryManager.getAllMemories()).thenReturn(emptyList())
        whenever(userProfileManager.getRelevantBehaviorDirectives(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(listOf("[LEARNING STYLE] Prioritize intuitive mental models before formal definitions"))

        val result = promptContextBuilder.buildSystemInstruction("Explain Paxos consensus")
        assertTrue(result.systemInstruction.contains("[USER PREFERRED ASSISTANT BEHAVIOR]"))
        assertTrue(result.systemInstruction.contains("Prioritize intuitive mental models"))
    }

    @Test
    fun `TEST 8 - multiple relevant memories are ranked by relevance`() {
        val memPartial = Memory(
            id = "partial",
            content = "User uses Kotlin for backend services",
            type = MemoryType.TECH_STACK,
            tags = listOf("backend")
        )
        val memExact = Memory(
            id = "exact",
            content = "User builds Android app using Kotlin Coroutines and LiteRT-LM",
            type = MemoryType.TECH_STACK,
            tags = listOf("kotlin", "coroutines", "android", "litert-lm")
        )
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(memPartial, memExact))

        val result = promptContextBuilder.retrieveRelevantMemories("How to optimize Kotlin coroutines on Android with LiteRT-LM?")
        assertEquals(2, result.size)
        // memExact has higher relevance score due to multiple tag and keyword matches
        assertEquals("exact", result[0].id)
    }

    @Test
    fun `TEST 9 - memory prompt output never exceeds the 250-token budget`() {
        assertEquals(250, PromptContextBuilder.MAX_MEMORY_TOKENS)

        val longMemories = (1..10).map { i ->
            Memory(
                id = "mem-$i",
                content = "Project $i: Highly detailed engineering requirements and architecture specification for module $i with multiple components.",
                type = MemoryType.PROJECT,
                tags = listOf("architecture", "module")
            )
        }
        whenever(memoryManager.getAllMemories()).thenReturn(longMemories)

        val result = promptContextBuilder.retrieveRelevantMemories("Tell me about the architecture specification for module", limit = 10, maxTokenBudget = 250)
        assertTrue(result.isNotEmpty())
        assertTrue(result.size < 10) // Cut off by budget
    }

    @Test
    fun `TEST 10 - no-memory case produces absent memory section`() {
        whenever(memoryManager.getAllMemories()).thenReturn(emptyList())

        val result = promptContextBuilder.buildSystemInstruction("How does QuickSort work?", persona = AiPersona.DSA)
        assertFalse(result.systemInstruction.contains("[USER KNOWLEDGE & RELEVANT PROJECT CONTEXT]"))
        assertTrue(result.injectedMemories.isEmpty())
    }

    // ========== Adversarial Tests ==========

    @Test
    fun `ADVERSARIAL CASE A - general trivia suppresses unrelated user stack memory`() {
        val mem = Memory(id = "1", content = "User uses Kotlin", type = MemoryType.TECH_STACK, tags = listOf("kotlin"))
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(mem))

        val result = promptContextBuilder.retrieveRelevantMemories("Explain the history of the Roman Empire.")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `ADVERSARIAL CASE B - learning preference is handled by UserProfileManager not MemoryManager`() {
        val prefMem = Memory(
            id = "pref",
            content = "User prefers step-by-step explanations",
            type = MemoryType.PREFERENCE,
            tags = listOf("learning")
        )
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(prefMem))

        val result = promptContextBuilder.retrieveRelevantMemories("How does binary search work?")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `ADVERSARIAL CASE C - project and model match retrieves relevant memory`() {
        val mem = Memory(
            id = "vivva-gemma",
            content = "User's VIVVA project uses Gemma 2B and LiteRT-LM",
            type = MemoryType.PROJECT,
            tags = listOf("vivva", "gemma", "litert-lm", "android")
        )
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(mem))

        val result = promptContextBuilder.retrieveRelevantMemories("Why is my Android Gemma inference slow?")
        assertEquals(1, result.size)
        assertEquals("vivva-gemma", result[0].id)
    }

    @Test
    fun `ADVERSARIAL CASE D - Python project memory suppressed for Kotlin query`() {
        val pythonMem = Memory(
            id = "proj-alpha",
            content = "User built Project Alpha using Python and Django",
            type = MemoryType.PROJECT,
            tags = listOf("python", "django")
        )
        whenever(memoryManager.getAllMemories()).thenReturn(listOf(pythonMem))

        val result = promptContextBuilder.retrieveRelevantMemories("How does Kotlin null safety work?")
        assertTrue(result.isEmpty())
    }
}
