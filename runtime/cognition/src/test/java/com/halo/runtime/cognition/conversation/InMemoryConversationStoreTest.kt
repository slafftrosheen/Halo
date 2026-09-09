package com.halo.runtime.cognition.conversation

import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.Role
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryConversationStoreTest {

    @Test
    fun `store is initially empty`() = runTest {
        val store = InMemoryConversationStore()
        assertTrue(store.turns.value.isEmpty())
        assertTrue(store.recentMessages(10).isEmpty())
    }

    @Test
    fun `append creates ordered user assistant pair`() = runTest {
        val store = InMemoryConversationStore()

        val turn = store.append(
            userText = "What is the capital of France?",
            assistantText = "Paris."
        )

        assertEquals("What is the capital of France?", turn.userText)
        assertEquals("Paris.", turn.assistantText)
        assertEquals(1, store.turns.value.size)

        val messages = store.recentMessages(10)
        assertEquals(2, messages.size)
        assertEquals(AIMessage(Role.USER, "What is the capital of France?"), messages[0])
        assertEquals(AIMessage(Role.ASSISTANT, "Paris."), messages[1])
    }

    @Test
    fun `maxTurns limits recentMessages output`() = runTest {
        val store = InMemoryConversationStore()

        store.append("Question 1", "Answer 1")
        store.append("Question 2", "Answer 2")
        store.append("Question 3", "Answer 3")

        val messages = store.recentMessages(maxTurns = 2)
        assertEquals(4, messages.size)
        assertEquals(AIMessage(Role.USER, "Question 2"), messages[0])
        assertEquals(AIMessage(Role.ASSISTANT, "Answer 2"), messages[1])
        assertEquals(AIMessage(Role.USER, "Question 3"), messages[2])
        assertEquals(AIMessage(Role.ASSISTANT, "Answer 3"), messages[3])
    }

    @Test
    fun `clear removes all turns`() = runTest {
        val store = InMemoryConversationStore()

        store.append("Question 1", "Answer 1")
        assertEquals(1, store.turns.value.size)

        store.clear()
        assertTrue(store.turns.value.isEmpty())
        assertTrue(store.recentMessages(10).isEmpty())
    }
}
