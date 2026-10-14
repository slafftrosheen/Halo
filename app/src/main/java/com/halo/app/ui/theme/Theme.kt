package com.halo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
    surfaceContainerLowest = androidx.compose.ui.graphics.HaloNightInk,
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
    primary = androidx.compose.ui.graphics.HaloDeepMint,
    onPrimary = LightSurface,
    primaryContainer = androidx.compose.ui.graphics.HaloMintTint,
    onPrimaryContainer = LightOnBackground,
    secondary = androidx.compose.ui.graphics.HaloSlate,
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
    if (palette == HaloPalette()) return base
    val accent = when (palette.accent) {
        HaloAccent.MINT -> if (dark) HaloPrimary else androidx.compose.ui.graphics.HaloDeepMint
        HaloAccent.BLUE -> androidx.compose.ui.graphics.Color(if (dark) 0xFFB6DFE2 else 0xFF2454A6)
        HaloAccent.LILAC -> androidx.compose.ui.graphics.Color(if (dark) 0xFFD5C8FF else 0xFF6841A0)
        HaloAccent.CORAL -> androidx.compose.ui.graphics.Color(if (dark) 0xFFF4BEA9 else 0xFF9A452A)
    }
    val background = when (palette.tone) {
        HaloTone.NATURAL -> base.background
        HaloTone.WARM -> androidx.compose.ui.graphics.Color(if (dark) 0xFF211B17 else 0xFFFBF8F3)
        HaloTone.COOL -> androidx.compose.ui.graphics.Color(if (dark) 0xFF171D25 else 0xFFF3F7FC)
    }
    val surface = when (palette.tone) {
        HaloTone.NATURAL -> base.surface
        HaloTone.WARM -> androidx.compose.ui.graphics.Color(if (dark) 0xFF2C2520 else 0xFFFFFCF8)
        HaloTone.COOL -> androidx.compose.ui.graphics.Color(if (dark) 0xFF232C38 else 0xFFF9FCFF)
    }
    fun tint(amount: Float) = androidx.compose.ui.graphics.lerp(surface, accent, amount)
    return base.copy(primary = accent, primaryContainer = tint(if (dark) .18f else .12f),
        onPrimaryContainer = base.onSurface, background = background, surface = surface,
        surfaceContainerLowest = background, surfaceContainerLow = androidx.compose.ui.graphics.lerp(background, surface, .65f),
        surfaceContainer = surface, surfaceContainerHigh = tint(.06f), surfaceContainerHighest = tint(.10f),
        surfaceVariant = tint(.08f), surfaceTint = accent)
}

@Composable
fun HaloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 0f,
    palette: HaloPalette = HaloPalette(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = haloColorScheme(darkTheme, palette),
        typography = Typography,
        shapes = HaloShapes,
        content = {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
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
