package com.halo.app.ui.toolkit

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class HaloRecordPhase { STARTING, RECORDING, STOPPING }

internal data class HaloRecordSessionState(
    val sessionToken: String,
    val sourceVersion: Long,
    val phase: HaloRecordPhase,
)

/** The foreground capture is the single owner of a session and exposes content-free control receipts. */
internal object HaloRecordSessions {
    private val mutable = MutableStateFlow<HaloRecordSessionState?>(null)
    val state = mutable.asStateFlow()
    private var revision = 0L
    private var stopCapture: (() -> Unit)? = null

    @Synchronized fun begin(onStop: () -> Unit): HaloRecordSessionState {
        check(mutable.value == null)
        val next = HaloRecordSessionState(UUID.randomUUID().toString(), ++revision, HaloRecordPhase.STARTING)
        stopCapture = onStop
        mutable.value = next
        return next
    }

    @Synchronized fun started(token: String) {
        val current = mutable.value ?: return
        if (current.sessionToken == token && current.phase == HaloRecordPhase.STARTING)
            mutable.value = current.copy(sourceVersion = ++revision, phase = HaloRecordPhase.RECORDING)
    }

    @Synchronized fun stop(token: String, expectedRevision: Long? = null): HaloRecordSessionState? {
        val current = mutable.value ?: return null
        if (current.sessionToken != token || expectedRevision != null && expectedRevision != current.sourceVersion ||
            current.phase == HaloRecordPhase.STOPPING) return null
        stopCapture?.invoke()
        return current.copy(sourceVersion = ++revision, phase = HaloRecordPhase.STOPPING).also { mutable.value = it }
    }

    @Synchronized fun finish(token: String) {
        if (mutable.value?.sessionToken == token) {
            stopCapture = null
            mutable.value = null
        }
    }
}
