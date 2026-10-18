package com.halo.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.fx.rememberHaloReducedMotion
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** Real sentence/word ranges only. Pausing resumes from the start of the current sentence. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HaloReadAloudBar(allowVoiceSettings: Boolean = true, onVoiceSettings: () -> Unit) {
    val controller = remember { Graph.readAloud }
    val state by controller.state.collectAsStateWithLifecycle()
    val followWords by controller.followWords.collectAsStateWithLifecycle()
    val current = state ?: return
    val reduced = rememberHaloReducedMotion()
    var speeds by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.halo.app.ui.fx.HaloReadAloudFx(current.playing, Modifier.size(24.dp))
                Text(uiText(if (current.limited) "Read first part aloud" else "Read aloud") + " · ${current.index + 1}/${current.sentences.size}",
                    style = MaterialTheme.typography.labelLarge)
            }
            val highlight = MaterialTheme.colorScheme.primary.copy(alpha = .18f)
            val text = buildAnnotatedString {
                append(current.current)
                if (followWords && current.playing && current.range == null)
                    addStyle(SpanStyle(textDecoration = TextDecoration.Underline), 0, current.current.length)
                current.range?.takeIf { followWords && it.first >= 0 && it.last < current.current.length }?.let { range ->
                    addStyle(if (reduced) SpanStyle(textDecoration = TextDecoration.Underline)
                        else SpanStyle(background = highlight),
                        range.first, range.last + 1)
                }
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            current.notice?.let { Text(uiText(it), style = MaterialTheme.typography.bodySmall) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { controller.sentence(-1) }, enabled = current.index > 0,
                    modifier = Modifier.size(48.dp)) { HaloIcon(HaloGlyph.PREVIOUS, uiText("Previous sentence")) }
                TextButton(onClick = controller::toggle, modifier = Modifier.heightIn(min = 48.dp)) {
                    HaloIcon(if (current.playing || current.preparing) HaloGlyph.PAUSE else HaloGlyph.PLAY, null)
                    Text(uiText(if (current.preparing) "Cancel voice preparation" else if (current.playing) "Pause reading" else "Resume reading"))
                }
                IconButton(onClick = { controller.sentence(1) }, enabled = current.index < current.sentences.lastIndex,
                    modifier = Modifier.size(48.dp)) { HaloIcon(HaloGlyph.NEXT, uiText("Next sentence")) }
                TextButton(onClick = controller::stop, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Stop")) }
                TextButton(onClick = { speeds = !speeds }, modifier = Modifier.heightIn(min = 48.dp)) { Text("${current.speed}×") }
                if (allowVoiceSettings) TextButton(onClick = onVoiceSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Voice")) }
            }
            if (speeds) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(.75, 1.0, 1.25, 1.5).forEach { rate ->
                    FilterChip(current.speed == rate, { controller.speed(rate) }, { Text("${rate}×") })
                }
            }
        }
    }
}

@Composable
fun HaloReadAloud(text: String, enabled: Boolean = true, label: String? = null, compact: Boolean = false) {
    HaloVoiceTextActions(text = "", onText = {}, allowDictation = false,
        readText = text, enabled = enabled, readActionLabel = label, compactRead = compact)
}
