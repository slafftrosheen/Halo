package com.halo.runtime.cognition.agent

import com.halo.sdk.runtime.thermal.ThermalSeverity
import java.time.Instant
import java.util.UUID

/**
 * MINING-P1-001 bounded agent execution.
 *
 * A formal, testable run-level safety layer. Halo's `CognitiveSession` / `AgentRun` journal remain
 * authoritative for execution truth; this guard only decides *whether the run may keep going* and
 * records a durable [AgentStep.RunGuardTriggered] when it says stop.
 *
 * Deliberately not exception-message substring matching: every reason is a typed enum derived from
 * counters the guard owns.
 */
enum class RunGuardReason {
    DEADLINE_EXCEEDED,
    TOOL_FAILURE_STREAK,
    TOOL_OSCILLATION,
    GENERATION_REPETITION,
    TOKEN_BUDGET_EXCEEDED,
    THERMAL_LIMIT,
    TOO_MANY_TOOL_ROUNDS,
    REPEATED_TOOL_CALL,
    UI_ACTION_LIMIT,
    UI_SCROLL_LIMIT,
}

/** Content-free evidence. Hashes, counts and ids only — never user content. */
data class RunGuardEvidence(
    val round: Int,
    val toolId: String? = null,
    val evidenceHash: String? = null,
    val elapsedMs: Long = 0L,
    val count: Int = 0,
) {
    /** Bounded, safe-to-log description. */
    val summary: String
        get() = buildString {
            append("round=$round")
            append(" elapsedMs=$elapsedMs")
            if (count > 0) append(" count=$count")
            toolId?.let { append(" tool=$it") }
            evidenceHash?.let { append(" evidence=$it") }
        }
}

sealed interface RunGuardDecision {
    data object Continue : RunGuardDecision

    data class Stop(
        val reason: RunGuardReason,
        val evidence: RunGuardEvidence,
    ) : RunGuardDecision
}

/** Filesystem/clock-free policy source so the guard stays portable and deterministic. */
interface AgentRunClock {
    fun nowMs(): Long

    object System : AgentRunClock {
        override fun nowMs(): Long = java.lang.System.currentTimeMillis()
    }
}

/** Thermal input, injected rather than imported, so `runtime:cognition` stays platform-free. */
fun interface AgentRunThermalGate {
    fun severity(): ThermalSeverity

    companion object {
        val NONE = AgentRunThermalGate { ThermalSeverity.NORMAL }
    }
}

/**
 * One guard instance per run. Not thread-safe by design: a run owns its guard, and all tool-loop
 * transitions happen on the run's own coroutine.
 */
class AgentRunGuard(
    val runId: String,
    val interactionId: String?,
    private val limits: AgentRunLimits,
    private val thermalGate: AgentRunThermalGate = AgentRunThermalGate.NONE,
    private val clock: AgentRunClock = AgentRunClock.System,
) {

    private val startedAtMs: Long = clock.nowMs()
    private val oscillation = ToolOscillationDetector(
        maxPeriod = limits.oscillationMaxPeriod,
        minRepeats = limits.oscillationMinRepeats,
    )
    private val generation = GenerationRepetitionDetector(
        minWindowChars = limits.generationMinWindowChars,
        minRepeats = limits.generationMinRepeats,
    )
    val tokens = AgentRunTokenAccounting(runId)

    private var thermalSeverity: ThermalSeverity = thermalGate.severity()
    private var failureStreak = 0
    private var toolRounds = 0
    private var repeatedCalls = 0
    private var lastStop: RunGuardDecision.Stop? = null

    val elapsedMs: Long get() = clock.nowMs() - startedAtMs
    val remainingMs: Long get() = (limits.maxWallClockMs - elapsedMs).coerceAtLeast(0)
    val currentToolRounds: Int get() = toolRounds
    val uiRepeatLimit: Int get() = limits.maxRepeatedSameUiAction
    val currentFailureStreak: Int get() = failureStreak
    val tokenSnapshot: AgentRunTokenLedger get() = tokens.snapshot()

    /** Re-read the injected thermal source; called before each round. */
    fun refreshThermal() {
        thermalSeverity = thermalGate.severity()
    }

    /**
     * Called before preparing one more provider/tool round.
     *
     * Order matters: a hard stop (deadline / thermal / round ceiling) is reported before softer
     * behavioural reasons so the user sees the most actionable cause.
     */
    fun beforeRound(): RunGuardDecision {
        lastStop?.let { return it }
        refreshThermal()
        checkThermal()?.let { return it }
        if (elapsedMs > limits.maxWallClockMs) {
            return stop(
                RunGuardReason.DEADLINE_EXCEEDED,
                RunGuardEvidence(round = toolRounds, elapsedMs = elapsedMs),
            )
        }
        if (toolRounds >= limits.maxToolRounds) {
            return stop(
                RunGuardReason.TOO_MANY_TOOL_ROUNDS,
                RunGuardEvidence(round = toolRounds, elapsedMs = elapsedMs, count = toolRounds),
            )
        }
        oscillation.detect()?.let { match ->
            return stop(
                RunGuardReason.TOOL_OSCILLATION,
                RunGuardEvidence(
                    round = toolRounds,
                    toolId = match.singleToolId,
                    evidenceHash = match.patternDigest,
                    elapsedMs = elapsedMs,
                    count = match.repeats,
                ),
            )
        }
        checkTokenBudget()?.let { return it }
        return RunGuardDecision.Continue
    }

    /**
     * Records one prepared tool call. Returns a stop decision when the exact same call (tool id +
     * canonical arguments digest) has already been prepared [AgentRunLimits.maxRepeatedToolCalls]
     * times, or when the rotation has begun to oscillate.
     */
    fun onToolCallPrepared(toolId: String, argumentsDigest: String, uiAction: Boolean = false): RunGuardDecision {
        toolRounds++
        oscillation.record(toolId, argumentsDigest)
        repeatedCalls++
        val repeatCount = countOccurrencesOf(toolId, argumentsDigest)
        if (repeatCount > (if (uiAction) limits.maxRepeatedSameUiAction else limits.maxRepeatedToolCalls)) {
            return stop(
                RunGuardReason.REPEATED_TOOL_CALL,
                RunGuardEvidence(
                    round = toolRounds,
                    toolId = toolId,
                    evidenceHash = argumentsDigest.take(12),
                    elapsedMs = elapsedMs,
                    count = repeatCount,
                ),
            )
        }
        oscillation.detect()?.let { match ->
            return stop(
                RunGuardReason.TOOL_OSCILLATION,
                RunGuardEvidence(
                    round = toolRounds,
                    toolId = match.singleToolId,
                    evidenceHash = match.patternDigest,
                    elapsedMs = elapsedMs,
                    count = match.repeats,
                ),
            )
        }
        return RunGuardDecision.Continue
    }

    /** A terminal tool failure. Returns a stop when the streak ceiling is reached. */
    fun onToolFailure(toolId: String, failureKind: String?): RunGuardDecision {
        failureStreak++
        if (failureStreak >= limits.maxToolFailureStreak) {
            return stop(
                RunGuardReason.TOOL_FAILURE_STREAK,
                RunGuardEvidence(
                    round = toolRounds,
                    toolId = toolId,
                    evidenceHash = failureKind?.take(24),
                    elapsedMs = elapsedMs,
                    count = failureStreak,
                ),
            )
        }
        return RunGuardDecision.Continue
    }

    /** A successful meaningful action clears the streak and the oscillation window. */
    fun onToolSucceeded(preserveOscillation: Boolean = false) {
        failureStreak = 0
        if (!preserveOscillation) oscillation.reset()
    }
    /** Counts come from durable ACTING records, so restart cannot replenish action budgets. */
    fun beforeUiDispatch(actions: Int, scrolls: Int, scroll: Boolean): RunGuardDecision {
        lastStop?.let { return it }
        if (actions >= limits.maxUiActionsPerRun) return stop(RunGuardReason.UI_ACTION_LIMIT,
            RunGuardEvidence(toolRounds, count = actions, elapsedMs = elapsedMs))
        if (scroll && scrolls >= limits.maxScrollsPerRun) return stop(RunGuardReason.UI_SCROLL_LIMIT,
            RunGuardEvidence(toolRounds, count = scrolls, elapsedMs = elapsedMs))
        return RunGuardDecision.Continue
    }

    /** Bounded streaming observation. Only clearly pathological loops stop the run. */
    fun onGenerationDelta(delta: String): RunGuardDecision {
        generation.observe(delta)?.let { match ->
            return stop(
                RunGuardReason.GENERATION_REPETITION,
                RunGuardEvidence(
                    round = toolRounds,
                    elapsedMs = elapsedMs,
                    count = match.repeats,
                ),
            )
        }
        checkTokenBudget()?.let { return it }
        return RunGuardDecision.Continue
    }

    fun onGenerationCompleted() = generation.reset()

    /** Deadline check usable from a watchdog without preparing another round. */
    fun checkDeadline(): RunGuardDecision =
        if (elapsedMs > limits.maxWallClockMs) {
            stop(RunGuardReason.DEADLINE_EXCEEDED, RunGuardEvidence(round = toolRounds, elapsedMs = elapsedMs))
        } else {
            RunGuardDecision.Continue
        }

    private fun checkThermal(): RunGuardDecision.Stop? = when (thermalSeverity) {
        ThermalSeverity.CRITICAL,
        ThermalSeverity.EMERGENCY,
        -> stop(
            RunGuardReason.THERMAL_LIMIT,
            RunGuardEvidence(round = toolRounds, elapsedMs = elapsedMs),
        )
        else -> null
    }

    private fun checkTokenBudget(): RunGuardDecision.Stop? {
        val ledger = tokens.snapshot()
        val inputCeiling = limits.maxEstimatedInputTokens?.toLong()
        val outputCeiling = limits.maxOutputTokens?.toLong()
        val inputOver = inputCeiling != null && ledger.bestInputTokens > inputCeiling
        // Unreported output is never treated as zero, and never guessed.
        val outputOver = outputCeiling != null &&
            ledger.reportedOutputTokens != null &&
            ledger.reportedOutputTokens > outputCeiling
        return if (inputOver || outputOver) {
            stop(
                RunGuardReason.TOKEN_BUDGET_EXCEEDED,
                RunGuardEvidence(
                    round = toolRounds,
                    elapsedMs = elapsedMs,
                    count = (ledger.bestInputTokens + (ledger.reportedOutputTokens ?: 0L)).toInt(),
                ),
            )
        } else {
            null
        }
    }

    private fun countOccurrencesOf(toolId: String, argumentsDigest: String): Int =
        oscillation.recentCalls().count { it == "$toolId:${argumentsDigest.take(8)}" }

    private fun stop(reason: RunGuardReason, evidence: RunGuardEvidence): RunGuardDecision.Stop {
        val decision = RunGuardDecision.Stop(reason, evidence)
        if (lastStop == null) lastStop = decision
        return decision
    }

    /** The first stop reason, retained so the run outcome reports the root cause. */
    fun firstStop(): RunGuardDecision.Stop? = lastStop

    /** Builds the durable journal step for a stop decision. Content-free. */
    fun toJournalStep(stop: RunGuardDecision.Stop): AgentStep.RunGuardTriggered =
        AgentStep.RunGuardTriggered(
            id = AgentStepId(UUID.randomUUID().toString()),
            runId = AgentRunId(runId),
            createdAt = Instant.ofEpochMilli(clock.nowMs()),
            reason = stop.reason,
            round = stop.evidence.round,
            toolId = stop.evidence.toolId,
            evidenceHash = stop.evidence.evidenceHash,
            elapsedMs = stop.evidence.elapsedMs,
            summary = RunGuardMessages.summary(stop),
        )
}

/**
 * User-facing recovery copy. Enum names never reach Home, Activity or speech.
 */
object RunGuardMessages {
    fun summary(stop: RunGuardDecision.Stop): String = when (stop.reason) {
        RunGuardReason.TOOL_OSCILLATION -> "Repeated the same action pattern"
        RunGuardReason.REPEATED_TOOL_CALL -> "Repeated the same action"
        RunGuardReason.TOOL_FAILURE_STREAK -> "The same action kept failing"
        RunGuardReason.DEADLINE_EXCEEDED -> "Took longer than the allowed interactive limit"
        RunGuardReason.TOO_MANY_TOOL_ROUNDS -> "Used too many action rounds"
        RunGuardReason.GENERATION_REPETITION -> "Started repeating itself"
        RunGuardReason.TOKEN_BUDGET_EXCEEDED -> "Reached the run's processing budget"
        RunGuardReason.THERMAL_LIMIT -> "Stopped because the phone is too hot"
        RunGuardReason.UI_ACTION_LIMIT -> "Reached the screen action limit"
        RunGuardReason.UI_SCROLL_LIMIT -> "Reached the screen scroll limit"
    }

    /** Spoken/primary wording when a run was deliberately stopped. */
    fun userMessage(stop: RunGuardDecision.Stop): String = when (stop.reason) {
        RunGuardReason.TOOL_OSCILLATION, RunGuardReason.REPEATED_TOOL_CALL ->
            "I stopped because I was repeating the same actions."
        RunGuardReason.TOOL_FAILURE_STREAK ->
            "I stopped because the same action kept failing."
        RunGuardReason.DEADLINE_EXCEEDED ->
            "This task took longer than the allowed interactive limit."
        RunGuardReason.TOO_MANY_TOOL_ROUNDS ->
            "I stopped because this needed more steps than I should run at once."
        RunGuardReason.GENERATION_REPETITION ->
            "I stopped because I started repeating myself."
        RunGuardReason.TOKEN_BUDGET_EXCEEDED ->
            "I stopped because this task needed more processing than allowed."
        RunGuardReason.THERMAL_LIMIT ->
            "Local processing paused because the phone is too hot."
        RunGuardReason.UI_ACTION_LIMIT, RunGuardReason.UI_SCROLL_LIMIT ->
            "I stopped because this screen workflow needed too many actions."
    }
}
