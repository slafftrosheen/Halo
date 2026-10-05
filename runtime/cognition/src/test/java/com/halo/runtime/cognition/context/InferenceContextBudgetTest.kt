package com.halo.runtime.cognition.context

import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.conversation.InMemoryConversationStore
import com.halo.sdk.provider.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class InferenceContextBudgetTest {
    @Test fun projectionRetainsNewestPairsAndNeverMutatesDurableHistory() = runTest {
        val store = InMemoryConversationStore()
        repeat(40) { store.append("Question $it " + "q".repeat(60), "Answer $it " + "a".repeat(60)) }
        val durable = store.turns.value.toList()
        val assembler = DefaultContextAssembler(store, DefaultBlackboard())
        val estimator = ConservativeContextTokenEstimator()
        for (budget in listOf(256, 512, 1024)) {
            val context = assembler.assemble(AiContextPolicy(includeConversation = true), setOf(AiCapability.TEXT),
                ContextAssemblyRequest(userText = "Current request", performanceBudget = InferenceContextBudget(budget))).context
            assertEquals(durable, store.turns.value)
            assertTrue(context.conversation.sumOf { estimator.estimate(it.content) } <= budget)
            assertEquals(0, context.conversation.size % 2)
            assertTrue(context.conversation.last().content.startsWith("Answer 39"))
            context.conversation.chunked(2).forEach { pair ->
                assertEquals(Role.USER, pair[0].role)
                assertEquals(Role.ASSISTANT, pair[1].role)
            }
        }
    }
    @Test fun turnLimitAlsoKeepsCompletePairsWithoutInvokingSummarizer() = runTest {
        val store = InMemoryConversationStore()
        repeat(10) { store.append("user $it", "answer $it") }
        val compactor = ContextCompactor(ContextSummarizer { _, _, _ -> error("Interactive projection must be deterministic") })
        val projection = compactor.projectForPerformance(store.turns.value, InferenceContextBudget(512, 2), 4096)
        assertEquals(listOf("user 8", "answer 8", "user 9", "answer 9"), projection.messages.map { it.content })
    }
    @Test fun requiredWorkingSetOutranksOldChatAndIsOutsideHistoryBudget() = runTest {
        val store = InMemoryConversationStore()
        repeat(30) { store.append("old $it " + "u".repeat(200), "a".repeat(200)) }
        val reference = "required working set " + "r".repeat(300)
        val candidate = ContextCandidate(ContextCandidateId("working"), ContextSource.WorkingSet, ContextContent.Text(reference),
            ContextTrust.USER_AUTHORED, 1f, ContextPriority.CRITICAL, 110, ContextFreshness.RECENT)
        val result = DefaultContextAssembler(store, DefaultBlackboard()).assemble(AiContextPolicy(includeConversation = true, includeWorkspace = true),
            setOf(AiCapability.TEXT), ContextAssemblyRequest(userText = "current", contextWindowTokens = 4096,
                performanceBudget = InferenceContextBudget(256), acquisition = ContextAcquisitionResult(candidates = listOf(candidate))))
        assertTrue(result.context.conversation.any { reference in it.content })
        assertEquals(30, store.turns.value.size)
    }

    @Test fun newestSuffixStopsAtFirstNonFittingPairAndCountsOriginalOmissions() = runTest {
        val store = InMemoryConversationStore()
        repeat(9) { store.append("user $it", "answer $it") }
        store.append("newest".repeat(100), "answer".repeat(100))
        val compactor = ContextCompactor()
        assertTrue(compactor.projectForPerformance(store.turns.value, InferenceContextBudget(100), 4096).messages.isEmpty())
        val small = store.turns.value.dropLast(1)
        val two = compactor.projectForPerformance(small, InferenceContextBudget(60), 4096)
        assertEquals(listOf("user 7", "answer 7", "user 8", "answer 8"), two.messages.map { it.content })
        store.append("last", "answer")
        val capped = compactor.projectForPerformance(store.turns.value.take(10), InferenceContextBudget(4096, 4), 4096)
        assertEquals(6, capped.omittedTurns)
    }
    @Test fun provisionalProfileIsLimitedToSmallLocalModels() {
        val voice = localInferencePerformance(true, 4096, true)!!
        assertEquals(512, voice.history.maxConversationTokens)
        assertEquals(128, voice.outputTokens)
        val text = localInferencePerformance(true, 4096, false)!!
        assertEquals(1024, text.history.maxConversationTokens)
        assertEquals(256, text.outputTokens)
        assertNull(localInferencePerformance(false, 4096, true))
        assertNull(localInferencePerformance(true, 8192, false))
        assertNull(localInferencePerformance(true, null, false))
    }

}
