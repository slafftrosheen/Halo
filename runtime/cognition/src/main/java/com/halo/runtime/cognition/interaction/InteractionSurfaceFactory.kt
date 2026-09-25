package com.halo.runtime.cognition.interaction

import com.halo.runtime.cognition.agent.AgentInputRequest
import com.halo.runtime.cognition.agent.AgentRun
import com.halo.runtime.cognition.agent.ExpectedInput
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * RUNTIME-019 deterministic surface identities.
 *
 * Surfaces derived from durable input are stable functions of the input
 * request id, so process restarts recreate the SAME conceptual surface.
 * Progress and result surfaces use run-scoped stable ids so updates mutate
 * the projection instead of creating endless duplicates.
 */
object InteractionSurfaceIds {
    const val SCHEMA_VERSION: Int = CURRENT_SURFACE_SCHEMA_VERSION

    fun forInput(requestId: com.halo.runtime.cognition.agent.AgentInputRequestId): InteractionSurfaceId =
        InteractionSurfaceId("input:${requestId.value}")

    fun forRunProgress(runId: com.halo.runtime.cognition.agent.AgentRunId): InteractionSurfaceId =
        InteractionSurfaceId("run:${runId.value}:progress")

    fun forToolResult(runId: com.halo.runtime.cognition.agent.AgentRunId, invocationId: String): InteractionSurfaceId =
        InteractionSurfaceId("run:${runId.value}:tool:${invocationId}:result")

    fun forRunError(runId: com.halo.runtime.cognition.agent.AgentRunId): InteractionSurfaceId =
        InteractionSurfaceId("run:${runId.value}:error")
}

/**
 * Pure Runtime mapper: durable [AgentInputRequest] → renderable
 * [InteractionSurface]. No host knowledge; hosts decide sheet vs dialog vs
 * spoken question. The surface is a projection — the request in the journal
 * remains the only durable truth.
 */
interface InteractionSurfaceFactory {
    fun forInput(request: AgentInputRequest, run: AgentRun): InteractionSurface
}

class DefaultInteractionSurfaceFactory : InteractionSurfaceFactory {

    override fun forInput(request: AgentInputRequest, run: AgentRun): InteractionSurface {
        val now = Instant.now()
        val id = InteractionSurfaceIds.forInput(request.id)

        val base = SurfaceParts(
            id = id,
            runId = request.runId,
            interactionId = run.interactionId,
            inputRequestId = request.id,
            createdAt = now,
            expiresAt = request.expiresAt,
            provenance = InteractionSurfaceProvenance(
                source = InteractionSurfaceSource.AGENT_INPUT,
                sourceId = request.id.value,
            ),
        )

        return when (request) {
            is AgentInputRequest.Approval -> {
                val riskLevel = runCatching { InteractionRiskLevel.valueOf(request.risk) }
                    .getOrDefault(InteractionRiskLevel.EXTERNAL_SIDE_EFFECT)
                val content = InteractionContent.Confirmation(
                    subject = request.action,
                    detail = request.detail,
                    risk = InteractionRiskPresentation(
                        level = riskLevel,
                        summary = request.detail,
                    ),
                    speech = request.speechPrompt?.let { InteractionSpeechPresentation(prompt = it) },
                )
                base.toSurface(
                    kind = InteractionSurfaceKind.CONFIRMATION,
                    title = null,
                    message = request.action,
                    content = content,
                    actions = listOf(
                        InteractionAction(InteractionActionId("approve"), "Approve", InteractionActionSemantic.CONFIRM),
                        InteractionAction(InteractionActionId("deny"), "Deny", InteractionActionSemantic.DENY),
                        InteractionAction(InteractionActionId("dismiss"), "Not now", InteractionActionSemantic.DISMISS),
                    ),
                    presentation = InteractionPresentationPolicy(
                        urgency = if (riskLevel == InteractionRiskLevel.DESTRUCTIVE) InteractionUrgency.HIGH else InteractionUrgency.NORMAL,
                        preferredModalities = setOf(InteractionModality.VISUAL, InteractionModality.VOICE),
                        requiresForegroundAttention = riskLevel == InteractionRiskLevel.DESTRUCTIVE,
                    ),
                )
            }
            is AgentInputRequest.Clarification -> when (val expected = request.expected) {
                is ExpectedInput.Confirmation -> base.toSurface(
                    kind = InteractionSurfaceKind.CONFIRMATION,
                    title = null,
                    message = request.question,
                    content = InteractionContent.Confirmation(
                        subject = request.question,
                        speech = request.speechPrompt?.let { InteractionSpeechPresentation(prompt = it) },
                    ),
                    actions = listOf(
                        InteractionAction(InteractionActionId("confirm"), "Yes", InteractionActionSemantic.CONFIRM),
                        InteractionAction(InteractionActionId("deny"), "No", InteractionActionSemantic.DENY),
                        InteractionAction(InteractionActionId("dismiss"), "Not now", InteractionActionSemantic.DISMISS),
                    ),
                )
                is ExpectedInput.Choice -> base.toSurface(
                    kind = InteractionSurfaceKind.CHOICE,
                    title = null,
                    message = request.question,
                    content = InteractionContent.Choice(
                        prompt = request.question,
                        options = expected.options.map { InteractionChoice(it.id, it.label, it.description) },
                        allowMultiple = expected.allowMultiple,
                        speech = request.speechPrompt?.let { InteractionSpeechPresentation(prompt = it) },
                    ),
                    actions = listOf(
                        InteractionAction(InteractionActionId("submit"), "Done", InteractionActionSemantic.SUBMIT),
                        InteractionAction(InteractionActionId("cancel"), "Cancel", InteractionActionSemantic.CANCEL),
                    ),
                )
                is ExpectedInput.Text -> base.toSurface(
                    kind = InteractionSurfaceKind.TEXT_INPUT,
                    title = null,
                    message = request.question,
                    content = InteractionContent.TextInput(
                        prompt = request.question,
                        placeholder = expected.placeholder,
                        initialValue = expected.initialValue,
                        constraints = com.halo.runtime.cognition.interaction.TextInputConstraints(
                            minLength = expected.minLength,
                            maxLength = expected.maxLength,
                            singleLine = expected.singleLine,
                            sensitive = expected.sensitive,
                        ),
                        speech = request.speechPrompt?.let { InteractionSpeechPresentation(prompt = it) },
                    ),
                    actions = listOf(
                        InteractionAction(InteractionActionId("submit"), "Submit", InteractionActionSemantic.SUBMIT),
                        InteractionAction(InteractionActionId("cancel"), "Cancel", InteractionActionSemantic.CANCEL),
                    ),
                )
                is ExpectedInput.Temporal -> base.toSurface(
                    kind = InteractionSurfaceKind.TEMPORAL_INPUT,
                    title = null,
                    message = request.question,
                    content = InteractionContent.TemporalInput(
                        prompt = request.question,
                        mode = expected.mode,
                        speech = request.speechPrompt?.let { InteractionSpeechPresentation(prompt = it) },
                    ),
                    actions = listOf(
                        InteractionAction(InteractionActionId("submit"), "Set", InteractionActionSemantic.SUBMIT),
                        InteractionAction(InteractionActionId("cancel"), "Cancel", InteractionActionSemantic.CANCEL),
                    ),
                )
                is ExpectedInput.Files -> base.toSurface(
                    kind = InteractionSurfaceKind.FILE_SELECTION,
                    title = null,
                    message = request.question,
                    content = InteractionContent.FileSelection(
                        prompt = request.question,
                        acceptedKinds = expected.acceptedKinds.map { kind ->
                            com.halo.runtime.cognition.interaction.FileKind.valueOf(kind.name)
                        }.toSet(),
                        allowMultiple = expected.allowMultiple,
                    ),
                    actions = listOf(
                        InteractionAction(InteractionActionId("submit"), "Choose", InteractionActionSemantic.SUBMIT),
                        InteractionAction(InteractionActionId("cancel"), "Cancel", InteractionActionSemantic.CANCEL),
                    ),
                    presentation = InteractionPresentationPolicy(
                        preferredModalities = setOf(InteractionModality.VISUAL),
                    ),
                )
            }
        }
    }

    private data class SurfaceParts(
        val id: InteractionSurfaceId,
        val runId: com.halo.runtime.cognition.agent.AgentRunId,
        val interactionId: com.halo.cognition.language.InteractionId,
        val inputRequestId: com.halo.runtime.cognition.agent.AgentInputRequestId,
        val createdAt: Instant,
        val expiresAt: Instant?,
        val provenance: InteractionSurfaceProvenance,
    ) {
        fun toSurface(
            kind: InteractionSurfaceKind,
            title: String?,
            message: String?,
            content: InteractionContent,
            actions: List<InteractionAction>,
            presentation: InteractionPresentationPolicy = InteractionPresentationPolicy(),
        ): InteractionSurface = InteractionSurface(
            id = id,
            runId = runId,
            interactionId = interactionId,
            inputRequestId = inputRequestId,
            kind = kind,
            title = title,
            message = message,
            content = content,
            actions = actions,
            presentation = presentation,
            createdAt = createdAt,
            expiresAt = expiresAt,
            provenance = provenance,
        )
    }
}

/**
 * RUNTIME-019 surface projection store. In-memory ONLY: the durable journal
 * ([com.halo.runtime.cognition.agent.AgentRunStore]) remains the single
 * source of truth, and the coordinator rebuilds this projection from it after
 * process death. Hosts observe [active] and respond through
 * [InteractionResponseHandler].
 */
interface InteractionSurfaceStore {
    val active: StateFlow<List<InteractionSurface>>

    suspend fun publish(surface: InteractionSurface)
    suspend fun update(surface: InteractionSurface)
    suspend fun dismiss(id: InteractionSurfaceId)
    suspend fun get(id: InteractionSurfaceId): InteractionSurface?
}

class InMemoryInteractionSurfaceStore : InteractionSurfaceStore {

    private val mutex = Mutex()
    private val surfaces = MutableStateFlow<List<InteractionSurface>>(emptyList())

    override val active: StateFlow<List<InteractionSurface>> = surfaces.asStateFlow()

    override suspend fun publish(surface: InteractionSurface) {
        mutex.withLock {
            if (surfaces.value.any { it.id == surface.id }) return
            surfaces.value = surfaces.value + surface
        }
    }

    override suspend fun update(surface: InteractionSurface) {
        mutex.withLock {
            if (surfaces.value.none { it.id == surface.id }) return
            surfaces.value = surfaces.value.map { if (it.id == surface.id) surface else it }
        }
    }

    override suspend fun dismiss(id: InteractionSurfaceId) {
        mutex.withLock {
            surfaces.value = surfaces.value.filterNot { it.id == id }
        }
    }

    override suspend fun get(id: InteractionSurfaceId): InteractionSurface? =
        surfaces.value.firstOrNull { it.id == id }
}
