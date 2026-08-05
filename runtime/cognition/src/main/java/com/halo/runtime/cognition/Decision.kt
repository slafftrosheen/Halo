package com.halo.runtime.cognition

import com.halo.sdk.runtime.ActionPlan

sealed interface Decision

data object DoNothing : Decision

data class ExecutePlan(
    val plan: ActionPlan
) : Decision

data class AskUser(
    val message: String
) : Decision

data class ThinkMore(
    val reason: String
) : Decision
