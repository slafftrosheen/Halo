package com.halo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val HaloColorScheme = darkColorScheme(
    primary = HaloPrimary,
    onPrimary = HaloBackground,
    primaryContainer = HaloPrimaryStrong.copy(alpha = 0.18f),
    onPrimaryContainer = HaloTextPrimary,
    secondary = HaloInformation,
    background = HaloBackground,
    onBackground = HaloTextPrimary,
    surface = HaloSurface,
    onSurface = HaloTextPrimary,
    surfaceVariant = HaloCard,
    onSurfaceVariant = HaloTextSecondary,
    outline = HaloBorder,
    outlineVariant = HaloBorderSubtle,
    error = HaloDanger,
    onError = HaloTextPrimary
)

private val HaloShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun BrainToolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Halo deliberately owns its palette so the runtime has a stable visual language
    // across Android hosts and does not inherit OEM dynamic colors.
    MaterialTheme(
        colorScheme = HaloColorScheme,
        typography = Typography,
        shapes = HaloShapes,
        content = content
    )
}
