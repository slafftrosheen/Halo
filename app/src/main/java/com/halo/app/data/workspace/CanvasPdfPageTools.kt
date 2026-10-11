package com.halo.app.data.workspace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** A single PDF page becomes a new bitmap. Original document bytes never change. */
object CanvasPdfPageTools {
    suspend fun pageCount(context: Context, file: WorkspaceFile): Int = withContext(Dispatchers.IO) {
        requirePdf(file)
        val fd = requireNotNull(context.contentResolver.openFileDescriptor(file.uri, "r")) {
            "PDF file is unavailable."
        }
        fd.use { handle -> PdfRenderer(handle).use { renderer -> renderer.pageCount } }
    }

    suspend fun preview(context: Context, file: WorkspaceFile, page: Int): Bitmap =
        render(context, file, page, preview = true)

    suspend fun export(context: Context, file: WorkspaceFile, page: Int): ByteArray =
        withContext(Dispatchers.IO) {
            val bitmap = render(context, file, page, preview = false)
            try {
                coroutineContext.ensureActive()
                val stream = ByteArrayOutputStream()
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                val bytes = stream.toByteArray()
                require(bytes.size in 1..20_000_000) { "PDF page image exceeds 20 MB." }
                coroutineContext.ensureActive()
                bytes
            } finally { bitmap.recycle() }
        }

    private fun requirePdf(file: WorkspaceFile) {
        require(file.mimeType == "application/pdf")
        require(file.availability in setOf(WorkspaceFileAvailability.AVAILABLE,
            WorkspaceFileAvailability.OWNED_LOCAL)) { "PDF source is not available." }
    }

    private suspend fun render(context: Context, file: WorkspaceFile, page: Int, preview: Boolean): Bitmap =
        withContext(Dispatchers.IO) {
            requirePdf(file)
            val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(file.uri, "r")) {
                "PDF file is unavailable."
            }
            descriptor.use { fd ->
                PdfRenderer(fd).use { pdf ->
                    require(page in 1..pdf.pageCount) { "Page number is outside this PDF." }
                    coroutineContext.ensureActive()
                    pdf.openPage(page - 1).use { pdfPage ->
                        val (width, height) = CanvasPdfPageGeometry.dimensions(
                            pdfPage.width, pdfPage.height, if (preview) 640 else 2048)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            coroutineContext.ensureActive()
                            bitmap
                        } catch (failure: Throwable) {
                            bitmap.recycle()
                            throw failure
                        }
                    }
                }
            }
        }
}
