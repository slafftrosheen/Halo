package com.halo.app.audio

import com.halo.sdk.runtime.AudioDeviceKind
import com.halo.sdk.runtime.InteractionAudioAffinity
import com.halo.sdk.runtime.InteractionAudioRouteState
import com.halo.sdk.runtime.SpeechInputRoute
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.SpeechRouteEvidence
import com.halo.sdk.runtime.resource.HaloResourceIds
import com.halo.sdk.runtime.resource.ResourceAccess
import com.halo.sdk.runtime.resource.ResourceAcquireResult
import com.halo.sdk.runtime.resource.ResourceClaim
import com.halo.sdk.runtime.resource.ResourceEndpointId
import com.halo.sdk.runtime.resource.ResourceHolder
import com.halo.sdk.runtime.resource.ResourceHolderKind
import com.halo.sdk.runtime.resource.ResourceKey
import com.halo.sdk.runtime.resource.ResourceLease
import com.halo.sdk.runtime.resource.ResourceLeaseManager
import com.halo.sdk.runtime.resource.ResourceLeaseRequest
import com.halo.sdk.runtime.resource.ResourcePreemptionPolicy
import com.halo.sdk.runtime.resource.ResourcePriority
import com.halo.sdk.runtime.resource.ResourceWaitPolicy
import com.halo.sdk.runtime.resource.ResourceYieldDecision
import com.halo.sdk.runtime.resource.ResourceYieldHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AudioSessionLease {
    val interactionId: String
    val deviceId: String?
    val acquiredAtMs: Long
    fun release()
}

class InputLease internal constructor(
    override val interactionId: String,
    override val deviceId: String?,
    override val acquiredAtMs: Long,
    private val underlyingLease: ResourceLease?,
    private val releaseAction: (InputLease) -> Unit,
) : AudioSessionLease {
    @Volatile private var released = false

    override fun release() {
        if (!released) synchronized(this) {
            if (!released) {
                released = true
                underlyingLease?.release()
                releaseAction(this)
            }
        }
    }
}

class OutputLease internal constructor(
    override val interactionId: String,
    override val deviceId: String?,
    override val acquiredAtMs: Long,
    private val underlyingLease: ResourceLease?,
    private val releaseAction: (OutputLease) -> Unit,
) : AudioSessionLease {
    @Volatile private var released = false

    override fun release() {
        if (!released) synchronized(this) {
            if (!released) {
                released = true
                underlyingLease?.release()
                releaseAction(this)
            }
        }
    }
}

sealed interface HaloAudioSessionState {
    data object Idle : HaloAudioSessionState
    data class Listening(val source: SpeechInputRoute, val captureId: String) : HaloAudioSessionState
    data class Thinking(val source: SpeechInputRoute, val captureId: String) : HaloAudioSessionState
    data class PreparingOutput(val target: SpeechOutputTarget, val transactionId: String) : HaloAudioSessionState
    data class Speaking(val target: SpeechOutputTarget, val transactionId: String, val route: SpeechRouteEvidence) : HaloAudioSessionState
    data class Failed(val transactionId: String?, val reason: HaloAudioFailure) : HaloAudioSessionState
}

enum class HaloAudioFailure { ROUTE_UNAVAILABLE, FOCUS_DENIED, PLAYBACK_FAILED, ROUTE_LOST }

data class SpeechRouteLease(
    val target: SpeechOutputTarget,
    val endpoint: SpeechRouteEvidence,
    val acquiredAt: Long,
    val expiresAt: Long,
) {
    fun isValid(now: Long = System.currentTimeMillis()): Boolean = now in acquiredAt until expiresAt
}

data class PlaybackGuardWindow(
    val startedAt: Long,
    val endedAt: Long?,
    val target: SpeechOutputTarget,
) {
    fun contains(captureAt: Long, postRollMs: Long = 550L): Boolean =
        captureAt >= startedAt && captureAt <= (endedAt ?: captureAt) + postRollMs
}

/**
 * Audio interaction state plus leases from the one process-wide Runtime resource
 * fabric. Platform route setup/evidence remains outside this coordinator.
 */
class HaloAudioSessionCoordinator(
    private val resourceLeaseManager: ResourceLeaseManager,
) {
    private val mutableState = MutableStateFlow<HaloAudioSessionState>(HaloAudioSessionState.Idle)
    val state: StateFlow<HaloAudioSessionState> = mutableState.asStateFlow()

    @Volatile private var inputOwner: InputLease? = null
    @Volatile private var outputOwner: OutputLease? = null

    /**
     * MINING-P0-001 the most recent (or active) interaction's audio affinity.
     *
     * This outlives the microphone capture: `finishInput()` ends the capture-scoped listening
     * state but deliberately never clears this binding. Only the interaction finishing, being
     * cancelled, an explicit policy handoff, or teardown ends it.
     */
    private val _affinity = MutableStateFlow<InteractionAudioAffinity?>(null)
    val affinity: StateFlow<InteractionAudioAffinity?> = _affinity.asStateFlow()

    @Volatile var playbackGuard: PlaybackGuardWindow? = null
        private set

    /**
     * Interactive input may cooperatively preempt lower-priority output. The
     * ResourceLeaseManager calls the OUTPUT OWNER'S registered yield handler;
     * input never supplies cleanup code for somebody else's lease.
     */
    suspend fun acquireInput(interactionId: String, deviceId: String?): Result<InputLease> {
        val endpoint = ResourceEndpointId(deviceId ?: "host:primary")
        val request = ResourceLeaseRequest(
            requestId = "audio.input:$interactionId",
            holder = ResourceHolder(
                id = "audio.input:$interactionId",
                interactionId = interactionId,
                kind = ResourceHolderKind.INTERACTION,
            ),
            claims = setOf(
                ResourceClaim(ResourceKey(HaloResourceIds.AudioInput, endpoint), ResourceAccess.EXCLUSIVE),
                ResourceClaim(ResourceKey(HaloResourceIds.AudioSession, ResourceEndpointId("host:android-audio")), ResourceAccess.EXCLUSIVE),
            ),
            priority = ResourcePriority.INTERACTIVE_INPUT,
            waitPolicy = ResourceWaitPolicy.Wait(750L),
            preemption = ResourcePreemptionPolicy.COOPERATIVE,
        )

        return when (val result = resourceLeaseManager.acquire(request)) {
            is ResourceAcquireResult.Granted -> {
                val lease = InputLease(
                    interactionId = interactionId,
                    deviceId = deviceId,
                    acquiredAtMs = result.lease.acquiredAtMs,
                    underlyingLease = result.lease,
                    releaseAction = ::releaseInput,
                )
                inputOwner = lease
                Result.success(lease)
            }
            else -> Result.failure(IllegalStateException("Audio input resource busy: $result"))
        }
    }

    suspend fun acquireOutput(
        interactionId: String,
        deviceId: String?,
        ownerYieldHandler: ResourceYieldHandler? = null,
    ): Result<OutputLease> {
        val endpoint = ResourceEndpointId(deviceId ?: "host:primary")
        val request = ResourceLeaseRequest(
            requestId = "audio.output:$interactionId",
            holder = ResourceHolder(
                id = "audio.output:$interactionId",
                interactionId = interactionId,
                kind = ResourceHolderKind.INTERACTION,
            ),
            claims = setOf(
                ResourceClaim(ResourceKey(HaloResourceIds.AudioOutput, endpoint), ResourceAccess.EXCLUSIVE),
                ResourceClaim(ResourceKey(HaloResourceIds.AudioSession, ResourceEndpointId("host:android-audio")), ResourceAccess.EXCLUSIVE),
            ),
            priority = ResourcePriority.INTERACTIVE,
            waitPolicy = ResourceWaitPolicy.FailFast,
            preemption = ResourcePreemptionPolicy.COOPERATIVE,
        )

        return when (val result = resourceLeaseManager.acquire(request, ownerYieldHandler)) {
            is ResourceAcquireResult.Granted -> {
                val lease = OutputLease(
                    interactionId = interactionId,
                    deviceId = deviceId,
                    acquiredAtMs = result.lease.acquiredAtMs,
                    underlyingLease = result.lease,
                    releaseAction = ::releaseOutput,
                )
                outputOwner = lease
                Result.success(lease)
            }
            else -> Result.failure(IllegalStateException("Audio output resource busy: $result"))
        }
    }

    fun activeInputLease(): InputLease? = inputOwner
    fun activeOutputLease(): OutputLease? = outputOwner

    fun listening(source: SpeechInputRoute, captureId: String) {
        mutableState.value = HaloAudioSessionState.Listening(source, captureId)
    }

    fun thinking(source: SpeechInputRoute, captureId: String) {
        mutableState.value = HaloAudioSessionState.Thinking(source, captureId)
    }

    fun preparing(target: SpeechOutputTarget, transactionId: String) {
        mutableState.value = HaloAudioSessionState.PreparingOutput(target, transactionId)
    }

    fun speaking(target: SpeechOutputTarget, transactionId: String, route: SpeechRouteEvidence) {
        if ((mutableState.value as? HaloAudioSessionState.PreparingOutput)?.transactionId != transactionId) return
        playbackGuard = PlaybackGuardWindow(System.currentTimeMillis(), null, target)
        mutableState.value = HaloAudioSessionState.Speaking(target, transactionId, route)
    }

    fun failed(transactionId: String?, reason: HaloAudioFailure) {
        if (transactionId != null && transactionId != currentTransactionId()) return
        finishGuard()
        transactionId?.let { id ->
            when (reason) {
                HaloAudioFailure.ROUTE_UNAVAILABLE -> transitionRoute(id, InteractionAudioRouteState.UNAVAILABLE)
                HaloAudioFailure.ROUTE_LOST -> transitionRoute(id, InteractionAudioRouteState.LOST)
                else -> Unit
            }
        }
        mutableState.value = HaloAudioSessionState.Failed(transactionId, reason)
    }

    fun idle(transactionId: String? = null) {
        if (transactionId != null && currentTransactionId() != transactionId) return
        finishGuard()
        // The interaction is finished: retain the record as "most recent" for diagnostics, but the
        // route is no longer authoritative for a new interaction.
        endCurrentInteraction()
        mutableState.value = HaloAudioSessionState.Idle
    }

    fun finishInput(captureId: String) {
        val current = mutableState.value
        val ownedCapture = when (current) {
            is HaloAudioSessionState.Listening -> current.captureId
            is HaloAudioSessionState.Thinking -> current.captureId
            else -> null
        }
        if (ownedCapture == captureId) mutableState.compareAndSet(current, HaloAudioSessionState.Idle)
        // Interaction audio affinity intentionally survives capture end. Speech recognition,
        // cognition, provider load, inference, planning, tool execution and TTS preparation all
        // happen after the microphone lease is released and must keep the same output target.
    }

    // -------------------------------------------------------------------------------------
    // Interaction audio affinity (MINING-P0-001)
    // -------------------------------------------------------------------------------------

    /**
     * Binds route affinity for an interaction whose input/origin is now established.
     *
     * A wearable-origin input keeps the originating wearable as the intended output with phone
     * fallback disabled, matching Halo's existing HeyCyan continuity policy. A phone-origin input
     * keeps Android's own communication-route selection for output.
     */
    fun bindInteraction(
        interactionId: String,
        inputRoute: SpeechInputRoute,
        intendedTarget: SpeechOutputTarget = defaultTargetFor(inputRoute),
        fallback: SpeechOutputFallback = defaultFallbackFor(inputRoute),
        originDeviceId: String? = (inputRoute as? SpeechInputRoute.Device)?.deviceId,
        originKind: AudioDeviceKind? = (inputRoute as? SpeechInputRoute.Device)?.kind,
    ) {
        if (interactionId.isBlank()) return
        val now = System.currentTimeMillis()
        val existing = _affinity.value
        _affinity.value = InteractionAudioAffinity(
            interactionId = interactionId,
            originDeviceId = originDeviceId,
            originKind = originKind,
            inputRoute = inputRoute,
            intendedTarget = intendedTarget,
            fallback = fallback,
            resolvedEndpointLabel = existing?.takeIf { it.interactionId == interactionId }?.resolvedEndpointLabel,
            routeState = InteractionAudioRouteState.PENDING,
            boundAtMs = existing?.takeIf { it.interactionId == interactionId }?.boundAtMs ?: now,
            lastTransitionAtMs = now,
        )
    }

    /** Explicit delivery change preserves historical input and clears unverified route evidence. */
    @Synchronized fun handoffInteraction(interactionId: String, target: SpeechOutputTarget, fallback: SpeechOutputFallback): Boolean {
        val current = _affinity.value?.takeIf { it.interactionId == interactionId } ?: return false
        finishGuard()
        mutableState.value = HaloAudioSessionState.Idle
        _affinity.value = current.copy(intendedTarget = target, fallback = fallback, resolvedEndpointLabel = null,
            routeState = InteractionAudioRouteState.PENDING, lastTransitionAtMs = System.currentTimeMillis())
        return true
    }

    /** Records the outcome of route preparation for the active interaction. */
    fun transitionRoute(
        interactionId: String,
        state: InteractionAudioRouteState,
        endpointLabel: String? = null,
        intendedTarget: SpeechOutputTarget? = null,
        fallback: SpeechOutputFallback? = null,
    ) {
        val current = currentAffinityFor(interactionId) ?: return
        _affinity.value = current.copy(
            intendedTarget = intendedTarget ?: current.intendedTarget,
            fallback = fallback ?: current.fallback,
            resolvedEndpointLabel = endpointLabel ?: current.resolvedEndpointLabel,
            routeState = state,
            lastTransitionAtMs = System.currentTimeMillis(),
        )
    }

    /** Ends affinity because the interaction finished, failed terminally, or was cancelled. */
    fun endInteraction(interactionId: String) {
        transitionRoute(interactionId, InteractionAudioRouteState.ENDED)
    }

    /** Ends the currently-tracked affinity, whatever id it was bound under. */
    fun endCurrentInteraction() {
        _affinity.value?.let { endInteraction(it.interactionId) }
    }

    /**
     * Exactly one audio interaction is active at a time, but the capture id bound at listening
     * time differs from the TTS transaction id that later finishes the turn. Adopt the transition
     * for the live record instead of silently dropping it.
     */
    private fun currentAffinityFor(interactionId: String?): InteractionAudioAffinity? {
        val current = _affinity.value ?: return null
        if (interactionId == null || current.interactionId == interactionId) return current
        return current.takeIf { it.routeState != InteractionAudioRouteState.ENDED }
    }

    private fun defaultTargetFor(route: SpeechInputRoute): SpeechOutputTarget = when (route) {
        is SpeechInputRoute.Phone -> SpeechOutputTarget.HostDefault
        is SpeechInputRoute.Device -> SpeechOutputTarget.Device(
            deviceId = route.deviceId,
            platformEndpointId = route.platformEndpointId,
            displayName = route.displayName,
            kind = route.kind,
        )
    }

    private fun defaultFallbackFor(route: SpeechInputRoute): SpeechOutputFallback =
        if (route is SpeechInputRoute.Device) SpeechOutputFallback.DISALLOW_PHONE
        else SpeechOutputFallback.ALLOW_PHONE

    private fun finishGuard() {
        playbackGuard = playbackGuard?.copy(endedAt = System.currentTimeMillis())
    }

    private fun currentTransactionId(): String? = when (val current = mutableState.value) {
        is HaloAudioSessionState.PreparingOutput -> current.transactionId
        is HaloAudioSessionState.Speaking -> current.transactionId
        else -> null
    }

    private fun releaseInput(lease: InputLease) {
        if (inputOwner === lease) inputOwner = null
    }

    private fun releaseOutput(lease: OutputLease) {
        if (outputOwner === lease) outputOwner = null
    }
}
