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
    val library by project.library.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var libraryOpen by rememberSaveable { mutableStateOf(false) }
    var libraryPage by rememberSaveable { mutableIntStateOf(0) }
    var shareNotice by remember { mutableStateOf<String?>(null) }
    val share = rememberKidsGrownUpGate(onVerified = { scope.launch {
        try {
            val id = project.state.value.noteId ?: return@launch
            val note = Graph.notebookUseCases.getNote(id) ?: error("Note unavailable")
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, note.title)
                putExtra(android.content.Intent.EXTRA_TEXT, note.body)
            }
            context.startActivity(android.content.Intent.createChooser(send, null))
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { shareNotice = "Could not open sharing. Your saved project is kept." }
    } }, onUnavailable = { shareNotice = "Ask a grown-up to set a device PIN or password in Android settings first." })
    var reading by rememberSaveable { mutableStateOf(false) }
    if (reading && state.noteId != null && project.isKept()) KidsStoryReader(state.noteId!!, state.draft) { reading = false }
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
                    val choice = rememberKidsSpokenChoice(uiText(kind)) { project.chooseKind(kind) }
                    OutlinedButton(onClick = choice.onClick, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                        HaloIcon(glyph, null, Modifier.size(28.dp)); Spacer(Modifier.width(12.dp)); Text(uiText(kind))
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
        if (state.editing) Text(uiText(when (state.authorship) {
            "SELF" -> "Written by you"
            "AI_ASSISTED" -> "Your idea, drafted with halo"
            "MIXED" -> "Drafted with halo, edited by you"
            else -> "Authorship history unavailable"
        }), style = MaterialTheme.typography.labelMedium)
        if (state.noteId != null && state.kind == "Story") OutlinedButton(onClick = { reading = true },
            enabled = !state.busy && project.isKept() && state.draft.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText("Read my story")) }
        if (state.noteId != null) OutlinedButton(onClick = share, enabled = !state.busy && project.isKept(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText("Share saved project · grown-up")) }
        TextButton(onClick = { libraryOpen = !libraryOpen }, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(uiText("My projects"))
        }
        if (libraryOpen) {
            val pages = (library.size + 2) / 3
            val page = libraryPage.coerceIn(0, (pages - 1).coerceAtLeast(0))
            library.drop(page * 3).take(3).forEach { item ->
                val choice = rememberKidsSpokenChoice(item.title.ifBlank { uiText("My project") }) { project.openProject(item.noteId); libraryOpen = false }
                OutlinedButton(onClick = choice.onClick, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(item.title.ifBlank { uiText("My project") })
                }
            }
            if (pages > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { libraryPage = page - 1 }, enabled = page > 0,
                    modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Previous")) }
                Text("${page + 1}/$pages")
                TextButton(onClick = { libraryPage = page + 1 }, enabled = page + 1 < pages,
                    modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Next")) }
            }
            Button(onClick = project::newProject, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText("Start a new project")) }
        }
        shareNotice?.let { Text(uiText(it)) }
        state.notice?.let { Text(uiText(it), style = MaterialTheme.typography.bodyMedium) }
    }
}
