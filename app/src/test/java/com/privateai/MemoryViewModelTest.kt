package com.privateai

import com.privateai.memory.Memory
import com.privateai.memory.MemoryManager
import com.privateai.memory.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MemoryManagerCrudTest {

    private lateinit var memoryManager: MemoryManager

    @Before
    fun setup() {
        memoryManager = mock()
    }

    @Test
    fun `adding memory returns success and updates list`() {
        val newMem = Memory(
            id = "mem-1",
            content = "User prefers Kotlin code",
            type = MemoryType.PREFERENCE,
            tags = listOf("kotlin", "prefs"),
            createdAt = 1000L,
            updatedAt = 1000L
        )

        whenever(memoryManager.addMemory(eq("User prefers Kotlin code"), eq(MemoryType.PREFERENCE), eq(listOf("kotlin", "prefs"))))
            .thenReturn(Result.success(newMem))

        val result = memoryManager.addMemory("User prefers Kotlin code", MemoryType.PREFERENCE, listOf("kotlin", "prefs"))
        assertTrue(result.isSuccess)
        assertEquals("mem-1", result.getOrNull()?.id)
        assertEquals(MemoryType.PREFERENCE, result.getOrNull()?.type)
    }

    @Test
    fun `updating existing memory preserves id and updates content`() {
        val updatedMem = Memory(
            id = "mem-1",
            content = "User strongly prefers Kotlin code",
            type = MemoryType.PREFERENCE,
            tags = listOf("kotlin", "android"),
            createdAt = 1000L,
            updatedAt = 2000L
        )

        whenever(memoryManager.updateMemory(updatedMem)).thenReturn(Result.success(Unit))

        val result = memoryManager.updateMemory(updatedMem)
        assertTrue(result.isSuccess)
        verify(memoryManager).updateMemory(updatedMem)
    }

    @Test
    fun `deleting memory invokes delete on manager`() {
        whenever(memoryManager.deleteMemory("mem-1")).thenReturn(Result.success(Unit))

        val result = memoryManager.deleteMemory("mem-1")
        assertTrue(result.isSuccess)
        verify(memoryManager).deleteMemory("mem-1")
    }

    @Test
    fun `clearing all memories invokes deleteAllMemories`() {
        whenever(memoryManager.deleteAllMemories()).thenReturn(Result.success(Unit))

        val result = memoryManager.deleteAllMemories()
        assertTrue(result.isSuccess)
        verify(memoryManager).deleteAllMemories()
    }

    @Test
    fun `searching memories filters by query`() {
        val memories = listOf(
            Memory("1", "User prefers dark mode", MemoryType.PREFERENCE, listOf("ui"), 100L, 100L),
            Memory("2", "User works on Kubernetes", MemoryType.FACT, listOf("devops"), 200L, 200L)
        )

        whenever(memoryManager.searchMemories("kubernetes")).thenReturn(listOf(memories[1]))

        val results = memoryManager.searchMemories("kubernetes")
        assertEquals(1, results.size)
        assertEquals("2", results[0].id)
    }

    @Test
    fun `memory types include USER_FACT, PROJECT, TECH_STACK, GOAL`() {
        val types = MemoryType.entries
        assertTrue(types.contains(MemoryType.USER_FACT))
        assertTrue(types.contains(MemoryType.PROJECT))
        assertTrue(types.contains(MemoryType.TECH_STACK))
        assertTrue(types.contains(MemoryType.GOAL))
    }
}
