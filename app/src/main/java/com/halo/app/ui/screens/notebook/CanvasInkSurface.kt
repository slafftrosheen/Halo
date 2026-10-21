package com.halo.app.ui.screens.notebook

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.RequestDisallowInterceptTouchEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.host.android.ink.AndroidInkInput
import com.halo.sdk.runtime.ink.*
import kotlin.math.roundToInt

private enum class InkMode { DRAW, ERASE, PAN }

/** Shared for free sketches and page overlays. Only finished edits invoke the draft owner. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun CanvasInkSurface(
    initial: String,
    onChanged: (String) -> Unit,
    background: ImageBitmap? = null,
    aspectMilli: Int = 1400,
) {
    val sourceAspect = background?.let { (it.width * 1000f / it.height).roundToInt() } ?: aspectMilli
    val parsed = remember(initial, sourceAspect) {
        InkCodec.decode(initial)?.let { doc ->
            if (initial.isEmpty()) doc.copy(aspectMilli = sourceAspect.takeIf { it in 125..8000 } ?: 0) else doc
        }
    }
    val readable = parsed != null
    val editor = remember(initial, sourceAspect) {
        InkEditor(parsed ?: InkDocument(), sourceAspect.takeIf { it in 125..8000 })
    }
    val committedCallback by rememberUpdatedState(onChanged)
    var revision by remember(initial) { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(InkMode.DRAW) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    var hover by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var rejectedChange by remember { mutableStateOf(false) }
    var panPointer by remember { mutableIntStateOf(-1) }
    var lastPan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    val focus = remember { FocusRequester() }
    val disallowIntercept = remember { RequestDisallowInterceptTouchEvent() }
    val surfaceLabel = stringResource(R.string.ink_surface_description)
    val zoomOutLabel = stringResource(R.string.ink_zoom_out)
    val zoomInLabel = stringResource(R.string.ink_zoom_in)
    val actualAspect = background?.let { (it.width * 1000f / it.height).roundToInt() }
        ?: parsed?.aspectMilli?.takeIf { it > 0 } ?: aspectMilli

    fun bounds() = inkBounds(boardSize.width.toFloat(), boardSize.height.toFloat(), actualAspect)
    fun clampPan(value: Offset): Offset {
        val rect = bounds()
        return Offset(value.x.coerceIn(-rect.width * (zoom - 1) / 2, rect.width * (zoom - 1) / 2),
            value.y.coerceIn(-rect.height * (zoom - 1) / 2, rect.height * (zoom - 1) / 2))
    }
    fun publish(rollback: () -> Unit = { editor.undo() }) {
        val current = editor.document
        try {
            val encoded = if (current == parsed) initial else InkCodec.encode(current)
            committedCallback(encoded); rejectedChange = false
        }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            // Aggregate page/draft quotas reject the entire edit, never save a truncated drawing.
            rollback(); rejectedChange = true
        }
        revision++
    }
    val input = remember(editor, density) {
        AndroidInkInput(editor,
            position = { x, y ->
                val rect = bounds()
                if (rect.width <= 0 || rect.height <= 0 || !x.isFinite() || !y.isFinite()) null else {
                    val px = (x - rect.center.x - pan.x) / zoom + rect.center.x
                    val py = (y - rect.center.y - pan.y) / zoom + rect.center.y
                    if (!editor.active && !rect.contains(Offset(px, py))) null
                    else (1000f * (px - rect.left) / rect.width).coerceIn(0f, 1000f) to
                        (1000f * (py - rect.top) / rect.height).coerceIn(0f, 1000f)
                }
            },
            eraserRadius = {
                val rect = bounds()
                (18f * density * 1000 / rect.width.coerceAtLeast(1f) / zoom) to
                    (18f * density * 1000 / rect.height.coerceAtLeast(1f) / zoom)
            },
            eraseSelected = { mode == InkMode.ERASE }, onPreview = { revision++ },
            onCommit = { publish() }, onHover = { hover = it })
    }
    // The normalized document belongs to the draft owner; the viewport is intentionally ephemeral.
    DisposableEffect(input) { onDispose { input.abort() } }
    val preview = remember(editor, revision) { editor.preview() }
    fun changeMode(next: InkMode) { input.abort(); panPointer = -1; hover = null; mode = next; revision++ }
    fun changeZoom(next: Float) { input.abort(); zoom = next.coerceIn(1f, 4f); pan = clampPan(pan); revision++ }
    fun undo() { input.abort(); if (editor.undo()) publish { editor.redo() } }
    fun redo() { input.abort(); if (editor.redo()) publish() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!readable) Text(stringResource(R.string.ink_unreadable), color = MaterialTheme.colorScheme.error)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(InkMode.DRAW to R.string.ink_draw, InkMode.ERASE to R.string.ink_erase,
                InkMode.PAN to R.string.ink_pan).forEach { (choice, label) ->
                FilterChip(mode == choice, { changeMode(choice) }, label = { Text(stringResource(label)) },
                    enabled = readable, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        val height = (LocalConfiguration.current.screenHeightDp * 0.42f).coerceIn(180f, 440f).dp
        val inkColor = if (background != null) Color.Black else MaterialTheme.colorScheme.onSurface
        Canvas(Modifier.fillMaxWidth().height(height).clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (!readable || event.type != KeyEventType.KeyDown || !event.isCtrlPressed) false else when (event.key) {
                    Key.Z -> { if (event.isShiftPressed) redo() else undo(); true }
                    Key.Y -> { redo(); true }
                    else -> false
                }
            }.focusable().semantics { contentDescription = surfaceLabel }
            .onSizeChanged { if (boardSize != it) { input.abort(); boardSize = it; pan = Offset.Zero; revision++ } }
            .pointerInteropFilter(disallowIntercept) { event ->
                if (!readable) false else {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) { focus.requestFocus(); disallowIntercept(true) }
                    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)
                        disallowIntercept(false)
                    if (mode != InkMode.PAN) input.motion(event) else {
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                panPointer = event.getPointerId(event.actionIndex)
                                lastPan = Offset(event.getX(event.actionIndex), event.getY(event.actionIndex))
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val i = event.findPointerIndex(panPointer)
                                if (i >= 0) {
                                    val next = Offset(event.getX(i), event.getY(i))
                                    pan = clampPan(pan + next - lastPan); lastPan = next
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> panPointer = -1
                            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == panPointer) panPointer = -1
                        }
                        true
                    }
                }
            }) {
            val rect = bounds()
            withTransform({ translate(pan.x, pan.y); scale(zoom, zoom, rect.center) }) {
                background?.let { image ->
                    drawImage(image, dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                        dstSize = IntSize(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)))
                }
                drawInk(preview, inkColor, rect)
                hover?.let { (x, y) ->
                    drawCircle(inkColor.copy(alpha = 0.5f), 5.dp.toPx() / zoom,
                        Offset(rect.left + x / 1000 * rect.width, rect.top + y / 1000 * rect.height), style = Stroke(1.dp.toPx() / zoom))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { undo() }, enabled = readable && editor.canUndo) { Text(stringResource(R.string.ink_undo)) }
            TextButton(onClick = { redo() }, enabled = readable && editor.canRedo) { Text(stringResource(R.string.ink_redo)) }
            TextButton(onClick = { input.abort(); if (editor.clear()) publish() }, enabled = readable && preview.strokes.isNotEmpty()) {
                Text(stringResource(R.string.ink_clear))
            }
            TextButton(onClick = { changeZoom(zoom - 0.5f) }, enabled = zoom > 1f,
                modifier = Modifier.semantics { contentDescription = zoomOutLabel }) { Text("−") }
            TextButton(onClick = { changeZoom(zoom + 0.5f) }, enabled = zoom < 4f,
                modifier = Modifier.semantics { contentDescription = zoomInLabel }) { Text("+") }
            TextButton(onClick = { changeZoom(1f) }) { Text(stringResource(R.string.ink_reset_view)) }
        }
        Text(stringResource(R.string.ink_input_help), style = MaterialTheme.typography.bodySmall)
        if (editor.limitReached) Text(stringResource(R.string.ink_capacity), color = MaterialTheme.colorScheme.error)
        if (rejectedChange) Text(stringResource(R.string.ink_draft_rejected), color = MaterialTheme.colorScheme.error)
    }
}

internal fun inkBounds(width: Float, height: Float, aspectMilli: Int): Rect {
    if (aspectMilli == 0 || width <= 0 || height <= 0) return Rect(0f, 0f, width, height)
    val aspect = aspectMilli / 1000f
    val w = minOf(width, height * aspect)
    val h = w / aspect
    return Rect((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
}

/** Same normalized coordinates for editing, thumbnails and page overlays. */
internal fun DrawScope.drawInk(document: InkDocument, ink: Color, rect: Rect) {
    fun InkPoint.pixel() = Offset(rect.left + x / 1000f * rect.width, rect.top + y / 1000f * rect.height)
    fun width(point: InkPoint) = (minOf(rect.width, rect.height) * 0.006f *
        (point.pressure?.let { 0.3f + 1.4f * it / 1000f } ?: 1f)).coerceAtLeast(0.75f)
    for (stroke in document.strokes) {
        val points = stroke.points
        if (points.isEmpty()) continue
        if (stroke.tool == InkTool.LEGACY) {
            if (points.size == 1) drawCircle(ink, 2.5.dp.toPx(), points.first().pixel())
            else {
                val path = Path().apply {
                    val first = points.first().pixel(); moveTo(first.x, first.y)
                    for (i in 1 until points.size) { val p = points[i].pixel(); lineTo(p.x, p.y) }
                }
                drawPath(path, ink, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            continue
        }
        drawCircle(ink, width(points.first()) / 2, points.first().pixel())
        if (points.size == 1) continue
        var start = points.first().pixel()
        val path = Path()
        for (i in 1 until points.size) {
            val control = points[i - 1].pixel()
            val next = points[i].pixel()
            val end = if (i == points.lastIndex) next else (control + next) / 2f
            path.reset(); path.moveTo(start.x, start.y); path.quadraticBezierTo(control.x, control.y, end.x, end.y)
            drawPath(path, ink, style = Stroke((width(points[i - 1]) + width(points[i])) / 2, cap = StrokeCap.Round))
            start = end
        }
    }
}
