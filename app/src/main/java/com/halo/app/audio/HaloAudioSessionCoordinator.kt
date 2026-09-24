package com.halo.app.audio

import com.halo.sdk.runtime.SpeechInputRoute
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.SpeechRouteEvidence
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
    private val releaseAction: (InputLease) -> Unit
) : AudioSessionLease {
    @Volatile private var released = false
    override fun release() {
        if (!released) synchronized(this) {
            if (!released) {
                released = true
                releaseAction(this)
            }
        }
    }
}

class OutputLease internal constructor(
    override val interactionId: String,
    override val deviceId: String?,
    override val acquiredAtMs: Long,
    private val releaseAction: (OutputLease) -> Unit
) : AudioSessionLease {
    @Volatile private var released = false
    override fun release() {
        if (!released) synchronized(this) {
            if (!released) {
                released = true
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
    val expiresAt: Long
) {
    fun isValid(now: Long = System.currentTimeMillis()): Boolean = now in acquiredAt until expiresAt
}

data class PlaybackGuardWindow(
    val startedAt: Long,
    val endedAt: Long?,
    val target: SpeechOutputTarget
) {
    fun contains(captureAt: Long, postRollMs: Long = 550L): Boolean =
        captureAt >= startedAt && captureAt <= (endedAt ?: captureAt) + postRollMs
}

/** Session state shared by input, route preparation and output. The route controller owns BLE work. */
class HaloAudioSessionCoordinator {
    private val mutableState = MutableStateFlow<HaloAudioSessionState>(HaloAudioSessionState.Idle)
    val state: StateFlow<HaloAudioSessionState> = mutableState.asStateFlow()
    private val ownershipLock = Any()
    @Volatile private var inputOwner: InputLease? = null
    @Volatile private var outputOwner: OutputLease? = null

    @Volatile var playbackGuard: PlaybackGuardWindow? = null
        private set

    fun acquireInput(interactionId: String, deviceId: String?): Result<InputLease> = synchronized(ownershipLock) {
        if (inputOwner != null || outputOwner != null) {
            return@synchronized Result.failure(IllegalStateException("Audio input is already owned"))
        }
        Result.success(InputLease(interactionId, deviceId, System.currentTimeMillis(), ::releaseInput).also {
            inputOwner = it
        })
    }

    fun acquireOutput(interactionId: String, deviceId: String?): Result<OutputLease> = synchronized(ownershipLock) {
        if (outputOwner != null || inputOwner != null) {
            return@synchronized Result.failure(IllegalStateException("Audio output is already owned"))
        }
        Result.success(OutputLease(interactionId, deviceId, System.currentTimeMillis(), ::releaseOutput).also {
            outputOwner = it
        })
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
        if (transactionId != null) {
            if (currentTransactionId() != transactionId) return
        }
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

    private fun releaseInput(lease: InputLease) = synchronized(ownershipLock) {
        if (inputOwner === lease) inputOwner = null
    }

    private fun releaseOutput(lease: OutputLease) = synchronized(ownershipLock) {
        if (outputOwner === lease) outputOwner = null
    }
}
