package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.halo.app.ai.SpeechSynthesisRegistry
import com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider
import com.halo.sdk.provider.Credential
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SpeechOutputSettings(viewModel: VoiceSettingsViewModel, onElevenLabs: () -> Unit) {
    val registry = viewModel.synthesisRegistry ?: return
    val selection by registry.selector.ttsSelection.collectAsState()
    val elevenLabs = registry.providers.filterIsInstance<ElevenLabsTtsProvider>().firstOrNull()
    val cloudState = elevenLabs?.state?.collectAsState()?.value
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Speech output", style = MaterialTheme.typography.titleMedium)
        (listOf("AUTOMATIC" to "Automatic (Android TTS)") + registry.providers.map { it.metadata.id to it.metadata.displayName }).forEach { (id, label) ->
            TextButton(enabled = id != ElevenLabsTtsProvider.ID || cloudState?.configured == true, onClick = { registry.selector.selectTts(id) }) { Text(if (selection.id == id) "$label · Selected" else label) }
        }
        Text("Android TTS · Local engine; readiness checked on synthesis")
        cloudState?.let { state -> Text("ElevenLabs · " + (state.error ?: if (!state.configured) "Not configured" else if (state.ready) "Ready" else "Configured; not checked") + if (selection.id == ElevenLabsTtsProvider.ID) " · Selected" else " · Not selected") }
        TextButton(onClick = onElevenLabs) { Text("Set up ElevenLabs") }
    }
}

@Composable
fun ElevenLabsSettingsScreen(provider: ElevenLabsTtsProvider, storage: Storage, onBack: () -> Unit, onTestVoice: () -> Unit) {
    var draft by remember(provider) { mutableStateOf("") }
    var configured by remember { mutableStateOf(provider.hasCredential()) }
    var catalogue by remember { mutableStateOf(provider.cachedCatalogue()) }
    var voice by remember { mutableStateOf(provider.voiceId()) }
    var model by remember { mutableStateOf(provider.modelId()) }
    var fallback by remember { mutableStateOf(storage.getBoolean(SpeechSynthesisRegistry.FALLBACK, true)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("Back") }; Text("ElevenLabs speech", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Selecting ElevenLabs sends sanitized spoken response text to ElevenLabs. Conversation history, memories and tool payloads are excluded. Provider retention follows your ElevenLabs account policy.") }
        item {
            Text(if (configured) "Credential configured" else "Not configured")
            OutlinedTextField(draft, { draft = it }, label = { Text("Replace API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Button(enabled = draft.isNotBlank(), onClick = {
                val value = draft; draft = ""
                scope.launch { try { provider.setCredential(Credential(ElevenLabsTtsProvider.ID, value)); configured = provider.hasCredential(); message = "Saved" } catch (_: Exception) { message = "Unable to save credential" } }
            }) { Text("Save key") }
            TextButton(onClick = { draft = ""; provider.clearCredential(); configured = false }) { Text("Remove key") }
        }
        item {
            Button(enabled = configured && !busy, onClick = {
                busy = true
                scope.launch {
                    try { catalogue = provider.refreshCatalogue(); message = "Voice and TTS model metadata refreshed" }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { message = "Refresh failed; cached choices retained" }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Refreshing…" else "Refresh voices and TTS models") }
            Text(if (catalogue == null) "Catalogue unavailable" else if (catalogue!!.stale) "Cached catalogue · Stale" else "Cached catalogue")
            message?.let { Text(it) }
        }
        item { Text("TTS model", style = MaterialTheme.typography.titleMedium) }
        items(catalogue?.models.orEmpty(), key = { "model:${it.id}" }) { choice ->
            TextButton(onClick = { provider.selectModel(choice.id); model = choice.id }) { Text(choice.name + if (model == choice.id) " · Selected" else "") }
        }
        item { Text("Voice", style = MaterialTheme.typography.titleMedium) }
        items(catalogue?.voices.orEmpty(), key = { "voice:${it.id}" }) { choice ->
            TextButton(onClick = { provider.selectVoice(choice.id); voice = choice.id }) { Text(choice.name + if (voice == choice.id) " · Selected" else "") }
        }
        item {
            Text("When cloud synthesis fails")
            TextButton(onClick = { fallback = !fallback; storage.putBoolean(SpeechSynthesisRegistry.FALLBACK, fallback) }) { Text(if (fallback) "Use Android TTS" else "Stay silent") }
            Text("This keeps the chosen audio route. Phone route fallback is a separate setting.")
            Button(enabled = configured && voice != null && model != null, onClick = onTestVoice) { Text("Test voice on selected audio route") }
        }
    }
}
