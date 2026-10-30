package com.halo.app.ui.presence

import com.halo.app.ui.icons.HaloGlyph

/** Menu metadata only. Navigation and mutations remain in their existing owners. */
data class HaloOrbDestination(val route: String, val title: String, val glyph: HaloGlyph)

data class HaloOrbContextAction(val id: String, val title: String, val glyph: HaloGlyph,
    val catalog: HaloToolCatalogRequest? = null, val enabled: Boolean = true,
    val disabledReason: String? = null)
