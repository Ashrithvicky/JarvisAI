package com.privateai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Chat input composer supporting multiline text, voice STT input, media attachments, and stop generation.
 */
@Composable
fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit = {},
    isGenerating: Boolean = false,
    enabled: Boolean = true,
    isListening: Boolean = false,
    onToggleVoice: () -> Unit = {},
    onPickAttachment: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    attachedMediaName: String? = null,
    attachedThumbnail: ByteArray? = null,
    isPreparingAttachment: Boolean = false,
    onRemoveAttachment: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var attachMenuOpen by remember { mutableStateOf(false) }
    val canSend = enabled && (value.isNotBlank() || attachedMediaName != null)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Attachment preview chip if an image/file is attached
        if (isPreparingAttachment) {
            Text(
                text = "Preparing attachment...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        if (attachedMediaName != null && attachedThumbnail != null) {
            val preview = remember(attachedThumbnail) {
                android.graphics.BitmapFactory.decodeByteArray(attachedThumbnail, 0, attachedThumbnail.size)?.asImageBitmap()
            }
            if (preview != null) {
                Box(modifier = Modifier.padding(bottom = 6.dp)) {
                    Image(
                        bitmap = preview,
                        contentDescription = attachedMediaName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp))
                    )
                    IconButton(
                        onClick = onRemoveAttachment,
                        modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove attachment",
                            modifier = Modifier.size(14.dp), tint = Color.White)
                    }
                }
            }
        } else if (attachedMediaName != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Attachment",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = attachedMediaName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onRemoveAttachment,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove attachment",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Main input row
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Attachment menu (file, camera, audio) - usable while a reply is generating
            Box {
                IconButton(
                    onClick = { attachMenuOpen = true },
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach media",
                        tint = if (attachedMediaName != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = attachMenuOpen, onDismissRequest = { attachMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Take photo") },
                        leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                        onClick = { attachMenuOpen = false; onTakePhoto() }
                    )
                    DropdownMenuItem(
                        text = { Text("Choose file") },
                        leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                        onClick = { attachMenuOpen = false; onPickAttachment() }
                    )
                }
            }

            // Input field
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                placeholder = {
                    Text(
                        text = when {
                            isListening -> "Listening (speak now)..."
                            else -> "Type a message..."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                ),
                singleLine = false,
                maxLines = 4
            )

            // Voice input button (Microphone STT)
            IconButton(
                onClick = onToggleVoice,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                if (isListening) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Stop voice input",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Start voice input",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Attach and mic stay usable while generating; Send is replaced by Stop until it finishes
            if (isGenerating) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.StopCircle,
                        contentDescription = "Stop generation",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (canSend)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
