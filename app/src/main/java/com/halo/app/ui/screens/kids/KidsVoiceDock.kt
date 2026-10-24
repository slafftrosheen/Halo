package com.halo.app.ui.screens.kids

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** Always visible; capture and reading remain owned by the shared speech/runtime paths. */
@Composable
internal fun KidsVoiceDock(status: String, busy: Boolean, saving: Boolean,
    onSpeak: () -> Unit, onType: () -> Unit, onStop: () -> Unit, onHelp: () -> Unit) {
    val shellOwnsOrb = com.halo.app.ui.presence.LocalShellHaloOrb.current
    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
        .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(uiText(if (status == "Talk or type") "Tap and talk" else status),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp))
        Box(Modifier.fillMaxWidth()) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(44.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                FilledTonalIconButton(onClick = onType, enabled = !saving,
                    modifier = Modifier.size(56.dp), shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface)) {
                    HaloIcon(HaloGlyph.NOTE, uiText("Type instead"), Modifier.size(28.dp))
                }
                if (!shellOwnsOrb) Spacer(Modifier.size(72.dp))
                FilledTonalIconButton(onClick = onHelp, modifier = Modifier.size(56.dp), shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface)) {
                    HaloIcon(HaloGlyph.QUESTION, uiText("What can I say?"), Modifier.size(28.dp))
                }
            }
        }
                if (!shellOwnsOrb) FilledIconButton(onClick = if (busy) onStop else onSpeak, enabled = !saving,
                    modifier = Modifier.align(Alignment.Center).offset(y = (-15).dp).size(72.dp), shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (busy) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (busy) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimaryContainer)) {
                    HaloIcon(if (busy) HaloGlyph.STOP else HaloGlyph.LISTEN,
                        uiText(if (busy) "Stop" else "Talk to halo"), Modifier.size(32.dp))
                }
        }
    }
}
