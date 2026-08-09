package com.halo.engines.execution.service

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.CapabilityMetadata
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRegistryTest {
    @Test
    fun `registry resolves highest priority matching capability`() = runBlocking {
        val registry = CapabilityRegistry()
        val low = TestCapability("low", priority = 1)
        val high = TestCapability("high", priority = 10)

        registry.register(low)
        registry.register(high)

        assertEquals("high", registry.resolve(DeviceTask(action = "test"))?.id)
        assertEquals(listOf("high", "low"), registry.capabilities().map { it.id })
    }

    @Test
    fun `registry rejects duplicate IDs and supports unregister`() {
        val registry = CapabilityRegistry()
        registry.register(TestCapability("same"))

        try {
            registry.register(TestCapability("same"))
            throw AssertionError("Expected duplicate capability registration to fail")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        assertNotNull(registry.get("same"))
        assertTrue(registry.unregister("same"))
        assertFalse(registry.unregister("same"))
    }

    private class TestCapability(
        override val id: String,
        priority: Int = 0
    ) : Capability {
        override val metadata = CapabilityMetadata(id = id, priority = priority)

        override suspend fun supports(task: Task): Boolean = task is DeviceTask

        override suspend fun execute(task: Task): Outcome = Outcome.Success(id)
    }
}