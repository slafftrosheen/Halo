package com.halo.engines.execution.capability

import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilityTest {

    @Test
    fun `supports returns true for DeviceTask`() = runBlocking<Unit> {
        val capability = DeviceCapability()
        val task = DeviceTask(
            action = "flashlight_on"
        )
        assertTrue(capability.supports(task))
    }

    @Test
    fun `execute returns Success for valid task`() = runBlocking<Unit> {
        val capability = DeviceCapability()
        val task = DeviceTask(
            action = "flashlight_on"
        )
        val result = capability.execute(task)
        assertTrue(result is Outcome.Success)
    }
}
