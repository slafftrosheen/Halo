package com.halo.app.data.workspace

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Portable, deterministic layout plan for 2–6 selected Canvas images. */
enum class CanvasStitchMode { HORIZONTAL, VERTICAL, GRID }
enum class CanvasStitchBackground { TRANSPARENT, WHITE }

data class CanvasStitchRecipe(
    val mode: CanvasStitchMode = CanvasStitchMode.GRID,
    val gap: Int = 8,
    val maxEdge: Int = 2048,
    val format: CanvasImageFormat = CanvasImageFormat.PNG,
    val background: CanvasStitchBackground = CanvasStitchBackground.WHITE,
) {
    init {
        require(gap in setOf(0, 8, 24))
        require(maxEdge in setOf(1024, 2048, 4096))
    }
}

data class CanvasImageSize(val width: Int, val height: Int) {
    init { require(width in 1..32768 && height in 1..32768) }
}

data class CanvasStitchPlacement(val x: Int, val y: Int, val width: Int, val height: Int)
data class CanvasStitchPlan(val width: Int, val height: Int, val placements: List<CanvasStitchPlacement>)

/** Layout is calculated independently of Android Bitmap, so it can be regression-tested on JVM. */
object CanvasStitchLayout {
    fun plan(sizes: List<CanvasImageSize>, recipe: CanvasStitchRecipe,
             previewMaxEdge: Int? = null): CanvasStitchPlan {
        require(sizes.size in 2..6) { "Select between 2 and 6 images." }
        val base = sizes.map { size ->
            val ratio = min(1.0, 1024.0 / maxOf(size.width, size.height))
            val w = (size.width * ratio).roundToInt().coerceAtLeast(1)
            val h = (size.height * ratio).roundToInt().coerceAtLeast(1)
            w to h
        }
        val gap = recipe.gap
        val raw: List<CanvasStitchPlacement>
        val width: Int
        val height: Int
        when (recipe.mode) {
            CanvasStitchMode.HORIZONTAL -> {
                width = base.sumOf { it.first } + gap * (base.size - 1)
                height = base.maxOf { it.second }
                var x = 0
                raw = base.map { (w, h) ->
                    CanvasStitchPlacement(x, (height - h) / 2, w, h).also { x += w + gap }
                }
            }
            CanvasStitchMode.VERTICAL -> {
                width = base.maxOf { it.first }
                height = base.sumOf { it.second } + gap * (base.size - 1)
                var y = 0
                raw = base.map { (w, h) ->
                    CanvasStitchPlacement((width - w) / 2, y, w, h).also { y += h + gap }
                }
            }
            CanvasStitchMode.GRID -> {
                val columns = if (base.size <= 4) 2 else 3
                val rows = (base.size + columns - 1) / columns
                val cellWidth = base.maxOf { it.first }
                val cellHeight = base.maxOf { it.second }
                width = columns * cellWidth + gap * (columns - 1)
                height = rows * cellHeight + gap * (rows - 1)
                raw = base.mapIndexed { index, (w, h) ->
                    val col = index % columns
                    val row = index / columns
                    CanvasStitchPlacement(col * (cellWidth + gap) + (cellWidth - w) / 2,
                        row * (cellHeight + gap) + (cellHeight - h) / 2, w, h)
                }
            }
        }
        val cap = min(recipe.maxEdge, previewMaxEdge ?: recipe.maxEdge)
        require(cap in 1..4096)
        val edgeScale = min(1.0, cap.toDouble() / maxOf(width, height))
        val pixelScale = min(1.0, sqrt(12_000_000.0 / (width.toDouble() * height)))
        val scale = min(edgeScale, pixelScale)
        val outW = (width * scale).roundToInt().coerceAtLeast(1)
        val outH = (height * scale).roundToInt().coerceAtLeast(1)
        val cells = raw.map { p ->
            val x1 = (p.x * scale).roundToInt().coerceIn(0, outW - 1)
            val y1 = (p.y * scale).roundToInt().coerceIn(0, outH - 1)
            val x2 = ((p.x + p.width) * scale).roundToInt().coerceIn(x1 + 1, outW)
            val y2 = ((p.y + p.height) * scale).roundToInt().coerceIn(y1 + 1, outH)
            CanvasStitchPlacement(x1, y1, x2 - x1, y2 - y1)
        }
        return CanvasStitchPlan(outW, outH, cells)
    }
}
