package com.halo.runtime.cognition

import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    private val retryPolicy = DefaultRetryPolicy()

    @Test
    fun testExponentialBackoff() = runTest {
        val outcome = Outcome.RetryableFailure("Test failure", null)
        
        val delay1 = retryPolicy.nextDelay(1, outcome)
        val delay2 = retryPolicy.nextDelay(2, outcome)
        val delay3 = retryPolicy.nextDelay(3, outcome)
        val delay4 = retryPolicy.nextDelay(4, outcome)
        val delay5 = retryPolicy.nextDelay(5, outcome)
        
        // Exponential: 100, 200, 400, 800, 1600
        assertEquals(100.milliseconds, delay1)
        assertEquals(200.milliseconds, delay2)
        assertEquals(400.milliseconds, delay3)
        assertEquals(800.milliseconds, delay4)
        assertEquals(1600.milliseconds, delay5)
    }

    @Test
    fun testMaxAttemptsForNetworkTimeout() = runTest {
        val outcome = Outcome.RetryableFailure("Network timeout", null)
        
        val max = retryPolicy.maxAttempts(outcome)
        
        assertEquals("Network timeouts should have 5 max attempts", 5, max)
    }

    @Test
    fun testMaxAttemptsForCameraBusy() = runTest {
        val outcome = Outcome.RetryableFailure("Camera busy", null)
        
        val max = retryPolicy.maxAttempts(outcome)
        
        assertEquals("Camera busy should have 2 max attempts", 2, max)
    }

    @Test
    fun testMaxAttemptsForSpotifyNotInstalled() = runTest {
        val outcome = Outcome.RetryableFailure("Spotify not installed", null)
        
        val max = retryPolicy.maxAttempts(outcome)
        
        assertEquals("App not installed should have 0 max attempts", 0, max)
    }

    @Test
    fun testMaxAttemptsForPermanentFailure() = runTest {
        val outcome = Outcome.PermanentFailure("Permission denied", null)
        
        val max = retryPolicy.maxAttempts(outcome)
        
        assertEquals("Permanent failures should have 0 max attempts", 0, max)
    }

    @Test
    fun testMaxAttemptsForCancelled() = runTest {
        val outcome = Outcome.Cancelled("User cancelled")
        
        val max = retryPolicy.maxAttempts(outcome)
        
        assertEquals("Cancelled should have 0 max attempts", 0, max)
    }

    @Test
    fun testShouldRetryRespectsMaxAttempts() = runTest {
        val outcome = Outcome.RetryableFailure("Network timeout", null)
        
        // Should retry up to 5 times
        assertTrue("Should retry on attempt 1", retryPolicy.shouldRetry(1, outcome))
        assertTrue("Should retry on attempt 2", retryPolicy.shouldRetry(2, outcome))
        assertTrue("Should retry on attempt 3", retryPolicy.shouldRetry(3, outcome))
        assertTrue("Should retry on attempt 4", retryPolicy.shouldRetry(4, outcome))
        assertTrue("Should retry on attempt 5", retryPolicy.shouldRetry(5, outcome))
        assertEquals(false, retryPolicy.shouldRetry(6, outcome))
    }

    @Test
    fun testShouldNotRetryPermanentFailure() = runTest {
        val outcome = Outcome.PermanentFailure("App not found", null)
        
        assertEquals(false, retryPolicy.shouldRetry(1, outcome))
    }

    @Test
    fun testShouldNotRetryCancelled() = runTest {
        val outcome = Outcome.Cancelled("User cancelled")
        
        assertEquals(false, retryPolicy.shouldRetry(1, outcome))
    }

    @Test
    fun testRetryableFailureHasRetryCount() = runTest {
        val outcome = Outcome.RetryableFailure("Test", null, retryCount = 3)
        
        assertEquals(3, outcome.retryCount)
    }

    @Test
    fun testDefaultRetryCountIsZero() = runTest {
        val outcome = Outcome.RetryableFailure("Test", null)
        
        assertEquals(0, outcome.retryCount)
    }
}