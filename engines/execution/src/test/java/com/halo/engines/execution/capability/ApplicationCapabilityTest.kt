package com.halo.engines.execution.capability

import com.halo.runtime.cognition.ApplicationTask
import com.halo.runtime.cognition.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationCapabilityTest {

    @Test
    fun `supports returns true for ApplicationTask`() = runBlocking<Unit> {
        val capability = ApplicationCapability()
        val task = ApplicationTask(
            packageName = "com.spotify.music",
            intent = "android.intent.action.VIEW"
        )
        assertTrue(capability.supports(task))
    }

    @Test
    fun `execute returns Success for valid task`() = runBlocking<Unit> {
        val capability = ApplicationCapability()
        val task = ApplicationTask(
            packageName = "com.spotify.music",
            intent = "android.intent.action.VIEW"
        )
        val result = capability.execute(task)
        assertTrue(result is Outcome.Success)
    }
}
