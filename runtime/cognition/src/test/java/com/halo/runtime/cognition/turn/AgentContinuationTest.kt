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
    @Test fun delegatedReferencesAndRecoveredChildResultsRetainConservativePrivateSourceProvenance() {
        val builder = com.halo.runtime.cognition.delegation.DelegatedContextBuilder { _, _ -> null }
        val privateCategories = setOf(com.halo.sdk.provider.RemoteDataCategory.WORKSPACE,
            com.halo.sdk.provider.RemoteDataCategory.SCREEN, com.halo.sdk.provider.RemoteDataCategory.MEMORIES)
        val segment = com.halo.runtime.cognition.context.ContextSegment("fixture",
            com.halo.runtime.cognition.context.ContextSegmentKind.WORKING_SET, 1, 1,
            com.halo.runtime.cognition.context.SegmentContent.InlineText("synthetic"),
            com.halo.runtime.cognition.context.ContextProvenance("fixture", com.halo.runtime.cognition.context.ContextTrust.USER_AUTHORED, false))
        val child = builder.build(listOf(com.halo.runtime.cognition.delegation.ResolvedDelegatedInput(segment, "synthetic")), false, emptyList())
        assertEquals(privateCategories, child.remoteDataCategories)
        assertTrue(builder.build(listOf(com.halo.runtime.cognition.delegation.ResolvedDelegatedInput(segment, "synthetic")), true, emptyList()).conversation.isEmpty())
        for (tool in listOf("halo_delegate_task", "halo_research"))
            assertTrue(com.halo.runtime.cognition.tools.remoteToolResultCategories(tool).containsAll(privateCategories))
    }

    @Test fun historicalPendingToolHasUnknownProvenanceAndCannotAcquireNewRemoteAuthority() {
        val step = com.halo.runtime.cognition.agent.AgentStep.ToolInvocationPrepared(
            com.halo.runtime.cognition.agent.AgentStepId("legacy"), com.halo.runtime.cognition.agent.AgentRunId("run"),
            java.time.Instant.now(), com.halo.runtime.cognition.agent.ToolInvocationId("invocation"), "call", "halo_fetch_url",
            "{}", "digest", "key", 1, "SAFE")
        val provenance = requireNotNull(step.remoteProvenance())
        assertEquals(-1L, provenance.revision)
        assertEquals(com.halo.sdk.provider.RemoteDataCategory.entries.toSet(), provenance.categories)
    }

    @Test fun toolContinuationCarriesWorkspaceMemoryAndScreenProvenance() {
        for ((name, category) in listOf("halo_get_workspace_object" to com.halo.sdk.provider.RemoteDataCategory.WORKSPACE,
            "halo_search_memory" to com.halo.sdk.provider.RemoteDataCategory.MEMORIES,
            "halo_read_screen" to com.halo.sdk.provider.RemoteDataCategory.SCREEN)) {
            val call = ToolCall("fixture", name, emptyMap())
            val next = continueToolConversation(AiContext.EMPTY,
                AiResponse("", toolCalls = listOf(call), providerId = "fixture"),
                listOf(AIMessage(Role.TOOL, "synthetic result", toolCallId = "fixture")))
            assertTrue(category in next.remoteDataCategories)
            assertTrue(com.halo.sdk.provider.RemoteDataCategory.TOOLS in next.remoteDataCategories)
        }
    }
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
