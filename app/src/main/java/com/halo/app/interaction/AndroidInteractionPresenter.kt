package com.halo.app.interaction

import com.halo.runtime.cognition.interaction.InteractionResponse
import com.halo.runtime.cognition.interaction.InteractionResponseHandler
import com.halo.runtime.cognition.interaction.InteractionSurface
import com.halo.runtime.cognition.interaction.InteractionSurfaceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * RUNTIME-019 Android host presenter.
 *
 * Responsibilities: observe Runtime surfaces, select the presentation candidate
 * deterministically, and route typed responses back into Runtime through the
 * [InteractionResponseHandler]. Contains no cognition logic and never executes
 * tools directly.
 */
class AndroidInteractionPresenter(
    private val surfaceStore: InteractionSurfaceStore,
    private val responseHandler: InteractionResponseHandler,
    /** Optional voice bridge onto the EXISTING speech runtime (specs #36–#39). */
    private val voiceAdapter: VoiceInteractionSurfaceAdapter? = null,
    /** Origin-device affinity: the speech output target of the originating run. */
    private val originLookup: suspend (runId: com.halo.runtime.cognition.agent.AgentRunId) -> com.halo.sdk.runtime.SpeechOutputTarget? = { null },
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(InteractionPresentationState())
    val state: StateFlow<InteractionPresentationState> = _state.asStateFlow()

    private var preferredRunId: com.halo.runtime.cognition.agent.AgentRunId? = null

    /** Explicit Activity selection changes presentation only; Runtime still validates every response. */
    fun showRun(runId: com.halo.runtime.cognition.agent.AgentRunId) {
        preferredRunId = runId
        val surfaces = surfaceStore.active.value
        _state.value = InteractionPresentationState(surfaces, selectPrimary(surfaces))
    }

    fun start() {
        scope.launch {
            surfaceStore.active.collect { surfaces ->
                _state.value = InteractionPresentationState(
                    surfaces = surfaces,
                    primary = selectPrimary(surfaces),
                )
                maybeSpeak(_state.value.primary)
            }
        }
    }

    /** Spoken once per surface identity; a re-presented surface speaks again. */
    private val spokenSurfaceIds = mutableSetOf<String>()

    private fun maybeSpeak(surface: InteractionSurface?) {
        val voice = voiceAdapter ?: return
        if (surface == null || surface.id.value in spokenSurfaceIds) return
        if (surface.kind != com.halo.runtime.cognition.interaction.InteractionSurfaceKind.CONFIRMATION) return
        spokenSurfaceIds.add(surface.id.value)
        scope.launch {
            val runId = surface.runId ?: return@launch
            voice.speak(surface, originLookup(runId))
        }
    }

    suspend fun respond(response: InteractionResponse) {
        responseHandler.respond(com.halo.runtime.cognition.interaction.InteractionResponseSubmission(response, com.halo.runtime.cognition.continuity.InteractionEndpoint.Phone))
    }

    /**
     * Deterministic priority (spec #31):
     * input-required consequential → input-required normal → error → progress
     * → result. Nothing here depends on which Flow emitted last.
     */
    private fun selectPrimary(surfaces: List<InteractionSurface>): InteractionSurface? =
        surfaces.filter { it.runId == preferredRunId && it.inputRequestId != null }.maxByOrNull { rank(it) }
            ?: surfaces.maxByOrNull { rank(it) }

    private fun rank(surface: InteractionSurface): Int = when (surface.kind) {
        com.halo.runtime.cognition.interaction.InteractionSurfaceKind.CONFIRMATION ->
            if (surface.presentation.urgency == com.halo.runtime.cognition.interaction.InteractionUrgency.HIGH ||
                surface.presentation.urgency == com.halo.runtime.cognition.interaction.InteractionUrgency.CRITICAL
            ) 50 else 40
        com.halo.runtime.cognition.interaction.InteractionSurfaceKind.CLARIFICATION,
        com.halo.runtime.cognition.interaction.InteractionSurfaceKind.CHOICE,
        com.halo.runtime.cognition.interaction.InteractionSurfaceKind.TEXT_INPUT,
        com.halo.runtime.cognition.interaction.InteractionSurfaceKind.TEMPORAL_INPUT,
        com.halo.runtime.cognition.interaction.InteractionSurfaceKind.FILE_SELECTION,
        -> 30
        com.halo.runtime.cognition.interaction.InteractionSurfaceKind.ERROR -> 20
        com.halo.runtime.cognition.interaction.InteractionSurfaceKind.PROGRESS -> 10
        com.halo.runtime.cognition.interaction.InteractionSurfaceKind.RESULT -> 5
        /*
         * MINING-P2-001: a presentation is output, so it ranks with a result and can never outrank
         * an unresolved input request. A generated card must never cover an approval prompt.
         */
        com.halo.runtime.cognition.interaction.InteractionSurfaceKind.PRESENTATION -> 5
    }
}

/**
 * Immutable host presentation state. [primary] is the surface shown as the
 * modal candidate; the rest stay available for Activity rendering.
 */
data class InteractionPresentationState(
    val surfaces: List<InteractionSurface> = emptyList(),
    val primary: InteractionSurface? = null,
)
