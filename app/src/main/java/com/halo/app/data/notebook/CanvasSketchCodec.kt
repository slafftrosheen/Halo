package com.halo.app.data.notebook

/** Compatibility projection used by existing repositories, exports and local OCR. */
data class CanvasSketchPoint(val x: Int, val y: Int)

object CanvasSketchCodec {
    private const val PREFIX = "sketch:v1|"
    const val MAX_STROKES = 64
    const val MAX_POINTS_PER_STROKE = 256

    /** Legacy callers still produce v1. New editors use the portable InkCodec directly. */
    fun encode(strokes: List<List<CanvasSketchPoint>>): String {
        require(strokes.size <= MAX_STROKES)
        require(strokes.all { it.isNotEmpty() && it.size <= MAX_POINTS_PER_STROKE })
        return PREFIX + strokes.joinToString("|") { stroke ->
            stroke.joinToString(";") { point ->
                "${point.x.coerceIn(0, 1000)},${point.y.coerceIn(0, 1000)}"
            }
        }
    }

    /** Read-only XY projection; editable v2 callers must retain the full InkDocument. */
    fun decode(value: String): List<List<CanvasSketchPoint>> {
        if (value.startsWith("sketch:v2|")) return com.halo.sdk.runtime.ink.InkCodec.decode(value)
            ?.strokes?.map { stroke -> stroke.points.map { CanvasSketchPoint(it.x, it.y) } }.orEmpty()
        if (!value.startsWith(PREFIX) || value.length > 200_000) return emptyList()
        val raw = value.removePrefix(PREFIX)
        if (raw.isBlank()) return emptyList()
        val strokes = raw.split("|", limit = MAX_STROKES + 1)
        if (strokes.size > MAX_STROKES) return emptyList()
        return strokes.map { line ->
            val entries = line.split(";", limit = MAX_POINTS_PER_STROKE + 1)
            if (entries.isEmpty() || entries.size > MAX_POINTS_PER_STROKE) return emptyList()
            entries.map { entry ->
                val parts = entry.split(",", limit = 3)
                if (parts.size != 2) return emptyList()
                val x = parts[0].toIntOrNull() ?: return emptyList()
                val y = parts[1].toIntOrNull() ?: return emptyList()
                if (x !in 0..1000 || y !in 0..1000) return emptyList()
                CanvasSketchPoint(x, y)
            }
        }
    }
}
