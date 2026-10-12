package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Measure content in board coordinates, then scale its complete visual and input layer. */
@Composable
internal fun CanvasCardFrame(
    width: Float,
    height: Float,
    scale: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.size((width * scale).dp, (height * scale).dp)) {
        Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
            .graphicsLayer(scaleX = scale, scaleY = scale, transformOrigin = TransformOrigin(0f, 0f))
            .size(width.dp, height.dp)) { content() }
    }
}
