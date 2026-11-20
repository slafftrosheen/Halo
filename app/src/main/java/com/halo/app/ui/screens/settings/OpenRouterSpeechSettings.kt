package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(provider) { withContext(Dispatchers.IO) { provider.reloadConfigurationNow() } }
    HaloSettingsSection(uiText("OpenRouter speech")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(uiText("Uses your saved OpenRouter key. No ElevenLabs key is needed. Speech is charged to your OpenRouter account."))
            if (!state.configured) Text(uiText("Add your OpenRouter key in AI services."))
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
                    Text(state.models.firstOrNull { it.id == model }?.name ?: model ?: uiText("Choose a speech model"))
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    state.models.forEach { choice -> DropdownMenuItem(text = { Text(choice.name) }, onClick = {
                        if (model != choice.id) voice = ""
                        model = choice.id; expanded = false
                    }) }
                }
            }
            val supportedVoices = state.models.firstOrNull { it.id == model }?.voices.orEmpty()
            if (supportedVoices.isNotEmpty()) Box {
                OutlinedButton(enabled = !busy, onClick = { voicesExpanded = true }) {
                    Text(voice.takeIf { it in supportedVoices } ?: uiText("Choose a voice"))
                }
                DropdownMenu(voicesExpanded, onDismissRequest = { voicesExpanded = false }) {
                    supportedVoices.forEach { supported -> DropdownMenuItem(text = { Text(supported) }, onClick = {
                        voice = supported; voicesExpanded = false
                    }) }
                }
            } else HaloTextField(voice, { voice = it.take(200) }, label = { Text(uiText("Voice name")) },
                kind = VoiceFieldKind.SHORT, singleLine = true)
            Text(uiText("Choose a voice supported by this model. Voice names differ between models."), style = MaterialTheme.typography.bodySmall)
            model?.takeIf { it.matches(Regex("[a-zA-Z0-9_.:-]+/[a-zA-Z0-9_.:-]+")) }?.let { id ->
                TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://openrouter.ai/$id"))) }) { Text(uiText("Model voices and pricing")) }
            }
            Button(enabled = !busy && voice.isNotBlank() && (supportedVoices.isEmpty() || voice in supportedVoices) && state.models.any { it.id == model } && mode == com.halo.app.ai.PrivacyMode.CLOUD,
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
            notice?.let { Text(uiText(it)) }
        }
    }
}
