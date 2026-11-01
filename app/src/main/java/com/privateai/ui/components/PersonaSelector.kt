package com.privateai.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.privateai.persona.AiPersona

@Composable
fun PersonaSelector(
    selectedPersona: AiPersona,
    onSelectPersona: (AiPersona) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AiPersona.entries.forEach { persona ->
            val isSelected = persona == selectedPersona
            FilterChip(
                selected = isSelected,
                onClick = { onSelectPersona(persona) },
                label = { Text(persona.displayName, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = getPersonaIcon(persona),
                        contentDescription = persona.displayName,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

private fun getPersonaIcon(persona: AiPersona): ImageVector {
    return when (persona) {
        AiPersona.GENERAL -> Icons.Default.SmartToy
        AiPersona.CODING -> Icons.Default.Code
        AiPersona.DSA -> Icons.Default.Psychology
        AiPersona.DEVOPS -> Icons.Default.Terminal
        AiPersona.SYSTEM_DESIGN -> Icons.Default.Hub
        AiPersona.TROUBLESHOOTING -> Icons.Default.BugReport
    }
}
