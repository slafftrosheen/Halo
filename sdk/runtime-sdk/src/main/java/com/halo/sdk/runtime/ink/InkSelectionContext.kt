package com.halo.sdk.runtime.ink

/** Selection gesture provenance, independent of Android pointer classes. */
enum class InkSelectionOrigin { BOX, LASSO, ACCESSIBILITY }

/** Inclusive, normalized thousandths in the source document's coordinate space. */
data class InkSelectionBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init {
        require(left in 0..1000 && top in 0..1000 && right in 0..1000 && bottom in 0..1000)
        require(left < right && top < bottom) { "Selection region must have nonzero area" }
    }
}

/**
 * Portable reference to a user's explicit ink selection. This stores references and bounds,
 * not stroke content or a rendered crop; the source owner must resolve IDs and revision before use.
 */
data class InkSelectionContext(
    val workspaceObjectId: String,
    val pageId: String?,
    val bounds: InkSelectionBounds,
    val selectedStrokeIds: List<String>,
    val sourceRevision: String,
    val origin: InkSelectionOrigin,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    init {
        require(workspaceObjectId.isNotBlank() && workspaceObjectId.length <= MAX_REFERENCE_LENGTH)
        require(pageId == null || (pageId.isNotBlank() && pageId.length <= MAX_REFERENCE_LENGTH))
        require(selectedStrokeIds.isNotEmpty() && selectedStrokeIds.size <= InkLimits.MAX_STROKES)
        require(selectedStrokeIds.all { it.isNotBlank() && it.length <= MAX_REFERENCE_LENGTH })
        require(selectedStrokeIds.distinct().size == selectedStrokeIds.size) { "Stroke IDs must be unique" }
        require(sourceRevision.isNotBlank() && sourceRevision.length <= MAX_REFERENCE_LENGTH)
        require(createdAtMs >= 0 && updatedAtMs >= createdAtMs)
        require(schemaVersion == CURRENT_SCHEMA_VERSION)
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_REFERENCE_LENGTH = 200
    }
}
