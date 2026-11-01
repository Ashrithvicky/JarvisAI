package com.privateai.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.privateai.conversation.MessageRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    role: MessageRole,
    content: String,
    modifier: Modifier = Modifier,
    timestamp: Long = System.currentTimeMillis(),
    attachmentName: String? = null,
    thumbnail: ByteArray? = null,
    onRegenerate: (() -> Unit)? = null,
    onSpeak: (() -> Unit)? = null,
    isSpeaking: Boolean = false,
    isGenerating: Boolean = false,
    onEdit: ((String) -> Unit)? = null
) {
    val isUser = role == MessageRole.USER
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var isCopied by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val timeFormatted = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Avatar(
                icon = Icons.Default.SmartToy,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                contentDescription = "Jarvis AI"
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Image first (like ChatGPT); other attachments get a small badge
            val picture = remember(thumbnail) {
                thumbnail?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
            }
            if (picture != null) {
                var showFull by remember { mutableStateOf(false) }
                androidx.compose.foundation.Image(
                    bitmap = picture,
                    contentDescription = attachmentName,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .widthIn(max = 240.dp)
                        .heightIn(max = 280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showFull = true }
                )
                if (showFull) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showFull = false },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        var scale by remember { mutableStateOf(1f) }
                        var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 6f)
                                        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 3f; offset = androidx.compose.ui.geometry.Offset.Zero })
                                }
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = picture,
                                contentDescription = attachmentName,
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                            )
                            IconButton(
                                onClick = { showFull = false },
                                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                    }
                }
            } else if (attachmentName != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Attachment",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = attachmentName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (content.isNotBlank() || !isUser || picture == null) Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isUser)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                    .pointerInput(onEdit != null) {
                        if (onEdit == null) return@pointerInput
                        // Observes without consuming so text selection still works; opens after a 2 s hold.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val slop = viewConfiguration.touchSlop * 3
                            // A null result means the finger stayed down and still for 1 s.
                            val released = withTimeoutOrNull(1_000L) {
                                while (true) {
                                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                        .firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed || (change.position - down.position).getDistance() > slop) break
                                }
                            }
                            if (released == null) menuOpen = true
                        }
                    }
                    .padding(12.dp)
            ) {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Copy message") },
                        onClick = {
                            menuOpen = false
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Message", content))
                            Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit question") },
                        onClick = {
                            menuOpen = false
                            editing = true
                        }
                    )
                }
                if (editing && onEdit != null) {
                    var draft by remember { mutableStateOf(content) }
                    AlertDialog(
                        onDismissRequest = { editing = false },
                        title = { Text("Edit question") },
                        text = {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                editing = false
                                onEdit(draft)
                            }) { Text("Send") }
                        },
                        dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
                    )
                }
                if (content.isBlank() && !isUser) {
                    Text(
                        text = "Jarvis is thinking...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SelectionContainer {
                    Column {
                        MarkdownText(
                            content = content,
                            textColor = if (isUser)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isUser && isGenerating) {
                            StreamingDotIndicator(
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                    }
                }
            }

            // Action row (Copy, TTS Voice, Regenerate, Timestamp)
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (content.isNotBlank()) {
                    // Copy button
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Message", content)
                            clipboard.setPrimaryClip(clip)
                            isCopied = true
                            Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                delay(2000)
                                isCopied = false
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Done else Icons.Default.ContentCopy,
                            contentDescription = "Copy message",
                            tint = if (isCopied) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // TTS audio speech button for assistant
                    if (!isUser && onSpeak != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onSpeak,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isSpeaking) "Stop speaking" else "Read message aloud",
                                tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Regenerate button
                    if (!isUser && onRegenerate != null && !isGenerating) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate response",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Avatar(
                icon = Icons.Default.Person,
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentDescription = "User"
            )
        }
    }
}

@Composable
private fun Avatar(
    icon: ImageVector,
    backgroundColor: Color,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun StreamingDotIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "streamingDotTransition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "streamingDotAlpha"
    )

    Box(
        modifier = modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
    )
}
