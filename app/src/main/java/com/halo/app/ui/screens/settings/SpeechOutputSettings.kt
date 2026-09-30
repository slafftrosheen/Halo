package com.halo.app.ui.screens.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.halo.app.ai.label
import com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider
import com.halo.sdk.provider.Credential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SpeechOutputSettings(viewModel: VoiceSettingsViewModel, onElevenLabs: () -> Unit) {
    val registry = viewModel.synthesisRegistry ?: return
    val selection by registry.selector.ttsSelection.collectAsStateWithLifecycle()
    val elevenLabs = registry.providers.filterIsInstance<ElevenLabsTtsProvider>().firstOrNull()
    val cloudState = elevenLabs?.state?.collectAsStateWithLifecycle()?.value
    var fallback by remember { mutableStateOf(viewModel.cloudSynthesisFallback()) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Speech output", style = MaterialTheme.typography.titleMedium)
        (listOf("AUTOMATIC" to "Automatic (Android TTS)") + registry.providers.map { it.metadata.id to it.metadata.displayName }).forEach { (id, label) ->
            TextButton(enabled = id != ElevenLabsTtsProvider.ID || (cloudState?.configured == true && elevenLabs?.voiceId() != null && elevenLabs?.modelId() != null), onClick = { registry.selector.selectTts(id) }) { Text(if (selection.id == id) "$label · Selected" else label) }
        }
        Text("Android TTS · Local engine; readiness checked on synthesis")
        cloudState?.let { state -> Text("ElevenLabs · " + (state.errorCode?.label ?: if (!state.configured) "Setup required" else if (state.ready) "Ready" else "Configured; not checked") + if (selection.id == ElevenLabsTtsProvider.ID) " · Selected" else " · Not selected") }
        elevenLabs?.takeIf { selection.id == ElevenLabsTtsProvider.ID }?.let { provider ->
            val cached = provider.cachedCatalogue()
            Text("Voice: ${cached?.voices?.firstOrNull { it.id == provider.voiceId() }?.name ?: "Choose a voice"}")
            Text("Model: ${cached?.models?.firstOrNull { it.id == provider.modelId() }?.name ?: "Choose a model"}")
        }
        Text("If cloud synthesis fails")
        TextButton(onClick = { fallback = !fallback; viewModel.setCloudSynthesisFallback(fallback) }) {
            Text(if (fallback) "Use Android TTS" else "Stay silent")
        }
        Text("Synthesis fallback keeps the selected audio output route.")
        TextButton(onClick = onElevenLabs) { Text("Configure ElevenLabs") }
    }
}

@Composable
fun ElevenLabsSettingsScreen(provider: ElevenLabsTtsProvider, onBack: () -> Unit, onTestVoice: () -> Unit) {
    var draft by remember(provider) { mutableStateOf("") }
    var configured by remember { mutableStateOf(provider.hasCredential()) }
    var catalogue by remember { mutableStateOf(provider.cachedCatalogue()) }
    var voice by remember { mutableStateOf(provider.voiceId()) }
    var model by remember { mutableStateOf(provider.modelId()) }

    var search by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(provider.voiceStyle()) }
    val health by provider.state.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("Back") }; Text("ElevenLabs speech", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Selecting ElevenLabs sends sanitized spoken response text to ElevenLabs. Conversation history, memories and tool payloads are excluded. Provider retention follows your ElevenLabs account policy.") }
        item {
            Text(if (configured) "Credential configured" else "Setup required")
            OutlinedTextField(draft, { draft = it }, label = { Text("API key") }, placeholder = { Text(if (configured) "Credential configured" else "Enter API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Button(enabled = draft.isNotBlank(), onClick = {
                val value = draft; draft = ""
                scope.launch { try { provider.setCredential(Credential(ElevenLabsTtsProvider.ID, value)); configured = provider.hasCredential(); message = "Configured · not checked" } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { message = "Unable to save credential" } }
            }) { Text("Save key") }
            TextButton(onClick = { draft = ""; provider.clearCredential(); configured = false }) { Text("Remove key") }
        }
        item {
            Text("Authentication: ${if (health.authenticated) "Ready" else "Not checked"} · Synthesis: ${if (health.ready) "Proven" else "Not tested"}")
            Button(enabled = configured && !busy, onClick = { busy = true; scope.launch { try { message = if (provider.health()) "Authentication ready; synthesis is checked separately" else "Connection failed. Check your key and network connection." } finally { busy = false } } }) { Text("Test connection") }
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
        item { Text("Voice", style = MaterialTheme.typography.titleMedium); OutlinedTextField(search, { search = it }, label = { Text("Search voices") }) }
        items(catalogue?.voices.orEmpty().filter { (it.name + " " + it.category.orEmpty()).contains(search, true) }, key = { "voice:${it.id}" }) { choice ->
            TextButton(onClick = { provider.selectVoice(choice.id); voice = choice.id }) { Text(choice.name + choice.category?.let { " · $it" }.orEmpty() + if (voice == choice.id) " · Selected" else "") }
        }
        item {
            Text("Voice style", style = MaterialTheme.typography.titleMedium)
            listOf("stability", "similarity_boost", "speed").forEach { key ->
                Text("${key.replace('_', ' ')}: ${"%.2f".format(style.getValue(key))}")
                Slider(value = style.getValue(key).toFloat(), onValueChange = { style = style + (key to it.toDouble()) },
                    valueRange = if (key == "speed") 0.7f..1.2f else 0f..1f,
                    onValueChangeFinished = { provider.saveVoiceStyle(style.getValue("stability"), style.getValue("similarity_boost"), style.getValue("speed")) })
            }
            Button(enabled = configured && voice != null && model != null, onClick = onTestVoice) { Text("Speak test phrase on phone") }
        }
    }
}
