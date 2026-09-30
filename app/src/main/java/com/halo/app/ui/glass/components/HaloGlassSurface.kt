package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.*

/** One opaque, clipped surface; avoids stacked highlight and alpha layers. */
@Composable
fun HaloGlassSurface(
    modifier: Modifier = Modifier,
    material: HaloGlassMaterial = HaloGlassMaterials.Standard,
    shape: Shape = HaloGlassTokens.largeShape,
    contentPadding: Dp = HaloGlassTokens.lg,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = material.containerColor,
        contentColor = material.contentColor,
        border = BorderStroke(1.dp, material.borderColor),
        shadowElevation = material.elevation,
    ) {
        Box(Modifier.padding(contentPadding), content = content)
    }
}
