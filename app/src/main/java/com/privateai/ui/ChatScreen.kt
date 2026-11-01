package com.privateai.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.privateai.conversation.MessageRole
import com.privateai.llm.Backend
import com.privateai.ui.components.ChatInput
import com.privateai.ui.components.ConversationDrawerContent
import com.privateai.ui.components.MessageBubble
import com.privateai.ui.components.PersonaSelector
import com.privateai.ui.components.SettingsDialog
import com.privateai.voice.VivvaVoiceService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showMemoryScreen by remember { mutableStateOf(false) }
    var handsFree by remember { mutableStateOf(VivvaVoiceService.isRunning) }

    // In-app dictation holds the recognizer; release it when the app leaves the screen so the wake word resumes.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) viewModel.stopVoiceInput()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val handsFreePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) {
            VivvaVoiceService.start(context)
            handsFree = true
        } else {
            Toast.makeText(context, "Microphone permission required for hands-free mode", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher for Microphone permission
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceInput()
        } else {
            Toast.makeText(context, "Microphone permission required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher for Image / Media Picker
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val filename = uri.lastPathSegment ?: "attachment"
            viewModel.attachUri(filename, mimeType, uri)
        }
    }

    // Camera: capture into the app cache via FileProvider, then attach the JPEG
    var pendingPhotoFile by remember { mutableStateOf<java.io.File?>(null) }
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val file = pendingPhotoFile
        pendingPhotoFile = null
        if (saved && file != null && file.exists()) {
            viewModel.attachPhotoFile(file)
        }
    }

    if (showMemoryScreen) {
        com.privateai.ui.memory.MemoryScreen(
            onBack = { showMemoryScreen = false }
        )
        return
    }

    // Auto-scroll to bottom when new messages are added
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Keep bottom visible during live token streaming if user is already at/near bottom
    val lastMessageContent = uiState.messages.lastOrNull()?.content
    LaunchedEffect(lastMessageContent) {
        if (uiState.isGenerating && uiState.messages.isNotEmpty()) {
            val lastIndex = uiState.messages.size - 1
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            val isAtBottom = !listState.canScrollForward || (lastVisibleItem?.index == lastIndex)

            if (isAtBottom && !listState.isScrollInProgress) {
                val scrollOffset = lastVisibleItem?.size ?: 0
                listState.scrollToItem(lastIndex, scrollOffset)
            }
        }
    }

    // Show error in snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = uiState.inferenceSettings,
            currentRelationshipStyle = uiState.selectedRelationshipStyle,
            onSave = { newSettings, newStyle ->
                viewModel.updateSettingsAndStyle(newSettings, newStyle)
                showSettingsDialog = false
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawerContent(
                conversations = uiState.conversations,
                currentConversationId = uiState.currentConversationId,
                onSelectConversation = { id ->
                    viewModel.switchConversation(id)
                    coroutineScope.launch { drawerState.close() }
                },
                onNewConversation = {
                    viewModel.createNewConversation()
                    coroutineScope.launch { drawerState.close() }
                },
                onRenameConversation = { id, title ->
                    viewModel.renameConversation(id, title)
                },
                onDeleteConversation = { id ->
                    viewModel.deleteConversation(id)
                },
                onOpenMemoryManager = {
                    showMemoryScreen = true
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open conversation drawer")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = uiState.currentConversationTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Jarvis • ${uiState.selectedPersona.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))

                                // Style badge
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = uiState.selectedRelationshipStyle.displayName.take(12),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Backend Badge
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (uiState.activeBackend == Backend.GPU)
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = if (uiState.activeBackend == Backend.GPU) "⚡ GPU" else "⚙️ CPU",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }

                                if (uiState.isLoading) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = uiState.statusMessage,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (handsFree) {
                                VivvaVoiceService.stop(context)
                                handsFree = false
                            } else {
                                handsFreePermissionsLauncher.launch(
                                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                                )
                            }
                        }) {
                            Icon(
                                if (handsFree) Icons.Default.Hearing else Icons.Default.HearingDisabled,
                                contentDescription = if (handsFree) "Stop listening for 'Hey Jarvis'" else "Listen for 'Hey Jarvis' (wake word)"
                            )
                        }
                        IconButton(onClick = { showMemoryScreen = true }) {
                            Icon(Icons.Default.Psychology, contentDescription = "Memories & Preferences")
                        }
                        IconButton(onClick = { viewModel.createNewConversation() }) {
                            Icon(Icons.Default.Add, contentDescription = "New chat")
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Settings")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Persona Selector Chips
                PersonaSelector(
                    selectedPersona = uiState.selectedPersona,
                    onSelectPersona = { viewModel.selectPersona(it) }
                )

                // Memory injection notice if memories were recalled
                if (uiState.lastInjectedMemories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Recalled ${uiState.lastInjectedMemories.size} relevant memory preferences",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Chat message list
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (uiState.messages.isEmpty() && !uiState.isLoading) {
                        // Empty State
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "✨",
                                style = MaterialTheme.typography.displayLarge,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Text(
                                text = "Jarvis - ${uiState.selectedPersona.displayName}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            Text(
                                text = "Let's Brainstorm, Go Ahead With Your Questions Bosss!!",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )

                            if (!uiState.isModelLoaded && !uiState.isLoading) {
                                Text(
                                    text = "\n⚠️ Model file not found in app storage.\nPlease push gemma-4-E2B-it.litertlm to continue.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = uiState.messages,
                                key = { index, msg -> "${msg.id}_$index" }
                            ) { index, message ->
                                val isLatestAssistant = !message.isUser && index == uiState.messages.size - 1

                                MessageBubble(
                                    role = if (message.isUser) MessageRole.USER else MessageRole.ASSISTANT,
                                    content = message.content,
                                    timestamp = message.timestamp,
                                    attachmentName = message.attachmentName,
                                    thumbnail = message.thumbnail,
                                    onRegenerate = if (isLatestAssistant) {
                                        { viewModel.regenerateLastResponse() }
                                    } else null,
                                    onSpeak = if (!message.isUser) {
                                        { viewModel.speakMessage(message.id, message.content) }
                                    } else null,
                                    isSpeaking = uiState.speakingMessageId == message.id,
                                    isGenerating = isLatestAssistant && uiState.isGenerating,
                                    onEdit = if (message.isUser && index == uiState.messages.indexOfLast { it.isUser } && !uiState.isGenerating) {
                                        { text -> viewModel.editLastQuestion(text) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                // Chat Input bar with voice STT, media attachment, and Stop generation
                ChatInput(
                    value = uiState.inputText,
                    onValueChange = { viewModel.updateInputText(it) },
                    onSend = { viewModel.sendMessage(uiState.inputText) },
                    onStop = { viewModel.stopGeneration() },
                    isGenerating = uiState.isGenerating,
                    enabled = uiState.isModelLoaded,
                    isListening = uiState.isListening,
                    onToggleVoice = {
                        if (uiState.isListening) {
                            viewModel.stopVoiceInput()
                        } else {
                            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onPickAttachment = {
                        mediaPickerLauncher.launch("*/*")
                    },
                    onTakePhoto = {
                        val file = java.io.File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                        pendingPhotoFile = file
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.provider", file
                        )
                        takePhotoLauncher.launch(uri)
                    },
                    attachedMediaName = uiState.pendingAttachment?.name,
                    attachedThumbnail = uiState.pendingAttachment?.thumbnail,
                    isPreparingAttachment = uiState.isPreparingAttachment,
                    onRemoveAttachment = { viewModel.clearAttachment() }
                )
            }
        }
    }
}
