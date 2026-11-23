package com.halo.app.data.studio

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Controls are relative to the live layer box. Its exact physical position comes from the document DPI. */
enum class StudioShape { ROUNDED_RECT, ARC, POLYGON, STAR, LINE, ARROW, FRAME, PATH, DIMENSION }

data class StudioPathNode(
    val x: Float, val y: Float,
    val incomingX: Float = x, val incomingY: Float = y,
    val outgoingX: Float = x, val outgoingY: Float = y,
) {
    init { require(listOf(x, y, incomingX, incomingY, outgoingX, outgoingY).all { it.isFinite() && it in 0f..1f }) }
}

data class StudioVectorGeometry(
    val shape: StudioShape,
    val corners: List<Float> = listOf(.12f, .12f, .12f, .12f),
    val sides: Int = 5,
    val innerRadius: Float = .45f,
    val startAngle: Float = 0f,
    val sweep: Float = 270f,
    val stroke: Float = .025f,
    val dash: Boolean = false,
    val closed: Boolean = true,
    val nodes: List<StudioPathNode> = emptyList(),
) {
    init {
        require(corners.size == 4 && corners.all { it in 0f..0.5f })
        require(sides in 3..60 && innerRadius in .05f..1f)
        require(startAngle in -360f..360f && sweep in -360f..360f && sweep != 0f)
        require(stroke in .001f..0.5f)
        require(nodes.size <= 128 && (shape != StudioShape.PATH || nodes.size >= 2))
    }

    val outlineOnly get() = shape in setOf(StudioShape.ARC, StudioShape.LINE, StudioShape.ARROW, StudioShape.FRAME, StudioShape.DIMENSION) ||
        (shape == StudioShape.PATH && !closed)

    fun path(box: RectF): Path = Path().apply {
        fun x(value: Float) = box.left + value * box.width()
        fun y(value: Float) = box.top + value * box.height()
        fun line(fromX: Float, fromY: Float, toX: Float, toY: Float) { moveTo(x(fromX), y(fromY)); lineTo(x(toX), y(toY)) }
        when (shape) {
            StudioShape.ROUNDED_RECT, StudioShape.FRAME -> {
                val scale = minOf(box.width(), box.height())
                val radii = corners.flatMap { listOf(it * scale, it * scale) }.toFloatArray()
                addRoundRect(box, radii, Direction.CW)
            }
            StudioShape.ARC -> arcTo(box, startAngle, sweep, true)
            StudioShape.POLYGON, StudioShape.STAR -> {
                val count = if (shape == StudioShape.STAR) sides * 2 else sides
                repeat(count) { index ->
                    val angle = (2 * PI * index / count) - PI / 2
                    val radius = if (shape == StudioShape.STAR && index % 2 == 1) innerRadius else 1f
                    val px = x(.5f + cos(angle).toFloat() * .5f * radius)
                    val py = y(.5f + sin(angle).toFloat() * .5f * radius)
                    if (index == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            StudioShape.LINE, StudioShape.ARROW -> {
                line(.05f, .9f, .95f, .1f)
                if (shape == StudioShape.ARROW) {
                    moveTo(x(.7f), y(.17f)); lineTo(x(.95f), y(.1f)); lineTo(x(.84f), y(.35f))
                }
            }
            StudioShape.DIMENSION -> {
                line(.1f, .7f, .9f, .7f)
                for (position in listOf(.1f, .9f)) {
                    line(position, .55f, position, .9f)
                    line(position - .025f, .78f, position + .025f, .62f)
                }
            }
            StudioShape.PATH -> {
                val first = nodes.first()
                moveTo(x(first.x), y(first.y))
                fun connect(a: StudioPathNode, b: StudioPathNode) {
                    cubicTo(x(a.outgoingX), y(a.outgoingY), x(b.incomingX), y(b.incomingY), x(b.x), y(b.y))
                }
                nodes.zipWithNext { from, to -> connect(from, to) }
                if (closed) { connect(nodes.last(), first); close() }
            }
        }
    }

    companion object {
        fun default(shape: StudioShape) = if (shape == StudioShape.PATH) StudioVectorGeometry(shape, nodes = listOf(
            StudioPathNode(.15f, .72f, outgoingX = .16f, outgoingY = .2f),
            StudioPathNode(.55f, .24f, incomingX = .32f, incomingY = .17f, outgoingX = .78f, outgoingY = .28f),
            StudioPathNode(.86f, .7f, incomingX = .83f, incomingY = .48f),
        ), closed = false) else StudioVectorGeometry(shape)
    }
}

enum class StudioLengthUnit(val label: String, val millimetres: Double) {
    PX("px", 0.0), PT("pt", 25.4 / 72), MM("mm", 1.0), CM("cm", 10.0), IN("in", 25.4);
    fun fromPixels(pixels: Double, dpi: Int) = if (this == PX) pixels else pixels * 25.4 / dpi / millimetres
    fun toPixels(value: Double, dpi: Int) = if (this == PX) value else value * millimetres * dpi / 25.4
}

/** A bounded expression field supports measurements, simple ratios and relative offsets. */
internal fun studioQuantity(input: String, previous: Double): Double? {
    val text = input.trim().replace(',', '.')
    val operation = Regex("""^([+-]?\d+(?:\.\d+)?)(?:\s*([*/])\s*([+-]?\d+(?:\.\d+)?))?$""").matchEntire(text) ?: return null
    val value = operation.groupValues[1].toDoubleOrNull() ?: return null
    val operand = operation.groupValues[3].toDoubleOrNull()
    val result = when (operation.groupValues[2]) {
        "/" -> value / (operand ?: return null)
        "*" -> value * (operand ?: return null)
        else -> if (text.startsWith('+')) previous + value else value
    }
    return result.takeIf { it.isFinite() && kotlin.math.abs(it) <= 1_000_000.0 }
}
