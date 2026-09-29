package com.halo.runtime.cognition.turn

import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.Role
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentContinuationTest {
    @Test
    fun discoveryContinuationRetainsGoalCallResultAndExpandedSchema() {
        val original = AIMessage(Role.USER, "set an alarm for 7")
        val discoveryCall = ToolCall("call-1", "halo_discover_tools", mapOf("query" to "alarm"))
        val response = AiResponse(
            text = "",
            toolCalls = listOf(discoveryCall),
            providerId = "fake",
        )
        val discoveryResult = AIMessage(
            role = Role.TOOL,
            content = "status=discovered\nhalo_exposure_add=halo_set_alarm",
            toolCallId = "call-1",
        )
        val alarm = ToolDefinition("halo_set_alarm", "halo_set_alarm", "Set an alarm")

        val next = continueToolConversation(
            AiContext.EMPTY.copy(conversation = listOf(original)),
            response,
            listOf(discoveryResult),
        ).copy(availableTools = listOf(alarm))

        assertEquals(original, next.conversation[0])
        assertEquals(discoveryCall, next.conversation[1].toolCalls.single())
        assertEquals(discoveryResult, next.conversation[2])
        assertTrue(next.availableTools.any { it.name == "halo_set_alarm" })
    }
}
