package com.halo.app.data.studio

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.floor

enum class StudioShapeOperation { WELD, TRIM, INTERSECT }

internal fun StudioLayer.canCombineShape(): Boolean = visible && !locked && opacity > 0 && color ushr 24 > 0 &&
    kind in setOf(StudioLayerKind.RECTANGLE, StudioLayerKind.ELLIPSE) && mask == null && vector?.outlineOnly != true

internal fun StudioDocument.combineShapes(command: StudioCommand.CombineShapes): StudioDocument {
    val first = requireNotNull(layers.find { it.id == command.targetId && it.canCombineShape() })
    val second = requireNotNull(layers.find { it.id == command.otherId && it.canCombineShape() })
    require(first.id != second.id)
    require(!command.keepSources || layers.size < StudioDocument.MAX_LAYERS)
    val path = documentShape(first)
    val operation = when (command.operation) {
        StudioShapeOperation.WELD -> Path.Op.UNION
        StudioShapeOperation.TRIM -> Path.Op.DIFFERENCE
        StudioShapeOperation.INTERSECT -> Path.Op.INTERSECT
    }
    check(path.op(documentShape(second), operation)) { "The shapes could not be combined" }
    require(!path.isEmpty) { "This operation leaves no filled area" }
    val limits = RectF().also { path.computeBounds(it, true) }
    val left = floor(limits.left * 1000.0 / width).toInt()
    val top = floor(limits.top * 1000.0 / height).toInt()
    val right = ceil(limits.right * 1000.0 / width).toInt()
    val bottom = ceil(limits.bottom * 1000.0 / height).toInt()
    val bounds = StudioRect(left, top, right - left, bottom - top).also(StudioRect::requireValid)
    val box = bounds.vectorPixels(width, height)
    val contours = buildList {
        val measure = PathMeasure(path, true)
        do {
            if (measure.length <= .01f) continue
            val contour = Path()
            check(measure.getSegment(0f, measure.length, contour, true))
            val fine = contour.approximate(.2f)
            val points = if (fine.size / 3 <= StudioVectorGeometry.MAX_CONTOUR_NODES + 1) fine else contour.approximate(.65f)
            val nodes = (points.indices step 3).map { index ->
                StudioPathNode(((points[index + 1] - box.left) / box.width()).coerceIn(0f, 1f),
                    ((points[index + 2] - box.top) / box.height()).coerceIn(0f, 1f))
            }.fold(mutableListOf<StudioPathNode>()) { unique, point ->
                if (unique.lastOrNull() != point) unique.add(point)
                unique
            }
            if (nodes.lastOrNull() == nodes.firstOrNull()) nodes.removeLastOrNull()
            if (nodes.distinct().size >= 3) {
                require(nodes.size <= StudioVectorGeometry.MAX_CONTOUR_NODES &&
                    size < StudioVectorGeometry.MAX_CONTOURS) { "The result has too many contour points" }
                add(nodes.toList())
            }
        } while (measure.nextContour())
    }
    require(contours.isNotEmpty()) { "This operation leaves no filled area" }
    val result = first.copy(id = if (command.keepSources) command.resultId else first.id,
        name = command.name ?: first.name, bounds = bounds, rotation = 0,
        vector = StudioVectorGeometry(StudioShape.PATH, nodes = contours.first(), additionalContours = contours.drop(1)))
    val next = if (command.keepSources) {
        layers.map { if (it.id in setOf(first.id, second.id)) it.copy(visible = false) else it } + result
    } else layers.mapNotNull {
        when (it.id) {
            first.id -> result
            second.id -> null
            else -> it
        }
    }
    return copy(layers = next, selectedLayerId = result.id)
}

internal fun StudioRect.vectorPixels(width: Int, height: Int) = RectF(
    x * width / 1000f, y * height / 1000f, (x + this.width) * width / 1000f, (y + this.height) * height / 1000f,
)

private fun StudioDocument.documentShape(layer: StudioLayer): Path {
    val rect = layer.bounds.vectorPixels(width, height)
    val path = layer.vector?.path(rect) ?: Path().apply {
        if (layer.kind == StudioLayerKind.ELLIPSE) addOval(rect, Path.Direction.CW) else addRect(rect, Path.Direction.CW)
    }
    path.transform(Matrix().apply { setRotate(layer.rotation.toFloat(), rect.centerX(), rect.centerY()) })
    return path
}
