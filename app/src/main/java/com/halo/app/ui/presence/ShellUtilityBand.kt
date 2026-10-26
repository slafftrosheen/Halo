package com.halo.app.ui.presence

import androidx.compose.runtime.*

/** Ephemeral composition projection. It carries no command, content grant or persisted state. */
class ShellUtilityProjection(val owner: ShellPageOwner, val locals: CompositionLocalContext, val content: @Composable () -> Unit)
/** Presentation only: the normal carousel currently exposes this page's enabled Back. */
val LocalShellCarouselActionIds = staticCompositionLocalOf<Set<String>> { emptySet() }
val LocalShellCarouselBackVisible = staticCompositionLocalOf { false }
val LocalShellUtilityBand = staticCompositionLocalOf<MutableState<ShellUtilityProjection?>?> { null }

@Composable
fun BindShellUtilityBand(owner: ShellPageOwner, content: @Composable () -> Unit): Boolean {
    val holder = LocalShellUtilityBand.current ?: return false
    val entry = androidx.lifecycle.compose.LocalLifecycleOwner.current as? androidx.navigation.NavBackStackEntry
    val effective = if (owner.navigationEntryId == null && entry != null) owner.copy(navigationEntryId = entry.id) else owner
    val eligible = LocalShellPageOwnerFilter.current(effective)
    val latest by rememberUpdatedState(content)
    val locals = currentCompositionLocalContext
    val projection = remember(effective, locals) { ShellUtilityProjection(effective, locals) { latest() } }
    SideEffect {
        if (eligible) holder.value = projection
        else if (holder.value === projection) holder.value = null
    }
    DisposableEffect(holder, projection) {
        onDispose { if (holder.value === projection) holder.value = null }
    }
    return true
}

/** Keep page lifecycle/view-model/saveable owners while relocating only the presentation. */
@Composable
fun RenderShellUtilityBand(projection: ShellUtilityProjection) {
    val carouselActionIds = LocalShellCarouselActionIds.current
    val carouselBackVisible = LocalShellCarouselBackVisible.current
    key(projection.owner.navigationEntryId, projection.owner.objectId) {
        CompositionLocalProvider(projection.locals) {
            CompositionLocalProvider(LocalShellCarouselBackVisible provides carouselBackVisible,
                LocalShellCarouselActionIds provides carouselActionIds) { projection.content() }
        }
    }
}
