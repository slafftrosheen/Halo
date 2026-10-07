package com.halo.app.mcp

import com.halo.app.data.mcp.*
import com.halo.app.security.SecretStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI

fun validateMcpEndpoint(endpoint: String): String? {
    if (endpoint.isBlank() || endpoint.length > 2048) return "Enter an endpoint of 1–2048 characters"
    val uri = runCatching { URI(endpoint) }.getOrNull() ?: return "Enter a valid absolute HTTP or HTTPS endpoint"
    if (!uri.isAbsolute || uri.scheme.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return "Enter a valid absolute HTTP or HTTPS endpoint"
    if (uri.rawUserInfo != null) return "Use the authentication field instead of credentials in the endpoint"
    return null
}
fun mcpEndpointDisplay(endpoint: String): String = runCatching {
    val uri = URI(endpoint)
    URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toString() + if (uri.rawQuery != null) " · query hidden" else ""
}.getOrDefault("Invalid endpoint")
fun mcpStateLabel(state: McpConnectionState): String = when (state) {
    McpConnectionState.UNKNOWN -> "Not connected"
    McpConnectionState.DISABLED -> "Disabled"
    McpConnectionState.CONNECTING -> "Connecting…"
    McpConnectionState.CONNECTED -> "Connected"
    McpConnectionState.ERROR -> "Connection error"
}
fun mcpTrustLabel(trust: McpTrustLevel): String = when (trust) {
    McpTrustLevel.UNTRUSTED -> "Untrusted"
    McpTrustLevel.USER_APPROVED -> "Approved by you"
    McpTrustLevel.LOCAL_TRUSTED -> "Trusted local server"
}
data class McpToolUiModel(val id: String, val displayName: String, val description: String,
    val remoteName: String, val enabled: Boolean, val risk: String, val lastRefreshedAt: Long)
data class McpServerUiModel(val id: String, val displayName: String, val endpointDisplay: String,
    val enabled: Boolean, val state: McpConnectionState, val protocolVersion: String?,
    val auth: McpAuthKind, val credentialSaved: Boolean, val trust: McpTrustLevel,
    val lastConnectedAt: Long?, val lastError: String?, val tools: List<McpToolUiModel>)
data class McpServerConfiguration(val name: String, val endpoint: String, val auth: McpAuthKind, val trust: McpTrustLevel)

/** Settings owns configuration and discovery only. No tools/call API is exposed here. */
class McpSettingsService(private val dao: McpDao, private val manager: McpIntegrationManager,
    private val secrets: SecretStore, private val syncRegistry: suspend () -> Unit) {
    private val configuration = Mutex()
    val servers: Flow<List<McpServerUiModel>> = combine(dao.observeServers(), dao.observeAllTools(), manager.states) { servers, tools, _ ->
        servers.map { server ->
            val auth = runCatching { McpAuthKind.valueOf(server.authKind) }.getOrDefault(McpAuthKind.NONE)
            val saved = auth == McpAuthKind.BEARER && server.credentialRef?.let { !runCatching { secrets.get(it) }.getOrNull().isNullOrBlank() } == true
            McpServerUiModel(server.id, server.displayName, mcpEndpointDisplay(server.endpoint), server.enabled,
                if (server.enabled) manager.stateOf(server.id) else McpConnectionState.DISABLED, server.protocolVersion,
                auth, saved, runCatching { McpTrustLevel.valueOf(server.trust) }.getOrDefault(McpTrustLevel.UNTRUSTED),
                server.lastConnectedAt, if (auth == McpAuthKind.BEARER && !saved) "Credential required" else safeError(server.lastError),
                tools.filter { it.serverId == server.id }.map { McpToolUiModel(it.id, it.displayName, it.description,
                    it.remoteName, it.enabled, it.risk, it.lastRefreshedAt) })
        }
    }.flowOn(Dispatchers.IO)
    suspend fun configuration(id: String): McpServerConfiguration? = dao.getServer(id)?.let {
        McpServerConfiguration(it.displayName, it.endpoint, McpAuthKind.valueOf(it.authKind), McpTrustLevel.valueOf(it.trust))
    }
    suspend fun save(id: String?, value: McpServerConfiguration, newToken: String? = null, removeCredential: Boolean = false): String = configuration.withLock {
        require(value.name.isNotBlank() && value.name.length <= 80) { "Enter a name of 1–80 characters" }
        validateMcpEndpoint(value.endpoint)?.let { throw IllegalArgumentException(it) }
        require(newToken == null || (newToken.isNotBlank() && newToken.length <= 8192)) { "Enter a bearer token" }
        withContext(NonCancellable) {
            val serverId = id ?: manager.addServer(value.name, value.endpoint, value.auth, trust = value.trust)
            val current = checkNotNull(dao.getServer(serverId))
            val ref = current.credentialRef ?: "mcp.server.$serverId.bearer"
            try {
                check(manager.updateServer(serverId)) // Invalidate the old session before any secret changes.
                val clearing = value.auth == McpAuthKind.NONE || removeCredential
                if (clearing) current.credentialRef?.let { secrets.remove(it) }
                else if (newToken != null) secrets.put(ref, newToken)
                check(manager.updateServer(serverId, value.name, value.endpoint, value.auth,
                    if (clearing) null else if (newToken != null) ref else current.credentialRef, value.trust,
                    replaceCredentialReference = true))
                serverId
            } catch (failure: Exception) {
                if (id == null) { secrets.remove(ref); manager.removeServer(serverId) }
                throw failure
            } finally { syncRegistry(); log("HALO_MCP_SERVER_SAVED", serverId) }
        }
    }
    suspend fun setEnabled(id: String, enabled: Boolean) = operation("HALO_MCP_ENABLE", id) { manager.setEnabled(id, enabled); Unit }
    suspend fun setToolEnabled(id: String, enabled: Boolean) = operation("HALO_MCP_TOOL_TOGGLE", "tool") { manager.setToolEnabled(id, enabled) }
    suspend fun connect(id: String) = operation("HALO_MCP_CONNECT", id) { manager.connect(id); Unit }
    suspend fun refreshTools(id: String) = operation("HALO_MCP_TOOL_REFRESH", id) { manager.refreshTools(id); Unit }
    private suspend fun operation(event: String, id: String, action: suspend () -> Unit) {
        try { action() } finally { withContext(NonCancellable) { syncRegistry(); log(event, id) } }
    }
    private fun log(event: String, id: String) {
        android.util.Log.i("HaloMcp", "$event server=${id.take(8)} state=${manager.stateOf(id)}")
    }
    suspend fun remove(id: String) = configuration.withLock {
        withContext(NonCancellable) {
            val current = dao.getServer(id) ?: return@withContext
            manager.setEnabled(id, false); syncRegistry()
            current.credentialRef?.let { secrets.remove(it) }
            manager.removeServer(id); syncRegistry(); log("HALO_MCP_SERVER_REMOVED", id)
        }
    }
    private fun safeError(error: String?): String? = error?.let {
        // Existing client errors are content-free. Unknown/restored error text is not displayed.
        when {
            it == "Unsupported protocol version" -> it
            it.startsWith("Bearer credential") -> "Credential required"
            it.matches(Regex("""(Connection failed|Tool listing failed) \(\w+\)""")) -> it.take(120)
            it.matches(Regex("""Server responded with HTTP \d{3}""")) -> it
            it.matches(Regex("""Server returned JSON-RPC error -?\d+""")) -> it
            it in setOf("Response contained no result object", "Empty response body", "Response was not JSON", "Tool namespace collision") -> it
            else -> "MCP discovery failed"
        }
    }
}
