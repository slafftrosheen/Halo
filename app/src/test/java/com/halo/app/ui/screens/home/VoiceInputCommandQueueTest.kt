package com.halo.app.ui.screens.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VoiceInputCommandQueueTest {
    @Test fun muteDuringAutomaticContinuationDelayNeverStartsCapture() = runTest {
        val queue = VoiceInputCommandQueue()
        var captures = 0
        val ticket = queue.current()
        val continuation = async { delay(250); queue.runIfCurrent(ticket) { captures++ } }
        runCurrent()
        advanceTimeBy(100)
        queue.supersede()
        advanceTimeBy(200)
        assertFalse(continuation.await())
        assertEquals(0, captures)
    }

    @Test fun stoppedQueuedUnmuteCannotReactivateMicrophone() = runTest {
        val queue = VoiceInputCommandQueue()
        val release = CompletableDeferred<Unit>()
        val blocking = launch { queue.withLock { release.await() } }
        runCurrent()
        val effects = mutableListOf<String>()
        val start = queue.supersede()
        val pending = async { queue.runIfCurrent(start) { effects += "start" } }
        runCurrent()
        val stop = queue.supersede()
        val ending = async { queue.runIfCurrent(stop) { effects += "stop" } }
        release.complete(Unit)
        blocking.join()
        assertFalse(pending.await())
        assertTrue(ending.await())
        assertEquals(listOf("stop"), effects)
    }

    @Test fun oldStopCannotStopNewerExplicitCapture() = runTest {
        val queue = VoiceInputCommandQueue()
        val release = CompletableDeferred<Unit>()
        val blocking = launch { queue.withLock { release.await() } }
        runCurrent()
        val effects = mutableListOf<String>()
        val stop = queue.supersede()
        val old = async { queue.runIfCurrent(stop) { effects += "stop" } }
        runCurrent()
        val start = queue.supersede()
        val current = async { queue.runIfCurrent(start) { effects += "start" } }
        release.complete(Unit)
        blocking.join()
        assertFalse(old.await())
        assertTrue(current.await())
        assertEquals(listOf("start"), effects)
    }

    @Test fun permissionAndConversationAreReadAfterWaitingForCommandSerialization() = runTest {
        val queue = VoiceInputCommandQueue()
        val release = CompletableDeferred<Unit>()
        val blocking = launch { queue.withLock { release.await() } }
        runCurrent()
        var allowed = true
        var effects = 0
        val pending = async { queue.runIfCurrent(queue.current(), allowed = { allowed }) { effects++ } }
        runCurrent()
        allowed = false
        release.complete(Unit)
        blocking.join()
        assertFalse(pending.await())
        assertEquals(0, effects)
    }

    @Test fun leavingAndReturningToSameConversationCannotRestoreOldAuthority() = runTest {
        val queue = VoiceInputCommandQueue()
        val old = queue.current()
        queue.supersede()
        queue.supersede()
        assertFalse(queue.runIfCurrent(old) { fail("Old conversation command executed") })
        assertTrue(queue.runIfCurrent(queue.current()) {})
    }

    @Test fun currentStartDeniedAfterWaitReconcilesOptimisticListeningAndSessionState() = runTest {
        val queue = VoiceInputCommandQueue()
        val release = CompletableDeferred<Unit>()
        val blocking = launch { queue.withLock { release.await() } }
        runCurrent()
        var permission = true
        var listeningRequested = true
        var sessionActive = true
        val ticket = queue.current()
        val pending = async { queue.runIfCurrent(ticket, allowed = { permission }, onDenied = {
            listeningRequested = false
            sessionActive = false
            queue.supersede()
        }) { fail("Permission-revoked capture started") } }
        runCurrent()
        permission = false
        release.complete(Unit)
        blocking.join()
        assertFalse(pending.await())
        assertFalse(listeningRequested)
        assertFalse(sessionActive)
        assertFalse(queue.isCurrent(ticket))
    }

    @Test fun staleDenialNeverClearsNewerListeningOrSessionState() = runTest {
        val queue = VoiceInputCommandQueue()
        val release = CompletableDeferred<Unit>()
        val blocking = launch { queue.withLock { release.await() } }
        runCurrent()
        val old = queue.current()
        var listeningRequested = true
        var sessionActive = true
        val pending = async { queue.runIfCurrent(old, allowed = { false }, onDenied = {
            listeningRequested = false
            sessionActive = false
        }) { fail("Old start executed") } }
        runCurrent()
        queue.supersede()
        release.complete(Unit)
        blocking.join()
        assertFalse(pending.await())
        assertTrue(listeningRequested)
        assertTrue(sessionActive)
    }

    @Test fun cancellingQueuedCaptureReleasesWaitWithoutSideEffects() = runTest {
        val queue = VoiceInputCommandQueue()
        val release = CompletableDeferred<Unit>()
        val blocking = launch { queue.withLock { release.await() } }
        runCurrent()
        val pending = launch { queue.runIfCurrent(queue.current()) { fail("Cancelled command executed") } }
        runCurrent()
        pending.cancel()
        pending.join()
        release.complete(Unit)
        blocking.join()
        assertTrue(queue.runIfCurrent(queue.current()) {})
    }

    @Test fun failedCommandDoesNotPoisonLaterControl() = runTest {
        val queue = VoiceInputCommandQueue()
        var failed = false
        try { queue.runIfCurrent(queue.current()) { error("fixture") } }
        catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
        assertTrue(queue.runIfCurrent(queue.supersede()) {})
    }

    @Test fun cancellingSuspendedCommandPropagatesAndReleasesSerialization() = runTest {
        val queue = VoiceInputCommandQueue()
        val entered = CompletableDeferred<Unit>()
        val active = launch { queue.runIfCurrent(queue.current()) { entered.complete(Unit); delay(10_000) } }
        entered.await()
        active.cancel()
        active.join()
        assertTrue(active.isCancelled)
        assertTrue(queue.runIfCurrent(queue.supersede()) {})
    }

    @Test fun rapidControlChangesDispatchOnlyLatestQueuedIntent() = runTest {
        val queue = VoiceInputCommandQueue()
        val release = CompletableDeferred<Unit>()
        val blocking = launch { queue.withLock { release.await() } }
        runCurrent()
        val effects = mutableListOf<Int>()
        val pending = (0..19).map { index ->
            val ticket = queue.supersede()
            async { queue.runIfCurrent(ticket) { effects += index } }
        }
        runCurrent()
        release.complete(Unit)
        blocking.join()
        assertEquals(1, pending.count { it.await() })
        assertEquals(listOf(19), effects)
    }
}
