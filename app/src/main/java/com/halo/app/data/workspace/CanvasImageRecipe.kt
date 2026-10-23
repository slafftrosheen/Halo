package com.halo.app.data.workspace

import kotlin.math.roundToInt

/** Previewable offline image-edit request; source Workspace assets remain immutable. */
enum class CanvasImageFormat(val mimeType: String, val extension: String) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
}

/** Crop coordinates are thousandths of the original source rectangle. */
data class CanvasCropPixels(val left: Int, val top: Int, val width: Int, val height: Int)

data class CanvasImageCrop(val x: Int, val y: Int, val width: Int, val height: Int) {
    init { require(x in 0..999 && y in 0..999 && width in 1..1000 && height in 1..1000 &&
        x + width <= 1000 && y + height <= 1000) }
    fun pixels(sourceWidth: Int, sourceHeight: Int): CanvasCropPixels {
        require(sourceWidth > 0 && sourceHeight > 0)
        val left = (x.toLong() * sourceWidth / 1000).toInt().coerceIn(0, sourceWidth - 1)
        val top = (y.toLong() * sourceHeight / 1000).toInt().coerceIn(0, sourceHeight - 1)
        val right = (((x + width).toLong() * sourceWidth + 999) / 1000).toInt().coerceIn(left + 1, sourceWidth)
        val bottom = (((y + height).toLong() * sourceHeight + 999) / 1000).toInt().coerceIn(top + 1, sourceHeight)
        return CanvasCropPixels(left, top, right - left, bottom - top)
    }
}

data class CanvasImageRecipe(
    val quarterTurns: Int = 0,
    val mirrorHorizontal: Boolean = false,
    val squareCrop: Boolean = false,
    val maxEdge: Int = 2048,
    val format: CanvasImageFormat = CanvasImageFormat.PNG,
    val crop: CanvasImageCrop? = null,
) {
    init {
        require(quarterTurns in 0..3)
        require(!squareCrop || crop == null)
        require(maxEdge in setOf(512, 1024, 2048, 4096))
    }
    /** Aspect-safe bounds, with no unintentional upscaling. */
    fun outputDimensions(width: Int, height: Int, previewLimit: Int? = null): Pair<Int, Int> {
        require(width in 1..32768 && height in 1..32768)
        val side = minOf(width, height)
        val pixels = crop?.pixels(width, height)
        val croppedWidth = pixels?.width ?: if (squareCrop) side else width
        val croppedHeight = pixels?.height ?: if (squareCrop) side else height
        val rotatedWidth = if (quarterTurns % 2 != 0) croppedHeight else croppedWidth
        val rotatedHeight = if (quarterTurns % 2 != 0) croppedWidth else croppedHeight
        val bound = minOf(maxEdge, previewLimit ?: maxEdge)
        val scale = minOf(1.0, bound.toDouble() / maxOf(rotatedWidth, rotatedHeight))
        return (rotatedWidth * scale).roundToInt().coerceAtLeast(1) to
            (rotatedHeight * scale).roundToInt().coerceAtLeast(1)
    }
}
