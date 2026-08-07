package com.halo.runtime.cognition

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

interface RetryPolicy {
    suspend fun shouldRetry(attempt: Int, outcome: Outcome): Boolean
    fun nextDelay(attempt: Int, outcome: Outcome): Duration
}

class DefaultRetryPolicy(
    private val maxAttempts: Int = 3
) : RetryPolicy {
    
    override suspend fun shouldRetry(attempt: Int, outcome: Outcome): Boolean {
        if (attempt >= maxAttempts) return false
        
        return when (outcome) {
            is Outcome.RetryableFailure -> true
            is Outcome.PermanentFailure -> false
            is Outcome.Cancelled -> false
            else -> false
        }
    }

    override fun nextDelay(attempt: Int, outcome: Outcome): Duration {
        // True exponential backoff: 100ms, 200ms, 400ms...
        val delayMs = 100L * (1 shl (attempt - 1).coerceAtLeast(0))
        return delayMs.milliseconds
    }
}
