package com.halo.sdk.provider

import com.halo.sdk.runtime.DeviceState
import com.halo.sdk.runtime.HaloEvent
import com.halo.sdk.runtime.Memory

/**
 * Definition of a tool/capability available to the AI.
 */
enum class ToolRisk {
    SAFE,
    CONFIRMATION_REQUIRED,
    DEVICE_CONTROL,
    DANGEROUS
}

data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val parameters: Map<String, Any> = emptyMap(),
    val risk: ToolRisk = ToolRisk.SAFE
)

/**
 * Full cognitive context for an AI generation request.
 */
data class AiContext(
    val conversation: List<AIMessage>,
    val deviceState: DeviceState?,
    val recentEvents: List<HaloEvent>,
    val memories: List<Memory>,
    val availableTools: List<ToolDefinition>
) {
    companion object {
        val EMPTY = AiContext(
            conversation = emptyList(),
            deviceState = null,
            recentEvents = emptyList(),
            memories = emptyList(),
            availableTools = emptyList()
        )
    }
}

/** Compatibility alias */
typealias ConversationMessage = AIMessage
