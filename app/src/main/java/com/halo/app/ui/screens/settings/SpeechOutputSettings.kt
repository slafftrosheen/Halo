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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SpeechOutputSettings(viewModel: VoiceSettingsViewModel, onElevenLabs: () -> Unit) {
    val registry = viewModel.synthesisRegistry ?: return
    val selection by registry.selector.ttsSelection.collectAsStateWithLifecycle()
    val elevenLabs = registry.providers.filterIsInstance<ElevenLabsTtsProvider>().firstOrNull()
    val cloudState = elevenLabs?.state?.collectAsStateWithLifecycle()?.value
    var fallback by remember { mutableStateOf(viewModel.cloudSynthesisFallback()) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Speech output", style = MaterialTheme.typography.titleMedium)
        (listOf("AUTOMATIC" to "Automatic (Android TTS)") + registry.providers.map { it.metadata.id to it.metadata.displayName }).forEach { (id, label) ->
            TextButton(enabled = !saving && (id != ElevenLabsTtsProvider.ID || (cloudState?.configured == true && cloudState.voiceId != null && cloudState.modelId != null)), onClick = {
                saving = true
                scope.launch {
                    try { withContext(Dispatchers.IO) { registry.selector.selectTts(id) }; saveError = null }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { saveError = "Unable to save speech provider selection" }
                    finally { saving = false }
                }
            }) { Text(if (selection.id == id) "$label · Selected" else label) }
        }
        Text("Android TTS · Local engine; readiness checked on synthesis")
        cloudState?.let { state ->
            val errCode = state.errorCode
            val status = when {
                state.loading -> "Loading secure configuration"
                state.error == "SECURE_STORAGE_UNREADABLE" -> "Stored credential unreadable"
                errCode != null -> errCode.label
                !state.configured -> "Setup required"
                state.ready -> "Ready"
                else -> "Configured; not checked"
            }
            Text("ElevenLabs · " + status + if (selection.id == ElevenLabsTtsProvider.ID) " · Selected" else " · Not selected")
        }
        elevenLabs?.takeIf { selection.id == ElevenLabsTtsProvider.ID }?.let { _ ->
            cloudState?.let { state ->
                Text("Voice: ${state.voiceName ?: "Choose a voice"}")
                Text("Model: ${state.modelName ?: "Choose a model"}")
            }
        }
        Text("If cloud synthesis fails")
        TextButton(enabled = !saving, onClick = {
            val next = !fallback
            saving = true
            scope.launch {
                try { withContext(Dispatchers.IO) { viewModel.setCloudSynthesisFallback(next) }; fallback = next; saveError = null }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { saveError = "Unable to save speech fallback" }
                finally { saving = false }
            }
        }) {
            Text(if (fallback) "Use Android TTS" else "Stay silent")
        }
        saveError?.let { Text(it) }
        Text("Synthesis fallback keeps the selected audio output route.")
        TextButton(onClick = onElevenLabs) { Text("Configure ElevenLabs") }
    }
}

@Composable
fun ElevenLabsSettingsScreen(provider: ElevenLabsTtsProvider, onBack: () -> Unit, onTestVoice: () -> Unit, onUseProvider: () -> Unit = {}) {
    val state by provider.state.collectAsStateWithLifecycle()
    var draft by remember(provider) { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var localStyle by remember(state.style) { mutableStateOf(state.style) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val configured = state.configured
    val catalogue = state.catalogue
    val voice = state.voiceId
    val model = state.modelId

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("Back") }; Text("ElevenLabs speech", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Selecting ElevenLabs sends sanitized spoken response text to ElevenLabs. Conversation history, memories and tool payloads are excluded. Provider retention follows your ElevenLabs account policy.") }
        item {
            Text(
                when {
                    state.loading -> "Loading secure configuration…"
                    state.error == "SECURE_STORAGE_UNREADABLE" -> "Stored credential cannot be read · enter the key again"
                    configured -> "Credential configured"
                    else -> "Setup required"
                }
            )
            OutlinedTextField(draft, { draft = it }, label = { Text("API key") }, placeholder = { Text(if (configured) "Credential configured" else "Enter API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Button(enabled = draft.isNotBlank() && !busy && !state.loading, onClick = {
                val value = draft.trim()
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { provider.setCredential(Credential(ElevenLabsTtsProvider.ID, value)) }
                        draft = ""
                        message = "Key saved; loading voices and models..."
                        try {
                            withContext(Dispatchers.IO) { provider.refreshCatalogue() }
                            message = "Key saved. Choose a voice and TTS model below."
                        } catch (cancelled: CancellationException) { throw cancelled }
                          catch (error: Exception) { message = (error as? com.halo.sdk.provider.SpeechProviderException)?.message ?: "Key saved, but loading choices failed. Check key permissions and network, then refresh." }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "Unable to save credential; your entered key is retained for retry." }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Working..." else "Save key and load choices") }
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try { withContext(Dispatchers.IO) { provider.clearCredential() }; draft = ""; message = "Credential removed" }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "Unable to remove credential" }
                    finally { busy = false }
                }
            }) { Text("Remove key") }
        }
        item {
            Text("Authentication: ${if (state.authenticated) "Ready" else "Not checked"} · Synthesis: ${if (state.ready) "Proven" else "Not tested"}")
            Button(enabled = configured && !busy, onClick = { busy = true; scope.launch { try { message = if (withContext(Dispatchers.IO) { provider.health() }) "Authentication ready; synthesis is checked separately" else provider.state.value.error ?: "Connection failed. Check your key and network connection." } finally { busy = false } } }) { Text("Test connection") }
            Button(enabled = configured && !busy, onClick = {
                busy = true
                scope.launch {
                    try { withContext(Dispatchers.IO) { provider.refreshCatalogue() }; message = "Voice and TTS model metadata refreshed" }
                    catch (e: CancellationException) { throw e }
                    catch (error: Exception) { message = (error as? com.halo.sdk.provider.SpeechProviderException)?.message ?: "Refresh failed; cached choices retained" }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Refreshing…" else "Refresh voices and TTS models") }
            Text(if (catalogue == null) "Catalogue unavailable" else if (catalogue.stale) "Cached catalogue · Stale" else "Cached catalogue")
            message?.let { Text(it) }
            state.error?.takeIf { it != message && it != "SECURE_STORAGE_UNREADABLE" }?.let { Text(it) }
        }
        item { Text("TTS model", style = MaterialTheme.typography.titleMedium) }
        items(catalogue?.models.orEmpty(), key = { "model:${it.id}" }) { choice ->
            TextButton(enabled = !busy, onClick = { busy = true; scope.launch {
                try { withContext(Dispatchers.IO) { provider.selectModel(choice.id) }; message = "TTS model saved" }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "Unable to save TTS model" }
                finally { busy = false }
            } }) { Text(choice.name + if (model == choice.id) " · Selected" else "") }
        }
        item { Text("Voice", style = MaterialTheme.typography.titleMedium); OutlinedTextField(search, { search = it }, label = { Text("Search voices") }) }
        items(catalogue?.voices.orEmpty().filter { (it.name + " " + it.category.orEmpty()).contains(search, true) }, key = { "voice:${it.id}" }) { choice ->
            TextButton(enabled = !busy, onClick = { busy = true; scope.launch {
                try { withContext(Dispatchers.IO) { provider.selectVoice(choice.id) }; message = "Voice saved" }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "Unable to save voice" }
                finally { busy = false }
            } }) { Text(choice.name + choice.category?.let { " · $it" }.orEmpty() + if (voice == choice.id) " · Selected" else "") }
        }
        item {
            Text("Voice style", style = MaterialTheme.typography.titleMedium)
            listOf("stability", "similarity_boost", "speed").forEach { key ->
                val currentValue = localStyle[key] ?: 0.5
                Text("${key.replace('_', ' ')}: ${"%.2f".format(currentValue)}")
                Slider(value = currentValue.toFloat(), onValueChange = { localStyle = localStyle + (key to it.toDouble()) },
                    valueRange = if (key == "speed") 0.7f..1.2f else 0f..1f,
                    enabled = !busy,
                    onValueChangeFinished = {
                        val selected = localStyle
                        busy = true
                        scope.launch {
                            try { withContext(Dispatchers.IO) { provider.saveVoiceStyle(selected.getValue("stability"), selected.getValue("similarity_boost"), selected.getValue("speed")) }; message = "Voice style saved" }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { localStyle = state.style; message = "Unable to save voice style" }
                            finally { busy = false }
                        }
                    })
            }
            Button(enabled = configured && voice != null && model != null && !busy, onClick = {
                busy = true
                scope.launch {
                    try { withContext(Dispatchers.IO) { onUseProvider() }; message = "ElevenLabs selected for speech output" }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "Unable to save speech provider selection" }
                    finally { busy = false }
                }
            }) { Text("Use ElevenLabs for speech output") }
            Button(enabled = configured && voice != null && model != null && !busy, onClick = onTestVoice) { Text("Speak test phrase on phone") }
        }
    }
}
