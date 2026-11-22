package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.halo.app.data.notebook.CanvasFramePreset
import com.halo.app.data.notebook.CanvasNode
import com.halo.app.data.notebook.CanvasNodeType
import com.halo.app.data.notebook.CanvasShapeKind
import com.halo.app.data.notebook.CanvasVisualShape
import com.halo.app.data.notebook.containsCenter
import com.halo.app.data.notebook.isCanvasContainer
import com.halo.app.language.uiText
import kotlin.math.roundToInt

@Composable
internal fun CanvasVisualShapeContent(shape: CanvasVisualShape, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val stroke = shape.outline.dp.toPx()
        val inset = (stroke / 2f + 1.dp.toPx()).coerceAtMost(minOf(size.width, size.height) / 3f)
        val rect = Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f))
        val origin = Offset(inset, inset)
        val fill = shape.fill?.let(::Color)
        when (shape.kind) {
            CanvasShapeKind.RECTANGLE -> {
                val radius = CornerRadius(shape.corner.dp.toPx().coerceAtMost(minOf(rect.width, rect.height) / 2f))
                if (fill != null) drawRoundRect(fill, origin, rect, radius)
                if (stroke > 0) drawRoundRect(line, origin, rect, radius, style = Stroke(stroke))
            }
            CanvasShapeKind.ELLIPSE -> {
                if (fill != null) drawOval(fill, origin, rect)
                if (stroke > 0) drawOval(line, origin, rect, style = Stroke(stroke))
            }
            CanvasShapeKind.DIAMOND, CanvasShapeKind.ARROW -> {
                val x = inset
                val y = inset
                val w = rect.width
                val h = rect.height
                val path = Path().apply {
                    if (shape.kind == CanvasShapeKind.DIAMOND) {
                        moveTo(x + w / 2, y)
                        lineTo(x + w, y + h / 2)
                        lineTo(x + w / 2, y + h)
                        lineTo(x, y + h / 2)
                    } else {
                        moveTo(x, y + h * .32f)
                        lineTo(x + w * .60f, y + h * .32f)
                        lineTo(x + w * .60f, y)
                        lineTo(x + w, y + h / 2)
                        lineTo(x + w * .60f, y + h)
                        lineTo(x + w * .60f, y + h * .68f)
                        lineTo(x, y + h * .68f)
                    }
                    close()
                }
                if (fill != null) drawPath(path, fill)
                if (stroke > 0) drawPath(path, line, style = Stroke(stroke))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CanvasShapeDialog(initial: CanvasVisualShape, editing: Boolean, ready: Boolean,
    onApply: (CanvasVisualShape) -> Unit, onDismiss: () -> Unit) {
    var shape by remember(initial) { mutableStateOf(initial) }
    val fills = listOf(null to "None", 0xFFBDA9F6.toInt() to "Lilac", 0xFF95BEEA.toInt() to "Blue",
        0xFFEDAD87.toInt() to "Apricot", 0xFFECB8CD.toInt() to "Pink", 0xFFE7D0A2.toInt() to "Sand")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(uiText(if (editing) "Shape properties" else "Add shape")) }, text = {
        Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CanvasVisualShapeContent(shape, Modifier.align(Alignment.CenterHorizontally).size(130.dp, 82.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CanvasShapeKind.entries.forEach { kind ->
                    FilterChip(shape.kind == kind, { shape = shape.copy(kind = kind) }, label = { Text(uiText(kind.label)) })
                }
            }
            Text(uiText("Fill"), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                fills.forEach { (fill, label) -> FilterChip(shape.fill == fill, { shape = shape.copy(fill = fill) }, label = { Text(uiText(label)) }) }
            }
            Text(uiText("Outline: %1\$d", shape.outline))
            Slider(shape.outline.toFloat(), { shape = shape.copy(outline = it.roundToInt()) }, valueRange = 0f..12f, steps = 11)
            if (shape.kind == CanvasShapeKind.RECTANGLE) {
                Text(uiText("Corner: %1\$d", shape.corner))
                Slider(shape.corner.toFloat(), { shape = shape.copy(corner = it.roundToInt()) }, valueRange = 0f..60f)
            }
        }
    }, confirmButton = {
        TextButton(onClick = { onApply(shape) }, enabled = ready && (shape.fill != null || shape.outline > 0)) {
            Text(uiText(if (editing) "Apply" else "Add"))
        }
    }, dismissButton = { TextButton(onDismiss) { Text(uiText("Cancel")) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CanvasFrameDialog(ready: Boolean, onInsert: (String, CanvasFramePreset) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var frame by remember { mutableStateOf(CanvasFramePreset.WIDE) }
    val untitled = uiText("Untitled frame")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(uiText("Add frame")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(80) }, singleLine = true, label = { Text(uiText("Title")) })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CanvasFramePreset.entries.forEach { preset ->
                    FilterChip(frame == preset, { frame = preset }, label = { Text(uiText(preset.label)) })
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = { onInsert(name.trim().ifBlank { untitled }, frame) }, enabled = ready) { Text(uiText("Add")) }
    }, dismissButton = { TextButton(onDismiss) { Text(uiText("Cancel")) } })
}

@Composable
internal fun CanvasPresentDialog(state: CanvasEditorUiState, onDismiss: () -> Unit) {
    val frames = remember(state.nodes) { state.nodes.filter { it.type == CanvasNodeType.FRAME }.sortedBy { it.zIndex } }
    if (frames.isEmpty()) { LaunchedEffect(Unit) { onDismiss() }; return }
    var page by remember { mutableIntStateOf(0) }
    val current = page.coerceIn(frames.indices)
    val frame = frames[current]
    val cards = remember(frame, state.nodes) { state.nodes.filter { !it.type.isCanvasContainer() && frame.containsCenter(it) }.sortedBy { it.zIndex } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(frame.text, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val zoom = minOf(maxWidth.value / frame.width, maxHeight.value / frame.height).coerceAtLeast(.001f)
                    Box(Modifier.size((frame.width * zoom).dp, (frame.height * zoom).dp).clipToBounds()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        cards.forEach { card -> key(card.id) {
                            val place = Modifier.offset(((card.x - frame.x) * zoom).dp, ((card.y - frame.y) * zoom).dp)
                                .size((card.width * zoom).dp, (card.height * zoom).dp).clipToBounds()
                            CanvasPresentedCard(card, state, place)
                        } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { page = current - 1 }, enabled = current > 0) { Text(uiText("Previous")) }
                    Text(uiText("%1\$d of %2\$d", current + 1, frames.size))
                    TextButton(onClick = { page = current + 1 }, enabled = current < frames.lastIndex) { Text(uiText("Next")) }
                    TextButton(onClick = onDismiss) { Text(uiText("Close")) }
                }
            }
        }
    }
}

@Composable
private fun CanvasPresentedCard(card: CanvasNode, state: CanvasEditorUiState, modifier: Modifier) {
    when (card.type) {
        CanvasNodeType.SHAPE -> CanvasVisualShapeContent(CanvasVisualShape.decode(card.text) ?: CanvasVisualShape(), modifier)
        CanvasNodeType.SKETCH -> CanvasSketchPreview(card.text, modifier)
        else -> {
            val file = card.refObjectId?.let { state.files[it] }
            if (file != null && file.isCanvasVisualSource() && file.isCanvasPreviewAvailable()) CanvasLocalFileThumbnail(file, modifier)
            else Surface(modifier, shape = MaterialTheme.shapes.small, color = if (card.type == CanvasNodeType.STICKY)
                MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(card.readableText(), Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
