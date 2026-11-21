package com.halo.sdk.provider

/** Bounded source ranges for speech. Keeps consecutive complete paragraphs in one request. */
object SpeechParagraphs {
    const val MAX_REQUEST_CHARS = 3_000
    data class Section(val text: String, val start: Int, val end: Int)

    private val paragraphBreak = Regex("\\r?\\n[^\\S\\r\\n]*\\r?\\n")

    fun sections(text: String, maxChars: Int = MAX_REQUEST_CHARS, packParagraphs: Boolean = true): Sequence<Section> = sequence {
        require(maxChars in 128..MAX_REQUEST_CHARS)
        var start = 0
        while (start < text.length) {
            while (start < text.length && text[start].isWhitespace()) start++
            if (start == text.length) break
            val limit = start + minOf(maxChars, text.length - start)
            val paragraphEnd = if (packParagraphs) null else paragraphBreak.find(text, start)?.range?.first
            var end = when {
                paragraphEnd != null && paragraphEnd <= limit -> paragraphEnd
                limit == text.length -> limit
                else -> findBoundary(text, start, limit)
            }
            if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
            val next = end
            while (end > start && text[end - 1].isWhitespace()) end--
            if (end > start) yield(Section(text.substring(start, end), start, end))
            start = next
        }
    }

    private fun findBoundary(text: String, start: Int, limit: Int): Int {
        var lineBreakSeen = false
        var sentenceEnd: Int? = null
        var wordEnd: Int? = null
        val middle = start + (limit - start) / 2
        for (index in limit - 1 downTo start + 1) {
            val character = text[index]
            if (character == '\n') {
                if (lineBreakSeen) return index
                lineBreakSeen = true
            } else if (!character.isWhitespace()) {
                lineBreakSeen = false
            }
            if (index >= middle) {
                if (sentenceEnd == null && character in ".!?…。！？" && text[index + 1].isWhitespace())
                    sentenceEnd = index + 1
                if (wordEnd == null && character.isWhitespace()) wordEnd = index
            }
        }
        return sentenceEnd ?: wordEnd ?: limit
    }
}
