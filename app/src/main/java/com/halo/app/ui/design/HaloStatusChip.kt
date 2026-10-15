package com.halo.app.ui.design

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** A passive fact, deliberately distinct from a selectable filter or an approval. */
@Composable
fun HaloStatusChip(label: String, active: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = HaloShapes.pill,
        color = if (active) lerp(scheme.surface, scheme.primaryContainer, .15f) else scheme.secondaryContainer,
        contentColor = scheme.onSurface) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
    }
}
