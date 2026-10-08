package com.halo.app.ui.screens.home

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
    var chooser by remember { mutableStateOf(false) }
    val alternatives = endpoints.filter { !sameEndpoint(it.endpoint, continuity.current) }
    if ((relevant || continuity.executing) && continuity.interactionId != null && alternatives.any { it.ready }) {
        Row(Modifier.fillMaxWidth()) {
            Text("Reply on ${endpointLabel(continuity.current)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { chooser = true }) { Text("Continue on…") }
        }
        if (continuity.status == InteractionHandoffStatus.UNAVAILABLE) Text("That endpoint is unavailable. Your reply route has not changed.", style = MaterialTheme.typography.bodySmall)
    }
    if (chooser && (relevant || continuity.executing)) ModalBottomSheet(onDismissRequest = { chooser = false }) {
        Text("Continue on", style = MaterialTheme.typography.titleMedium)
        alternatives.forEach { item -> TextButton(enabled = item.ready, onClick = { model.handoff(item.endpoint); chooser = false }) {
            Text(item.label + if (item.ready) "" else " · unavailable")
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
            TextButton(onClick = model::dismissRecentCapture) { Text("Dismiss") }
        }
    }
}
private fun endpointLabel(endpoint: InteractionEndpoint?): String = when (endpoint) {
    is InteractionEndpoint.Wearable -> endpoint.displayName?.takeIf { it.isNotBlank() } ?: "Wearable"
    InteractionEndpoint.AndroidAssistant -> "Android assistant"
    else -> "Phone"
}
