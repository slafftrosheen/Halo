package com.halo.app.ui.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.halo.app.ui.fx.haloShimmer

/** Caller owns the loading state. Reduced motion is a still skeleton, never fake progress. */
@Composable
fun HaloLoadingSkeleton(label: String, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = lerp(base, MaterialTheme.colorScheme.onSurface, .08f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(.8f, .55f, 1f).forEach { width ->
                Box(Modifier.fillMaxWidth(width).height(16.dp).clip(RoundedCornerShape(8.dp))
                    .haloShimmer(base, highlight))
            }
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
