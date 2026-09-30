package com.halo.runtime.cognition.turn

/**
 * MINING-P1-001 streaming speech.
 *
 * The chunker itself is dumb on purpose; the safety rule lives in [SpeechSourceKind]. Reference
 * implementations (Voice Agent `SentenceExtractor`) chunk on sentence boundaries. Halo adds the
 * constraint that only the *terminal user response* may be spoken: provider tool requests, tool
 * results, reasoning and approval metadata must never reach the speaker.
 *
 * [TtsSanitizer] is applied to every chunk after it is cut, so a chunk can never leak a `<think>`
 * block or a tool envelope even when the boundary happens to fall mid-envelope.
 */
enum class SpeechSourceKind {
    /** The assistant's final answer to the user. The only speakable source. */
    TERMINAL_RESPONSE,

    /** A provider tool request / tool result / reasoning / approval payload. Never speakable. */
    NON_TERMINAL,
}

data class StreamingSpeechConfig(
    /** A chunk must have at least this many words, unless it is force-flushed. */
    val minWords: Int = 3,
    /** A chunk must have at least this many characters, unless it is force-flushed. */
    val minChars: Int = 12,
    /** Hard ceiling on buffered characters before a boundary is forced. */
    val maxBufferedChars: Int = 320,
    /** Hard ceiling on buffered words before a boundary is forced. */
    val maxBufferedWords: Int = 80,
) {
    init {
        require(minWords in 1..40) { "minWords must stay small and bounded" }
        require(minChars in 1..200) { "minChars must stay small and bounded" }
        require(maxBufferedChars >= minChars) { "maxBufferedChars must exceed minChars" }
    }

    companion object {
        val DEFAULT = StreamingSpeechConfig()
    }
}

/**
 * Incremental chunker. Feed it deltas of one generation; it returns the chunks that are ready to be
 * spoken, in order. Chunks are already sanitized and trimmed.
 *
 * State belongs to one response: create a chunker per spoken response, never per application.
 */
class StreamingSpeechChunker(
    private val config: StreamingSpeechConfig = StreamingSpeechConfig.DEFAULT,
    private val sanitize: (String) -> String = TtsSanitizer::sanitize,
) {

    private val buffer = StringBuilder()

    /** Deltas from a non-terminal source are dropped, not buffered: they are not speech. */
    fun accept(delta: String, kind: SpeechSourceKind): List<String> {
        if (kind != SpeechSourceKind.TERMINAL_RESPONSE) return emptyList()
        if (delta.isEmpty()) return emptyList()
        buffer.append(delta)
        return drain(force = false)
    }

    /** Call once the terminal response is complete. */
    fun flush(): List<String> = drain(force = true)

    fun pendingChars(): Int = buffer.length

    fun reset() = buffer.clear()

    private fun drain(force: Boolean): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        val text = buffer.toString()
        var index = 0
        while (index < text.length) {
            val character = text[index]
            val boundary = isBoundary(character)
            if (!boundary) {
                index++
                continue
            }
            // Include trailing whitespace after punctuation in the candidate chunk.
            var end = index + 1
            while (end < text.length && text[end].isWhitespace()) end++
            val candidate = text.substring(start, end)
            if (worthSpeaking(candidate) || force) {
                speakable(candidate)?.let(chunks::add)
                start = end
            }
            index = end
        }

        val remainder = text.substring(start)
        // Forced boundaries keep latency bounded without speaking a fragment mid-word.
        if (start > 0 && (force || remainder.length >= config.maxBufferedChars)) {
            if (force) {
                speakable(remainder)?.let(chunks::add)
                buffer.setLength(0)
            } else {
                val cut = breakPoint(remainder)
                val head = remainder.substring(0, cut)
                speechSafe(head)?.let { chunks.add(sanitize(it)) }
                buffer.setLength(0)
                buffer.append(remainder.substring(cut))
            }
        } else if (start > 0) {
            buffer.setLength(0)
            buffer.append(remainder)
        } else if (force) {
            buffer.setLength(0)
        }
        return chunks
    }

    private fun worthSpeaking(candidate: String): Boolean {
        val words = candidate.count { it.isWhitespace() } + 1
        val chars = candidate.count { !it.isWhitespace() }
        if (chars >= config.maxBufferedChars) return true
        if (chars < config.minChars && words < config.minWords) return false
        return chars >= config.minChars
    }

    private fun speakable(candidate: String): String? = speechSafe(candidate)?.let(sanitize)

    private fun speechSafe(candidate: String): String? =
        candidate.takeIf { it.any(Char::isLetterOrDigit) }?.takeIf { it.isNotBlank() }

    /** Last sensible break point for a forced chunk: end of a sentence, else a word boundary. */
    private fun breakPoint(text: String): Int {
        val lastBoundary = text.indexOfLast(::isBoundary)
        if (lastBoundary > 0) return lastBoundary + 1
        val lastSpace = text.lastIndexOf(' ')
        return if (lastSpace > 0) lastSpace + 1 else text.length
    }

    /**
     * Sentence and clause boundaries across Latin and CJK punctuation, plus em/en dash boundaries
     * where a spoken pause is natural.
     */
    private fun isBoundary(character: Char): Boolean = character in BOUNDARIES

    private companion object {
        val BOUNDARIES: Set<Char> = setOf(
            // Sentence punctuation
            '.', '!', '?', '\n',
            // Clause punctuation (Latin)
            ',', ';', ':',
            // CJK sentence + clause punctuation
            '\u3002', '\uFF01', '\uFF1F', '\uFF0C', '\uFF1B', '\uFF1A', '\u3001',
            // Dash boundaries
            '\u2014', '\u2013',
            // CJK ellipsis
            '\u2026',
        )
    }
}
