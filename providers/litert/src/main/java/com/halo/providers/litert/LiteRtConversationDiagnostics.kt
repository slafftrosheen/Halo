package com.halo.providers.litert

import com.halo.sdk.provider.LocalComputeBackend

/**
 * MINING-P0-001 how the private preface reached the model. Halo has exactly two real modes today;
 * there is deliberately no fabricated "merged fallback" mode that the code does not implement.
 *
 * The prompt-mediated tool preface is *folded into* the SDK system instruction by
 * `LiteRtLocalProvider.systemInstructionFor`, which is reported separately as
 * [LiteRtConversationDiagnostics.toolPrefaceFolded].
 */
enum class LiteRtSystemInstructionMode {
    NONE,

    /** Passed as `ConversationConfig.systemInstruction`. */
    SDK_SYSTEM_INSTRUCTION,
}

/**
 * Structured, content-free conversation diagnostic snapshot.
 *
 * Never contains the raw system instruction, only its length and a short fingerprint, so the
 * private preface can be compared across generations without being logged or exported.
 */
data class LiteRtConversationDiagnostics(
    val generationId: String?,
    val conversationGeneration: Long,
    val createdAtMs: Long?,
    val systemInstructionMode: LiteRtSystemInstructionMode,
    val systemInstructionChars: Int,
    val systemInstructionFingerprint: String?,
    val toolPrefaceFolded: Boolean,
    val initialMessageCount: Int,
    val toolSchemaCount: Int,
    val toolSchemaFingerprint: String?,
    val requestedBackend: LocalComputeBackend?,
    val effectiveBackend: LocalComputeBackend?,
    val requestedContextTokens: Int?,
    val effectiveContextTokens: Int?,
    val conversationCreateDurationMs: Long?,
    val firstTokenDurationMs: Long?,
    val lastRebuildReason: String?,
    val promptEchoSuspected: Boolean,
) {
    fun summaryLine(): String = buildString {
        append("system=${systemInstructionMode.name}")
        append(" chars=$systemInstructionChars")
        systemInstructionFingerprint?.let { append(" fp=$it") }
        append(" tools=$toolSchemaCount")
        append(" history=$initialMessageCount")
        effectiveBackend?.let { append(" backend=$it") }
        append(" echo=$promptEchoSuspected")
    }
}

/**
 * Bounded prompt-echo observation.
 *
 * Observation only: this batch does not terminate generation. The later P1 bounded-agent batch owns
 * the breaker policy. A long normalized window from the private preface appearing in generated text
 * is strong evidence of echo; the matched sequence itself is never logged or retained.
 */
object LiteRtPromptEcho {

    /** Conservative threshold for a "strong likely echo". */
    const val MIN_MATCHED_CHARS = 64

    /** Bounded work: at most this many preface windows and this much generated text are inspected. */
    private const val MAX_PREFACE_WINDOWS = 2_048
    private const val MAX_GENERATED_CHARS = 16_384

    fun suspected(generated: String, preface: String?): Boolean {
        if (preface.isNullOrBlank()) return false
        val normalizedPreface = normalize(preface)
        if (normalizedPreface.length < MIN_MATCHED_CHARS) return false
        val normalizedGenerated = normalize(generated).take(MAX_GENERATED_CHARS)
        if (normalizedGenerated.length < MIN_MATCHED_CHARS) return false

        val windows = HashSet<Long>()
        val limit = (normalizedPreface.length - MIN_MATCHED_CHARS).coerceAtMost(MAX_PREFACE_WINDOWS)
        for (start in 0..limit) {
            windows.add(windowHash(normalizedPreface, start))
        }
        val lastStart = normalizedGenerated.length - MIN_MATCHED_CHARS
        for (start in 0..lastStart) {
            if (windows.contains(windowHash(normalizedGenerated, start))) return true
        }
        return false
    }

    private fun normalize(text: String): String = text
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun windowHash(text: String, start: Int): Long {
        var hash = FNV_OFFSET
        var index = start
        val end = start + MIN_MATCHED_CHARS
        while (index < end) {
            hash = (hash xor text[index].code.toLong()) * FNV_PRIME
            index++
        }
        return hash
    }

    private const val FNV_OFFSET = -3750763034362895579L // 14695981039346656037 as signed
    private const val FNV_PRIME = 1099511628211L
}
