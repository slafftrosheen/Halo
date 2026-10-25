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
    data class ManagedDevice(val deviceId: String, val displayName: String? = null) : InteractionEndpoint
    data class AudioAccessory(val accessoryId: String, val displayName: String? = null,
        val audioKind: AudioDeviceKind) : InteractionEndpoint
    data class WearOs(val nodeId: String, val displayName: String? = null) : InteractionEndpoint
}
enum class InteractionHandoffChannel { AUDIO_OUTPUT, PRESENTATION, INPUT_PREFERENCE }
data class InteractionHandoffRequest(val interactionId: String, val presentationEndpoint: InteractionEndpoint? = null,
    val audioOutputEndpoint: InteractionEndpoint? = null)
enum class InteractionHandoffReason { USER_REQUESTED, INPUT_MOVED }
enum class InteractionHandoffStatus { IDLE, APPLIED, UNAVAILABLE, NO_ACTIVE_INTERACTION }
enum class EndpointAvailability { READY, CONNECTED, AVAILABLE, UNAVAILABLE }
enum class EndpointPersistence { DURABLE, SESSION_ONLY }
data class InteractionEndpointCapabilities(val audioInput: Boolean = false, val audioOutput: Boolean = false,
    val visualOutput: Boolean = false, val controls: Boolean = false, val haptics: Boolean = false, val camera: Boolean = false)
data class InteractionEndpointState(val endpoint: InteractionEndpoint, val ready: Boolean, val label: String,
    val capabilities: InteractionEndpointCapabilities = InteractionEndpointCapabilities(),
    val persistence: EndpointPersistence = EndpointPersistence.DURABLE,
    val availability: EndpointAvailability = if (ready) EndpointAvailability.READY else EndpointAvailability.UNAVAILABLE)
data class InteractionContinuityState(
    val conversationId: String? = null, val interactionId: String? = null, val runId: AgentRunId? = null,
    val origin: InteractionEndpoint? = null, val current: InteractionEndpoint? = null,
    val status: InteractionHandoffStatus = InteractionHandoffStatus.IDLE,
    val executing: Boolean = false,
    val inputEndpoint: InteractionEndpoint? = origin,
    val presentationEndpoint: InteractionEndpoint? = InteractionEndpoint.Phone,
    val audioOutputEndpoint: InteractionEndpoint? = current,

)
data class ContinuityDelivery(val target: SpeechOutputTarget, val fallback: SpeechOutputFallback,
    val delivery: ResponseDelivery, val presentationEndpoint: InteractionEndpoint = InteractionEndpoint.Phone)

/** Pure journal projection. Connections and UI navigation have no authority here. */
object RunContinuityResolver {
    /** Input provenance is independent of current output handoff/delivery. */
    fun inputModality(run: AgentRun): com.halo.runtime.cognition.turn.InteractionInputModality =
        run.steps.filterIsInstance<AgentStep.RunPrepared>().firstOrNull()?.inputModality
            ?: when (run.origin?.source) {
                ObservationSource.AndroidPhoneMic, ObservationSource.HeyCyanGlasses -> com.halo.runtime.cognition.turn.InteractionInputModality.VOICE
                else -> com.halo.runtime.cognition.turn.InteractionInputModality.UNKNOWN
            }

    fun inputRoute(run: AgentRun): SpeechInputRoute? {
        if (inputModality(run) != com.halo.runtime.cognition.turn.InteractionInputModality.VOICE) return null
        run.steps.filterIsInstance<AgentStep.RunPrepared>().firstOrNull()?.inputRoute?.let { return it }
        return when (run.origin?.source) {
            ObservationSource.AndroidPhoneMic -> SpeechInputRoute.Phone
            ObservationSource.HeyCyanGlasses -> run.origin?.hostId?.let {
                SpeechInputRoute.Device(it, null, null, AudioDeviceKind.WEARABLE)
            }
            else -> null
        }
    }

    fun conversationId(run: AgentRun): String? =
        run.steps.filterIsInstance<AgentStep.ContinuityBound>().lastOrNull()?.conversationId
            ?: run.steps.filterIsInstance<AgentStep.RunPrepared>().firstOrNull()?.conversationId
    fun endpoint(run: AgentRun): InteractionEndpoint =
        run.steps.filterIsInstance<AgentStep.InteractionHandoffRecorded>().lastOrNull { it.channel == InteractionHandoffChannel.AUDIO_OUTPUT }?.to
            ?: endpoint(run.speechOutputTarget, run.origin)
    fun endpoint(target: SpeechOutputTarget?, origin: ObservationOrigin? = null): InteractionEndpoint = when (target) {
        is SpeechOutputTarget.Device -> target.deviceId?.let {
            if (target.kind == AudioDeviceKind.WEARABLE) InteractionEndpoint.ManagedDevice(it, target.displayName)
            else InteractionEndpoint.AudioAccessory(it, target.displayName, target.kind)
        } ?: InteractionEndpoint.Phone
        else -> if (origin?.source == ObservationSource.AndroidAssist) InteractionEndpoint.AndroidAssistant else InteractionEndpoint.Phone
    }
    fun delivery(run: AgentRun): ContinuityDelivery {
        val handoff = run.steps.filterIsInstance<AgentStep.InteractionHandoffRecorded>().lastOrNull { it.channel == InteractionHandoffChannel.AUDIO_OUTPUT }
        val audio = if (handoff != null) delivery(handoff.to) else ContinuityDelivery(
            run.speechOutputTarget ?: SpeechOutputTarget.Phone,
            run.speechOutputFallback,
            run.steps.filterIsInstance<AgentStep.RunPrepared>().firstOrNull()?.responseDelivery
                ?: if (run.speechOutputTarget is SpeechOutputTarget.Device) ResponseDelivery.SPEECH_AND_VISUAL else ResponseDelivery.VISUAL_ONLY)
        return audio.copy(presentationEndpoint = run.steps.filterIsInstance<AgentStep.InteractionHandoffRecorded>()
            .lastOrNull { it.channel == InteractionHandoffChannel.PRESENTATION }?.to ?: InteractionEndpoint.Phone)
    }
    fun delivery(endpoint: InteractionEndpoint): ContinuityDelivery = when (endpoint) {
        is InteractionEndpoint.AudioAccessory -> ContinuityDelivery(SpeechOutputTarget.Device(endpoint.accessoryId, null,
            endpoint.displayName, endpoint.audioKind), SpeechOutputFallback.DISALLOW_PHONE, ResponseDelivery.SPEECH_AND_VISUAL)
        is InteractionEndpoint.WearOs -> ContinuityDelivery(SpeechOutputTarget.HostDefault, SpeechOutputFallback.ALLOW_PHONE, ResponseDelivery.VISUAL_ONLY)
        is InteractionEndpoint.ManagedDevice -> ContinuityDelivery(SpeechOutputTarget.Device(endpoint.deviceId, platformEndpointId = null,
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
        _state.value = InteractionContinuityState(conversationId, interactionId, origin = endpoint, current = endpoint, executing = true,
            presentationEndpoint = if (endpoint == InteractionEndpoint.AndroidAssistant) endpoint else InteractionEndpoint.Phone)
    }
    suspend fun attach(runId: AgentRunId) = mutex.withLock {
        val run = runs.get(runId) ?: return@withLock
        if (_state.value.interactionId == run.interactionId.value) {
            overrides[run.interactionId.value]?.let { endpoint ->
                coordinator.recordStep(AgentStep.InteractionHandoffRecorded(AgentStepId(UUID.randomUUID().toString()), runId,
                    Instant.now(), _state.value.origin ?: InteractionEndpoint.Phone, endpoint, InteractionHandoffReason.USER_REQUESTED))
            }
            val presentation = _state.value.presentationEndpoint
            if (presentation != null && presentation != InteractionEndpoint.Phone && presentation != _state.value.origin) {
                coordinator.recordStep(AgentStep.InteractionHandoffRecorded(AgentStepId(UUID.randomUUID().toString()), runId,
                    Instant.now(), InteractionEndpoint.Phone, presentation, InteractionHandoffReason.USER_REQUESTED, InteractionHandoffChannel.PRESENTATION))
            }
            _state.value = _state.value.copy(runId = runId)
        }
    }
    suspend fun restore(run: AgentRun) = mutex.withLock {
        _state.value = InteractionContinuityState(RunContinuityResolver.conversationId(run), run.interactionId.value,
            run.id, RunContinuityResolver.endpoint(run.speechOutputTarget, run.origin), RunContinuityResolver.endpoint(run), executing = true,
            presentationEndpoint = run.steps.filterIsInstance<AgentStep.InteractionHandoffRecorded>()
                .lastOrNull { it.channel == InteractionHandoffChannel.PRESENTATION }?.to ?: InteractionEndpoint.Phone)
    }
    suspend fun handoff(interactionId: String, to: InteractionEndpoint,
        reason: InteractionHandoffReason = InteractionHandoffReason.USER_REQUESTED,
        runId: AgentRunId? = null,
        channel: InteractionHandoffChannel = if (to is InteractionEndpoint.WearOs || to is InteractionEndpoint.AndroidAssistant) InteractionHandoffChannel.PRESENTATION else InteractionHandoffChannel.AUDIO_OUTPUT): InteractionHandoffStatus = mutex.withLock {
        if (channel == InteractionHandoffChannel.INPUT_PREFERENCE) return@withLock InteractionHandoffStatus.UNAVAILABLE
        val current = _state.value
        val run = (runId ?: current.runId)?.let { runs.get(it) }
        if (current.interactionId != interactionId || (!current.executing && !playbackActive(interactionId)) ||
            (runId != null && current.runId != runId) || (run?.state?.isTerminal == true && !playbackActive(interactionId))) {
            return@withLock InteractionHandoffStatus.NO_ACTIVE_INTERACTION
        }
        if (!isReady(to)) { _state.value = current.copy(status = InteractionHandoffStatus.UNAVAILABLE); return@withLock InteractionHandoffStatus.UNAVAILABLE }
        if (run != null && !run.state.isTerminal && !coordinator.recordStep(AgentStep.InteractionHandoffRecorded(AgentStepId(UUID.randomUUID().toString()),
                run.id, Instant.now(), (if (channel == InteractionHandoffChannel.PRESENTATION) current.presentationEndpoint else current.audioOutputEndpoint) ?: InteractionEndpoint.Phone, to, reason, channel)))
            return@withLock InteractionHandoffStatus.NO_ACTIVE_INTERACTION
        if (channel == InteractionHandoffChannel.PRESENTATION) {
            _state.value = current.copy(presentationEndpoint = to, status = InteractionHandoffStatus.APPLIED)
        } else {
            overrides[interactionId] = to
            _state.value = current.copy(current = to, audioOutputEndpoint = to, status = InteractionHandoffStatus.APPLIED)
            applyAudioHandoff(interactionId, RunContinuityResolver.delivery(to))
        }
        events?.publish(InteractionHandoffChanged(interactionId, run?.id?.value, endpointKind(current.current), endpointKind(to), "APPLIED"))
        InteractionHandoffStatus.APPLIED
    }
    /** Serialize final route selection with handoff. No later handoff may race this publication. */
    suspend fun finalDelivery(interactionId: String, runId: AgentRunId?, original: ContinuityDelivery): ContinuityDelivery = mutex.withLock {
        val run = runId?.let { runs.get(it) }
        val selected = overrides.remove(interactionId)?.let { RunContinuityResolver.delivery(it) }
            ?: run?.steps?.filterIsInstance<AgentStep.InteractionHandoffRecorded>()?.lastOrNull { it.channel == InteractionHandoffChannel.AUDIO_OUTPUT }?.let { RunContinuityResolver.delivery(it.to) }
            ?: original
        val result = selected.copy(presentationEndpoint =
            run?.steps?.filterIsInstance<AgentStep.InteractionHandoffRecorded>()?.lastOrNull { it.channel == InteractionHandoffChannel.PRESENTATION }?.to
                ?: _state.value.takeIf { it.interactionId == interactionId }?.presentationEndpoint ?: original.presentationEndpoint)
        if (_state.value.interactionId == interactionId) _state.value = _state.value.copy(executing = false)
        result
    }
    suspend fun end(interactionId: String) = mutex.withLock {
        overrides.remove(interactionId)
        if (_state.value.interactionId == interactionId) _state.value = _state.value.copy(executing = false)
    }
}

fun sameEndpoint(a: InteractionEndpoint?, b: InteractionEndpoint?): Boolean = when {
    a is InteractionEndpoint.ManagedDevice && b is InteractionEndpoint.ManagedDevice -> a.deviceId == b.deviceId
    a is InteractionEndpoint.AudioAccessory && b is InteractionEndpoint.AudioAccessory -> a.accessoryId == b.accessoryId
    a is InteractionEndpoint.WearOs && b is InteractionEndpoint.WearOs -> a.nodeId == b.nodeId
    else -> a == b
}
private fun endpointKind(endpoint: InteractionEndpoint?): String = when (endpoint) {
    is InteractionEndpoint.ManagedDevice -> "MANAGED_DEVICE"
    is InteractionEndpoint.AudioAccessory -> "AUDIO_ACCESSORY"
    is InteractionEndpoint.WearOs -> "WEAR_OS"
    InteractionEndpoint.AndroidAssistant -> "ANDROID_ASSISTANT"
    InteractionEndpoint.Phone -> "PHONE"
    null -> "NONE"
}
data class InteractionHandoffChanged(val interactionId: String, val runId: String?, val fromKind: String,
    val toKind: String, val status: String, override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
