package com.halo.app.di.tools

import com.halo.app.data.workspace.*
import com.halo.runtime.cognition.tools.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** Extension of WorkspaceToolExecutor; no competing edit engine or presentation authority. */
class WorkspaceContinuityActions(private val owner: WorkspaceContinuity) : ToolExecutor {
    private val id = mapOf("type" to "string", "pattern" to WorkspaceActionIds.PATTERN)
    private val version = mapOf("type" to "string", "minLength" to 1, "maxLength" to 200)
    private fun definition(name: String, description: String, fields: Map<String, Any>, required: List<String> = fields.keys.toList()) =
        ToolDefinition(name, name, description, mapOf("type" to "object", "properties" to fields, "required" to required, "additionalProperties" to false))
    override fun definitions() = listOf(
        definition("halo_workspace_describe", "Read current active object identity/version, supported handoffs and bounded source history. Earlier/missing source versions never imply that a saved result was updated. Does not read document bodies or promote memory.", mapOf("objectId" to id)),
        definition("halo_workspace_open_with", "Request presentation of an exact active Workspace object in OPEN/FILE_TOOLS/STUDIO/STUDY/CHAT. CHAT selects that exact source through the existing privacy-aware context owner. Dispatch does not prove rendering, model transfer or edit completion. STUDIO image handoff creates a separate project, never overwrites the original.",
            mapOf("objectId" to id, "version" to version, "destination" to mapOf("type" to "string", "enum" to WorkspaceDestination.entries.map { it.name }))),
        definition("halo_workspace_handoff_status", "Read a transient handoff receipt. DELIVERED_TO_HOST means host navigation/selection dispatch only, not a rendered screen or completed effect. A canonical Studio result can report SAVED_RESULT_AVAILABLE with its durable ID/version after restart; this does not prove display. Otherwise UNKNOWN after process death/history expiry; never replay uncertain writes.", mapOf("requestId" to id)),
        definition("halo_workspace_canvases", "Find active Canvas destination IDs and exact revisions for explicit insertion. No content extraction.", mapOf("query" to mapOf("type" to "string", "maxLength" to 200)), emptyList()),
        definition("halo_workspace_insert", "Insert one exact note/file reference on one exact active Canvas. Keeps source identity/provenance; original is unchanged. Export a Studio project first. No automatic retry after either revision changes.",
            mapOf("objectId" to id, "version" to version, "canvasId" to id, "canvasVersion" to version)),
        definition("halo_workspace_select", "Explicitly add the exact object to the existing Working Set. Does not add Memory facts, send data, start a provider or overwrite content.", mapOf("objectId" to id, "version" to version))
    )
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        val args = toolCall.arguments
        fun exact(required: Set<String>, optional: Set<String> = emptySet()) = require(args.keys.containsAll(required) && args.keys.all { it in required || it in optional })
        fun string(key: String): String = (args[key] as? String ?: error("Expected text")).also { require(it.length in 1..200) }
        fun id(key: String) = string(key).also { require(WorkspaceActionIds.isValid(it)) }
        fun success(value: JSONObject, truth: ActionTruth = ActionTruth.CONFIRMED) = ToolExecutionResult.Success(toolCall.id, toolCall.functionName, value.toString(), truth)
        try {
            return when (toolCall.functionName) {
                "halo_workspace_describe" -> {
                    exact(setOf("objectId")); val item = owner.describe(id("objectId"))
                    success(JSONObject().put("objectId", item.objectId).put("version", item.version).put("title", item.title).put("type", item.type.name)
                        .put("destinations", JSONArray(item.destinations.map { it.name })).put("sources", JSONArray().apply { item.sources.forEach { source ->
                            put(JSONObject().put("objectId", source.objectId).put("version", source.version).put("title", source.title ?: JSONObject.NULL)
                                .put("state", source.state.name).put("depth", source.depth).put("nodeId", source.nodeId ?: JSONObject.NULL))
                        } }))
                }
                "halo_workspace_open_with" -> {
                    exact(setOf("objectId", "version", "destination"))
                    val request = owner.requestOpen(id("objectId"), string("version"), WorkspaceDestination.valueOf(string("destination")))
                    success(JSONObject().put("requestId", request.id).put("objectId", request.objectId).put("version", request.version).put("state", "DISPATCHED"), ActionTruth.DISPATCHED)
                }
                "halo_workspace_handoff_status" -> {
                    exact(setOf("requestId")); val requestId = id("requestId")
                    val result = owner.handoffResult(requestId)
                    success(JSONObject().put("requestId", requestId).put("phase", if (result != null) "SAVED_RESULT_AVAILABLE" else owner.requests.phase(requestId)?.name ?: "UNKNOWN")
                        .put("resultId", result?.objectId ?: JSONObject.NULL).put("resultVersion", result?.version ?: JSONObject.NULL))
                }
                "halo_workspace_canvases" -> {
                    exact(emptySet(), setOf("query")); val query = if ("query" in args) (args["query"] as? String ?: error("Expected text")).also { require(it.length <= 200) } else ""
                    val rows = owner.canvases.first().filter { it.title.contains(query, ignoreCase = true) }
                    success(JSONObject().put("canvases", JSONArray().apply { rows.take(20).forEach { row -> put(JSONObject().put("objectId", row.objectId).put("version", row.version).put("title", row.title)) } }).put("more", rows.size > 20))
                }
                "halo_workspace_insert" -> {
                    exact(setOf("objectId", "version", "canvasId", "canvasVersion"))
                    val node = owner.insert(id("canvasId"), string("canvasVersion"), id("objectId"), string("version"))
                    success(JSONObject().put("nodeId", node).put("objectId", id("objectId")).put("version", string("version")).put("canvasId", id("canvasId")))
                }
                "halo_workspace_select" -> {
                    exact(setOf("objectId", "version")); owner.selectWorkingSet(id("objectId"), string("version"))
                    success(JSONObject().put("objectId", id("objectId")).put("version", string("version")).put("state", "SELECTED"))
                }
                else -> error("Unsupported handoff")
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName,
            "Object or destination changed or is unavailable. Reopen it and review the exact source before continuing.", ToolRejectionKind.RESOURCE_UNAVAILABLE) }
    }
}
