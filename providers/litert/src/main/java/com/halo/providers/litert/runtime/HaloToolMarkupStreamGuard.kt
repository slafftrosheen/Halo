package com.halo.providers.litert.runtime

/**
 * Hold just enough streamed text to prevent tool-marker fragments from reaching
 * chat/TTS if a tool-free LiteRT response hallucinates HALO_PROMPT markup.
 *
 * Tool-enabled HALO_PROMPT turns are buffered to completion and validated by
 * HaloPromptToolProtocol instead; this guard preserves ordinary tool-free streaming.
 */
internal class HaloToolMarkupStreamGuard {
    private val pending = StringBuilder()
    private val holdChars = maxOf(HaloPromptToolProtocol.OPEN_TAG.length,
        HaloPromptToolProtocol.CLOSE_TAG.length)
    var rejectedMarkup: Boolean = false
        private set

    fun offer(token: String): String {
        if (rejectedMarkup) return ""
        pending.append(token)
        if (pending.contains("<halo_tool_call") ||
            pending.contains("</halo_tool_call")) {
            pending.setLength(0)
            rejectedMarkup = true
            return ""
        }
        val ready = pending.length - holdChars
        if (ready <= 0) return ""
        val accepted = pending.substring(0, ready)
        pending.delete(0, ready)
        return accepted
    }

    /** Return the un-emitted suffix only when no tool marker was observed. */
    fun finish(): String {
        if (rejectedMarkup) return ""
        return pending.toString().also { pending.setLength(0) }
    }
}
