package com.halo.app.data.workspace

/** Bounded, ordered, one-based PDF page selections. */
object CanvasPdfPageSelection {
    const val MAX_PAGES = 6

    fun parse(input: String, totalPages: Int): List<Int>? {
        if (totalPages !in 1..1000000 || input.length !in 1..90) return null
        val output = linkedSetOf<Int>()
        for (token in input.split(',')) {
            val parts = token.trim().replace('–', '-').split('-')
            if (parts.size !in 1..2) return null
            val from = parts[0].trim().toIntOrNull() ?: return null
            val to = if (parts.size == 2) parts[1].trim().toIntOrNull() ?: return null else from
            if (from !in 1..totalPages || to !in from..totalPages || to - from >= MAX_PAGES) return null
            for (page in from..to) {
                if (!output.add(page) || output.size > MAX_PAGES) return null
            }
        }
        return output.toList().takeIf { it.isNotEmpty() }
    }
}
