package com.halo.app.ui.glass.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloGlassTokens
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme

/**
 * Bottom sheet style glass container.
 */
@Composable
fun HaloGlassSheet(
    modifier: Modifier = Modifier,
    material: HaloGlassMaterial = HaloGlassMaterials.Elevated,
    shape: Shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    content: @Composable BoxScope.() -> Unit
) {
    HaloGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        material = material,
        shape = shape,
        contentPadding = 0.dp,
    ) {
        Box(Modifier.padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 20.dp),
            content = content)
    }
}

/**
 * Predefined glass sheet variants.
 */
object HaloGlassSheet {
    @Composable
    fun Standard(
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        HaloGlassSheet(
            modifier = modifier,
            material = HaloGlassMaterials.Standard,
            content = content
        )
    }

    @Composable
    fun Background(
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        HaloGlassSheet(
            modifier = modifier,
            material = HaloGlassMaterials.Background,
            content = content
        )
    }

    @Composable
    fun Strong(
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        HaloGlassSheet(
            modifier = modifier,
            material = HaloGlassMaterials.Strong,
            content = content
        )
    }
}

/**
 * Decorative handle for glass sheets.
 */
@Composable
fun HaloGlassSheetHandle(
    modifier: Modifier = Modifier,
    width: Dp = 36.dp,
    height: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.outlineVariant
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .background(color, shape = HaloGlassTokens.pillShape)
    )
}
