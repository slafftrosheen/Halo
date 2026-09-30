package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.halo.app.ai.ProviderControlRepository
import com.halo.app.ai.SpeechSynthesisRegistry
import com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider

@Composable
fun ProviderDiagnosticsPanel(control: ProviderControlRepository, speech: SpeechSynthesisRegistry) {
    val rows by control.rows.collectAsState()
    val selection by speech.selector.ttsSelection.collectAsState()
    val sttSelection by speech.selector.sttSelection.collectAsState()
    val recognitionAvailable by com.halo.app.di.ManualDependencyGraph.speechRecognitionAvailability.collectAsState()
    Column {
        Text("Provider diagnostics", style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            Text("${row.descriptor.displayName} · ${if (row.selected) "selected · " else ""}${row.connection.name} · ${row.modelCount} models · ${row.catalogue.name} · generation ${row.generation.name}")
            TextButton(enabled = row.configured && row.connection != com.halo.app.ai.ProviderConnectionState.CHECKING,
                onClick = { control.testConnection(row.descriptor.providerId) }) { Text("Test ${row.descriptor.displayName} connection") }
            TextButton(enabled = row.configured && row.generation != com.halo.app.ai.GenerationProof.CHECKING, onClick = { control.checkGeneration(row.descriptor.providerId) }) { Text("Run generation check") }
        }
        Text("STT: ${sttSelection.id} · ${if (recognitionAvailable) "available" else "not installed"} · language and installation in Voice settings")
        Text("TTS selection: ${selection.id}")
        speech.providers.forEach { provider ->
            val cloud = provider as? ElevenLabsTtsProvider
            val health = cloud?.state?.collectAsState()?.value
            Text("${provider.metadata.displayName} · ${if (provider.metadata.local) "local" else "cloud"} · ${if (cloud == null || health?.configured == true) "configured" else "not configured"}")
            health?.let { Text(if (it.ready) "Synthesis ready" else it.error ?: "Synthesis not checked") }
            if (cloud != null) Text("Voice ${cloud.voiceId() ?: "not selected"} · TTS model ${cloud.modelId() ?: "not selected"} · metadata ${if (cloud.cachedCatalogue()?.stale == true) "stale" else if (cloud.cachedCatalogue() != null) "cached" else "unavailable"}")
            TextButton(onClick = { com.halo.app.di.ManualDependencyGraph.haloSpeechOutputCoordinator.preview(provider.metadata.id) }) { Text("Test ${provider.metadata.displayName} voice on phone") }
        }
    }
}
