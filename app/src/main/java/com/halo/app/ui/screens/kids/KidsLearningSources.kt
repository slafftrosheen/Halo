package com.halo.app.ui.screens.kids

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.study.ResolvedStudySource
import com.halo.app.ui.screens.study.StudyHomeViewModel
import com.halo.app.ui.workspace.WorkspaceObjectPicker
import kotlinx.coroutines.launch

/** Intake uses existing Workspace owners; no automatic cloud extraction or fabricated exercises. */
@Composable
internal fun KidsLearningSources(model: StudyHomeViewModel, onBusyChanged: (Boolean) -> Unit = {}) {
    val selected by model.selectedIds.collectAsStateWithLifecycle()
    val candidates by model.candidates.collectAsStateWithLifecycle()
    val previews by model.previews.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableStateOf(false) }
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by owner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    var photoRequested by remember { mutableStateOf(false) }
    var permissionWanted by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    val stopEpoch by com.halo.app.ui.components.VoiceFieldCancellation.epoch.collectAsStateWithLifecycle()
    var captureEpoch by remember { mutableLongStateOf(stopEpoch) }
    fun setProcessing(value: Boolean) { processing = value; onBusyChanged(value) }
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { photoRequested = false; permissionWanted = false }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); onBusyChanged(false) }
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::importMaterial)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = capturePath; capturePath = null
        if (path != null) { setProcessing(true); scope.launch {
            val captured = java.io.File(path)
            try {
                if (success && captureEpoch == com.halo.app.ui.components.VoiceFieldCancellation.epoch.value) {
                    val received = Graph.captures.receive(captured, sourceKind = "PHONE_CAMERA", publishForInference = false)
                    if (captureEpoch == com.halo.app.ui.components.VoiceFieldCancellation.epoch.value) model.select(received)
                } else Graph.captures.discardPendingCapture(captured)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { notice = "Could not save the photo. Please try again." }
            finally { setProcessing(false) }
        } }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (permissionWanted) {
            permissionWanted = false
            if (granted) photoRequested = true else notice = "Camera permission was not granted"
        }
    }
    LaunchedEffect(photoRequested, lifecycleState) {
        if (photoRequested && lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            photoRequested = false
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionWanted = true
                cameraPermission.launch(android.Manifest.permission.CAMERA)
            } else {
                setProcessing(true)
                try {
                    val pending = Graph.captures.preparePhoneCapture()
                    if (owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                        capturePath = pending.file.absolutePath; camera.launch(pending.uri)
                    } else { Graph.captures.discardPendingCapture(pending.file); setProcessing(false) }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { setProcessing(false); throw cancelled }
                catch (_: Exception) { setProcessing(false); notice = "No camera app is available" }
            }
        }
    }
    LaunchedEffect(stopEpoch) {
        if (captureEpoch != stopEpoch) { photoRequested = false; permissionWanted = false; setProcessing(false) }
    }
    val capture = rememberKidsGrownUpGate(onVerified = {
        captureEpoch = com.halo.app.ui.components.VoiceFieldCancellation.epoch.value
        photoRequested = true
    },
        onUnavailable = { notice = "Ask a grown-up to set a device PIN or password in Android settings first." })
    val chooseNotes = rememberKidsGrownUpGate(onVerified = { picker = true },
        onUnavailable = { notice = "Ask a grown-up to set a device PIN or password in Android settings first." })
    if (picker) WorkspaceObjectPicker(candidates.filter { it.id.value !in selected },
        title = uiText("My notes"), onPick = { model.select(it.id.value); picker = false },
        onDismiss = { picker = false })
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText("Homework or my notes"), style = MaterialTheme.typography.titleMedium)
        Text(uiText("Choose material, check what it says, then tell halo which exercise to work on."),
            style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = capture, enabled = !busy && !processing,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText("Homework photo")) }
        OutlinedButton(onClick = { file.launch(arrayOf("image/*", "application/pdf", "text/*",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
            enabled = !busy && !processing, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText("Choose files")) }
        OutlinedButton(onClick = chooseNotes, enabled = !busy && !processing,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText("My notes")) }
        if (processing) {
            Text(uiText("Saving photo…")); LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        previews.forEach { source ->
            Text(source.title, style = MaterialTheme.typography.titleSmall)
            when (source) {
                is ResolvedStudySource.Text -> Text(source.passages.joinToString("\n\n") { it.text }.take(4000))
                is ResolvedStudySource.Unsupported -> Text(uiText(source.reason))
                else -> Text(uiText("This page needs an on-device vision model. If halo cannot read it, type or dictate the exercise instead."))
            }
            TextButton(onClick = { model.remove(source.objectId) }, enabled = !busy) { Text(uiText("Remove")) }
        }
        notice?.let { Text(uiText(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
