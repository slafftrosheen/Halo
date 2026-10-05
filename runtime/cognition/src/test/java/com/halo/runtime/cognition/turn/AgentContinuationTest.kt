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

    @Test fun projectedHistoryCannotDropCurrentRequestOrSplitToolContinuation() = kotlinx.coroutines.test.runTest {
        val store = com.halo.runtime.cognition.conversation.InMemoryConversationStore()
        store.append("old history".repeat(100), "old answer".repeat(100))
        val projection = com.halo.runtime.cognition.context.ContextCompactor().projectForPerformance(store.turns.value,
            com.halo.runtime.cognition.context.InferenceContextBudget(0), 4096)
        assertTrue(projection.messages.isEmpty())
        val request = AIMessage(Role.USER, "current request independent of history")
        val call = ToolCall("atomic-call", "halo_echo", mapOf("text" to "value"))
        val receipt = AIMessage(Role.TOOL, "receipt", toolCallId = call.id)
        val next = continueToolConversation(AiContext.EMPTY.copy(conversation = projection.messages + request),
            AiResponse("", toolCalls = listOf(call), providerId = "fake"), listOf(receipt))
        assertEquals(request, next.conversation[0])
        assertEquals(call, next.conversation[1].toolCalls.single())
        assertEquals(receipt, next.conversation[2])
        assertEquals(1, store.turns.value.size)
    }
}
