package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.audio.readingProvenance
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.components.LocalPrivateSpeech
import com.halo.app.ui.components.VoiceFieldCancellation
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.theme.HaloAccent
import com.halo.app.ui.theme.brandAccent
import com.halo.sdk.provider.RemoteDataCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** Explicit read-back shares the normal playback owner. The displayed section belongs to this note's request. */
@Composable
internal fun NoteReadBack(noteId: String, text: String) {
    if (LocalInspectionMode.current || text.isBlank()) return
    val reader = remember { Graph.readAloud }
    val state by reader.state.collectAsStateWithLifecycle()
    val followWords by reader.followWords.collectAsStateWithLifecycle()
    val appearance = LocalNotePresentation.current
    val privateSpeech = LocalPrivateSpeech.current
    val registry = remember { Graph.speechSynthesisRegistry }
    val requestId = remember(noteId, text) { UUID.randomUUID().toString() }
    val current = state?.takeIf { it.id == requestId }
    var session by remember(requestId) { mutableStateOf<String?>(null) }
    var waiting by remember(requestId) { mutableStateOf(false) }
    var failed by remember(requestId) { mutableStateOf(false) }

    DisposableEffect(requestId) { onDispose { session?.let(reader::stopSession) } }
    key(requestId) {
        val scope = rememberCoroutineScope()
        TextButton(onClick = {
            if (current != null) reader.toggle()
            else {
                val providerId = registry.selector.ttsSelection.value.id
                val provider = registry.providers.firstOrNull { it.metadata.id == providerId }
                if (providerId != "AUTOMATIC" && provider == null) {
                    failed = true
                    return@TextButton
                }
                VoiceFieldCancellation.stop()
                val epoch = VoiceFieldCancellation.epoch.value
                waiting = true
                failed = false
                scope.launch {
                    try {
                        val released = withTimeoutOrNull(3_000) {
                            VoiceFieldCancellation.activeFields.first { it.isEmpty() }
                            true
                        } == true
                        if (!released) {
                            failed = true
                            return@launch
                        }
                        val sources = setOf(noteId)
                        val category = RemoteDataCategory.WORKSPACE
                        val provenance = if (!privateSpeech && provider?.metadata?.local == false) withContext(Dispatchers.IO) {
                            readingProvenance(category, sources)
                        } else null
                        if (registry.selector.ttsSelection.value.id != providerId) {
                            failed = true
                            return@launch
                        }
                        if (VoiceFieldCancellation.epoch.value == epoch)
                            session = reader.start(requestId, text, providerId,
                                cloudApproved = provenance != null, language = null, remoteProvenance = provenance,
                                beforeSentence = { provenance == null || readingProvenance(category, sources) == provenance })
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                    } finally {
                        waiting = false
                    }
                }
            }
        }, enabled = !waiting, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (current == null) stringResource(R.string.note_look_read_back) else uiText(
                if (current.preparing) "Cancel voice preparation" else if (current.playing) "Pause reading" else "Resume reading"))
        }
    }
    if (failed) Text(uiText("Reading aloud is unavailable with the current voice settings."), style = MaterialTheme.typography.bodySmall)
    if (current != null) {
        val ink = brandAccent(false, HaloAccent.LILAC)
        val line = buildAnnotatedString {
            append(current.current)
            if (current.playing && followWords) {
                val range = current.range?.takeIf { it.first >= 0 && it.last < current.current.length }
                if (range != null) addStyle(SpanStyle(color = ink.onFill, background = ink.fill), range.first, range.last + 1)
                else addStyle(SpanStyle(textDecoration = TextDecoration.Underline), 0, current.current.length)
            }
        }
        Surface(shape = HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(line, style = appearance.textStyle(MaterialTheme.typography.bodyMedium), maxLines = 6)
                current.notice?.let { Text(uiText(it), style = MaterialTheme.typography.bodySmall) }
                if (current.speed > .75 && current.notice == null) TextButton(
                    onClick = { reader.speed(listOf(1.25, 1.0, .75).first { it < current.speed }) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(uiText("Slower")) }
            }
        }
    }
}
