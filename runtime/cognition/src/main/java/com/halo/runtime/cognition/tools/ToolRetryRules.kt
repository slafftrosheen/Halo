package com.halo.runtime.cognition.tools

/**
 * Runtime-side replay/non-retryable rule for tool execution.
 *
 * Rule: a tool with confirmation requirements, external side effects or unsafe
 * replay semantics is never transparently retried. Only SAFE replay tools whose
 * declared retry policy explicitly permits the structured failure kind may be
 * retried, with a bounded attempt count.
 *
 * This is a pure decision helper; callers (session loop, future durable executor)
 * keep ownership of scheduling.
 */
object ToolRetryRules {

    /** Upper bound on automatic retry attempts regardless of declaration. */
    const val MAX_AUTOMATIC_ATTEMPTS = 3

    fun mayAutomaticallyRetry(descriptor: ToolDescriptor, failureKind: ToolFailureKind): Boolean {
        val security = descriptor.security
        if (security.replaySafety != ReplaySafety.SAFE) return false
        if (security.risk !in setOf(ToolRiskClass.READ_ONLY, ToolRiskClass.REVERSIBLE)) return false
        if (security.confirmation == ConfirmationPolicy.ALWAYS) return false

        val retry = descriptor.execution.retry
        if (retry.maxAttempts <= 1) return false
        return failureKind in retry.retryableFailureKinds
    }

    /** Effective attempt count including the first attempt; always >= 1. */
    fun effectiveAttempts(descriptor: ToolDescriptor): Int =
        if (mayAutomaticallyRetry(descriptor, ToolFailureKind.TRANSIENT)) {
            descriptor.execution.retry.maxAttempts.coerceIn(1, MAX_AUTOMATIC_ATTEMPTS)
        } else {
            1
        }
}
