package com.halo.app.ui.screens.home

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.components.VoiceFieldCancellation
import java.util.UUID

/** Observe newly committed answers, never replay a restored answer; the app-wide mode governs speech. */
@Composable
internal fun HomeAutoReadAnswers(state: HomeUiState, text: String, active: Boolean, submission: TextSubmissionState) {
    val reader = remember { Graph.readAloud }
    val enabled by reader.autoReadAnswers.collectAsStateWithLifecycle()
    val reply = state.presentedIdentity?.takeIf { it.source == ResponsePresentationSource.COMMITTED_CONVERSATION }
    val id = reply?.turnId
    val completedSubmission = submission.phase == TextSubmissionPhase.COMPLETED &&
        reply != null && submission.conversationId == reply.conversationId &&
        submission.interactionId != null && submission.interactionId == reply.interactionId
    val tracker = remember { HomeAutoReadTracker() }
    LaunchedEffect(state.conversationId, id, active, text, state.sessionState, submission.busy, completedSubmission) {
        if (!tracker.observe(state.conversationId, id, active || submission.busy, text.isNotBlank(),
                state.sessionState is com.halo.sdk.provider.CognitiveSessionState.Failed, completedSubmission)) return@LaunchedEffect
        if (!enabled || VoiceFieldCancellation.activeFields.value.isNotEmpty()) return@LaunchedEffect
        val registry = Graph.speechSynthesisRegistry
        val provider = registry.selector.ttsSelection.value.id
        VoiceFieldCancellation.stop()
        val provenance = reply?.remoteSpeechProvenance
        reader.start(UUID.randomUUID().toString(), reply?.text ?: return@LaunchedEffect, provider,
            Graph.powerModeEnabled() && provenance != null, null, remoteProvenance = provenance)
    }
}

/** Restoring a thread is never a speech trigger, including delayed Room emissions. */
internal class HomeAutoReadTracker {
    private var conversation: String? = null
    private var observed: String? = null
    private var armed = false

    fun observe(conversationId: String?, turnId: String?, active: Boolean, hasText: Boolean,
        failed: Boolean = false, completedSubmission: Boolean = false): Boolean {
        if (conversationId != conversation) {
            conversation = conversationId
            observed = turnId
            armed = false
        }
        if (failed) { armed = false; observed = turnId; return false }
        if (active) { armed = true; return false }
        if (turnId == null || !hasText) return false
        if (turnId == observed) return false // Room may commit after the session becomes idle.
        val read = armed || completedSubmission
        observed = turnId
        armed = false
        return read
    }
}
