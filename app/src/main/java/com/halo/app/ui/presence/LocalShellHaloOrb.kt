package com.halo.app.ui.presence

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True only while the surrounding shell supplies the assistant launcher and owns its lifecycle.
 * Standalone previews, Mini and other hosts keep their local launcher by default.
 */
val LocalShellHaloOrb = staticCompositionLocalOf { false }
