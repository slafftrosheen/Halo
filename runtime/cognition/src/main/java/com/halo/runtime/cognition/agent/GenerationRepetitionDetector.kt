package com.halo.runtime.cognition.agent

/**
 * MINING-P1-001 generation repetition.
 *
 * Local models get stuck emitting the same sentence forever. This detector is deliberately
 * conservative: it only reports a *consecutive, identical* normalized window repeated at least
 * [minRepeats] times, so ordinary writing (a phrase used twice, a list, a refrain) is not treated
 * as a loop.
 *
 * The repeated text is never returned or logged; only the window length and repeat count are.
 * This is observation feeding the run guard, not a style judge.
 */
data class GenerationRepetitionMatch(
    val windowChars: Int,
    val repeats: Int,
)

class GenerationRepetitionDetector(
    private val minWindowChars: Int = 24,
    private val minRepeats: Int = 3,
    private val maxWindowChars: Int = 200,
    private val maxBufferedChars: Int = 8_192,
) {

    private val buffer = StringBuilder()

    fun observe(delta: String): GenerationRepetitionMatch? {
        if (delta.isEmpty()) return null
        buffer.append(delta)
        if (buffer.length > maxBufferedChars) {
            buffer.delete(0, buffer.length - maxBufferedChars)
        }
        return detect()
    }

    fun reset() = buffer.clear()

    private fun detect(): GenerationRepetitionMatch? {
        val text = normalize(buffer)
        if (text.length < minWindowChars * minRepeats) return null
        // Only the tail can be a freshly-formed loop, and only the tail is bounded here.
        val tailLength = minOf(text.length, maxWindowChars * minRepeats)
        val tail = text.substring(text.length - tailLength)
        for (window in minWindowChars..minOf(maxWindowChars, tail.length / minRepeats)) {
            val unit = tail.substring(tail.length - window, tail.length)
            var repeats = 1
            var cursor = tail.length - window
            while (cursor - window >= 0 && tail.regionMatches(cursor - window, unit, 0, window)) {
                repeats++
                cursor -= window
                if (repeats >= minRepeats) return GenerationRepetitionMatch(window, repeats)
            }
        }
        return null
    }

    private fun normalize(source: StringBuilder): String = source
        .toString()
        .lowercase()
        .replace(WHITESPACE, " ")
        .trim()

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
