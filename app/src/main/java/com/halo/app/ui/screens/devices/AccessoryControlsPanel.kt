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
        Text(uiText("The voice button starts a conversation with halo."), style = MaterialTheme.typography.bodySmall)
        Text(uiText("Touch and swipe keep the glasses’ own controls."), style = MaterialTheme.typography.bodySmall)
        if (endpoint?.connected == true) {
            if (endpoint.supported.isEmpty()) Text(uiText("Press a button while identifying it to see whether it can be changed."), style = MaterialTheme.typography.bodySmall)
            endpoint.supported.forEach { row ->
                val mapped = remember(revision, endpoint, row) {
                    (owner.mappings.device(endpoint) + owner.mappings.defaults(endpoint.deviceClass))
                        .firstOrNull { it.control == row.first && it.gesture == row.second }?.selection?.action ?: AccessoryAction.SystemDefault
                }
                TextButton(onClick = { picker = row }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("${uiText(row.first.controlLabel())} · ${uiText(row.second.gestureLabel())} · ${uiText(mapped.label())}")
                }
            }
            TextButton(onClick = { identify = !identify; owner.identify(if (identify) endpointId else null) }) {
                Text(uiText(if (identify) "Stop identifying" else "Identify control"))
            }
            if (identify) Text(uiText("Press a control. Native device behavior still applies."), style = MaterialTheme.typography.bodySmall)
            // Evidence, enum values and dispatcher reasons stay with their owner.
            TextButton(onClick = { owner.mappings.reset(endpoint) }) { Text(uiText("Reset device controls")) }
        } else Text(uiText("Connect this device to configure controls."), style = MaterialTheme.typography.bodySmall)
        if (endpoint?.availableActions?.contains(AccessoryAction.CaptureAndAsk) != true)
            Text(uiText("Photo questions need on-phone photo understanding before they can be assigned to a button."), style = MaterialTheme.typography.bodySmall)
    }
    picker?.let { row -> if (endpoint != null) AlertDialog(onDismissRequest = { picker = null }, title = { Text(uiText("Assign control")) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Row { Checkbox(checked = classDefault, onCheckedChange = { classDefault = it }); Text(uiText("Use as default for this device class")) }
            endpoint.availableActions.sortedBy { it.ordinal }.forEach { action -> TextButton(onClick = {
                if (action == AccessoryAction.ReviewRoutine) routinePicker = row
                else owner.mappings.save(endpoint, ControlBinding(row.first, row.second, AccessoryActionSelection(action)), classDefault)
                picker = null
            }, enabled = endpoint.connected && row in endpoint.supported &&
                (action != AccessoryAction.ReviewRoutine || routines.isNotEmpty())) {
                Text(uiText(action.label()) + if (action == AccessoryAction.ReviewRoutine && routines.isEmpty()) " · " + uiText("No routines") else "")
            } }
        } }, confirmButton = { TextButton(onClick = { picker = null }) { Text(uiText("Close")) } }) }
    routinePicker?.let { row -> if (endpoint != null) AlertDialog(onDismissRequest = { routinePicker = null }, title = { Text(uiText("Review routine")) },
        text = { Column { routines.forEach { routine -> TextButton(onClick = {
            owner.mappings.save(endpoint, ControlBinding(row.first, row.second, AccessoryActionSelection(AccessoryAction.ReviewRoutine, routine.id.value)), classDefault)
            routinePicker = null
        }) { Text(routine.name) } } } }, confirmButton = { TextButton(onClick = { routinePicker = null }) { Text(uiText("Close")) } }) }
}
private fun AccessoryAction.label() = when (this) {
    AccessoryAction.TalkToHalo -> "Talk to halo"
    AccessoryAction.StopSpeaking -> "Stop speaking"
    AccessoryAction.RepeatLastAnswer -> "Repeat last answer"
    AccessoryAction.TakePhoto -> "Take photo"
    AccessoryAction.CaptureAndAsk -> "Take a photo and ask halo"
    AccessoryAction.OpenRecentCapture -> "Open latest photo"
    AccessoryAction.CreateNote -> "Create note"
    AccessoryAction.OpenStudy -> "Open Study"
    AccessoryAction.OpenCurrentInteraction -> "Open current conversation"
    AccessoryAction.ContinueOnThisDevice -> "Continue here"
    AccessoryAction.ReviewRoutine -> "Review routine"
    AccessoryAction.SystemDefault -> "Keep the glasses’ own action"
    AccessoryAction.DoNothing -> "Do nothing"
}

private fun AccessoryControl.controlLabel() = when (this) {
    AccessoryControl.ASSISTANT -> "Voice button"
    AccessoryControl.CAMERA -> "Camera button"
    AccessoryControl.PLAY_PAUSE -> "Play or pause"
    AccessoryControl.NEXT -> "Next"
    AccessoryControl.PREVIOUS -> "Previous"
    AccessoryControl.VOLUME_UP -> "Volume up"
    AccessoryControl.VOLUME_DOWN -> "Volume down"
    AccessoryControl.PRIMARY -> "Main button"
    AccessoryControl.SECONDARY -> "Second button"
    AccessoryControl.CUSTOM -> "Button"
}

private fun AccessoryControlAction.gestureLabel() = when (this) {
    AccessoryControlAction.PRESS -> "Press"
    AccessoryControlAction.RELEASE -> "Release"
    AccessoryControlAction.SINGLE -> "Tap"
    AccessoryControlAction.DOUBLE -> "Double tap"
    AccessoryControlAction.LONG -> "Hold"
}
