package com.halo.app.ui.workspace

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.halo.app.data.studio.StudioArtPoint
import com.halo.app.data.studio.StudioPixelMask
import com.halo.app.data.studio.StudioSelectionCombine
import com.halo.app.data.workspace.CanvasImageRecipe
import com.halo.app.data.workspace.CanvasImageTools
import com.halo.app.language.uiText
import com.halo.app.ui.instruments.HaloInstrumentMotion
import com.halo.app.ui.theme.HaloLookMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

internal enum class StudioArtGesture { TAP, DRAG }

internal class StudioArtworkInput(
    val gesture: StudioArtGesture,
    val onStart: (StudioArtPoint, StudioSelectionCombine?) -> Unit,
    val onMove: (StudioArtPoint) -> Unit = {},
    val onEnd: (StudioArtPoint) -> Unit = {},
    val onCancel: () -> Unit = {},
)

@Composable
internal fun StudioPixelPreview(
    bitmap: Bitmap, ready: Boolean, input: StudioArtworkInput,
    mask: StudioPixelMask?, outline: List<StudioArtPoint>, ellipse: Boolean = false, marquee: Boolean = false,
) {
    val latestInput by rememberUpdatedState(input)
    val ink = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val ratio = bitmap.width.toFloat() / bitmap.height
        val width = minOf(maxWidth, maxOf(248.dp * ratio, 32.dp))
        val height = (width / ratio).coerceIn(32.dp, 248.dp)
        Box(Modifier.width(width).height(height).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(bitmap, ready, input.gesture) {
                if (!ready) return@pointerInput
                fun point(offset: Offset) = StudioArtPoint((offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                    (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f))
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val gesture = latestInput
                    val modifiers = currentEvent.keyboardModifiers
                    val combine = when {
                        modifiers.isAltPressed -> StudioSelectionCombine.SUBTRACT
                        modifiers.isShiftPressed -> StudioSelectionCombine.ADD
                        else -> null
                    }
                    down.consume()
                    val start = point(down.position)
                    if (gesture.gesture == StudioArtGesture.DRAG) gesture.onStart(start, combine)
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) {
                            cancelled = true
                            break
                        }
                        val location = point(change.position)
                        change.consume()
                        if (change.pressed) {
                            if (gesture.gesture == StudioArtGesture.DRAG) gesture.onMove(location)
                        } else {
                            if (gesture.gesture == StudioArtGesture.TAP) gesture.onStart(location, combine)
                            else gesture.onEnd(location)
                            break
                        }
                    }
                    if (cancelled) gesture.onCancel()
                }
            }) {
            Image(bitmap.asImageBitmap(), uiText("Artwork preview"), Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
            if (mask != null) StudioSelectionOverlay(mask, Modifier.matchParentSize())
            if (outline.size >= 2) Canvas(Modifier.matchParentSize()) {
                fun offset(p: StudioArtPoint) = Offset(p.x * size.width, p.y * size.height)
                val first = offset(outline.first())
                val last = offset(outline.last())
                val line = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())))
                if (marquee) {
                    val corner = Offset(minOf(first.x, last.x), minOf(first.y, last.y))
                    val extent = Size(abs(first.x - last.x), abs(first.y - last.y))
                    if (ellipse) drawOval(ink, topLeft = corner, size = extent, style = line)
                    else drawRect(ink, topLeft = corner, size = extent, style = line)
                } else {
                    val path = Path().apply {
                        moveTo(first.x, first.y)
                        for (vertex in outline.drop(1)) {
                            val p = offset(vertex)
                            lineTo(p.x, p.y)
                        }
                    }
                    drawPath(path, ink, style = line)
                }
            }
        }
    }
}

@Composable
internal fun StudioSelectionOverlay(mask: StudioPixelMask, modifier: Modifier, recipe: CanvasImageRecipe = CanvasImageRecipe()) {
    val result by produceState<StudioSelectionFrames?>(null, mask, recipe) {
        value = withContext(Dispatchers.Default) {
            val frames = (0..1).map { phase ->
                val pixels = IntArray(mask.width * mask.height)
                for (y in 0 until mask.height) for (x in 0 until mask.width) {
                    val alpha = mask.at(x, y)
                    val edge = alpha >= 128 && (x == 0 || y == 0 || x == mask.width - 1 || y == mask.height - 1 ||
                        mask.at(x - 1, y) < 128 || mask.at(x + 1, y) < 128 ||
                        mask.at(x, y - 1) < 128 || mask.at(x, y + 1) < 128)
                    pixels[y * mask.width + x] = when {
                        edge -> if (((x + y) / 3 + phase) % 2 == 0) 0xffe5fff3.toInt() else 0xff123527.toInt()
                        alpha > 0 -> ((alpha * 53 / 255) shl 24) or 0x0074e6b9
                        else -> 0
                    }
                }
                val raw = Bitmap.createBitmap(pixels, mask.width, mask.height, Bitmap.Config.ARGB_8888)
                CanvasImageTools.transformOwnedBitmap(raw, recipe, preview = true)
            }
            StudioSelectionFrames(mask, recipe, frames)
        }
    }
    val motion = HaloInstrumentMotion.level()
    var phase by remember(mask) { mutableIntStateOf(0) }
    LaunchedEffect(motion, mask) {
        phase = 0
        if (motion != HaloLookMotion.STILL) while (true) {
            delay(if (motion == HaloLookMotion.GENTLE) 400 else 270)
            phase = 1 - phase
        }
    }
    val frames = result?.takeIf { it.mask === mask && it.recipe == recipe }
    val frame = frames?.images?.getOrNull(phase)
    if (frame != null) Image(frame.asImageBitmap(), null, modifier, contentScale = ContentScale.Fit)
}

private data class StudioSelectionFrames(val mask: StudioPixelMask, val recipe: CanvasImageRecipe, val images: List<Bitmap>)

internal fun List<StudioArtPoint>.sampleStudioPoint(point: StudioArtPoint): List<StudioArtPoint> {
    val last = lastOrNull() ?: return listOf(point)
    if (hypot(last.x - point.x, last.y - point.y) < .0025f) return this
    val kept = if (size < 384) this else filterIndexed { index, _ -> index % 2 == 0 }
    return kept + point
}
