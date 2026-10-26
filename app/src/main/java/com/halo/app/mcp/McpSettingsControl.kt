package com.halo.app.mcp

/** Non-secret projection of the existing Settings owner, not a second configuration store. */
data class McpSettingsToolState(val id: String, val enabled: Boolean)
data class McpSettingsSnapshot(val serverId: String, val sourceVersion: String, val enabled: Boolean,
    val state: String, val tools: List<McpSettingsToolState>)
data class McpSettingsEffect(val snapshot: McpSettingsSnapshot, val applied: Boolean, val networkRequested: Boolean)
enum class McpSettingsCommand { SERVER_ENABLED, TOOL_ENABLED, REFRESH }
interface McpSettingsControl {
    suspend fun settingsCatalog(): List<McpSettingsSnapshot>
    suspend fun settingsSnapshot(id: String): McpSettingsSnapshot?
    suspend fun updateSettings(id: String, sourceVersion: String, command: McpSettingsCommand,
        toolId: String?, enabled: Boolean?, canConfigure: () -> Boolean): McpSettingsEffect?
}
