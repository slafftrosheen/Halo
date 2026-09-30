package com.halo.runtime.cognition.interaction

import com.halo.runtime.cognition.agent.AgentInputResolution
import com.halo.runtime.cognition.agent.AgentInputRequestId
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.runtime.cognition.agent.AgentRunStore
import com.halo.runtime.cognition.agent.AgentInputResolutions
import com.halo.runtime.cognition.agent.isTerminal
import java.time.Instant

/**
 * RUNTIME-019 Runtime boundary for host responses. The UI never executes
 * tools: hosts submit typed [InteractionResponse]s, this handler validates
 * them against the active surface and its durable request, journals ONE
 * atomic resolution, and hands off to run resumption.
 */
interface InteractionResponseHandler {
    suspend fun respond(response: InteractionResponse): InteractionResponseResult
}

sealed interface InteractionResponseResult {
    /** Resolution journaled (or presentation dismissed); host should stop offering the surface. */
    data object Accepted : InteractionResponseResult

    /** Response is structurally or semantically invalid for this surface. */
    data class Rejected(val reason: String) : InteractionResponseResult

    /** The durable request expired before resolution. */
    data object Expired : InteractionResponseResult

    /** Surface gone or durable request already resolved by another host/surface. */
    data object NoLongerPending : InteractionResponseResult
}

/**
 * Default handler. [resolveInput] must be the atomic CAS
 * ([com.halo.runtime.cognition.agent.AgentRunStore.resolveInputIfPending]);
 * [resumeRun] wraps the R-018 claim + session resume (never tool execution);
 * [cancelRun] routes to existing execution cancellation.
 */
class DefaultInteractionResponseHandler(
    private val surfaceStore: InteractionSurfaceStore,
    private val runStore: AgentRunStore,
    private val resumeRun: suspend (runId: com.halo.runtime.cognition.agent.AgentRunId) -> ResumeRequestResult,
    private val cancelRun: suspend (runId: com.halo.runtime.cognition.agent.AgentRunId) -> Boolean = { false },
    private val coordinator: InteractionSurfaceCoordinator? = null,
) : InteractionResponseHandler {

    override suspend fun respond(response: InteractionResponse): InteractionResponseResult {
        val surface = surfaceStore.get(response.surfaceId) ?: return InteractionResponseResult.NoLongerPending

        // Presentation-only: dismissing hides the surface; the durable request
        // stays pending and may be re-presented later.
        if (response is InteractionResponse.Dismissed) {
            surfaceStore.dismiss(surface.id)
            return InteractionResponseResult.Accepted
        }

        val requestId = surface.inputRequestId
            ?: return routeNonInputResponse(surface, response)

        // Pre-checks (the CAS below stays authoritative against races).
        val now = Instant.now()
        if (surface.expiresAt != null && surface.expiresAt.isBefore(now)) {
            runStore.resolveInputIfPending(requestId, AgentInputResolution.Expired)
            coordinator?.onInputResolved(requestId)
            return InteractionResponseResult.Expired
        }
        val runId = surface.runId
            ?: return InteractionResponseResult.Rejected("input surface has no run binding")
        val run = runStore.get(runId)
            ?: return InteractionResponseResult.NoLongerPending
        if (run.state.isTerminal) return InteractionResponseResult.NoLongerPending

        val failure = validateResponse(surface, response)
        if (failure != null) return InteractionResponseResult.Rejected(failure)

        val resolution = durableResolution(response, surface)
            ?: return InteractionResponseResult.Rejected("response type not allowed for this surface")

        // Atomic: first valid resolution wins; every later response becomes
        // NoLongerPending and hosts drop their representations.
        val accepted = runStore.resolveInputIfPending(requestId, resolution)
        if (!accepted) return InteractionResponseResult.NoLongerPending

        coordinator?.onInputResolved(requestId)

        val cancelScope = cancelScopeOf(surface)
        return when {
            response is InteractionResponse.Cancelled && cancelScope == InteractionCancelScope.RUN -> {
                cancelRun(runId)
                InteractionResponseResult.Accepted
            }
            else -> {
                resumeRun(runId)
                InteractionResponseResult.Accepted
            }
        }
    }

    /** Progress cancellation routes through Runtime execution cancellation. */
    private suspend fun routeNonInputResponse(
        surface: InteractionSurface,
        response: InteractionResponse,
    ): InteractionResponseResult {
        if (response is InteractionResponse.Cancelled &&
            surface.content is InteractionContent.Progress &&
            surface.content.cancellable &&
            surface.runId != null
        ) {
            cancelRun(surface.runId)
            return InteractionResponseResult.Accepted
        }
        return InteractionResponseResult.Rejected("surface does not accept responses")
    }

    private fun cancelScopeOf(surface: InteractionSurface): InteractionCancelScope = when (val c = surface.content) {
        is InteractionContent.Confirmation -> c.cancelScope
        is InteractionContent.Choice -> c.cancelScope
        is InteractionContent.TextInput -> c.cancelScope
        is InteractionContent.TemporalInput -> c.cancelScope
        is InteractionContent.FileSelection -> c.cancelScope
        else -> InteractionCancelScope.INPUT_ONLY
    }

    /** Structural validation of the response against the surface content. */
    private fun validateResponse(surface: InteractionSurface, response: InteractionResponse): String? {
        return when (val content = surface.content) {
            is InteractionContent.Confirmation ->
                if (response !is InteractionResponse.Confirmed && response !is InteractionResponse.Denied &&
                    response !is InteractionResponse.Cancelled
                ) {
                    "confirmation surface accepts confirm/deny/cancel"
                } else {
                    null
                }
            is InteractionContent.Choice -> {
                if (response !is InteractionResponse.ChoiceSelected) {
                    "choice surface requires a choice selection"
                } else {
                    val validIds = content.options.map { it.id }.toSet()
                    val unknown = response.optionIds - validIds
                    when {
                        response.optionIds.isEmpty() -> "no option selected"
                        unknown.isNotEmpty() -> "unknown option ids: $unknown"
                        !content.allowMultiple && response.optionIds.size > 1 -> "single choice only"
                        else -> null
                    }
                }
            }
            is InteractionContent.TextInput -> {
                if (response !is InteractionResponse.TextSubmitted) {
                    "text surface requires submitted text"
                } else {
                    val v = response.value
                    when {
                        v.length < content.constraints.minLength -> "text shorter than minLength"
                        v.length > content.constraints.maxLength -> "text longer than maxLength"
                        content.constraints.singleLine && v.contains('\n') -> "single-line text expected"
                        else -> null
                    }
                }
            }
            is InteractionContent.TemporalInput ->
                if (response !is InteractionResponse.TemporalSubmitted) "temporal surface requires a temporal value" else null
            is InteractionContent.FileSelection ->
                if (response !is InteractionResponse.FilesSelected) "file surface requires file references" else null
            is InteractionContent.Progress,
            is InteractionContent.Result,
            is InteractionContent.Error,
            // MINING-P2-001: a structured presentation is a view, not a durable input request. A
            // ChoiceGroup inside one resolves the *existing* clarification surface, never the card.
            is InteractionContent.Presentation,
            -> "surface does not resolve durable input"
        }
    }

    /**
     * Canonical durable resolution, journaled via [AgentInputResolutions.encode]
     * as the `InputResolved.resolution` string. Sensitive text is NEVER journaled
     * verbatim: only redacted length evidence is kept (spec #54/#55).
     */
    private fun durableResolution(response: InteractionResponse, surface: InteractionSurface): AgentInputResolution? {
        val resolution = response.toResolution(surface.content)
        return when {
            resolution is AgentInputResolution.Clarified -> {
                val content = surface.content as? InteractionContent.TextInput
                val text = response as? InteractionResponse.TextSubmitted
                if (content != null && content.constraints.sensitive && text != null) {
                    AgentInputResolution.Clarified("<redacted:${text.value.length} chars>")
                } else {
                    resolution
                }
            }
            else -> resolution
        }
    }
}
