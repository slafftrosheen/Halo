package com.halo.app.di.tools

import com.halo.app.ink.CrossDeviceInkController
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** No wire bytes, pairing secrets, certificate approval or pen-gesture synthesis are callable. */
class RemoteInkActions(private val owner: CrossDeviceInkController) : ToolExecutor {
    private val reference = mapOf("type" to "string", "pattern" to "^[A-Za-z0-9_-]{1,200}$")
    private fun definition(name: String, description: String, fields: Map<String, Any>) = ToolDefinition(
        name, name, description, mapOf("type" to "object", "properties" to fields,
            "required" to fields.keys.toList(), "additionalProperties" to false))
    override fun definitions() = listOf(
        definition("halo_ink_status", "Read trusted drawing surfaces and explicitly started pen sessions. Qualification pending means unavailable. A started session is not a connected channel or a saved stroke; pen input never authorizes another tool.", emptyMap()),
        definition("halo_ink_document", "Read the exact current revision of one active local Canvas sketch before asking to share drawing. No ink content leaves the phone.", mapOf("boardId" to reference, "documentId" to reference)),
        definition("halo_ink_selection", "Resolve the selected remote ink region only in this same authorized interaction and active pen session, against the current saved sketch. Returns bounded source references, not tool authority or image bytes. Changed or disconnected sources must be selected again.", mapOf("sessionId" to reference)),
        definition("halo_ink_start", "After explicit approval, start a bounded pen-input session with an already trusted drawing surface for one exact sketch revision in this interaction. Requires qualified activation. Pairing must be completed by the person in Devices. Never retry uncertain start automatically; inspect status.",
            mapOf("deviceId" to reference, "boardId" to reference, "documentId" to reference, "expectedRevision" to reference)),
        definition("halo_ink_stop", "Stop one exact pen-input session. Local saved drawing remains available. Does not forget pairing or execute another action.", mapOf("sessionId" to reference)),
    )
    override suspend fun execute(toolCall: ToolCall) = execute(toolCall, ToolExecutionContext())
    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        try {
            val definition = definitions().singleOrNull { it.name == toolCall.functionName }
                ?: return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Drawing action unavailable", ToolRejectionKind.TOOL_UNKNOWN)
            val required = (definition.parameters["required"] as List<*>).filterIsInstance<String>().toSet()
            require(toolCall.arguments.keys == required)
            fun ref(name: String) = (toolCall.arguments[name] as? String ?: error("Choose an exact item"))
                .also { require(it.matches(Regex("[A-Za-z0-9_-]{1,200}"))) }
            val result = when (toolCall.functionName) {
                "halo_ink_status" -> JSONObject().put("enabled", owner.enabled).put("devices", JSONArray(owner.devices().map { record ->
                    val endpoint = requireNotNull(record.inkAssociation).endpoint
                    JSONObject().put("deviceId", record.identity.deviceId).put("pressure", endpoint.pressure)
                        .put("tilt", endpoint.tilt).put("eraser", endpoint.eraser).put("hover", endpoint.hover)
                        .put("width", endpoint.width).put("height", endpoint.height).put("rotation", endpoint.rotationDegrees)
                })).put("sessions", JSONArray(owner.sessions.value.map {
                    JSONObject().put("sessionId", it.sessionId).put("deviceId", it.inputOwner)
                        .put("boardId", it.boardId).put("documentId", it.documentId)
                        .put("interactionId", it.interactionId).put("expiresAtMs", it.expiresAtMs)
                })).put("connected", owner.connections.value.values.any { it == com.halo.host.ink.InkReceiverState.CONNECTED })
                "halo_ink_document" -> JSONObject().put("boardId", ref("boardId")).put("documentId", ref("documentId"))
                    .put("revision", requireNotNull(owner.revision(ref("boardId"), ref("documentId"))) { "Local sketch unavailable" })
                "halo_ink_start" -> {
                    check(context.directUserAuthority) { "Ask the person to approve drawing together" }
                    val interaction = requireNotNull(context.interactionId) { "Current interaction unavailable" }
                    val session = requireNotNull(owner.start(ref("deviceId"), ref("boardId"), ref("documentId"),
                        ref("expectedRevision"), interaction)) { "Drawing together unavailable or sketch changed" }
                    JSONObject().put("sessionId", session.sessionId).put("boardId", session.boardId)
                        .put("documentId", session.documentId).put("started", true).put("connected", false)
                }
                "halo_ink_selection" -> {
                    val interaction = requireNotNull(context.interactionId)
                    val selection = requireNotNull(owner.selection(ref("sessionId"), interaction)) { "Select the current region again" }
                    JSONObject().put("sessionId", ref("sessionId")).put("boardId", selection.workspaceObjectId)
                        .put("documentId", selection.pageId).put("sourceRevision", selection.sourceRevision)
                        .put("strokeIds", JSONArray(selection.selectedStrokeIds))
                        .put("bounds", JSONObject().put("left", selection.bounds.left).put("top", selection.bounds.top)
                            .put("right", selection.bounds.right).put("bottom", selection.bounds.bottom))
                }
                else -> JSONObject().put("sessionId", ref("sessionId")).put("stopped", owner.stop(ref("sessionId")))
            }
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, result.toString())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName,
                "Drawing together is unavailable or the selected sketch changed. Check Devices and select the sketch again.", ToolRejectionKind.INVALID_ARGUMENTS)
        }
    }

    companion object {
        fun resultSchema(name: String): Map<String, Any> {
            val id = mapOf("type" to "string", "maxLength" to 200)
            val fields: Map<String, Any> = when (name) {
                "halo_ink_status" -> mapOf("enabled" to mapOf("type" to "boolean"), "connected" to mapOf("type" to "boolean"),
                    "devices" to mapOf("type" to "array", "maxItems" to 64, "items" to mapOf("type" to "object")),
                    "sessions" to mapOf("type" to "array", "maxItems" to 4, "items" to mapOf("type" to "object")))
                "halo_ink_document" -> mapOf("boardId" to id, "documentId" to id, "revision" to id)
                "halo_ink_start" -> mapOf("sessionId" to id, "boardId" to id, "documentId" to id,
                    "started" to mapOf("type" to "boolean"), "connected" to mapOf("type" to "boolean"))
                "halo_ink_selection" -> mapOf("sessionId" to id, "boardId" to id, "documentId" to id, "sourceRevision" to id,
                    "strokeIds" to mapOf("type" to "array", "minItems" to 1, "maxItems" to 256, "uniqueItems" to true, "items" to id),
                    "bounds" to mapOf("type" to "object", "properties" to listOf("left", "top", "right", "bottom").associateWith {
                        mapOf("type" to "integer", "minimum" to 0, "maximum" to 1000)
                    }, "required" to listOf("left", "top", "right", "bottom"), "additionalProperties" to false))
                else -> mapOf("sessionId" to id, "stopped" to mapOf("type" to "boolean"))
            }
            return mapOf("type" to "object", "properties" to fields, "required" to fields.keys.toList(), "additionalProperties" to false)
        }
    }
}
