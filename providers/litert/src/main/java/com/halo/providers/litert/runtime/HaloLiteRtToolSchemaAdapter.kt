package com.halo.providers.litert.runtime

import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import org.json.JSONObject

/**
 * Translates Halo's provider-neutral [ToolDefinition] into LiteRT-LM compatible tool declarations.
 *
 * CRITICAL SECURITY INVARIANT:
 * `automaticToolCalling` is ALWAYS set to `false`. LiteRT must NEVER execute tools internally.
 * Tool execution MUST pass through ProviderTurnExecutor and ToolTurnExecutor.
 */
object HaloLiteRtToolSchemaAdapter {

    fun formatToolDefinitionsAsJsonSchema(tools: List<ToolDefinition>): String {
        val toolsArray = tools.map { tool ->
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", tool.id)
                    put("description", tool.description)
                    if (tool.parameters.isNotEmpty()) {
                        put("parameters", JSONObject(tool.parameters))
                    } else {
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject())
                        })
                    }
                })
            }
        }
        return JSONObject().put("tools", toolsArray).toString()
    }

    fun parseToolCallsFromResponseText(
        text: String,
        requestId: String,
    ): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        val regex = Regex("<halo_tool_call>(.*?)</halo_tool_call>", RegexOption.DOT_MATCHES_ALL)
        var ordinal = 0
        regex.findAll(text).forEach { match ->
            val jsonStr = match.groupValues[1].trim()
            runCatching {
                val json = JSONObject(jsonStr)
                val name = json.optString("name").ifBlank { json.optString("functionName") }
                val argsObj = json.optJSONObject("arguments") ?: JSONObject()
                val args = mutableMapOf<String, Any>()
                argsObj.keys().forEach { key -> args[key] = argsObj.get(key) }
                if (name.isNotBlank()) {
                    ordinal++
                    calls.add(
                        ToolCall(
                            id = "$requestId:litert:$ordinal",
                            functionName = name,
                            arguments = args
                        )
                    )
                }
            }
        }
        return calls
    }
}
