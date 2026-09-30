package com.halo.runtime.cognition.agent

/**
 * MINING-P1-001 run-scoped token accounting.
 *
 * `InferenceLedger` remains the process-wide operational projection and is NOT replaced. This is
 * the per-run safety budget, and it deliberately keeps three different facts apart:
 *
 * - `estimatedInputTokens`  — Halo's own bounded estimate of what it sent.
 * - `reportedInputTokens`   — what the provider actually reported (nullable: most local runtimes do
 *                             not report anything).
 * - `reportedOutputTokens`  — what the provider actually reported.
 *
 * Estimated must never be presented as reported. Old conversation runs must never be folded into
 * the next run's budget, which is why accounting is created per run and never shared.
 */
data class AgentRunTokenLedger(
    val runId: String,
    val estimatedInputTokens: Long = 0L,
    val reportedInputTokens: Long? = null,
    val reportedOutputTokens: Long? = null,
    val generationCount: Int = 0,
) {
    /** Best available input figure, labelled so callers cannot mistake its provenance. */
    val bestInputTokens: Long get() = reportedInputTokens ?: estimatedInputTokens

    val inputIsEstimated: Boolean get() = reportedInputTokens == null

    fun summaryLine(): String = buildString {
        append("estimatedInput=$estimatedInputTokens")
        append(" reportedInput=${reportedInputTokens ?: "unreported"}")
        append(" reportedOutput=${reportedOutputTokens ?: "unreported"}")
        append(" generations=$generationCount")
    }
}

/** Mutable accumulator owned by one [AgentRunGuard] for one run. */
class AgentRunTokenAccounting(private val runId: String) {
    private var estimatedInput = 0L
    private var reportedInput: Long? = null
    private var reportedOutput: Long? = null
    private var generations = 0

    /** Halo's own bounded estimate for one provider request. */
    fun noteEstimatedPrompt(tokens: Long) {
        if (tokens <= 0L) return
        estimatedInput += tokens
        generations++
    }

    /** Provider-reported usage. Absent values leave the previous fact untouched. */
    fun noteProviderReported(inputTokens: Long?, outputTokens: Long?) {
        inputTokens?.let { reportedInput = (reportedInput ?: 0L) + it }
        outputTokens?.let { reportedOutput = (reportedOutput ?: 0L) + it }
    }

    fun snapshot(): AgentRunTokenLedger = AgentRunTokenLedger(
        runId = runId,
        estimatedInputTokens = estimatedInput,
        reportedInputTokens = reportedInput,
        reportedOutputTokens = reportedOutput,
        generationCount = generations,
    )
}
