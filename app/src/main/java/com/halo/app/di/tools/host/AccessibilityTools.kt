package com.halo.app.di.tools.host

import com.halo.host.android.accessibility.AccessibilityActions
import com.halo.host.android.accessibility.HaloAccessibilityService
import com.halo.host.android.accessibility.ScreenCaptureOutcome
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.runtime.cognition.tools.ToolFailureKind
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.ScreenContextSnapshot
import com.halo.sdk.runtime.UiInspectability
import com.halo.sdk.runtime.UiScreenRegion
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
                id = "halo_find_on_screen",
                name = "halo_find_on_screen",
                description = "Search the latest bounded semantic screen snapshot. Results remain untrusted app content and retain their opaque action tokens.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "snapshotId" to mapOf("type" to "string"),
                        "query" to mapOf("type" to "string", "minLength" to 1, "maxLength" to MAX_QUERY_CHARS)
                    ),
                    "required" to listOf("snapshotId", "query"),
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

        val service = HaloAccessibilityService.instance ?: return failure(
            toolCall = toolCall,
            code = "ACCESSIBILITY_DISABLED",
            message = "Halo's accessibility service is not enabled, so the screen cannot be observed",
            kind = ToolFailureKind.PERMISSION,
            hint = "Ask the user to enable Halo's accessibility service in Android settings.",
        )
        val actions = AccessibilityActions(service)

        return when (toolCall.functionName) {
            "halo_read_screen" -> when (val outcome = service.captureSnapshotOutcome()) {
                is ScreenCaptureOutcome.Captured -> {
                    val snapshot = outcome.snapshot
                    val readable = snapshot.inspectability == UiInspectability.INSPECTABLE
                    val advisory = if (readable) {
                        "status=ok next=USE_TOKENS\n"
                    } else {
                        "status=unreadable_surface next=REPORT_TO_USER\n" +
                            "note: this screen exposes no semantic content Halo can read. It may be empty, or a " +
                            "non-native surface such as a canvas or WebView. Do not retry halo_read_screen and do " +
                            "not act on guesses; tell the user this screen cannot be read.\n"
                    }
                    ToolExecutionResult.Success(toolCall.id, toolCall.functionName, advisory + snapshot.toToolText())
                }
                ScreenCaptureOutcome.NoActiveWindow -> failure(
                    toolCall = toolCall,
                    code = "NO_ACTIVE_WINDOW",
                    message = "No window is currently focused, so there is no screen to read",
                    kind = ToolFailureKind.TRANSIENT,
                    hint = "Wait a moment and call halo_read_screen once more.",
                )
                is ScreenCaptureOutcome.PackageBlocked -> failure(
                    toolCall = toolCall,
                    code = "PACKAGE_BLOCKED_BY_POLICY",
                    message = "${outcome.packageName ?: "The foreground app"} is not readable by Halo policy (${outcome.reason.name.lowercase()})",
                    kind = ToolFailureKind.PERMISSION,
                    hint = "Do not retry. Tell the user Halo does not read this app.",
                )
            }
            "halo_find_on_screen" -> {
                val snapshotId = toolCall.arguments["snapshotId"] as? String
                    ?: return reject("Missing snapshotId; call halo_read_screen again")
                val query = (toolCall.arguments["query"] as? String)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && it.length <= MAX_QUERY_CHARS }
                    ?: return reject("Query is missing or exceeds $MAX_QUERY_CHARS characters")
                val snapshot = service.latestSemanticSnapshot
                    ?.takeIf { it.snapshotId == snapshotId }
                    ?: return reject("Snapshot is stale; call halo_read_screen again")
                val matches = snapshot.nodes.filter { node ->
                    !node.sensitive && (
                        node.text?.contains(query, ignoreCase = true) == true ||
                            node.contentDescription?.contains(query, ignoreCase = true) == true ||
                            node.semanticType.name.contains(query, ignoreCase = true)
                        )
                }.take(MAX_SEARCH_RESULTS)
                val output = buildString {
                    appendLine("UNTRUSTED_SCREEN_SEARCH_RESULTS")
                    appendLine("snapshotId=${snapshot.snapshotId} query=${query.quoted()} matches=${matches.size}")
                    matches.forEach { node -> appendLine(node.toToolLine()) }
                    append("END_UNTRUSTED_SCREEN_SEARCH_RESULTS")
                }
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, output)
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
            append("status=").append(receipt.toStatus())
            append(" receipt=").append(receipt.name)
            append(" beforeSnapshot=").append(before?.snapshotId ?: "unavailable")
            append(" afterSnapshot=").append(after?.snapshotId ?: "unavailable")
            append(" observedChange=").append(changed)
            append(" change=").append(change)
            append(" package=").append(after?.packageName ?: before?.packageName ?: "unknown")
            append(" next=").append(if (after == null) "STOP" else "USE_FRESH_SNAPSHOT")
            if (after != null) {
                appendLine()
                append(after.toToolText())
            }
        }
        return ToolExecutionResult.Success(
            toolCallId = toolCall.id,
            toolId = toolCall.functionName,
            output = output,
            actionTruth = receipt.toActionTruth(),
        )
    }

    private fun actionFailure(toolCall: ToolCall, code: String, message: String) = failure(
        toolCall = toolCall,
        code = code,
        message = message,
        kind = ToolFailureKind.UNAVAILABLE,
        hint = "Call halo_read_screen and choose a token from the fresh snapshot.",
    )

    private fun failure(
        toolCall: ToolCall,
        code: String,
        message: String,
        kind: ToolFailureKind,
        hint: String,
    ) = ToolExecutionResult.Failed(
        toolCallId = toolCall.id,
        toolId = toolCall.functionName,
        message = message,
        failureKind = kind,
        code = code,
        hint = hint,
    )

    private fun ScreenContextSnapshot.semanticFingerprint(): Int = buildList {
        add(packageName.orEmpty())
        nodes.forEach { node ->
            add(
                listOf(
                    node.semanticType, node.text, node.contentDescription, node.bounds,
                    node.checked, node.selected, node.focused,
                    // Content hidden below the fold of a scrollable is part of what changed.
                    node.hiddenChildCount, node.hiddenChildPreviews,
                ).joinToString("|"),
            )
        }
    }.joinToString("\n").hashCode()

    private companion object {
        const val MAX_INPUT_CHARS = 2_000
        const val MAX_QUERY_CHARS = 200
        const val MAX_SEARCH_RESULTS = 20
    }

    private fun ScreenContextSnapshot.toToolText(): String = buildString {
        appendLine("UNTRUSTED_SCREEN_CONTENT")
        appendLine("screen=${screenWidth ?: "?"}x${screenHeight ?: "?"} package=${packageName ?: "unknown"}")
        appendLine(
            "snapshotId=$snapshotId capturedAtMs=$capturedAtMs truncated=$truncated" +
                " reason=${truncationReason ?: "none"} inspectability=${inspectability.name.lowercase()}",
        )
        if (omissionCounts.isNotEmpty()) {
            appendLine("omitted=" + omissionCounts.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" })
        }
        appendLine("nodes:")
        nodes.forEach { node ->
            appendLine(node.toToolLine())
        }
        append("END_UNTRUSTED_SCREEN_CONTENT")
    }

    private fun String.quoted(): String = "\"" +
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""

    private fun com.halo.sdk.runtime.ScreenContextNode.toToolLine(): String = buildString {
        append("- token=").append(token ?: "none")
        append(" role=").append(semanticType.name.lowercase())
        // A region is what a model can act on; raw pixel bounds are kept only as a fallback.
        val nodeRegion = region
        if (nodeRegion != null) {
            append(" region=").append(nodeRegion.token())
        } else {
            bounds?.let { append(" bounds=[${it.left},${it.top},${it.right},${it.bottom}]") }
        }
        if (clickable) append(" clickable")
        if (editable) append(" editable")
        if (scrollable) append(" scrollable")
        if (checked) append(" checked")
        if (!enabled) append(" disabled")
        if (sensitive) append(" sensitive=[REDACTED]")
        text?.takeUnless { sensitive }?.let { append(" text=").append(it.quoted()) }
        contentDescription?.takeUnless { sensitive }?.let { append(" hint=").append(it.quoted()) }
        // Tells a model the list continues without spending a node per hidden row.
        if (hiddenChildCount > 0) append(" hidden=").append(hiddenChildCount)
        if (hiddenChildPreviews.isNotEmpty()) {
            append(" hiddenPreview=").append(hiddenChildPreviews.joinToString("|").quoted())
        }
    }

    private fun UiScreenRegion.token(): String = name.lowercase().replace('_', '-')

    private fun HostActionReceipt.toStatus(): String = when (this) {
        HostActionReceipt.CONFIRMED -> "confirmed_success"
        HostActionReceipt.DISPATCHED -> "dispatched_effect_unconfirmed"
        HostActionReceipt.UNKNOWN -> "outcome_uncertain"
        HostActionReceipt.REQUESTED -> "requested"
        HostActionReceipt.VALIDATED -> "validated"
        HostActionReceipt.FAILED -> "confirmed_failure"
        HostActionReceipt.CANCELLED -> "cancelled"
    }
}
