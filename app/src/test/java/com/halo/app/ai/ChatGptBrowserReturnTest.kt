package com.halo.app.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatGptBrowserReturnTest {
    @Test fun callbackWaitsForHaloBeforeExchangingOneTimeCode() = runTest {
        var foreground = false
        var exchanges = 0
        val job = launch { finishChatGptAfterReturn({ foreground }, { true }) { exchanges++ } }
        runCurrent(); advanceTimeBy(1000); runCurrent()
        assertEquals(0, exchanges)
        foreground = true; advanceUntilIdle(); job.join()
        assertEquals(1, exchanges)
    }
    @Test fun localModeOrCancellationWhileBrowserOpenNeverExchangesCode() = runTest {
        var power = true
        var exchanges = 0
        val job = launch { finishChatGptAfterReturn({ false }, { power }) { exchanges++ } }
        runCurrent(); power = false; advanceUntilIdle()
        assertTrue(job.isCancelled); assertEquals(0, exchanges)
        val cancelled = launch { finishChatGptAfterReturn({ false }, { true }) { exchanges++ } }
        runCurrent(); cancelled.cancelAndJoin(); assertEquals(0, exchanges)
    }
}
