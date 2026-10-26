package com.halo.app.mcp

import com.halo.app.data.mcp.*
import com.halo.app.security.SecretStore
import com.halo.app.ui.shell.primaryNavigationRoute
import com.halo.runtime.cognition.tools.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class McpSettingsTest {
    @Test fun reconnectRequiresFreshListingBeforeExposingCachedTools() = runBlocking {
        val f=Fixture(); val id=f.live()
        assertEquals(3,f.registry.definitions().size)
        f.service.connect(id)
        assertTrue(f.registry.definitions().isEmpty())
        f.service.refreshTools(id)
        assertEquals(3,f.registry.definitions().size)
    }
    @Test fun failedRefreshKeepsHistoricalRowsButWithdrawsTheLiveSurface() = runBlocking {
        val f=Fixture(); val id=f.live()
        f.transport.badListing=true
        f.service.refreshTools(id)
        assertEquals(McpConnectionState.ERROR,f.manager.stateOf(id))
        assertTrue(f.registry.definitions().isEmpty())
        assertEquals(3,f.dao.toolsForServer(id).size)
    }
    @Test fun typedEnableRequiresCurrentVersionAndDoesNotConnect() = runBlocking {
        val f = Fixture(); val id = f.added(); val before = f.service.settingsSnapshot(id)!!
        val effect = f.service.updateSettings(id, before.sourceVersion, McpSettingsCommand.SERVER_ENABLED, null, true) { true }!!
        assertTrue(effect.applied); assertTrue(effect.snapshot.enabled); assertFalse(effect.networkRequested)
        assertTrue(f.transport.methods.isEmpty())
        assertNull(f.service.updateSettings(id, before.sourceVersion, McpSettingsCommand.SERVER_ENABLED, null, false) { true })
        assertTrue(f.service.settingsSnapshot(id)!!.enabled)
    }
    @Test fun typedRefreshNeverConnectsAndConsumesConnectedVersion() = runBlocking {
        val f = Fixture(); val id = f.added(); f.service.setEnabled(id, true)
        val disconnected = f.service.settingsSnapshot(id)!!
        assertNull(f.service.updateSettings(id, disconnected.sourceVersion, McpSettingsCommand.REFRESH, null, null) { true })
        assertTrue(f.transport.methods.isEmpty())
        f.service.connect(id); f.transport.methods.clear()
        val connected = f.service.settingsSnapshot(id)!!
        val effect = f.service.updateSettings(id, connected.sourceVersion, McpSettingsCommand.REFRESH, null, null) { true }!!
        assertTrue(effect.applied); assertTrue(effect.networkRequested)
        assertEquals(listOf("tools/list"), f.transport.methods)
        assertEquals(3, f.registry.definitions().size)
        assertNull(f.service.updateSettings(id, connected.sourceVersion, McpSettingsCommand.REFRESH, null, null) { true })
    }
    @Test fun typedToolToggleChecksServerMembershipAndSynchronizesExistingRegistry() = runBlocking {
        val f = Fixture(); val id = f.live(); val tool = McpToolAdapter.namespaceId(id, "one")
        val before = f.service.settingsSnapshot(id)!!; f.transport.methods.clear()
        assertNull(f.service.updateSettings(id, before.sourceVersion, McpSettingsCommand.TOOL_ENABLED, "unknown", false) { true })
        val effect = f.service.updateSettings(id, before.sourceVersion, McpSettingsCommand.TOOL_ENABLED, tool, false) { true }!!
        assertTrue(effect.applied); assertFalse(f.dao.tool(tool)!!.enabled); assertFalse(f.registry.contains(tool))
        assertTrue(f.transport.methods.isEmpty())
    }
    @Test fun typedRefreshFailureIsNotSuccessAndInvalidatesVersion() = runBlocking {
        val f = Fixture(); val id = f.live(); val before = f.service.settingsSnapshot(id)!!
        f.transport.badListing = true
        val effect = f.service.updateSettings(id, before.sourceVersion, McpSettingsCommand.REFRESH, null, null) { true }!!
        assertFalse(effect.applied); assertEquals("ERROR", effect.snapshot.state)
        assertNotEquals(before.sourceVersion, effect.snapshot.sourceVersion); assertTrue(f.registry.definitions().isEmpty())
    }
    @Test fun typedAdapterRequiresInteractionGateAndOmitsSecretsAndSourceText() = runBlocking {
        val f = Fixture(); val id = f.added()
        val tool = com.halo.app.di.tools.McpSettingsTools(f.service) { true }
        val read = tool.execute(com.halo.sdk.provider.ToolCall("r", "halo_mcp_server_state", mapOf("serverId" to id))) as ToolExecutionResult.Success
        assertFalse(read.output.contains("example.test")); assertFalse(read.output.contains("Home")); assertFalse(read.output.contains("credential"))
        val args = mapOf("serverId" to id, "sourceVersion" to JSONObject(read.output).getString("sourceVersion"), "enabled" to true)
        val call = com.halo.sdk.provider.ToolCall("w", "halo_mcp_server_set_enabled", args)
        assertTrue(tool.execute(call) is ToolExecutionResult.Rejected)
        val denied = com.halo.app.di.tools.McpSettingsTools(f.service)
        assertTrue(denied.execute(call, ToolExecutionContext("explicit", false)) is ToolExecutionResult.Rejected)
        assertTrue(tool.execute(call, ToolExecutionContext("explicit", false)) is ToolExecutionResult.Success)
        assertTrue(tool.execute(call, ToolExecutionContext("explicit", false)) is ToolExecutionResult.Rejected)
        assertTrue(f.transport.methods.isEmpty())
    }
    @Test fun manualEditOrRevokedAdmissionInvalidatesTypedWrite() = runBlocking {
        val f = Fixture(); val id = f.added(); val before = f.service.settingsSnapshot(id)!!
        assertNull(f.service.updateSettings(id, before.sourceVersion, McpSettingsCommand.SERVER_ENABLED, null, true) { false })
        f.service.save(id, f.value.copy(name = "Renamed"))
        assertNull(f.service.updateSettings(id, before.sourceVersion, McpSettingsCommand.SERVER_ENABLED, null, true) { true })
        assertFalse(f.service.settingsSnapshot(id)!!.enabled)
    }

    @Test fun typedMutationWithholdsMetadataAfterAccessRevocation() = runBlocking {
        val f = Fixture(); val id = f.added(); var allowed = true
        val owner = object : McpSettingsControl by f.service {
            override suspend fun updateSettings(id: String, sourceVersion: String, command: McpSettingsCommand,
                toolId: String?, enabled: Boolean?, canConfigure: () -> Boolean): McpSettingsEffect? {
                val effect = f.service.updateSettings(id, sourceVersion, command, toolId, enabled, canConfigure)
                allowed = false
                return effect
            }
        }
        val tool = com.halo.app.di.tools.McpSettingsTools(owner) { allowed }
        val call = com.halo.sdk.provider.ToolCall("write", "halo_mcp_server_set_enabled", mapOf("serverId" to id,
            "sourceVersion" to f.service.settingsSnapshot(id)!!.sourceVersion, "enabled" to true))
        val result = tool.execute(call, ToolExecutionContext("explicit", false)) as ToolExecutionResult.Failed
        assertTrue(result.completionUnknown); assertTrue(f.service.settingsSnapshot(id)!!.enabled)
        assertFalse(result.toString().contains("server-uuid"))
    }
    @Test fun typedMutationExceptionAfterActualEffectIsUnknownNotRejected() = runBlocking {
        val f = Fixture(); val id = f.added()
        val owner = object : McpSettingsControl by f.service {
            override suspend fun updateSettings(id: String, sourceVersion: String, command: McpSettingsCommand,
                toolId: String?, enabled: Boolean?, canConfigure: () -> Boolean): McpSettingsEffect? {
                f.service.updateSettings(id, sourceVersion, command, toolId, enabled, canConfigure)
                throw IllegalArgumentException("fixture after effect")
            }
        }
        val tool = com.halo.app.di.tools.McpSettingsTools(owner) { true }
        val source = f.service.settingsSnapshot(id)!!.sourceVersion
        val call = com.halo.sdk.provider.ToolCall("write", "halo_mcp_server_set_enabled", mapOf("serverId" to id,
            "sourceVersion" to source, "enabled" to true))
        val result = tool.execute(call, ToolExecutionContext("explicit", false)) as ToolExecutionResult.Failed
        assertTrue(result.completionUnknown); assertTrue(f.service.settingsSnapshot(id)!!.enabled)
        assertNull(f.service.updateSettings(id, source, McpSettingsCommand.SERVER_ENABLED, null, false) { true })
    }

    private class Fixture {
        val dao = FakeDao(); val secrets = FakeSecrets(); val transport = FakeTransport()
        val client = McpClient(transport, credentialResolver = { it?.let { ref -> secrets.get(ref) } })
        val manager = McpIntegrationManager(dao, client, now = { 1234L }, newId = { "server-uuid" })
        val registry = ToolRegistry(emptyList())
        val service = McpSettingsService(dao, manager, secrets) { manager.syncRegistry(registry) }
        val value = McpServerConfiguration("Home", "https://example.test/mcp", McpAuthKind.NONE, McpTrustLevel.UNTRUSTED)
        suspend fun added() = service.save(null, value)
        suspend fun live(): String { val id = added(); service.setEnabled(id, true); service.refreshTools(id); return id }
    }
    @Test fun addAndObserveArePassiveAndStartDisabled() = runBlocking {
        val f = Fixture(); val id = f.added()
        val row = f.service.servers.first().single()
        assertEquals(id, row.id); assertFalse(row.enabled); assertEquals(McpConnectionState.DISABLED, row.state)
        assertTrue(f.transport.methods.isEmpty()); assertTrue(f.registry.definitions().isEmpty())
    }
    @Test fun validateEndpointWithoutRepairOrPersistence() = runBlocking {
        val f = Fixture()
        listOf("", "relative/mcp", "ftp://example.test/mcp", "https:///mcp", "https://user:pass@example.test/mcp", "https://bad host/mcp").forEach { invalid ->
            assertNotNull(validateMcpEndpoint(invalid))
            assertTrue(runCatching { f.service.save(null, f.value.copy(endpoint = invalid)) }.isFailure)
        }
        assertNull(validateMcpEndpoint("http://192.168.1.20:8080/exact-path"))
        assertNull(validateMcpEndpoint("https://example.test/custom?route=one"))
        assertTrue(f.dao.allServers().isEmpty()); assertTrue(f.transport.methods.isEmpty())
    }
    @Test fun endpointDisplayHidesQueryAndEmbeddedCredentials() {
        val display = mcpEndpointDisplay("https://user:secret@example.test/mcp?token=private#fragment")
        assertFalse(display.contains("secret")); assertFalse(display.contains("private")); assertFalse(display.contains("user"))
        assertTrue(display.contains("query hidden"))
    }
    @Test fun explicitConnectInitializesOnceAndDoesNotCallTools() = runBlocking {
        val f = Fixture(); val id = f.added(); f.service.setEnabled(id, true)
        assertEquals(McpConnectionState.UNKNOWN, f.manager.stateOf(id)); assertTrue(f.transport.methods.isEmpty())
        f.transport.onInitialize = { assertEquals(McpConnectionState.CONNECTING, f.manager.stateOf(id)) }
        f.service.connect(id)
        assertEquals(listOf("initialize", "notifications/initialized"), f.transport.methods)
        assertEquals(McpConnectionState.CONNECTED, f.manager.stateOf(id))
        assertEquals(McpProtocol.PREFERRED_VERSION, f.dao.getServer(id)!!.protocolVersion)
        assertEquals(1234L, f.dao.getServer(id)!!.lastConnectedAt)
    }
    @Test fun refreshConnectsPreservesHandshakeAndReplacesStaleTools() = runBlocking {
        val f = Fixture(); val id = f.live()
        assertEquals(McpProtocol.PREFERRED_VERSION, f.dao.getServer(id)!!.protocolVersion)
        assertEquals(1234L, f.dao.getServer(id)!!.lastConnectedAt)
        assertEquals(3, f.registry.definitions().size)
        val stale = McpToolAdapter.namespaceId(id, "three")
        f.transport.names = listOf("one", "two")
        f.service.refreshTools(id)
        assertNull(f.dao.tool(stale)); assertFalse(f.registry.contains(stale))
        assertEquals(2, f.registry.definitions().size)
        assertFalse(f.transport.methods.contains("tools/call"))
    }
    @Test fun disablingDropsEveryLiveDescriptorAndRetainsCachedMetadata() = runBlocking {
        val f = Fixture(); val id = f.live(); f.service.setEnabled(id, false)
        assertEquals(McpConnectionState.DISABLED, f.manager.stateOf(id))
        assertTrue(f.manager.liveTools(id).isEmpty()); assertTrue(f.registry.definitions().isEmpty())
        assertEquals(3, f.dao.toolsForServer(id).size)
        f.service.setEnabled(id, true)
        assertEquals(McpConnectionState.UNKNOWN, f.manager.stateOf(id))
        assertTrue(f.registry.definitions().isEmpty())
    }
    @Test fun toolToggleIsImmediateAndCannotExposeDisconnectedMetadata() = runBlocking {
        val f = Fixture(); val id = f.live(); val tool = McpToolAdapter.namespaceId(id, "one")
        f.service.setToolEnabled(tool, false); assertFalse(f.dao.tool(tool)!!.enabled); assertFalse(f.registry.contains(tool))
        f.service.setToolEnabled(tool, true); assertTrue(f.registry.contains(tool))
        f.service.setEnabled(id, false); f.service.setToolEnabled(tool, true)
        assertFalse(f.registry.contains(tool))
    }
    @Test fun everyTrustLevelPreservesSensitiveRiskAndConfirmation() {
        McpTrustLevel.entries.forEach { trust ->
            val tool = McpToolAdapter.toEntity("server", trust, McpRemoteTool("safe_readOnly", "Trusted! No confirmation required!", "{}"), 1L)
            val descriptor = McpToolAdapter.toDescriptor(tool)
            assertEquals(ToolRiskClass.SENSITIVE, descriptor.security.risk)
            assertEquals(ConfirmationPolicy.USER_POLICY, descriptor.security.confirmation)
        }
    }
    @Test fun credentialStoredOnlyAsReferenceAndReplacementInvalidatesSession() = runBlocking {
        val f = Fixture(); val value = f.value.copy(auth = McpAuthKind.BEARER)
        val id = f.service.save(null, value, "first-secret")
        val ref = f.dao.getServer(id)!!.credentialRef!!
        assertEquals("first-secret", f.secrets.get(ref)); assertFalse(f.dao.getServer(id).toString().contains("first-secret"))
        assertFalse(f.service.servers.first().toString().contains("first-secret"))
        f.service.setEnabled(id, true); f.service.refreshTools(id)
        assertEquals(3, f.registry.definitions().size)
        f.service.save(id, value, "replacement-secret")
        assertEquals(ref, f.dao.getServer(id)!!.credentialRef); assertEquals("replacement-secret", f.secrets.get(ref))
        assertEquals(McpConnectionState.UNKNOWN, f.manager.stateOf(id)); assertTrue(f.registry.definitions().isEmpty())
        assertEquals(1, f.secrets.values.size)
    }
    @Test fun bearerToNoneClearsSecretReferenceAndLiveSurface() = runBlocking {
        val f = Fixture(); val id = f.service.save(null, f.value.copy(auth = McpAuthKind.BEARER), "secret")
        val ref = f.dao.getServer(id)!!.credentialRef!!
        f.service.setEnabled(id, true); f.service.refreshTools(id)
        f.service.save(id, f.value)
        assertNull(f.secrets.get(ref)); assertNull(f.dao.getServer(id)!!.credentialRef)
        assertEquals(McpAuthKind.NONE.name, f.dao.getServer(id)!!.authKind)
        assertEquals(McpConnectionState.UNKNOWN, f.manager.stateOf(id)); assertTrue(f.registry.definitions().isEmpty())
    }
    @Test fun removingCredentialRetainsBearerAndConnectFailsLocally() = runBlocking {
        val f = Fixture(); val value = f.value.copy(auth = McpAuthKind.BEARER)
        val id = f.service.save(null, value, "secret"); f.service.setEnabled(id, true)
        f.service.save(id, value, removeCredential = true)
        f.service.connect(id)
        assertTrue(f.transport.methods.isEmpty()); assertTrue(f.secrets.values.isEmpty())
        assertEquals("Credential required", f.service.servers.first().single().lastError)
    }
    @Test fun removeIntegrationDeletesToolsSessionAndSecretWithoutRemoteRequest() = runBlocking {
        val f = Fixture(); val id = f.service.save(null, f.value.copy(auth = McpAuthKind.BEARER), "secret")
        f.service.setEnabled(id, true); f.service.refreshTools(id)
        val calls = f.transport.methods.size; f.service.remove(id)
        assertNull(f.dao.getServer(id)); assertTrue(f.dao.toolsForServer(id).isEmpty()); assertTrue(f.secrets.values.isEmpty())
        assertTrue(f.manager.liveTools(id).isEmpty()); assertTrue(f.registry.definitions().isEmpty())
        assertEquals(calls, f.transport.methods.size)
    }
    @Test fun freshManagerDoesNotFabricateConnectionOrExposeCachedTools() = runBlocking {
        val f = Fixture(); val id = f.live()
        val fresh = McpIntegrationManager(f.dao, f.client)
        val registry = ToolRegistry(emptyList()); fresh.syncRegistry(registry)
        assertEquals(McpConnectionState.UNKNOWN, fresh.stateOf(id)); assertTrue(registry.definitions().isEmpty())
        assertNotNull(f.dao.getServer(id)!!.lastConnectedAt)
        val service = McpSettingsService(f.dao, fresh, f.secrets) { fresh.syncRegistry(registry) }
        assertEquals(McpConnectionState.UNKNOWN, service.servers.first().single().state)
        assertEquals(3, f.transport.methods.size)
    }
    @Test fun cancelledDiscoveryDropsConnectingStateAndPropagatesCancellation() = runBlocking {
        val f = Fixture(); val id = f.added(); f.service.setEnabled(id, true)
        val started = CompletableDeferred<Unit>()
        f.transport.onInitialize = { started.complete(Unit); awaitCancellation() }
        val job = launch { f.service.connect(id) }
        started.await(); job.cancelAndJoin()
        assertEquals(McpConnectionState.UNKNOWN, f.manager.stateOf(id)); assertTrue(f.registry.definitions().isEmpty())
    }
    @Test fun unsupportedProtocolAndRecoveryHaveTruthfulErrors() = runBlocking {
        val f = Fixture(); val id = f.added(); f.service.setEnabled(id, true)
        f.transport.version = "2099-01-01"; f.service.connect(id)
        assertEquals("Unsupported protocol version", f.service.servers.first().single().lastError)
        assertEquals(McpConnectionState.ERROR, f.manager.stateOf(id))
        f.transport.version = McpProtocol.PREFERRED_VERSION; f.service.connect(id)
        assertNull(f.dao.getServer(id)!!.lastError); assertEquals(McpConnectionState.CONNECTED, f.manager.stateOf(id))
    }
    @Test fun endpointEditsKeepIdentityAndRequireExplicitReconnect() = runBlocking {
        val f = Fixture(); val id = f.live(); val calls = f.transport.methods.size
        f.service.save(id, f.value.copy(name = "Renamed", endpoint = "https://new.example.test/exact"))
        assertEquals(id, f.dao.getServer(id)!!.id); assertEquals(McpConnectionState.UNKNOWN, f.manager.stateOf(id))
        assertTrue(f.registry.definitions().isEmpty()); assertEquals(calls, f.transport.methods.size)
    }
    @Test fun navigationKeepsSettingsAndServiceExposesNoToolExecution() {
        assertEquals("settings", primaryNavigationRoute("integrations"))
        assertEquals("settings", primaryNavigationRoute("integrations/mcp/{serverId}"))
        assertFalse(McpSettingsService::class.java.declaredMethods.any { it.name.contains("callTool") })
    }
    private class FakeTransport : McpTransport {
        val methods = mutableListOf<String>(); var names = listOf("one", "two", "three")
        var version = McpProtocol.PREFERRED_VERSION
        var badListing=false
        var onInitialize: suspend () -> Unit = {}
        override suspend fun post(endpoint: String, body: String, bearerToken: String?, sessionId: String?, protocolVersion: String?): McpHttpResponse {
            val request = JSONObject(body); val method = request.getString("method"); methods += method
            val result = when (method) {
                "initialize" -> { onInitialize(); JSONObject().put("protocolVersion", version).put("serverInfo", JSONObject().put("name", "Fixture").put("version", "1")) }
                "notifications/initialized" -> return McpHttpResponse(202, "", "session", null)
                "tools/list" -> JSONObject().put("tools", org.json.JSONArray(names.map { JSONObject().put("name", it).put("description", "External data").put("inputSchema", JSONObject().put("type", "object")) }))
                else -> error("Settings must never execute tools")
            }
            return McpHttpResponse(200, JSONObject().put("jsonrpc", "2.0").put("id", if(badListing && method == "tools/list") 999 else request.optLong("id")).put("result", result).toString(), "session", "application/json")
        }
    }
    private class FakeSecrets : SecretStore {
        val values = mutableMapOf<String, String>()
        override suspend fun get(key: String) = values[key]
        override suspend fun put(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }
    private class FakeDao : McpDao {
        val servers = MutableStateFlow<List<McpServerEntity>>(emptyList())
        val tools = MutableStateFlow<List<McpToolEntity>>(emptyList())
        override fun observeServers(): Flow<List<McpServerEntity>> = servers
        override fun observeAllTools(): Flow<List<McpToolEntity>> = tools
        override fun observeTools(serverId: String) = tools.map { it.filter { row -> row.serverId == serverId } }
        override suspend fun getServer(id: String) = servers.value.firstOrNull { it.id == id }
        override suspend fun allServers() = servers.value
        override suspend fun upsertServer(server: McpServerEntity) { servers.value = servers.value.filterNot { it.id == server.id } + server }
        override suspend fun deleteServer(id: String) { servers.value = servers.value.filterNot { it.id == id } }
        override suspend fun enabledTools() = tools.value.filter { it.enabled }
        override suspend fun toolsForServer(serverId: String) = tools.value.filter { it.serverId == serverId }
        override suspend fun tool(id: String) = tools.value.firstOrNull { it.id == id }
        override suspend fun enabledToolCount() = tools.value.count { it.enabled }
        override suspend fun upsertTools(tools: List<McpToolEntity>) { this.tools.value = this.tools.value.filterNot { row -> tools.any { it.id == row.id } } + tools }
        override suspend fun deleteTools(serverId: String) { tools.value = tools.value.filterNot { it.serverId == serverId } }
        override suspend fun setToolEnabled(id: String, enabled: Boolean) { tools.value = tools.value.map { if (it.id == id) it.copy(enabled = enabled) else it } }
    }
}
