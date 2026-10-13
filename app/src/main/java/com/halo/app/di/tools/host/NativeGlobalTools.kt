package com.halo.app.di.tools.host

import com.halo.cognition.language.NativeGlobalAction
import com.halo.host.android.accessibility.AndroidGlobalActions
import com.halo.host.android.accessibility.HaloAccessibilityService
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*

/** Deterministic public global actions, with the same explicit grant and approval boundary as UI tools. */
class NativeGlobalTools : ToolExecutor {
    override fun definitions() = listOf(ToolDefinition("halo_native_global", "halo_native_global",
        "Request one fixed public Android global action. Requires manually granted accessibility and review. A screenshot dispatch is not proof a saved image exists.",
        mapOf("type" to "object", "properties" to mapOf("action" to mapOf("type" to "string", "enum" to NativeGlobalAction.entries.map { it.wireId })),
            "required" to listOf("action"), "additionalProperties" to false)))
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(message: String, kind: ToolRejectionKind) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, message, kind)
        if (toolCall.functionName != "halo_native_global") return reject("Unknown tool", ToolRejectionKind.TOOL_UNKNOWN)
        val action = toolCall.arguments["action"] as? String
        if (toolCall.arguments.keys != setOf("action") || NativeGlobalAction.entries.none { it.wireId == action })
            return reject("Unknown fixed action", ToolRejectionKind.INVALID_ARGUMENTS)
        val service = HaloAccessibilityService.instance ?: return reject("Grant accessibility manually first", ToolRejectionKind.PERMISSION_REQUIRED)
        val operation = AndroidGlobalActions.supported(service)[action]
            ?: return reject("Android does not advertise this global action", ToolRejectionKind.UNSUPPORTED_OPERATION)
        val id = AndroidGlobalActions.id(service, operation) ?: return reject("Global action became unavailable", ToolRejectionKind.RESOURCE_UNAVAILABLE)
        return try {
            if (!service.performGlobalAction(id)) return reject("Android rejected this global action", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
            ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                ToolResultEnvelope.success(ActionTruth.DISPATCHED, details = "action=$action; OS accepted dispatch; visible or saved effect unverified"), ActionTruth.DISPATCHED)
        } catch (_: SecurityException) { reject("Accessibility authority changed", ToolRejectionKind.PERMISSION_REQUIRED) }
    }
}
