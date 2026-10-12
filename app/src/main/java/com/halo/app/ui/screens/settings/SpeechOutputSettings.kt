package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.halo.app.ui.design.HaloFocusAction
import com.halo.app.ui.icons.HaloGlyph
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
    val accessorySettings = com.halo.app.di.ManualDependencyGraph.voiceExperienceSettings
    val accessoryPolicy by accessorySettings.state.collectAsStateWithLifecycle()
    val selection by registry.selector.ttsSelection.collectAsStateWithLifecycle()
    val elevenLabs = registry.providers.filterIsInstance<ElevenLabsTtsProvider>().firstOrNull()
    val cloudState = elevenLabs?.state?.collectAsStateWithLifecycle()?.value
    var fallback by remember { mutableStateOf(viewModel.cloudSynthesisFallback()) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val kokoro = registry.providers.filterIsInstance<com.halo.app.audio.KokoroSpeechProvider>().firstOrNull()
    val kokoroState = kokoro?.state?.collectAsStateWithLifecycle()?.value
    val kokoroProgress = kokoro?.downloadProgress?.collectAsStateWithLifecycle()?.value
    var importingPack by remember { mutableStateOf(false) }
    val packPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null && kokoro != null) scope.launch {
            importingPack=true
            try { kokoro.importPack(uri); saveError=null }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { saveError="Voice pack import failed. Check the file and available storage, then retry." }
            finally { importingPack=false }
        }
    }
    val output = com.halo.app.di.ManualDependencyGraph.haloSpeechOutputCoordinator
    val outputTest by output.outputTest.collectAsStateWithLifecycle()
    val endpoints by com.halo.app.di.ManualDependencyGraph.audioAccessoryRegistry.accessories.collectAsStateWithLifecycle()
    var testTarget by remember { mutableStateOf<com.halo.sdk.runtime.SpeechOutputTarget>(com.halo.sdk.runtime.SpeechOutputTarget.HostDefault) }
    var testLabel by remember { mutableStateOf("Android default") }
    DisposableEffect(output) { onDispose { output.stopOutputTest() } }
    val mediaControls = com.halo.app.di.ManualDependencyGraph.scopedSpeechMediaControls
    val mediaEnabled by mediaControls.enabled.collectAsStateWithLifecycle()
    val controlRevision by com.halo.app.di.ManualDependencyGraph.accessoryControls.mappings.changes.collectAsStateWithLifecycle()
    var controlsPicker by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText("Test audio output"), style = MaterialTheme.typography.titleMedium)
        Text(uiText("One-time test using your selected speech voice. Your saved routing is kept."), style = MaterialTheme.typography.bodySmall)
        val testBusy = outputTest.outcome in setOf("Preparing", "Route verified · Playing")
        val choices = listOf(com.halo.sdk.runtime.SpeechOutputTarget.HostDefault to "Android default",
            com.halo.sdk.runtime.SpeechOutputTarget.Phone to "Phone speaker") + endpoints.filter { it.canOutput }.map {
            com.halo.sdk.runtime.SpeechOutputTarget.Device(it.id, it.outputDeviceId, it.label, it.kind) to it.label
        }
        choices.forEach { (target, label) -> TextButton(enabled = !testBusy, onClick = { testTarget = target; testLabel = label }) {
            Text((if (target is com.halo.sdk.runtime.SpeechOutputTarget.Device) label else uiText(label)) + if (testTarget == target) " · " + uiText("Selected for test") else "")
        } }
        Column {
            HaloFocusAction(uiText("Play test phrase"), HaloGlyph.SPEAK, !testBusy && choices.any { it.first == testTarget }, { output.testOutput(testTarget, testLabel) })
            TextButton(enabled = testBusy, onClick = { output.stopOutputTest() }) { Text(uiText("Stop")) }
        }
        Text(uiText("Requested output: %1\$s", outputTest.requested))
        Text(uiText("Observed output: %1\$s", outputTest.observed ?: uiText("Not verified during playback")))
        Text(uiText(outputTest.outcome))
        if (outputTest.outcome == "Playback completed") TextButton(enabled = !outputTest.audibleConfirmed, onClick = { output.confirmOutputTest() }) { Text(uiText(if (outputTest.audibleConfirmed) "You confirmed audible output" else "I heard it on the intended output")) }
        Text(uiText("Connected and available do not establish audible output. Confirm only what you heard."), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text(uiText("Wearables & audio"), style = MaterialTheme.typography.titleMedium)
        val controlAction = remember(mediaEnabled, controlRevision) {
            if (mediaEnabled) com.halo.app.di.ManualDependencyGraph.accessoryControls.mappings.defaults("SCOPED_MEDIA")
                .firstOrNull()?.selection?.action?.name ?: "SystemDefault" else "SystemDefault"
        }
        TextButton(onClick = { controlsPicker = true }) { Text(uiText("Control during Halo speech: %1\$s", uiText(controlAction.replace(Regex("([a-z])([A-Z])"), "$1 $2")))) }
        Text(uiText("Native assistant · Managed by Android. Media and volume remain system controls while Halo is idle."), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { mediaControls.configure(com.halo.sdk.host.AccessoryAction.SystemDefault)
            com.halo.app.di.ManualDependencyGraph.accessoryControls.mappings.resetAll() }) { Text(uiText("Reset custom controls")) }
        if (controlsPicker) AlertDialog(onDismissRequest = { controlsPicker = false }, title = { Text(uiText("During Halo speech")) },
            text = { Column { (listOf(com.halo.sdk.host.AccessoryAction.SystemDefault) + com.halo.app.controls.ScopedSpeechMediaControls.allowed).forEach { action ->
                TextButton(onClick = { mediaControls.configure(action); controlsPicker = false }) { Text(action.name.replace(Regex("([a-z])([A-Z])"), "$1 $2")) }
            } } }, confirmButton = { TextButton(onClick = { controlsPicker = false }) { Text(uiText("Close")) } })
        Row(Modifier.fillMaxWidth()) {
            Text(uiText("Use connected audio accessories"), Modifier.weight(1f))
            Switch(accessoryPolicy.useConnectedAudioAccessories, onCheckedChange = { value -> scope.launch(Dispatchers.IO) {
                accessorySettings.update(accessorySettings.state.value.copy(useConnectedAudioAccessories = value))
            } })
        }
        Row(Modifier.fillMaxWidth()) {
            Text(uiText("Wear OS nearby interaction"), Modifier.weight(1f))
            Switch(accessoryPolicy.wearNearbyInteraction, onCheckedChange = { value -> scope.launch(Dispatchers.IO) {
                accessorySettings.update(accessorySettings.state.value.copy(wearNearbyInteraction = value))
                if (value) com.halo.app.di.ManualDependencyGraph.wearEndpointBridge.start()
                else com.halo.app.di.ManualDependencyGraph.wearEndpointBridge.stop()
            } })
        }
        Text(uiText("System audio follows Android's current output route."), style = MaterialTheme.typography.bodySmall)
        Text(uiText("Speech output"), style = MaterialTheme.typography.titleMedium)
        kokoroState?.let { state ->
            Text("Kokoro · ${uiText(state.phase)} · ${uiText(state.message)}")
            Text(uiText("English voice on this phone. Download the complete publisher pack (320 MB). Other languages can use Android speech."),style=MaterialTheme.typography.bodySmall)
            Button(onClick={scope.launch {
                importingPack=true
                try { kokoro?.downloadPack(); saveError=null }
                catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { saveError="Voice pack download failed. Check your connection and storage, then retry." }
                finally { importingPack=false }
            }},enabled=!importingPack && state.modelVersion==null) { Text(uiText("Download Kokoro voice")) }
            kokoroProgress?.let { bytes ->
                LinearProgressIndicator(progress={ (bytes.toFloat()/com.halo.app.audio.KokoroModelDownload.BYTES).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
                Text("${bytes/1000000} / 320 MB")
                TextButton(onClick={kokoro?.cancelDownload()}) { Text(uiText("Pause download")) }
            }
            Text(uiText("Downloads resume after interruption. Nothing is selected automatically."),style=MaterialTheme.typography.bodySmall)
            TextButton({packPicker.launch(arrayOf("application/zip","application/octet-stream"))},enabled=!importingPack && !testBusy) {Text(uiText(if(importingPack) "Importing…" else "Import Kokoro pack"))}
            val context=androidx.compose.ui.platform.LocalContext.current
            TextButton({context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html")))}) {Text(uiText("Model files and license"))}
            if(state.modelVersion != null) {
                Row {
                    TextButton({scope.launch { try { kokoro?.removePack() } catch(cancelled: CancellationException) {throw cancelled} catch(_: Exception) {saveError="Voice pack removal failed. Try again after playback stops."} }},enabled=!importingPack && !testBusy) {Text(uiText("Remove pack"))}
                    TextButton({kokoro?.clearCache()}) {Text(uiText("Clear cached speech"))}
                }
                var voice by remember { mutableStateOf(com.halo.app.di.ManualDependencyGraph.storage.getString("kokoro_voice","3") ?: "3") }
                Text("Kokoro voice · $voice")
                Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) { (0..10).forEach { id ->
                    TextButton({voice=id.toString();com.halo.app.di.ManualDependencyGraph.storage.putString("kokoro_voice",voice)}) {Text("$id" + if(voice==id.toString()) " ✓" else "")}
                } }
                Text(uiText("Speech cache: private PCM, at most 64 MB, expires after seven days. Other languages use the configured synthesis fallback or report unavailable."),style=MaterialTheme.typography.bodySmall)
            }
        }
        (listOf("AUTOMATIC" to "Automatic (Android TTS)") + registry.providers.map { it.metadata.id to it.metadata.displayName }).forEach { (id, label) ->
            TextButton(enabled = !saving && !testBusy && (id != com.halo.app.audio.KokoroSpeechProvider.ID || (kokoroState?.modelVersion != null && !importingPack)) && (id != ElevenLabsTtsProvider.ID || (cloudState?.configured == true && cloudState.voiceId != null && cloudState.modelId != null)), onClick = {
                saving = true
                scope.launch {
                    try { withContext(Dispatchers.IO) { registry.selector.selectTts(id) }; saveError = null }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { saveError = "Unable to save speech provider selection" }
                    finally { saving = false }
                }
            }) { Text(label + if (selection.id == id) " · " + uiText("Selected") else "") }
        }
        Text(uiText("Android TTS · Local engine; readiness checked on synthesis"))
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
            Text(uiText("ElevenLabs") + " · " + uiText(status) + " · " + uiText(if (selection.id == ElevenLabsTtsProvider.ID) "Selected" else "Not selected"))
        }
        elevenLabs?.takeIf { selection.id == ElevenLabsTtsProvider.ID }?.let { _ ->
            cloudState?.let { state ->
                Text(uiText("Voice: %1\$s", state.voiceName ?: uiText("Choose a voice")))
                Text(uiText("Model: %1\$s", state.modelName ?: uiText("Choose a model")))
            }
        }
        Text(uiText("If cloud synthesis fails"))
        TextButton(enabled = !saving && !testBusy, onClick = {
            val next = !fallback
            saving = true
            scope.launch {
                try { withContext(Dispatchers.IO) { viewModel.setCloudSynthesisFallback(next) }; fallback = next; saveError = null }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { saveError = "Unable to save speech fallback" }
                finally { saving = false }
            }
        }) {
            Text(uiText(if (fallback) "Use Android TTS" else "Stay silent"))
        }
        saveError?.let { Text(uiText(it)) }
        Text(uiText("Synthesis fallback keeps the selected audio output route."))
        TextButton(onClick = onElevenLabs) { Text(uiText("Configure ElevenLabs")) }
    }
}

@Composable
fun ElevenLabsSettingsScreen(provider: ElevenLabsTtsProvider, onBack: () -> Unit, onTestVoice: () -> Unit, onUseProvider: () -> Unit = {}) {
    val state by provider.state.collectAsStateWithLifecycle()
    var draft by remember(provider) { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var localStyle by remember(state.style) { mutableStateOf(state.style) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val configured = state.configured
    val catalogue = state.catalogue
    val voice = state.voiceId
    val model = state.modelId

    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text(uiText("Back")) }; Text(uiText("ElevenLabs speech"), style = MaterialTheme.typography.headlineSmall) }
        item { Text(uiText("Selecting ElevenLabs sends sanitized spoken response text to ElevenLabs. Conversation history, memories and tool payloads are excluded. Provider retention follows your ElevenLabs account policy.")) }
        item {
            Text(
                when {
                    state.loading -> "Loading secure configuration…"
                    state.error == "SECURE_STORAGE_UNREADABLE" -> "Stored credential cannot be read · enter the key again"
                    configured -> "Credential configured"
                    else -> "Setup required"
                }
            )
            OutlinedTextField(draft, { draft = it }, label = { Text(uiText("API key")) }, placeholder = { Text(if (configured) "Credential configured" else "Enter API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
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
            }) { Text(uiText("Remove key")) }
        }
        item {
            Text("Authentication: ${if (state.authenticated) "Ready" else "Not checked"} · Synthesis: ${if (state.ready) "Proven" else "Not tested"}")
            Button(enabled = configured && !busy, onClick = { busy = true; scope.launch { try { message = if (withContext(Dispatchers.IO) { provider.health() }) "Authentication ready; synthesis is checked separately" else provider.state.value.error ?: "Connection failed. Check your key and network connection." } finally { busy = false } } }) { Text(uiText("Test connection")) }
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
            message?.let { Text(uiText(it)) }
            state.error?.takeIf { it != message && it != "SECURE_STORAGE_UNREADABLE" }?.let { Text(uiText("Speech output is unavailable. Check authorization, voice selection and your connection.")) }
        }
        item { Text(uiText("TTS model"), style = MaterialTheme.typography.titleMedium) }
        items(catalogue?.models.orEmpty(), key = { "model:${it.id}" }) { choice ->
            TextButton(enabled = !busy, onClick = { busy = true; scope.launch {
                try { withContext(Dispatchers.IO) { provider.selectModel(choice.id) }; message = "TTS model saved" }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "Unable to save TTS model" }
                finally { busy = false }
            } }) { Text(choice.name + if (model == choice.id) " · Selected" else "") }
        }
        item { Text(uiText("Voice"), style = MaterialTheme.typography.titleMedium); OutlinedTextField(search, { search = it }, label = { Text(uiText("Search voices")) }) }
        items(catalogue?.voices.orEmpty().filter { (it.name + " " + it.category.orEmpty()).contains(search, true) }, key = { "voice:${it.id}" }) { choice ->
            TextButton(enabled = !busy, onClick = { busy = true; scope.launch {
                try { withContext(Dispatchers.IO) { provider.selectVoice(choice.id) }; message = "Voice saved" }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "Unable to save voice" }
                finally { busy = false }
            } }) { Text(choice.name + choice.category?.let { " · $it" }.orEmpty() + if (voice == choice.id) " · Selected" else "") }
        }
        item {
            Text(uiText("Voice style"), style = MaterialTheme.typography.titleMedium)
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
            }) { Text(uiText("Use ElevenLabs for speech output")) }
            Button(enabled = configured && voice != null && model != null && !busy, onClick = onTestVoice) { Text(uiText("Speak test phrase on phone")) }
        }
    }
}
