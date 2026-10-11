package com.halo.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.workspace.*

@Composable
fun LocalAudioLabScreen(model: LocalAudioLabViewModel, onBack: () -> Unit, onModels: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(model::attach) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { HaloScreenHeader(uiText("Local audio test"), uiText("Try an audio file with an installed model"), navigation = onBack) }
        item {
            Text(uiText("Audio stays on this phone. This test uses no tools and saves no conversation. Audio input models return text; Voice settings control spoken replies."))
            TextButton(onClick = onModels, enabled = !state.busy) { Text(uiText("On-device models")) }
        }
        item { HaloWorkspaceSection(uiText("Model")) {
            if (state.models.isEmpty()) Text(uiText("No audio-capable model installed"), Modifier.padding(16.dp))
            state.models.forEach { candidate ->
                HaloWorkspaceRow(candidate.displayName, selected = state.modelId == candidate.id,
                    enabled = !state.busy, trailingText = if (state.modelId == candidate.id) uiText("Selected") else null,
                    onClick = { model.select(candidate.id) })
            }
            TextButton(onClick = model::refresh, enabled = !state.busy) { Text(uiText("Refresh candidates")) }
        } }
        item {
            Text(uiText("Choose a WAV, MP3 or FLAC clip up to 30 seconds long. Maximum size: 20 MiB."))
            TextButton(onClick = { picker.launch("audio/*") }, enabled = !state.busy) { Text(uiText("Choose audio file")) }
            if (state.hasAudio) TextButton(onClick = model::clearAudio, enabled = !state.busy) { Text(uiText("Remove audio")) }
            Button(onClick = model::run, enabled = state.hasAudio && state.modelId != null && !state.busy) { Text(uiText("Transcribe audio")) }
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = model::cancel) { Text(uiText("Cancel")) }
            }
        }
        state.message?.let { message -> item { Text(uiText(message)) } }
        state.response?.let { response -> item { HaloWorkspaceCard { Text(response) } } }
    }
    }
}
