package com.halo.app.data.studio

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

internal data class StudioSmudgeStroke(
    val path: List<StudioArtPoint>, val strength: Int, val radius: Int, val wet: Boolean,
) {
    init { require(path.size in 2..384 && strength in 1..100 && radius in 4..120) }
}

private data class Dab(val x: Float, val y: Float)

/** A smudge drags neighbouring pixels along its path. Wet mix also carries pigment between dabs. */
internal suspend fun smudgeStudioPixels(
    canvas: Bitmap, strokes: List<StudioSmudgeStroke>, selection: StudioPixelMask?, artboardEdge: Int,
): Boolean {
    require(!canvas.isRecycled && canvas.isMutable && strokes.size in 1..12 && artboardEdge > 0)
    val width = canvas.width
    val height = canvas.height
    val scale = maxOf(width, height).toFloat() / artboardEdge
    var changed = false
    for (stroke in strokes) {
        coroutineContext.ensureActive()
        val radius = maxOf(1, (stroke.radius * scale).roundToInt())
        val path = stroke.path.map { point -> Dab(point.x * (width - 1), point.y * (height - 1)) }
        val strength = stroke.strength / 100f
        val length = path.zipWithNext().sumOf { (a, b) -> hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) }
        val spacing = maxOf(1f, radius * .45f, (length / 640).toFloat())
        val patch = IntArray((radius * 2 + 1) * (radius * 2 + 1))
        val output = IntArray(patch.size)
        var carried: Int? = null
        for (segment in 0 until path.lastIndex) {
            val from = path[segment]
            val to = path[segment + 1]
            val dx = to.x - from.x
            val dy = to.y - from.y
            val distance = hypot(dx, dy)
            if (distance < .45f) continue
            val samples = ceil(distance / spacing).toInt().coerceAtLeast(1)
            val pull = minOf(radius * .48f, distance / samples * 1.2f).coerceAtLeast(.75f)
            for (index in 1..samples) {
                coroutineContext.ensureActive()
                val point = Dab(from.x + dx * index / samples, from.y + dy * index / samples)
                val pigment = canvas.getPixel(point.x.roundToInt(), point.y.roundToInt())
                carried = if (stroke.wet) carried?.let { blendStudioPigments(it, pigment, .26f) } ?: pigment else null
                if (dragStudioDab(canvas, patch, output, point, radius, dx / distance * pull, dy / distance * pull,
                    strength, carried, selection)) changed = true
            }
        }
    }
    return changed
}

private fun dragStudioDab(
    canvas: Bitmap, patch: IntArray, output: IntArray, point: Dab, radius: Int, dx: Float, dy: Float,
    strength: Float, pigment: Int?, selection: StudioPixelMask?,
): Boolean {
    val centerX = point.x.roundToInt()
    val centerY = point.y.roundToInt()
    val left = maxOf(0, centerX - radius)
    val top = maxOf(0, centerY - radius)
    val right = minOf(canvas.width, centerX + radius + 1)
    val bottom = minOf(canvas.height, centerY + radius + 1)
    val width = right - left
    val height = bottom - top
    canvas.getPixels(patch, 0, width, left, top, width, height)
    var changed = false
    for (y in top until bottom) for (x in left until right) {
        val i = (y - top) * width + (x - left)
        val localX = x - point.x
        val localY = y - point.y
        val distance = (localX * localX + localY * localY) / (radius * radius)
        if (distance >= 1f) {
            output[i] = patch[i]
            continue
        }
        val coverage = selection?.cover(x, y, canvas.width, canvas.height) ?: 255
        val old = patch[i]
        val sx = (x - dx - left).roundToInt().coerceIn(0, width - 1)
        val sy = (y - dy - top).roundToInt().coerceIn(0, height - 1)
        val picked = patch[sy * width + sx]
        val source = if (pigment != null) blendStudioPigments(picked, pigment, .28f) else picked
        val used = blendStudioPigments(old, source, strength * (1f - distance) * coverage / 255)
        output[i] = used
        if (used != old) changed = true
    }
    if (changed) canvas.setPixels(output, 0, width, left, top, width, height)
    return changed
}

/** Mix premultiplied contributions so dragging across transparent edges keeps their colour. */
private fun blendStudioPigments(from: Int, to: Int, weight: Float): Int {
    if (from == to || weight <= 0f) return from
    val fromAlpha = Color.alpha(from) * (1f - weight)
    val toAlpha = Color.alpha(to) * weight
    val alpha = fromAlpha + toAlpha
    if (alpha < .5f) return 0
    fun channel(first: Int, second: Int) = ((first * fromAlpha + second * toAlpha) / alpha).roundToInt().coerceIn(0, 255)
    return Color.argb(alpha.roundToInt(), channel(Color.red(from), Color.red(to)),
        channel(Color.green(from), Color.green(to)), channel(Color.blue(from), Color.blue(to)))
}
