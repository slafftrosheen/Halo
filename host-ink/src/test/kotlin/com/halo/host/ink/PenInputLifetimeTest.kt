package com.halo.host.ink

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class PenInputLifetimeTest {
    @Test fun ownerCancellationStopsBlockedInputBeforeWaitingForCompletion(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val stopRequested = CompletableDeferred<Unit>()
        val finish = CountDownLatch(1)
        val exited = AtomicBoolean(false)
        val owner = launch {
            readPenUntilCancelled(stop = { stopRequested.complete(Unit) }) {
                entered.complete(Unit)
                check(finish.await(5, TimeUnit.SECONDS)) { "Fixture input was not released" }
                exited.set(true)
            }
        }
        try {
            withTimeout(2000) { entered.await() }
            owner.cancel()
            withTimeout(2000) { stopRequested.await() }
            // A new native owner must not be admitted while the old reader is still alive.
            assertFalse(exited.get())
            assertFalse(owner.isCompleted)
            finish.countDown()
            withTimeout(2000) { owner.join() }
            assertTrue(exited.get())
        } finally {
            finish.countDown()
            owner.cancelAndJoin()
        }
    }

    @Test fun cancellationStopUnblocksTheActualReader(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val released = CountDownLatch(1)
        val owner = launch {
            readPenUntilCancelled(stop = { released.countDown() }) {
                entered.complete(Unit)
                check(released.await(5, TimeUnit.SECONDS)) { "Cancellation did not stop native input" }
            }
        }
        try {
            withTimeout(2000) { entered.await() }
            owner.cancel()
            withTimeout(2000) { owner.join() }
        } finally {
            released.countDown()
            owner.cancelAndJoin()
        }
    }
}
