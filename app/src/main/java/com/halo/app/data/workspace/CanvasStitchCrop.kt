package com.halo.app.data.workspace

import kotlin.math.roundToInt

data class CanvasStitchCrop(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Source rectangle for aspect-safe center crop (only used by HERO layout). */
object CanvasStitchCropping {
    fun centered(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): CanvasStitchCrop {
        require(sourceWidth > 0 && sourceHeight > 0 && targetWidth > 0 && targetHeight > 0)
        if (sourceWidth.toLong() * targetHeight > sourceHeight.toLong() * targetWidth) {
            val width = (sourceHeight.toDouble() * targetWidth / targetHeight)
                .roundToInt().coerceIn(1, sourceWidth)
            val left = (sourceWidth - width) / 2
            return CanvasStitchCrop(left, 0, left + width, sourceHeight)
        }
        val height = (sourceWidth.toDouble() * targetHeight / targetWidth)
            .roundToInt().coerceIn(1, sourceHeight)
        val top = (sourceHeight - height) / 2
        return CanvasStitchCrop(0, top, sourceWidth, top + height)
    }
}
