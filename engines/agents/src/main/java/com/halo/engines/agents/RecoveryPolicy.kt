package com.halo.engines.agents

import com.halo.icl.ontology.Goal
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.RuntimeStage

// RecoveryContext provides rich context for intelligent recovery decisions
data class RecoveryContext(
    val observation: Observation,
    val goal: Goal?,
    val plan: com.halo.runtime.cognition.ExecutionPlan?,
    val task: Task?,
    val attempt: Int,
    val deviceState: String? = null,
    val networkState: String? = null
)

sealed interface RecoveryAction {
    data class FallbackToRules(val fallbackGoal: Goal) : RecoveryAction
    data class NotifyUser(val message: String) : RecoveryAction
    object AbortToIdle : RecoveryAction
    // NEW: Retry the execution after a short delay
    data class RetryExecution(val delayMs: Long = 0) : RecoveryAction
}

interface RecoveryPolicy {
    suspend fun determineRecovery(failure: Outcome, context: RecoveryContext): RecoveryAction
}

class DefaultRecoveryPolicy : RecoveryPolicy {
    override suspend fun determineRecovery(failure: Outcome, context: RecoveryContext): RecoveryAction {
        return when (failure) {
            is Outcome.RetryableFailure -> {
                // If it was retryable but still bubbled up, we likely exceeded retries/timeouts
                // But if retryCount is low, we can retry execution
                if (context.attempt < 2) {
                    RecoveryAction.RetryExecution(delayMs = 1000)
                } else {
                    RecoveryAction.NotifyUser("The operation timed out after ${context.attempt} attempts.")
                }
            }
            is Outcome.PermanentFailure -> {
                RecoveryAction.NotifyUser(failure.reason)
            }
            is Outcome.Cancelled -> {
                RecoveryAction.AbortToIdle
            }
            else -> RecoveryAction.AbortToIdle
        }
    }
}
