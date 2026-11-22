package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.providers.openrouter.OpenRouterSpeechProvider
import kotlinx.coroutines.*

@Composable internal fun OpenRouterSpeechSettings(provider: OpenRouterSpeechProvider) {
    val control = remember { Graph.openRouterSpeechControl }
    val state by provider.state.collectAsStateWithLifecycle()
    val mode by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    var model by remember(state.modelId) { mutableStateOf(state.modelId) }
    var voice by remember(state.voiceId) { mutableStateOf(state.voiceId.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    var voicesExpanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val output = remember { Graph.haloSpeechOutputCoordinator }
    val outputTest by output.outputTest.collectAsStateWithLifecycle()
    var previewId by remember { mutableStateOf<String?>(null) }
    val activeTest = outputTest.transactionId != null && outputTest.outcome in setOf("Preparing", "Route verified · Playing")
    val ownTest = previewId != null && previewId == outputTest.transactionId
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(provider) { withContext(Dispatchers.IO) { provider.reloadConfigurationNow() } }
    HaloSettingsSection(androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_voices_online_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(uiText("Uses your saved OpenRouter key. No ElevenLabs key is needed. Speech is charged to your OpenRouter account."))
            if (!state.configured) Text(uiText("Add your key in AI services."))
            Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_voices_from_list), style = MaterialTheme.typography.bodySmall)
            com.halo.app.ui.components.CloudModeResolution()
            OutlinedButton(enabled = !busy && mode == com.halo.app.ai.PrivacyMode.CLOUD, onClick = {
                busy = true
                scope.launch {
                    try { withContext(Dispatchers.IO) { control.refresh() }; notice = null }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { notice = "Could not load speech models. Check Power mode, your OpenRouter key and connection." }
                    finally { busy = false }
                }
            }) { Text(uiText(if (busy) "Working..." else "Load speech models")) }
            Box {
                OutlinedButton(enabled = !busy && state.models.isNotEmpty(), onClick = { expanded = true }) {
                    Text(state.models.firstOrNull { it.id == model }?.name ?: uiText("Choose a speech model"))
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    state.models.forEach { choice -> DropdownMenuItem(text = { Text(choice.name) }, onClick = {
                        if (model != choice.id) voice = ""
                        model = choice.id; expanded = false
                    }) }
                }
            }
            val selectedModel = state.models.firstOrNull { it.id == model }
            val supportedVoices = selectedModel?.voices.orEmpty()
            if (supportedVoices.isNotEmpty()) Box {
                OutlinedButton(enabled = !busy, onClick = { voicesExpanded = true }) {
                    Text(voice.takeIf { it in supportedVoices } ?: uiText("Choose a voice"))
                }
                DropdownMenu(voicesExpanded, onDismissRequest = { voicesExpanded = false }) {
                    supportedVoices.forEach { supported -> DropdownMenuItem(text = { Text(supported) }, onClick = {
                        voice = supported; voicesExpanded = false
                    }) }
                }
            } else if (selectedModel != null) {
                HaloTextField(value = voice, onValueChange = { voice = it.take(200) }, kind = VoiceFieldKind.URL_ID, enabled = !busy,
                    label = { Text(uiText("Voice ID")) }, singleLine = true)
                Text(uiText("OpenRouter did not list voices for this model. Enter a voice ID from the provider."),
                    style = MaterialTheme.typography.bodySmall)
                if (selectedModel.id == "fish-audio/s2.1-pro-free:free") {
                    TextButton(enabled = !busy, onClick = { voice = "9a9cf47702da476aa4629e2506d4a857" }) {
                        Text(uiText("Use Fish Audio’s Hannah voice ID"))
                    }
                }
            }
            Text(uiText("Choose a voice supported by this model. Voice names differ between models."), style = MaterialTheme.typography.bodySmall)
            model?.takeIf { it.matches(Regex("[a-zA-Z0-9_.:-]+/[a-zA-Z0-9_.:-]+")) }?.let { id ->
                TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://openrouter.ai/$id"))) }) { Text(uiText("Model voices and pricing")) }
            }
            Button(enabled = !busy && voice.isNotBlank() && voice.none(Char::isISOControl) &&
                (supportedVoices.isEmpty() || voice in supportedVoices) && selectedModel != null && mode == com.halo.app.ai.PrivacyMode.CLOUD,
                onClick = {
                    val selectedModel = model ?: return@Button
                    val selectedVoice = voice
                    val revision = Graph.privacyModes.revision
                    val version = state.sourceVersion
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                control.configure(selectedModel, selectedVoice, revision, version)
                            }
                            notice = "OpenRouter speech selected. Use Play test phrase to hear it."
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { notice = "Voice settings could not be saved. Load the models and try again." }
                        finally { busy = false }
                    }
                }) { Text(uiText("Use OpenRouter speech")) }
            if (state.configured && model != null && state.modelId == model && state.voiceId == voice && voice.isNotBlank()) {
                OutlinedButton(onClick = {
                    previewId = output.testOutput(com.halo.sdk.runtime.SpeechOutputTarget.Phone, "Phone speaker",
                        phrase = "Halo voice test.", previewProviderId = provider.metadata.id,
                        previewModelId = model, previewVoiceId = voice)
                }, enabled = !busy && !activeTest && mode == com.halo.app.ai.PrivacyMode.CLOUD,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_voices_play_test))
                }
            }
            if (ownTest) {
                Text(uiText(outputTest.outcome), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (activeTest) TextButton(onClick = output::stopOutputTest) { Text(uiText("Stop")) }
            }
            notice?.let { Text(uiText(it)) }
        }
    }
}
