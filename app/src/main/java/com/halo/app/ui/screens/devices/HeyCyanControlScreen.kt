package com.halo.app.ui.screens.devices

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.audio.HeyCyanAudioPairingState
import com.halo.app.capture.WearableCaptureStatus
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloActionStyle
import com.halo.app.ui.workspace.*
import com.halo.drivers.heycyan.protocol.AudioRecordingState
import com.halo.drivers.heycyan.protocol.VideoRecordingState
import com.halo.sdk.runtime.embodiment.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun HeyCyanControlScreen(deviceId: String, viewModel: DevicesViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pairing by viewModel.audioPairingState.collectAsStateWithLifecycle()
    val mediaFlow = remember(viewModel) {
        viewModel.mediaCoordinator()?.state ?: MutableStateFlow(WearableCaptureStatus())
    }
    val media by mediaFlow.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onAudioAssociationResult(it.resultCode, it.data)
    }
    LaunchedEffect(deviceId) {
        viewModel.restoreAudioAssociation(deviceId)
        viewModel.audioConfirmationRequests.collect { launcher.launch(IntentSenderRequest.Builder(it).build()) }
    }
    val scope = rememberCoroutineScope()
    var reviewing by remember(deviceId) { mutableStateOf(false) }
    var reviewFailed by remember(deviceId) { mutableStateOf(false) }
    val device = state.devices.find { it.id == deviceId }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        HaloWorkspaceHeader(device?.name ?: uiText("My glasses"), onBack = onBack)
        GlassesControlsContent(device, pairing,
            media.takeIf { it.deviceId == deviceId } ?: WearableCaptureStatus(),
            onConnect = {
                if (device?.sessionState in setOf(SessionState.READY, SessionState.CONNECTED,
                        SessionState.HANDSHAKE, SessionState.CAPABILITY_SYNC, SessionState.STATE_SYNC,
                        SessionState.CONNECTING)) viewModel.disconnectDevice(deviceId)
                else viewModel.connectDevice(deviceId)
            },
            onPhoto = { viewModel.takePhoto(deviceId) },
            onVideo = {
                if (device?.videoState == VideoRecordingState.RECORDING) viewModel.stopVideo(deviceId)
                else viewModel.startVideo(deviceId)
            },
            onAudio = {
                if (device?.audioState == AudioRecordingState.RECORDING) viewModel.stopAudioRecording(deviceId)
                else viewModel.startAudioRecording(deviceId)
            },
            onReviewPhoto = {
                if (!reviewing) {
                    reviewing = true; reviewFailed = false
                    scope.launch {
                        try { reviewFailed = viewModel.capturePhotoForReview(deviceId).isFailure }
                        finally { reviewing = false }
                    }
                }
            },
            onCopy = { viewModel.syncMedia(deviceId) }, onRefresh = { viewModel.refreshMedia(deviceId) },
            onConnectAudio = { viewModel.connectAudio(deviceId) },
            onAutoConnect = { viewModel.toggleAutoConnect(deviceId, it) },
            onForget = { viewModel.forgetDevice(deviceId); onBack() },
            reviewing = reviewing, reviewFailed = reviewFailed,
            controls = {
                if (device?.hasLiveGlasses == true && device.sessionState == SessionState.READY) {
                    AccessoryControlsPanel(com.halo.app.di.ManualDependencyGraph.hostGraph.heyCyanHost.controlEndpointId)
                } else Text(uiText("Connect these glasses to change their buttons."))
            })
        Spacer(Modifier.height(16.dp))
    }
}

/** D04: display strings never grant capture authority. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GlassesControlsContent(
    device: GenericDeviceUiState?, pairing: HeyCyanAudioPairingState, media: WearableCaptureStatus,
    onConnect: () -> Unit, onPhoto: () -> Unit, onVideo: () -> Unit, onAudio: () -> Unit,
    onReviewPhoto: () -> Unit, onCopy: () -> Unit, onRefresh: () -> Unit,
    onConnectAudio: () -> Unit, onAutoConnect: (Boolean) -> Unit, onForget: () -> Unit,
    reviewing: Boolean = false, reviewFailed: Boolean = false, controls: @Composable () -> Unit = {},
) {
    val ready = device?.sessionState == SessionState.READY && device.hasLiveGlasses
    val connecting = device?.sessionState in setOf(SessionState.CONNECTING, SessionState.CONNECTED,
        SessionState.HANDSHAKE, SessionState.CAPABILITY_SYNC, SessionState.STATE_SYNC) ||
        (device?.sessionState == SessionState.READY && !ready)
    val available = ready && !media.busy && !reviewing
    var forget by rememberSaveable(device?.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        HaloWorkspaceCard {
            Text(uiText(when {
                ready -> "Camera controls ready"
                device?.sessionState == SessionState.CONNECTING -> "Connecting…"
                connecting -> "Connected · getting ready"
                device?.sessionState == SessionState.ERROR -> "Connection failed"
                else -> "Saved · not connected"
            }), style = MaterialTheme.typography.titleMedium)
            Text(uiText(if (ready) "Sound is checked separately below." else
                "Connect your glasses before taking photos or recording."))
            if (ready) FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassesFact("Battery", device?.battery ?: "Unknown")
                GlassesFact("Wearing", when (device?.wearStateText) {
                    "WORN" -> "Yes"; "NOT_WORN" -> "No"; else -> "Not checked yet"
                })
                device?.mediaPhotoCount?.let { GlassesFact("Photos on glasses", it.toString()) }
            }
            if (device?.healthWarning != null) Text(uiText("These glasses need attention. Copy your photos, then reconnect."),
                color = MaterialTheme.colorScheme.error)
            HaloWorkspaceButton(uiText(if (ready || connecting) "Disconnect" else "Connect"), onConnect,
                enabled = device != null && device.sessionState != SessionState.DISCONNECTING,
                primary = !ready && !connecting)
        }
        HaloWorkspaceSection(uiText("Capture")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HaloWorkspaceButton(uiText(when (device?.photoState) {
                        PhotoActionState.CAPTURING -> "Capturing…"
                        PhotoActionState.REQUESTED -> "Photo requested"
                        PhotoActionState.FAILED -> "Retry photo"
                        else -> "Take photo"
                    }), onPhoto, enabled = available && device?.photoState != PhotoActionState.CAPTURING, primary = false)
                    val recording = device?.videoState == VideoRecordingState.RECORDING
                    HaloWorkspaceButton(uiText(if (recording) "Stop video" else "Start video"), onVideo,
                        enabled = ready && (recording || available) && device?.videoState !in
                            setOf(VideoRecordingState.STARTING, VideoRecordingState.STOPPING),
                        actionStyle = if (recording) HaloActionStyle.STOP else HaloActionStyle.SECONDARY)
                    val audio = device?.audioState == AudioRecordingState.RECORDING
                    HaloWorkspaceButton(uiText(if (audio) "Stop audio" else "Start audio"), onAudio,
                        enabled = ready && (audio || available),
                        actionStyle = if (audio) HaloActionStyle.STOP else HaloActionStyle.SECONDARY)
                }
                Text(uiText("Review a fresh photo before choosing what halo may use. Taking it does not send it to an AI service."))
                HaloWorkspaceButton(uiText(if (reviewing) "Opening photo…" else "Review a photo with halo"),
                    onReviewPhoto, enabled = available, primary = ready)
                if (reviewFailed) Text(uiText("The photo could not be opened. Check the glasses connection and try again."),
                    color = MaterialTheme.colorScheme.error)
            }
        }
        HaloWorkspaceSection(uiText("Photos")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiText("Photos stay on your glasses until copied. Copied photos appear in Notebook."))
                val copyStatus = when {
                    media.failed && media.captureRequested -> uiText("The photo request failed. Check the glasses connection and try again.")
                    media.failed -> uiText("Photos could not be copied. Keep the glasses nearby and try again.")
                    media.captureRequested && media.busy -> uiText("Requesting a photo…")
                    media.captureRequested -> uiText("Photo requested. Copy photos to receive it.")
                    media.busy -> uiText("Copying photos from your glasses…")
                    media.waitingForConnection -> uiText("Waiting for the glasses to allow photo transfer.")
                    media.lastSync != null -> uiText("Last copy completed: %1\$d photos added.", media.received)
                    else -> uiText("No completed copy yet.")
                }
                Text(copyStatus, style = MaterialTheme.typography.bodyMedium)
                if (media.busy && media.itemIndex != null && media.itemTotal != null) {
                    Text(uiText("Photo %1\$d of %2\$d", media.itemIndex, media.itemTotal))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HaloWorkspaceButton(uiText(if (media.busy && !media.captureRequested) "Copying…" else "Copy photos"), onCopy,
                        enabled = available, primary = false)
                    HaloWorkspaceButton(uiText("Check for photos"), onRefresh, enabled = available, primary = false)
                }
            }
        }
        HaloWorkspaceSection(uiText("Sound")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiText(glassesAudioLabel(device?.id, pairing)))
                Text(uiText("A connected audio output does not confirm that you heard halo."))
                val pairingBusy = pairing == HeyCyanAudioPairingState.Discovering ||
                    pairing == HeyCyanAudioPairingState.AwaitingUserConfirmation || pairing is HeyCyanAudioPairingState.Bonding
                HaloWorkspaceButton(uiText(if (pairingBusy) "Connecting audio…" else "Connect audio"),
                    onConnectAudio, enabled = ready && !pairingBusy, primary = false)
            }
        }
        HaloWorkspaceSection(uiText("Buttons")) { Column(Modifier.padding(12.dp)) { controls() } }
        HaloWorkspaceToggleRow(title = uiText("Auto-connect"),
            subtitle = uiText("Reconnect when this saved device becomes available."),
            checked = device?.autoConnect == true, onCheckedChange = onAutoConnect, enabled = device != null)
        TextButton(onClick = { forget = true }, enabled = device != null, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(uiText("Forget these glasses…"), color = MaterialTheme.colorScheme.error)
        }
    }
    if (forget) AlertDialog(onDismissRequest = { forget = false }, title = { Text(uiText("Forget these glasses?")) },
        text = { Text(uiText("The saved connection will be removed. Photos already copied to this phone stay in Notebook.")) },
        confirmButton = { TextButton(onClick = { forget = false; onForget() }) { Text(uiText("Forget device")) } },
        dismissButton = { TextButton(onClick = { forget = false }) { Text(uiText("Cancel")) } })
}

@Composable
private fun GlassesFact(label: String, value: String) {
    Column { Text(uiText(label), style = MaterialTheme.typography.labelMedium)
        Text(uiText(value), style = MaterialTheme.typography.titleMedium) }
}

internal fun glassesOwnsReadyCapture(deviceId: String, activeDeviceId: String?, state: SessionState?): Boolean =
    activeDeviceId != null && deviceId.equals(activeDeviceId, ignoreCase = true) && state == SessionState.READY

internal fun glassesAudioLabel(deviceId: String?, state: HeyCyanAudioPairingState): String = when (state) {
    is HeyCyanAudioPairingState.AudioConnected -> if (state.association.wearableDeviceId == deviceId)
        "Glasses audio connected" else "Sound is not connected for these glasses."
    is HeyCyanAudioPairingState.Associated -> if (state.association.wearableDeviceId == deviceId)
        "Paired · waiting for sound" else "Sound is not connected for these glasses."
    is HeyCyanAudioPairingState.Failed -> "Sound could not connect. Try connecting audio again."
    is HeyCyanAudioPairingState.Bonding, HeyCyanAudioPairingState.Discovering -> "Connecting audio…"
    HeyCyanAudioPairingState.AwaitingUserConfirmation -> "Waiting for your approval in Android"
    else -> "Sound is not connected for these glasses."
}
