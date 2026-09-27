package com.halo.providers.litert.tools

import com.halo.sdk.provider.ToolDefinition
import org.json.JSONObject

object LiteRtToolSchemaAdapter {

    fun formatToolDefinitionToJsonSchema(tool: ToolDefinition): String {
        val json = JSONObject().apply {
            // LiteRT's OpenApiTool adapter reads the function name directly from
            // the top-level object; this is intentionally not the OpenAI wrapper.
            put("name", tool.id)
            put("description", tool.description)
            put("parameters", if (tool.parameters.isNotEmpty()) {
                JSONObject(tool.parameters)
            } else {
                JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                }
            })
        }
        return json.toString()
    }
}
