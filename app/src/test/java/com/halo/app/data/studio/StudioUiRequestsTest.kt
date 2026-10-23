package com.halo.app.data.studio

import com.halo.sdk.provider.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StudioUiRequestsTest {
    @Test fun claimIsSingleUseAndPreservesOriginalPrivacyAuthority() = runTest {
        var launches = 0
        val requests = StudioUiRequests(backgroundScope, { launches++ }, { testScheduler.currentTime * 1_000_000 })
        val origin = RemoteDispatchProvenance(setOf(RemoteDataCategory.SCREEN, RemoteDataCategory.MEMORIES), 17)
        val request = requests.offer("project", StudioUiRequestKind.AI_REVIEW, 3, "Edit this", origin)
        assertEquals(1, launches)
        assertTrue(runCatching { requests.offer("other", StudioUiRequestKind.OPEN) }.isFailure)
        assertEquals(origin, requests.claim(request.id)?.provenance)
        assertNull(requests.pending.value)
        assertNull(requests.claim(request.id))
        requests.mark(request.id, StudioUiRequestPhase.KEPT, "copy")
        requests.mark(request.id, StudioUiRequestPhase.DISCARDED)
        assertEquals(StudioUiRequestPhase.KEPT, requests.receipt(request.id)?.phase)
        assertEquals("copy", requests.receipt(request.id)?.resultProjectId)
    }
    @Test fun expiryRemovesUndeliveredPromptAndDoesNotReplayIt() = runTest {
        val requests = StudioUiRequests(backgroundScope, {}, { testScheduler.currentTime * 1_000_000 })
        val request = requests.offer("project", StudioUiRequestKind.AI_REVIEW, 1, "Private draft")
        advanceTimeBy(StudioUiRequests.TTL_MILLIS + 1); runCurrent()
        assertNull(requests.pending.value)
        assertNull(requests.claim(request.id))
        assertEquals(StudioUiRequestPhase.EXPIRED, requests.receipt(request.id)?.phase)
        assertNull(requests.receipt("missing"))
    }
    @Test fun launchFailureClearsPendingAndRetainsOnlyBoundedReceipts() = runTest {
        var failing = true
        val requests = StudioUiRequests(backgroundScope, { check(!failing) })
        assertTrue(runCatching { requests.offer("project", StudioUiRequestKind.OPEN) }.isFailure)
        assertNull(requests.pending.value)
        failing = false
        val first = requests.offer("project", StudioUiRequestKind.OPEN)
        requests.claim(first.id); requests.attached("project")
        assertEquals(StudioUiRequestPhase.ATTACHED, requests.receipt(first.id)?.phase)
        repeat(16) { val request = requests.offer("project", StudioUiRequestKind.OPEN); requests.claim(request.id) }
        assertNull(requests.receipt(first.id))
    }
}
