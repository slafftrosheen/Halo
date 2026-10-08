package com.halo.app.study

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** PDF visual support only. One selected page per request; no OCR or text extraction. */
interface StudyPdfPages {
    suspend fun pageCount(file: WorkspaceFile): Int
    suspend fun render(file: WorkspaceFile, page: Int): PreparedImageInput
}
class StudyPdfPageRenderer(private val context: Context, private val preparer: ImageInputPreparer) : StudyPdfPages {
    override suspend fun pageCount(file: WorkspaceFile): Int = withContext(Dispatchers.IO) {
        require(file.mimeType == "application/pdf")
        context.contentResolver.openFileDescriptor(file.uri, "r")!!.use { fd -> PdfRenderer(fd).use { it.pageCount } }
    }
    override suspend fun render(file: WorkspaceFile, page: Int): PreparedImageInput = withContext(Dispatchers.IO) {
        require(file.mimeType == "application/pdf")
        val content = context.contentResolver.openFileDescriptor(file.uri, "r")!!.use { fd ->
            PdfRenderer(fd).use { renderer ->
                require(page in 1..renderer.pageCount)
                renderer.openPage(page - 1).use { selected ->
                    val (width, height) = boundedSize(selected.width, selected.height)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        selected.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val stream = ByteArrayOutputStream()
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                        MediaAssetContent(stream.toByteArray(), "image/png")
                    } finally { bitmap.recycle() }
                }
            }
        }
        preparer.prepare(content, null, ImageInputLimits())
    }
    companion object {
        internal fun boundedSize(width: Int, height: Int): Pair<Int, Int> {
            require(width > 0 && height > 0)
            val ratio = minOf(1.0, 1600.0 / maxOf(width, height))
            return (width * ratio).toInt().coerceAtLeast(1) to (height * ratio).toInt().coerceAtLeast(1)
        }
    }

}
