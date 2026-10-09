package com.halo.app.data.notebook

/**
 * Portable, bounded v1 format for pen input on Canvas. Coordinates are integer
 * thousandths of the sketch rectangle, independent of screen density / viewport.
 * Nothing here needs Android drawing classes or a database migration.
 */
data class CanvasSketchPoint(val x: Int, val y: Int)

object CanvasSketchCodec {
    private const val PREFIX = "sketch:v1|"
    const val MAX_STROKES = 64
    const val MAX_POINTS_PER_STROKE = 256

    fun encode(strokes: List<List<CanvasSketchPoint>>): String {
        require(strokes.size <= MAX_STROKES)
        require(strokes.all { it.isNotEmpty() && it.size <= MAX_POINTS_PER_STROKE })
        return PREFIX + strokes.joinToString("|") { stroke ->
            stroke.joinToString(";") { point ->
                "${point.x.coerceIn(0, 1000)},${point.y.coerceIn(0, 1000)}"
            }
        }
    }

    /** Malformed, future-version or oversized payloads are treated as unreadable drawings. */
    fun decode(value: String): List<List<CanvasSketchPoint>> {
        if (!value.startsWith(PREFIX) || value.length > 200_000) return emptyList()
        val raw = value.removePrefix(PREFIX)
        if (raw.isBlank()) return emptyList()
        val strokes = raw.split("|")
        if (strokes.size > MAX_STROKES) return emptyList()
        return strokes.map { line ->
            val entries = line.split(";")
            if (entries.isEmpty() || entries.size > MAX_POINTS_PER_STROKE) return emptyList()
            entries.map { entry ->
                val parts = entry.split(",")
                if (parts.size != 2) return emptyList()
                val x = parts[0].toIntOrNull() ?: return emptyList()
                val y = parts[1].toIntOrNull() ?: return emptyList()
                if (x !in 0..1000 || y !in 0..1000) return emptyList()
                CanvasSketchPoint(x, y)
            }
        }
    }
}
