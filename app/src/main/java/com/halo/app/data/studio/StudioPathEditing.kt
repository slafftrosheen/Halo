package com.halo.app.data.studio

enum class StudioPathControl { NODE, INCOMING, OUTGOING }

sealed interface StudioNodeEdit {
    data class Move(val index: Int, val control: StudioPathControl, val x: Float, val y: Float) : StudioNodeEdit
    data class Split(val index: Int, val fraction: Float = .5f) : StudioNodeEdit
    data class Delete(val index: Int) : StudioNodeEdit
    data object Reverse : StudioNodeEdit
}

internal fun StudioVectorGeometry.editPath(contourIndex: Int, edit: StudioNodeEdit): StudioVectorGeometry {
    require(shape == StudioShape.PATH)
    require(contourIndex in contours.indices)
    val contour = contours[contourIndex]
    val adjusted = when (edit) {
        is StudioNodeEdit.Move -> contour.toMutableList().apply {
            require(edit.x in 0f..1f && edit.y in 0f..1f)
            require(edit.index in indices)
            val point = this[edit.index]
            val dx = edit.x - point.x
            val dy = edit.y - point.y
            this[edit.index] = when (edit.control) {
                StudioPathControl.NODE -> StudioPathNode(edit.x, edit.y,
                    (point.incomingX + dx).coerceIn(0f, 1f), (point.incomingY + dy).coerceIn(0f, 1f),
                    (point.outgoingX + dx).coerceIn(0f, 1f), (point.outgoingY + dy).coerceIn(0f, 1f))
                StudioPathControl.INCOMING -> point.copy(incomingX = edit.x, incomingY = edit.y)
                StudioPathControl.OUTGOING -> point.copy(outgoingX = edit.x, outgoingY = edit.y)
            }
        }
        is StudioNodeEdit.Split -> splitStudioCurve(contour, closed, edit.index, edit.fraction)
        is StudioNodeEdit.Delete -> {
            require(edit.index in contour.indices && contour.size > (if (closed) 3 else 2))
            contour.filterIndexed { index, _ -> index != edit.index }
        }
        StudioNodeEdit.Reverse -> contour.asReversed().map {
            it.copy(incomingX = it.outgoingX, incomingY = it.outgoingY, outgoingX = it.incomingX, outgoingY = it.incomingY)
        }
    }
    return withContour(contourIndex, adjusted)
}

internal fun StudioVectorGeometry.withContour(contourIndex: Int, points: List<StudioPathNode>): StudioVectorGeometry {
    require(shape == StudioShape.PATH && contourIndex in contours.indices)
    return if (contourIndex == 0) copy(nodes = points) else copy(additionalContours =
        additionalContours.mapIndexed { index, old -> if (index == contourIndex - 1) points else old })
}

private data class Point(val x: Float, val y: Float) {
    fun towards(other: Point, t: Float) = Point((x + (other.x - x) * t).coerceIn(0f, 1f),
        (y + (other.y - y) * t).coerceIn(0f, 1f))
}

/** De Casteljau subdivision keeps the existing cubic outline when a node is inserted. */
private fun splitStudioCurve(nodes: List<StudioPathNode>, closed: Boolean, index: Int, t: Float): List<StudioPathNode> {
    require(nodes.size < StudioVectorGeometry.MAX_CONTOUR_NODES && t in .01f..0.99f)
    require(index in nodes.indices && (closed || index != nodes.lastIndex))
    val end = (index + 1) % nodes.size
    val a = nodes[index]
    val b = nodes[end]
    val p0 = Point(a.x, a.y)
    val p1 = Point(a.outgoingX, a.outgoingY)
    val p2 = Point(b.incomingX, b.incomingY)
    val p3 = Point(b.x, b.y)
    val a1 = p0.towards(p1, t)
    val b1 = p1.towards(p2, t)
    val c1 = p2.towards(p3, t)
    val a2 = a1.towards(b1, t)
    val b2 = b1.towards(c1, t)
    val pivot = a2.towards(b2, t)
    return nodes.toMutableList().apply {
        this[index] = a.copy(outgoingX = a1.x, outgoingY = a1.y)
        this[end] = b.copy(incomingX = c1.x, incomingY = c1.y)
        add(index + 1, StudioPathNode(pivot.x, pivot.y, a2.x, a2.y, b2.x, b2.y))
    }
}
