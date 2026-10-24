package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.fx.haloTrace
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

@Composable
internal fun NotebookMemoryTip(count: Int, onReview: () -> Unit) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    Surface(Modifier.fillMaxWidth().haloTrace(enabled = true, accent = MaterialTheme.colorScheme.primary, corner = 20.dp, lapOnAppear = true), shape = HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HaloIcon(HaloGlyph.HALO, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(uiText("halo tip"), style = MaterialTheme.typography.labelLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HaloIcon(HaloGlyph.MEMORY, null, Modifier.size(24.dp))
                Text(uiText("Memories waiting for your OK: %1\$d", count), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge)
                if (!stacked) HaloActionButton(onReview, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Review")) }
            }
            if (stacked) HaloActionButton(onReview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Review")) }
        }
    }
}
