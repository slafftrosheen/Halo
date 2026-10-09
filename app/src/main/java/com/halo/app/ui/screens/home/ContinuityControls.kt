package com.halo.app.ui.screens.home

import com.halo.app.language.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.runtime.cognition.continuity.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContinuityControls(model: HomeViewModel, relevant: Boolean, openCapture: (String) -> Unit) {
    val continuity by model.continuity.collectAsStateWithLifecycle()
    val endpoints by model.continuityEndpoints.collectAsStateWithLifecycle()
    val blocked by model.blockedRecovery.collectAsStateWithLifecycle()
    val capture by model.recentCapture.collectAsStateWithLifecycle()
    val audioOwner = com.halo.app.di.ManualDependencyGraph.haloSpeechOutputCoordinator.session
    val affinity by audioOwner.affinity.collectAsStateWithLifecycle()
    val audioState by audioOwner.state.collectAsStateWithLifecycle()
    val activeAffinity = affinity?.takeIf { it.interactionId == continuity.interactionId }
    val speaking = audioState as? com.halo.app.audio.HaloAudioSessionState.Speaking
    val replyLabel = speaking?.route?.androidDeviceName ?: activeAffinity?.resolvedEndpointLabel
        ?: activeAffinity?.intendedTargetLabel ?: endpointLabel(continuity.audioOutputEndpoint)
    val explicitPhone = (speaking?.target ?: activeAffinity?.intendedTarget) == com.halo.sdk.runtime.SpeechOutputTarget.Phone
    var chooser by remember { mutableStateOf(false) }
    val alternatives = endpoints.filter { (it.endpoint == InteractionEndpoint.Phone && !explicitPhone) || !sameEndpoint(it.endpoint, if (it.capabilities.visualOutput && !it.capabilities.audioOutput) continuity.presentationEndpoint else continuity.audioOutputEndpoint) }
    if ((relevant || continuity.executing) && continuity.interactionId != null) {
        Row(Modifier.fillMaxWidth()) {
            Text("Input: ${endpointLabel(continuity.inputEndpoint)} · Reply: $replyLabel · View: ${endpointLabel(continuity.presentationEndpoint)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (alternatives.any { it.ready }) TextButton(onClick = { chooser = true }) { Text("Continue on…") }
        }
        if (continuity.status == InteractionHandoffStatus.UNAVAILABLE) Text("That endpoint is unavailable. Your reply route has not changed.", style = MaterialTheme.typography.bodySmall)
        if (audioState is com.halo.app.audio.HaloAudioSessionState.Failed) Text("Audio output blocked. Reconnect the selected device or choose an available output.", style = MaterialTheme.typography.bodySmall)
    }
    if (chooser && (relevant || continuity.executing)) ModalBottomSheet(onDismissRequest = { chooser = false }) {
        Text("Continue on", style = MaterialTheme.typography.titleMedium)
        alternatives.forEach { item -> TextButton(enabled = item.ready, onClick = { model.handoff(item.endpoint); chooser = false }) {
            Text((if (item.capabilities.audioOutput) "Continue audio on " else "Continue view on ") + item.label + " · " + listOfNotNull(
                "Screen".takeIf { item.capabilities.visualOutput },
                (if (item.capabilities.audioInput) "Voice" else "Audio").takeIf { item.capabilities.audioOutput },
                "Camera".takeIf { item.capabilities.camera }, "Controls".takeIf { item.capabilities.controls }).joinToString(" + ") + if (item.ready) "" else " · unavailable")
        } }
        Text("The conversation and computation stay on your phone.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
    }
    blocked?.let {
        Text("The original conversation is unavailable.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = model::rebindBlockedRecovery) { Text("Continue in current conversation") }
        TextButton(onClick = model::cancelBlockedRecovery) { Text("Cancel task") }
    }
    capture?.let { recent ->
        Row(Modifier.fillMaxWidth()) {
            Text("${recent.mediaKind} from ${recent.sourceLabel} · ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(recent.timestamp))}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { openCapture(recent.objectId) }) { Text("Open in Notebook") }
            TextButton(onClick = model::dismissRecentCapture) { Text(uiText("Dismiss")) }
        }
    }
}
private fun endpointLabel(endpoint: InteractionEndpoint?): String = when (endpoint) {
    is InteractionEndpoint.ManagedDevice -> endpoint.displayName?.takeIf { it.isNotBlank() } ?: "Wearable"
    is InteractionEndpoint.AudioAccessory -> endpoint.displayName ?: "Audio accessory"
    is InteractionEndpoint.WearOs -> endpoint.displayName ?: "Watch"
    InteractionEndpoint.AndroidAssistant -> "Android assistant"
    else -> "Phone"
}
