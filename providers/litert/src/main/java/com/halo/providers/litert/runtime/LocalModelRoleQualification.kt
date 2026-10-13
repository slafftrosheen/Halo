package com.halo.providers.litert.runtime

import com.halo.sdk.provider.*

const val ROLE_PROBE_SUITE_VERSION = 2

enum class LocalRoleProbeVerdict { PASS, FAIL, NOT_APPLICABLE, NOT_RUN }
enum class LocalRoleProbeFailure {
    ARTIFACT_UNAVAILABLE, ARTIFACT_CHANGED, UNSUPPORTED_MODEL_TASK, RESOURCE_BUSY, LOAD_FAILED, GENERATION_FAILED,
    EMPTY_OUTPUT, WRONG_ANSWER, INSTRUCTION_NOT_FOLLOWED, TOOL_PROTOCOL_UNAVAILABLE,
    TOOL_CALL_INVALID, TOOL_CONTINUATION_FAILED, BACKEND_CHANGED, CANCELLED,
    WARM_RESIDENCY_NOT_PROVEN,
}
data class LocalRoleProbeResult(val role: AiModelRole, val verdict: LocalRoleProbeVerdict,
    val failure: LocalRoleProbeFailure? = null)
data class LocalFastQualification(
    val coldTtftMs: Long?, val warmTtftMs: List<Long>, val successfulWarmSamples: Int,
    val medianWarmTtftMs: Long?, val minWarmTtftMs: Long?, val maxWarmTtftMs: Long?,
    val effectiveBackend: String?, val verdict: LocalRoleProbeVerdict,
    val failure: LocalRoleProbeFailure? = null,
    val warmResidencyProven: Boolean? = null,
) {
    val latency: String? get() = medianWarmTtftMs?.let { when {
        it <= 4000 -> "GOOD"; it <= 8000 -> "SLOW"; else -> "VERY_SLOW"
    } }
}
data class LocalChatQualification(val passed: Int, val total: Int,
    val instructionPass: Boolean, val arithmeticPass: Boolean, val contextRecallPass: Boolean,
    val verdict: LocalRoleProbeVerdict, val failure: LocalRoleProbeFailure? = null)
data class LocalReasoningQualification(val passed: Int, val total: Int, val reasoningChars: Int,
    val verdict: LocalRoleProbeVerdict, val failure: LocalRoleProbeFailure? = null)
data class LocalAgenticQualification(val verdict: LocalRoleProbeVerdict,
    val protocol: ToolCallProtocol, val initialToolPass: Boolean = false,
    val continuationPass: Boolean = false, val provenanceRetained: Boolean = false,
    val failure: LocalRoleProbeFailure? = null)
data class LocalVisionQualification(val verdict: LocalRoleProbeVerdict,
    val eligibility: String = "NO_VERIFIED_IMAGE_CAPABILITY")
data class LocalModelRoleQualification(
    val modelId: String, val artifactFingerprint: String, val artifactBytes: Long,
    val requestedBackend: LocalComputeBackend?, val backend: LocalComputeBackend?,
    val contextTokens: Int?, val threads: Int?, val gpuLayers: Int?, val loadMs: Long?,
    val fast: LocalFastQualification? = null, val chat: LocalChatQualification? = null,
    val reasoning: LocalReasoningQualification? = null, val agentic: LocalAgenticQualification? = null,
    val vision: LocalVisionQualification? = null, val failure: LocalRoleProbeFailure? = null,
    val startedAtMs: Long, val completedAtMs: Long, val memoryPressure: String? = null,
) {
    companion object {
        /** Explicit allowlist: never serialize prompts, responses, reasoning, identifiers or arguments. */
        fun report(rows: List<LocalModelRoleQualification>, sourceVersion: String): String = buildString {
            appendLine("Halo source=$sourceVersion roleProbeSuite=$ROLE_PROBE_SUITE_VERSION")
            for (r in rows) {
                appendLine("model=${r.modelId} sha=${r.artifactFingerprint.take(12)} bytes=${r.artifactBytes}")
                appendLine("requested=${r.requestedBackend} backend=${r.backend} context=${r.contextTokens} threads=${r.threads} gpuLayers=${r.gpuLayers} loadMs=${r.loadMs} memoryPressure=${r.memoryPressure}")
                r.fast?.let { appendLine("FAST ${it.verdict} ${it.successfulWarmSamples}/3 coldMs=${it.coldTtftMs} warmMs=${it.warmTtftMs} medianMs=${it.medianWarmTtftMs} minMs=${it.minWarmTtftMs} maxMs=${it.maxWarmTtftMs} ${it.latency ?: ""} failure=${it.failure} residency=${it.warmResidencyProven ?: "UNKNOWN"}") }
                r.chat?.let { appendLine("CHAT ${it.verdict} ${it.passed}/${it.total} failure=${it.failure}") }
                r.reasoning?.let { appendLine("REASONING ${it.verdict} ${it.passed}/${it.total} reasoningChars=${it.reasoningChars} failure=${it.failure}") }
                r.agentic?.let { appendLine("AGENTIC ${it.verdict} tools=${it.protocol} initial=${it.initialToolPass} continuation=${it.continuationPass} retained=${it.provenanceRetained} failure=${it.failure}") }
                r.vision?.let { appendLine("VISION ${it.verdict} ${it.eligibility}") }
                appendLine("failure=${r.failure}")
            }
        }
    }
}

/** Narrow v1 normalizer. One terminal period is accepted; additional prose always fails. */
internal fun probeAnswer(text: String): String {
    var answer = text.trim()
    if (answer.length >= 2 && ((answer.first() == '\"' && answer.last() == '\"') ||
        (answer.first() == '\'' && answer.last() == '\''))) answer = answer.substring(1, answer.length - 1).trim()
    return answer.removeSuffix(".")
}
internal fun probeMatches(text: String, expected: String): Boolean = probeAnswer(text).equals(expected, ignoreCase = true)
internal fun fastQualification(samples: List<RuntimeGenerationResult?>, backend: String?): LocalFastQualification {
    val primer = samples.firstOrNull()
    val warm = samples.drop(1).filterNotNull().filter { it.text.isNotBlank() && it.timeToFirstTokenMs >= 0 && it.coldStart != true }
    val times = warm.map { it.timeToFirstTokenMs }
    val reportsResidency = samples.any { it?.coldStart != null }
    val residency = if (reportsResidency) primer?.coldStart == true && samples.drop(1).filterNotNull().all { it.coldStart == false } else true
    val pass = primer?.text?.isNotBlank() == true && residency && times.size == 3
    return LocalFastQualification(primer?.timeToFirstTokenMs, times, times.size,
        times.sorted().takeIf { it.size == 3 }?.get(1), times.minOrNull(), times.maxOrNull(), backend,
        if (pass) LocalRoleProbeVerdict.PASS else LocalRoleProbeVerdict.FAIL,
        if (!residency) LocalRoleProbeFailure.WARM_RESIDENCY_NOT_PROVEN else if (!pass) LocalRoleProbeFailure.GENERATION_FAILED else null,
        warmResidencyProven = residency.takeIf { reportsResidency })
}
