package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.halo.app.data.notebook.CanvasSketchCodec
import com.halo.app.data.notebook.CanvasSketchPoint
import com.halo.app.language.uiText
import kotlin.math.roundToInt

@Composable
internal fun CanvasSketchPreview(encoded: String, modifier: Modifier = Modifier) {
    val strokes = remember(encoded) { CanvasSketchCodec.decode(encoded) }
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        renderSketch(strokes, ink)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.renderSketch(
    strokes: List<List<CanvasSketchPoint>>,
    ink: androidx.compose.ui.graphics.Color,
) {
    for (stroke in strokes) {
        if (stroke.isEmpty()) continue
        fun CanvasSketchPoint.pixel() = Offset(x / 1000f * size.width, y / 1000f * size.height)
        if (stroke.size == 1) {
            drawCircle(ink, 2.5.dp.toPx(), stroke.first().pixel())
        } else {
            val path = Path().apply {
                val start = stroke.first().pixel()
                moveTo(start.x, start.y)
                stroke.drop(1).forEach { point ->
                    val next = point.pixel()
                    lineTo(next.x, next.y)
                }
            }
            drawPath(path, ink, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
internal fun CanvasSketchPadDialog(
    initial: String = "",
    recovery: String? = null,
    onDraft: (String) -> Unit = {},
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strokes: SnapshotStateList<List<CanvasSketchPoint>> = remember {
        mutableStateListOf<List<CanvasSketchPoint>>().apply { addAll(CanvasSketchCodec.decode(recovery ?: initial)) }
    }
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    val ink = MaterialTheme.colorScheme.onSurface
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Sketch")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiText("Draw with your finger or stylus."), style = MaterialTheme.typography.bodySmall)
                Box(Modifier.fillMaxWidth().height(260.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .onSizeChanged { boardSize = it }
                    .pointerInput(boardSize) {
                        var activeStroke = -1
                        fun convert(point: Offset): CanvasSketchPoint = CanvasSketchPoint(
                            (point.x / boardSize.width.coerceAtLeast(1) * 1000).roundToInt().coerceIn(0, 1000),
                            (point.y / boardSize.height.coerceAtLeast(1) * 1000).roundToInt().coerceIn(0, 1000),
                        )
                        detectDragGestures(
                            onDragStart = { point ->
                                activeStroke = if (strokes.size < CanvasSketchCodec.MAX_STROKES) {
                                    strokes.add(listOf(convert(point))); strokes.lastIndex
                                } else -1
                            },
                            onDragEnd = { activeStroke = -1; onDraft(CanvasSketchCodec.encode(strokes.toList())) },
                            onDragCancel = { activeStroke = -1; onDraft(CanvasSketchCodec.encode(strokes.toList())) },
                            onDrag = { change, _ ->
                                val index = activeStroke
                                if (index >= 0 && index < strokes.size) {
                                    val stroke = strokes[index]
                                    val next = convert(change.position)
                                    if (stroke.size < CanvasSketchCodec.MAX_POINTS_PER_STROKE &&
                                        stroke.lastOrNull() != next) strokes[index] = stroke + next
                                }
                                change.consume()
                            },
                        )
                    },
                ) {
                    CanvasSketchPreview(CanvasSketchCodec.encode(strokes.toList()), Modifier.fillMaxSize())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex); onDraft(CanvasSketchCodec.encode(strokes.toList())) },
                        enabled = strokes.isNotEmpty()) { Text(uiText("Undo stroke")) }
                    TextButton(onClick = { strokes.clear(); onDraft(CanvasSketchCodec.encode(strokes.toList())) }, enabled = strokes.isNotEmpty()) { Text(uiText("Clear sketch")) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(CanvasSketchCodec.encode(strokes.toList())) },
                enabled = strokes.isNotEmpty()) { Text(uiText("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
    )
}
