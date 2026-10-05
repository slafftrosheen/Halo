package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.halo.host.android.resolver.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.apps.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import java.time.Instant

/** Opens a platform composer. Never requests SMS privileges and never claims message delivery. */
class MessagingTools(private val context: Context) : ToolExecutor, ToolPreflightProvider {
    private val pending = TransientSemanticArguments()
    override fun restoreTransientCall(toolCall: ToolCall) = pending.restore(toolCall)
    override suspend fun preflight(toolCall: ToolCall): ToolPreflightResult = ToolPreflightResult.Validated(pending.prepare(toolCall))
    override fun definitions() = listOf("sms", "email", "message").map { channel -> ToolDefinition(id = "halo_compose_$channel", name = "halo_compose_$channel",
        description = "Open a $channel composer with a recipient and draft. Opening is not sending; the user sends from the app.",
        parameters = mapOf("type" to "object", "properties" to mapOf("recipient" to mapOf("type" to "string", "maxLength" to 100),
            "body" to mapOf("type" to "string", "maxLength" to 4096)), "required" to listOf("recipient", "body"), "additionalProperties" to false)) }
    override suspend fun execute(incoming: ToolCall): ToolExecutionResult {
        val call = pending.consume(incoming) ?: return ToolExecutionResult.Rejected(incoming.id, incoming.functionName, "Draft expired; provide it again", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
        val recipient = call.arguments["recipient"] as? String ?: return rejected(call, "Specify a recipient")
        val body = call.arguments["body"] as? String ?: return rejected(call, "Specify a message")
        val email = call.functionName == "halo_compose_email"
        val resolved = if (email) recipient.takeIf { it.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) }
            ?: return rejected(call, "Provide the recipient's email address") else when (val result = AndroidContactResolver(context).resolve(recipient)) {
                is ContactResolveResult.Found -> CommunicationTools.canonicalDialNumber(result.number)
                is ContactResolveResult.MultipleMatches -> return rejected(call, "Recipient is ambiguous. Please specify the exact contact or number.")
                else -> return rejected(call, "Provide an exact contact or phone number. Contacts permission may be needed.")
            }
        val intent = composeIntent(resolved, body, email).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return rejected(call, "No compatible messaging app is available")
        val started = Instant.now()
        try { context.startActivity(intent) } catch (_: Exception) {
            return ToolExecutionResult.Failed(call.id, call.functionName, "Composer could not be opened", completionUnknown = true)
        }
        val receipt = SemanticOperationReceipt(call.id, SemanticActionId("prepare_message"), AppOperationRoute.ANDROID_INTENT,
            intent.resolveActivity(context.packageManager)?.packageName, DispatchTruth.DISPATCHED, EffectTruth.UNCONFIRMED, null, started, Instant.now(), ToolRiskClass.EXTERNAL_SIDE_EFFECT)
        return ToolExecutionResult.Success(call.id, call.functionName, "COMPOSER_OPENED. I opened the message for you. Send it from the messaging app.",
            ActionTruth.DISPATCHED, semanticReceipt = receipt, semanticArgumentShapes = listOf(
                SemanticArgumentShape("recipient", SemanticValueType.STRING, true, recipient.length), SemanticArgumentShape("body", SemanticValueType.STRING, true, body.length)))
    }
    private fun rejected(call: ToolCall, reason: String) = ToolExecutionResult.Rejected(call.id, call.functionName, reason, ToolRejectionKind.UNSUPPORTED_OPERATION)
    companion object {
        fun composeIntent(recipient: String, body: String, email: Boolean): Intent = if (email)
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(recipient))).putExtra(Intent.EXTRA_TEXT, body)
        else Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(recipient))).putExtra("sms_body", body)
    }
}
