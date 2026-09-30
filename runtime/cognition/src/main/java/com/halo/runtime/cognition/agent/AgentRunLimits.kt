package com.halo.runtime.cognition.agent

/**
 * MINING-P1-001 bounded agent execution.
 *
 * A 0.6B-class local model will happily repeat an action forever. These are the
 * *orchestration* ceilings for one agent run. Provider/model output limits still apply
 * independently: a run limit is never a substitute for a provider limit, and it is never
 * replicated as a magic number inside a turn loop.
 *
 * Harvested conceptually from Android-Agent `harness-improve`
 * (`RunTokenLedger`, `ToolOscillationDetector`, `BreakerLedger`, thermal gating). No code
 * is copied and Halo's own `AgentStep` journal remains execution truth.
 */
data class AgentRunLimits(
    /** Wall-clock ceiling for the whole run, measured from [AgentRunGuard.start]. */
    val maxWallClockMs: Long,
    /** Hard upper bound on provider<->tool rounds in one run. */
    val maxToolRounds: Int,
    /** How often one identical (toolId + canonical arguments digest) pair may be prepared. */
    val maxRepeatedToolCalls: Int,
    /** Consecutive terminal tool failures before the run stops. */
    val maxToolFailureStreak: Int,
    /** Estimated prompt/context tokens allowed across the run, or null for no estimate ceiling. */
    val maxEstimatedInputTokens: Int?,
    /** Provider-reported output tokens allowed across the run, or null for no ceiling. */
    val maxOutputTokens: Int?,
    /** Longest repeating tool-call period the oscillation detector considers (A A A, A B A B, ...). */
    val oscillationMaxPeriod: Int = 4,
    /** How many times the period must repeat after its first occurrence before tripping. */
    val oscillationMinRepeats: Int = 2,
    /** Minimum normalized window length for generation-repetition observation. */
    val generationMinWindowChars: Int = 24,
    /** How many times that window must repeat before the run is considered stuck. */
    val generationMinRepeats: Int = 3,
    val maxUiActionsPerRun: Int = 12,
    val maxScrollsPerRun: Int = 6,
    val maxRepeatedSameUiAction: Int = 3,
) {
    init {
        require(maxWallClockMs > 0) { "A run must have a positive wall-clock budget" }
        require(maxToolRounds > 0) { "A run must have a positive tool-round budget" }
        require(maxToolFailureStreak > 0) { "A run must have a positive failure-streak budget" }
        require(oscillationMaxPeriod in 1..8) { "Oscillation period must stay small and bounded" }
        require(oscillationMinRepeats >= 1) { "Oscillation requires at least one repetition" }
        require(generationMinRepeats >= 2) { "A single repeated phrase is not a loop" }
        require(maxUiActionsPerRun > 0 && maxScrollsPerRun > 0 && maxRepeatedSameUiAction > 0)
    }

    companion object {
        val DELEGATED = AgentRunLimits(45_000L, 4, 1, 2, 12_000, 2_000)
        /** Voice/chat interaction: the user is waiting. */
        val INTERACTIVE = AgentRunLimits(
            maxWallClockMs = 90_000L,
            maxToolRounds = 8,
            maxRepeatedToolCalls = 2,
            maxToolFailureStreak = 3,
            maxEstimatedInputTokens = 24_000,
            maxOutputTokens = 6_000,
        )

        /** Unattended routine execution: bounded, but allowed to take longer. */
        val ROUTINE = AgentRunLimits(
            maxWallClockMs = 240_000L,
            maxToolRounds = 4,
            maxRepeatedToolCalls = 1,
            maxToolFailureStreak = 2,
            maxEstimatedInputTokens = 16_000,
            maxOutputTokens = 3_000,
        )

        /** Host-initiated/system work: shortest leash, no user available to unblock it. */
        val SYSTEM = AgentRunLimits(
            maxWallClockMs = 45_000L,
            maxToolRounds = 3,
            maxRepeatedToolCalls = 1,
            maxToolFailureStreak = 2,
            maxEstimatedInputTokens = 12_000,
            maxOutputTokens = 2_000,
        )

        private val byKind = mapOf(
            AgentRunKind.INTERACTIVE to INTERACTIVE,
            AgentRunKind.ROUTINE to ROUTINE,
            AgentRunKind.SYSTEM to SYSTEM,
        )

        /** Bounded defaults per run kind; never one value hardcoded across the source tree. */
        fun forKind(kind: AgentRunKind): AgentRunLimits = byKind[kind] ?: INTERACTIVE
    }
}
