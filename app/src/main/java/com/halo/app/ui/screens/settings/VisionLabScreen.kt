package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText
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
    fun launchCamera() { scope.launch {
        try { camera.launch(model.prepareCamera()) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { model.cameraResult(false) }
    } }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) launchCamera() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(model::attach) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(com.halo.app.ui.design.HaloSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HaloScreenHeader(uiText("Vision Lab"), uiText("Compare one prepared image across concrete models"), navigation = onBack) }
        item {
            Column {
                Text(uiText("Cloud images require your Context & privacy permission. Diagnostics do not enter conversation history or memory."))
                Text(uiText("Results apply to this diagnostic request. Changing a model or backend requires checking its current capability; a past success does not grant permanent compatibility."), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onPrivacy, enabled = !state.busy) { Text(uiText("Context & privacy")) }
                TextButton(onClick = { picker.launch("image/*") }, enabled = !state.busy) { Text(uiText("Choose gallery image")) }
                TextButton(onClick = { cameraPermission.launch(android.Manifest.permission.CAMERA) }, enabled = !state.busy) { Text(uiText("Phone camera")) }
                TextButton(onClick = model::wearable, enabled = !state.busy) { Text(uiText("HeyCyan camera")) }
                TextButton(onClick = model::assistantScreenshot, enabled = !state.busy) { Text(uiText("Active assistant screenshot")) }
                TextButton(onClick = model::useAttached, enabled = !state.busy) { Text(uiText("Use attached image")) }
                Text(uiText("Source") + ": " + uiText(state.source ?: "None"))
                if (!state.localInstalled) Text(uiText("Local Vision: Not installed"))
                TextButton(onClick = model::refresh, enabled = !state.busy) { Text(uiText("Refresh candidates")) }
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
        item { Column { Button(modifier = Modifier.fillMaxWidth(), onClick = { model.run() }, enabled = !state.busy && state.selected.isNotEmpty() && state.source != null) { Text(uiText("Compare")) }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { model.run(probe = true) }, enabled = !state.busy && state.selected.isNotEmpty()) { Text(uiText("Verify image support")) } } }
        if (state.busy) item {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = model::cancel) { Text(uiText("Cancel")) }
        }
        state.message?.let { message -> item { Text(uiText(message)) } }
        items(state.rows) { row ->
            Text("${row.providerId} / ${row.modelId}\n${row.stage}: ${row.failure ?: "Success"} / ${row.evidence}\n" +
                "${row.sourceBytes} to ${row.preparedBytes} bytes; ${row.width}x${row.height} ${row.mimeType}\n" +
                "Prepare ${row.preparationMs} ms; request ${row.requestMs} ms; total ${row.totalMs} ms; response ${row.responseLength} characters")
        }
    }
    }
}
