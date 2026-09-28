package com.halo.runtime.cognition.turn

import com.halo.runtime.cognition.agent.ToolRejectionCode
import com.halo.runtime.cognition.tools.ToolResultEnvelope

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
        /*
         * AGENTIC-001: rejections carry the same machine-readable header as
         * successes so a small local model can distinguish "I may retry with
         * different arguments" from "a permission is missing, tell the user".
         */
        val modelResult = ToolResultEnvelope.rejected(
            reasonCode = code.name,
            retryable = retryable,
            requiresUserAction = requiresUserAction,
            details = humanReason.replace('\n', ' ').take(512),
        )
        return RuntimeToolRejection(
            reasonCode = code,
            humanReason = humanReason,
            retryable = retryable,
            requiresUserAction = requiresUserAction,
            modelResult = modelResult,
        )
    }
}
