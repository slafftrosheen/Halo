package com.halo.app.study

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ExtractionPart(val sourceId: String, val sourceVersion: String?, val label: String,
    val locator: String, val method: String, val text: String, val warning: String? = null)

/** Host-only extraction, without inference or network access. */
class StudyTextExtractor(private val context: Context, private val media: MediaAssetResolver,
    private val preparer: ImageInputPreparer) {
    suspend fun image(ref: MediaAssetRef): String {
        val prepared = preparer.prepare(media.open(ref), ref, ImageInputLimits(maxWidth=1600, maxHeight=1600, maxPixels=2560000))
        val bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(prepared.content.bytes, 0, prepared.content.bytes.size) ?: error("Image is unreadable")
        }
        return recognize(bitmap)
    }
    private suspend fun recognize(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            // Native completion owns the bitmap lifetime, even when the review is cancelled.
            val text = withContext(NonCancellable) {
                suspendCancellableCoroutine<String> { continuation ->
                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.text.trim()) }
                        .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                        .addOnCanceledListener { continuation.cancel() }
                }
            }
            currentCoroutineContext().ensureActive()
            return text
        } finally { recognizer.close(); bitmap.recycle() }
    }
    suspend fun document(uri: Uri, mime: String, id: String, version: String?, label: String,
        firstPage: Int = 1, lastPage: Int = firstPage, onPart: (ExtractionPart) -> Unit) = withContext(Dispatchers.IO) {
        if (mime == "application/pdf") {
            require(firstPage > 0 && lastPage >= firstPage && lastPage - firstPage < 8) { "Choose at most eight pages" }
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd -> PdfRenderer(fd).use { pdf ->
                require(lastPage <= pdf.pageCount) { "PDF contains ${pdf.pageCount} pages" }
                for (page in firstPage..lastPage) {
                    ensureActive()
                    try {
                        val bitmap = pdf.openPage(page - 1).use { source ->
                            val (w, h) = StudyPdfPageRenderer.boundedSize(source.width, source.height)
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(Color.WHITE)
                                try { source.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                                catch (error: Throwable) { it.recycle(); throw error }
                            }
                        }
                        val raw = recognize(bitmap)
                        onPart(ExtractionPart(id, version, label, "Page $page of ${pdf.pageCount}", "Local Latin-script OCR",
                            raw.take(StudySourceLimits.DECODED_CHARS / 8),
                            "Selected-page coverage only. Verify against the original. Other scripts are unsupported." +
                                if (raw.length > StudySourceLimits.DECODED_CHARS / 8) " Page text truncated." else ""))
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { onPart(ExtractionPart(id, version, label, "Page $page of ${pdf.pageCount}", "Local OCR", "", error.message ?: "Page extraction failed")) }
                }
            } } ?: error("PDF unavailable; relink the source")
        } else {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                if (mime == DOCX) { val job=currentCoroutineContext(); StudyDocxText.read(input) { job.ensureActive() } } else {
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        require(output.size() + n <= StudySourceLimits.READ_BYTES) { "Text exceeds extraction budget; split the document" }
                        output.write(buffer, 0, n)
                    }
                    Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString().removePrefix("\ufeff")
                        .also { require(it.length <= StudySourceLimits.DECODED_CHARS) { "Text exceeds character budget" } }
                }
            } ?: error("Document unavailable; relink the source")
            onPart(ExtractionPart(id, version, label, "Document text", if (mime == DOCX) "DOCX paragraph extraction" else "UTF-8 text", text,
                if (mime == DOCX) "Text only; layout and embedded objects are not included. Review before using." else null))
        }
    }
    companion object { const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document" }
}
