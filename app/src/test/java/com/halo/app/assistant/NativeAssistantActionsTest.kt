package com.halo.app.assistant

import com.halo.host.android.assistant.NativeAssistantActions
import com.halo.host.android.assistant.NativeAppActionSource
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NativeAssistantActionsTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun nativeActionsAreBoundedOpaqueFreshAndSingleUse() = runTest {
        var time = 1000L
        var dispatched: Int? = null
        val actions = NativeAssistantActions { time }
        actions.attach("invocation", object : NativeAppActionSource {
            override suspend fun list() = (0..20).map { "action-$it" }
            override fun dispatch(index: Int) { dispatched = index }
        })
        val discovered = actions.list()
        assertEquals(12, discovered.size)
        assertNotEquals("action-1", discovered[1].token)
        actions.dispatch(discovered[1].token)
        assertEquals(1, dispatched)
        assertTrue(runCatching { actions.dispatch(discovered[1].token) }.isFailure)
        val refreshed = actions.list()
        time += 30_001
        assertTrue(runCatching { actions.dispatch(refreshed[0].token) }.isFailure)
    }
    @Test fun aLateDiscoveryCannotRestoreActionsFromAClosedSession() = runTest {
        val reply = CompletableDeferred<List<String>>()
        val actions = NativeAssistantActions()
        actions.attach("old", object : NativeAppActionSource {
            override suspend fun list() = reply.await()
            override fun dispatch(index: Int) { fail("Closed session must not dispatch") }
        })
        val discovery = async { runCatching { actions.list() } }
        runCurrent()
        actions.detach("old")
        reply.complete(listOf("send"))
        assertTrue(discovery.await().isFailure)
        assertTrue(runCatching { actions.dispatch("send") }.isFailure)
    }
    @Test fun invalidationAndAnotherInvocationRevokeTheOldTokens() = runTest {
        val source = object : NativeAppActionSource {
            override suspend fun list() = listOf("open")
            override fun dispatch(index: Int) { fail("Old token must not dispatch") }
        }
        val actions = NativeAssistantActions()
        actions.attach("first", source)
        val old = actions.list().single()
        actions.invalidate()
        assertTrue(runCatching { actions.dispatch(old.token) }.isFailure)
        val next = actions.list().single()
        actions.attach("second", source)
        actions.detach("first") // A late old-session callback must not close the new session.
        assertEquals(1, actions.list().size)
        assertTrue(runCatching { actions.dispatch(next.token) }.isFailure)
    }
}
