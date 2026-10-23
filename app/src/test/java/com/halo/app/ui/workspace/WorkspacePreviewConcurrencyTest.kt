package com.halo.app.ui.workspace

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WorkspacePreviewConcurrencyTest {
    @Test fun actualSharedDecodeGateBoundsConcurrentWork() = runBlocking {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val jobs = (0 until 8).map {
            async(Dispatchers.IO) {
                withWorkspacePreviewDecode {
                    val count = active.incrementAndGet()
                    maximum.updateAndGet { previous -> maxOf(previous, count) }
                    entered.countDown()
                    try { check(release.await(5, TimeUnit.SECONDS)) }
                    finally { active.decrementAndGet() }
                }
            }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(2, active.get())
        } finally { release.countDown() }
        jobs.awaitAll()
        assertEquals(2, maximum.get())
        assertEquals(0, active.get())
    }

    @Test fun canceledWaitingPreviewNeverOpensItsSourceAndReleasesTheGate() = runBlocking {
        val entered = CountDownLatch(2)
        val holders = (0 until 2).map {
            launch(Dispatchers.Default) { withWorkspacePreviewDecode { entered.countDown(); awaitCancellation() } }
        }
        var opens = 0
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val attempting = CompletableDeferred<Unit>()
            val waiting = async(Dispatchers.Default) {
                attempting.complete(Unit)
                decodeWorkspacePreviewAsync { opens++; null }
            }
            attempting.await()
            waiting.cancelAndJoin()
            assertEquals(0, opens)
        } finally { holders.forEach { it.cancelAndJoin() } }
        withTimeout(5000) { withWorkspacePreviewDecode { assertEquals(0, opens) } }
    }
}
