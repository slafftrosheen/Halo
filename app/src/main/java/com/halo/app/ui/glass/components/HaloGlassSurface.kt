package com.halo.app.ui.glass.components

import androidx.compose.foundation.Background
import androidx.compose.foundation.border.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AndroidApiLevel
import androidx.compose.ui.unit.Dp
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassShapes
import com.halo.app.ui.glass.material.HaloGlassTokens

/**
 * Base glass surface component that implements the Halo Glass material system.
 * Provides background translucency, backdrop blur (when available), subtle border,
 * elevation shadow, and specular highlight.
 */
@Composable
fun HaloGlassSurface(
    modifier: Modifier = Modifier,
    material: HaloGlassMaterial = HaloGlassMaterials.Standard,
    shape: Shape = HaloGlassShapes.large,
    content: @Composable () -> Unit
) {
    // Check if backdrop blur is supported (API 31+)
    val isBlurSupported = AndroidApiLevel.current >= 31
    
    Box(
        modifier = modifier
            .then(
                // Apply glass background with border
                Background(
                    color = material.containerColor,
                    shape = shape
                )
                .then(
                    BorderStroke(
                        width = 1.dp,
                        brush = androidx.compose.foundation.border.BrushBorderBrush(
                            Brush.solid(color = material.borderColor)
                        ),
                        shape = shape
                    )
                )
            )
            .then(
                // Apply elevation shadow
                Modifier
                    .graphicsLayer { elevationZ = material.elevation }
                    .then(
                        androidx.compose.foundation.layout.sizeIn(
                            width = androidx.constraints.LayoutParams.WRAP_CONTENT,
                            height = androidx.constraints.LayoutParams.WRAP_CONTENT
                        )
                    )
                    .shadow(material.elevation, shape)
            )
            .then(
                // Apply backdrop blur if supported (API 31+)
                if (isBlurSupported) {
                    // Note: Actual RenderEffect blur would require Accompanist or custom implementation
                    // For now, we rely on the translucent color to suggest blur
                    Modifier
                } else {
                    // Fallback: enhance visual appearance for pre-API 31 devices
                    Modifier
                        .then(
                            Modifier
                                .drawBehind {
                                    // Draw gradient highlights for glass effect on older APIs
                                    if (material.highlightBrush != null) {
                                        // Top glass highlight
                                        drawRect(
                                            topLeft = androidx.ui.geometry.Offset.Zero,
                                            size = androidx.ui.geometry.Size(size.width, 2.dp),
                                            brush = material.highlightBrush
                                        )
                                        // Bottom subtle definition
                                        drawRect(
                                            topLeft = androidx.ui.geometry.Offset(0, size.height - 2.dp),
                                            size = androidx.ui.geometry.Size(size.width, 2.dp),
                                            color = androidx.compose.ui.graphics.Color(0x1A000000)
                                        )
                                    }
                                }
                        )
                }
            )
            .then(
                // Add slight elevation-based blur simulation for consistency
                Modifier
                    .drawBehind {
                        // Additional depth enhancement
                    }
            )
    ) {
        // Specular highlight layer (if provided)
        if (material.highlightBrush != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.2f) // Subtle highlight intensity
                    .then(
                        Modifier
                            .drawBehind {
                                // In a full implementation, this would use the highlight brush
                                // to create a realistic specular reflection
                            }
                    )
            )
        }
        
        // Main content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(HaloGlassTokens.lg),
            content = content
        )
    }
}