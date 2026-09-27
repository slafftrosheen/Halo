package com.halo.providers.litert.tools

import com.halo.sdk.provider.ToolDefinition

/**
 * OpenApiTool wrapper for LiteRT.
 *
 * ABSOLUTE SECURITY RULE:
 * automaticToolCalling = false in ConversationConfig.
 * If execute(...) is ever invoked by LiteRT, it fails loudly to prevent silent execution bypass.
 */
class HaloOpenApiTool(
    val definition: ToolDefinition,
) {
    fun getToolDescriptionJsonString(): String {
        return LiteRtToolSchemaAdapter.formatToolDefinitionToJsonSchema(definition)
    }

    fun execute(paramsJsonString: String): String {
        error(
            "Halo tools must never execute inside LiteRT. " +
            "automaticToolCalling must remain false."
        )
    }
}
