package com.halo.app.ui.glass.material

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.BrushShader

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
        get() = androidx.compose.foundation.border.BrushBorderBrush(
            Brush.solid(color = borderColor)
        )
    
    /**
     * Creates a combined background+border modifier for glass surfaces.
     */
    fun glassBackgroundModifier(shape: androidx.compose.foundation.shape.Shape): androidx.compose.ui.Modifier {
        return androidx.compose.foundation.background.Background(
            color = containerColor,
            shape = shape
        ) then
        androidx.compose.foundation.border.BorderStroke(
            width = 1.dp,
            brush = borderBrush,
            shape = shape
        )
    }
}

/**
 * Collection of predefined glass material levels for Halo.
 * Five levels from Background (deepest) to Interactive (highest interaction).
 */
object HaloGlassMaterials {
    // Level 1: Background glass (deepest, full-screen backdrop)
    val Background = HaloGlassMaterial(
        containerColor = Color(0x8C080A0B),  // ~55% alpha #080A0B
        borderColor = Color(0x33FFFFFF),      // 20% white border
        contentColor = Color(0xFFF5F7F6),
        blurRadius = 20.dp,
        elevation = 0.dp,
        highlightBrush = null
    )

    // Level 2: Standard glass (cards, sections, most common)
    val Standard = HaloGlassMaterial(
        containerColor = Color(0xAA141719),   // ~66% alpha #141719
        borderColor = Color(0x4DFFFFFF),      // 30% white border
        contentColor = Color(0xFFF5F7F6),
        blurRadius = 30.dp,
        elevation = HaloGlassElevation.level1, // 4.dp
        highlightBrush = HaloGlassColors.HighlightTop
    )

    // Level 3: Elevated glass (modals, sheets, elevated surfaces)
    val Elevated = HaloGlassMaterial(
        containerColor = Color(0xBF1B1F20),   // ~75% alpha #1B1F20
        borderColor = Color(0x66FFFFFF),      // 40% white border
        contentColor = Color(0xFFF5F7F6),
        blurRadius = 40.dp,
        elevation = HaloGlassElevation.level3, // 12.dp
        highlightBrush = HaloGlassColors.HighlightTop
    )

    // Level 4: Strong glass (floating mic, critical UI, focused elements)
    val Strong = HaloGlassMaterial(
        containerColor = Color(0xD11B1F20),   // ~82% alpha #1B1F20
        borderColor = Color(0x80FFFFFF),      // 50% white border
        contentColor = Color(0xFFF5F7F6),
        blurRadius = 50.dp,
        elevation = HaloGlassElevation.level4, // 20.dp
        highlightBrush = HaloGlassColors.HighlightTop
    )

    // Level 5: Interactive glass (buttons, controls, pressed states)
    val Interactive = HaloGlassMaterial(
        containerColor = Color(0xE61B1F20),   // ~90% alpha #1B1F20
        borderColor = Color(0x99FFFFFF),      // 60% white border
        contentColor = Color(0xFFF5F7F6),
        blurRadius = 20.dp,                   // Less blur for interactive elements
        elevation = HaloGlassElevation.level2, // 8.dp
        highlightBrush = HaloGlassColors.HighlightTop
    )
    
    /**
     * Creates a pressed variant of a material (typically for buttons)
     */
    fun HaloGlassMaterial.pressed(): HaloGlassMaterial {
        return copy(
            containerColor = containerColor.copy(alpha = ((containerColor.alpha * 0xFF) * 0.9).toInt().toUInt()),
            borderColor = borderColor,
            contentColor = contentColor,
            blurRadius = blurRadius,
            elevation = elevation * 1.5,
            highlightBrush = highlightBrush
        )
    }
}