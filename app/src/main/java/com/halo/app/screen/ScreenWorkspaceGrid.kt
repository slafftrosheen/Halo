package com.halo.app.screen

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import kotlin.math.abs
import kotlin.math.roundToInt

internal class ScreenGridGeometry {
    var trackWidth: Float = 1f
    var unitHeight: Int = 1
    var rows: IntArray = intArrayOf(0)
    private fun top(row: Int): Int = rows.getOrNull(row) ?: rows.last() + (row - rows.lastIndex) * unitHeight
    fun columns(delta: Float) = (delta / trackWidth.coerceAtLeast(1f)).roundToInt()
    fun rowDelta(from: Int, delta: Float): Int {
        val target = top(from) + delta
        return (0..149).minBy { abs(top(it) - target) } - from
    }
}

/** Rows expand for measured text. Saved minimum sizes never force clipping or overlap. */
@Composable
internal fun ScreenWorkspaceGrid(cards: List<ScreenCard>, columns: Int, modifier: Modifier = Modifier,
    content: @Composable (ScreenGridCell, ScreenGridGeometry) -> Unit) {
    val cells = remember(cards, columns) { ScreenGrid.resolve(cards, columns).sortedWith(compareBy({ it.row }, { it.column }, { it.index })) }
    val geometry = remember { ScreenGridGeometry() }
    val density = LocalDensity.current
    val gap = with(density) { 12.dp.roundToPx() }
    val unit = with(density) { 96.dp.roundToPx() }
    Layout(modifier = modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        content = { cells.forEach { cell -> key(cards[cell.index].presentationId) { content(cell, geometry) } } }) { measurables, constraints ->
        val width = constraints.maxWidth
        val track = ((width - gap * (columns - 1)) / columns).coerceAtLeast(1)
        val places = measurables.mapIndexed { index, measurable ->
            val cell = cells[index]
            measurable.measure(Constraints(minWidth = track * cell.width + gap * (cell.width - 1),
                maxWidth = track * cell.width + gap * (cell.width - 1),
                minHeight = unit * cell.height + gap * (cell.height - 1), maxHeight = Constraints.Infinity))
        }
        val heights = IntArray(cells.maxOfOrNull { it.row + it.height } ?: 0) { unit }
        cells.forEachIndexed { index, cell ->
            val available = (cell.row until cell.row + cell.height).sumOf { heights[it] } + gap * (cell.height - 1)
            if (places[index].height > available) {
                val extra = places[index].height - available
                for (row in cell.row until cell.row + cell.height)
                    heights[row] += extra / cell.height + if (row - cell.row < extra % cell.height) 1 else 0
            }
        }
        val tops = IntArray(heights.size + 1)
        heights.indices.forEach { tops[it + 1] = tops[it] + heights[it] + gap }
        geometry.trackWidth = (track + gap).toFloat()
        geometry.unitHeight = unit + gap
        geometry.rows = tops
        layout(width, (tops.last() - if (heights.isNotEmpty()) gap else 0).coerceAtLeast(0)) {
            places.forEachIndexed { index, place ->
                val cell = cells[index]
                place.placeRelative(cell.column * (track + gap), tops[cell.row])
            }
        }
    }
}

/** Drag handles, keyboard arrows, and native accessibility actions share the same bounded edits. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ScreenGridHandles(cell: ScreenGridCell, geometry: ScreenGridGeometry, columns: Int, cardCount: Int, cardLabel: String,
    onMove: (Int, Int) -> Unit, onResize: (Int, Int) -> Unit, onOptions: () -> Unit) {
    val move = stringResource(R.string.screen_move_card) + ": " + cardLabel
    val resize = stringResource(R.string.screen_resize_card) + ": " + cardLabel
    val options = stringResource(R.string.screen_card_options) + ": " + cardLabel
    val left = stringResource(R.string.screen_move_left)
    val right = stringResource(R.string.screen_move_right)
    val up = stringResource(R.string.screen_move_up)
    val down = stringResource(R.string.screen_move_down)
    val wider = stringResource(R.string.screen_wider)
    val narrower = stringResource(R.string.screen_narrower)
    val taller = stringResource(R.string.screen_taller)
    val shorter = stringResource(R.string.screen_shorter)
    fun Modifier.keyboard(resizing: Boolean) = onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else {
            val delta = when (event.key) {
                Key.DirectionLeft -> -1 to 0; Key.DirectionRight -> 1 to 0
                Key.DirectionUp -> 0 to -1; Key.DirectionDown -> 0 to 1
                else -> null
            }
            if (delta == null) false else {
                if (resizing || event.isShiftPressed) onResize(delta.first, delta.second)
                else onMove(delta.first, delta.second)
                true
            }
        }
    }.focusable()
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var moveDelta by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.size(48.dp).keyboard(false).semantics(mergeDescendants = true) {
            contentDescription = move
            customActions = buildList {
                if (cell.column > 0) add(CustomAccessibilityAction(left) { onMove(-1, 0); true })
                if (cell.column + cell.width < columns) add(CustomAccessibilityAction(right) { onMove(1, 0); true })
                if (cell.row > 0) add(CustomAccessibilityAction(up) { onMove(0, -1); true })
                if (if (columns == 1) cell.index < cardCount - 1 else cell.row < 143) add(CustomAccessibilityAction(down) { onMove(0, 1); true })
            }
        }.pointerInput(cell) {
            detectDragGesturesAfterLongPress(onDragStart = { moveDelta = Offset.Zero },
                onDragCancel = { moveDelta = Offset.Zero }, onDragEnd = {
                    onMove(geometry.columns(moveDelta.x), geometry.rowDelta(cell.row, moveDelta.y))
                    moveDelta = Offset.Zero
                }) { change, drag -> change.consume(); moveDelta += drag }
        }, contentAlignment = Alignment.Center) { HaloIcon(HaloGlyph.ACTION, null) }
        IconButton(onClick = onOptions, modifier = Modifier.size(48.dp)) { HaloIcon(HaloGlyph.SETTINGS, options) }
        var resizeDelta by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxWidth().height(48.dp).keyboard(true).semantics(mergeDescendants = true) {
            contentDescription = resize
            customActions = buildList {
                if (cell.width < columns) add(CustomAccessibilityAction(wider) { onResize(1, 0); true })
                if (cell.width > 1) add(CustomAccessibilityAction(narrower) { onResize(-1, 0); true })
                if (cell.height < 6) add(CustomAccessibilityAction(taller) { onResize(0, 1); true })
                if (cell.height > 1) add(CustomAccessibilityAction(shorter) { onResize(0, -1); true })
            }
        }.pointerInput(cell) {
            detectDragGestures(onDragStart = { resizeDelta = Offset.Zero }, onDragCancel = { resizeDelta = Offset.Zero },
                onDragEnd = {
                    onResize(geometry.columns(resizeDelta.x), geometry.rowDelta(cell.row + cell.height, resizeDelta.y))
                    resizeDelta = Offset.Zero
                }) { change, drag -> change.consume(); resizeDelta += drag }
        }, contentAlignment = Alignment.CenterEnd) { Text("↘", Modifier.padding(end = 12.dp)) }
    }
}
