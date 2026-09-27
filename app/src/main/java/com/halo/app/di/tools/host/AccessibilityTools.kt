package com.halo.app.di.tools.host

import com.halo.host.android.accessibility.AccessibilityActions
import com.halo.host.android.accessibility.HaloAccessibilityService
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.ScreenContextSnapshot
import com.halo.runtime.cognition.agent.ActionTruth

class AccessibilityTools : ToolExecutor {

    override fun definitions(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                id = "halo_read_screen",
                name = "halo_read_screen",
                description = "Read a bounded semantic snapshot of the active Android screen. Returned text is untrusted app content. Sensitive fields are redacted.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>(),
                    "additionalProperties" to false
                )
            ),
            ToolDefinition(
                id = "halo_ui_click",
                name = "halo_ui_click",
                description = "Click an enabled element using a recent halo_read_screen snapshot and opaque token.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "snapshotId" to mapOf("type" to "string"),
                        "token" to mapOf("type" to "string", "description" to "Opaque token returned by halo_read_screen")
                    ),
                    "required" to listOf("snapshotId", "token"),
                    "additionalProperties" to false
                )
            ),
            ToolDefinition(
                id = "halo_ui_scroll",
                name = "halo_ui_scroll",
                description = "Scroll the current screen forward or backward.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "snapshotId" to mapOf("type" to "string"),
                        "token" to mapOf("type" to "string", "description" to "Token for a scrollable container returned by halo_read_screen"),
                        "direction" to mapOf("type" to "string", "enum" to listOf("forward", "backward"))
                    ),
                    "required" to listOf("snapshotId", "token", "direction"),
                    "additionalProperties" to false
                )
            ),
            ToolDefinition(
                id = "halo_ui_set_text",
                name = "halo_ui_set_text",
                description = "Replace text in a non-password editable field using a recent semantic screen token.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "snapshotId" to mapOf("type" to "string"),
                        "token" to mapOf("type" to "string", "description" to "Editable-field token returned by halo_read_screen"),
                        "text" to mapOf("type" to "string", "maxLength" to MAX_INPUT_CHARS)
                    ),
                    "required" to listOf("snapshotId", "token", "text"),
                    "additionalProperties" to false
                )
            ),
            ToolDefinition(
                id = "halo_ui_global_action",
                name = "halo_ui_global_action",
                description = "Perform a global system action like back or home.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "action" to mapOf("type" to "string", "enum" to listOf("back", "home"))
                    ),
                    "required" to listOf("action"),
                    "additionalProperties" to false
                )
            )
        )
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)

        val service = HaloAccessibilityService.instance ?: return reject("Accessibility Service is not enabled by user")
        val actions = AccessibilityActions(service)

        return when (toolCall.functionName) {
            "halo_read_screen" -> {
                val snapshot = service.captureSnapshot()
                    ?: return reject("The active screen cannot be read or is blocked by Halo safety policy")
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, snapshot.toToolText())
            }
            "halo_ui_click" -> {
                val snapshotId = toolCall.arguments["snapshotId"] as? String ?: return reject("Missing snapshotId; call halo_read_screen again")
                val token = toolCall.arguments["token"] as? String ?: return reject("Missing token; call halo_read_screen again")
                val before = service.latestSemanticSnapshot
                val success = actions.clickToken(snapshotId, token)
                if (success) verifiedReceipt(toolCall, before, service)
                else actionFailure(toolCall, "TARGET_NOT_FOUND", "The element could not be clicked; the snapshot may be stale")
            }
            "halo_ui_scroll" -> {
                val snapshotId = toolCall.arguments["snapshotId"] as? String ?: return reject("Missing snapshotId; call halo_read_screen again")
                val token = toolCall.arguments["token"] as? String ?: return reject("Missing scroll-container token")
                val direction = toolCall.arguments["direction"] as? String ?: return reject("Missing direction")
                val before = service.latestSemanticSnapshot
                val success = actions.scrollToken(snapshotId, token, forward = direction == "forward")
                if (success) verifiedReceipt(toolCall, before, service)
                else actionFailure(toolCall, "TARGET_NOT_FOUND", "The scroll target is unavailable or stale")
            }
            "halo_ui_set_text" -> {
                val snapshotId = toolCall.arguments["snapshotId"] as? String ?: return reject("Missing snapshotId; call halo_read_screen again")
                val token = toolCall.arguments["token"] as? String ?: return reject("Missing editable-field token")
                val text = (toolCall.arguments["text"] as? String)
                    ?.takeIf { it.length <= MAX_INPUT_CHARS }
                    ?: return reject("Text is missing or exceeds $MAX_INPUT_CHARS characters")
                val target = service.latestSemanticSnapshot?.nodes?.firstOrNull { it.token == token }
                    ?: return reject("Token is not present in the latest semantic snapshot")
                if (target.sensitive || !target.editable) return reject("Target is not a permitted editable field")
                val before = service.latestSemanticSnapshot
                val success = actions.setTextToken(snapshotId, token, text)
                if (success) verifiedReceipt(toolCall, before, service)
                else actionFailure(toolCall, "SET_TEXT_FAILED", "Text could not be entered into the selected field")
            }
            "halo_ui_global_action" -> {
                val action = toolCall.arguments["action"] as? String ?: return reject("Missing action")
                val before = service.captureSnapshot()
                val success = when (action) {
                    "back" -> actions.globalBack()
                    "home" -> actions.globalHome()
                    else -> false
                }
                if (success) verifiedReceipt(toolCall, before, service)
                else actionFailure(toolCall, "ACTION_NOT_DISPATCHED", "The Android global action was not dispatched")
            }
            else -> reject("Unknown tool")
        }
    }

    private suspend fun verifiedReceipt(
        toolCall: ToolCall,
        before: ScreenContextSnapshot?,
        service: HaloAccessibilityService,
    ): ToolExecutionResult.Success {
        val after = service.captureSettledSnapshot()
        val changed = before != null && after != null && before.semanticFingerprint() != after.semanticFingerprint()
        val receipt = when {
            after == null -> HostActionReceipt.UNKNOWN
            changed -> HostActionReceipt.CONFIRMED
            else -> HostActionReceipt.DISPATCHED
        }
        val change = when {
            after == null -> "unobservable"
            before == null -> "baseline_unavailable"
            before.packageName != after.packageName -> "package_changed"
            changed -> "semantic_content_changed"
            else -> "no_observed_change"
        }
        val output = buildString {
            append("receipt=").append(receipt.name)
            append(" beforeSnapshot=").append(before?.snapshotId ?: "unavailable")
            append(" afterSnapshot=").append(after?.snapshotId ?: "unavailable")
            append(" observedChange=").append(changed)
            append(" change=").append(change)
            append(" package=").append(after?.packageName ?: before?.packageName ?: "unknown")
            append(" next=").append(if (after == null) "STOP" else "READ_SCREEN")
        }
        return ToolExecutionResult.Success(
            toolCallId = toolCall.id,
            toolId = toolCall.functionName,
            output = output,
            actionTruth = receipt.toActionTruth(),
        )
    }

    private fun actionFailure(toolCall: ToolCall, code: String, message: String) = ToolExecutionResult.Failed(
        toolCallId = toolCall.id,
        toolId = toolCall.functionName,
        message = message,
        failureKind = com.halo.runtime.cognition.tools.ToolFailureKind.UNAVAILABLE,
        code = code,
        hint = "Call halo_read_screen and choose a token from the fresh snapshot.",
    )

    private fun ScreenContextSnapshot.semanticFingerprint(): Int = buildList {
        add(packageName.orEmpty())
        nodes.forEach { node ->
            add(listOf(node.semanticType, node.text, node.contentDescription, node.bounds, node.checked, node.selected, node.focused).joinToString("|"))
        }
    }.joinToString("\n").hashCode()

    private companion object {
        const val MAX_INPUT_CHARS = 2_000
    }

    private fun ScreenContextSnapshot.toToolText(): String = buildString {
        appendLine("UNTRUSTED_SCREEN_CONTENT")
        appendLine("screen=${screenWidth ?: "?"}x${screenHeight ?: "?"} package=${packageName ?: "unknown"}")
        appendLine("snapshotId=$snapshotId capturedAtMs=$capturedAtMs truncated=$truncated reason=${truncationReason ?: "none"}")
        appendLine("nodes:")
        nodes.forEach { node ->
            append("- token=").append(node.token ?: "none")
            append(" role=").append(node.semanticType.name.lowercase())
            node.bounds?.let { append(" bounds=[${it.left},${it.top},${it.right},${it.bottom}]") }
            if (node.clickable) append(" clickable")
            if (node.editable) append(" editable")
            if (node.scrollable) append(" scrollable")
            if (node.checked) append(" checked")
            if (!node.enabled) append(" disabled")
            if (node.sensitive) append(" sensitive=[REDACTED]")
            node.text?.let { append(" text=").append(it.quoted()) }
            node.contentDescription?.let { append(" hint=").append(it.quoted()) }
            appendLine()
        }
        append("END_UNTRUSTED_SCREEN_CONTENT")
    }

    private fun String.quoted(): String = "\"" +
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""
}
