package com.halo.app.ui.screens.home

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.components.VoiceFieldCancellation
import java.util.UUID

/** Observe newly committed answers, never replay a restored answer or silently consent to a cloud voice. */
@Composable
internal fun HomeAutoReadAnswers(state: HomeUiState, text: String, active: Boolean) {
    val reader = remember { Graph.readAloud }
    val enabled by reader.autoReadAnswers.collectAsStateWithLifecycle()
    val id = state.presentedIdentity?.turnId
    var observed by remember { mutableStateOf(id) }
    LaunchedEffect(id, active) {
        if (active || id == null || id == observed || text.isBlank()) return@LaunchedEffect
        observed = id
        if (state.sessionState is com.halo.sdk.provider.CognitiveSessionState.Failed) return@LaunchedEffect
        if (!enabled || VoiceFieldCancellation.activeFields.value.isNotEmpty()) return@LaunchedEffect
        val registry = Graph.speechSynthesisRegistry
        val provider = registry.selector.ttsSelection.value.id
        if (provider != "AUTOMATIC" && registry.providers.firstOrNull { it.metadata.id == provider }?.metadata?.local != true)
            return@LaunchedEffect
        VoiceFieldCancellation.stop()
        reader.start(UUID.randomUUID().toString(), text, provider, false, null)
    }
}
