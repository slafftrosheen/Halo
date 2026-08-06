package com.halo.engines.execution.capability

import com.halo.runtime.cognition.MediaTask
import com.halo.runtime.cognition.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCapabilityTest {

    @Test
    fun `supports returns true for MediaTask`() = runBlocking<Unit> {
        val capability = MediaCapability()
        val task = MediaTask(
            mediaId = "spotify:track:xyz",
            mediaAction = "play"
        )
        assertTrue(capability.supports(task))
    }

    @Test
    fun `execute returns Success for valid task`() = runBlocking<Unit> {
        val capability = MediaCapability()
        val task = MediaTask(
            mediaId = "spotify:track:xyz",
            mediaAction = "play"
        )
        val result = capability.execute(task)
        assertTrue(result is Outcome.Success)
    }
}
