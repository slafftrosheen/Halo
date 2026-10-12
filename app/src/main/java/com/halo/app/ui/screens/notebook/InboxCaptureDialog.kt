package com.halo.app.ui.screens.notebook

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** Explicit capture into the existing Inbox. Board insertion reuses the saved object id. */
@Composable
internal fun InboxCaptureDialog(canvasId: String? = null, initialImport: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var savedId by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var failedPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var recoveryRevision by remember { mutableIntStateOf(0) }
    var retryMemo by rememberSaveable { mutableStateOf<String?>(null) }
    val recorder = remember { CanvasVoiceMemoRecorder(context.applicationContext, Graph.infra.resourceLeaseManager) }
    val recoveries = remember(retryMemo, savedId, recoveryRevision) { File(context.filesDir, "workspace/owned/voice-inbox").listFiles()
        ?.filter { it.isFile && it.extension == "m4a" && it.length() >= 1024 }?.sortedByDescending { it.lastModified() }?.take(20).orEmpty() }
    fun import(uri: Uri, staging: File? = null, mime: String? = null) {
        if (busy) return
        busy = true; message = "Saving to Inbox…"
        scope.launch {
            try {
                val file = Graph.workspaceUseCases.importForCanvas(uri, mimeType = mime)
                savedId = file.objectId.value; staging?.delete(); retryMemo = null; recoveryRevision++
                message = "Saved to Inbox."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { retryMemo = staging?.absolutePath; message = "Save failed. Your source is kept; retry or choose the file again." }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> import(uri) } }
    var importLaunched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialImport) {
        if (initialImport && !importLaunched) {
            importLaunched = true
            picker.launch(arrayOf("*/*"))
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingPhoto
        pendingPhoto = null
        if (path != null) {
            val file = File(path)
            if (!success) scope.launch { Graph.captures.discardPendingCapture(file); message = "Photo cancelled. No image was received." }
            else {
                busy = true
                scope.launch {
                    try { savedId = Graph.captures.receive(file, sourceKind = "PHONE_CAMERA", publishForInference = false); message = "Saved to Inbox." }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failedPhoto = path; message = "Photo could not be saved. Retry the saved photo." }
                    finally { busy = false }
                }
            }
        }
    }
    fun takePhoto() { scope.launch {
        try { val pending = Graph.captures.preparePhoneCapture(); pendingPhoto = pending.file.absolutePath; camera.launch(pending.uri) }
        catch (_: Exception) { message = "No camera app is available" }
    } }
    fun startMemo() {
        runCatching { check(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)); recorder.start {
            val file = recorder.stop(); recording = false
            if (file != null) import(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.captures", file), file, "audio/mp4")
            else message = "Recording was too short. Try again."
        } }
            .onSuccess { recording = true; message = "Recording…" }
            .onFailure { message = "Could not start recording. The microphone may be busy." }
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startMemo() else message = "Microphone permission denied."
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) takePhoto() else message = "Camera permission was not granted"
    }
    DisposableEffect(owner, recorder) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && recording) {
            recorder.cancel(); recording = false; message = "Recording cancelled when Canvas left the foreground."
        } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); recorder.cancel() }
    }
    fun close() { if (!busy) { recorder.cancel(); onDismiss() } }
    AlertDialog(onDismissRequest = ::close, title = { Text(uiText("Save to Inbox")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Capture now, organize later. Everything stays local unless you choose to share it."))
            OutlinedTextField(text, { text = it.take(12_000) }, label = { Text(uiText("Short note")) }, modifier = Modifier.fillMaxWidth(), enabled = !busy && !recording)
            HaloActionButton(onClick = {
                busy = true
                scope.launch {
                    try { savedId = Graph.notebookUseCases.createNote(text).id; text = ""; message = "Saved to Inbox." }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "Could not save that change. Try again." }
                    finally { busy = false }
                }
            }, enabled = !busy && !recording && text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(uiText("Save note")) }
            HaloActionButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy && !recording,
                modifier = Modifier.fillMaxWidth(), style = HaloActionStyle.SECONDARY) { Text(uiText("Import file")) }
            HaloActionButton(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto() else cameraPermission.launch(Manifest.permission.CAMERA) },
                enabled = !busy && !recording, modifier = Modifier.fillMaxWidth(), style = HaloActionStyle.SECONDARY) { Text(uiText("Photo")) }
            if (recording) {
                HaloActionButton(onClick = {
                    val file = recorder.stop(); recording = false
                    if (file == null) message = "Recording was too short. Try again."
                    else import(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.captures", file), file, "audio/mp4")
                }, modifier = Modifier.fillMaxWidth()) { Text(uiText("Stop & save")) }
                HaloActionButton(onClick = { recorder.cancel(); recording = false; message = "Recording cancelled." },
                    modifier = Modifier.fillMaxWidth(), style = HaloActionStyle.STOP) { Text(uiText("Cancel recording")) }
            } else HaloActionButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startMemo() else microphone.launch(Manifest.permission.RECORD_AUDIO)
            }, enabled = !busy, modifier = Modifier.fillMaxWidth(), style = HaloActionStyle.SECONDARY) { Text(uiText("Record memo")) }
            Text(uiText("Voice memos are limited to two minutes. Stop and save before leaving this screen."), style = MaterialTheme.typography.bodyMedium)
            recoveries.forEach { file ->
                Text(file.name, style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { import(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.captures", file), file, "audio/mp4") }, enabled = !busy && !recording) { Text(uiText("Retry saved memo")) }
                    TextButton(onClick = { file.delete(); recoveryRevision++; retryMemo = null; savedId = null; message = "Recording cancelled." }, enabled = !busy && !recording) { Text(uiText("Discard saved memo")) }
                }
            }
            failedPhoto?.let { path ->
                TextButton(onClick = {
                    busy = true
                    scope.launch {
                        try { savedId = Graph.captures.receive(File(path), sourceKind = "PHONE_CAMERA", publishForInference = false); failedPhoto = null; message = "Saved to Inbox." }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { message = "Photo could not be saved. Retry the saved photo." }
                        finally { busy = false }
                    }
                }, enabled = !busy && !recording) { Text(uiText("Retry saved photo")) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(uiText(it)) }
            if (canvasId != null && savedId != null) HaloActionButton(onClick = {
                busy = true
                scope.launch {
                    try { Graph.notebookCanvasRepository.addReferences(canvasId, listOf(requireNotNull(savedId))); message = "Saved to Inbox and added to Canvas." }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "Saved to Inbox. Canvas linking failed; add the items from Inbox." }
                    finally { busy = false }
                }
            }, enabled = !busy && !recording, modifier = Modifier.fillMaxWidth(), style = HaloActionStyle.SECONDARY) { Text(uiText("Add to current Canvas")) }
        } }, confirmButton = { TextButton(onClick = ::close, enabled = !busy) { Text(uiText("Done")) } })
}
