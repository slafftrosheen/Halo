package com.halo.app.ui.presence

import androidx.compose.runtime.*
import com.halo.app.ui.icons.HaloGlyph

/** Ephemeral page identity, never runtime authority or a durable command. */
data class ShellPageOwner(val routeFamily: String, val objectId: String? = null, val sourceIdentity: String? = null,
    val navigationEntryId: String? = null,
    val canonicalSourceVersion: String? = null,
    val selectionIds: List<String> = emptyList(),
    val canonicalToolIds: List<String> = emptyList(),
    val activeToolId: String? = null,
    /** Localized presentation only; no new callable action or authority. */
    val disabledActionReasons: Map<String, String> = emptyMap())
data class ShellPageActionMetadata(val id: String, val title: String, val glyph: HaloGlyph, val enabled: Boolean)
data class ShellPageAction(val id: String, val title: String, val glyph: HaloGlyph,
    val enabled: Boolean = true, val onInvoke: () -> Unit)

class ShellPageProjection internal constructor(val owner: ShellPageOwner,
    val actions: List<ShellPageActionMetadata>, private val dispatch: (String) -> Boolean) {
    /** False means removed/disabled/changed page. Never fall back to another screen's action. */
    fun invoke(id: String): Boolean = dispatch(id)
}

val LocalShellPageActions = staticCompositionLocalOf<MutableState<ShellPageProjection?>?> { null }
/** Root restricts animated outgoing pages to the current exact route/object; isolated hosts allow their page. */
val LocalShellPageOwnerFilter = staticCompositionLocalOf<(ShellPageOwner) -> Boolean> { { true } }
private class PageActionsLease { var active = true; var projection: ShellPageProjection? = null }

/** One projection per page. Callback-only recomposition keeps metadata/projection identity stable. */
@Composable
fun BindShellPageActions(owner: ShellPageOwner, actions: List<ShellPageAction>) {
    require(actions.map { it.id }.distinct().size == actions.size) { "Page action IDs must be unique" }
    val holder = LocalShellPageActions.current
    val navigationEntry = androidx.lifecycle.compose.LocalLifecycleOwner.current as? androidx.navigation.NavBackStackEntry
    val effectiveOwner = if (owner.navigationEntryId == null && navigationEntry != null)
        owner.copy(navigationEntryId = navigationEntry.id) else owner
    val eligible = LocalShellPageOwnerFilter.current(effectiveOwner)
    val latestEligible by rememberUpdatedState(eligible)
    val latestOwner by rememberUpdatedState(effectiveOwner)
    val latestActions by rememberUpdatedState(actions)
    val lease = remember(holder) { PageActionsLease() }
    val metadata = actions.map { ShellPageActionMetadata(it.id, it.title, it.glyph, it.enabled) }
    val latestMetadata by rememberUpdatedState(metadata)
    val projection = remember(holder, effectiveOwner, metadata) {
        lateinit var created: ShellPageProjection
        created = ShellPageProjection(effectiveOwner, metadata) { id ->
            val current = latestActions.singleOrNull { it.id == id && it.enabled }
            if (!lease.active || !latestEligible || latestOwner != effectiveOwner || latestMetadata != metadata ||
                lease.projection !== created || (holder != null && holder.value !== created) || current == null) false
            else { current.onInvoke(); true }
        }
        created
    }
    SideEffect {
        val previous = lease.projection
        lease.projection = projection
        if (eligible) {
            if (holder != null && holder.value !== projection) holder.value = projection
        } else if (holder?.value === previous || holder?.value === projection) holder?.value = null
    }
    DisposableEffect(holder) {
        onDispose {
            lease.active = false
            if (holder?.value === lease.projection) holder?.value = null
        }
    }
}
