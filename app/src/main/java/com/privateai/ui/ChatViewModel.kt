package com.privateai.ui

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.privateai.llm.ChatTurn
import com.privateai.llm.HistoryItem
import com.privateai.memory.MemoryExtractor
import com.privateai.multimodal.MediaPreparer
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.privateai.PrivateAIApplication
import com.privateai.conversation.Conversation
import com.privateai.conversation.ConversationManager
import com.privateai.conversation.ConversationSummary
import com.privateai.conversation.MessageRole
import com.privateai.llm.Backend
import com.privateai.llm.LLMConfig
import com.privateai.llm.LLMEngine
import com.privateai.llm.LLMState
import com.privateai.memory.Memory
import com.privateai.model.ModelManager
import com.privateai.multimodal.AttachmentManager
import com.privateai.multimodal.AttachmentMeta
import com.privateai.multimodal.SpeechInputHelper
import com.privateai.multimodal.TextToSpeechHelper
import com.privateai.multimodal.WakeWordHelper
import com.privateai.multimodal.PreparedMedia
import com.privateai.llm.MediaKind
import com.privateai.conversation.ConversationTitleGenerator
import com.privateai.voice.VivvaVoiceService
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.cancelAndJoin
import com.privateai.persona.AiPersona
import com.privateai.persona.RelationshipStyle
import com.privateai.settings.BackendPreference
import com.privateai.settings.InferenceSettings
import com.privateai.settings.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import com.privateai.voice.VoiceCommandBus
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the chat screen.
 * Coordinates UI state, LLM inference, conversation history, memory retrieval, persona strategies,
 * relationship styles, voice STT/TTS, and encrypted media attachments.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val UI_UPDATE_INTERVAL_MS = 60L

        // Two models in memory exceed the phone's RAM and Android kills the app, so share one engine.
        private val generationLock = Mutex()
        @Volatile private var processEngine: LLMEngine? = null
        @Volatile private var loadedKey: String? = null
        // Not tied to a ViewModel: a load must finish (and never leak a model) even if its screen is gone.
        private val loadScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)
        private val liveViewModels = java.util.concurrent.atomic.AtomicInteger(0)
    }

    private val appContainer = (application as PrivateAIApplication).appContainer
    private val mediaPreparer = MediaPreparer(application)
    private val modelManager: ModelManager = appContainer.modelManager
    private val conversationManager: ConversationManager = appContainer.conversationManager
    private val llmEngineFactory = appContainer.llmEngineFactory
    private val settingsManager: SettingsManager = appContainer.settingsManager
    private val contextManager = appContainer.contextManager
    private val behaviorDetector = appContainer.behaviorDetector
    private val behaviorAccumulator = appContainer.behaviorAccumulator

    private val attachmentManager = AttachmentManager(application, appContainer.securityManager)
    private var ttsHelper: TextToSpeechHelper? = null
    private var speechInputHelper: SpeechInputHelper? = null
    private var wakeWordHelper: WakeWordHelper? = null

    private var llmEngine: LLMEngine?
        get() = processEngine
        set(value) { processEngine = value }
    private var currentConversationId: String? = null

    // One running reply per chat; the model itself is used by one reply at a time (generationLock).
    private val jobs = mutableMapOf<String, Job>()
    private val partials = mutableMapOf<String, Pair<String, StringBuilder>>()
    private val deletedConversations = mutableSetOf<String>()
    private var speakNextReply = false

    private val _uiState = MutableStateFlow(
        ChatUiState(
            selectedPersona = settingsManager.getActivePersona(),
            selectedRelationshipStyle = settingsManager.getActiveRelationshipStyle(),
            inferenceSettings = settingsManager.getInferenceSettings()
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        liveViewModels.incrementAndGet()
        com.privateai.util.AppLogger.write("ChatViewModel created id=${System.identityHashCode(this)} live=${liveViewModels.get()}")
        initVoiceHelpers(application)
        loadConversationsList()
        restoreOrInitConversation()
        initializeEngine()
        observeVoiceCommands()
    }

    private fun observeVoiceCommands() {
        viewModelScope.launch {
            val ready = _uiState.map { it.isModelLoaded && !it.isGenerating }.distinctUntilChanged()
            combine(VoiceCommandBus.pending, ready) { cmd, isReady -> cmd.takeIf { isReady } }
                .filterNotNull()
                .collect { cmd ->
                    VoiceCommandBus.consume()
                    speakNextReply = true
                    sendMessage(cmd)
                }
        }
        viewModelScope.launch {
            var wasGenerating = false
            _uiState.map { it.isGenerating }.distinctUntilChanged().collect { generating ->
                if (wasGenerating && !generating && speakNextReply) {
                    speakNextReply = false
                    _uiState.value.messages.lastOrNull { !it.isUser }?.let { ttsHelper?.speak(it.content) }
                }
                wasGenerating = generating
            }
        }
    }

    private fun initVoiceHelpers(application: Application) {
        ttsHelper = TextToSpeechHelper(application)
        wakeWordHelper = WakeWordHelper(
            onWakeWordDetected = {
                _uiState.value = _uiState.value.copy(statusMessage = "Wake word detected: Listening...")
                startVoiceInput()
            }
        )
        speechInputHelper = SpeechInputHelper(
            context = application,
            onResult = { text ->
                val wakeTriggered = wakeWordHelper?.processTranscribedText(text) ?: false
                if (!wakeTriggered) {
                    val combined = joinDictation(dictationBase, text)
                    dictationBase = combined
                    _uiState.value = _uiState.value.copy(inputText = combined)
                }
            },
            onPartial = { partial ->
                _uiState.value = _uiState.value.copy(inputText = joinDictation(dictationBase, partial))
            },
            onError = { err ->
                VivvaVoiceService.setAppMicInUse(false)
                _uiState.value = _uiState.value.copy(isListening = false, error = err)
            },
            onListeningStateChanged = { listening ->
                VivvaVoiceService.setAppMicInUse(listening)
                _uiState.value = _uiState.value.copy(isListening = listening)
            }
        )
    }

    fun loadConversationsList() {
        val summaries = conversationManager.getAllConversationSummaries()
        _uiState.value = _uiState.value.copy(conversations = summaries)
    }

    private fun restoreOrInitConversation() {
        val savedConvId = settingsManager.getLastActiveConversationId()
        val existing = if (savedConvId != null) conversationManager.getConversation(savedConvId) else null

        if (existing != null) {
            currentConversationId = existing.id
            displayConversation(existing)
        } else {
            val all = conversationManager.getAllConversationSummaries()
            if (all.isNotEmpty()) {
                val first = conversationManager.getConversation(all.first().id)
                if (first != null) {
                    currentConversationId = first.id
                    settingsManager.setLastActiveConversationId(first.id)
                    displayConversation(first)
                    return
                }
            }
            val newConv = conversationManager.createConversation()
            currentConversationId = newConv.id
            settingsManager.setLastActiveConversationId(newConv.id)
            displayConversation(newConv)
        }
        loadConversationsList()
    }

    private fun loadThumbnails(conversationId: String, messages: List<ChatMessage>) {
        if (messages.none { it.attachmentId != null }) return
        viewModelScope.launch(Dispatchers.Default) {
            val loaded = messages.filter { it.attachmentId != null }.associate { m ->
                m.id to attachmentManager.readById(conversationId, m.attachmentId!!).getOrNull()
            }
            if (_uiState.value.currentConversationId != conversationId) return@launch
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages.map { m -> loaded[m.id]?.let { m.copy(thumbnail = it) } ?: m }
            )
        }
    }

    private fun displayConversation(conversation: Conversation) {
        val tagRegex = Regex("^\\[Attached Media: (.*?)(?:\\|([0-9a-fA-F-]{36}))?]\\n?")
        val uiMessages = conversation.messages.map { msg ->
            val tag = tagRegex.find(msg.content)
            ChatMessage(
                id = msg.id,
                content = if (tag != null) msg.content.removeRange(tag.range) else msg.content,
                isUser = msg.role == MessageRole.USER,
                attachmentName = tag?.groupValues?.get(1),
                attachmentId = tag?.groupValues?.get(2)?.takeIf { it.isNotEmpty() },
                timestamp = msg.timestamp
            )
        }
        loadThumbnails(conversation.id, uiMessages)
        val inProgress = partials[conversation.id]?.let { (id, text) ->
            ChatMessage(id = id, content = text.toString(), isUser = false)
        }
        _uiState.value = _uiState.value.copy(
            currentConversationId = conversation.id,
            currentConversationTitle = conversation.title,
            messages = if (inProgress != null) uiMessages + inProgress else uiMessages,
            isGenerating = jobs[conversation.id]?.isActive == true,
            inputText = "",
            pendingAttachment = null,
            isListening = false,
            speakingMessageId = null,
            lastInjectedMemories = emptyList(),
            error = null
        )
    }

    fun initializeEngine() {
        loadScope.launch {
            // Waits for a running reply so a settings change never interrupts it.
            generationLock.withLock { loadEngine() }
        }
    }

    private suspend fun loadEngine() {
        run {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Locating model...")

            val modelPath = modelManager.getEffectiveModelPath()
            if (modelPath == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "No model found. Push gemma-4-E2B-it.litertlm to app storage.",
                    error = "Model file not found"
                )
                return@run
            }

            _uiState.value = _uiState.value.copy(statusMessage = "Loading on-device model...")

            val key = "$modelPath|${_uiState.value.inferenceSettings}|${_uiState.value.selectedPersona.systemPrompt}"
            val live = llmEngine
            if (live != null && loadedKey == key) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isModelLoaded = true,
                    activeBackend = live.activeBackend ?: Backend.GPU,
                    statusMessage = ""
                )
                return@run
            }

            try {
                com.privateai.util.AppLogger.write("ENGINE load start: ${com.privateai.util.AppLogger.memorySnapshot()}")
                llmEngine?.close()
                llmEngine = null
                loadedKey = null
                val engine = llmEngineFactory.create()
                val settings = _uiState.value.inferenceSettings

                val targetBackend = when (settings.backendPreference) {
                    BackendPreference.AUTO -> Backend.GPU
                    BackendPreference.GPU -> Backend.GPU
                    BackendPreference.CPU -> Backend.CPU
                }
                val allowFallback = settings.backendPreference == BackendPreference.AUTO

                val config = LLMConfig(
                    modelPath = modelPath,
                    backend = targetBackend,
                    allowFallback = allowFallback,
                    maxTokens = settings.maxTokens,
                    temperature = settings.temperature,
                    topK = settings.topK,
                    topP = settings.topP,
                    seed = settings.seed,
                    systemInstruction = _uiState.value.selectedPersona.systemPrompt
                )

                engine.initialize(config)
                    .onSuccess {
                        llmEngine = engine
                        loadedKey = key
                        com.privateai.util.AppLogger.write("ENGINE load done: ${com.privateai.util.AppLogger.memorySnapshot()}")
                        val actualBackend = engine.activeBackend ?: targetBackend
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isModelLoaded = true,
                            activeBackend = actualBackend,
                            statusMessage = ""
                        )
                        Log.i(TAG, "LiteRT-LM engine ready (actualBackend=$actualBackend)")
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            statusMessage = "Failed to load model: ${error.message}",
                            error = error.message
                        )
                        Log.e(TAG, "Engine initialization failed", error)
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Error: ${e.message}",
                    error = e.message
                )
                Log.e(TAG, "Unexpected error during initialization", e)
            }
        }
    }

    fun selectPersona(persona: AiPersona) {
        settingsManager.setActivePersona(persona)
        _uiState.value = _uiState.value.copy(selectedPersona = persona)
    }

    fun updateSettingsAndStyle(newSettings: InferenceSettings, newStyle: RelationshipStyle) {
        val oldSettings = _uiState.value.inferenceSettings
        settingsManager.saveInferenceSettings(newSettings)
        settingsManager.setActiveRelationshipStyle(newStyle)
        _uiState.value = _uiState.value.copy(
            inferenceSettings = newSettings,
            selectedRelationshipStyle = newStyle
        )

        if (oldSettings != newSettings) {
            initializeEngine()
        }
    }

    fun switchConversation(conversationId: String) {
        if (conversationId == currentConversationId) return
        stopSpeaking()
        stopVoiceInput()
        val conv = conversationManager.getConversation(conversationId) ?: return
        currentConversationId = conv.id
        settingsManager.setLastActiveConversationId(conv.id)
        displayConversation(conv)
        loadConversationsList()
    }

    fun createNewConversation(title: String = "New Chat") {
        stopSpeaking()
        stopVoiceInput()
        val currentConv = currentConversationId?.let { conversationManager.getConversation(it) }
        val targetConv = if (currentConv != null && currentConv.messages.isEmpty()) {
            currentConv
        } else {
            conversationManager.createConversation(title)
        }
        currentConversationId = targetConv.id
        settingsManager.setLastActiveConversationId(targetConv.id)
        displayConversation(targetConv)
        loadConversationsList()
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        conversationManager.updateConversationTitle(conversationId, newTitle)
        if (conversationId == currentConversationId) {
            _uiState.value = _uiState.value.copy(currentConversationTitle = newTitle)
        }
        loadConversationsList()
    }

    fun deleteConversation(conversationId: String) {
        deletedConversations += conversationId
        jobs.remove(conversationId)?.cancel()
        partials.remove(conversationId)
        stopSpeaking()
        stopVoiceInput()
        conversationManager.deleteConversation(conversationId)
        attachmentManager.deleteConversationAttachments(conversationId)
        if (conversationId == currentConversationId) {
            restoreOrInitConversation()
        } else {
            loadConversationsList()
        }
    }

    // Voice STT / TTS methods
    fun toggleVoiceInput() {
        if (_uiState.value.isListening) {
            stopVoiceInput()
        } else {
            startVoiceInput()
        }
    }

    fun startVoiceInput() {
        dictationBase = _uiState.value.inputText
        speechInputHelper?.startListening()
    }

    private var dictationBase: String = ""

    private fun joinDictation(base: String, spoken: String): String =
        if (base.isBlank()) spoken else "${base.trimEnd()} $spoken"

    fun stopVoiceInput() {
        speechInputHelper?.stopListening()
        _uiState.value = _uiState.value.copy(isListening = false)
    }

    fun speakMessage(messageId: String, content: String) {
        if (_uiState.value.speakingMessageId == messageId) {
            stopSpeaking()
        } else {
            ttsHelper?.stop()
            val ok = ttsHelper?.speak(content) ?: false
            if (ok) {
                _uiState.value = _uiState.value.copy(speakingMessageId = messageId)
            }
        }
    }

    fun stopSpeaking() {
        ttsHelper?.stop()
        _uiState.value = _uiState.value.copy(speakingMessageId = null)
    }

    // Attachment methods: heavy decoding/downscaling runs off the main thread
    fun attachMedia(name: String, mimeType: String, rawBytes: ByteArray) {
        attachJob?.cancel()
        attachJob = viewModelScope.launch(Dispatchers.Default) {
            publishAttachment(name, mimeType, mediaPreparer.prepare(name, mimeType, rawBytes), rawBytes)
        }
    }

    fun attachUri(name: String, mimeType: String, uri: android.net.Uri) {
        attachJob?.cancel()
        _uiState.value = _uiState.value.copy(isPreparingAttachment = true)
        attachJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                publishAttachment(name, mimeType, mediaPreparer.prepareUri(name, mimeType, uri), null)
            } catch (e: Exception) {
                Log.e(TAG, "Attachment failed", e)
                _uiState.value = _uiState.value.copy(isPreparingAttachment = false, error = "Could not load attachment")
            }
        }
    }

    private var attachJob: Job? = null

    fun attachPhotoFile(file: java.io.File) {
        attachJob?.cancel()
        _uiState.value = _uiState.value.copy(isPreparingAttachment = true)
        attachJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = file.readBytes()
                publishAttachment("photo.jpg", "image/jpeg", mediaPreparer.prepare("photo.jpg", "image/jpeg", bytes), null)
            } catch (e: Exception) {
                Log.e(TAG, "Photo failed", e)
                _uiState.value = _uiState.value.copy(isPreparingAttachment = false, error = "Could not load photo")
            } finally {
                file.delete()
            }
        }
    }

    private fun publishAttachment(name: String, mimeType: String, prepared: PreparedMedia, raw: ByteArray?) {
        val firstImage = prepared.parts.firstOrNull { it.kind == MediaKind.IMAGE }?.bytes
        // Keep only compact bytes: the downscaled frame for images/video, original for small audio/text.
        val compact = firstImage ?: raw ?: ByteArray(0)
        _uiState.value = _uiState.value.copy(
            pendingAttachment = PendingMedia(name, mimeType, compact, firstImage, prepared),
            isPreparingAttachment = false
        )
    }

    fun clearAttachment() {
        attachJob?.cancel()
        _uiState.value = _uiState.value.copy(pendingAttachment = null, isPreparingAttachment = false)
    }

    fun sendMessage(content: String) {
        val pendingAtt = _uiState.value.pendingAttachment
        if (content.isBlank() && pendingAtt == null) return

        val running = jobs[currentConversationId]
        if (running?.isActive == true) return

        if (llmEngine == null) return
        val conversationId = currentConversationId ?: return
        val conversation = conversationManager.getConversation(conversationId) ?: return

        // 1. Process and encrypt attachment if present
        var attachedMeta: AttachmentMeta? = null
        if (pendingAtt != null) {
            val saveRes = attachmentManager.saveAttachment(
                conversationId = conversationId,
                mimeType = pendingAtt.mimeType,
                rawBytes = pendingAtt.data
            )
            attachedMeta = saveRes.getOrNull()
        }

        val tagName = pendingAtt?.name?.replace("|", "_")?.replace("]", ")")
        val tagId = attachedMeta?.id?.let { "|$it" }.orEmpty()
        val effectiveUserQuery = if (pendingAtt != null && content.isBlank()) {
            "[Attached Media: $tagName$tagId]"
        } else if (pendingAtt != null) {
            "[Attached Media: $tagName$tagId]\n$content"
        } else {
            content
        }

        // 2. Build budgeted context with Persona, Relationship Style, Memory, and Rolling Summary
        val budgetedContext = contextManager.assembleConversationContext(
            conversation = conversation,
            currentUserQuery = effectiveUserQuery,
            persona = _uiState.value.selectedPersona,
            relationshipStyle = _uiState.value.selectedRelationshipStyle,
            maxContextTokens = _uiState.value.inferenceSettings.maxTokens
        )

        // 3. Add user message to UI state
        val userMessage = ChatMessage(
            content = content,
            isUser = true,
            attachmentName = pendingAtt?.name,
            thumbnail = pendingAtt?.thumbnail
        )
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            inputText = "",
            pendingAttachment = null,
            lastInjectedMemories = budgetedContext.injectedMemories
        )

        // 4. Save user message to persistent storage
        conversationManager.addMessage(conversationId, MessageRole.USER, effectiveUserQuery, autoTitle = false)

        // Update auto-title in UI state if needed
        val updatedConv = conversationManager.getConversation(conversationId)
        if (updatedConv != null) {
            _uiState.value = _uiState.value.copy(currentConversationTitle = updatedConv.title)
        }
        loadConversationsList()

        // 5. Start streaming response; it keeps running if the user switches to another chat.
        val assistantId = java.util.UUID.randomUUID().toString()
        val responseBuilder = StringBuilder()
        partials[conversationId] = assistantId to responseBuilder
        var thisJob: Job? = null
        val clock = java.text.SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a zzz", java.util.Locale.getDefault())
            .format(java.util.Date())
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var isPersisted = false
            var lastUiUpdate = 0L

            fun persistAssistantResponse() {
                if (!isPersisted && conversationId !in deletedConversations) {
                    val finalResponse = responseBuilder.toString().trim()
                    if (finalResponse.isNotBlank()) {
                        isPersisted = true
                        try {
                            conversationManager.addMessage(conversationId, MessageRole.ASSISTANT, finalResponse)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to persist assistant response", e)
                        }
                    }
                }
            }

            fun publish(force: Boolean) {
                val now = SystemClock.uptimeMillis()
                if (!force && now - lastUiUpdate < UI_UPDATE_INTERVAL_MS) return
                lastUiUpdate = now
                if (_uiState.value.currentConversationId != conversationId) return
                val messages = _uiState.value.messages
                val index = messages.indexOfFirst { it.id == assistantId }
                val text = responseBuilder.toString()
                _uiState.value = _uiState.value.copy(
                    messages = if (index >= 0) {
                        messages.toMutableList().also { it[index] = it[index].copy(content = text) }
                    } else {
                        messages + ChatMessage(id = assistantId, content = text, isUser = false)
                    }
                )
            }

            try {
                publish(force = true)
                // Only one generation can use the model at a time; other chats' requests wait here.
                generationLock.withLock {
                    val engine = llmEngine ?: throw IllegalStateException("Model is not ready")
                    val prepared = pendingAtt?.let {
                        it.prepared ?: withContext(Dispatchers.Default) { mediaPreparer.prepare(it.name, it.mimeType, it.data) }
                    }
                    val modelMessage = listOfNotNull(prepared?.inlineText, content.takeIf { it.isNotBlank() })
                        .joinToString("\n\n")
                        .ifBlank { "Look at the attached image and tell me what you see. If it contains text, transcribe it exactly." }
                    val history = budgetedContext.recentMessages.map {
                        val text = it.content.replace(Regex("^\\[Attached Media: .*?]\\n?"), "(shared an attachment earlier) ").trim()
                        HistoryItem(isUser = it.role == MessageRole.USER, text = text)
                    }
                    val turn = ChatTurn(
                        message = modelMessage,
                        systemPrompt = budgetedContext.systemInstruction +
                            "\n\n[DEVICE CLOCK] Current date and time on the user's phone: $clock. " +
                            "Use it for any question about today's date, time or day.",
                        history = history,
                        media = prepared?.parts.orEmpty()
                    )

                    val genStart = SystemClock.uptimeMillis()
                    var firstTokenAt = 0L
                    var tokenCount = 0
                    engine.sendTurnStream(turn)
                        .collect { token ->
                            if (tokenCount++ == 0) firstTokenAt = SystemClock.uptimeMillis()
                            responseBuilder.append(token)
                            publish(force = false)
                        }
                    val genEnd = SystemClock.uptimeMillis()
                    com.privateai.util.AppLogger.write(
                        "GEN done: prompt=${modelMessage.length}ch history=${history.size} media=${turn.media.size} " +
                            "firstToken=${if (firstTokenAt > 0) firstTokenAt - genStart else -1}ms total=${genEnd - genStart}ms chunks=$tokenCount"
                    )

                    publish(force = true)
                    persistAssistantResponse()
                    generateTitleAsync(conversationId, content.ifBlank { pendingAtt?.name ?: "attachment" }, responseBuilder.toString())
                    // Lightweight behavioral learning and memory capture
                    try {
                        val signals = behaviorDetector.detect(effectiveUserQuery)
                        if (signals.isNotEmpty()) {
                            behaviorAccumulator.processSignals(signals)
                        }
                        withContext(Dispatchers.IO) {
                            MemoryExtractor.remember(content, appContainer.memoryManager)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Behavioral learning error (non-fatal)", e)
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    persistAssistantResponse()
                }
                throw e
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Out of memory while generating", e)
                withContext(NonCancellable) { persistAssistantResponse() }
                reportError(conversationId, "Not enough memory. Close other apps and try again.")
            } catch (e: Exception) {
                Log.e(TAG, "Error streaming response", e)
                withContext(NonCancellable) {
                    persistAssistantResponse()
                }
                reportError(conversationId, "Inference error: ${e.message}")
            } finally {
                withContext(NonCancellable) {
                    persistAssistantResponse()
                    if (jobs[conversationId] === thisJob) jobs.remove(conversationId)
                    if (partials[conversationId]?.first == assistantId) partials.remove(conversationId)
                    refreshGenerating()
                    loadConversationsList()
                }
            }
        }
        thisJob = job
        jobs[conversationId] = job
        refreshGenerating()
        job.start()
    }

    private fun reportError(conversationId: String, message: String) {
        if (_uiState.value.currentConversationId == conversationId) {
            _uiState.value = _uiState.value.copy(error = message)
        }
    }

    private fun refreshGenerating() {
        val generating = currentConversationId?.let { jobs[it]?.isActive == true } == true
        if (_uiState.value.isGenerating != generating) {
            _uiState.value = _uiState.value.copy(isGenerating = generating)
        }
    }

    /** Stops only the reply of the chat on screen; replies of other chats keep running. */
    fun stopGeneration() {
        val id = currentConversationId
        if (id != null) {
            jobs.remove(id)?.cancel()
            partials.remove(id)
        }
        titleJob?.cancel()
        refreshGenerating()
    }

    private var titleJob: Job? = null

    private fun generateTitleAsync(conversationId: String, userText: String, reply: String) {
        val conv = conversationManager.getConversation(conversationId) ?: return
        if (conv.isCustomTitle || conv.title != "New Chat" || reply.isBlank()) return

        titleJob = viewModelScope.launch {
            val raw = try {
                val sb = StringBuilder()
                val prompt = "Conversation:\nUser: ${userText.take(300)}\nAssistant: ${reply.take(400)}\n\n" +
                    "Give this conversation a short topic title of 2 to 4 words that describes what it is about. " +
                    "Do not copy the user's words. Examples: 'hey hi' -> Friendly Greeting; " +
                    "'explain dijkstra' -> Shortest Path Algorithm; 'what is in this photo' -> Photo Description. " +
                    "Reply with only the title."
                generationLock.withLock {
                    llmEngine?.sendTurnStream(ChatTurn(prompt, "You write short, specific chat titles."))
                        ?.collect { sb.append(it) }
                }
                sb.toString()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Title generation failed", e)
                ""
            }
            val title = cleanTitle(raw).ifBlank { ConversationTitleGenerator.generateTitle(userText) }
            conversationManager.setGeneratedTitle(conversationId, title)
            if (_uiState.value.currentConversationId == conversationId) {
                _uiState.value = _uiState.value.copy(currentConversationTitle = title)
            }
            loadConversationsList()
        }
    }

    private fun cleanTitle(raw: String): String =
        raw.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            .trim().trim('"', '\'', '*', '#', '.', ' ')
            .split(Regex("\\s+")).take(6).joinToString(" ")
            .take(40)

    fun regenerateLastResponse() {
        val last = _uiState.value.messages.lastOrNull { it.isUser } ?: return
        editLastQuestion(last.content)
    }

    /** Replaces the last question (and the reply to it) and asks the model again. */
    fun editLastQuestion(newText: String) {
        val id = currentConversationId ?: return
        if (jobs[id]?.isActive == true) return
        val messages = _uiState.value.messages
        val lastUserIndex = messages.indexOfLast { it.isUser }
        if (lastUserIndex < 0) return
        val lastUser = messages[lastUserIndex]
        if (newText.isBlank() && lastUser.attachmentId == null) return

        conversationManager.removeLastTurn(id)
        _uiState.value = _uiState.value.copy(messages = messages.take(lastUserIndex))

        val attachmentId = lastUser.attachmentId
        if (attachmentId == null) {
            sendMessage(newText)
            return
        }
        viewModelScope.launch {
            try {
                val name = lastUser.attachmentName ?: "photo.jpg"
                val bytes = withContext(Dispatchers.Default) { attachmentManager.readById(id, attachmentId).getOrNull() }
                if (bytes != null) {
                    val prepared = withContext(Dispatchers.Default) { mediaPreparer.prepare(name, "image/jpeg", bytes) }
                    _uiState.value = _uiState.value.copy(
                        pendingAttachment = PendingMedia(name, "image/jpeg", bytes, bytes, prepared)
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not reload the original attachment", e)
            }
            sendMessage(newText)
        }
    }

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        jobs.values.forEach { it.cancel() }
        ttsHelper?.shutdown()
        liveViewModels.decrementAndGet()
    }
}

/**
 * Complete UI state for the ChatScreen.
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentConversationId: String? = null,
    val currentConversationTitle: String = "New Chat",
    val conversations: List<ConversationSummary> = emptyList(),
    val selectedPersona: AiPersona = AiPersona.GENERAL,
    val selectedRelationshipStyle: RelationshipStyle = RelationshipStyle.COMPANION,
    val inferenceSettings: InferenceSettings = InferenceSettings(),
    val activeBackend: Backend = Backend.GPU,
    val inputText: String = "",
    val pendingAttachment: PendingMedia? = null,
    val isListening: Boolean = false,
    val isPreparingAttachment: Boolean = false,
    val speakingMessageId: String? = null,
    val isLoading: Boolean = false,
    val isModelLoaded: Boolean = false,
    val isGenerating: Boolean = false,
    val statusMessage: String = "",
    val error: String? = null,
    val lastInjectedMemories: List<Memory> = emptyList()
)

data class PendingMedia(
    val name: String,
    val mimeType: String,
    val data: ByteArray,
    val thumbnail: ByteArray? = null,
    val prepared: PreparedMedia? = null
)

/**
 * Represents a chat message in the UI.
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val attachmentName: String? = null,
    val thumbnail: ByteArray? = null,
    val attachmentId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
