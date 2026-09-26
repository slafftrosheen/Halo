package com.halo.runtime.cognition.turn

import com.halo.runtime.cognition.agent.ToolRejectionCode

data class RuntimeToolRejection(
    val reasonCode: ToolRejectionCode,
    val humanReason: String,
    val retryable: Boolean,
    val requiresUserAction: Boolean,
    val modelResult: String,
)

object ToolRejectionFactory {
    fun create(
        code: ToolRejectionCode,
        humanReason: String,
        retryable: Boolean = false,
        requiresUserAction: Boolean = false,
    ): RuntimeToolRejection {
        val modelResult = "tool_result: status=rejected reason_code=${code.name} retryable=$retryable requires_user_action=$requiresUserAction\nreason: $humanReason"
        return RuntimeToolRejection(
            reasonCode = code,
            humanReason = humanReason,
            retryable = retryable,
            requiresUserAction = requiresUserAction,
            modelResult = modelResult,
        )
    }
}
