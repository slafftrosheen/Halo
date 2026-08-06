package com.halo.engines.execution.capability

import com.halo.runtime.cognition.NavigationTask
import com.halo.runtime.cognition.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationCapabilityTest {

    @Test
    fun `supports returns true for NavigationTask`() = runBlocking<Unit> {
        val capability = NavigationCapability()
        val task = NavigationTask(
            coordinates = "home"
        )
        assertTrue(capability.supports(task))
    }

    @Test
    fun `execute returns Success for valid task`() = runBlocking<Unit> {
        val capability = NavigationCapability()
        val task = NavigationTask(
            coordinates = "home"
        )
        val result = capability.execute(task)
        assertTrue(result is Outcome.Success)
    }
}
