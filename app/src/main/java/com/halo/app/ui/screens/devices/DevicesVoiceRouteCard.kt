package com.halo.app.ui.screens.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

@Composable
internal fun DevicesVoiceRouteCard(actualOutput: String?, intendedOutput: String?, onSoundSettings: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = HaloShapes.control, color = MaterialTheme.colorScheme.secondaryContainer) {
                    HaloIcon(HaloGlyph.SPEAK, null, Modifier.padding(10.dp).size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(uiText("Voice and sound"), style = MaterialTheme.typography.titleMedium)
                    Text(uiText("Choose a voice and output provider"), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (intendedOutput != null) {
                Text(uiText("Intended output: %1\$s", intendedOutput), style = MaterialTheme.typography.bodyMedium)
                Text(uiText("Last voice output: %1\$s", actualOutput ?: uiText("Not played")),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onSoundSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Sound settings")) }
        }
    }
}
