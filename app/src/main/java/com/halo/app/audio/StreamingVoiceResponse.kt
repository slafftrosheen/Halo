package com.halo.app.audio

import com.halo.runtime.cognition.turn.*
import com.halo.sdk.runtime.StreamingSpeechPrepared
import kotlinx.coroutines.channels.Channel
import java.security.MessageDigest

/** One bounded, process-only response. Retains a prefix digest and length, never its raw text. */
internal class StreamingVoiceResponse(val prepared: StreamingSpeechPrepared) {
    val chunks = Channel<String>(8)
    private val chunker = StreamingSpeechChunker(StreamingSpeechConfig(completeSentencesOnly = true))
    private val digest = MessageDigest.getInstance("SHA-256")
    private var prefixLength = 0
    private var closed = false
    private var suspended = false
    @Volatile var replacedByFullResponse = false
        private set
    @Volatile var halted = false
        private set
    @Volatile var audible = false
        private set
    @Volatile var abandoned = false
        private set
    var chunkCount = 0
        private set
    @Volatile var audibleChunks = 0
        private set
    @Volatile var synthesisReadyAt: Long? = null
        private set
    @Synchronized fun synthesized() { if (synthesisReadyAt == null) synthesisReadyAt = System.nanoTime() }
    val openedAt = System.nanoTime()
    var firstTokenAt: Long? = null
        private set
    var firstChunkAt: Long? = null
        private set
    var audibleAt: Long? = null
        private set
    @Synchronized fun started(): Boolean {
        if (abandoned) return false
        audible = true
        audibleChunks++
        if (audibleAt == null) audibleAt = System.nanoTime()
        return true
    }
    @Synchronized fun accept(delta: String) {
        if (closed || suspended || abandoned) return
        if (firstTokenAt == null) firstTokenAt = System.nanoTime()
        // A split markup/envelope must never be streamed to a cloud speech provider.
        if (delta.any { it == '<' || it == '{' || it == '`' }) { suspended = true; chunker.reset(); return }
        if (delta.length + chunker.pendingChars() > 4096) { suspended = true; chunker.reset(); return }
        chunker.accept(delta, SpeechSourceKind.TERMINAL_RESPONSE).forEach { chunk ->
            if (!suspended && chunks.trySend(chunk).isSuccess) {
                val normalized = normalize(chunk)
                val appended = (if (prefixLength == 0) "" else " ") + normalized
                digest.update(appended.toByteArray(Charsets.UTF_8))
                prefixLength += appended.length
                chunkCount++
                if (firstChunkAt == null) firstChunkAt = System.nanoTime()
            } else suspended = true
        }
    }
    /** Final response is the sole authority for the remainder. No replay after audible output. */
    @Synchronized fun finish(finalText: String): Finish {
        if (abandoned) return Finish.STOP
        closed = true
        if (!audible) { replacedByFullResponse = true; abandon(); return Finish.FULL_RESPONSE }
        val final = normalize(TtsSanitizer.sanitize(finalText))
        val prefix = final.take(prefixLength)
        val actual = MessageDigest.getInstance("SHA-256").digest(prefix.toByteArray(Charsets.UTF_8))
        if (prefix.length != prefixLength || !actual.contentEquals(digest.digest())) {
            abandon(); return Finish.MISMATCH
        }
        val suffix = final.drop(prefixLength).trim()
        // Consumer drains queued chunks before this final suffix (sent outside the event collector).
        remainder = suffix
        chunker.reset()
        return Finish.SUFFIX
    }
    var remainder: String = ""
        private set
    @Synchronized fun failBeforeAudio() { halted = true; suspended = true; chunker.reset(); chunks.cancel() }
    @Synchronized fun abandon() { abandoned = true; closed = true; chunker.reset(); chunks.cancel(); remainder = "" }
    enum class Finish { FULL_RESPONSE, SUFFIX, MISMATCH, STOP }
    companion object { fun normalize(text: String) = text.trim().replace(Regex("\\s+"), " ") }
}
