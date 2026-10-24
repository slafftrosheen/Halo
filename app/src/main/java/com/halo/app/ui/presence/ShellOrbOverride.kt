package com.halo.app.ui.presence

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf

/** A page can project its existing scoped editor/lesson owner into the one shell face.
 * This contains no engine, state machine, persistence or effect replay. */
data class ShellOrbOverride(val signal: HaloSignalPresentation, val interaction: HaloOrbInteraction,
    val onPrimary: (HaloOrbPrimary) -> Unit, val onStop: () -> Unit)

val LocalShellOrbOverride = staticCompositionLocalOf<MutableState<ShellOrbOverride?>?> { null }
