package com.privateai.llm

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction layer for local LLM inference.
 *
 * This interface decouples the UI and business logic from the specific
 * inference runtime (LiteRT-LM, llama.cpp, etc.). The implementation
 * handles model loading, conversation management, and token generation.
 *
 * SECURITY: All inference happens on-device. No network calls.
 */
interface LLMEngine {

    /** Current state of the engine */
    val state: Flow<LLMState>

    /** The actual successfully initialized hardware backend, or null if not ready */
    val activeBackend: Backend?

    /**
     * Initialize the engine with the given configuration.
     * This loads the model into memory and may take several seconds.
     * Must be called on a background thread.
     */
    suspend fun initialize(config: LLMConfig): Result<Unit>

    /**
     * Send a message and receive a streaming response.
     * Each emission is a partial response token.
     */
    fun sendMessageStream(
        message: String,
        systemPrompt: String? = null
    ): Flow<String>

    /**
     * Send a full turn (system prompt, recent history, media) and stream the reply.
     * Engines without native support fall back to a text-only stream.
     */
    fun sendTurnStream(turn: ChatTurn): Flow<String> =
        sendMessageStream(turn.message, turn.systemPrompt)

    /**
     * Send a message and receive a complete response.
     * Blocks until the full response is generated.
     */
    suspend fun sendMessage(
        message: String,
        systemPrompt: String? = null
    ): Result<String>

    /**
     * Reset the conversation context while keeping the model loaded.
     */
    fun resetConversation()

    /**
     * Release all resources. The engine cannot be used after this call.
     */
    fun close()
}

enum class MediaKind { IMAGE, AUDIO }

class MediaPart(val kind: MediaKind, val bytes: ByteArray)

class HistoryItem(val isUser: Boolean, val text: String)

/** One user turn plus the context the model needs to answer it. */
class ChatTurn(
    val message: String,
    val systemPrompt: String?,
    val history: List<HistoryItem> = emptyList(),
    val media: List<MediaPart> = emptyList()
)

/**
 * Represents the current state of the LLM engine.
 */
sealed class LLMState {
    /** Engine is not initialized */
    data object NotReady : LLMState()

    /** Engine is loading the model */
    data class Loading(val progress: Float = 0f) : LLMState()

    /** Engine is ready for inference */
    data object Ready : LLMState()

    /** Engine encountered an error */
    data class Error(val message: String, val cause: Throwable? = null) : LLMState()

    /** Engine is generating a response */
    data object Generating : LLMState()
}

/**
 * Configuration for the LLM engine.
 */
data class LLMConfig(
    val modelPath: String,
    val backend: Backend = Backend.GPU,
    val allowFallback: Boolean = true,
    val maxTokens: Int = 2048,
    val temperature: Double = 0.7,
    val topK: Int = 40,
    val topP: Double = 0.9,
    val seed: Long = 0L,
    val cacheDir: String? = null,
    val systemInstruction: String? = null
)

/**
 * Hardware backend for inference.
 */
enum class Backend {
    CPU,
    GPU,
    NPU
}
