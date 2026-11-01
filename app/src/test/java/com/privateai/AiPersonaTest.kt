package com.privateai

import com.privateai.persona.AiPersona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPersonaTest {

    @Test
    fun `all expected personas exist and have valid prompts`() {
        val personas = AiPersona.entries
        assertEquals(6, personas.size)

        for (persona in personas) {
            assertTrue("Persona ${persona.name} displayName is blank", persona.displayName.isNotBlank())
            assertTrue("Persona ${persona.name} description is blank", persona.description.isNotBlank())
            assertTrue("Persona ${persona.name} systemPrompt is blank", persona.systemPrompt.isNotBlank())
        }
    }

    @Test
    fun `dsa persona includes step by step algorithmic structure`() {
        val dsaPrompt = AiPersona.DSA.systemPrompt
        assertTrue(dsaPrompt.contains("Constraints"))
        assertTrue(dsaPrompt.contains("Brute Force"))
        assertTrue(dsaPrompt.contains("Optimal Approach"))
        assertTrue(dsaPrompt.contains("Complexity Analysis"))
        assertTrue(dsaPrompt.contains("Edge Cases"))
    }
}
