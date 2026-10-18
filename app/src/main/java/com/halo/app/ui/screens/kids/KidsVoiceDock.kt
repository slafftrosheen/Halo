package com.halo.app.ui.screens.kids

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** Always visible; capture and reading remain owned by the shared speech/runtime paths. */
@Composable
internal fun KidsVoiceDock(status: String, busy: Boolean, saving: Boolean,
    onSpeak: () -> Unit, onType: () -> Unit, onStop: () -> Unit, onHelp: () -> Unit) {
    Surface(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp),
        shape = com.halo.app.ui.design.HaloShapes.sheet,
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(uiText(status), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = if (busy) onStop else onType, modifier = Modifier.size(56.dp)) {
                    HaloIcon(if (busy) HaloGlyph.STOP else HaloGlyph.NOTE, uiText(if (busy) "Stop" else "Type instead"))
                }
                FilledIconButton(onClick = onSpeak, enabled = !saving, modifier = Modifier.size(72.dp)) {
                    HaloIcon(HaloGlyph.LISTEN, uiText("Talk to halo"), Modifier.size(32.dp))
                }
                IconButton(onClick = onHelp, modifier = Modifier.size(56.dp)) { HaloIcon(HaloGlyph.QUESTION, uiText("What can I say?")) }
            }
        }
    }
}
