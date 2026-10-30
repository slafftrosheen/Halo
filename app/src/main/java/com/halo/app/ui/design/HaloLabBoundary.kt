package com.halo.app.ui.design

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText

/** Technical destinations keep their explicit label while honoring the selected appearance. */
@Composable
internal fun HaloLabBoundary(enabled: Boolean, content: @Composable () -> Unit) {
    val current = MaterialTheme.colorScheme
    val colors = current
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = if (enabled) colors.background else com.halo.app.ui.theme.haloWorkspaceBackground()) {
            Column(Modifier.fillMaxSize()) {
                if (enabled) Text(uiText("Lab · Advanced"), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = colors.primary, style = MaterialTheme.typography.labelLarge)
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        }
    }
}
