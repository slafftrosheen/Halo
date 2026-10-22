package com.halo.app.ui.screens.home

import com.halo.app.language.uiText

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
    onPrivacy: () -> Unit = {}, onVision: () -> Unit = {}, expanded: Boolean = true,
    showSaveReview: Boolean = true) {
    val chips by model.contextChips.collectAsStateWithLifecycle()
    val notice by model.contextNotice.collectAsStateWithLifecycle()
    val review by model.saveReview.collectAsStateWithLifecycle()
    var chooser by remember { mutableStateOf(false) }
    if (expanded) {
    notice?.let { Text(uiText(it), style = MaterialTheme.typography.bodyMedium) }
    // H03: import and camera are direct actions. Only picking existing Notebook
    // sources opens a secondary chooser; the permission/capture owners are unchanged.
    Text(uiText("Add an attachment"), style = MaterialTheme.typography.titleSmall)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onCamera, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(uiText("Take a photo"))
        }
        TextButton(onClick = onImport, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(uiText("Import image"))
        }
        TextButton(onClick = { chooser = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(uiText("Notebook"))
        }
    }
    if (chips.isNotEmpty()) {
        HorizontalDivider()
        Text(uiText("Attachments and context"), style = MaterialTheme.typography.titleSmall)
    }
    chips.forEach { chip ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            chip.mediaRef?.takeIf { chip.focus.state !in setOf(ContextReferenceState.EXPIRED, ContextReferenceState.SOURCE_UNAVAILABLE) }?.let { ref ->
                com.halo.app.capture.VisualAttachmentThumbnail(com.halo.app.capture.VisualAttachment(ref, chip.title), Modifier.size(40.dp))
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(chip.title, maxLines = 2)
                // All status segments are app-authored labels, but the source title
                // above remains user content and must not be passed through uiText.
                val sourceStatus = listOf(
                    uiText(if (chip.saved) "Saved source" else "Temporary context"),
                    uiText(contextAvailabilityLabel(chip.focus.state)),
                    uiText(chip.sharing)
                ).joinToString(" · ")
                Text(sourceStatus, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick={ model.removeContext(chip.focus.category) }) { Text(uiText("Remove")) }
        }
        (chip.focus.reference as? ContextReference.WorkspaceObject)?.takeIf { chip.focus.state == ContextReferenceState.SOURCE_UNAVAILABLE }?.let { ref ->
            TextButton(onClick={ model.useUpdatedContext(ref.objectId) }) { Text(uiText("Use updated source")) }
        }
        if (chip.mediaRef != null) Row {
            if (!chip.saved) TextButton(onClick = model::saveVisualAttachment) { Text(uiText("Save image")) }
            TextButton(onClick = onPrivacy) { Text(uiText("Image privacy")) }
        }
        if(chip.focus.state in setOf(ContextReferenceState.EXPIRED,ContextReferenceState.SOURCE_UNAVAILABLE)) {
            TextButton(onClick={ chooser=true }) { Text(uiText("Reattach source")) }
        }
        (chip.focus.reference as? ContextReference.StudySession)?.let { ref ->
            val scope = rememberCoroutineScope()
            TextButton(onClick={ scope.launch { ManualDependencyGraph.interactionContext.navigate(ContextualNextActionTarget.ContinueStudy(ref.sessionId)) } }) { Text(uiText("Continue in Study")) }
        }
    }
    if (notice != null) TextButton(onClick = onVision, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(uiText("Image and model setup"))
    }
    }
    if(chooser) {
        val notes by ManualDependencyGraph.notebookRepository.observeNotes().collectAsStateWithLifecycle(emptyList())
        val sessions by ManualDependencyGraph.studyUseCases.observeActiveSessions().collectAsStateWithLifecycle(emptyList())
        val scope = rememberCoroutineScope()
        AlertDialog(onDismissRequest={ chooser=false },title={ Text(uiText("Choose context")) },text={
            Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
                TextButton(onClick={ chooser=false; onCamera() }) { Text(uiText("Take a photo")) }
                TextButton(onClick={ chooser=false; onImport() }) { Text(uiText("Import image")) }
                Text(uiText("Notebook"))
                notes.take(30).forEach { note -> TextButton(onClick={ model.selectContext(note.id); chooser=false }) { Text(note.title) } }
                sessions.take(5).forEach { session -> TextButton(onClick={ chooser=false; scope.launch {
                    ManualDependencyGraph.interactionContext.selectStudy(session.id)
                } }) { Text(uiText("Study") + ": " + (session.goal ?: uiText("Current lesson"))) } }
            }
        },confirmButton={ TextButton(onClick={ chooser=false }) { Text(uiText("Close")) } })
    }
    if (showSaveReview) review?.let { exact -> AlertDialog(onDismissRequest={ model.saveReview.value=null },
        title={ Text(uiText("Save result to Notebook?")) },
        text={ Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
            Text(uiText("Model-generated result · saving does not verify its claims"),style=MaterialTheme.typography.bodyMedium)
            Text(exact.text)
        } },confirmButton={ TextButton(onClick=model::confirmSaveResult) { Text(uiText("Save")) } },
        dismissButton={ TextButton(onClick={ model.saveReview.value=null }) { Text(uiText("Not now")) } }) }
}

internal fun contextAvailabilityLabel(state: ContextReferenceState): String = when(state) {
    ContextReferenceState.EXPIRED -> "Expired — attach again"
    ContextReferenceState.SOURCE_UNAVAILABLE -> "Source unavailable"
    ContextReferenceState.BOUND_TO_TURN -> "Attached to this request"
    ContextReferenceState.CONSUMED -> "Ready for follow-up"
    else -> "Ready"
}
