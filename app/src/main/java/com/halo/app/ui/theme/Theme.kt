package com.halo.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HaloColorScheme =
  darkColorScheme(
    primary = HaloAccent,
    onPrimary = HaloBase,
    background = HaloBase,
    onBackground = HaloTextPrimary,
    surface = HaloSurface,
    onSurface = HaloTextPrimary,
    surfaceVariant = HaloDivider,
    onSurfaceVariant = HaloTextSecondary,
  )

@Composable
fun BrainToolTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic color to maintain the strict minimal aesthetic
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = HaloColorScheme, typography = Typography, content = content)
}
