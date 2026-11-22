package com.halo.app.data.studio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

internal data class StudioArtPoint(val x: Float, val y: Float) {
    init { require(x in 0f..1f && y in 0f..1f) }
}

internal enum class StudioSelectionCombine { REPLACE, ADD, SUBTRACT }

internal sealed interface StudioSelectionRequest {
    data class Marquee(val first: StudioArtPoint, val last: StudioArtPoint, val ellipse: Boolean) : StudioSelectionRequest
    data class Lasso(val vertices: List<StudioArtPoint>) : StudioSelectionRequest {
        init { require(vertices.size in 3..384) }
    }
    data class Wand(val seed: StudioArtPoint, val tolerance: Int, val contiguous: Boolean) : StudioSelectionRequest {
        init { require(tolerance in 0..100) }
    }
    data class Subject(val refine: Int, val seed: StudioArtPoint? = null) : StudioSelectionRequest {
        init { require(refine in 0..10) }
    }
}

/** Preview-resolution coverage in untransformed artboard coordinates. */
internal class StudioPixelMask(val width: Int, val height: Int, private val coverage: ByteArray) {
    init { require(width in 1..640 && height in 1..640 && coverage.size == width * height) }
    val hasSelection = coverage.any { it != 0.toByte() }

    fun at(x: Int, y: Int): Int = coverage[y * width + x].toInt() and 255

    /** Bilinear filtering preserves a feathered mask when applying it to full resolution art. */
    fun cover(x: Int, y: Int, destinationWidth: Int, destinationHeight: Int): Int {
        if (destinationWidth == width && destinationHeight == height) return at(x, y)
        val sx = ((x + .5f) * width / destinationWidth - .5f).coerceIn(0f, width - 1f)
        val sy = ((y + .5f) * height / destinationHeight - .5f).coerceIn(0f, height - 1f)
        val left = sx.toInt()
        val top = sy.toInt()
        val right = minOf(left + 1, width - 1)
        val bottom = minOf(top + 1, height - 1)
        val mixX = sx - left
        val mixY = sy - top
        val a = at(left, top) * (1f - mixX) + at(right, top) * mixX
        val b = at(left, bottom) * (1f - mixX) + at(right, bottom) * mixX
        return (a * (1f - mixY) + b * mixY).roundToInt()
    }

    fun combined(previous: StudioPixelMask?, mode: StudioSelectionCombine): StudioPixelMask {
        if (mode == StudioSelectionCombine.REPLACE || previous == null && mode == StudioSelectionCombine.ADD) return this
        if (previous == null) return StudioPixelMask(width, height, ByteArray(coverage.size))
        val combined = ByteArray(coverage.size)
        for (y in 0 until height) for (x in 0 until width) {
            val old = previous.cover(x, y, width, height)
            val new = at(x, y)
            combined[y * width + x] = when (mode) {
                StudioSelectionCombine.REPLACE -> new
                StudioSelectionCombine.ADD -> maxOf(old, new)
                StudioSelectionCombine.SUBTRACT -> old * (255 - new) / 255
            }.toByte()
        }
        return StudioPixelMask(width, height, combined)
    }
}

internal suspend fun studioPixelMask(
    source: Bitmap, request: StudioSelectionRequest, feather: Int, artboardEdge: Int,
): StudioPixelMask? {
    require(feather in 0..200 && artboardEdge > 0)
    val width = source.width
    val height = source.height
    val coverage = when (request) {
        is StudioSelectionRequest.Marquee, is StudioSelectionRequest.Lasso -> shapeCoverage(width, height, request)
        is StudioSelectionRequest.Wand -> wandCoverage(source.pixels(), width, height, request)
        is StudioSelectionRequest.Subject -> subjectCoverage(source.pixels(), width, height, request) ?: return null
    }
    val radius = (feather.toFloat() * maxOf(width, height) / artboardEdge).roundToInt()
    val refined = if (request is StudioSelectionRequest.Subject) maxOf(radius, request.refine) else radius
    return StudioPixelMask(width, height, featherCoverage(coverage, width, height, refined))
}

private fun shapeCoverage(width: Int, height: Int, request: StudioSelectionRequest): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val canvas = Canvas(bitmap)
        when (request) {
            is StudioSelectionRequest.Marquee -> {
                val a = request.first
                val b = request.last
                val rect = RectF(minOf(a.x, b.x) * width, minOf(a.y, b.y) * height,
                    maxOf(a.x, b.x) * width, maxOf(a.y, b.y) * height)
                if (request.ellipse) canvas.drawOval(rect, paint) else canvas.drawRect(rect, paint)
            }
            is StudioSelectionRequest.Lasso -> {
                val path = Path().apply {
                    val first = request.vertices.first()
                    moveTo(first.x * width, first.y * height)
                    for (point in request.vertices.drop(1)) lineTo(point.x * width, point.y * height)
                    close()
                    fillType = Path.FillType.EVEN_ODD
                }
                canvas.drawPath(path, paint)
            }
            else -> error("Expected a selection outline")
        }
        val pixels = bitmap.pixels()
        return ByteArray(pixels.size) { Color.alpha(pixels[it]).toByte() }
    } finally { bitmap.recycle() }
}

private suspend fun wandCoverage(
    pixels: IntArray, width: Int, height: Int, request: StudioSelectionRequest.Wand,
): ByteArray {
    val seed = request.seed.index(width, height)
    val target = pixels[seed]
    val tolerance = request.tolerance * 255 / 100
    val coverage = ByteArray(pixels.size)
    val similar = { index: Int -> studioPixelDistance(pixels[index], target) <= tolerance }
    if (request.contiguous) {
        floodStudioPixels(width, height, intArrayOf(seed), similar) { coverage[it] = -1 }
    } else {
        for (index in pixels.indices) {
            if (index % 8192 == 0) coroutineContext.ensureActive()
            if (similar(index)) coverage[index] = -1
        }
    }
    return coverage
}

/**
 * Local foreground proposal. An edge-connected background is inferred from the common colours
 * and transparency at the picture boundary. Keep the strongest independent foreground component.
 */
private suspend fun subjectCoverage(
    pixels: IntArray, width: Int, height: Int, request: StudioSelectionRequest.Subject,
): ByteArray? {
    if (width < 3 || height < 3) return null
    val boundary = IntArray(2 * width + 2 * (height - 2))
    var next = 0
    for (x in 0 until width) {
        boundary[next++] = x
        boundary[next++] = (height - 1) * width + x
    }
    for (y in 1 until height - 1) {
        boundary[next++] = y * width
        boundary[next++] = y * width + width - 1
    }
    val palette = boundary.groupBy { studioBorderColor(pixels[it]) }.values
        .filter { it.size >= maxOf(2, boundary.size / 36) }
        .sortedByDescending { it.size }.take(8)
        .map { positions -> averageStudioBorder(positions, pixels) }
    if (palette.isEmpty()) return null
    val foreground = ByteArray(pixels.size) { 1 }
    val tolerance = 40 - request.refine * 2
    floodStudioPixels(width, height, boundary, { index ->
        val color = pixels[index]
        Color.alpha(color) < 20 || palette.any { studioPixelDistance(color, it) <= tolerance }
    }) { foreground[it] = 0 }
    for (index in pixels.indices) if (Color.alpha(pixels[index]) < 20) foreground[index] = 0
    val seed = request.seed?.index(width, height)
    if (seed != null && foreground[seed] == 0.toByte()) return null
    return studioForegroundComponent(foreground, width, height, seed)
}

private fun studioBorderColor(color: Int): Int {
    if (Color.alpha(color) < 20) return -1
    return (Color.red(color) / 32 shl 8) or (Color.green(color) / 32 shl 4) or Color.blue(color) / 32
}

private fun averageStudioBorder(positions: List<Int>, pixels: IntArray): Int {
    var alpha = 0
    var red = 0
    var green = 0
    var blue = 0
    for (position in positions) {
        val color = pixels[position]
        alpha += Color.alpha(color)
        red += Color.red(color)
        green += Color.green(color)
        blue += Color.blue(color)
    }
    val n = positions.size
    return Color.argb(alpha / n, red / n, green / n, blue / n)
}

private suspend fun studioForegroundComponent(
    foreground: ByteArray, width: Int, height: Int, seed: Int?,
): ByteArray? {
    val queue = IntArray(foreground.size)
    var chosen: IntArray? = null
    var bestScore = 0.0
    for (start in if (seed != null) seed..seed else foreground.indices) {
        if (start % 8192 == 0) coroutineContext.ensureActive()
        if (foreground[start] != 1.toByte()) continue
        var head = 0
        var tail = 0
        var centerWeight = 0.0
        fun add(index: Int) {
            if (foreground[index] == 1.toByte()) {
                foreground[index] = 0
                queue[tail++] = index
            }
        }
        add(start)
        while (head < tail) {
            if (head % 8192 == 0) coroutineContext.ensureActive()
            val index = queue[head++]
            val x = index % width
            val y = index / width
            centerWeight += 1.3 - hypot(x.toDouble() / width - .5, y.toDouble() / height - .5)
            if (x > 0) add(index - 1)
            if (x + 1 < width) add(index + 1)
            if (y > 0) add(index - width)
            if (y + 1 < height) add(index + width)
        }
        if (tail >= maxOf(1, foreground.size / 500) && tail.toLong() * 100 <= foreground.size.toLong() * 96 && centerWeight > bestScore) {
            bestScore = centerWeight
            chosen = queue.copyOf(tail)
        }
    }
    val component = chosen ?: return null
    return ByteArray(foreground.size).also { coverage -> component.forEach { coverage[it] = -1 } }
}

private suspend fun floodStudioPixels(
    width: Int, height: Int, seeds: IntArray, allowed: (Int) -> Boolean, accept: (Int) -> Unit,
) {
    val visited = BooleanArray(width * height)
    val queue = IntArray(visited.size)
    var head = 0
    var tail = 0
    fun add(index: Int) {
        if (visited[index]) return
        visited[index] = true
        if (allowed(index)) queue[tail++] = index
    }
    for (seed in seeds) add(seed)
    while (head < tail) {
        if (head % 8192 == 0) coroutineContext.ensureActive()
        val index = queue[head++]
        accept(index)
        val x = index % width
        val y = index / width
        if (x > 0) add(index - 1)
        if (x + 1 < width) add(index + 1)
        if (y > 0) add(index - width)
        if (y + 1 < height) add(index + width)
    }
}

private suspend fun featherCoverage(source: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
    if (radius <= 0) return source
    val horizontal = ByteArray(source.size)
    val result = ByteArray(source.size)
    val divisor = radius * 2 + 1
    fun pass(input: ByteArray, output: ByteArray, lines: Int, length: Int, lineStride: Int, stride: Int) {
        for (line in 0 until lines) {
            val base = line * lineStride
            fun at(coordinate: Int) = input[base + coordinate.coerceIn(0, length - 1) * stride].toInt() and 255
            var sum = 0
            for (i in -radius..radius) sum += at(i)
            for (index in 0 until length) {
                output[base + index * stride] = (sum / divisor).toByte()
                sum += at(index + radius + 1) - at(index - radius)
            }
        }
    }
    pass(source, horizontal, height, width, width, 1)
    coroutineContext.ensureActive()
    pass(horizontal, result, width, height, 1, width)
    return result
}

internal fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
private fun StudioArtPoint.index(width: Int, height: Int) =
    (y * height).toInt().coerceIn(0, height - 1) * width + (x * width).toInt().coerceIn(0, width - 1)

private fun studioPixelDistance(a: Int, b: Int): Int {
    val alpha = abs(Color.alpha(a) - Color.alpha(b))
    val weight = minOf(Color.alpha(a), Color.alpha(b))
    return maxOf(alpha, maxOf(abs(Color.red(a) - Color.red(b)), abs(Color.green(a) - Color.green(b)),
        abs(Color.blue(a) - Color.blue(b))) * weight / 255)
}
