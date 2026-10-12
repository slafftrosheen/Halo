package com.halo.app.ui.screens.devices

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
import com.halo.sdk.host.*

@Composable
internal fun AccessoryControlsPanel(endpointId: String) {
    val owner = ManualDependencyGraph.accessoryControls
    val endpoints by owner.endpoints.collectAsStateWithLifecycle()
    val feedback by owner.feedback.collectAsStateWithLifecycle()
    val revision by owner.mappings.changes.collectAsStateWithLifecycle()
    val endpoint = endpoints.firstOrNull { it.id == endpointId }
    var picker by remember { mutableStateOf<Pair<AccessoryControl, AccessoryControlAction>?>(null) }
    var routinePicker by remember { mutableStateOf<Pair<AccessoryControl, AccessoryControlAction>?>(null) }
    val routineFlow = remember { ManualDependencyGraph.routineRepository.observeRoutinesReadOnly() }
    val routines by routineFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var identify by remember { mutableStateOf(false) }
    var classDefault by remember { mutableStateOf(false) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(endpointId, identify, lifecycle) {
        if (identify) owner.identify(endpointId)
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { owner.identify(null); identify = false }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { owner.identify(null); lifecycle.lifecycle.removeObserver(observer) }
    }
    Column {
        Text(uiText("Controls"), style = MaterialTheme.typography.titleMedium)
        Text(uiText("Voice button · Native voice capture"), style = MaterialTheme.typography.bodySmall)
        Text(uiText("Touch and swipe · Observed · Not assignable"), style = MaterialTheme.typography.bodySmall)
        if (endpoint?.connected == true) {
            if (endpoint.supported.isEmpty()) Text(uiText("No assignable control delivered in this connection. Use Identify control."), style = MaterialTheme.typography.bodySmall)
            endpoint.supported.forEach { row ->
                val mapped = remember(revision, endpoint, row) {
                    (owner.mappings.device(endpoint) + owner.mappings.defaults(endpoint.deviceClass))
                        .firstOrNull { it.control == row.first && it.gesture == row.second }?.selection?.action ?: AccessoryAction.SystemDefault
                }
                TextButton(onClick = { picker = row }) { Text("${row.first} · ${row.second} · ${mapped.label()}") }
            }
            TextButton(onClick = { identify = !identify; owner.identify(if (identify) endpointId else null) }) {
                Text(if (identify) "Stop identifying" else "Identify control")
            }
            if (identify) Text(uiText("Press a control. Native device behavior still applies."), style = MaterialTheme.typography.bodySmall)
            Text("${feedback.control} · ${feedback.gesture} · ${feedback.evidence}", style = MaterialTheme.typography.bodySmall)
            Text(feedback.outcome, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { owner.mappings.reset(endpoint) }) { Text(uiText("Reset device controls")) }
        } else Text(uiText("Connect this device to configure controls."), style = MaterialTheme.typography.bodySmall)
        if (endpoint?.availableActions?.contains(AccessoryAction.CaptureAndAsk) != true)
            Text(uiText("Capture and ask · Unavailable: verified local vision required"), style = MaterialTheme.typography.bodySmall)
    }
    picker?.let { row -> if (endpoint != null) AlertDialog(onDismissRequest = { picker = null }, title = { Text(uiText("Assign control")) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Row { Checkbox(checked = classDefault, onCheckedChange = { classDefault = it }); Text(uiText("Use as default for this device class")) }
            endpoint.availableActions.sortedBy { it.ordinal }.forEach { action -> TextButton(onClick = {
                if (action == AccessoryAction.ReviewRoutine) routinePicker = row
                else owner.mappings.save(endpoint, ControlBinding(row.first, row.second, AccessoryActionSelection(action)), classDefault)
                picker = null
            }, enabled = action != AccessoryAction.ReviewRoutine || routines.isNotEmpty()) { Text(action.label() + if (action == AccessoryAction.ReviewRoutine && routines.isEmpty()) " · No routines" else "") } }
        } }, confirmButton = { TextButton(onClick = { picker = null }) { Text(uiText("Close")) } }) }
    routinePicker?.let { row -> if (endpoint != null) AlertDialog(onDismissRequest = { routinePicker = null }, title = { Text(uiText("Review routine")) },
        text = { Column { routines.forEach { routine -> TextButton(onClick = {
            owner.mappings.save(endpoint, ControlBinding(row.first, row.second, AccessoryActionSelection(AccessoryAction.ReviewRoutine, routine.id.value)), classDefault)
            routinePicker = null
        }) { Text(routine.name) } } } }, confirmButton = { TextButton(onClick = { routinePicker = null }) { Text(uiText("Close")) } }) }
}
private fun AccessoryAction.label() = name.replace(Regex("([a-z])([A-Z])"), "$1 $2")
