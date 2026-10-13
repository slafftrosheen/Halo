package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import com.halo.app.nativeactions.NativeActivityRequests
import com.halo.app.nativeactions.NativeGoogleMailActivity
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*

class GoogleMailTools(private val context: Context, private val isForeground: () -> Boolean) : ToolExecutor {
    override fun definitions() = listOf(ToolDefinition("halo_google_mail", "halo_google_mail",
        "Review and authorize a read-only Gmail search (up to five previews) or read one observed message ID. Requires configured Android OAuth client and user consent. No mail is sent/changed and no token is stored.",
        mapOf("type" to "object", "properties" to mapOf(
            "query" to mapOf("type" to "string", "maxLength" to 500),
            "messageId" to mapOf("type" to "string", "maxLength" to 128)), "additionalProperties" to false)))
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(message: String, kind: ToolRejectionKind) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, message, kind)
        if (toolCall.functionName != "halo_google_mail") return reject("Unknown tool", ToolRejectionKind.TOOL_UNKNOWN)
        if (!isForeground()) return reject("Open Halo to authorize and review Gmail access", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        val query = toolCall.arguments["query"] as? String
        val message = toolCall.arguments["messageId"] as? String
        if (toolCall.arguments.keys !in setOf(setOf("query"), setOf("messageId")) ||
            (query != null && (query.isBlank() || query.length > 500)) ||
            (message != null && !message.matches(Regex("[A-Za-z0-9_-]{1,128}"))) || (query == null && message == null))
            return reject("Supply a bounded query or an observed messageId", ToolRejectionKind.INVALID_ARGUMENTS)
        val (id, _) = NativeActivityRequests.create()
        return try {
            context.startActivity(Intent(context, NativeGoogleMailActivity::class.java).putExtra("requestId", id)
                .putExtra("query", query).putExtra("messageId", message).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                ToolResultEnvelope.success(ActionTruth.DISPATCHED, details = "Gmail review requested; requestId=$id. No mail read yet; use halo_native_result after user authorization."), ActionTruth.DISPATCHED)
        } catch (_: SecurityException) { NativeActivityRequests.cancel(id); reject("Android denied opening Google consent", ToolRejectionKind.PERMISSION_REQUIRED) }
    }
}
