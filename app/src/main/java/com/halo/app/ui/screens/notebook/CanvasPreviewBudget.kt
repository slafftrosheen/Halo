package com.halo.app.ui.screens.notebook

/** Screen-pixel geometry only: this policy never changes a saved Canvas node. */
internal data class CanvasPreviewCandidate(
    val id: String, val x: Float, val y: Float, val width: Float, val height: Float, val zIndex: Int,
)

internal const val CANVAS_THUMBNAIL_LIMIT = 16

/** Culling alone does not bound a board containing hundreds of overlapping images. */
internal fun admittedCanvasPreviews(
    candidates: List<CanvasPreviewCandidate>, viewportWidth: Float, viewportHeight: Float, focusedId: String?,
): Set<String> {
    if (!viewportWidth.isFinite() || !viewportHeight.isFinite() || viewportWidth <= 0 || viewportHeight <= 0)
        return emptySet()
    fun distance(item: CanvasPreviewCandidate): Float {
        val dx = item.x + item.width / 2 - viewportWidth / 2
        val dy = item.y + item.height / 2 - viewportHeight / 2
        return dx * dx + dy * dy
    }
    return candidates.asSequence().filter {
        it.x.isFinite() && it.y.isFinite() && it.width.isFinite() && it.height.isFinite() &&
            it.width > 0 && it.height > 0 && it.x < viewportWidth && it.y < viewportHeight &&
            it.x + it.width > 0 && it.y + it.height > 0
    }.sortedWith(compareBy<CanvasPreviewCandidate> { it.id != focusedId }
        .thenBy(::distance).thenByDescending { it.zIndex }.thenBy { it.id })
        .distinctBy { it.id }.take(CANVAS_THUMBNAIL_LIMIT).map { it.id }.toSet()
}
