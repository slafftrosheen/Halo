package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolDefinition

object McpSettingsOperability {
    val reads = setOf("halo_mcp_servers", "halo_mcp_server_state")
    val writes = setOf("halo_mcp_server_set_enabled", "halo_mcp_tool_set_enabled", "halo_mcp_refresh_tools")
    fun forDefinition(definition: ToolDefinition): ToolOperability? {
        if (definition.name !in reads + writes) return null
        return ToolOperability(capabilityId = CapabilityId("host.assistant.mcp_settings"),
            owner = "McpSettingsService/McpIntegrationManager", operation = if (definition.name in reads) ToolOperation.QUERY else ToolOperation.EDIT,
            resultEncoding = ToolResultEncoding.JSON,
            resultSchema = mapOf("type" to "object", "properties" to mapOf(
                "serverId" to mapOf("type" to "string", "maxLength" to 240),
                "sourceVersion" to mapOf("type" to "string", "pattern" to "^mcp-settings:[a-f0-9]{64}$"),
                "servers" to mapOf("type" to "array", "maxItems" to 64, "items" to mapOf("type" to "object")),
                "tools" to mapOf("type" to "array", "maxItems" to 30, "items" to mapOf("type" to "object")),
                "nextOffset" to mapOf("type" to listOf("integer", "null")),
                "enabled" to mapOf("type" to "boolean"), "state" to mapOf("type" to "string"),
                "knownToolCount" to mapOf("type" to "integer"), "applied" to mapOf("type" to "boolean"),
                "networkRequested" to mapOf("type" to "boolean"), "toolExecuted" to mapOf("type" to "boolean"),
                "connectionRequested" to mapOf("type" to "boolean"))),
            sourceVersionArguments = if (definition.name in writes) setOf("sourceVersion") else emptySet(),
            resultIdFields = if (definition.name == "halo_mcp_servers") emptySet() else setOf("serverId"),
            limitations = setOf("Personal server/tool configuration requires existing Workspace disclosure authority remotely.",
                "No labels, endpoints, secrets, remote descriptions/schemas, credential edits, trust change, implicit connect or tool execution.",
                "Writes require foreground grown-up interaction and durable ALWAYS approval. The exact process-local version is consumed before dispatch; NEVER_REPLAY uncertain writes.",
                "Refresh requires an already-connected session and retains the existing connected-service TEXT/WORKSPACE remote client policy.",
                "Saved enabled state or successful discovery is not remote-tool execution or device qualification."))
    }
}
