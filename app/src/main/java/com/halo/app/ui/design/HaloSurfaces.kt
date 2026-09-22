package com.halo.app.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object HaloSurfaces {
    val canvas: Color
        @Composable get() = MaterialTheme.colorScheme.background
    val surface: Color
        @Composable get() = MaterialTheme.colorScheme.surface
    val selected: Color
        @Composable get() = MaterialTheme.colorScheme.primaryContainer
}
