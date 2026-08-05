package com.halo.engines.execution.service

import com.halo.runtime.cognition.*

class RetryManager {
    suspend fun withRetry(policy: RetryPolicy, block: suspend () -> TaskResult): TaskResult {
        var attempts = 0
        var currentDelay = policy.initialDelayMs

        while (attempts <= policy.maxRetries) {
            val result = block()
            if (result !is TaskResult.Retry) {
                return result
            }

            attempts++
            if (attempts > policy.maxRetries) {
                return TaskResult.Failure("Exceeded max retries: ${policy.maxRetries}")
            }

            kotlinx.coroutines.delay(currentDelay)
            currentDelay = (currentDelay * policy.backoffMultiplier).toLong().coerceAtMost(policy.maxDelayMs)
        }
        
        return TaskResult.Failure("Retry loop failed unexpectedly")
    }
}
