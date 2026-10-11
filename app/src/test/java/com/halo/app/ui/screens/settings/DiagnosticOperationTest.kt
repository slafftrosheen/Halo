package com.halo.app.ui.screens.settings

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticOperationTest {
    @Test fun cancellationRetainsOwnershipUntilCleanupFinishes() = runTest {
        val events = mutableListOf<String>()
        val operation = DiagnosticOperation(this, { events += "start" }, { events += "cancel" },
            { events += "error" }, { events += "finish" }, StandardTestDispatcher(testScheduler))
        operation.start {
            try { awaitCancellation() }
            finally { withContext(NonCancellable) { delay(100); events += "cleanup" } }
        }
        runCurrent()
        operation.cancel()
        runCurrent()
        operation.start { events += "overlap" }
        advanceUntilIdle()
        assertEquals(listOf("start", "cleanup", "cancel", "finish"), events)
        operation.start { events += "next" }
        advanceUntilIdle()
        assertEquals(listOf("start", "next", "finish"), events.takeLast(3))
    }
    @Test fun failureAndSuccessBothReleaseBusyState() = runTest {
        var busy = false
        var error: Exception? = null
        val operation = DiagnosticOperation(this, { busy = true }, {}, { error = it },
            { busy = false }, StandardTestDispatcher(testScheduler))
        operation.start { throw IllegalArgumentException("bad sample") }
        assertTrue(busy)
        operation.start { fail("Concurrent request entered") }
        advanceUntilIdle()
        assertFalse(busy)
        assertEquals("bad sample", error?.message)
        var completed = false
        operation.start { completed = true }
        advanceUntilIdle()
        assertTrue(completed)
        assertFalse(busy)
    }
}
