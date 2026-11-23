package com.halo.app.data.studio

import java.io.InputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A bounded reader for the supportable ASCII DXF subset. Coordinates use the file's declared units. */
internal object StudioDxfImport {
    const val MAX_BYTES = 2 * 1024 * 1024
    private data class Tag(val code: Int, val value: String)
    private data class Point(val x: Double, val y: Double)
    private data class Outline(val points: List<Point>, val closed: Boolean, val name: String, val color: Int)

    fun read(source: InputStream): StudioDocument {
        val buffer = ByteArray(8192)
        val content = java.io.ByteArrayOutputStream()
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            require(content.size() + count <= MAX_BYTES)
            content.write(buffer, 0, count)
        }
        val bytes = content.toByteArray()
        require(bytes.isNotEmpty())
        val lines = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            .removePrefix("\uFEFF").trimEnd().lines()
        require(lines.size in 8..120_000 && lines.size % 2 == 0)
        val tags = lines.chunked(2).map { (key, value) ->
            Tag(requireNotNull(key.trim().toIntOrNull()), value.trim())
        }
        require(tags.last() == Tag(0, "EOF"))
        val unitIndex = tags.indexOf(Tag(9, "$" + "INSUNITS"))
        val unit = tags.getOrNull(unitIndex + 1).takeIf { unitIndex >= 0 && it?.code == 70 }?.value?.toIntOrNull()
        val mmPerUnit = when (unit) {
            1 -> 25.4
            2 -> 304.8
            4 -> 1.0
            5 -> 10.0
            6 -> 1000.0
            else -> throw IllegalArgumentException("DXF requires declared millimetres, centimetres, metres, inches, or feet")
        }
        val entityIndex = tags.indices.firstOrNull { index ->
            tags[index] == Tag(2, "ENTITIES") && tags.getOrNull(index - 1) == Tag(0, "SECTION")
        } ?: throw IllegalArgumentException("No DXF entities")
        val end = tags.indexOfFirstAfter(entityIndex) { it == Tag(0, "ENDSEC") }
        require(end > entityIndex)
        val outlines = mutableListOf<Outline>()
        var index = entityIndex + 1
        while (index < end) {
            val entity = tags[index]
            require(entity.code == 0 && outlines.size < StudioDocument.MAX_LAYERS)
            val following = tags.indexOfFirstAfter(index) { it.code == 0 }.coerceAtMost(end)
            require(following > index)
            outlines += parseEntity(entity.value, tags.subList(index + 1, following), mmPerUnit)
            index = following
        }
        require(outlines.isNotEmpty())
        return document(outlines)
    }

    private fun List<Tag>.indexOfFirstAfter(index: Int, predicate: (Tag) -> Boolean): Int =
        (index + 1 until size).firstOrNull { predicate(this[it]) } ?: -1

    private fun parseEntity(kind: String, fields: List<Tag>, mmPerUnit: Double): Outline {
        fun number(raw: String): Double = requireNotNull(raw.toDoubleOrNull())
            .let { it * mmPerUnit }.also { require(it.isFinite() && abs(it) <= 1_000_000) }
        fun length(tag: Int) = number(fields.first { it.code == tag }.value)
        val name = fields.firstOrNull { it.code == 8 }?.value.orEmpty().take(94).ifBlank { "DXF" }
        val color = fields.firstOrNull { it.code == 420 }?.value?.toIntOrNull()?.and(0xFFFFFF)?.or(0xFF000000.toInt())
            ?: 0xFF18203A.toInt()
        require(fields.none { (it.code in setOf(30, 31, 38, 39, 40, 41, 42, 43) && number(it.value) != 0.0) ||
            (it.code in setOf(210, 220) && number(it.value) != 0.0) ||
            (it.code == 230 && it.value.toDoubleOrNull() != 1.0) })
        val outline = when (kind) {
            "LINE" -> Outline(listOf(Point(length(10), length(20)), Point(length(11), length(21))), false, name, color)
            "LWPOLYLINE" -> {
                var x: Double? = null
                val points = mutableListOf<Point>()
                fields.forEach { tag ->
                    if (tag.code == 10) { require(x == null); x = number(tag.value) }
                    if (tag.code == 20) { points += Point(requireNotNull(x), number(tag.value)); x = null }
                }
                require(x == null && points.size in 2..2048)
                val count = fields.firstOrNull { it.code == 90 }?.value?.toIntOrNull()
                require(count == points.size)
                val flags = fields.firstOrNull { it.code == 70 }?.value?.toIntOrNull() ?: 0
                require(flags and 129 == flags)
                Outline(points, flags and 1 != 0, name, color)
            }
            else -> throw IllegalArgumentException("Unsupported DXF entity: $kind")
        }
        val points = simplify(outline.points)
        require(points.size in (if (outline.closed) 3 else 2)..128)
        return outline.copy(points = points)
    }

    private fun simplify(points: List<Point>): List<Point> {
        val retained = BooleanArray(points.size)
        retained[0] = true
        retained[points.lastIndex] = true
        val spans = ArrayDeque<Pair<Int, Int>>()
        spans.addLast(0 to points.lastIndex)
        while (spans.isNotEmpty()) {
            val (from, through) = spans.removeLast()
            val a = points[from]
            val b = points[through]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val squared = dx * dx + dy * dy
            var best = -1
            var threshold = .08
            for (i in from + 1 until through) {
                val p = points[i]
                val ratio = if (squared == 0.0) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / squared).coerceIn(0.0, 1.0)
                val distance = hypot(p.x - a.x - dx * ratio, p.y - a.y - dy * ratio)
                if (distance > threshold) {
                    best = i
                    threshold = distance
                }
            }
            if (best >= 0) {
                retained[best] = true
                spans.addLast(from to best)
                spans.addLast(best to through)
            }
        }
        return points.filterIndexed { index, _ -> retained[index] }
    }

    private fun document(outlines: List<Outline>): StudioDocument {
        val points = outlines.flatMap(Outline::points)
        val originX = points.minOf { it.x } - 1
        val originY = points.maxOf { it.y } + 1
        val spanX = points.maxOf { it.x } - originX + 1
        val spanY = originY - points.minOf { it.y } + 1
        val dpi = (96 downTo 36).firstOrNull { candidate ->
            val w = ceil(StudioLengthUnit.MM.toPixels(spanX, candidate))
            val h = ceil(StudioLengthUnit.MM.toPixels(spanY, candidate))
            w <= 4096 && h <= 4096 && w * h <= 12_000_000
        } ?: throw IllegalArgumentException("DXF dimensions exceed the Studio page limits")
        val width = ceil(StudioLengthUnit.MM.toPixels(spanX, dpi)).toInt()
        val height = ceil(StudioLengthUnit.MM.toPixels(spanY, dpi)).toInt()
        fun normalizedX(mm: Double) = (StudioLengthUnit.MM.toPixels(mm - originX, dpi) / width * 1000).roundToInt()
        fun normalizedY(mm: Double) = (StudioLengthUnit.MM.toPixels(originY - mm, dpi) / height * 1000).roundToInt()
        val layers = outlines.mapIndexed { index, item ->
            val local = item.points.map { point -> normalizedX(point.x) to normalizedY(point.y) }
            val x = local.minOf { it.first }
            val y = local.minOf { it.second }
            val w = (local.maxOf { it.first } - x).coerceAtLeast(1)
            val h = (local.maxOf { it.second } - y).coerceAtLeast(1)
            val nodes = local.map { (px, py) -> StudioPathNode((px - x) / w.toFloat(), (py - y) / h.toFloat()) }
            val outline = if (item.closed) nodes + nodes.first() else nodes
            require(outline.distinct().size >= 2 && outline.size <= 128)
            StudioLayer(name = item.name + " " + (index + 1), kind = StudioLayerKind.RECTANGLE,
                bounds = StudioRect(x, y, w, h), color = item.color,
                vector = StudioVectorGeometry(StudioShape.PATH, nodes = outline, closed = false))
        }
        return StudioDocument(width, height, layers = layers, dpi = dpi).also(StudioDocument::requireValid)
    }
}
