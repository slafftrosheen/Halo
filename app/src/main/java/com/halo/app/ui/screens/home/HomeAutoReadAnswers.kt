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
    val tracker = remember { HomeAutoReadTracker() }
    LaunchedEffect(state.conversationId, id, active, text, state.sessionState) {
        if (!tracker.observe(state.conversationId, id, active, text.isNotBlank(),
                state.sessionState is com.halo.sdk.provider.CognitiveSessionState.Failed)) return@LaunchedEffect
        if (!enabled || VoiceFieldCancellation.activeFields.value.isNotEmpty()) return@LaunchedEffect
        val registry = Graph.speechSynthesisRegistry
        val provider = registry.selector.ttsSelection.value.id
        if (provider != "AUTOMATIC" && registry.providers.firstOrNull { it.metadata.id == provider }?.metadata?.local != true)
            return@LaunchedEffect
        VoiceFieldCancellation.stop()
        reader.start(UUID.randomUUID().toString(), text, provider, false, null)
    }
}

/** Restoring a thread is never a speech trigger, including delayed Room emissions. */
internal class HomeAutoReadTracker {
    private var conversation: String? = null
    private var observed: String? = null
    private var armed = false

    fun observe(conversationId: String?, turnId: String?, active: Boolean, hasText: Boolean,
        failed: Boolean = false): Boolean {
        if (conversationId != conversation) {
            conversation = conversationId
            observed = turnId
            armed = false
        }
        if (failed) { armed = false; observed = turnId; return false }
        if (active) { armed = true; return false }
        if (turnId == null || !hasText) return false
        if (turnId == observed) return false // Room may commit after the session becomes idle.
        val read = armed && turnId != observed
        observed = turnId
        armed = false
        return read
    }
}
