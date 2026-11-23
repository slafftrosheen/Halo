package com.halo.app.ui.workspace

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.halo.app.data.studio.*
import com.halo.app.language.uiText
import java.math.BigDecimal

private data class PathMarker(val node: Int, val control: StudioPathControl)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioPathControls(vector: StudioVectorGeometry, ratio: Float, onChange: (StudioVectorGeometry) -> Unit) {
    var contour by remember { mutableIntStateOf(0) }
    var node by remember { mutableIntStateOf(0) }
    var control by remember { mutableStateOf(StudioPathControl.NODE) }
    val activeContour = contour.coerceIn(vector.contours.indices)
    val nodes = vector.contours[activeContour]
    val activeNode = node.coerceIn(nodes.indices)
    val marker = PathMarker(activeNode, control)
    val selected = nodes[activeNode]
    val liveNodes by rememberUpdatedState(nodes)
    val liveMarker by rememberUpdatedState(marker)
    val liveVector by rememberUpdatedState(vector)
    val liveChange by rememberUpdatedState(onChange)

    fun update(edit: StudioNodeEdit) {
        liveChange(liveVector.editPath(activeContour, edit))
    }

    val ink = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val selectedInk = MaterialTheme.colorScheme.tertiary
    val border = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(228.dp).pointerInput(activeContour, ratio) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val box = pathEditorBox(Size(size.width.toFloat(), size.height.toFloat()), ratio, 15.dp.toPx())
            val selection = liveMarker
            val currentNodes = liveNodes
            val touch = down.position
            val point = currentNodes[selection.node]
            val anchor = point.location(StudioPathControl.NODE, box)
            val handle = listOf(StudioPathControl.INCOMING, StudioPathControl.OUTGOING)
                .map { it to (point.location(it, box) - touch).getDistance() }
                .filter { (key, distance) -> distance < 20.dp.toPx() && point.location(key, box) != anchor }
                .minByOrNull { it.second }?.first
            val nearest = nearestPathNode(currentNodes, box, touch, 25.dp.toPx())
            val held = if (handle != null) PathMarker(selection.node, handle)
                else nearest?.let { PathMarker(it, if (it == selection.node) selection.control else StudioPathControl.NODE) }
            if (held == null || down.isConsumed) return@awaitEachGesture

            node = held.node
            control = held.control
            down.consume()
            val initial = liveVector
            val grab = currentNodes[held.node].location(held.control, box)
            var moved = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) {
                    if (moved) liveChange(initial)
                    break
                }
                change.consume()
                if (!change.pressed) break
                val delta = change.position - touch
                if (!moved && delta.getDistance() < viewConfiguration.touchSlop) continue
                moved = true
                // Preserve the initial grab offset even when the finger lands beside a small handle.
                val at = grab + delta
                liveChange(initial.editPath(activeContour, StudioNodeEdit.Move(held.node, held.control,
                    ((at.x - box.left) / box.width()).coerceIn(0f, 1f),
                    ((at.y - box.top) / box.height()).coerceIn(0f, 1f))))
            }
        }
    }) {
        val box = pathEditorBox(size, ratio, 15.dp.toPx())
        drawRect(border, Offset(box.left, box.top), Size(box.width(), box.height()), style = Stroke(1.dp.toPx()))
        val path = vector.path(box).asComposePath()
        if (!vector.outlineOnly) drawPath(path, ink.copy(alpha = .16f))
        drawPath(path, ink, style = Stroke(2.dp.toPx()))
        val anchor = selected.location(StudioPathControl.NODE, box)
        listOf(StudioPathControl.INCOMING, StudioPathControl.OUTGOING).forEach { handle ->
            val end = selected.location(handle, box)
            if (end != anchor) {
                drawLine(selectedInk, anchor, end, strokeWidth = 1.5.dp.toPx())
                drawCircle(if (handle == control) selectedInk else surface, 5.dp.toPx(), end)
                drawCircle(selectedInk, 5.dp.toPx(), end, style = Stroke(1.5.dp.toPx()))
            }
        }
        nodes.forEachIndexed { index, n ->
            val at = n.location(StudioPathControl.NODE, box)
            drawCircle(if (index == activeNode) selectedInk else surface, 5.dp.toPx(), at)
            drawCircle(if (index == activeNode) selectedInk else ink, 5.dp.toPx(), at, style = Stroke(2.dp.toPx()))
        }
    }
    if (vector.contours.size > 1) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            vector.contours.indices.forEach { index ->
                FilterChip(activeContour == index, { contour = index; node = 0; control = StudioPathControl.NODE },
                    label = { Text(uiText("Contour %d", index + 1)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
    Text(uiText("Node %d of %d", activeNode + 1, nodes.size), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { node = activeNode - 1 }, enabled = activeNode > 0, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(uiText("Previous node"))
        }
        TextButton(onClick = { node = activeNode + 1 }, enabled = activeNode < nodes.lastIndex, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(uiText("Next node"))
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StudioPathControl.entries.forEach { option ->
            FilterChip(option == control, { control = option }, modifier = Modifier.heightIn(min = 48.dp), label = {
                Text(uiText(when (option) {
                    StudioPathControl.NODE -> "Anchor"
                    StudioPathControl.INCOMING -> "Incoming handle"
                    StudioPathControl.OUTGOING -> "Outgoing handle"
                }))
            })
        }
    }
    val exact = when (control) {
        StudioPathControl.NODE -> selected.x to selected.y
        StudioPathControl.INCOMING -> selected.incomingX to selected.incomingY
        StudioPathControl.OUTGOING -> selected.outgoingX to selected.outgoingY
    }
    key(activeContour, marker, exact) {
        var x by remember { mutableStateOf(exact.first.plainCoordinate()) }
        var y by remember { mutableStateOf(exact.second.plainCoordinate()) }
        val nx = x.replace(',', '.').toFloatOrNull()?.takeIf { it in 0f..1f }
        val ny = y.replace(',', '.').toFloatOrNull()?.takeIf { it in 0f..1f }
        Text(uiText("Coordinates within the shape · 0 to 1"), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(x, { x = it.take(14) }, Modifier.weight(1f), singleLine = true,
                isError = nx == null, label = { Text("X") })
            OutlinedTextField(y, { y = it.take(14) }, Modifier.weight(1f), singleLine = true,
                isError = ny == null, label = { Text("Y") })
        }
        TextButton(onClick = { if (nx != null && ny != null) update(StudioNodeEdit.Move(activeNode, control, nx, ny)) },
            enabled = nx != null && ny != null && (nx != exact.first || ny != exact.second),
            modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Set coordinates")) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = {
            update(StudioNodeEdit.Split(activeNode))
            node = activeNode + 1
            control = StudioPathControl.NODE
        }, enabled = nodes.size < StudioVectorGeometry.MAX_CONTOUR_NODES && (vector.closed || activeNode < nodes.lastIndex),
            modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Split next segment")) }
        TextButton(onClick = {
            update(StudioNodeEdit.Delete(activeNode))
            node = activeNode.coerceAtMost(nodes.lastIndex - 1)
        }, enabled = nodes.size > (if (vector.closed) 3 else 2),
            modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Delete node")) }
        TextButton(onClick = {
            update(StudioNodeEdit.Reverse)
            node = nodes.lastIndex - activeNode
            control = when (control) {
                StudioPathControl.NODE -> StudioPathControl.NODE
                StudioPathControl.INCOMING -> StudioPathControl.OUTGOING
                StudioPathControl.OUTGOING -> StudioPathControl.INCOMING
            }
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Reverse contour")) }
    }
    var showCoordinates by remember { mutableStateOf(false) }
    TextButton(onClick = { showCoordinates = true }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(uiText("Paste contour coordinates"))
    }
    if (showCoordinates) {
        var text by remember(activeContour) { mutableStateOf(nodes.joinToString("\n") { point ->
            listOf(point.x, point.y, point.incomingX, point.incomingY, point.outgoingX, point.outgoingY)
                .joinToString(" ") { it.plainCoordinate() }
        }) }
        var invalid by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(text, { text = it.take(8000); invalid = false }, Modifier.fillMaxWidth(),
                label = { Text(uiText("Contour coordinates")) },
                supportingText = { Text(uiText("One node per line: x y and optional incoming and outgoing x y. Values from 0 to 1.")) },
                minLines = 3, maxLines = 6, isError = invalid)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    runCatching { liveVector.withContour(activeContour, parseContourCoordinates(text)) }.onSuccess {
                        liveChange(it)
                        node = 0
                        showCoordinates = false
                    }.onFailure { invalid = true }
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Replace contour")) }
                TextButton(onClick = { showCoordinates = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText("Cancel"))
                }
            }
        }
    }
}

private fun Float.plainCoordinate() = BigDecimal(toString()).setScale(7, java.math.RoundingMode.HALF_UP)
    .stripTrailingZeros().toPlainString()

private fun pathEditorBox(size: Size, ratio: Float, pad: Float): RectF {
    val width = (size.width - pad * 2).coerceAtLeast(1f)
    val height = (size.height - pad * 2).coerceAtLeast(1f)
    val fittedWidth = minOf(width, height * ratio).coerceAtLeast(1f)
    val fittedHeight = minOf(height, fittedWidth / ratio).coerceAtLeast(1f)
    return RectF((size.width - fittedWidth) / 2, (size.height - fittedHeight) / 2,
        (size.width + fittedWidth) / 2, (size.height + fittedHeight) / 2)
}

private fun StudioPathNode.location(control: StudioPathControl, box: RectF): Offset {
    val (x, y) = when (control) {
        StudioPathControl.NODE -> this.x to this.y
        StudioPathControl.INCOMING -> incomingX to incomingY
        StudioPathControl.OUTGOING -> outgoingX to outgoingY
    }
    return Offset(box.left + x * box.width(), box.top + y * box.height())
}

private fun nearestPathNode(nodes: List<StudioPathNode>, box: RectF, touch: Offset, radius: Float): Int? =
    nodes.indices.minByOrNull { (nodes[it].location(StudioPathControl.NODE, box) - touch).getDistance() }
        ?.takeIf { (nodes[it].location(StudioPathControl.NODE, box) - touch).getDistance() <= radius }

private fun parseContourCoordinates(text: String): List<StudioPathNode> =
    text.lineSequence().filter { it.isNotBlank() }.take(StudioVectorGeometry.MAX_CONTOUR_NODES + 1).map { row ->
        val points = row.trim().split(Regex("""\s+"""))
        require(points.size == 2 || points.size == 6)
        val n = points.map { it.replace(',', '.').toFloat() }
        if (n.size == 2) StudioPathNode(n[0], n[1]) else StudioPathNode(n[0], n[1], n[2], n[3], n[4], n[5])
    }.toList()
