package com.halo.app.ui.screens.notebook

import androidx.compose.ui.geometry.Rect
import com.halo.sdk.runtime.ink.InkSelectionBounds

/** Legacy ink has no saved aspect; this is a shared display fallback, not recovered geometry. */
internal fun inkSelectionAspect(aspectMilli: Int, bounds: InkSelectionBounds): Float {
    val sourceAspect = if (aspectMilli == 0) 1.4f else aspectMilli / 1000f
    require(sourceAspect > 0f && sourceAspect.isFinite())
    return sourceAspect * (bounds.right - bounds.left) / (bounds.bottom - bounds.top)
}

/** Fit the actual region into its viewport; extreme selections get margins instead of stretching. */
internal fun inkSelectionFrame(width: Float, height: Float, aspectMilli: Int,
    bounds: InkSelectionBounds): Rect {
    require(width.isFinite() && height.isFinite() && width >= 0f && height >= 0f)
    if (width == 0f || height == 0f) return Rect.Zero
    val aspect = inkSelectionAspect(aspectMilli, bounds)
    val fittedWidth = minOf(width, height * aspect)
    val fittedHeight = fittedWidth / aspect
    return Rect((width - fittedWidth) / 2f, (height - fittedHeight) / 2f,
        (width + fittedWidth) / 2f, (height + fittedHeight) / 2f)
}

/** Original document rectangle whose selected bounds map exactly onto the fitted region. */
internal fun inkSelectionSourceRect(frame: Rect, bounds: InkSelectionBounds): Rect {
    val sourceWidth = frame.width * 1000f / (bounds.right - bounds.left)
    val sourceHeight = frame.height * 1000f / (bounds.bottom - bounds.top)
    val left = frame.left - bounds.left * sourceWidth / 1000f
    val top = frame.top - bounds.top * sourceHeight / 1000f
    return Rect(left, top, left + sourceWidth, top + sourceHeight)
}
