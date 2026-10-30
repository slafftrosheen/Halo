package com.halo.app.ui.screens.kids

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            val controls: @androidx.compose.runtime.Composable (Modifier) -> Unit = { controlModifier ->
                com.halo.app.ui.zone5.Zone5ActionButton(uiText("Type instead"), "office_edit_text",
                    enabled = !saving, modifier = controlModifier.heightIn(min = 56.dp), onClick = onType)
                com.halo.app.ui.zone5.Zone5ActionButton(uiText("What can I say?"), "action_help",
                    modifier = controlModifier.heightIn(min = 56.dp), onClick = onHelp)
            }
            if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f)
                Column(Modifier.fillMaxWidth().padding(8.dp)) { controls(Modifier.fillMaxWidth()) }
            else Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) { controls(Modifier.weight(1f)) }
        }
        if (!shellOwnsOrb) FilledIconButton(onClick = if (busy) onStop else onSpeak, enabled = !saving,
            modifier = Modifier.padding(top = 8.dp).size(72.dp), shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
            val label = uiText(if (busy) "Stop" else "Talk to halo")
            androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer) {
                com.halo.app.ui.zone5.Zone5Icon(if (busy) "media_stop" else "voice_listen", HaloGlyph.LISTEN,
                    Modifier.size(32.dp).then(Modifier.semantics { contentDescription = label }))
            }
        }
    }
}
