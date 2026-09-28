package com.halo.runtime.cognition.tools

/**
 * AGENTIC-001 bounded agent loop.
 *
 * A 0.6B local model will happily call the same tool forever. The limits below
 * are enforced by the turn executor and the cognitive turn loop, never by the
 * model's own judgement.
 */
data class ToolLoopLimits(
    /** Provider/tool rounds inside one interaction. */
    val maxToolRounds: Int = 6,
    /** Tool calls executed in a single tool turn. */
    val maxToolCallsPerTurn: Int = 8,
    /** How often the identical tool + canonical arguments pair may run in one run. */
    val maxRepeatedIdenticalCalls: Int = 2,
    /** Tool schemas shown to a small local model at once. */
    val maxLocalExposedSchemas: Int = 12,
) {
    companion object {
        val DEFAULT = ToolLoopLimits()
    }
}
