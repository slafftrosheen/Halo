package com.halo.app.ui.screens.notebook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.halo.sdk.runtime.ink.InkCodec
import com.halo.sdk.runtime.ink.InkSelectionContext
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
                    paint.color = android.graphics.Color.rgb(25, 32, 38)
                    paint.alpha = 255
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
            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not render selected sketch marks." }
                val bytes = output.toByteArray()
                require(bytes.size in 1..MAX_BYTES) { "Selected sketch crop exceeds the image limit." }
                bytes
            }
        } finally {
            bitmap.recycle()
        }
    }
}
