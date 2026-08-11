package com.halo.app.ui.glass.material

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Immutable data class representing a glass material level.
 * Each level defines the visual properties for different glass elevations and interactions.
 */
@Immutable
data class HaloGlassMaterial(
    val containerColor: Color,      // Background color with alpha for glass effect
    val borderColor: Color,         // Subtle border color
    val contentColor: Color,        // Primary text/icon color
    val blurRadius: Dp,             // Backdrop blur radius (0 if not supported)
    val elevation: Dp,              // Shadow elevation
    val highlightBrush: Brush?      // Specular highlight brush (top edge)
) {
    /**
     * Creates a border stroke from the border color.
     */
    val borderBrush: Brush
        get() = SolidColor(borderColor)
    
    /**
     * Creates a combined background+border modifier for glass surfaces.
     */
    fun glassBackgroundModifier(modifier: Modifier, shape: Shape): Modifier {
        return modifier
            .background(
                color = containerColor,
                shape = shape
            )
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = shape
            )
    }
}

/**
 * Collection of predefined glass material levels for Halo.
 */
object HaloGlassMaterials {
    val Background = HaloGlassMaterial(
        containerColor = Color(0x8C080A0B),
        borderColor = Color(0x33FFFFFF),
        contentColor = Color(0xFFF5F7F6),
        blurRadius = 20.dp,
        elevation = 0.dp,
        highlightBrush = null
    )

    val Standard = HaloGlassMaterial(
        containerColor = Color(0xAA141719),
        borderColor = Color(0x4DFFFFFF),
        contentColor = Color(0xFFF5F7F6),
        blurRadius = 30.dp,
        elevation = HaloGlassElevation.level1,
        highlightBrush = HaloGlassColors.HighlightTop
    )

    val Elevated = HaloGlassMaterial(
        containerColor = Color(0xBF1B1F20),
        borderColor = Color(0x66FFFFFF),
        contentColor = Color(0xFFF5F7F6),
        blurRadius = 40.dp,
        elevation = HaloGlassElevation.level3,
        highlightBrush = HaloGlassColors.HighlightTop
    )

    val Strong = HaloGlassMaterial(
        containerColor = Color(0xD11B1F20),
        borderColor = Color(0x80FFFFFF),
        contentColor = Color(0xFFF5F7F6),
        blurRadius = 50.dp,
        elevation = HaloGlassElevation.level4,
        highlightBrush = HaloGlassColors.HighlightTop
    )

    val Interactive = HaloGlassMaterial(
        containerColor = Color(0xE61B1F20),
        borderColor = Color(0x99FFFFFF),
        contentColor = Color(0xFFF5F7F6),
        blurRadius = 20.dp,
        elevation = HaloGlassElevation.level2,
        highlightBrush = HaloGlassColors.HighlightTop
    )
    
    fun HaloGlassMaterial.pressed(): HaloGlassMaterial {
        return copy(
            containerColor = containerColor.copy(alpha = containerColor.alpha * 0.9f),
            elevation = elevation * 1.5f
        )
    }
}
