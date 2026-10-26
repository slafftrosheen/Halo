package com.halo.app.di.tools

import com.halo.app.mcp.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** No keys, endpoints, implicit connect, calls or approval shortcuts. */
class McpSettingsTools(private val owner: McpSettingsControl,
    private val canConfigure: () -> Boolean = { false }) : ToolExecutor {
    private val id = mapOf("type" to "string", "pattern" to "^[A-Za-z0-9_.:/+-]{1,240}$")
    private val version = mapOf("type" to "string", "pattern" to "^mcp-settings:[a-f0-9]{64}$")
    private val offset = mapOf("type" to "integer", "minimum" to 0, "maximum" to 10000)
    private val exact = mapOf("serverId" to id, "sourceVersion" to version)
    private fun definition(name: String, description: String, fields: Map<String, Any>, required: List<String> = fields.keys.toList()) =
        ToolDefinition(name, name, description, mapOf("type" to "object", "properties" to fields,
            "required" to required, "additionalProperties" to false))
    override fun definitions() = listOf(
        definition("halo_mcp_servers", "Read bounded existing MCP server IDs and local status; no names, endpoint, secrets or remote request. Metadata is personal Workspace configuration.", mapOf("offset" to offset), emptyList()),
        definition("halo_mcp_server_state", "Read one exact MCP server and bounded tool IDs/enabled states with fresh sourceVersion. No endpoint, secret, remote description/schema or network. Read before changes.", mapOf("serverId" to id, "offset" to offset), listOf("serverId")),
        definition("halo_mcp_server_set_enabled", "After durable approval set the existing MCP server enabled flag at exact sourceVersion. Enabling does not connect, authorize calls or change trust/credentials. Disabling withdraws its live tool surface. Verify returned saved state; never replay uncertain mutations.", exact + ("enabled" to mapOf("type" to "boolean"))),
        definition("halo_mcp_tool_set_enabled", "After durable approval set one observed tool's enabled flag on its exact server/sourceVersion. Does not call it, alter trust/risk or waive tool approval. No network or implicit connect. Never replay uncertain writes.", exact + mapOf("toolId" to id, "enabled" to mapOf("type" to "boolean"))),
        definition("halo_mcp_refresh_tools", "After durable approval refresh tool discovery for an already connected, enabled exact server/sourceVersion. Uses existing authenticated discovery and remote policy; never connects a disconnected server. No remote tool execution, new credentials or grants. Failure remains failure; never replay uncertain requests.", exact)
    )
    override suspend fun execute(toolCall: ToolCall) = execute(toolCall, ToolExecutionContext())
    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        fun reject(kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(
            toolCall.id, toolCall.functionName, "MCP settings request unavailable, changed or not authorized. Read current state before a fresh decision.", kind)
        var dispatched = false
        try {
            val definition = definitions().singleOrNull { it.name == toolCall.functionName } ?: return reject(ToolRejectionKind.TOOL_UNKNOWN)
            val args = toolCall.arguments
            val fields = definition.parameters["properties"] as Map<*, *>
            val required = definition.parameters["required"] as List<*>
            require(args.keys.all { it in fields } && args.keys.containsAll(required.filterIsInstance<String>()))
            if (!canConfigure()) return reject(ToolRejectionKind.PERMISSION_REQUIRED)
            val offset = if ("offset" in args) (args["offset"] as? Number)?.let {
                require(it.toDouble().isFinite() && it.toDouble() == it.toLong().toDouble() && it.toLong() in 0..10000); it.toInt()
            } ?: return reject() else 0
            if (toolCall.functionName == "halo_mcp_servers") {
                val values = owner.settingsCatalog()
                val output = JSONObject().put("servers", JSONArray(values.drop(offset).take(64).map { summary(it) }))
                    .put("nextOffset", if (offset + 64 < values.size) offset + 64 else JSONObject.NULL).put("networkRequested", false)
                if (!canConfigure()) return reject(ToolRejectionKind.PERMISSION_REQUIRED)
                return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, output.toString(), ActionTruth.ACKNOWLEDGED)
            }
            fun identifier(key: String): String = (args[key] as? String)?.also {
                require(it.matches(Regex("[A-Za-z0-9_.:/+-]{1,240}")))
            } ?: throw IllegalArgumentException()
            val serverId = identifier("serverId")
            val reading = toolCall.functionName == "halo_mcp_server_state"
            val effect = if (reading) null else {
                if (context.interactionId.isNullOrBlank()) return reject(ToolRejectionKind.PERMISSION_REQUIRED)
                val sourceVersion = args["sourceVersion"] as? String ?: return reject()
                require(sourceVersion.matches(Regex("mcp-settings:[a-f0-9]{64}")))
                val command = when (toolCall.functionName) {
                    "halo_mcp_server_set_enabled" -> McpSettingsCommand.SERVER_ENABLED
                    "halo_mcp_tool_set_enabled" -> McpSettingsCommand.TOOL_ENABLED
                    else -> McpSettingsCommand.REFRESH
                }
                val toolId = if (command == McpSettingsCommand.TOOL_ENABLED) identifier("toolId") else null
                val enabled = if (command != McpSettingsCommand.REFRESH) args["enabled"] as? Boolean ?: return reject() else null
                dispatched = true
                owner.updateSettings(serverId, sourceVersion, command, toolId, enabled, canConfigure)
                    ?: return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE)
            }
            val value = effect?.snapshot ?: owner.settingsSnapshot(serverId) ?: return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE)
            if (effect != null && !effect.applied) return ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
                "MCP settings operation did not complete successfully. Read current state; do not replay this request.", completionUnknown = false)
            val output = summary(value).put("tools", JSONArray(value.tools.drop(offset).take(30).map {
                JSONObject().put("toolId", it.id).put("enabled", it.enabled)
            })).put("nextOffset", if (offset + 30 < value.tools.size) offset + 30 else JSONObject.NULL)
                .put("networkRequested", effect?.networkRequested ?: false).put("applied", effect?.applied ?: false)
                .put("toolExecuted", false).put("connectionRequested", false)
            if (!canConfigure()) return if (dispatched) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
                "MCP settings access changed after dispatch. Completion metadata withheld; do not replay this request.", completionUnknown = true)
                else reject(ToolRejectionKind.PERMISSION_REQUIRED)
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, output.toString(),
                if (reading) ActionTruth.ACKNOWLEDGED else ActionTruth.CONFIRMED)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) { return if (dispatched) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
            "MCP settings completion is unknown. Read current state when authorized; never replay this request.", completionUnknown = true) else reject() }
        catch (_: Exception) { return if (dispatched) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
            "MCP settings completion is unknown. Read current state; never replay this request.", completionUnknown = true)
            else reject(ToolRejectionKind.RESOURCE_UNAVAILABLE) }
    }
    private fun summary(value: McpSettingsSnapshot) = JSONObject().put("serverId", value.serverId)
        .put("sourceVersion", value.sourceVersion).put("enabled", value.enabled).put("state", value.state)
        .put("knownToolCount", value.tools.size)
}
