package com.halo.runtime.cognition.executive

import java.util.UUID

enum class ExecutiveVerdict {
    Authorize,
    Pause,
    Cancel,
    RequireConfirmation
}

data class ExecutiveDecision(
    val goalId: UUID,
    val verdict: ExecutiveVerdict,
    val reasoning: String
)
