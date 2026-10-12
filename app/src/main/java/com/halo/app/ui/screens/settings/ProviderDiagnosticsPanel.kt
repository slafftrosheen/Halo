package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.halo.app.ai.label
import com.halo.app.ai.ProviderControlRepository
import com.halo.app.ai.SpeechSynthesisRegistry
import com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider

@Composable
fun ProviderDiagnosticsPanel(control: ProviderControlRepository, speech: SpeechSynthesisRegistry) {
    val rows by control.rows.collectAsStateWithLifecycle()
    val bindings by control.router.roleBindings.collectAsStateWithLifecycle()
    val chatProvider by control.router.selectedProviderId.collectAsStateWithLifecycle()
    val chatModel by control.router.selectedModelId.collectAsStateWithLifecycle()
    val selection by speech.selector.ttsSelection.collectAsStateWithLifecycle()
    val sttSelection by speech.selector.sttSelection.collectAsStateWithLifecycle()
    val recognitionAvailable by com.halo.app.di.ManualDependencyGraph.speechRecognitionAvailability.collectAsStateWithLifecycle()
    Column {
        Text(uiText("AI providers"), style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            Text("${row.descriptor.displayName} · ${if (row.selected) "selected · " else ""}${row.connection.label} · ${row.modelCount} models · ${row.catalogue.name.lowercase()} · ${row.generation.label}")
            row.lastError?.let { err ->
                Text(uiText("Provider check failed. Review configuration and try again."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            TextButton(enabled = row.configured && row.connection != com.halo.app.ai.ProviderConnectionState.CHECKING,
                onClick = { control.testConnection(row.descriptor.providerId) }) { Text("Test ${row.descriptor.displayName} connection") }
            TextButton(enabled = row.configured && row.generation != com.halo.app.ai.GenerationProof.CHECKING, onClick = { control.checkGeneration(row.descriptor.providerId) }) { Text(uiText("Run generation check")) }
        }
        Text(uiText("Selected routes"), style = MaterialTheme.typography.titleMedium)
        Text("Chat: ${rows.firstOrNull { it.descriptor.providerId == chatProvider }?.descriptor?.displayName ?: "Unset"} / ${chatModel ?: "Unset"}")
        listOf(com.halo.sdk.provider.AiModelRole.AGENTIC, com.halo.sdk.provider.AiModelRole.VISION).forEach { role ->
            val binding = bindings[role]
            Text("${role.name.lowercase().replaceFirstChar { it.uppercase() }}: ${binding?.providerId ?: binding?.selection?.name?.lowercase()?.replace('_', ' ') ?: "Automatic"} / ${binding?.modelId ?: "Default model"}")
        }
        Text(uiText("Speech output"), style = MaterialTheme.typography.titleMedium)
        Text("STT: ${if (sttSelection.id == "AUTOMATIC") "Automatic" else sttSelection.id} · ${if (recognitionAvailable) "available" else "not installed"} · language and installation in Voice settings")
        Text("TTS selection: ${if (selection.id == "AUTOMATIC") "Android TTS (automatic)" else speech.providers.firstOrNull { it.metadata.id == selection.id }?.metadata?.displayName ?: selection.id}")
        speech.providers.forEach { provider ->
            val cloud = provider as? ElevenLabsTtsProvider
            val health = cloud?.state?.collectAsStateWithLifecycle()?.value
            Text("${provider.metadata.displayName} · ${if (provider.metadata.local) "local" else "cloud"} · ${if (cloud == null || health?.configured == true) "configured" else "not configured"}")
            health?.let { Text(if (it.ready) "Synthesis ready" else it.errorCode?.label ?: "Synthesis not checked") }
            if (cloud != null) Text("Voice ${cloud.voiceId() ?: "not selected"} · TTS model ${cloud.modelId() ?: "not selected"} · metadata ${if (cloud.cachedCatalogue()?.stale == true) "stale" else if (cloud.cachedCatalogue() != null) "cached" else "unavailable"}")
            TextButton(onClick = { com.halo.app.di.ManualDependencyGraph.haloSpeechOutputCoordinator.preview(provider.metadata.id) }) { Text("Test ${provider.metadata.displayName} voice on phone") }
        }
    }
}
