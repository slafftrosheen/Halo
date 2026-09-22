package com.halo.app.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object HaloContentColors {
    val primary: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface
    val secondary: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val accent: Color
        @Composable get() = MaterialTheme.colorScheme.primary
}
