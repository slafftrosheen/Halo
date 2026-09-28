package com.halo.app.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.min

@Composable
fun HaloGlyphRenderer(
    glyph: HaloGlyph,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val pixelData = HaloGlyphs.get(glyph)
    Canvas(modifier = modifier) {
        val gridWidth = 16
        val gridHeight = 16
        val cellSize = min(size.width, size.height) / gridWidth
        val offsetX = (size.width - (cellSize * gridWidth)) / 2f
        val offsetY = (size.height - (cellSize * gridHeight)) / 2f

        if (cellSize >= 2.35f) {
            drawPixelMatrix(pixelData, tint, cellSize, offsetX, offsetY)
        } else {
            drawCompactSilhouette(pixelData, tint, cellSize, offsetX, offsetY)
        }
    }
}

/**
 * Large glyphs should visibly be made from pixels. A small optical inset keeps
 * neighbouring cells distinct without turning curves into a dotted outline.
 */
private fun DrawScope.drawPixelMatrix(
    pixelData: Array<String>,
    tint: Color,
    cellSize: Float,
    offsetX: Float,
    offsetY: Float,
) {
    val inset = (cellSize * 0.075f).coerceAtLeast(0.35f)
    val pixelSize = (cellSize - inset * 2f).coerceAtLeast(1f)
    val radius = CornerRadius((cellSize * 0.16f).coerceAtMost(1.6f))
    for (y in 0 until 16) {
        val row = pixelData.getOrNull(y) ?: continue
        for (x in 0 until 16) {
            if (!row.getOrNull(x).isEmptyPixel()) {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(offsetX + x * cellSize + inset, offsetY + y * cellSize + inset),
                    size = Size(pixelSize, pixelSize),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/** Compact navigation glyphs read better as connected silhouettes than as sub-pixel dots. */
private fun DrawScope.drawCompactSilhouette(
    pixelData: Array<String>,
    tint: Color,
    cellSize: Float,
    offsetX: Float,
    offsetY: Float,
) {
    val overlap = cellSize * 0.10f
    val cornerRadius = CornerRadius(cellSize * 0.14f)
    for (y in 0 until 16) {
        val row = pixelData.getOrNull(y) ?: continue
        var x = 0
        while (x < 16) {
            while (x < 16 && row.getOrNull(x).isEmptyPixel()) x++
            if (x >= 16) break
            val start = x
            while (x < 16 && !row.getOrNull(x).isEmptyPixel()) x++
            drawRoundRect(
                color = tint,
                topLeft = Offset(offsetX + start * cellSize - overlap, offsetY + y * cellSize - overlap),
                size = Size((x - start) * cellSize + overlap * 2f, cellSize + overlap * 2f),
                cornerRadius = cornerRadius,
            )
        }
    }
}

private fun Char?.isEmptyPixel(): Boolean = this == null || this == '.' || this == ' '
