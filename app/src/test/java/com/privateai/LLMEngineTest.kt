package com.privateai

import com.privateai.llm.Backend
import com.privateai.llm.LLMConfig
import com.privateai.llm.LLMEngine
import com.privateai.llm.LLMState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for LLM configuration and state management.
 */
class LLMConfigTest {

    private lateinit var config: LLMConfig

    @Before
    fun setup() {
        config = LLMConfig(
            modelPath = "/data/local/tmp/gemma-4-E2B-it.litertlm",
            backend = Backend.GPU,
            maxTokens = 2048,
            temperature = 0.7,
            topK = 40,
            topP = 0.9,
            seed = 0L,
            cacheDir = "/tmp/cache",
            systemInstruction = "You are a helpful assistant."
        )
    }

    @Test
    fun `config has correct default values`() {
        val defaultConfig = LLMConfig(modelPath = "test.litertlm")
        assertEquals("test.litertlm", defaultConfig.modelPath)
        assertEquals(Backend.GPU, defaultConfig.backend)
        assertTrue(defaultConfig.allowFallback)
        assertEquals(2048, defaultConfig.maxTokens)
        assertEquals(0.7, defaultConfig.temperature, 0.001)
        assertEquals(40, defaultConfig.topK)
        assertEquals(0.9, defaultConfig.topP, 0.001)
        assertEquals(0L, defaultConfig.seed)
        assertEquals(null, defaultConfig.cacheDir)
        assertEquals(null, defaultConfig.systemInstruction)
    }

    @Test
    fun `config stores all values correctly`() {
        assertEquals("/data/local/tmp/gemma-4-E2B-it.litertlm", config.modelPath)
        assertEquals(Backend.GPU, config.backend)
        assertEquals(2048, config.maxTokens)
        assertEquals(0.7, config.temperature, 0.001)
        assertEquals(40, config.topK)
        assertEquals(0.9, config.topP, 0.001)
        assertEquals(0L, config.seed)
        assertEquals("/tmp/cache", config.cacheDir)
        assertEquals("You are a helpful assistant.", config.systemInstruction)
    }

    @Test
    fun `config data class equality works`() {
        val config1 = LLMConfig(modelPath = "test.litertlm")
        val config2 = LLMConfig(modelPath = "test.litertlm")
        assertEquals(config1, config2)
    }

    @Test
    fun `backend enum has correct values`() {
        assertEquals(3, Backend.values().size)
        assertTrue(Backend.values().contains(Backend.CPU))
        assertTrue(Backend.values().contains(Backend.GPU))
        assertTrue(Backend.values().contains(Backend.NPU))
    }
}

/**
 * Tests for LLM state management.
 */
class LLMStateTest {

    @Test
    fun `NotReady state is initial`() {
        val state = LLMState.NotReady
        assertTrue(state is LLMState.NotReady)
    }

    @Test
    fun `Loading state has progress`() {
        val state = LLMState.Loading(0.5f)
        assertTrue(state is LLMState.Loading)
        assertEquals(0.5f, (state as LLMState.Loading).progress)
    }

    @Test
    fun `Error state has message and optional cause`() {
        val cause = RuntimeException("test error")
        val state = LLMState.Error("Something went wrong", cause)
        assertTrue(state is LLMState.Error)
        assertEquals("Something went wrong", (state as LLMState.Error).message)
        assertEquals(cause, state.cause)
    }

    @Test
    fun `Ready state is standalone`() {
        val state = LLMState.Ready
        assertTrue(state is LLMState.Ready)
    }

    @Test
    fun `Generating state is standalone`() {
        val state = LLMState.Generating
        assertTrue(state is LLMState.Generating)
    }
}

/**
 * Mock LLM engine for testing.
 */
class MockLLMEngine(
    override var activeBackend: Backend? = Backend.GPU
) : LLMEngine {
    private val _state = MutableStateFlow<LLMState>(LLMState.NotReady)
    override val state: Flow<LLMState> = _state

    var initializeCalled = false
    var sendMessageCalled = false
    var lastMessage: String? = null
    var resetConversationCalled = false
    var closeCalled = false

    override suspend fun initialize(config: LLMConfig): Result<Unit> {
        initializeCalled = true
        activeBackend = config.backend
        _state.value = LLMState.Ready
        return Result.success(Unit)
    }

    override fun sendMessageStream(
        message: String,
        systemPrompt: String?
    ): Flow<String> {
        sendMessageCalled = true
        lastMessage = message
        return flowOf("Mock response to: ", message)
    }

    override suspend fun sendMessage(
        message: String,
        systemPrompt: String?
    ): Result<String> {
        sendMessageCalled = true
        lastMessage = message
        return Result.success("Mock response to: $message")
    }

    override fun resetConversation() {
        resetConversationCalled = true
    }

    override fun close() {
        closeCalled = true
        _state.value = LLMState.NotReady
    }
}

/**
 * Tests for the mock LLM engine.
 */
class MockLLMEngineTest {

    private lateinit var engine: MockLLMEngine

    @Before
    fun setup() {
        engine = MockLLMEngine()
    }

    @Test
    fun `initial state is NotReady`() = runBlocking {
        assertTrue(engine.state.first() is LLMState.NotReady)
    }

    @Test
    fun `initialize sets state to Ready`() = runBlocking {
        val config = LLMConfig(modelPath = "test.litertlm")
        val result = engine.initialize(config)
        assertTrue(result.isSuccess)
        assertTrue(engine.initializeCalled)
        assertTrue(engine.state.first() is LLMState.Ready)
    }

    @Test
    fun `sendMessage returns response`() = runBlocking {
        val result = engine.sendMessage("Hello")
        assertTrue(result.isSuccess)
        assertEquals("Mock response to: Hello", result.getOrNull())
        assertTrue(engine.sendMessageCalled)
        assertEquals("Hello", engine.lastMessage)
    }

    @Test
    fun `sendMessageStream returns flow`() = runBlocking {
        val messages = engine.sendMessageStream("Hello").toList()
        assertEquals(2, messages.size)
        assertEquals("Mock response to: ", messages[0])
        assertEquals("Hello", messages[1])
    }

    @Test
    fun `resetConversation is called`() {
        engine.resetConversation()
        assertTrue(engine.resetConversationCalled)
    }

    @Test
    fun `close is called`() {
        engine.close()
        assertTrue(engine.closeCalled)
    }
}
