package com.halo.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph
import com.halo.runtime.cognition.context.*
import kotlinx.coroutines.launch

@Composable
fun ContextContinuityControls(model: HomeViewModel,onImport: () -> Unit, onCamera: () -> Unit = {},
    onPrivacy: () -> Unit = {}, onVision: () -> Unit = {}) {
    val chips by model.contextChips.collectAsStateWithLifecycle()
    val notice by model.contextNotice.collectAsStateWithLifecycle()
    val review by model.saveReview.collectAsStateWithLifecycle()
    var chooser by remember { mutableStateOf(false) }
    notice?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
    chips.forEach { chip ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            chip.mediaRef?.takeIf { chip.focus.state !in setOf(ContextReferenceState.EXPIRED, ContextReferenceState.SOURCE_UNAVAILABLE) }?.let { ref ->
                com.halo.app.capture.VisualAttachmentThumbnail(com.halo.app.capture.VisualAttachment(ref, chip.title), Modifier.size(40.dp))
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(chip.title, maxLines = 2)
                Text("${if(chip.saved) "Saved source" else "Temporary context"} · ${contextAvailabilityLabel(chip.focus.state)} · ${chip.sharing}",style=MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick={ model.removeContext(chip.focus.category) }) { Text("Remove") }
        }
        (chip.focus.reference as? ContextReference.WorkspaceObject)?.takeIf { chip.focus.state == ContextReferenceState.SOURCE_UNAVAILABLE }?.let { ref ->
            TextButton(onClick={ model.useUpdatedContext(ref.objectId) }) { Text("Use updated source") }
        }
        if (chip.mediaRef != null) Row {
            if (!chip.saved) TextButton(onClick = model::saveVisualAttachment) { Text("Save image") }
            TextButton(onClick = onPrivacy) { Text("Image privacy") }
        }
        if(chip.focus.state in setOf(ContextReferenceState.EXPIRED,ContextReferenceState.SOURCE_UNAVAILABLE)) {
            TextButton(onClick={ chooser=true }) { Text("Reattach source") }
        }
        (chip.focus.reference as? ContextReference.StudySession)?.let { ref ->
            val scope = rememberCoroutineScope()
            TextButton(onClick={ scope.launch { ManualDependencyGraph.interactionContext.navigate(ContextualNextActionTarget.ContinueStudy(ref.sessionId)) } }) { Text("Continue in Study") }
        }
    }
    if (notice != null) TextButton(onClick = onVision) { Text("Image and model setup") }
    TextButton(onClick={ chooser=true }) { Text("Attach") }
    if(chooser) {
        val notes by ManualDependencyGraph.notebookRepository.observeNotes().collectAsStateWithLifecycle(emptyList())
        val sessions by ManualDependencyGraph.studyUseCases.observeActiveSessions().collectAsStateWithLifecycle(emptyList())
        val scope = rememberCoroutineScope()
        AlertDialog(onDismissRequest={ chooser=false },title={ Text("Choose context") },text={
            Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
                TextButton(onClick={ chooser=false; onCamera() }) { Text("Take a photo") }
                TextButton(onClick={ chooser=false; onImport() }) { Text("Import image") }
                Text("Notebook")
                notes.take(30).forEach { note -> TextButton(onClick={ model.selectContext(note.id); chooser=false }) { Text(note.title) } }
                sessions.take(5).forEach { session -> TextButton(onClick={ chooser=false; scope.launch {
                    ManualDependencyGraph.interactionContext.selectStudy(session.id)
                } }) { Text("Study: ${session.goal ?: "Current lesson"}") } }
            }
        },confirmButton={ TextButton(onClick={ chooser=false }) { Text("Close") } })
    }
    review?.let { exact -> AlertDialog(onDismissRequest={ model.saveReview.value=null },
        title={ Text("Save result to Notebook?") },
        text={ Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
            Text("Model-generated result · saving does not verify its claims",style=MaterialTheme.typography.bodySmall)
            Text(exact.text)
        } },confirmButton={ TextButton(onClick=model::confirmSaveResult) { Text("Save") } },
        dismissButton={ TextButton(onClick={ model.saveReview.value=null }) { Text("Not now") } }) }
}

internal fun contextAvailabilityLabel(state: ContextReferenceState): String = when(state) {
    ContextReferenceState.EXPIRED -> "Expired — attach again"
    ContextReferenceState.SOURCE_UNAVAILABLE -> "Source unavailable"
    ContextReferenceState.BOUND_TO_TURN -> "Attached to this request"
    ContextReferenceState.CONSUMED -> "Ready for follow-up"
    else -> "Ready"
}
