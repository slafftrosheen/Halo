package com.halo.app.data.workspace

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Pixel-budgeted export geometry. PDF page units are not an output bitmap size. */
object CanvasPdfPageGeometry {
    fun dimensions(width: Int, height: Int, maxEdge: Int = 2048): Pair<Int, Int> {
        require(width in 1..32768 && height in 1..32768)
        require(maxEdge in setOf(640, 1024, 2048))
        val edgeFactor = maxEdge.toDouble() / maxOf(width, height)
        val areaFactor = sqrt(12_000_000.0 / (width.toDouble() * height))
        val scale = min(edgeFactor, areaFactor)
        return (width * scale).roundToInt().coerceIn(1, maxEdge) to
            (height * scale).roundToInt().coerceIn(1, maxEdge)
    }
}
