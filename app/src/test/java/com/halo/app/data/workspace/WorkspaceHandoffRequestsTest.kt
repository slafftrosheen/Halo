package com.halo.app.data.workspace

import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkspaceHandoffRequestsTest {
    @Test fun aRequestIsClaimedOnceAndExpiryNeverReplaysIt() = runTest {
        var launches = 0
        val requests = WorkspaceHandoffRequests(this) { launches++ }
        val request = requests.offer("source", "1", WorkspaceDestination.CHAT)
        assertTrue(runCatching { requests.offer("other", "2", WorkspaceDestination.OPEN) }.isFailure)
        assertTrue(requests.claim(request))
        assertFalse(requests.claim(request))
        requests.finish(request, true)
        advanceTimeBy(180001); runCurrent()
        assertEquals(WorkspaceHandoffPhase.DELIVERED_TO_HOST, requests.phase(request.id))
        assertEquals(1, launches)
        assertNull(requests.pending.value)
    }
    @Test fun abandonedAndFailedDispatchesAreTerminalAndCanBeReplacedExplicitly() = runTest {
        val expired = WorkspaceHandoffRequests(this, {})
        val request = expired.offer("source", "1", WorkspaceDestination.STUDY)
        advanceTimeBy(180001); runCurrent()
        assertEquals(WorkspaceHandoffPhase.EXPIRED, expired.phase(request.id))
        assertFalse(expired.claim(request))
        val replacement = expired.offer("source", "2", WorkspaceDestination.OPEN)
        assertNotEquals(request.id, replacement.id)
        assertTrue(expired.claim(replacement))
        expired.finish(replacement, false)
        assertEquals(WorkspaceHandoffPhase.REJECTED, expired.phase(replacement.id))
        val failed = WorkspaceHandoffRequests(this) { error("No host") }
        assertTrue(runCatching { failed.offer("source", "1", WorkspaceDestination.OPEN) }.isFailure)
        assertNull(failed.pending.value)
    }
}
