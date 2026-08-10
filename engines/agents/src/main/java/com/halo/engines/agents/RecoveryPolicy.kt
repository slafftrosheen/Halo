package com.halo.engines.agents

import com.halo.icl.ontology.Goal
import com.halo.runtime.cognition.ExecutionPlan
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.RecoveryAction
import com.halo.runtime.cognition.RecoveryContext
import com.halo.runtime.cognition.Task

interface RecoveryPolicy {

    suspend fun determineRecovery(
        failure: Outcome,
        context: RecoveryContext
    ): RecoveryAction
}

class DefaultRecoveryPolicy : RecoveryPolicy {

    override suspend fun determineRecovery(
        failure: Outcome,
        context: RecoveryContext
    ): RecoveryAction {

        return when (failure) {

            is Outcome.RetryableFailure -> {

                if (context.attempt < 2) {
                    RecoveryAction.RetryExecution(
                        delayMs = 500
                    )
                } else {
                    RecoveryAction.NotifyUser(
                        message = "The operation could not be completed after ${context.attempt} attempts."
                    )
                }
            }

            is Outcome.PermanentFailure -> {

                RecoveryAction.NotifyUser(
                    message = failure.reason
                )
            }

            is Outcome.Cancelled -> {

                RecoveryAction.AbortToIdle
            }

            else -> {

                RecoveryAction.AbortToIdle
            }
        }
    }
}