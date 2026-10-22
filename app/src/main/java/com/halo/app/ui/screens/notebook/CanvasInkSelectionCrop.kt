package com.halo.app.ui.screens.notebook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.sdk.runtime.ink.InkCodec
import com.halo.sdk.runtime.ink.InkSelectionContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Renders only bookmarked sketch strokes into a bounded, transient visual attachment. */
internal object CanvasInkSelectionCropRenderer {
    private const val MAX_DIMENSION = 768
    private const val MAX_BYTES = 4 * 1024 * 1024

    fun renderSketch(encoded: String, selection: InkSelectionContext): ByteArray {
        val document = requireNotNull(InkCodec.decode(encoded)) { "Sketch source is unavailable." }
        val selectedIds = selection.selectedStrokeIds.toSet()
        val strokes = document.strokes.filter { it.strokeId in selectedIds }
        require(strokes.isNotEmpty()) { "Selected sketch marks are unavailable." }

        val bounds = selection.bounds
        val regionAspect = if (document.aspectMilli == 0) 1.4f else
            (document.aspectMilli / 1000f) * (bounds.right - bounds.left) / (bounds.bottom - bounds.top)
        val aspect = regionAspect.coerceIn(0.125f, 8f)
        val width = if (aspect >= 1f) MAX_DIMENSION else (MAX_DIMENSION * aspect).toInt().coerceAtLeast(96)
        val height = if (aspect <= 1f) MAX_DIMENSION else (MAX_DIMENSION / aspect).toInt().coerceAtLeast(96)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            drawSelection(canvas, width, height, document, selection)
            return encode(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun renderPage(context: Context, file: WorkspaceFile, pageNumber: Int,
        encodedPageInk: String, selection: InkSelectionContext): ByteArray = withContext(Dispatchers.IO) {
        require(selection.workspaceObjectId == file.objectId.value && selection.pageId == "page-$pageNumber") {
            "Selection does not match the source page."
        }
        val pageInk = requireNotNull(InkCodec.decode(encodedPageInk)) { "Page annotations are unavailable." }
        val selectedIds = selection.selectedStrokeIds.toSet()
        require(pageInk.strokes.any { it.strokeId in selectedIds }) { "Selected page marks are unavailable." }
        val rendered = requireNotNull(renderInkPage(context, file, pageNumber, MAX_DIMENSION)) {
            "The selected source page is unavailable."
        }
        try {
            val background = rendered.bitmap
            val bounds = selection.bounds
            val left = (background.width * bounds.left / 1000f).toInt().coerceIn(0, background.width - 1)
            val top = (background.height * bounds.top / 1000f).toInt().coerceIn(0, background.height - 1)
            val right = kotlin.math.ceil(background.width * bounds.right / 1000f).toInt().coerceIn(left + 1, background.width)
            val bottom = kotlin.math.ceil(background.height * bounds.bottom / 1000f).toInt().coerceIn(top + 1, background.height)
            val crop = Bitmap.createBitmap(background, left, top, right - left, bottom - top)
            try {
                drawSelection(Canvas(crop), crop.width, crop.height, pageInk, selection)
                encode(crop)
            } finally { if (crop !== background) crop.recycle() }
        } finally { if (!rendered.bitmap.isRecycled) rendered.bitmap.recycle() }
    }

    private fun drawSelection(canvas: Canvas, width: Int, height: Int,
        document: com.halo.sdk.runtime.ink.InkDocument, selection: InkSelectionContext) {
        val bounds = selection.bounds
        val selectedIds = selection.selectedStrokeIds.toSet()
        val strokes = document.strokes.filter { it.strokeId in selectedIds }
        require(strokes.isNotEmpty()) { "Selected marks are unavailable." }
        canvas.save()
        canvas.clipRect(RectF(0f, 0f, width.toFloat(), height.toFloat()))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(25, 32, 38)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val baseWidth = minOf(width, height) * 0.008f
        fun pxX(x: Int) = (x - bounds.left) * width.toFloat() / (bounds.right - bounds.left)
        fun pxY(y: Int) = (y - bounds.top) * height.toFloat() / (bounds.bottom - bounds.top)
        for (stroke in strokes) {
            val points = stroke.points
            if (points.size == 1) {
                val point = points.first()
                paint.style = Paint.Style.FILL
                val pressure = point.pressure?.let { 0.3f + 1.4f * it / 1000f } ?: 1f
                canvas.drawCircle(pxX(point.x), pxY(point.y), (baseWidth * pressure / 2f).coerceAtLeast(1f), paint)
                paint.style = Paint.Style.STROKE
            } else {
                for (index in 1 until points.size) {
                    val before = points[index - 1].pressure
                    val after = points[index].pressure
                    val pressure = if (before == null || after == null) 1f else
                        0.3f + 1.4f * (before + after) / 2000f
                    paint.strokeWidth = (baseWidth * pressure).coerceAtLeast(1f)
                    val segment = Path().apply {
                        moveTo(pxX(points[index - 1].x), pxY(points[index - 1].y))
                        lineTo(pxX(points[index].x), pxY(points[index].y))
                    }
                    canvas.drawPath(segment, paint)
                }
            }
        }
        canvas.restore()
    }

    private fun encode(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not render the selected region." }
        output.toByteArray().also { require(it.size in 1..MAX_BYTES) { "Selected region exceeds the image limit." } }
    }
}
