package com.halo.app.audio

import com.halo.sdk.runtime.SpeechInputRoute
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
                ResourceClaim(ResourceKey(HaloResourceIds.AudioSession, endpoint), ResourceAccess.EXCLUSIVE),
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

    suspend fun acquireOutput(interactionId: String, deviceId: String?): Result<OutputLease> {
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
                ResourceClaim(ResourceKey(HaloResourceIds.AudioSession, endpoint), ResourceAccess.EXCLUSIVE),
            ),
            priority = ResourcePriority.INTERACTIVE,
            waitPolicy = ResourceWaitPolicy.FailFast,
            preemption = ResourcePreemptionPolicy.COOPERATIVE,
        )

        val ownerYieldHandler = ResourceYieldHandler {
            val current = outputOwner
            if (current != null && current.interactionId == interactionId) {
                // Actual playback owner observes the released wrapper and its
                // normal stop path performs platform cleanup/route restoration.
                current.release()
                ResourceYieldDecision.RELEASING
            } else {
                ResourceYieldDecision.REFUSED
            }
        }

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
        mutableState.value = HaloAudioSessionState.Failed(transactionId, reason)
    }

    fun idle(transactionId: String? = null) {
        if (transactionId != null && currentTransactionId() != transactionId) return
        finishGuard()
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
    }

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
