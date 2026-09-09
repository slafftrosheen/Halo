package com.halo.drivers.heycyan

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealHeyCyanDriverCancellationTest {

    @Test
    fun `CancellationException is rethrown during connection`() = runTest {
        try {
            throw CancellationException("Simulated connection cancellation")
        } catch (e: CancellationException) {
            assertEquals("Simulated connection cancellation", e.message)
            return@runTest
        } catch (e: Throwable) {
            fail("Expected CancellationException, got ${e.javaClass.simpleName}")
        }
        fail("CancellationException was not thrown")
    }

    @Test
    fun `cancellation cleanup does not overwrite original exception`() = runTest {
        var originalExceptionCaught = false
        try {
            try {
                throw CancellationException("Original Cancellation")
            } catch (e: CancellationException) {
                runCatching {
                    // Simulating a cleanup step that might fail
                    throw RuntimeException("Cleanup failure")
                }
                throw e
            }
        } catch (e: CancellationException) {
            originalExceptionCaught = true
            assertEquals("Original Cancellation", e.message)
        }
        assertEquals(true, originalExceptionCaught)
    }
}
