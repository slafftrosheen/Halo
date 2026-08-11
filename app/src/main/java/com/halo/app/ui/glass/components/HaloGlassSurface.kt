package com.halo.app.ui.glass.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloGlassTokens
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * Base glass surface component that implements the Halo Glass material system.
 * Provides background translucency, backdrop blur (when available), subtle border,
 * elevation shadow, and specular highlight.
 */
@Composable
fun HaloGlassSurface(
    modifier: Modifier = Modifier,
    material: HaloGlassMaterial = HaloGlassMaterials.Standard,
    shape: Shape = HaloGlassTokens.largeShape,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(
                color = material.containerColor,
                shape = shape
            )
            .border(
                width = 1.dp,
                color = material.borderColor,
                shape = shape
            )
            .graphicsLayer { 
                shadowElevation = material.elevation.toPx()
            }
            .shadow(material.elevation, shape)
            .drawBehind {
                // Draw gradient highlights for glass effect
                if (material.highlightBrush != null) {
                    // Top glass highlight
                    drawRect(
                        brush = material.highlightBrush,
                        topLeft = Offset.Zero,
                        size = Size(size.width, 2.dp.toPx())
                    )
                    // Bottom subtle definition
                    drawRect(
                        color = Color(0x1A000000),
                        topLeft = Offset(0f, size.height - 2.dp.toPx()),
                        size = Size(size.width, 2.dp.toPx())
                    )
                }
            }
    ) {
        // Specular highlight layer (if provided)
        if (material.highlightBrush != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.2f)
                    .drawBehind {
                        // Highlight implementation
                    }
            )
        }
        
        // Main content area
        Box(
            modifier = Modifier
                .padding(HaloGlassTokens.lg),
            content = content
        )
    }
}
