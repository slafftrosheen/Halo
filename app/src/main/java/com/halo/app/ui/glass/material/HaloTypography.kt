package com.halo.app.ui.glass.material

import com.halo.app.ui.theme.Typography
import androidx.compose.ui.text.font.FontFamily

/** Compatibility names backed by the same type scale as every Material screen. */
object HaloTypography {
    val displayLarge = Typography.displayLarge
    val displayMedium = Typography.headlineLarge
    val headlineLarge = Typography.headlineLarge
    val headlineMedium = Typography.headlineMedium
    val titleLarge = Typography.titleLarge
    val titleMedium = Typography.titleMedium
    val bodyLarge = Typography.bodyLarge
    val bodyMedium = Typography.bodyMedium
    val bodySmall = Typography.bodySmall
    val labelLarge = Typography.labelLarge
    val labelMedium = Typography.labelMedium
    val labelSmall = Typography.labelSmall
    val monoMedium = Typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    val monoSmall = Typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
}
