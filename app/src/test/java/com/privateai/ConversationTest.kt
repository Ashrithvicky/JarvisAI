package com.privateai

import com.privateai.conversation.Message
import com.privateai.conversation.MessageRole
import com.privateai.ui.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for conversation data models.
 */
class ConversationTest {

    @Test
    fun `message has correct role`() {
        val userMessage = Message(
            id = "1",
            role = MessageRole.USER,
            content = "Hello",
            timestamp = System.currentTimeMillis()
        )
        assertEquals(MessageRole.USER, userMessage.role)

        val assistantMessage = Message(
            id = "2",
            role = MessageRole.ASSISTANT,
            content = "Hi there!",
            timestamp = System.currentTimeMillis()
        )
        assertEquals(MessageRole.ASSISTANT, assistantMessage.role)

        val systemMessage = Message(
            id = "3",
            role = MessageRole.SYSTEM,
            content = "System instruction",
            timestamp = System.currentTimeMillis()
        )
        assertEquals(MessageRole.SYSTEM, systemMessage.role)
    }

    @Test
    fun `message equality works`() {
        val timestamp = System.currentTimeMillis()
        val message1 = Message("1", MessageRole.USER, "Hello", timestamp)
        val message2 = Message("1", MessageRole.USER, "Hello", timestamp)
        assertEquals(message1, message2)
    }

    @Test
    fun `message with different content is not equal`() {
        val timestamp = System.currentTimeMillis()
        val message1 = Message("1", MessageRole.USER, "Hello", timestamp)
        val message2 = Message("1", MessageRole.USER, "Hi", timestamp)
        assertNotEquals(message1, message2)
    }

    @Test
    fun `message role enum has correct values`() {
        assertEquals(3, MessageRole.values().size)
        assertEquals(MessageRole.USER, MessageRole.valueOf("USER"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.valueOf("ASSISTANT"))
        assertEquals(MessageRole.SYSTEM, MessageRole.valueOf("SYSTEM"))
    }

    @Test
    fun `chat message data class works`() {
        val message = ChatMessage(
            content = "Test message",
            isUser = true,
            timestamp = 1234567890L
        )
        assertEquals("Test message", message.content)
        assertEquals(true, message.isUser)
        assertEquals(1234567890L, message.timestamp)
    }

    @Test
    fun `chat message default timestamp is set`() {
        val before = System.currentTimeMillis()
        val message = ChatMessage(content = "Test", isUser = false)
        val after = System.currentTimeMillis()
        assert(message.timestamp in before..after)
    }

    @Test
    fun `new conversation initializes with clean state`() {
        val conversation = com.privateai.conversation.Conversation(
            id = "conv-new-123",
            title = "New Chat",
            createdAt = 1000L,
            updatedAt = 1000L,
            messages = emptyList()
        )
        assertEquals("conv-new-123", conversation.id)
        assertEquals("New Chat", conversation.title)
        assertEquals(0, conversation.messages.size)
        assertEquals(false, conversation.isCustomTitle)
    }

    @Test
    fun `conversations remain isolated when messages are added`() {
        val conv1 = com.privateai.conversation.Conversation(
            id = "conv-1",
            title = "First Chat",
            createdAt = 1000L,
            updatedAt = 1000L,
            messages = listOf(
                Message("m1", MessageRole.USER, "Hello in 1", 1001L),
                Message("m2", MessageRole.ASSISTANT, "Response in 1", 1002L)
            )
        )

        val conv2 = com.privateai.conversation.Conversation(
            id = "conv-2",
            title = "New Chat",
            createdAt = 2000L,
            updatedAt = 2000L,
            messages = emptyList()
        )

        assertEquals(2, conv1.messages.size)
        assertEquals(0, conv2.messages.size)
        assertNotEquals(conv1.id, conv2.id)
    }
}
