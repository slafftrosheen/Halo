package com.halo.app.ui.components

import androidx.compose.runtime.Immutable
import com.halo.runtime.kernel.ProcessState

/**
 * Canonical UI presence states for the Halo orb.
 * Map this to real runtime/engine states when ready.
 */
@Immutable
enum class HaloPresenceState {
    Idle,
    Listening,
    Thinking,
    Acting,
    Error
}

/**
 * High-level presence snapshot for the UI.
 */
@Immutable
data class HaloPresenceUi(
    val state: HaloPresenceState,
    val primaryLabel: String = "Halo",
    val secondaryLabel: String? = null,
    val isInteractive: Boolean = true,
    val enginesHealth: ProcessState? = null
)

/**
 * Simple helper to map a ProcessState into a presence state.
 */
fun presenceFromProcessState(state: ProcessState?): HaloPresenceState {
    return when (state) {
        ProcessState.Running -> HaloPresenceState.Acting
        ProcessState.Paused -> HaloPresenceState.Thinking
        ProcessState.Crashed -> HaloPresenceState.Error
        null, ProcessState.Stopped -> HaloPresenceState.Idle
    }
}
