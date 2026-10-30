package com.halo.app.ui.glass.material

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Legacy names resolve to the current Halo theme, including manual appearance choices. */
object HaloGlassColors {
    val Background: Color @Composable get() = MaterialTheme.colorScheme.background
    val BackgroundSecondary: Color @Composable get() = MaterialTheme.colorScheme.background
    val Glass: Color @Composable get() = MaterialTheme.colorScheme.surface
    val GlassElevated: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val GlassStrong: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val TextMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
    val AccentStrong: Color @Composable get() = MaterialTheme.colorScheme.primary
    val AccentMuted: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val AccentContainer: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val Success: Color @Composable get() = MaterialTheme.colorScheme.primary
    val Danger: Color @Composable get() = MaterialTheme.colorScheme.error
    val Info: Color @Composable get() = MaterialTheme.colorScheme.secondary
    val BorderSubtle: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val BorderStandard: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val BorderStrong: Color @Composable get() = MaterialTheme.colorScheme.outline
    val Warning: Color @Composable get() = MaterialTheme.colorScheme.tertiary
    val HighlightTop = Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    val HighlightBottom = HighlightTop
}
