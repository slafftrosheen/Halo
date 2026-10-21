package com.halo.app.ui.design

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText

/** Technical destinations keep a visible, quiet identity independent of the chosen accent. */
@Composable
internal fun HaloLabBoundary(enabled: Boolean, content: @Composable () -> Unit) {
    val current = MaterialTheme.colorScheme
    val dark = current.background.luminance() < .5f
    val colors = if (!enabled) current else current.copy(
        primary = Color(if (dark) 0xFFC9B8FF else 0xFF6446D6),
        primaryContainer = Color(0xFFC9B8FF), onPrimaryContainer = Color(0xFF160E3A),
    )
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Column(Modifier.fillMaxSize()) {
                if (enabled) Text(uiText("Lab · Advanced"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = colors.primary, style = MaterialTheme.typography.labelLarge)
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        }
    }
}
