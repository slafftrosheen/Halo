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
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF263E38),
    onPrimaryContainer = DarkOnBackground,
    secondary = HaloAction,
    onSecondary = DarkBackground,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF263C50),
    onSecondaryContainer = DarkOnSurface,
    tertiary = HaloPastelCoral,
    onTertiary = DarkBackground,
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF4A342B),
    onTertiaryContainer = DarkOnSurface,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF101613),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF19231E),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF334138),
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = androidx.compose.ui.graphics.Color(0xFFFF8795),
    onError = DarkBackground
)

private val HaloLightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF147D64),
    onPrimary = LightSurface,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFDDF2E9),
    onPrimaryContainer = LightOnBackground,
    secondary = androidx.compose.ui.graphics.Color(0xFF3967B5),
    onSecondary = LightSurface,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE0EDFF),
    onSecondaryContainer = LightOnSurface,
    tertiary = androidx.compose.ui.graphics.Color(0xFF925137),
    onTertiary = LightSurface,
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFE5D8),
    onTertiaryContainer = LightOnSurface,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF1F5EF),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFEBF0E9),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFE5ECE3),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFDEE7DC),
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = androidx.compose.ui.graphics.Color(0xFFB3263B),
    onError = LightSurface
)

private val HaloShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun HaloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 0f,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) HaloDarkColorScheme else HaloLightColorScheme,
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
