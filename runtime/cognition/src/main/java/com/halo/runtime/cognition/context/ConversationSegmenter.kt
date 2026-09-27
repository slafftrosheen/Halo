package com.halo.runtime.cognition.context

import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.Role

sealed interface ConversationSegment {
    val messages: List<AIMessage>

    data class Single(val message: AIMessage) : ConversationSegment {
        override val messages: List<AIMessage> get() = listOf(message)
    }

    data class ToolChain(
        val assistantMessage: AIMessage,
        val toolResultMessages: List<AIMessage>
    ) : ConversationSegment {
        override val messages: List<AIMessage> get() = listOf(assistantMessage) + toolResultMessages
    }
}

object ConversationSegmenter {

    fun segment(conversation: List<AIMessage>): List<ConversationSegment> {
        val segments = mutableListOf<ConversationSegment>()
        var i = 0
        while (i < conversation.size) {
            val msg = conversation[i]
            if (msg.role == Role.ASSISTANT && msg.toolCalls.isNotEmpty()) {
                val toolCalls = msg.toolCalls
                val toolCallIds = toolCalls.map { it.id }.toSet()
                val toolResults = mutableListOf<AIMessage>()

                var j = i + 1
                while (j < conversation.size) {
                    val nextMsg = conversation[j]
                    if (nextMsg.role == Role.TOOL && (nextMsg.toolCallId == null || nextMsg.toolCallId in toolCallIds)) {
                        toolResults.add(nextMsg)
                        j++
                    } else {
                        break
                    }
                }
                segments.add(ConversationSegment.ToolChain(msg, toolResults))
                i = j
            } else {
                segments.add(ConversationSegment.Single(msg))
                i++
            }
        }
        return segments
    }
}
