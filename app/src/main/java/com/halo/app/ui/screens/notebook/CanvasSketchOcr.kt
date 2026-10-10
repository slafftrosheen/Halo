package com.halo.app.ui.screens.notebook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.halo.app.data.notebook.CanvasSketchCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device Latin-script handwriting *best-effort OCR*. This is not a handwriting
 * recognition model; unreadable strokes may return no text. Never replaces the drawing.
 */
internal object CanvasSketchOcr {
    suspend fun recognize(encoded: String): String {
        val strokes = CanvasSketchCodec.decode(encoded)
        require(strokes.isNotEmpty())
        var owned: Bitmap? = null
        try {
        val bitmap = withContext(Dispatchers.Default) {
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            owned = bitmap
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    strokeWidth = 6f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    style = Paint.Style.STROKE
                }
                strokes.forEach { stroke ->
                    val path = android.graphics.Path()
                    stroke.forEachIndexed { index, point ->
                        val x = point.x * 1.024f
                        val y = point.y * 1.024f
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, pen)
                }
            }
            bitmap
        }
        owned = null
        return recognizeBitmap(bitmap)
        } finally { owned?.takeUnless { it.isRecycled }?.recycle() }
    }
    suspend fun recognizeSource(context: Context, file: com.halo.app.data.workspace.WorkspaceFile): String {
        val page = renderInkPage(context, file, 1) ?: error("Preview unavailable")
        return recognizeBitmap(page.bitmap)
    }
    private suspend fun recognizeBitmap(bitmap: Bitmap): String {
        val recognizer = try { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
            catch (failure: Throwable) { bitmap.recycle(); throw failure }
        try {
            val result = withContext(NonCancellable) {
                suspendCancellableCoroutine<String> { continuation ->
                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.text.trim()) }
                        .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                        .addOnCanceledListener { continuation.cancel() }
                }
            }
            currentCoroutineContext().ensureActive()
            return result.take(12000)
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }
}
