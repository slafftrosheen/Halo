package com.halo.app.ui.presence

import com.halo.app.ui.icons.HaloGlyph

/** Menu metadata only. Navigation and mutations remain in their existing owners. */
data class HaloOrbDestination(val route: String, val title: String, val glyph: HaloGlyph)

data class HaloOrbContextAction(val id: String, val title: String, val glyph: HaloGlyph,
    val catalog: HaloToolCatalogRequest? = null, val enabled: Boolean = true,
    val disabledReason: String? = null,
    /** Exact ephemeral page lease that produced this presentation; never persisted as a pin. */
    val pageLease: ShellPageProjection? = null)

internal fun HaloOrbContextAction.invokePageAction(current: ShellPageProjection?, accepts: (ShellPageOwner) -> Boolean): Boolean {
    val shown = pageLease ?: return false
    if (!enabled || !id.startsWith("page:") || current !== shown || !accepts(shown.owner)) return false
    return shown.invoke(id.removePrefix("page:"))
}
