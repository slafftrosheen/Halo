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
    val Background: HaloGlassMaterial @androidx.compose.runtime.Composable get() = themed(false)
    val Standard: HaloGlassMaterial @androidx.compose.runtime.Composable get() = themed(false)
    val Elevated: HaloGlassMaterial @androidx.compose.runtime.Composable get() = themed(true)
    val Strong: HaloGlassMaterial @androidx.compose.runtime.Composable get() = themed(true)
    val Interactive: HaloGlassMaterial @androidx.compose.runtime.Composable get() = themed(false)

    @androidx.compose.runtime.Composable
    private fun themed(raised: Boolean): HaloGlassMaterial {
        val colors = androidx.compose.material3.MaterialTheme.colorScheme
        return HaloGlassMaterial(
            containerColor = if (raised) colors.surfaceVariant else colors.surface,
            borderColor = colors.outlineVariant,
            contentColor = colors.onSurface,
            blurRadius = 0.dp,
            elevation = if (raised) 1.dp else 0.dp,
            highlightBrush = null,
        )
    }

    fun HaloGlassMaterial.pressed(): HaloGlassMaterial = copy(
        containerColor = containerColor.copy(alpha = 0.9f), elevation = 0.dp,
    )
}
