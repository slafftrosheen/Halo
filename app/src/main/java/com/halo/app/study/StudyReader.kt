package com.halo.app.study

/**
 * Sentence segmentation for read-along narration.
 *
 * Providers may supply verified playback word ranges; otherwise the reader highlights sentences.
 * Original character offsets are retained rather than estimated. Segmentation is
 * locale-tolerant: CJK full stops and ellipses are terminators, and coverage beyond the bounded sentence count must be disclosed.
 */
object SentenceSegmenter {
    data class Segment(val text: String, val start: Int, val end: Int)
    fun ranges(text: String): List<Segment> {
        var cursor=0
        return split(text).map { sentence ->
            val start=text.indexOf(sentence,cursor).takeIf { it >= 0 } ?: cursor
            cursor=start+sentence.length
            Segment(sentence,start,cursor)
        }
    }
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
