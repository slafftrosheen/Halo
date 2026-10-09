package com.halo.app.data.notebook

import java.util.Base64

/** Per-file, per-page vector marks stored on the linked Canvas card; original file is untouched. */
data class CanvasFileMarkup(val title: String, val pages: Map<Int, String>)

object CanvasFileMarkupCodec {
    private const val HEADER = "halo-markup:v1|"
    const val MAX_PAGES = 16
    private const val MAX_PAYLOAD = 1_500_000

    fun decode(content: String): CanvasFileMarkup {
        if (!content.startsWith(HEADER)) return CanvasFileMarkup(content, emptyMap())
        if (content.length > MAX_PAYLOAD) return CanvasFileMarkup(content, emptyMap())
        return runCatching {
            val parts = content.removePrefix(HEADER).split('|')
            require(parts.size in 1..(MAX_PAGES + 1))
            val title = un64(parts.first())
            require(title.length <= 300)
            val pages = mutableMapOf<Int, String>()
            parts.drop(1).forEach { part ->
                val separator = part.indexOf(':')
                require(separator > 0)
                val page = part.substring(0, separator).toInt()
                require(page in 1..9999 && page !in pages)
                val strokes = un64(part.substring(separator + 1))
                require(CanvasSketchCodec.decode(strokes).isNotEmpty())
                pages[page] = strokes
            }
            CanvasFileMarkup(title, pages.toMap())
        }.getOrDefault(CanvasFileMarkup(content, emptyMap()))
    }

    /** Atomic multi-page upsert. An empty value clears marks on that page. */
    fun merge(previous: String, updates: Map<Int, String>): String {
        val base = decode(previous)
        require(!previous.startsWith(HEADER) || base.title != previous) { "Unrecognized markup; refusing to overwrite" }
        val pages = base.pages.toMutableMap()
        for ((page, value) in updates) {
            require(page in 1..9999)
            if (value.isBlank()) pages.remove(page) else {
                require(CanvasSketchCodec.decode(value).isNotEmpty())
                pages[page] = value
            }
        }
        require(pages.size <= MAX_PAGES)
        if (pages.isEmpty()) return base.title
        val packed = HEADER + encode64(base.title) + pages.entries.sortedBy { it.key }
            .joinToString("") { (page, strokes) -> "|$page:" + encode64(strokes) }
        require(packed.length <= MAX_PAYLOAD)
        return packed
    }

    private fun encode64(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun un64(value: String): String = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}
