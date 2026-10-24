package com.halo.app.ui.screens.kids

import androidx.compose.runtime.*
import com.halo.app.ui.presence.*

/** Projects the existing scoped Kids voice target; never dispatches into a Home conversation. */
@Composable
internal fun KidsShellOrbBinding(signal: HaloSignalPresentation, busy: Boolean,
    onTalk: () -> Unit, onStop: () -> Unit) {
    val holder = LocalShellOrbOverride.current
    val latestTalk by rememberUpdatedState(onTalk)
    val latestStop by rememberUpdatedState(onStop)
    val primary = remember { { action: HaloOrbPrimary ->
        if (action == HaloOrbPrimary.TALK) latestTalk() else latestStop()
    } }
    val stop = remember { { latestStop() } }
    val interaction = HaloOrbInteraction(if (busy) HaloOrbPrimary.STOP else HaloOrbPrimary.TALK, busy)
    var installed by remember(holder) { mutableStateOf<ShellOrbOverride?>(null) }
    LaunchedEffect(signal, interaction, holder) {
        if (holder != null) {
            val projection = ShellOrbOverride(signal, interaction, primary, stop)
            installed = projection
            holder.value = projection
        }
    }
    DisposableEffect(holder) {
        onDispose { if (holder?.value === installed) holder?.value = null }
    }
}
