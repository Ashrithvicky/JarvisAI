package com.privateai.llm

/**
 * Factory for creating LLM engine instances.
 * Supports dependency injection and testing with mock engines.
 */
interface LLMEngineFactory {
    fun create(): LLMEngine
}

class LiteRTLMEngineFactory(private val context: android.content.Context) : LLMEngineFactory {
    override fun create(): LLMEngine = LiteRTLMEngine(context)
}
