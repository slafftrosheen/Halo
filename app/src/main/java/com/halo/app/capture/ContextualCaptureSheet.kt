package com.halo.app.capture

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText

import androidx.compose.runtime.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextualCaptureSheet(controller: ContextualCaptureController, onVisionSetup: () -> Unit) {
    val capture by controller.state.collectAsStateWithLifecycle()
    val visible by controller.visible.collectAsStateWithLifecycle()
    val current = capture ?: return
    if(!visible) return
    var more by remember(current.captureId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var extractionError by remember(current.captureId) { mutableStateOf<String?>(null) }
    var previewLoaded by remember(current.mediaRef.id) { mutableStateOf(false) }
    var modelFact by remember(current.captureId) { mutableStateOf(false) }
    var attachPicker by remember(current.captureId) { mutableStateOf(false) }
    val saveTitle by controller.saveTitle.collectAsStateWithLifecycle()
    val saveDescription by controller.saveDescription.collectAsStateWithLifecycle()
    val notes by Graph.notebookRepository.observeNotes().collectAsStateWithLifecycle(emptyList())
    var fact by remember(current.captureId) { mutableStateOf("") }
    var mode by remember(current.captureId) { mutableStateOf(CaptureExtractionMode.READ_TEXT) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, current.mediaRef.id) {
        value = try {
            val input = Graph.mediaAssetResolver.open(current.mediaRef)
            val prepared = Graph.imageInputPreparer.prepare(input, current.mediaRef, ImageInputLimits(maxWidth=640,maxHeight=640,maxPixels=409600))
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { android.graphics.BitmapFactory.decodeByteArray(prepared.content.bytes,0,prepared.content.bytes.size) }
        } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch(_: Exception) { null }
        finally { previewLoaded = true }
    }
    ModalBottomSheet(onDismissRequest = { controller.visible.value = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(uiText("Capture review"), style=MaterialTheme.typography.headlineSmall)
            Text(current.label, style = MaterialTheme.typography.bodyMedium)
            Text(uiText(when(current.quality) {
                CaptureQuality.AI_THUMBNAIL -> "Preview / AI thumbnail"
                CaptureQuality.ORIGINAL -> "Original image"
                CaptureQuality.UNKNOWN -> "Received image · quality unknown"
            }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            bitmap?.let { image ->
                com.halo.app.ui.workspace.InspectableImage(image, uiText("Captured image"), Modifier.fillMaxWidth().height(240.dp))
            } ?: Surface(Modifier.fillMaxWidth(), shape = com.halo.app.ui.design.HaloShapes.control, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    if (!previewLoaded) CircularProgressIndicator(Modifier.size(28.dp))
                    Text(uiText(if (previewLoaded) "Image preview unavailable. Open the saved source or choose another image." else "Loading preview…"), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(uiText("What should halo do?"), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick={scope.launch {
                try {
                    val id=controller.save()
                    controller.visible.value=false
                    Graph.extractionReview.workspace(id)
                } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch(error: Exception) { extractionError=error.message ?: "Extraction unavailable; capture is kept" }
            }},enabled=bitmap != null && current.status != CaptureStatus.SAVING) { Text(uiText("Extract text on phone")) }
            extractionError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            val saving = current.status == CaptureStatus.SAVING
            Button(onClick={ controller.action(CaptureAction.ASK) },enabled=bitmap != null && !saving) { Text(uiText("Ask halo")) }
            Text(uiText("Media sharing follows the Private / Cloud switch at the top of halo."), style = MaterialTheme.typography.bodySmall)
            com.halo.app.ui.components.CloudModeResolution(required = false)
            TextButton(onClick={controller.visible.value=false;onVisionSetup()}) { Text(uiText("Choose vision model")) }
            OutlinedButton(onClick={ controller.action(CaptureAction.STUDY) },enabled=bitmap != null && !saving) { Text(uiText("Study this")) }
            Row(Modifier.horizontalScroll(rememberScrollState())) { CaptureExtractionMode.entries.forEach { value ->
                FilterChip(mode==value,{mode=value},label={Text(uiText(when(value){CaptureExtractionMode.READ_TEXT->"Text";CaptureExtractionMode.IDENTIFY_OBJECTS->"Objects";else->"Key information"}))})
            } }
            OutlinedButton(onClick={controller.action(CaptureAction.EXTRACT,mode)},enabled=bitmap != null && !saving) { Text(uiText("Extract information")) }
            Text(uiText("Model-extracted results are drafts, not verified OCR. Review uncertain values before using them."),style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={controller.action(CaptureAction.SAVE)},enabled=!saving && bitmap != null && current.workspaceObjectId == null) { Text(uiText(if(saving) "Saving…" else if(current.workspaceObjectId != null) "Saved to Notebook" else "Save to Notebook")) }
            current.workspaceObjectId?.let { TextButton(onClick=controller::openSaved) { Text(uiText("Open saved source")) } }
            TextButton(onClick={more=!more}) { Text(uiText(if(more) "Less" else "More · attach and remember")) }
            if(more) {
            if(current.workspaceObjectId == null) {
                HaloTextField(saveTitle,{controller.saveTitle.value=it.take(160)},label={Text(uiText("Title (optional)"))},modifier=Modifier.fillMaxWidth(), kind = VoiceFieldKind.SHORT)
                Text(uiText("Collection · Inbox"))
                HaloTextField(saveDescription,{controller.saveDescription.value=it.take(8000)},label={Text(uiText("Optional note"))},modifier=Modifier.fillMaxWidth(), kind = VoiceFieldKind.TEXT)
            }
            TextButton(onClick={attachPicker=!attachPicker},enabled=!saving) { Text(uiText("Attach to existing note")) }
            if(attachPicker && notes.isEmpty()) Text(uiText("No notes yet. Save this image to Notebook first."), style = MaterialTheme.typography.bodySmall)
            if(attachPicker) notes.take(20).forEach { note -> TextButton(onClick={controller.attachToNote(note.id)}, enabled = !saving) { Text(note.title) } }
            Row { Checkbox(modelFact,{modelFact=it}); Text(uiText("This selected fact was suggested by a model")) }
            HaloTextField(fact,{fact=it.take(2000)},label={Text(uiText("Fact to remember (edit before review)"))},modifier=Modifier.fillMaxWidth(), kind = VoiceFieldKind.TEXT)
            TextButton(onClick={controller.reviewMemory(fact,modelFact)},enabled=fact.isNotBlank() && !saving) { Text(uiText("Review memory")) }
            }
            current.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            TextButton(onClick=controller::discard,enabled=!saving) { Text(uiText("Discard temporary capture")) }
        }
    }
}
