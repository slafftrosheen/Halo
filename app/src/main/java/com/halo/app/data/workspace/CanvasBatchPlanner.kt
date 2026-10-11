package com.halo.app.data.workspace

/**
 * Pure, Android-independent batch planner for Canvas transformations.
 *
 * Planning never mutates source assets. Execution must create new workspace assets,
 * retain provenance and request confirmation before overwriting or sharing.
 */
enum class CanvasBatchOperation { ROTATE, MIRROR, SQUARE_CROP, RESIZE, CONVERT_PNG, CONVERT_JPEG }

data class CanvasBatchSource(val id: String, val mimeType: String, val available: Boolean)

data class CanvasBatchPlan(
    val accepted: List<CanvasBatchSource>,
    val rejected: Map<String, String>,
    val operation: CanvasBatchOperation,
    val imageRecipe: CanvasImageRecipe,
)

object CanvasBatchPlanner {
    const val MAX_BATCH_SIZE = 32

    fun plan(
        sources: List<CanvasBatchSource>,
        operation: CanvasBatchOperation,
        baseRecipe: CanvasImageRecipe = CanvasImageRecipe(),
    ): CanvasBatchPlan {
        require(sources.size <= MAX_BATCH_SIZE) { "Select at most $MAX_BATCH_SIZE assets." }
        val rejected = linkedMapOf<String, String>()
        val accepted = mutableListOf<CanvasBatchSource>()
        val seen = mutableSetOf<String>()
        sources.forEach { source ->
            val reason = when {
                source.id.isBlank() -> "Missing asset ID"
                !seen.add(source.id) -> "Duplicate asset"
                !source.available -> "Source unavailable"
                source.mimeType !in setOf("image/png", "image/jpeg", "image/webp") -> "Unsupported image type"
                else -> null
            }
            if (reason == null) accepted += source else rejected[source.id] = reason
        }
        val recipe = when (operation) {
            CanvasBatchOperation.ROTATE -> baseRecipe.copy(quarterTurns = (baseRecipe.quarterTurns + 1) % 4)
            CanvasBatchOperation.MIRROR -> baseRecipe.copy(mirrorHorizontal = !baseRecipe.mirrorHorizontal)
            CanvasBatchOperation.SQUARE_CROP -> baseRecipe.copy(squareCrop = true)
            CanvasBatchOperation.RESIZE -> baseRecipe
            CanvasBatchOperation.CONVERT_PNG -> baseRecipe.copy(format = CanvasImageFormat.PNG)
            CanvasBatchOperation.CONVERT_JPEG -> baseRecipe.copy(format = CanvasImageFormat.JPEG)
        }
        return CanvasBatchPlan(accepted, rejected, operation, recipe)
    }
}
