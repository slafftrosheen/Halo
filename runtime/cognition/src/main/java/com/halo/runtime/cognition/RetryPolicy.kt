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
        // True exponential backoff: 100ms, 200ms, 400ms, 800ms, 1600ms...
        val delayMs = 100L * (1 shl (attempt - 1).coerceAtLeast(0))
        return delayMs.milliseconds
    }
}
