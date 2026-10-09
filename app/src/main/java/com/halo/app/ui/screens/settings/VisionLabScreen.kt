package com.halo.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.ui.design.HaloScreenHeader

@Composable
fun VisionLabScreen(model: VisionLabViewModel, onBack: () -> Unit, onPrivacy: () -> Unit, onCamera: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), model::cameraResult)
    fun launchCamera() { scope.launch { runCatching { model.prepareCamera() }.onSuccess { camera.launch(it) } } }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) launchCamera() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(model::attach) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HaloScreenHeader("Vision Lab", "Compare one prepared image across concrete models", navigation = onBack) }
        item {
            Column {
                Text("Cloud images require your Context & privacy permission. Diagnostics do not enter conversation history or memory.")
                Text("Results apply to this diagnostic request. Changing a model or backend requires checking its current capability; a past success does not grant permanent compatibility.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onPrivacy) { Text("Context & privacy") }
                TextButton(onClick = { picker.launch("image/*") }, enabled = !state.busy) { Text("Choose gallery image") }
                TextButton(onClick = { cameraPermission.launch(android.Manifest.permission.CAMERA) }, enabled = !state.busy) { Text("Phone camera") }
                TextButton(onClick = model::wearable, enabled = !state.busy) { Text("HeyCyan camera") }
                TextButton(onClick = model::assistantScreenshot, enabled = !state.busy) { Text("Active assistant screenshot") }
                TextButton(onClick = model::useAttached, enabled = !state.busy) { Text("Use attached image") }
                Text("Source: ${state.source ?: "None"}")
                if (!state.localInstalled) Text("Local Vision: Not installed")
                TextButton(onClick = model::refresh, enabled = !state.busy) { Text("Refresh candidates") }
            }
        }
        items(state.candidates, key = { "${it.provider.id}/${it.model.id}" }) { candidate ->
            Row(Modifier.fillMaxWidth()) {
                Checkbox(checked = "${candidate.provider.id}/${candidate.model.id}" in state.selected, enabled = !state.busy,
                    onCheckedChange = { model.toggle(candidate) })
                Column { Text("${candidate.provider.displayName} / ${candidate.model.displayName}")
                    Text("${candidate.model.capabilityEvidence[com.halo.sdk.provider.AiCapability.IMAGE] ?: "UNKNOWN"} / ${candidate.model.executionLocation}") }
            }
        }
        item { Row { Button(onClick = { model.run() }, enabled = !state.busy) { Text("Compare") }
            TextButton(onClick = { model.run(probe = true) }, enabled = !state.busy) { Text("Verify image support") } } }
        state.message?.let { message -> item { Text(message) } }
        items(state.rows) { row ->
            Text("${row.providerId} / ${row.modelId}\n${row.stage}: ${row.failure ?: "Success"} / ${row.evidence}\n" +
                "${row.sourceBytes} to ${row.preparedBytes} bytes; ${row.width}x${row.height} ${row.mimeType}\n" +
                "Prepare ${row.preparationMs} ms; request ${row.requestMs} ms; total ${row.totalMs} ms; response ${row.responseLength} characters")
        }
    }
}
