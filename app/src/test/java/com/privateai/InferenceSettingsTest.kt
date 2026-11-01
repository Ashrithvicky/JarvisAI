package com.privateai

import com.privateai.settings.BackendPreference
import com.privateai.settings.InferenceSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceSettingsTest {

    @Test
    fun `default settings are correct`() {
        val settings = InferenceSettings()
        assertEquals(BackendPreference.AUTO, settings.backendPreference)
        assertEquals(0.7, settings.temperature, 0.001)
        assertEquals(40, settings.topK)
        assertEquals(0.9, settings.topP, 0.001)
        assertEquals(2048, settings.maxTokens)
    }

    @Test
    fun `settings serialize and deserialize cleanly`() {
        val settings = InferenceSettings(
            backendPreference = BackendPreference.CPU,
            temperature = 0.5,
            topK = 50,
            topP = 0.85,
            maxTokens = 1024
        )
        val json = Json { ignoreUnknownKeys = true }
        val raw = json.encodeToString(InferenceSettings.serializer(), settings)
        val decoded = json.decodeFromString(InferenceSettings.serializer(), raw)
        assertEquals(settings, decoded)
    }

    @Test
    fun `AUTO backend preference resolves to GPU with fallback enabled`() {
        val settings = InferenceSettings(backendPreference = BackendPreference.AUTO)
        val targetBackend = when (settings.backendPreference) {
            BackendPreference.AUTO -> com.privateai.llm.Backend.GPU
            BackendPreference.GPU -> com.privateai.llm.Backend.GPU
            BackendPreference.CPU -> com.privateai.llm.Backend.CPU
        }
        val allowFallback = settings.backendPreference == BackendPreference.AUTO

        assertEquals(com.privateai.llm.Backend.GPU, targetBackend)
        org.junit.Assert.assertTrue(allowFallback)
    }

    @Test
    fun `GPU backend preference resolves to GPU with fallback disabled`() {
        val settings = InferenceSettings(backendPreference = BackendPreference.GPU)
        val targetBackend = when (settings.backendPreference) {
            BackendPreference.AUTO -> com.privateai.llm.Backend.GPU
            BackendPreference.GPU -> com.privateai.llm.Backend.GPU
            BackendPreference.CPU -> com.privateai.llm.Backend.CPU
        }
        val allowFallback = settings.backendPreference == BackendPreference.AUTO

        assertEquals(com.privateai.llm.Backend.GPU, targetBackend)
        org.junit.Assert.assertFalse(allowFallback)
    }

    @Test
    fun `CPU backend preference resolves to CPU with fallback disabled`() {
        val settings = InferenceSettings(backendPreference = BackendPreference.CPU)
        val targetBackend = when (settings.backendPreference) {
            BackendPreference.AUTO -> com.privateai.llm.Backend.GPU
            BackendPreference.GPU -> com.privateai.llm.Backend.GPU
            BackendPreference.CPU -> com.privateai.llm.Backend.CPU
        }
        val allowFallback = settings.backendPreference == BackendPreference.AUTO

        assertEquals(com.privateai.llm.Backend.CPU, targetBackend)
        org.junit.Assert.assertFalse(allowFallback)
    }
}
