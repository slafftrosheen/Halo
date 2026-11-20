package com.halo.app.di.tools.host

import com.halo.app.ai.ChatGptBrowserLogin
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Browser passwords/MFA remain a protected human step; only the app's account owner is callable. */
class ChatGptAccountTools(private val owner: () -> ChatGptBrowserLogin, private val allowed: () -> Boolean) : ToolExecutor {
    override fun definitions() = definitionsList()
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        val name = toolCall.functionName
        fun reject(kind: ToolRejectionKind) = ToolExecutionResult.Rejected(toolCall.id, name,
            "Account unavailable or changed. Read current account status before a fresh request.", kind)
        if (name !in NAMES) return reject(ToolRejectionKind.TOOL_UNKNOWN)
        if (!allowed()) return reject(ToolRejectionKind.PERMISSION_REQUIRED)
        if (toolCall.arguments.keys != if (name == STATUS) emptySet<String>() else setOf("sourceVersion"))
            return reject(ToolRejectionKind.INVALID_ARGUMENTS)
        var changed = false
        try {
            val control = owner()
            control.account.load()
            var revoked: Boolean? = null
            if (name != STATUS) {
                val version = toolCall.arguments["sourceVersion"] as? String ?: return reject(ToolRejectionKind.INVALID_ARGUMENTS)
                if (version != control.account.state.value.revision) return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE)
                changed = true
                when (name) {
                    SIGN_IN -> control.start()
                    DISCONNECT -> revoked = control.disconnect(version)
                }
            }
            val state = control.account.state.value
            val result = JSONObject().put("sourceVersion", state.revision).put("connected", state.connected)
                .put("subscriptionAccess", state.subscriptionAccess).put("signInPending", control.state.value.busy)
                .put("browserRequested", name == SIGN_IN).put("localAccountRemoved", name == DISCONNECT && !state.connected)
                .put("remoteRevoked", revoked ?: JSONObject.NULL)
                .put("humanStep", if (name == SIGN_IN) "Finish OpenAI sign-in and approval in the browser, then return to Halo. Never ask for credentials in chat." else JSONObject.NULL)
            return ToolExecutionResult.Success(toolCall.id, name, result.toString(),
                when (name) { SIGN_IN -> ActionTruth.DISPATCHED; DISCONNECT -> ActionTruth.CONFIRMED; else -> ActionTruth.ACKNOWLEDGED })
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return ToolExecutionResult.Failed(toolCall.id, name,
            "Account action did not finish. Read status before another explicit decision; do not replay automatically.", completionUnknown = changed) }
    }
    companion object {
        const val STATUS = "halo_chatgpt_account_status"
        const val SIGN_IN = "halo_chatgpt_account_sign_in"
        const val DISCONNECT = "halo_chatgpt_account_disconnect"
        val NAMES = setOf(STATUS, SIGN_IN, DISCONNECT)
        private val version = mapOf("type" to "string", "minLength" to 36, "maxLength" to 36)
        private fun schema(fields: Map<String, Any>) = mapOf("type" to "object", "properties" to fields,
            "required" to fields.keys.toList(), "additionalProperties" to false)
        fun definitionsList() = listOf(
            ToolDefinition(STATUS, STATUS, "Read Halo's ChatGPT connection, granted subscription access and sourceVersion. Does not expose credentials, account identity, browser URL or usage quotas; no network or inference.", schema(emptyMap())),
            ToolDefinition(SIGN_IN, SIGN_IN, "After explicit approval start Sign in with ChatGPT in the external browser using current account sourceVersion. Requires app-wide Power. Returns DISPATCHED, not proof of login. The person must complete OpenAI passwords/MFA and approval; never request credentials in conversation. Poll status after returning. No API-key fallback, model selection or inference.", schema(mapOf("sourceVersion" to version))),
            ToolDefinition(DISCONNECT, DISCONNECT, "After explicit approval remove the exact observed ChatGPT connection from Halo and request remote token revocation. Read sourceVersion first. A confirmed local removal can coexist with remoteRevoked=false; then the person must remove Halo in ChatGPT settings. Never deletes ChatGPT history, removes other provider keys or replays an uncertain action.", schema(mapOf("sourceVersion" to version))),
        )
        fun operability(name: String) = ToolOperability(version = 1, capabilityId = CapabilityId("host.accounts.chatgpt"),
            owner = "ChatGptBrowserLogin/ChatGptAccount", operation = if (name == STATUS) ToolOperation.QUERY else ToolOperation.EDIT,
            resultEncoding = ToolResultEncoding.JSON, sourceVersionArguments = if (name == STATUS) emptySet() else setOf("sourceVersion"),
            resultSchema = schema(mapOf("sourceVersion" to version, "connected" to mapOf("type" to "boolean"),
                "subscriptionAccess" to mapOf("type" to "boolean"), "signInPending" to mapOf("type" to "boolean"),
                "browserRequested" to mapOf("type" to "boolean"), "localAccountRemoved" to mapOf("type" to "boolean"),
                "remoteRevoked" to mapOf("type" to listOf("boolean", "null")),
                "humanStep" to mapOf("type" to listOf("string", "null"), "maxLength" to 300))),
            limitations = setOf("Passwords, MFA and OpenAI account approval are human-only browser security steps.",
                "Local/Power, foreground adult authority and exact account version apply; subscription eligibility is provider-controlled."))
    }
}
