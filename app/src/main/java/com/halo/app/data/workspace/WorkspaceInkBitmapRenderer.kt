package com.halo.app.data.workspace

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.halo.sdk.runtime.ink.InkDocument
import com.halo.sdk.runtime.ink.InkLimits

/** Android raster output for portable Ink; shared by Studio and source-linked PDF page images. */
object WorkspaceInkBitmapRenderer {
    fun draw(canvas: Canvas, document: InkDocument, rect: RectF, paint: Paint, baseWidth: Float) {
        InkLimits.requireValid(document)
        require(baseWidth.isFinite() && baseWidth > 0)
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        fun x(point: com.halo.sdk.runtime.ink.InkPoint) = rect.left + point.x * rect.width() / 1000
        fun y(point: com.halo.sdk.runtime.ink.InkPoint) = rect.top + point.y * rect.height() / 1000
        fun width(point: com.halo.sdk.runtime.ink.InkPoint): Float =
            (baseWidth *
                (point.pressure?.let { .3f + 1.4f * it / 1000f } ?: 1f)).coerceAtLeast(.75f)
        for (stroke in document.strokes) {
            val points = stroke.points
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x(points.first()), y(points.first()), width(points.first()) / 2, paint)
            paint.style = Paint.Style.STROKE
            var startX = x(points.first()); var startY = y(points.first())
            for (index in 1 until points.size) {
                val before = points[index - 1]; val current = points[index]
                val endX = if (index == points.lastIndex) x(current) else (x(before) + x(current)) / 2
                val endY = if (index == points.lastIndex) y(current) else (y(before) + y(current)) / 2
                paint.strokeWidth = (width(before) + width(current)) / 2
                val path = Path().apply { moveTo(startX, startY); quadTo(x(before), y(before), endX, endY) }
                canvas.drawPath(path, paint)
                startX = endX; startY = endY
            }
        }
    }
}
