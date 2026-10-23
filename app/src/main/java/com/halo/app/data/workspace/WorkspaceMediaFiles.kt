package com.halo.app.data.workspace

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Deterministic adapters onto canonical image/PDF renderers; all writes publish new managed files. */
class WorkspaceMediaFiles(private val context: Context, private val files: WorkspaceFileRepository,
    private val versions: WorkspaceFileVersions, val receipts: WorkspaceFileReceipts) {
    suspend fun transform(source: WorkspaceFileVersion, recipe: CanvasImageRecipe): WorkspaceFile {
        val file = image(source)
        return transformPinned(file, recipe)
    }
    private suspend fun image(source: WorkspaceFileVersion): WorkspaceFile {
        require(versions.current(source).mimeType.startsWith("image/"))
        return versions.pin(source).also { require(it.mimeType.startsWith("image/")) }
    }
    private suspend fun transformPinned(file: WorkspaceFile, recipe: CanvasImageRecipe,
        jobId: String? = null, index: Int = 0): WorkspaceFile {
        val source = WorkspaceFileVersion(file.objectId.value, requireNotNull(file.version))
        val bytes = CanvasImageTools.export(context, file, recipe)
        val metadata = JSONObject().put("operation", "file-image-transform").put("sourceVersion", source.version)
            .put("turns", recipe.quarterTurns).put("mirror", recipe.mirrorHorizontal).put("square", recipe.squareCrop)
            .put("edge", recipe.maxEdge).put("format", recipe.format.name)
            .put("crop", recipe.crop?.let { JSONObject().put("x", it.x).put("y", it.y).put("width", it.width).put("height", it.height) } ?: JSONObject.NULL)
            .put("batchReceiptId", jobId ?: JSONObject.NULL).toString()
        return files.createManagedFile(file.displayName.substringBeforeLast('.', file.displayName).take(90) + "." + recipe.format.extension,
            recipe.format.mimeType, bytes, source.objectId, source.version, metadata,
            published = { output -> if (jobId != null) receipts.saved(jobId, index, source, output) },
            validateSource = { versions.current(source) })
    }
    suspend fun batch(title: String, sources: List<WorkspaceFileVersion>, recipe: CanvasImageRecipe,
        onStarted: (WorkspaceBatchReceipt) -> Unit = {}): WorkspaceBatchReceipt {
        require(sources.size in 1..6 && sources.map { it.objectId }.distinct().size == sources.size)
        val job = receipts.create(title, sources)
        try {
            onStarted(job)
            for ((index, source) in sources.withIndex()) {
                try { transformPinned(image(source), recipe, job.id, index) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { receipts.failed(job.id, index) }
            }
            return receipts.finish(job.id)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { runCatching { receipts.finish(job.id, cancelled = true) } }
            throw cancelled
        } finally { receipts.release(job.id) }
    }
    suspend fun collage(sources: List<WorkspaceFileVersion>, recipe: CanvasStitchRecipe): WorkspaceFile {
        require(sources.size in 2..6 && sources.map { it.objectId }.distinct().size == sources.size)
        val pinned = sources.map { image(it) }
        val exact = pinned.map { WorkspaceFileVersion(it.objectId.value, requireNotNull(it.version)) }
        val bytes = CanvasImageStitcher.export(context, pinned.map { CanvasStitchSource.WorkspaceImage(it) }, recipe)
        val metadata = JSONObject().put("operation", "file-image-collage").put("mode", recipe.mode.name).put("gap", recipe.gap)
            .put("edge", recipe.maxEdge).put("format", recipe.format.name).put("background", recipe.background.name)
            .put("sources", JSONArray().apply { exact.forEach { put(JSONObject().put("id", it.objectId).put("version", it.version)) } }).toString()
        return files.createManagedFile("Collage." + recipe.format.extension, recipe.format.mimeType, bytes,
            exact.first().objectId, exact.first().version, metadata) { exact.forEach { versions.current(it) } }
    }
    suspend fun previewImage(source: WorkspaceFileVersion, recipe: CanvasImageRecipe): Bitmap =
        CanvasImageTools.preview(context, versions.current(source), recipe)
    suspend fun previewCollage(sources: List<WorkspaceFileVersion>, recipe: CanvasStitchRecipe): Bitmap {
        require(sources.size in 2..6 && sources.map { it.objectId }.distinct().size == sources.size)
        return CanvasImageStitcher.preview(context, sources.map { CanvasStitchSource.WorkspaceImage(versions.current(it)) }, recipe)
    }
    suspend fun pdfPageCount(source: WorkspaceFileVersion): Int = CanvasPdfPageTools.pageCount(context, pdf(source))
    suspend fun previewPdfPage(source: WorkspaceFileVersion, page: Int): Bitmap = CanvasPdfPageTools.preview(context, pdf(source), page)
    private suspend fun pdf(source: WorkspaceFileVersion): WorkspaceFile = versions.current(source).also { require(it.mimeType == "application/pdf") }
    /** Explicit raster extraction, never advertised as a text-preserving PDF edit. */
    suspend fun extractPdfPageImage(source: WorkspaceFileVersion, page: Int): WorkspaceFile {
        pdf(source)
        val file = versions.pin(source)
        val pinned = WorkspaceFileVersion(file.objectId.value, requireNotNull(file.version))
        val bytes = CanvasPdfPageTools.export(context, file, page)
        val metadata = JSONObject().put("operation", "file-pdf-page-image").put("page", page)
            .put("sourceVersion", pinned.version).put("rasterized", true).put("nativePdfEditing", false).toString()
        return files.createManagedFile(file.displayName.substringBeforeLast('.', file.displayName).take(80) + "-page-$page.png",
            "image/png", bytes, pinned.objectId, pinned.version, metadata) { versions.current(pinned) }
    }
}
