package com.privateai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.privateai.persona.RelationshipStyle
import com.privateai.settings.BackendPreference
import com.privateai.settings.InferenceSettings
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    currentSettings: InferenceSettings,
    currentRelationshipStyle: RelationshipStyle = RelationshipStyle.COMPANION,
    onSave: (InferenceSettings, RelationshipStyle) -> Unit,
    onDismiss: () -> Unit
) {
    var backendPref by remember { mutableStateOf(currentSettings.backendPreference) }
    var selectedStyle by remember { mutableStateOf(currentRelationshipStyle) }
    var temperature by remember { mutableDoubleStateOf(currentSettings.temperature) }
    var topK by remember { mutableIntStateOf(currentSettings.topK) }
    var topP by remember { mutableDoubleStateOf(currentSettings.topP) }
    var maxTokens by remember { mutableIntStateOf(currentSettings.maxTokens) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Jarvis Settings & Preferences", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // Relationship / Conversational Style Selection
                Text("Personality", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = RelationshipStyle.COMPANION.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Backend Selection
                Text("Inference Backend", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    BackendPreference.entries.forEachIndexed { index, pref ->
                        SegmentedButton(
                            selected = backendPref == pref,
                            onClick = { backendPref = pref },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = BackendPreference.entries.size)
                        ) {
                            Text(pref.name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(
                    text = when (backendPref) {
                        BackendPreference.AUTO -> "Tries GPU (OpenCL) first; automatically falls back to CPU if unavailable."
                        BackendPreference.GPU -> "Forces GPU (OpenCL) execution."
                        BackendPreference.CPU -> "Forces CPU execution."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Temperature
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Temperature", style = MaterialTheme.typography.titleSmall)
                    Text(String.format("%.2f", temperature), style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = temperature.toFloat(),
                    onValueChange = { temperature = (it * 100).roundToInt() / 100.0 },
                    valueRange = 0.0f..1.5f,
                    steps = 14
                )

                // Top-K
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Top-K", style = MaterialTheme.typography.titleSmall)
                    Text("$topK", style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = topK.toFloat(),
                    onValueChange = { topK = it.roundToInt() },
                    valueRange = 1f..100f,
                    steps = 98
                )

                // Top-P
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Top-P", style = MaterialTheme.typography.titleSmall)
                    Text(String.format("%.2f", topP), style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = topP.toFloat(),
                    onValueChange = { topP = (it * 100).roundToInt() / 100.0 },
                    valueRange = 0.1f..1.0f,
                    steps = 8
                )

                // Max Tokens
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Max Output Tokens", style = MaterialTheme.typography.titleSmall)
                    Text("$maxTokens", style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = { maxTokens = it.roundToInt() },
                    valueRange = 256f..4096f,
                    steps = 14
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        currentSettings.copy(
                            backendPreference = backendPref,
                            temperature = temperature,
                            topK = topK,
                            topP = topP,
                            maxTokens = maxTokens
                        ),
                        selectedStyle
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SurfaceSelectableRow(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onSelect,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
