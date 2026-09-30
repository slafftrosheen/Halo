package com.halo.app.mcp

import com.halo.app.data.mcp.McpAuthKind
import com.halo.app.data.mcp.McpServerEntity
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * MINING-P1-001 MCP client.
 *
 * Required protocol flow only: `initialize` → `notifications/initialized` → `tools/list` →
 * `tools/call`. Nothing is polled and nothing is cached beyond the caller's own state, so
 * disabling or removing a server makes its tools unavailable immediately.
 *
 * This is a capability source, not an inference host: Halo's OpenCode Companion provider is a
 * different concept and is not involved.
 */
class McpClient(
    private val transport: McpTransport,
    private val clientName: String = "Halo",
    private val clientVersion: String = "1.0",
    /** Resolves a `credentialRef` into an actual secret. Credentials never enter the database. */
    private val credentialResolver: suspend (String?) -> String? = { null },
) {

    private val requestIds = AtomicLong(1L)

    private fun nextId(): Long = requestIds.getAndIncrement()

    data class Session(
        val protocolVersion: String?,
        val serverName: String?,
        val serverVersion: String?,
        val instructions: String?,
        val sessionId: String?,
        val versionCompatible: Boolean,
    )

    /**
     * Performs the handshake. Returns the negotiated session; a version the client cannot speak is
     * reported through [Session.versionCompatible] instead of being silently accepted.
     */
    suspend fun initialize(server: McpServerEntity): Session {
        val token = bearerFor(server)
        val response = transport.post(
            endpoint = server.endpoint,
            body = McpProtocol.initializeRequest(nextId(), clientName, clientVersion).toString(),
            bearerToken = token,
            sessionId = null,
            protocolVersion = null,
        )
        ensureSuccess(response, McpStage.INITIALIZE)
        val result = McpJson.resultOf(parseObject(response.body), McpStage.INITIALIZE)
        val initialized = McpJson.parseInitialize(result)

        // Notification carries no id and expects 202 with an empty body.
        val notification = transport.post(
            endpoint = server.endpoint,
            body = McpProtocol.initializedNotification().toString(),
            bearerToken = token,
            sessionId = response.sessionId,
            protocolVersion = initialized.protocolVersion,
        )
        if (!notification.isSuccess && !notification.isAcceptedNotification) {
            ensureSuccess(notification, McpStage.INITIALIZE)
        }

        return Session(
            protocolVersion = initialized.protocolVersion,
            serverName = initialized.serverName,
            serverVersion = initialized.serverVersion,
            instructions = initialized.instructions,
            sessionId = response.sessionId,
            versionCompatible = McpProtocol.negotiate(initialized.protocolVersion),
        )
    }

    /** Bounded pagination: [McpProtocol.MAX_TOOLS] and a cursor loop that cannot run forever. */
    suspend fun listTools(server: McpServerEntity, session: Session): List<McpRemoteTool> {
        val token = bearerFor(server)
        val collected = LinkedHashMap<String, McpRemoteTool>()
        var cursor: String? = null
        var pages = 0
        while (pages < MAX_PAGES && collected.size < McpProtocol.MAX_TOOLS) {
            pages++
            val response = transport.post(
                endpoint = server.endpoint,
                body = McpProtocol.toolsListRequest(nextId(), cursor).toString(),
                bearerToken = token,
                sessionId = session.sessionId,
                protocolVersion = session.protocolVersion,
            )
            ensureSuccess(response, McpStage.TOOLS_LIST)
            val result = McpJson.resultOf(parseObject(response.body), McpStage.TOOLS_LIST)
            val page = McpJson.parseTools(result)
            page.tools.forEach { tool -> collected.putIfAbsent(tool.name, tool) }
            cursor = page.nextCursor
            if (cursor == null) break
        }
        return collected.values.toList()
    }

    suspend fun callTool(
        server: McpServerEntity,
        session: Session,
        remoteToolName: String,
        arguments: JSONObject,
    ): McpCallOutcome {
        val response = transport.post(
            endpoint = server.endpoint,
            body = McpProtocol.toolsCallRequest(nextId(), remoteToolName, arguments).toString(),
            bearerToken = bearerFor(server),
            sessionId = session.sessionId,
            protocolVersion = session.protocolVersion,
        )
        ensureSuccess(response, McpStage.TOOLS_CALL)
        val result = McpJson.resultOf(parseObject(response.body), McpStage.TOOLS_CALL)
        return McpJson.parseCallResult(result)
    }

    private suspend fun bearerFor(server: McpServerEntity): String? {
        if (server.authKind != McpAuthKind.BEARER.name) return null
        val ref = server.credentialRef ?: throw McpProtocolException(
            McpStage.TRANSPORT, null, "Bearer credential is not configured")
        val token = try {
            credentialResolver(ref)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A missing credential is a configuration truth, not a crash.
            null
        }
        return token?.takeIf { it.isNotBlank() } ?: throw McpProtocolException(
            McpStage.TRANSPORT, null, "Bearer credential is unavailable")
    }

    private fun parseObject(body: String): JSONObject {
        if (body.isBlank()) {
            throw McpProtocolException(McpStage.PARSE, null, "Empty response body")
        }
        return try {
            JSONObject(body)
        } catch (_: Exception) {
            throw McpProtocolException(McpStage.PARSE, null, "Response was not JSON")
        }
    }

    private fun ensureSuccess(response: McpHttpResponse, stage: McpStage) {
        if (response.isSuccess) return
        throw McpProtocolException(
            stage = stage,
            code = response.status,
            message = "Server responded with HTTP ${response.status}",
        )
    }

    private companion object {
        const val MAX_PAGES = 5
    }
}
