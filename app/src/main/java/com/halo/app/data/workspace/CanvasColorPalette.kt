package com.halo.app.data.workspace

/** Small, deterministic offline dominant-color extractor. Pixels are ARGB integers. */
object CanvasColorPalette {
    private data class Bucket(var count: Int = 0, var red: Long = 0, var green: Long = 0, var blue: Long = 0)

    fun extract(pixels: IntArray, limit: Int = 6): List<String> {
        require(limit in 1..12)
        val buckets = HashMap<Int, Bucket>()
        for (pixel in pixels) {
            val alpha = pixel ushr 24
            if (alpha < 128) continue // Transparent padding is not a design color.
            val red = pixel ushr 16 and 255
            val green = pixel ushr 8 and 255
            val blue = pixel and 255
            val key = (red shr 4 shl 8) or (green shr 4 shl 4) or (blue shr 4)
            val bucket = buckets.getOrPut(key) { Bucket() }
            bucket.count++
            bucket.red += red
            bucket.green += green
            bucket.blue += blue
        }
        val candidates = buckets.entries.sortedWith(compareByDescending<Map.Entry<Int, Bucket>> { it.value.count }
            .thenBy { it.key })
            .map { entry ->
                val bucket = entry.value
                Triple((bucket.red / bucket.count).toInt(), (bucket.green / bucket.count).toInt(),
                    (bucket.blue / bucket.count).toInt())
            }
        val chosen = mutableListOf<Triple<Int, Int, Int>>()
        for (color in candidates) {
            if (chosen.none { other ->
                val r = color.first - other.first
                val g = color.second - other.second
                val b = color.third - other.third
                r * r + g * g + b * b < 36 * 36
            }) chosen += color
            if (chosen.size >= limit) break
        }
        return chosen.map { color ->
            "#" + listOf(color.first, color.second, color.third).joinToString("") {
                it.toString(16).padStart(2, '0').uppercase(java.util.Locale.ROOT)
            }
        }
    }
}
