package com.privateai.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryExtractorTest {

    @Test
    fun `extracts a goal`() {
        val result = MemoryExtractor.extract("I want to crack the Google interview this year.")
        assertEquals(MemoryType.GOAL, result.single().type)
        assertTrue(result.single().content.contains("Google interview"))
    }

    @Test
    fun `extracts a project and a fact`() {
        val types = MemoryExtractor.extract("My name is Vignesh and I'm building a private AI app.").map { it.type }
        assertTrue(MemoryType.PROJECT in types)
        assertTrue(MemoryType.USER_FACT in types)
    }

    @Test
    fun `ignores small talk`() {
        assertTrue(MemoryExtractor.extract("What is a linked list?").isEmpty())
    }
}
