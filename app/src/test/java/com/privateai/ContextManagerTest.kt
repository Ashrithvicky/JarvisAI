package com.privateai

import com.privateai.context.ContextManager
import com.privateai.context.PromptContextBuilder
import com.privateai.conversation.Conversation
import com.privateai.conversation.Message
import com.privateai.conversation.MessageRole
import com.privateai.memory.MemoryManager
import com.privateai.persona.AiPersona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ContextManagerTest {

    private lateinit var memoryManager: MemoryManager
    private lateinit var promptContextBuilder: PromptContextBuilder
    private lateinit var contextManager: ContextManager

    @Before
    fun setup() {
        memoryManager = mock()
        promptContextBuilder = PromptContextBuilder(memoryManager)
        contextManager = ContextManager(memoryManager, userProfileManager = null, promptContextBuilder = promptContextBuilder)
    }

    @Test
    fun `estimateTokens handles empty and non-empty strings`() {
        assertEquals(0, contextManager.estimateTokens(""))
        val tokens = contextManager.estimateTokens("Hello, how are you?")
        assertTrue(tokens in 4..8)
    }

    @Test
    fun `assembleConversationContext for small conversation includes all messages without summary`() {
        whenever(memoryManager.getAllMemories()).thenReturn(emptyList())

        val conv = Conversation(
            id = "conv-1",
            title = "DSA Practice",
            createdAt = 1000L,
            updatedAt = 2000L,
            messages = listOf(
                Message("1", MessageRole.USER, "Explain binary search", 1000L),
                Message("2", MessageRole.ASSISTANT, "Binary search works on sorted arrays in O(log n)", 1500L)
            )
        )

        val result = contextManager.assembleConversationContext(
            conversation = conv,
            currentUserQuery = "What is the time complexity?",
            persona = AiPersona.DSA,
            maxContextTokens = 2048
        )

        assertEquals(2, result.recentMessages.size)
        assertEquals(null, result.summaryUsed)
        assertTrue(result.systemInstruction.contains("Competitive Programmer"))
        assertTrue(result.estimatedTotalTokens < 2048)
    }

    @Test
    fun `assembleConversationContext for long conversation generates rolling summary for older turns`() {
        whenever(memoryManager.getAllMemories()).thenReturn(emptyList())

        // Create 20 messages that will exceed a tight budget of 300 tokens
        val longMessages = (1..20).map { i ->
            Message(
                id = "msg-$i",
                role = if (i % 2 == 1) MessageRole.USER else MessageRole.ASSISTANT,
                content = "This is a detailed message number $i discussing algorithms and data structure problem solving in depth.",
                timestamp = i * 1000L
            )
        }

        val conv = Conversation(
            id = "conv-long",
            title = "Long DSA Chat",
            createdAt = 1000L,
            updatedAt = 50000L,
            messages = longMessages
        )

        val result = contextManager.assembleConversationContext(
            conversation = conv,
            currentUserQuery = "Now solve step 21",
            persona = AiPersona.DSA,
            maxContextTokens = 800
        )

        // Verbatim list is truncated to fit budget
        assertTrue(result.recentMessages.size < 20)
        // Summary was generated and added to system instructions
        assertNotNull(result.summaryUsed)
        assertTrue(result.systemInstruction.contains("[CONVERSATION SUMMARY OF EARLIER TURNS]"))
    }
}
