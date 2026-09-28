package com.halo.providers.litert.runtime

/**
 * Separates internal reasoning markup from user-visible output.
 *
 * Reasoning-oriented artifacts (Qwen-style thinking modes, DeepSeek R1 distills) may emit
 * `<think>…</think>` blocks on the same content channel as the final answer. That markup must
 * never reach Android TTS, but the raw text is still valuable diagnostic evidence, so it is
 * retained separately instead of being discarded.
 *
 * The filter is chunk-safe: a tag split across two stream chunks is buffered rather than leaked.
 */
class LiteRtThinkingFilter(
    private val openTags: List<String> = DEFAULT_OPEN_TAGS,
    private val closeTags: List<String> = DEFAULT_CLOSE_TAGS,
) {

    private val raw = StringBuilder()
    private val reasoning = StringBuilder()
    private val buffer = StringBuilder()
    private var insideThinking = false

    val hasReasoning: Boolean get() = reasoning.isNotEmpty()

    /** Everything the model produced, including suppressed reasoning. */
    fun rawText(): String = raw.toString()

    /** Suppressed reasoning text (diagnostic only — never spoken). */
    fun reasoningText(): String = reasoning.toString()

    /** Feeds a stream chunk and returns only the visible part of it. */
    fun accept(chunk: String): String {
        if (chunk.isEmpty()) return ""
        raw.append(chunk)
        buffer.append(chunk)
        val emit = StringBuilder()
        drain(emit, flushAll = false)
        return emit.toString()
    }

    /** Flushes whatever remains once the stream has ended. */
    fun finish(): String {
        val emit = StringBuilder()
        drain(emit, flushAll = true)
        return emit.toString()
    }

    private fun drain(emit: StringBuilder, flushAll: Boolean) {
        while (true) {
            if (!insideThinking) {
                val hit = findTag(buffer, openTags)
                if (hit != null) {
                    emit.append(buffer, 0, hit.first)
                    buffer.delete(0, hit.first + hit.second.length)
                    insideThinking = true
                    continue
                }
                val remainder = buffer.toString()
                val holdback = if (flushAll) 0 else partialPrefixLength(remainder, openTags)
                val safe = remainder.length - holdback
                if (safe > 0) {
                    emit.append(remainder, 0, safe)
                    buffer.delete(0, safe)
                }
                return
            }

            val hit = findTag(buffer, closeTags)
            if (hit != null) {
                reasoning.append(buffer, 0, hit.first)
                buffer.delete(0, hit.first + hit.second.length)
                insideThinking = false
                continue
            }
            val remainder = buffer.toString()
            val keep = if (flushAll) 0 else partialPrefixLength(remainder, closeTags)
            val drop = remainder.length - keep
            if (drop > 0) {
                reasoning.append(remainder, 0, drop)
                buffer.delete(0, drop)
            }
            return
        }
    }

    private fun findTag(text: StringBuilder, tags: List<String>): Pair<Int, String>? {
        var bestIndex = -1
        var bestTag: String? = null
        for (tag in tags) {
            val index = text.indexOf(tag)
            if (index < 0) continue
            if (bestIndex < 0 || index < bestIndex || (index == bestIndex && tag.length > (bestTag?.length ?: 0))) {
                bestIndex = index
                bestTag = tag
            }
        }
        val tag = bestTag ?: return null
        return bestIndex to tag
    }

    /** Length of the longest trailing proper prefix of any tag, so partial tags are held back. */
    private fun partialPrefixLength(text: String, tags: List<String>): Int {
        var best = 0
        for (tag in tags) {
            val limit = minOf(tag.length - 1, text.length)
            for (length in limit downTo 1) {
                if (text.endsWith(tag.substring(0, length))) {
                    if (length > best) best = length
                    break
                }
            }
        }
        return best
    }

    companion object {
        val DEFAULT_OPEN_TAGS = listOf("<think>", "<thinking>")
        val DEFAULT_CLOSE_TAGS = listOf("</think>", "</thinking>")

        /** Non-streaming convenience: splits a complete response into visible text and reasoning. */
        fun split(text: String): Pair<String, String> {
            val filter = LiteRtThinkingFilter()
            val visible = filter.accept(text) + filter.finish()
            return visible to filter.reasoningText()
        }
    }
}
