package com.halo.app.di.tools.host

import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.display.*
import kotlinx.coroutines.currentCoroutineContext
import org.json.JSONArray
import org.json.JSONObject

/** UI and assistant share the host owner; a tool cannot turn discovery into output. */
class ExternalDisplayTools(
    private val output: ExternalDisplayOutput,
    private val canAccess: () -> Boolean,
    private val remoteReadAllowed: () -> Boolean,
) : ToolExecutor, ToolGrantPreviewProvider {
    override fun definitions() = definitionsList()

    override suspend fun grantSummary(toolCall: ToolCall): String = when (toolCall.functionName) {
        SHOW -> "Show the supplied text on the explicitly selected external display. Output stops when Halo leaves the foreground."
        CLEAR -> "Stop the selected external display card."
        else -> "Read connected Android presentation displays; no output is started."
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        val name = toolCall.functionName
        if (name !in NAMES) return reject(toolCall, "Unknown display operation.", ToolRejectionKind.TOOL_UNKNOWN)
        val allowed = when (name) { SHOW -> setOf("endpointToken", "sourceVersion", "text"); CLEAR -> setOf("endpointToken", "sourceVersion"); else -> emptySet() }
        if (toolCall.arguments.keys != allowed) return reject(toolCall, "Use exactly the documented display arguments.", ToolRejectionKind.INVALID_ARGUMENTS)
        val scope = currentCoroutineContext()[RunToolScope]
        if (scope == null || !canAccess() || !scope.targetIsLocal && !remoteReadAllowed())
            return reject(toolCall, "Display tools require foreground grown-up access and the existing disclosure policy.", ToolRejectionKind.PERMISSION_REQUIRED)
        if (name == LIST) {
            val state = output.snapshot()
            if (!canAccess() || !scope.targetIsLocal && !remoteReadAllowed())
                return reject(toolCall, "Display access changed.", ToolRejectionKind.PERMISSION_REQUIRED)
            val endpoints = JSONArray()
            state.endpoints.take(ExternalDisplaySession.MAX_DISPLAYS).forEach { endpoint ->
                endpoints.put(JSONObject().put("endpointToken", endpoint.token).put("name", endpoint.name.take(120))
                    .put("width", endpoint.width).put("height", endpoint.height))
            }
            return ToolExecutionResult.Success(toolCall.id, name, JSONObject().put("sourceVersion", state.revision)
                .put("foreground", state.foreground).put("endpoints", endpoints)
                .put("activeToken", state.activeToken ?: JSONObject.NULL).toString(), ActionTruth.ACKNOWLEDGED)
        }
        val token = (toolCall.arguments["endpointToken"] as? String)?.takeIf { it.length in 1..100 }
            ?: return reject(toolCall, "List and select a current endpointToken.", ToolRejectionKind.INVALID_ARGUMENTS)
        val number = toolCall.arguments["sourceVersion"] as? Number
            ?: return reject(toolCall, "sourceVersion is required.", ToolRejectionKind.INVALID_ARGUMENTS)
        val revision = number.toLong()
        if (revision < 0 || revision.toDouble() != number.toDouble())
            return reject(toolCall, "sourceVersion must be the current integer revision.", ToolRejectionKind.INVALID_ARGUMENTS)
        val receipt = if (name == SHOW) {
            val text = (toolCall.arguments["text"] as? String)?.takeIf { it.isNotBlank() && it.length <= ExternalDisplaySession.MAX_TEXT_LENGTH }
                ?: return reject(toolCall, "Use 1–400 characters for the card.", ToolRejectionKind.INVALID_ARGUMENTS)
            output.show(token, text, revision)
        } else output.clear(revision, token)
        if (receipt.status == "REJECTED") return reject(toolCall, receipt.message)
        return ToolExecutionResult.Success(toolCall.id, name, JSONObject().put("status", receipt.status)
            .put("message", receipt.message).put("sourceVersion", receipt.sourceVersion).put("endpointToken", token).toString(),
            if (receipt.status == "DISPATCHED") ActionTruth.DISPATCHED else ActionTruth.CONFIRMED)
    }

    private fun reject(call: ToolCall, message: String, kind: ToolRejectionKind = ToolRejectionKind.RESOURCE_UNAVAILABLE) =
        ToolExecutionResult.Rejected(call.id, call.functionName, message, kind)

    companion object {
        const val LIST = "halo_external_displays"
        const val SHOW = "halo_external_display_show"
        const val CLEAR = "halo_external_display_clear"
        val NAMES = setOf(LIST, SHOW, CLEAR)
        private val tokenSchema = mapOf("type" to "string", "minLength" to 1, "maxLength" to 100)
        private val versionSchema = mapOf("type" to "integer", "minimum" to 0)
        private fun schema(fields: Map<String, Any>) = mapOf("type" to "object", "properties" to fields,
            "required" to fields.keys.toList(), "additionalProperties" to false)

        fun definitionsList(): List<ToolDefinition> = listOf(
            ToolDefinition(LIST, LIST, "List connected Android presentation displays and active output. Ask which display if ambiguous; tokens expire on disconnect, configuration change or background. Names do not prove a brand, camera, USB control or tracking support.", schema(emptyMap())),
            ToolDefinition(SHOW, SHOW, "Show up to 400 characters on an explicitly selected external display. First read halo_external_displays and pass its endpointToken and sourceVersion. Requires foreground unlocked grown-up Halo and approval policy. DISPATCHED means Android accepted the card, not that the wearer saw it. No automatic replay or mirroring.",
                schema(mapOf("endpointToken" to tokenSchema, "sourceVersion" to versionSchema,
                    "text" to mapOf("type" to "string", "minLength" to 1, "maxLength" to ExternalDisplaySession.MAX_TEXT_LENGTH)))),
            ToolDefinition(CLEAR, CLEAR, "Stop the active external display card using activeToken and sourceVersion from halo_external_displays. Stale selections fail; no automatic replay after a new card or connection.",
                schema(mapOf("endpointToken" to tokenSchema, "sourceVersion" to versionSchema))),
        )

        fun operability(name: String) = ToolOperability(capabilityId = CapabilityId("host.display.external"),
            owner = "AndroidExternalDisplayPresenter", operation = if (name == LIST) ToolOperation.QUERY else ToolOperation.PRESENT,
            resultEncoding = ToolResultEncoding.JSON, sourceVersionArguments = if (name == LIST) emptySet() else setOf("sourceVersion", "endpointToken"),
            resultIdFields = if (name == LIST) setOf("activeToken") else setOf("endpointToken"),
            resultSchema = if (name == LIST) schema(mapOf("sourceVersion" to versionSchema, "foreground" to mapOf("type" to "boolean"),
                "activeToken" to mapOf("type" to listOf("string", "null"), "maxLength" to 100),
                "endpoints" to mapOf("type" to "array", "maxItems" to ExternalDisplaySession.MAX_DISPLAYS,
                    "items" to schema(mapOf("endpointToken" to tokenSchema, "name" to mapOf("type" to "string", "maxLength" to 120),
                        "width" to mapOf("type" to "integer"), "height" to mapOf("type" to "integer"))))))
                else schema(mapOf("sourceVersion" to versionSchema, "endpointToken" to tokenSchema,
                    "status" to mapOf("type" to "string", "enum" to listOf("DISPATCHED", "CLEARED")),
                    "message" to mapOf("type" to "string", "maxLength" to 200))),
            limitations = setOf("Connection tokens are ephemeral and never restored after process death. Content is not persisted or automatically replayed.",
                "Output is standard Android text presentation only. No XREAL-specific USB, pose, camera, audio or physical acceptance is claimed.",
                "Existing foreground, unlock, Kids, Power disclosure and confirmation policy apply. A successful submission does not prove visibility."))
    }
}
