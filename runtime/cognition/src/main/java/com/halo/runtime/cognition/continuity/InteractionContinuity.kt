package com.halo.runtime.cognition.continuity

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.sdk.host.ObservationSource
import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

sealed interface InteractionEndpoint {
    data object Phone : InteractionEndpoint
    data object AndroidAssistant : InteractionEndpoint
    data class Wearable(val deviceId: String, val displayName: String? = null) : InteractionEndpoint
}
enum class InteractionHandoffReason { USER_REQUESTED, INPUT_MOVED }
enum class InteractionHandoffStatus { IDLE, APPLIED, UNAVAILABLE, NO_ACTIVE_INTERACTION }
data class InteractionEndpointState(val endpoint: InteractionEndpoint, val ready: Boolean, val label: String)
data class InteractionContinuityState(
    val conversationId: String? = null, val interactionId: String? = null, val runId: AgentRunId? = null,
    val origin: InteractionEndpoint? = null, val current: InteractionEndpoint? = null,
    val status: InteractionHandoffStatus = InteractionHandoffStatus.IDLE,
    val executing: Boolean = false,
)
data class ContinuityDelivery(val target: SpeechOutputTarget, val fallback: SpeechOutputFallback,
    val delivery: ResponseDelivery)

/** Pure journal projection. Connections and UI navigation have no authority here. */
object RunContinuityResolver {
    fun conversationId(run: AgentRun): String? =
        run.steps.filterIsInstance<AgentStep.ContinuityBound>().lastOrNull()?.conversationId
            ?: run.steps.filterIsInstance<AgentStep.RunPrepared>().firstOrNull()?.conversationId
    fun endpoint(run: AgentRun): InteractionEndpoint =
        run.steps.filterIsInstance<AgentStep.InteractionHandoffRecorded>().lastOrNull()?.to
            ?: endpoint(run.speechOutputTarget, run.origin)
    fun endpoint(target: SpeechOutputTarget?, origin: ObservationOrigin? = null): InteractionEndpoint = when (target) {
        is SpeechOutputTarget.Device -> target.deviceId?.let { InteractionEndpoint.Wearable(it, target.displayName) } ?: InteractionEndpoint.Phone
        else -> if (origin?.source == ObservationSource.AndroidAssist) InteractionEndpoint.AndroidAssistant else InteractionEndpoint.Phone
    }
    fun delivery(run: AgentRun): ContinuityDelivery {
        val handoff = run.steps.filterIsInstance<AgentStep.InteractionHandoffRecorded>().lastOrNull()
        return if (handoff != null) delivery(handoff.to) else ContinuityDelivery(
            run.speechOutputTarget ?: SpeechOutputTarget.Phone,
            run.speechOutputFallback,
            run.steps.filterIsInstance<AgentStep.RunPrepared>().firstOrNull()?.responseDelivery
                ?: if (run.speechOutputTarget is SpeechOutputTarget.Device) ResponseDelivery.SPEECH_AND_VISUAL else ResponseDelivery.VISUAL_ONLY)
    }
    fun delivery(endpoint: InteractionEndpoint): ContinuityDelivery = when (endpoint) {
        is InteractionEndpoint.Wearable -> ContinuityDelivery(SpeechOutputTarget.Device(endpoint.deviceId, platformEndpointId = null,
            displayName = endpoint.displayName, kind = AudioDeviceKind.WEARABLE), SpeechOutputFallback.DISALLOW_PHONE, ResponseDelivery.SPEECH_AND_VISUAL)
        else -> ContinuityDelivery(SpeechOutputTarget.Phone, SpeechOutputFallback.ALLOW_PHONE, ResponseDelivery.SPEECH_AND_VISUAL)
    }
}

/** Delivery authority only: never starts inference, tools, connections, or a new run. */
class InteractionContinuityCoordinator(
    private val runs: AgentRunStore,
    private val coordinator: AgentRunCoordinator,
    private val isReady: suspend (InteractionEndpoint) -> Boolean,
    private val playbackActive: (String) -> Boolean = { false },
    private val events: EventBus? = null,
    private val applyAudioHandoff: suspend (String, ContinuityDelivery) -> Unit = { _, _ -> },
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(InteractionContinuityState())
    val state = _state.asStateFlow()
    private val overrides = mutableMapOf<String, InteractionEndpoint>()
    suspend fun bind(conversationId: String?, interactionId: String, endpoint: InteractionEndpoint) = mutex.withLock {
        _state.value = InteractionContinuityState(conversationId, interactionId, origin = endpoint, current = endpoint, executing = true)
    }
    suspend fun attach(runId: AgentRunId) = mutex.withLock {
        val run = runs.get(runId) ?: return@withLock
        if (_state.value.interactionId == run.interactionId.value) {
            overrides[run.interactionId.value]?.let { endpoint ->
                coordinator.recordStep(AgentStep.InteractionHandoffRecorded(AgentStepId(UUID.randomUUID().toString()), runId,
                    Instant.now(), _state.value.origin ?: InteractionEndpoint.Phone, endpoint, InteractionHandoffReason.USER_REQUESTED))
            }
            _state.value = _state.value.copy(runId = runId)
        }
    }
    suspend fun restore(run: AgentRun) = mutex.withLock {
        _state.value = InteractionContinuityState(RunContinuityResolver.conversationId(run), run.interactionId.value,
            run.id, RunContinuityResolver.endpoint(run.speechOutputTarget, run.origin), RunContinuityResolver.endpoint(run), executing = true)
    }
    suspend fun handoff(interactionId: String, to: InteractionEndpoint,
        reason: InteractionHandoffReason = InteractionHandoffReason.USER_REQUESTED,
        runId: AgentRunId? = null): InteractionHandoffStatus = mutex.withLock {
        val current = _state.value
        val run = (runId ?: current.runId)?.let { runs.get(it) }
        if (current.interactionId != interactionId || (!current.executing && !playbackActive(interactionId)) ||
            (runId != null && current.runId != runId) || (run?.state?.isTerminal == true && !playbackActive(interactionId))) {
            return@withLock InteractionHandoffStatus.NO_ACTIVE_INTERACTION
        }
        if (!isReady(to)) { _state.value = current.copy(status = InteractionHandoffStatus.UNAVAILABLE); return@withLock InteractionHandoffStatus.UNAVAILABLE }
        if (run != null && !run.state.isTerminal && !coordinator.recordStep(AgentStep.InteractionHandoffRecorded(AgentStepId(UUID.randomUUID().toString()),
                run.id, Instant.now(), current.current ?: InteractionEndpoint.Phone, to, reason)))
            return@withLock InteractionHandoffStatus.NO_ACTIVE_INTERACTION
        overrides[interactionId] = to
        _state.value = current.copy(current = to, status = InteractionHandoffStatus.APPLIED)
        applyAudioHandoff(interactionId, RunContinuityResolver.delivery(to))
        events?.publish(InteractionHandoffChanged(interactionId, run?.id?.value, endpointKind(current.current), endpointKind(to), "APPLIED"))
        InteractionHandoffStatus.APPLIED
    }
    /** Serialize final route selection with handoff. No later handoff may race this publication. */
    suspend fun finalDelivery(interactionId: String, runId: AgentRunId?, original: ContinuityDelivery): ContinuityDelivery = mutex.withLock {
        val run = runId?.let { runs.get(it) }
        val result = overrides.remove(interactionId)?.let { RunContinuityResolver.delivery(it) }
            ?: run?.steps?.filterIsInstance<AgentStep.InteractionHandoffRecorded>()?.lastOrNull()?.let { RunContinuityResolver.delivery(it.to) }
            ?: original
        if (_state.value.interactionId == interactionId) _state.value = _state.value.copy(executing = false)
        result
    }
    suspend fun end(interactionId: String) = mutex.withLock {
        overrides.remove(interactionId)
        if (_state.value.interactionId == interactionId) _state.value = _state.value.copy(executing = false)
    }
}

fun sameEndpoint(a: InteractionEndpoint?, b: InteractionEndpoint?): Boolean = when {
    a is InteractionEndpoint.Wearable && b is InteractionEndpoint.Wearable -> a.deviceId == b.deviceId
    else -> a == b
}
private fun endpointKind(endpoint: InteractionEndpoint?): String = when (endpoint) {
    is InteractionEndpoint.Wearable -> "WEARABLE"
    InteractionEndpoint.AndroidAssistant -> "ANDROID_ASSISTANT"
    InteractionEndpoint.Phone -> "PHONE"
    null -> "NONE"
}
data class InteractionHandoffChanged(val interactionId: String, val runId: String?, val fromKind: String,
    val toKind: String, val status: String, override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
