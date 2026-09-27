package com.halo.providers.litert.runtime

import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.Role
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.asUntrustedPromptSection

data class LiteRtMappedMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
)

data class LiteRtConversationInput(
    val systemInstruction: String?,
    val initialMessages: List<LiteRtMappedMessage>,
    val outgoingMessage: LiteRtMappedMessage?,
)

object LiteRtConversationMapper {

    fun map(request: AiRequest, context: AiContext): LiteRtConversationInput {
        var systemInstruction: String? = null
        val initialMessages = mutableListOf<LiteRtMappedMessage>()

        for (msg in context.conversation) {
            when (msg.role) {
                Role.SYSTEM -> {
                    systemInstruction = if (systemInstruction == null) {
                        msg.content
                    } else {
                        "$systemInstruction\n${msg.content}"
                    }
                }
                Role.USER -> {
                    initialMessages.add(LiteRtMappedMessage("user", msg.content))
                }
                Role.ASSISTANT -> {
                    initialMessages.add(LiteRtMappedMessage("model", msg.content, msg.toolCalls, msg.toolCallId))
                }
                Role.TOOL -> {
                    initialMessages.add(LiteRtMappedMessage("tool", msg.content, toolCallId = msg.toolCallId))
                }
            }
        }

        context.screenContext?.let { screen ->
            val screenText = screen.asUntrustedPromptSection()
            systemInstruction = if (systemInstruction == null) {
                screenText
            } else {
                "$systemInstruction\n\n$screenText"
            }
        }

        val outgoingMessage = if (request.appendTextAsUserMessage && !request.text.isNullOrBlank()) {
            LiteRtMappedMessage("user", request.text!!)
        } else if (initialMessages.isNotEmpty()) {
            null
        } else {
            LiteRtMappedMessage("user", request.text ?: "")
        }

        return LiteRtConversationInput(
            systemInstruction = systemInstruction,
            initialMessages = initialMessages,
            outgoingMessage = outgoingMessage
        )
    }
}
