package com.halo.providers.litert

import com.halo.providers.litert.runtime.LiteRtConversationMapper
import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.Role
import com.halo.sdk.provider.ProviderTurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LiteRtConversationMapperTest {

    @Test
    fun `maps system instructions and message history into structured input`() {
        val context = AiContext(
            conversation = listOf(
                AIMessage(Role.SYSTEM, "You are Halo, an AI assistant."),
                AIMessage(Role.USER, "What is the capital of France?"),
                AIMessage(Role.ASSISTANT, "Paris is the capital of France.")
            ),
            deviceState = null,
            recentEvents = emptyList(),
            memories = emptyList(),
            availableTools = emptyList()
        )

        val request = AiRequest(text = "What about Germany?", appendTextAsUserMessage = true)
        val result = LiteRtConversationMapper.map(request, context)

        assertEquals("You are Halo, an AI assistant.", result.systemInstruction)
        assertEquals(2, result.initialMessages.size)
        assertEquals("user", result.initialMessages[0].role)
        assertEquals("model", result.initialMessages[1].role)
        assertNotNull(result.outgoingMessage)
        assertEquals("What about Germany?", result.outgoingMessage?.content)
    }

    @Test
    fun `does not duplicate user turn when appendTextAsUserMessage is false`() {
        val context = AiContext(
            conversation = listOf(
                AIMessage(Role.USER, "Hello")
            ),
            deviceState = null,
            recentEvents = emptyList(),
            memories = emptyList(),
            availableTools = emptyList()
        )

        val request = AiRequest(text = "Hello", appendTextAsUserMessage = false)
        val result = LiteRtConversationMapper.map(request, context)

        assertEquals(1, result.initialMessages.size)
        assertNull(result.outgoingMessage)
    }

    @Test
    fun `tool continuation uses bounded internal trigger without replaying original user text`() {
        val context = AiContext(
            conversation = listOf(
                AIMessage(Role.USER, "set an alarm for 7"),
                AIMessage(Role.ASSISTANT, "", toolCallId = null),
                AIMessage(Role.TOOL, "Clock accepted alarm request", toolCallId = "call-1"),
            ),
            deviceState = null,
            recentEvents = emptyList(),
            memories = emptyList(),
            availableTools = emptyList(),
        )

        val result = LiteRtConversationMapper.map(
            AiRequest(
                text = "set an alarm for 7",
                appendTextAsUserMessage = false,
                turnKind = ProviderTurnKind.TOOL_CONTINUATION,
            ),
            context,
        )

        assertEquals(3, result.initialMessages.size)
        assertEquals(true, result.continuation)
        assertNotNull(result.outgoingMessage)
        assertEquals(false, result.outgoingMessage?.content?.contains("set an alarm for 7"))
    }
}
