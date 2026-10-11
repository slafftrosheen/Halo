package com.halo.app.data.workspace

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Executes a validated Canvas batch without modifying any input WorkspaceFile. */
object CanvasBatchExecutor {
    data class Result(
        val completedIds: List<String>,
        val failures: Map<String, String>,
    )

    /**
     * [saveNewAsset] must persist a *new* asset and associate it with [source].
     * It must not overwrite source.uri. Cancellation propagates to the caller.
     */
    suspend fun execute(
        context: Context,
        plan: CanvasBatchPlan,
        resolve: suspend (String) -> WorkspaceFile?,
        saveNewAsset: suspend (source: WorkspaceFile, bytes: ByteArray, mimeType: String) -> Unit,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): Result {
        val completed = mutableListOf<String>()
        val failures = linkedMapOf<String, String>()
        failures.putAll(plan.rejected)
        val total = plan.accepted.size
        onProgress(0, total)
        plan.accepted.forEachIndexed { index, entry ->
            coroutineContext.ensureActive()
            try {
                val file = requireNotNull(resolve(entry.id)) { "Source asset no longer exists" }
                require(file.mimeType == entry.mimeType) { "Source type changed since planning" }
                require(file.availability in setOf(
                    WorkspaceFileAvailability.AVAILABLE,
                    WorkspaceFileAvailability.OWNED_LOCAL,
                )) { "Source is unavailable" }
                val bytes = CanvasImageTools.export(context, file, plan.imageRecipe)
                coroutineContext.ensureActive()
                saveNewAsset(file, bytes, plan.imageRecipe.format.mimeType)
                completed += entry.id
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failures[entry.id] = failure.message ?: "Image processing failed"
            }
            onProgress(index + 1, total)
        }
        return Result(completed, failures)
    }
}
