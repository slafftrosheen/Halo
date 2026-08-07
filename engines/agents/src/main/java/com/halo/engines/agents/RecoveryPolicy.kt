package com.halo.engines.agents

import com.halo.runtime.cognition.Goal
import com.halo.runtime.cognition.Outcome
import com.halo.sdk.runtime.RuntimeStage

sealed interface RecoveryAction {
    data class FallbackToRules(val fallbackGoal: Goal) : RecoveryAction
    data class NotifyUser(val message: String) : RecoveryAction
    object AbortToIdle : RecoveryAction
}

interface RecoveryPolicy {
    suspend fun determineRecovery(failure: Outcome, stage: RuntimeStage): RecoveryAction
}

class DefaultRecoveryPolicy : RecoveryPolicy {
    override suspend fun determineRecovery(failure: Outcome, stage: RuntimeStage): RecoveryAction {
        return when (failure) {
            is Outcome.RetryableFailure -> {
                // If it was retryable but still bubbled up, we likely exceeded retries/timeouts
                RecoveryAction.NotifyUser("The operation timed out.")
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
