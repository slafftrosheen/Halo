package com.halo.runtime.cognition

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

interface RetryPolicy {
    suspend fun shouldRetry(attempt: Int, outcome: Outcome): Boolean
    fun nextDelay(attempt: Int, outcome: Outcome): Duration
    fun maxAttempts(outcome: Outcome): Int
}

class DefaultRetryPolicy(
    private val baseMaxAttempts: Int = 3
) : RetryPolicy {
    
    // Override to provide outcome-specific max attempts
    override fun maxAttempts(outcome: Outcome): Int {
        return when (outcome) {
            is Outcome.RetryableFailure -> {
                val reason = outcome.reason.lowercase()
                when {
                    reason.contains("network") || reason.contains("timeout") -> 5
                    reason.contains("camera") || reason.contains("busy") -> 2
                    reason.contains("spotify") || reason.contains("not installed") -> 0
                    else -> baseMaxAttempts
                }
            }
            is Outcome.PermanentFailure -> 0
            is Outcome.Cancelled -> 0
            else -> baseMaxAttempts
        }
    }

    override suspend fun shouldRetry(attempt: Int, outcome: Outcome): Boolean {
        if (attempt > maxAttempts(outcome)) return false
        
        return when (outcome) {
            is Outcome.RetryableFailure -> true
            is Outcome.PermanentFailure -> false
            is Outcome.Cancelled -> false
            else -> false
        }
    }

    override fun nextDelay(attempt: Int, outcome: Outcome): Duration {
        // True exponential backoff: 100ms, 200ms, 400ms, 800ms, 1600ms... capped at MAX_DELAY_MS.
        // The exponent is bounded before the shift is applied: `1 shl n` overflows into a negative
        // Int at n >= 31, which would otherwise turn a long retry chain into a negative delay.
        // Growth is also capped, so a high attempt count waits a fixed maximum instead of doubling
        // into hours. Deliberately not jittered: this is a single-client on-device runtime, so there
        // is no retry herd to spread, and provider-supplied Retry-After is honoured upstream.
        val exponent = (attempt - 1).coerceIn(0, MAX_BACKOFF_SHIFT)
        val delayMs = (BASE_DELAY_MS * (1L shl exponent)).coerceAtMost(MAX_DELAY_MS)
        return delayMs.milliseconds
    }

    private companion object {
        const val BASE_DELAY_MS = 100L

        /** Longest single wait; backoff stops growing here rather than doubling without limit. */
        const val MAX_DELAY_MS = 30_000L

        /** Highest exponent we will apply, keeping `1L shl exponent` far from overflow. */
        const val MAX_BACKOFF_SHIFT = 20
    }
}
