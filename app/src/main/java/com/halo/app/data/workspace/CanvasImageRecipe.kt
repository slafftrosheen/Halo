package com.halo.app.data.workspace

import kotlin.math.roundToInt

/** Previewable offline image-edit request; source Workspace assets remain immutable. */
enum class CanvasImageFormat(val mimeType: String, val extension: String) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
}

data class CanvasImageRecipe(
    val quarterTurns: Int = 0,
    val mirrorHorizontal: Boolean = false,
    val squareCrop: Boolean = false,
    val maxEdge: Int = 2048,
    val format: CanvasImageFormat = CanvasImageFormat.PNG,
) {
    init {
        require(quarterTurns in 0..3)
        require(maxEdge in setOf(512, 1024, 2048, 4096))
    }
    /** Aspect-safe bounds, with no unintentional upscaling. */
    fun outputDimensions(width: Int, height: Int, previewLimit: Int? = null): Pair<Int, Int> {
        require(width in 1..32768 && height in 1..32768)
        val side = minOf(width, height)
        val croppedWidth = if (squareCrop) side else width
        val croppedHeight = if (squareCrop) side else height
        val rotatedWidth = if (quarterTurns % 2 != 0) croppedHeight else croppedWidth
        val rotatedHeight = if (quarterTurns % 2 != 0) croppedWidth else croppedHeight
        val bound = minOf(maxEdge, previewLimit ?: maxEdge)
        val scale = minOf(1.0, bound.toDouble() / maxOf(rotatedWidth, rotatedHeight))
        return (rotatedWidth * scale).roundToInt().coerceAtLeast(1) to
            (rotatedHeight * scale).roundToInt().coerceAtLeast(1)
    }
}
