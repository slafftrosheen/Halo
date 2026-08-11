package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloGlassTokens

/**
 * Small glass indicator/chip for status, tags, or small data points.
 */
@Composable
fun HaloGlassChip(
    modifier: Modifier = Modifier,
    text: String,
    textColor: Color = Color.Unspecified,
    material: HaloGlassMaterial = HaloGlassMaterials.Interactive,
    shape: Shape = HaloGlassTokens.pillShape,
    elevation: Dp = 2.dp
) {
    HaloGlassSurface(
        modifier = modifier,
        material = material.copy(elevation = elevation),
        shape = shape
    ) {
        Text(
            text = text,
            color = if (textColor == Color.Unspecified) material.contentColor else textColor,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * Semantic variants for glass chips.
 */
object HaloGlassChip {
    @Composable
    fun Success(modifier: Modifier = Modifier, text: String) {
        HaloGlassChip(
            modifier = modifier,
            text = text,
            material = HaloGlassMaterials.Interactive.copy(
                containerColor = HaloGlassColors.Success.copy(alpha = 0.2f),
                contentColor = HaloGlassColors.Success
            )
        )
    }

    @Composable
    fun Warning(modifier: Modifier = Modifier, text: String) {
        HaloGlassChip(
            modifier = modifier,
            text = text,
            material = HaloGlassMaterials.Interactive.copy(
                containerColor = HaloGlassColors.Warning.copy(alpha = 0.2f),
                contentColor = HaloGlassColors.Warning
            )
        )
    }

    @Composable
    fun Danger(modifier: Modifier = Modifier, text: String) {
        HaloGlassChip(
            modifier = modifier,
            text = text,
            material = HaloGlassMaterials.Interactive.copy(
                containerColor = HaloGlassColors.Danger.copy(alpha = 0.2f),
                contentColor = HaloGlassColors.Danger
            )
        )
    }

    @Composable
    fun Info(modifier: Modifier = Modifier, text: String) {
        HaloGlassChip(
            modifier = modifier,
            text = text,
            material = HaloGlassMaterials.Interactive.copy(
                containerColor = HaloGlassColors.Info.copy(alpha = 0.2f),
                contentColor = HaloGlassColors.Info
            )
        )
    }

    @Composable
    fun Accent(modifier: Modifier = Modifier, text: String) {
        HaloGlassChip(
            modifier = modifier,
            text = text,
            material = HaloGlassMaterials.Interactive.copy(
                containerColor = HaloGlassColors.Accent.copy(alpha = 0.2f),
                contentColor = HaloGlassColors.Accent
            )
        )
    }
}
