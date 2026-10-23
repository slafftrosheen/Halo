package com.halo.app.ui.screens.notebook

import kotlin.math.max
import kotlin.math.min

internal data class CanvasInspectionTransform(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f)

/** Clamp against the fitted image, so a very narrow/tall source cannot be panned out of sight. */
internal fun transformCanvasInspection(
    current: CanvasInspectionTransform, width: Float, height: Float, aspect: Float,
    zoom: Float = 1f, panX: Float = 0f, panY: Float = 0f, anchorX: Float = 0f, anchorY: Float = 0f,
): CanvasInspectionTransform {
    if (listOf(width, height, aspect).any { !it.isFinite() || it <= 0f }) return CanvasInspectionTransform()
    val base = current.scale.takeIf { it.isFinite() && it > 0 }?.coerceIn(1f, 4f) ?: 1f
    val factor = zoom.takeIf { it.isFinite() && it > 0 } ?: 1f
    val scale = (base * factor).coerceIn(1f, 4f)
    if (scale == 1f) return CanvasInspectionTransform()
    val ratio = scale / base
    val fitWidth = min(width, height * aspect)
    val fitHeight = fitWidth / aspect
    val limitX = max(0f, (fitWidth * scale - width) / 2)
    val limitY = max(0f, (fitHeight * scale - height) / 2)
    fun valid(value: Float) = value.takeIf { it.isFinite() } ?: 0f
    return CanvasInspectionTransform(scale,
        (valid(current.x) * ratio + valid(anchorX) * (1 - ratio) + valid(panX)).coerceIn(-limitX, limitX),
        (valid(current.y) * ratio + valid(anchorY) * (1 - ratio) + valid(panY)).coerceIn(-limitY, limitY))
}
