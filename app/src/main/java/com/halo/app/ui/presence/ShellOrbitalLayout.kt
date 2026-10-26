package com.halo.app.ui.presence

import androidx.compose.runtime.staticCompositionLocalOf

/** Presentation only: leaves the side arc clear while retaining the explicit full list.
 * Compact and standalone launchers keep their original adjacent list control.
 */
internal val LocalShellOrbitalLayout = staticCompositionLocalOf { false }
