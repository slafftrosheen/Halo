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
        val cellPadding = cellSize * 0.10f
        val actualCellSize = cellSize - cellPadding
        val cornerRadius = CornerRadius(actualCellSize * 0.25f)

        val offsetX = (size.width - (cellSize * gridWidth)) / 2f
        val offsetY = (size.height - (cellSize * gridHeight)) / 2f

        for (y in 0 until gridHeight) {
            val row = pixelData.getOrNull(y) ?: continue
            for (x in 0 until gridWidth) {
                val char = row.getOrNull(x) ?: continue
                if (char != '.' && char != ' ') {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(
                            offsetX + x * cellSize + cellPadding / 2f,
                            offsetY + y * cellSize + cellPadding / 2f
                        ),
                        size = Size(actualCellSize, actualCellSize),
                        cornerRadius = cornerRadius
                    )
                }
            }
        }
    }
}
