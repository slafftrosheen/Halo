package com.halo.app.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
        val overlap = cellSize * 0.08f
        // Adjacent rows must overlap. The previous 0.78-cell bars rendered every glyph as
        // disconnected scanlines at navigation sizes, making curves and diagonals look striped.
        val barHeight = cellSize + overlap * 2f
        val cornerRadius = CornerRadius(cellSize * 0.18f)

        val offsetX = (size.width - (cellSize * gridWidth)) / 2f
        val offsetY = (size.height - (cellSize * gridHeight)) / 2f

        // Merge adjacent pixels into continuous bars. The original 16x16 artwork
        // remains the source of truth, but connected silhouettes render cleanly at
        // compact sizes and avoid the "beaded" appearance of individual cells.
        for (y in 0 until gridHeight) {
            val row = pixelData.getOrNull(y) ?: continue
            var x = 0
            while (x < gridWidth) {
                while (x < gridWidth && row.getOrNull(x).isEmptyPixel()) x++
                if (x >= gridWidth) break
                val start = x
                while (x < gridWidth && !row.getOrNull(x).isEmptyPixel()) x++
                val cells = x - start
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(
                        offsetX + start * cellSize - overlap,
                        offsetY + y * cellSize - overlap,
                    ),
                    size = Size(cells * cellSize + overlap * 2f, barHeight),
                    cornerRadius = cornerRadius,
                )
            }
        }
    }
}

private fun Char?.isEmptyPixel(): Boolean = this == null || this == '.' || this == ' '
