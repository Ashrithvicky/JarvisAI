package com.privateai.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend as LiteRTBackend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * LiteRT-LM implementation of [LLMEngine].
 *
 * Uses Google's LiteRT-LM runtime for on-device LLM inference.
 *
 * Supports CPU, GPU and NPU backends.
 *
 * SECURITY:
 * - Inference is performed on-device.
 * - This engine does not make network calls.
 */
class LiteRTLMEngine(
    private val context: Context
) : LLMEngine {

    companion object {
        private const val TAG = "LiteRTLMEngine"
        private const val MAX_IMAGES = 4
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    // Completed when the native generation of the previous turn has actually finished or been cancelled.
    @Volatile
    private var inFlight: CompletableDeferred<Unit>? = null
    private var _activeBackend: Backend? = null
    private var samplerConfig: SamplerConfig? = null
    private var maxOutputTokens: Int = 1024
    private var multimodal = false

    override val activeBackend: Backend?
        get() = _activeBackend

    private val _state = MutableStateFlow<LLMState>(
        LLMState.NotReady
    )

    override val state: StateFlow<LLMState> =
        _state.asStateFlow()

    override suspend fun initialize(
        config: LLMConfig
    ): Result<Unit> = withContext(Dispatchers.IO) {

        try {
            _state.value = LLMState.Loading(0f)
            _activeBackend = null

            var resolvedBackend = config.backend
            val initialBackend: LiteRTBackend = when (config.backend) {
                Backend.CPU -> LiteRTBackend.CPU()
                Backend.GPU -> LiteRTBackend.GPU()
                Backend.NPU -> LiteRTBackend.NPU(
                    nativeLibraryDir = context.applicationInfo.nativeLibraryDir
                )
            }

            val cacheDir = config.cacheDir ?: context.cacheDir.path
            var engineConfig = EngineConfig(
                modelPath = config.modelPath,
                backend = initialBackend,
                visionBackend = LiteRTBackend.GPU(),
                audioBackend = LiteRTBackend.CPU(),
                maxNumImages = MAX_IMAGES,
                cacheDir = cacheDir
            )

            Log.d(TAG, "Initializing LiteRT-LM engine (model=${config.modelPath}, requestedBackend=${config.backend}, allowFallback=${config.allowFallback})")

            var newEngine = Engine(engineConfig)

            try {
                try {
                    newEngine.initialize()
                    multimodal = true
                } catch (mm: Exception) {
                    // Model without vision/audio encoders: retry as text-only.
                    Log.w(TAG, "Multimodal init failed, retrying text-only", mm)
                    newEngine.close()
                    newEngine = Engine(
                        EngineConfig(modelPath = config.modelPath, backend = initialBackend, cacheDir = cacheDir)
                    )
                    newEngine.initialize()
                    multimodal = false
                }
                resolvedBackend = config.backend
                Log.i(TAG, "Backend ${config.backend} initialized successfully (multimodal=$multimodal)")
            } catch (e: Exception) {
                if (config.backend == Backend.GPU && config.allowFallback) {
                    Log.w(TAG, "GPU backend failed in AUTO mode, falling back to CPU", e)
                    resolvedBackend = Backend.CPU
                    engineConfig = EngineConfig(
                        modelPath = config.modelPath,
                        backend = LiteRTBackend.CPU(),
                        cacheDir = config.cacheDir ?: context.cacheDir.path
                    )
                    newEngine = Engine(engineConfig)
                    newEngine.initialize()
                    Log.i(TAG, "CPU fallback initialized successfully")
                } else {
                    Log.e(TAG, "Backend ${config.backend} initialization failed (allowFallback=${config.allowFallback})", e)
                    throw e
                }
            }

            engine = newEngine
            _activeBackend = resolvedBackend

            val samplerConfig = SamplerConfig(
                topK = config.topK,
                topP = config.topP,
                temperature = config.temperature,
                seed = config.seed.toInt()
            )

            this@LiteRTLMEngine.samplerConfig = samplerConfig
            this@LiteRTLMEngine.maxOutputTokens = config.maxTokens

            val conversationConfig = ConversationConfig(
                systemInstruction = config.systemInstruction?.let {
                    Contents.of(it)
                },
                samplerConfig = samplerConfig,
                maxOutputToken = config.maxTokens
            )

            conversation = newEngine.createConversation(conversationConfig)

            _state.value = LLMState.Ready

            Log.i(
                TAG,
                "Engine ready (actualBackend=$_activeBackend, topK=${config.topK}, topP=${config.topP}, temp=${config.temperature}, maxTokens=${config.maxTokens})"
            )

            Result.success(Unit)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to initialize LiteRT-LM engine",
                e
            )

            _activeBackend = null
            _state.value = LLMState.Error(
                e.message ?: "Unknown error",
                e
            )

            Result.failure(e)
        }
    }

    override fun sendMessageStream(
        message: String,
        systemPrompt: String?
    ): Flow<String> = callbackFlow {

        val conv = conversation
            ?: throw IllegalStateException(
                "Engine not initialized"
            )

        _state.value = LLMState.Generating

        val callback = object : MessageCallback {
            override fun onMessage(message: Message) {
                trySend(message.toString())
            }

            override fun onDone() {
                close(null)
            }

            override fun onError(throwable: Throwable) {
                Log.e(TAG, "Error in streaming response from LiteRT-LM", throwable)
                close(throwable)
            }
        }

        try {
            conv.sendMessageAsync(message, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate sendMessageAsync", e)
            close(e)
        }

        awaitClose {
            _state.value = LLMState.Ready
        }
    }

    // A fresh conversation per turn so the per-turn system prompt (style, memories, goals) actually reaches the model.
    override fun sendTurnStream(turn: ChatTurn): Flow<String> = callbackFlow {
        val eng = engine ?: throw IllegalStateException("Engine not initialized")

        // Never close the conversation while the native side is still producing tokens.
        inFlight?.let { withTimeoutOrNull(3_000L) { it.await() } }
        val settled = CompletableDeferred<Unit>()
        inFlight = settled

        val initial = turn.history.map {
            if (it.isUser) Message.user(it.text) else Message.model(it.text)
        }
        val system = turn.systemPrompt?.takeIf { it.isNotBlank() }?.let { Contents.of(it) }

        val newConv = try {
            conversation?.close()
            conversation = null
            eng.createConversation(
                ConversationConfig(
                    systemInstruction = system,
                    initialMessages = initial,
                    samplerConfig = samplerConfig,
                    maxOutputToken = maxOutputTokens
                )
            ).also { conversation = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create turn conversation", e)
            settled.complete(Unit)
            close(e)
            return@callbackFlow
        }

        val parts = mutableListOf<Content>()
        if (multimodal) {
            turn.media.forEach {
                parts += when (it.kind) {
                    MediaKind.IMAGE -> Content.ImageBytes(it.bytes)
                    MediaKind.AUDIO -> Content.AudioBytes(it.bytes)
                }
            }
        }
        parts += Content.Text(turn.message)
        Log.i(TAG, "Turn: multimodal=$multimodal media=${turn.media.map { "${it.kind}:${it.bytes.size}" }} history=${turn.history.size} text=${turn.message.take(80)}")

        _state.value = LLMState.Generating

        val callback = object : MessageCallback {
            override fun onMessage(message: Message) {
                trySend(message.toString())
            }

            override fun onDone() {
                settled.complete(Unit)
                close(null)
            }

            override fun onError(throwable: Throwable) {
                Log.e(TAG, "Error in streaming response from LiteRT-LM", throwable)
                settled.complete(Unit)
                close(throwable)
            }
        }

        try {
            newConv.sendMessageAsync(Contents.of(parts), callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate sendMessageAsync", e)
            settled.complete(Unit)
            close(e)
        }

        awaitClose {
            if (!settled.isCompleted) {
                try {
                    newConv.cancelProcess()
                } catch (e: Exception) {
                    Log.w(TAG, "cancelProcess failed", e)
                    settled.complete(Unit)
                }
            }
            _state.value = LLMState.Ready
        }
    }

    override suspend fun sendMessage(
        message: String,
        systemPrompt: String?
    ): Result<String> =
        withContext(Dispatchers.IO) {

            try {

                val conv = conversation
                    ?: return@withContext Result.failure(
                        IllegalStateException(
                            "Engine not initialized"
                        )
                    )

                _state.value =
                    LLMState.Generating

                val response =
                    conv.sendMessage(message)

                _state.value =
                    LLMState.Ready

                Result.success(
                    response.toString()
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Error generating response",
                    e
                )

                _state.value =
                    LLMState.Ready

                Result.failure(e)
            }
        }

    override fun resetConversation() {

        try {

            conversation?.close()
            conversation = null

            engine?.let { currentEngine ->

                conversation =
                    currentEngine.createConversation()
            }

            _state.value =
                LLMState.Ready

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to reset conversation",
                e
            )

            _state.value =
                LLMState.Error(
                    e.message ?: "Failed to reset conversation",
                    e
                )
        }
    }

    override fun close() {

        try {

            conversation?.runCatching { cancelProcess() }
            conversation?.close()
            conversation = null

            engine?.close()
            engine = null

            _state.value =
                LLMState.NotReady

            Log.d(
                TAG,
                "Engine closed"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error closing engine",
                e
            )
        }
    }
}