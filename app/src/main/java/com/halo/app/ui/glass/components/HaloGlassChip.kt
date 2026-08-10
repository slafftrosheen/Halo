package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.halo.app.ui.glass.components.HaloGlassSurface
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassShapes
import com.halo.app.ui.glass.material.HaloGlassTokens

/**
 * Glass chip component that replaces legacy HaloTag and HaloStatusChip.
 * Used for labels, status indicators, and categorical chips.
 */
@Composable
fun HaloGlassChip(
    modifier: Modifier = Modifier,
    text: String,
    color: Color = HaloGlassColors.TextPrimary,
    material: HaloGlassMaterial = HaloGlassMaterials.Standard,
    shape: androidx.compose.foundation.shape.Shape = HaloGlassShapes.pill,
    padding: Dp = HaloGlassTokens.xs
) {
    // Determine appropriate text color based on background
    val textColor = if (color == HaloGlassColors.TextPrimary) {
        HaloGlassColors.TextPrimary
    } else {
        // Simple contrast check - in production would use proper luminance calculation
        val isLightBackground = 
            (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 186
        if (isLightBackground) HaloGlassColors.TextPrimary else HaloGlassColors.TextPrimary
    }
    
    HaloGlassSurface(
        modifier = modifier
            .then(
                Modifier
                    .padding(
                        horizontal = padding,
                        vertical = padding / 2
                    )
            ),
        material = material.copy(
            containerColor = color
        ),
        shape = shape
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
    }
}

/**
 * Extended glass chip with semantic variants.
 */
object HaloGlassChip {
    /**
     * Creates a status chip indicating success
     */
    @Composable
    fun Success(
        modifier: Modifier = Modifier,
        text: String = "Success"
    ) {
        HaloGlassChip(
            modifier = modifier,
            text = text,
            color = HaloGlassColors.Success,
            material = HaloGlassMaterials.Standard.copy(
                containerColor = HaloGlassColors.Success.copy(alpha = 0.1f)
            )
        )
    }

    /**
     * Creates a status chip indicating warning
     */
    @Composable
    fun Warning(
        modifier: Modifier = Modifier,
        text: String = "Warning"
    ) {
        HaloGlassChip(
            modifier = modifier,
            text = text,
            color = HaloGlassColors.Warning,
            material = HaloGlassMaterials.Standard.copy(
                containerColor = HaloGlassColors.Warning.copy(alpha = 0.1f)
            )
        )
    }

    /**
     * Creates a status chip indicating danger/error
     */
    @Composable
    fun Danger(
        modifier: Modifier = Modifier,
        text: String = "Error"
    ) {
        HaloGlassChip(
            modifier = modifier,
            text = text,
            color = HaloGlassColors.Danger,
            material = HaloGlassMaterials.Standard.copy(
                containerColor = HaloGlassColors.Danger.copy(alpha = 0.1f)
            )
        )
    }

    /**
     * Creates a status chip indicating info
     */
    @Composable
    fun Info(
        modifier: Modifier = Modifier,
        text: String = "Info"
    ) {
        HaloGlassChip(
            modifier = modifier,
            text = text,
            color = HaloGlassColors.Info,
            material = HaloGlassMaterials.Standard.copy(
                containerColor = HaloGlassColors.Info.copy(alpha = 0.1f)
            )
        )
    }

    /**
     * Creates an accent chip for primary actions/tags
     */
    @Composable
    fun Accent(
        modifier: Modifier = Modifier,
        text: String
    ) {
        HaloGlassChip(
            modifier = modifier,
            text = text,
            color = HaloGlassColors.Accent,
            material = HaloGlassMaterials.Standard.copy(
                containerColor = HaloGlassColors.Accent.copy(alpha = 0.1f)
            )
        )
    }
}