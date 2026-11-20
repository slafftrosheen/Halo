package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import kotlinx.coroutines.*

@Composable internal fun OpenRouterRecognitionSettings() {
    val control = remember { Graph.openRouterRecognitionControl }
    val state by control.provider.state.collectAsStateWithLifecycle()
    val mode by control.modes.mode.collectAsStateWithLifecycle()
    var model by remember(state.modelId) { mutableStateOf(state.modelId) }
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    HaloSettingsSection(uiText("OpenRouter transcription")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(uiText("Power mode can transcribe your speech with your saved OpenRouter key. Local mode keeps recognition on this phone. Transcription is charged to your OpenRouter account."))
            if (!state.configured) Text(uiText("Add your OpenRouter key in AI services."))
            OutlinedButton(enabled = !busy && mode == com.halo.app.ai.PrivacyMode.CLOUD, onClick = {
                busy = true
                scope.launch {
                    try { withContext(Dispatchers.IO) { control.refresh() }; notice = null }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { notice = "Could not load transcription models. Check Power mode, your OpenRouter key and connection." }
                    finally { busy = false }
                }
            }) { Text(uiText(if (busy) "Working..." else "Load transcription models")) }
            Box {
                OutlinedButton(enabled = !busy && state.models.isNotEmpty(), onClick = { expanded = true }) {
                    Text(state.models.firstOrNull { it.id == model }?.name ?: model ?: uiText("Choose a transcription model"))
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    state.models.forEach { choice -> DropdownMenuItem(text = { Text(choice.name) }, onClick = {
                        model = choice.id; expanded = false
                    }) }
                }
            }
            model?.takeIf { it.matches(Regex("[a-zA-Z0-9_.:-]+/[a-zA-Z0-9_.:-]+")) }?.let { id ->
                TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://openrouter.ai/$id"))) }) { Text(uiText("Model details and pricing")) }
            }
            Button(enabled = !busy && state.models.any { it.id == model } && mode == com.halo.app.ai.PrivacyMode.CLOUD,
                onClick = {
                    val selected = model ?: return@Button
                    val revision = control.modes.revision
                    val version = state.sourceVersion
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { control.configure(selected, revision, version) }
                            notice = "OpenRouter transcription selected. Use the microphone test to try it."
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { notice = "Listening settings could not be saved. Load the models and try again." }
                        finally { busy = false }
                    }
                }) { Text(uiText("Use OpenRouter transcription")) }
            notice?.let { Text(uiText(it)) }
        }
    }
}
