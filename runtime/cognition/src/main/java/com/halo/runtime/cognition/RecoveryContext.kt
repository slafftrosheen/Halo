package com.halo.runtime.cognition

import com.halo.icl.ontology.Goal
import com.halo.runtime.cognition.ExecutionPlan
import com.halo.runtime.cognition.Task

data class RecoveryContext(
    val goal: Goal,
    val plan: ExecutionPlan,
    val task: Task?,
    val attempt: Int,
    val deviceState: String? = null,
    val networkState: String? = null
)

sealed interface RecoveryAction {

    data class FallbackToRules(
        val fallbackGoal: Goal
    ) : RecoveryAction

    data class NotifyUser(
        val message: String
    ) : RecoveryAction

    data object AbortToIdle : RecoveryAction

    data class RetryExecution(
        val delayMs: Long = 0
    ) : RecoveryAction
}