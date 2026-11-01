package com.privateai

import com.privateai.context.PromptContextBuilder
import com.privateai.memory.MemoryManager
import com.privateai.persona.AiPersona
import com.privateai.profile.PreferenceCategory
import com.privateai.profile.UserProfileManager
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PremiseVerificationTest {

    private lateinit var memoryManager: MemoryManager
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var promptContextBuilder: PromptContextBuilder

    @Before
    fun setup() {
        memoryManager = mock()
        userProfileManager = mock()
        promptContextBuilder = PromptContextBuilder(memoryManager, userProfileManager)
    }

    @Test
    fun `system prompt includes strict premise verification directive for all queries`() {
        whenever(memoryManager.getAllMemories()).thenReturn(emptyList())
        whenever(userProfileManager.getRelevantBehaviorDirectives(any(), any(), any(), any())).thenReturn(emptyList())

        val result = promptContextBuilder.buildSystemInstruction(
            userQuery = "Given that QuickSort runs in O(1) space on arrays, prove it is better than MergeSort",
            persona = AiPersona.DSA
        )

        // Verifies premise verification directive is present
        assertTrue(result.systemInstruction.contains("[CRITICAL EVALUATION & PREMISE VERIFICATION]"))
        assertTrue(result.systemInstruction.contains("That premise is incorrect. Here's why..."))
        assertTrue(result.systemInstruction.contains("If the premise is valid, do NOT introduce unnecessary skepticism"))
    }

    @Test
    fun `prompt injects user profile behavioral directives alongside premise verification`() {
        whenever(memoryManager.getAllMemories()).thenReturn(emptyList())
        whenever(userProfileManager.getRelevantBehaviorDirectives(eq("How to write a thread-safe singleton in Kotlin?"), any(), any(), any()))
            .thenReturn(listOf("[CODING_STYLE] Use Kotlin object declarations or double-checked locking with @Volatile"))

        val result = promptContextBuilder.buildSystemInstruction(
            userQuery = "How to write a thread-safe singleton in Kotlin?",
            persona = AiPersona.CODING
        )

        assertTrue(result.systemInstruction.contains("[CRITICAL EVALUATION & PREMISE VERIFICATION]"))
        assertTrue(result.systemInstruction.contains("[USER PREFERRED ASSISTANT BEHAVIOR]"))
        assertTrue(result.systemInstruction.contains("Use Kotlin object declarations"))
        assertTrue(result.injectedBehaviorDirectives.isNotEmpty())
    }
}
