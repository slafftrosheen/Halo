package com.halo.app.ui.screens.kids

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import kotlinx.coroutines.launch

/** Choose → shape → real generation → reviewed draft → Notebook/Canvas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KidsCreateFlow(project: KidsProjectViewModel, onOpenCanvas: ((String) -> Unit)?) {
    val state by project.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var shaping by rememberSaveable { mutableStateOf(false) }
    if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(uiText(if (state.editing && !shaping) "My project" else "What shall we make?"),
            style = MaterialTheme.typography.titleLarge)
        Text(uiText(if (state.busy && Graph.kidsPreferences.cloudAllowed.value) "Cloud may be used"
            else state.executionLabel ?: "On this phone"), style = MaterialTheme.typography.labelMedium)
        if (!state.editing || shaping) {
            if (state.kind == null) {
                listOf("Story" to HaloGlyph.READ_MODE, "Character" to HaloGlyph.HALO, "Project" to HaloGlyph.TOOL).forEach { (kind, glyph) ->
                    OutlinedButton(onClick = { project.chooseKind(kind) }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                        HaloIcon(glyph, null); Spacer(Modifier.width(12.dp)); Text(uiText(kind))
                    }
                }
            } else {
                Text(uiText(state.kind!!), style = MaterialTheme.typography.titleMedium)
                Text(uiText(when (state.kind) {
                    "Character" -> "What is your character called? What makes them special?"
                    "Project" -> "What should it do? What materials could you use?"
                    else -> "Who is the hero? What would you like to happen?"
                }))
            }
            HaloTextField(value = state.idea, onValueChange = project::editIdea, kind = VoiceFieldKind.TEXT,
                label = { Text(uiText("My idea")) }, enabled = !state.busy, maxChars = 600)
            if (state.kind != null) {
                HaloTextField(value = state.details, onValueChange = project::editDetails, kind = VoiceFieldKind.TEXT,
                    label = { Text(uiText("More about it")) }, enabled = !state.busy, maxChars = 1200)
                Button(onClick = { shaping = false; project.generate(state.idea) },
                    enabled = !state.busy && state.idea.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText("Let's make it")) }
                OutlinedButton(onClick = { shaping = false; project.beginManual(state.idea) }, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText("Write it myself")) }
            }
        }
        if (state.busy) {
            Text(uiText(if (state.operation == "SAVING") "Saving…" else "Making a draft…"))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.operation == "GENERATING") TextButton(onClick = project::stop,
                modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Stop")) }
        }
        if (state.editing && !shaping) {
            Text(uiText(if (project.isKept()) "Saved project" else "Draft"), style = MaterialTheme.typography.titleMedium)
            Text(uiText("Review it, change anything you like, then choose Keep to save it."))
            HaloTextField(value = state.draft, onValueChange = project::editDraft, kind = VoiceFieldKind.TEXT,
                enabled = !state.busy, minLines = 4, maxLines = 12, maxChars = 12000)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { project.keep(state.draft) }, enabled = !state.busy && state.draft.isNotBlank(),
                    modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Keep")) }
                OutlinedButton(onClick = { shaping = true }, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Change")) }
                TextButton(onClick = project::discard, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Discard")) }
            }
        }
        state.noteId?.let { id ->
            OutlinedButton(onClick = { scope.launch {
                Graph.interactionContext.navigate(com.halo.runtime.cognition.context.ContextualNextActionTarget.OpenSource(id))
            } }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText("Open in Notebook")) }
            if (onOpenCanvas != null) OutlinedButton(onClick = { scope.launch {
                project.prepareCanvas()?.let(onOpenCanvas)
            } }, enabled = !state.busy && project.isKept(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(uiText("Edit in Canvas"))
            }
            if (!project.isKept() && state.editing) Text(uiText("Keep your changes before opening the project in Canvas."),
                style = MaterialTheme.typography.bodySmall)
        }
        state.notice?.let { Text(uiText(it), style = MaterialTheme.typography.bodyMedium) }
    }
}
