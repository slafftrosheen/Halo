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
    primary = HaloReady,
    onPrimary = DarkBackground,
    primaryContainer = HaloPastelMint.copy(alpha = 0.16f),
    onPrimaryContainer = DarkOnBackground,
    secondary = HaloAction,
    onSecondary = DarkOnBackground,
    secondaryContainer = HaloPastelBlue.copy(alpha = 0.14f),
    onSecondaryContainer = DarkOnSurface,
    tertiary = HaloPastelCoral,
    tertiaryContainer = HaloPastelCoral.copy(alpha = 0.14f),
    onTertiaryContainer = DarkOnSurface,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = HaloDanger,
    onError = DarkOnBackground
)

private val HaloLightColorScheme = lightColorScheme(
    primary = HaloReady,
    onPrimary = LightOnBackground,
    primaryContainer = HaloPastelMint.copy(alpha = 0.42f),
    onPrimaryContainer = LightOnBackground,
    secondary = HaloAction,
    onSecondary = LightSurface,
    secondaryContainer = HaloPastelBlue.copy(alpha = 0.48f),
    onSecondaryContainer = LightOnSurface,
    tertiary = HaloPastelCoral,
    tertiaryContainer = HaloPastelCoral.copy(alpha = 0.48f),
    onTertiaryContainer = LightOnSurface,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = HaloDanger,
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
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) HaloDarkColorScheme else HaloLightColorScheme,
        typography = Typography,
        shapes = HaloShapes,
        content = content
    )
}

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
