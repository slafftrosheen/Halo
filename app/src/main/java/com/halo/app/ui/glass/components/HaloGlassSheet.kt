package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Glass sheet component for bottom sheets and modal containers.
 * Provides a glass-material surface with appropriate rounding for sheets.
 */
@Composable
fun HaloGlassSheet(
    modifier: Modifier = Modifier,
    material: HaloGlassMaterial = HaloGlassMaterials.Elevated,
    shape: androidx.compose.foundation.shape.Shape = HaloGlassShapes.xl,
    content: @Composable () -> Unit
) {
    HaloGlassSurface(
        modifier = modifier
            .then(
                Modifier
                    .fillMaxWidth()
                    .glassElevation(HaloGlassTokens.level3) // 12.dp elevation for sheets
            ),
        material = material,
        shape = shape
    ) {
        content()
    }
}

/**
 * Extended glass sheet with different variants.
 */
object HaloGlassSheet {
    /**
     * Creates a standard glass sheet (elevated material)
     */
    @Composable
    fun Standard(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        HaloGlassSheet(
            modifier = modifier,
            material = HaloGlassMaterials.Elevated,
            content = content
        )
    }

    /**
     * Creates a background glass sheet (for full-screen modals)
     */
    @Composable
    fun Background(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        HaloGlassSheet(
            modifier = modifier,
            material = HaloGlassMaterials.Background,
            shape = HaloGlassShapes.large, // Less rounding for full-screen
            content = content
        )
    }

    /**
     * Creates a strong glass sheet (for critical modals)
     */
    @Composable
    fun Strong(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        HaloGlassSheet(
            modifier = modifier,
            material = HaloGlassMaterials.Strong,
            content = content
        )
    }
}

/**
 * Glass bottom sheet handle - the grabber at the top of bottom sheets.
 */
@Composable
fun HaloGlassSheetHandle(
    modifier: Modifier = Modifier,
    width: Dp = 36.dp,
    height: Dp = 4.dp,
    color: Color = HaloGlassColors.BorderStandard
) {
    Box(
        modifier = modifier
            .then(
                Modifier
                    .size(width, height)
                    .align(androidx.compose.ui.Alignment.CenterHorizontally)
            )
    ) {
        HaloGlassSurface(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp), // Inner padding for the handle
            material = HaloGlassMaterials.Standard.copy(
                containerColor = color
            ),
            shape = HaloGlassShapes.pill
        ) {
            // Handle content is just the glass surface
        }
    }
}