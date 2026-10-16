package com.halo.app.audio

/**
 * Bounded punctuation/word-aware sections for user-initiated reading.
 *
 * Unlike assistant text generation, a long note is already final.  No
 * model inference or side effects are involved: splitting happens immediately
 * before the single routed playback flow consumes it.
 */
internal object SpeechReadingSegments {
    fun chunks(text: String, maxChars: Int = 720): Sequence<String> = sequence {
        require(maxChars in 128..2000)
        var offset = 0
        while (offset < text.length) {
            while (offset < text.length && text[offset].isWhitespace()) offset++
            if (offset >= text.length) break
            val limit = (offset + maxChars).coerceAtMost(text.length)
            var end = limit
            if (limit < text.length) {
                val minimum = offset + maxChars / 2
                var boundary = -1
                for (i in (limit - 1) downTo minimum) {
                    if (text[i] == '\n' || text[i] in ".!?;:") {
                        boundary = i + 1
                        break
                    }
                }
                if (boundary < minimum) {
                    for (i in (limit - 1) downTo minimum) {
                        if (text[i].isWhitespace()) {
                            boundary = i + 1
                            break
                        }
                    }
                }
                if (boundary >= minimum) end = boundary
            }
            val section = text.substring(offset, end).trim()
            if (section.isNotEmpty()) yield(section)
            offset = end
        }
    }
}
