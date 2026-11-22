package com.halo.app.data.studio

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.BitSet
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

internal enum class StudioFillStyle(val label: String) { SOLID("Solid"), LINEAR("Linear gradient"), RADIAL("Radial gradient") }

internal data class StudioFillRequest(val x: Float, val y: Float, val tolerance: Int, val color: Int, val end: Int,
    val style: StudioFillStyle) {
    init { require(x in 0f..1f && y in 0f..1f && tolerance in 0..100) }
}

/** Floods only the clicked connected region. The output is transparent outside the region. */
internal suspend fun renderStudioFill(source: Bitmap, request: StudioFillRequest): Bitmap = withContext(Dispatchers.Default) {
    val width = source.width
    val height = source.height
    val count = width * height
    val pixels = IntArray(count)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    val startX = (request.x * width).toInt().coerceIn(0, width - 1)
    val startY = (request.y * height).toInt().coerceIn(0, height - 1)
    val start = startY * width + startX
    val target = pixels[start]
    val threshold = request.tolerance * 255 / 100
    val visited = BitSet(count)
    val queue = IntArray(count)
    var head = 0
    var tail = 0
    var minX = startX
    var maxX = startX
    var maxSquared = 0f
    fun visit(index: Int) {
        if (visited[index]) return
        visited.set(index)
        if (studioColorDistance(pixels[index], target) <= threshold) queue[tail++] = index
    }
    visit(start)
    while (head < tail) {
        if (head % 16384 == 0) coroutineContext.ensureActive()
        val index = queue[head++]
        val x = index % width
        val y = index / width
        minX = minOf(minX, x)
        maxX = maxOf(maxX, x)
        val dx = (x - startX).toFloat()
        val dy = (y - startY).toFloat()
        maxSquared = maxOf(maxSquared, dx * dx + dy * dy)
        if (x > 0) visit(index - 1)
        if (x < width - 1) visit(index + 1)
        if (y > 0) visit(index - width)
        if (y < height - 1) visit(index + width)
    }
    pixels.fill(Color.TRANSPARENT)
    val radius = kotlin.math.sqrt(maxSquared).coerceAtLeast(1f)
    val span = (maxX - minX).coerceAtLeast(1).toFloat()
    for (item in 0 until tail) {
        if (item % 16384 == 0) coroutineContext.ensureActive()
        val index = queue[item]
        val x = index % width
        val y = index / width
        val amount = when (request.style) {
            StudioFillStyle.SOLID -> 0f
            StudioFillStyle.LINEAR -> (x - minX) / span
            StudioFillStyle.RADIAL -> hypot((x - startX).toFloat(), (y - startY).toFloat()) / radius
        }
        pixels[index] = mixStudioFill(request.color, request.end, amount.coerceIn(0f, 1f))
    }
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun studioColorDistance(first: Int, second: Int): Int = maxOf(abs(Color.alpha(first) - Color.alpha(second)),
    abs(Color.red(first) - Color.red(second)), abs(Color.green(first) - Color.green(second)), abs(Color.blue(first) - Color.blue(second)))

private fun mixStudioFill(from: Int, to: Int, amount: Float): Int {
    fun mix(first: Int, second: Int) = (first + (second - first) * amount).roundToInt()
    return Color.argb(mix(Color.alpha(from), Color.alpha(to)), mix(Color.red(from), Color.red(to)),
        mix(Color.green(from), Color.green(to)), mix(Color.blue(from), Color.blue(to)))
}
