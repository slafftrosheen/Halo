package com.halo.app.mcp

import org.json.JSONArray
import org.json.JSONObject

/**
 * MINING-P1-001 MCP protocol surface.
 *
 * Harvested conceptually from Brownie's `McpClient` / `McpRemoteTool` JSON-RPC mechanics. Scope is
 * deliberately TOOLS only: `tools/list` and `tools/call`. Resources, prompts and sampling are not
 * implemented merely because MCP defines them.
 *
 * No background polling: every network call here happens because a user or a model turn asked for it.
 */
object McpProtocol {
    /** Streamable HTTP transport revision Halo speaks. */
    const val PREFERRED_VERSION = "2025-06-18"

    /** Versions Halo can interoperate with, newest first. Negotiation is honest, not assumed. */
    val SUPPORTED_VERSIONS = listOf("2025-06-18", "2025-03-26", "2024-11-05")

    const val METHOD_INITIALIZE = "initialize"
    const val METHOD_INITIALIZED = "notifications/initialized"
    const val METHOD_TOOLS_LIST = "tools/list"
    const val METHOD_TOOLS_CALL = "tools/call"

    /** Bounded so a hostile or broken server cannot exhaust memory. */
    const val MAX_TOOLS = 200
    const val MAX_SCHEMA_CHARS = 16_000
    const val MAX_DESCRIPTION_CHARS = 2_000
    const val MAX_RESULT_CHARS = 8_000

    fun initializeRequest(id: Long, clientName: String, clientVersion: String): JSONObject = JSONObject()
        .put("jsonrpc", "2.0")
        .put("id", id)
        .put("method", METHOD_INITIALIZE)
        .put(
            "params",
            JSONObject()
                .put("protocolVersion", PREFERRED_VERSION)
                .put(
                    "capabilities",
                    // Halo declares no client capabilities it does not implement.
                    JSONObject(),
                )
                .put("clientInfo", JSONObject().put("name", clientName).put("version", clientVersion)),
        )

    fun initializedNotification(): JSONObject = JSONObject()
        .put("jsonrpc", "2.0")
        .put("method", METHOD_INITIALIZED)
        .put("params", JSONObject())

    fun toolsListRequest(id: Long, cursor: String? = null): JSONObject {
        val params = JSONObject()
        cursor?.takeIf { it.isNotBlank() }?.let { params.put("cursor", it) }
        return JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", METHOD_TOOLS_LIST).put("params", params)
    }

    fun toolsCallRequest(id: Long, remoteToolName: String, arguments: JSONObject): JSONObject = JSONObject()
        .put("jsonrpc", "2.0")
        .put("id", id)
        .put("method", METHOD_TOOLS_CALL)
        .put("params", JSONObject().put("name", remoteToolName).put("arguments", arguments))

    /**
     * Version negotiation. A server that answers with a version Halo does not speak is not silently
     * accepted: the caller records it and refuses to pretend compatibility.
     */
    fun negotiate(serverVersion: String?): Boolean = serverVersion != null && serverVersion in SUPPORTED_VERSIONS
}

data class McpInitializeResult(
    val protocolVersion: String?,
    val serverName: String?,
    val serverVersion: String?,
    val instructions: String?,
)

data class McpRemoteTool(
    val name: String,
    val description: String,
    /** Raw JSON schema as published by the server. Never trusted as Halo policy. */
    val inputSchemaJson: String,
)

data class McpToolsPage(
    val tools: List<McpRemoteTool>,
    val nextCursor: String?,
)

/** Result of one remote tool call. Text is untrusted external content. */
data class McpCallOutcome(
    val text: String,
    val isError: Boolean,
    val contentBlocks: Int,
    val truncated: Boolean = false,
)

class McpProtocolException(
    val stage: McpStage,
    val code: Int?,
    /** Bounded, content-free summary. */
    message: String,
    val completionUnknown: Boolean = false,
) : Exception(message)

enum class McpStage {
    TRANSPORT,
    INITIALIZE,
    TOOLS_LIST,
    TOOLS_CALL,
    PARSE,
}

/** Parsing helpers shared by the client and its tests. */
object McpJson {
    /**
     * Extracts the JSON-RPC result from one message. Throws [McpProtocolException] with a bounded
     * message — never echoing the server's payload text back to the model or the log.
     */
    fun resultOf(message: JSONObject, stage: McpStage, expectedId: Long? = null): JSONObject {
        if(expectedId != null && (message.optString("jsonrpc") != "2.0" ||
            message.opt("id") !is Number || message.opt("id")?.toString() != expectedId.toString()))
            throw McpProtocolException(stage,null,"Response did not match the request")
        if(message.has("result") == message.has("error"))
            throw McpProtocolException(stage,null,"Response must contain exactly one result or error")
        val error = message.optJSONObject("error")
        if (error != null) {
            throw McpProtocolException(
                stage = stage,
                code = error.optInt("code").takeIf { error.has("code") },
                message = "Server returned JSON-RPC error ${error.optInt("code")}",
            )
        }
        return message.optJSONObject("result")
            ?: throw McpProtocolException(stage, null, "Response contained no result object")
    }

    fun parseInitialize(result: JSONObject): McpInitializeResult = McpInitializeResult(
        protocolVersion = result.optString("protocolVersion").ifBlank { null },
        serverName = result.optJSONObject("serverInfo")?.optString("name")?.ifBlank { null },
        serverVersion = result.optJSONObject("serverInfo")?.optString("version")?.ifBlank { null },
        instructions = result.optString("instructions").take(McpProtocol.MAX_DESCRIPTION_CHARS).ifBlank { null },
    )

    fun parseTools(result: JSONObject): McpToolsPage {
        val array = result.optJSONArray("tools") ?: throw McpProtocolException(McpStage.TOOLS_LIST,null,"Tool listing is missing")
        if(array.length() > McpProtocol.MAX_TOOLS) throw McpProtocolException(McpStage.TOOLS_LIST,null,"Tool listing exceeds limits")
        val tools = buildList {
            val count = minOf(array.length(), McpProtocol.MAX_TOOLS)
            for (index in 0 until count) {
                val item = array.optJSONObject(index) ?: throw McpProtocolException(McpStage.TOOLS_LIST,null,"Invalid tool definition")
                val name = item.optString("name").trim()
                if (name.isEmpty() || name.length > 128) throw McpProtocolException(McpStage.TOOLS_LIST,null,"Invalid tool name")
                val schema=item.optJSONObject("inputSchema")?.toString()
                    ?: throw McpProtocolException(McpStage.TOOLS_LIST,null,"Tool input schema is missing")
                if(schema.length > McpProtocol.MAX_SCHEMA_CHARS) throw McpProtocolException(McpStage.TOOLS_LIST,null,"Tool input schema exceeds limits")
                add(
                    McpRemoteTool(
                        name = name,
                        description = item.optString("description").take(McpProtocol.MAX_DESCRIPTION_CHARS),
                        inputSchemaJson = schema,
                    )
                )
            }
        }
        val cursor = result.optString("nextCursor").ifBlank { null }
        return McpToolsPage(tools = tools, nextCursor = cursor)
    }

    /**
     * Flattens MCP content blocks into model-visible text. Only `type = text` blocks contribute
     * text; unknown block types are reported structurally so a server cannot smuggle instructions
     * through an unexpected shape.
     */
    fun parseCallResult(result: JSONObject): McpCallOutcome {
        val isError = result.optBoolean("isError", false)
        val array = result.optJSONArray("content")
        if (array == null) {
            val fallback = result.optJSONObject("structuredContent")?.toString() ?: ""
            return outcome(fallback,isError,0,fallback.length > McpProtocol.MAX_RESULT_CHARS)
        }
        val builder = StringBuilder()
        var blocks = 0
        for (index in 0 until array.length()) {
            val block = array.optJSONObject(index) ?: continue
            blocks++
            when (block.optString("type")) {
                "text" -> {
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append(block.optString("text").take(McpProtocol.MAX_RESULT_CHARS + 1))
                }
                else -> {
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append("[${block.optString("type").take(80).ifBlank { "non_text" }} content omitted]")
                }
            }
            if (builder.length > McpProtocol.MAX_RESULT_CHARS) break
        }
        return outcome(builder.toString(),isError,array.length(),builder.length > McpProtocol.MAX_RESULT_CHARS || blocks < array.length())
    }
    private fun outcome(text: String,isError: Boolean,blocks: Int,truncated: Boolean): McpCallOutcome {
        val notice="\n[Output truncated; remaining content was not supplied to Halo.]"
        val limit=McpProtocol.MAX_RESULT_CHARS - if(truncated) notice.length else 0
        var end=minOf(text.length,limit)
        if(end > 0 && end < text.length && text[end-1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        return McpCallOutcome(text.substring(0,end) + if(truncated) notice else "",isError,blocks,truncated)
    }
}
