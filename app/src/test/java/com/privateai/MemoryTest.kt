package com.privateai

import com.privateai.memory.Memory
import com.privateai.memory.MemoryType
import com.privateai.memory.isFactualContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for memory data models and factual memory types.
 */
class MemoryTest {

    @Test
    fun `memory stores all fields correctly`() {
        val memory = Memory(
            id = "test-id",
            content = "User is building VIVVA on-device AI",
            type = MemoryType.PROJECT,
            tags = listOf("vivva", "ai", "on-device"),
            createdAt = 1000L,
            updatedAt = 2000L
        )

        assertEquals("test-id", memory.id)
        assertEquals("User is building VIVVA on-device AI", memory.content)
        assertEquals(MemoryType.PROJECT, memory.type)
        assertEquals(listOf("vivva", "ai", "on-device"), memory.tags)
        assertEquals(1000L, memory.createdAt)
        assertEquals(2000L, memory.updatedAt)
    }

    @Test
    fun `memory equality works`() {
        val memory1 = Memory("id1", "content", MemoryType.USER_FACT, emptyList(), 100L, 200L)
        val memory2 = Memory("id1", "content", MemoryType.USER_FACT, emptyList(), 100L, 200L)
        assertEquals(memory1, memory2)
    }

    @Test
    fun `memory with different id is not equal`() {
        val memory1 = Memory("id1", "content", MemoryType.USER_FACT, emptyList(), 100L, 200L)
        val memory2 = Memory("id2", "content", MemoryType.USER_FACT, emptyList(), 100L, 200L)
        assertNotEquals(memory1, memory2)
    }

    @Test
    fun `memory type enum has correct factual and legacy values`() {
        assertTrue(MemoryType.USER_FACT.isFactualContext)
        assertTrue(MemoryType.PROJECT.isFactualContext)
        assertTrue(MemoryType.TECH_STACK.isFactualContext)
        assertTrue(MemoryType.GOAL.isFactualContext)
        assertTrue(MemoryType.FACT.isFactualContext)
        assertEquals(MemoryType.PREFERENCE, MemoryType.valueOf("PREFERENCE"))
    }

    @Test
    fun `memory can have empty tags`() {
        val memory = Memory("id", "content", MemoryType.USER_FACT, emptyList(), 0L, 0L)
        assertEquals(emptyList<String>(), memory.tags)
    }

    @Test
    fun `memory can have multiple tags`() {
        val tags = listOf("tag1", "tag2", "tag3")
        val memory = Memory("id", "content", MemoryType.TECH_STACK, tags, 0L, 0L)
        assertEquals(3, memory.tags.size)
    }
}
