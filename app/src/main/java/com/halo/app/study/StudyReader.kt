package com.halo.app.study

/**
 * Sentence segmentation for read-along narration.
 *
 * The speech provider does not expose word timings through Halo's portable contract, so the reader
 * highlights the sentence it is speaking rather than pretending to track words. Segmentation is
 * locale-tolerant: CJK full stops and ellipses are terminators, and the segmenter never drops text.
 */
object SentenceSegmenter {
    private const val MAX_SENTENCE_CHARACTERS = 320
    private const val MAX_SENTENCES = 400

    private val terminators = charArrayOf('.', '!', '?', '。', '！', '？', '…', ';', '；', '\n')

    fun split(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val sentences = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            val candidate = current.toString().trim()
            current.setLength(0)
            if (candidate.isEmpty()) return
            if (candidate.length <= MAX_SENTENCE_CHARACTERS) {
                sentences += candidate
            } else {
                // Very long runs (no punctuation, e.g. a pasted paragraph) are split on spaces so a
                // single utterance never becomes unmanageably long.
                var start = 0
                while (start < candidate.length) {
                    val end = (start + MAX_SENTENCE_CHARACTERS).coerceAtMost(candidate.length)
                    val breakAt = candidate.lastIndexOf(' ', end).takeIf { it > start } ?: end
                    sentences += candidate.substring(start, breakAt).trim()
                    start = breakAt
                }
            }
        }

        trimmed.forEach { character ->
            current.append(character)
            if (terminators.contains(character)) flush()
        }
        flush()
        return sentences.filter(String::isNotEmpty).take(MAX_SENTENCES)
    }

    /** Index of the sentence containing [offset], or the last sentence when past the end. */
    fun indexForOffset(sentences: List<String>, offset: Int): Int {
        if (sentences.isEmpty()) return -1
        var cursor = 0
        sentences.forEachIndexed { index, sentence ->
            cursor += sentence.length + 1
            if (offset < cursor) return index
        }
        return sentences.lastIndex
    }
}
