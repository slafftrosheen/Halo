package com.halo.runtime.cognition.agent

/**
 * MINING-P1-001 tool oscillation.
 *
 * A small local model oscillates: `A A A A`, `A B A B A B`, sometimes with a period of three or
 * four distinct calls. Optionally-guessed heuristics are not used here; a rotation is only
 * reported when the recorded rotation repeats for at least `minRepeats` extra cycles.
 *
 * Only `toolId` and the canonical *arguments digest* are retained. Raw arguments are never
 * stored, logged or exported.
 */
data class ToolOscillationMatch(
    val period: Int,
    val repeats: Int,
    /** Digest over the rotation's compact `toolId:digest8` tokens. Never user content. */
    val patternDigest: String,
    /** The single tool id of a period-1 loop, or null for a multi-call rotation. */
    val singleToolId: String?,
)

class ToolOscillationDetector(
    private val maxPeriod: Int = 4,
    private val minRepeats: Int = 2,
    private val maxTracked: Int = 64,
) {

    /** Compact identity per call: enough to detect a loop, useless as content. */
    private val history = ArrayDeque<String>()

    fun record(toolId: String, argumentsDigest: String) {
        history.addLast("$toolId:${argumentsDigest.take(8)}")
        while (history.size > maxTracked) history.removeFirst()
    }

    /** Clears the window after a successful meaningful action, so old noise cannot trip it. */
    fun reset() = history.clear()

    fun detect(): ToolOscillationMatch? {
        if (history.size < 2) return null
        val items = history.toList()
        for (period in 1..maxPeriod) {
            val required = period * (minRepeats + 1)
            if (items.size < required) continue
            val window = items.subList(items.size - required, items.size)
            val base = window.subList(0, period)
            val repeats = window.chunked(period).all { it == base }
            if (!repeats) continue
            val pattern = base.joinToString("|")
            return ToolOscillationMatch(
                period = period,
                repeats = required / period,
                // Reuses the existing canonical digest helper instead of adding a second one.
                patternDigest = ToolArgumentCanonicalizer.sha256Hex(pattern).take(12),
                singleToolId = if (period == 1) base.first().substringBefore(':') else null,
            )
        }
        return null
    }

    /** Snapshot of the compact rotation window (toolId:digest8 tokens). Never user content. */
    internal fun recentCalls(): List<String> = history.toList()

    /** Bounded, content-free description for the journal and Activity. */
    fun describe(match: ToolOscillationMatch): String = if (match.period == 1) {
        "repeated ${match.singleToolId ?: "the same call"} ${match.repeats} times"
    } else {
        "repeated a ${match.period}-call rotation ${match.repeats} times"
    }
}
