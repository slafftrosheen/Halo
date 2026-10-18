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
import androidx.compose.ui.graphics.asImageBitmap
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
    var more by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var extractionError by remember { mutableStateOf<String?>(null) }
    var previewLoaded by remember(current.mediaRef.id) { mutableStateOf(false) }
    var modelFact by remember(current.captureId) { mutableStateOf(false) }
    var attachPicker by remember { mutableStateOf(false) }
    val saveTitle by controller.saveTitle.collectAsStateWithLifecycle()
    val saveDescription by controller.saveDescription.collectAsStateWithLifecycle()
    val notes by Graph.notebookRepository.observeNotes().collectAsStateWithLifecycle(emptyList())
    var fact by remember(current.captureId) { mutableStateOf("") }
    var cloud by remember(current.captureId, current.workspaceObjectId) { mutableStateOf(false) }
    var mode by remember(current.captureId) { mutableStateOf(CaptureExtractionMode.READ_TEXT) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, current.mediaRef.id) {
        value = try {
            val input = Graph.mediaAssetResolver.open(current.mediaRef)
            val prepared = Graph.imageInputPreparer.prepare(input, current.mediaRef, ImageInputLimits(maxWidth=640,maxHeight=640,maxPixels=409600))
            android.graphics.BitmapFactory.decodeByteArray(prepared.content.bytes,0,prepared.content.bytes.size)
        } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch(_: Exception) { null }
        finally { previewLoaded = true }
    }
    ModalBottomSheet(onDismissRequest = { controller.visible.value = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(uiText("Capture review"), style=MaterialTheme.typography.headlineSmall)
            Text("${current.label} · ${when(current.quality) {CaptureQuality.AI_THUMBNAIL -> "Preview / AI thumbnail";CaptureQuality.ORIGINAL -> "Original image";CaptureQuality.UNKNOWN -> "Received image · quality unknown"}} · ${current.width} × ${current.height}")
            current.captureTimestampMs?.let { Text("Capture reported at ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}",style=MaterialTheme.typography.bodySmall) }
            bitmap?.let { Image(it.asImageBitmap(), "Captured image", Modifier.fillMaxWidth().heightIn(max=300.dp)) }
                ?: Text(if(previewLoaded) "Image preview unavailable. Open the saved source or choose another image." else "Loading preview…")
            Text(uiText("What should Halo do?"))
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
            Button(onClick={ controller.action(CaptureAction.ASK) },enabled=bitmap != null && !saving) { Text(uiText("Ask Halo")) }
            Text(uiText("Image analysis requires an eligible vision model and your image privacy permissions. Captures are local-only by default."),style=MaterialTheme.typography.bodySmall)
            Row {
                Checkbox(cloud, { allow ->
                    cloud = allow
                    current.workspaceObjectId?.let { Graph.workspaceMediaAiPolicy.setAccess(it, if(allow) com.halo.app.data.workspace.WorkspaceAiAccess.ALLOW_CONFIGURED_CLOUD else com.halo.app.data.workspace.WorkspaceAiAccess.LOCAL_ONLY) }
                        ?: Graph.workspaceMediaAiPolicy.allowTemporaryCloud(current.mediaRef.id, allow)
                })
                Text(uiText("Allow this image for configured cloud analysis (global permissions still apply)"), Modifier.weight(1f))
            }
            TextButton(onClick={controller.visible.value=false;onVisionSetup()}) { Text(uiText("Choose vision model")) }
            Button(onClick={ controller.action(CaptureAction.STUDY) },enabled=bitmap != null && !saving) { Text(uiText("Study this")) }
            Row(Modifier.horizontalScroll(rememberScrollState())) { CaptureExtractionMode.entries.forEach { value ->
                FilterChip(mode==value,{mode=value},label={Text(when(value){CaptureExtractionMode.READ_TEXT->"Text";CaptureExtractionMode.IDENTIFY_OBJECTS->"Objects";else->"Key information"})})
            } }
            Button(onClick={controller.action(CaptureAction.EXTRACT,mode)},enabled=bitmap != null && !saving) { Text(uiText("Extract information")) }
            Text(uiText("Model-extracted results are drafts, not verified OCR. Review uncertain values before using them."),style=MaterialTheme.typography.bodySmall)
            Button(onClick={controller.action(CaptureAction.SAVE)},enabled=!saving && bitmap != null) { Text(if(saving) "Saving…" else if(current.workspaceObjectId != null) "Saved to Notebook" else "Save to Notebook") }
            current.workspaceObjectId?.let { TextButton(onClick=controller::openSaved) { Text(uiText("Open saved source")) } }
            TextButton(onClick={more=!more}) { Text(if(more) "Less" else "More · attach and remember") }
            if(more) {
            if(current.workspaceObjectId == null) {
                HaloTextField(saveTitle,{controller.saveTitle.value=it.take(160)},label={Text(uiText("Title (optional)"))},modifier=Modifier.fillMaxWidth(), kind = VoiceFieldKind.SHORT)
                Text(uiText("Collection · Inbox"))
                HaloTextField(saveDescription,{controller.saveDescription.value=it.take(8000)},label={Text(uiText("Optional note"))},modifier=Modifier.fillMaxWidth(), kind = VoiceFieldKind.TEXT)
            }
            TextButton(onClick={attachPicker=!attachPicker},enabled=!saving) { Text(uiText("Attach to existing note")) }
            if(attachPicker) notes.take(20).forEach { note -> TextButton(onClick={controller.attachToNote(note.id)}) { Text(note.title) } }
            Row { Checkbox(modelFact,{modelFact=it}); Text(uiText("This selected fact was suggested by a model")) }
            HaloTextField(fact,{fact=it.take(2000)},label={Text(uiText("Fact to remember (edit before review)"))},modifier=Modifier.fillMaxWidth(), kind = VoiceFieldKind.TEXT)
            TextButton(onClick={controller.reviewMemory(fact,modelFact)},enabled=fact.isNotBlank() && !saving) { Text(uiText("Review memory")) }
            }
            current.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            TextButton(onClick=controller::discard,enabled=!saving) { Text(uiText("Discard temporary capture")) }
        }
    }
}
