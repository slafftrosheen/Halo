package com.halo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

private val HaloDarkColorScheme = darkColorScheme(
    primary = HaloPrimary,
    onPrimary = DarkBackground,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF16463E),
    onPrimaryContainer = DarkOnBackground,
    secondary = HaloAction,
    onSecondary = DarkBackground,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF1D1A4D),
    onSecondaryContainer = DarkOnSurface,
    tertiary = HaloPastelCoral,
    onTertiary = DarkBackground,
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF493029),
    onTertiaryContainer = DarkOnSurface,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    surfaceContainerLowest = HaloNightInk,
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF120F41),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF272359),
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = androidx.compose.ui.graphics.Color(0xFFFF8795),
    onError = DarkBackground,
    errorContainer = androidx.compose.ui.graphics.Color(0xFF512A35),
    onErrorContainer = DarkOnSurface,
    surfaceTint = androidx.compose.ui.graphics.Color.Transparent
)

private val HaloLightColorScheme = lightColorScheme(
    primary = HaloDeepMint,
    onPrimary = LightSurface,
    primaryContainer = HaloMintTint,
    onPrimaryContainer = LightOnBackground,
    secondary = HaloSlate,
    onSecondary = LightSurface,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE5EDEA),
    onSecondaryContainer = LightOnSurface,
    tertiary = androidx.compose.ui.graphics.Color(0xFF8D4D25),
    onTertiary = LightSurface,
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFE4D6),
    onTertiaryContainer = LightOnSurface,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF0F3EF),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFE9EEE8),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFDDE2DD),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFC9D8CF),
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = androidx.compose.ui.graphics.Color(0xFFB3263B),
    onError = LightSurface,
    errorContainer = androidx.compose.ui.graphics.Color(0xFFFFE1E5),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF7E1428),
    surfaceTint = androidx.compose.ui.graphics.Color.Transparent
)

private val HaloShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = com.halo.app.ui.design.HaloShapes.control,
    medium = com.halo.app.ui.design.HaloShapes.card,
    large = com.halo.app.ui.design.HaloShapes.hero,
    extraLarge = com.halo.app.ui.design.HaloShapes.hero
)

/** Curated pairs preserve readable foregrounds; semantic error/warning colours retain their meaning. */
internal fun haloColorScheme(dark: Boolean, palette: HaloPalette): androidx.compose.material3.ColorScheme {
    val base = if (dark) HaloDarkColorScheme else HaloLightColorScheme
    val surfaces = brandSurfaces(dark, palette.tone)
    val accent = brandAccent(dark, palette.accent)
    return base.copy(
        primary = accent.ink,
        onPrimary = if (dark) accent.onFill else androidx.compose.ui.graphics.Color.White,
        primaryContainer = accent.fill, onPrimaryContainer = accent.onFill,
        secondary = surfaces.muted, onSecondary = surfaces.background,
        secondaryContainer = surfaces.raised, onSecondaryContainer = surfaces.text,
        background = surfaces.background, onBackground = surfaces.text,
        surface = surfaces.surface, onSurface = surfaces.text,
        surfaceContainerLowest = surfaces.background,
        surfaceContainerLow = surfaces.surface, surfaceContainer = surfaces.surface,
        surfaceContainerHigh = surfaces.raised, surfaceContainerHighest = surfaces.raised,
        surfaceVariant = surfaces.raised, onSurfaceVariant = surfaces.muted,
        outline = surfaces.muted.copy(alpha = .55f),
        outlineVariant = surfaces.text.copy(alpha = if (dark) .08f else .12f),
        error = androidx.compose.ui.graphics.Color(if (dark) 0xFFFF7A7A else 0xFFC53030),
        surfaceTint = androidx.compose.ui.graphics.Color.Transparent,
    )
}

@Composable
fun HaloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 0f,
    palette: HaloPalette = HaloPalette(),
    content: @Composable () -> Unit
) {
    // AppLanguageProvider supplies the selected language through LocalConfiguration.
    // This remains reactive on EN/LV/RU changes without restarting Halo's runtime.
    val language = LocalConfiguration.current.locales.get(0)?.language ?: "en"
    val visual = rememberVisualPreferences()
    val scheme = haloColorScheme(darkTheme, palette).let { colors ->
        if (visual.higherContrast) colors.copy(
            onSurfaceVariant = colors.onSurface,
            outline = colors.onSurface.copy(alpha = .72f),
            outlineVariant = colors.onSurface.copy(alpha = .38f),
        ) else colors
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = haloTypographyForLanguage(language),
        shapes = HaloShapes,
        content = {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                LocalHaloPalette provides palette,
                LocalHaloAccentInk provides brandAccent(darkTheme, palette.accent).ink,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,
                    effectiveFontScale(density.fontScale, fontScale)), content = content)
        }
    )
}

/** An app preference may enlarge text, but must never undo the system accessibility setting. */
internal fun effectiveFontScale(systemScale: Float, requestedScale: Float): Float =
    if (requestedScale.isFinite() && requestedScale > 0f) maxOf(systemScale, requestedScale) else systemScale

/** Compatibility alias while older previews/tests migrate to HaloTheme. */
@Composable
fun BrainToolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredDynamicColor = dynamicColor
    HaloTheme(darkTheme = darkTheme, content = content)
}
