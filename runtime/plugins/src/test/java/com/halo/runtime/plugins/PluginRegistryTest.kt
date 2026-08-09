package com.halo.runtime.plugins

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.ICapabilityRegistry
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task
import com.halo.sdk.runtime.HaloIntent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRegistryTest {
    @Test
    fun `plugin capabilities are registered and removed with plugin`() {
        val registry = RecordingCapabilityRegistry()
        val plugins = PluginRegistry(registry)
        val plugin = TestPlugin()

        plugins.register(plugin)
        assertEquals(listOf("capability.test.plugin"), registry.registered)
        assertEquals(listOf(plugin), plugins.plugins())

        plugins.unregister(plugin)
        assertEquals(listOf("capability.test.plugin"), registry.unregistered)
        assertTrue(plugins.plugins().isEmpty())
    }

    private class RecordingCapabilityRegistry : ICapabilityRegistry {
        val registered = mutableListOf<String>()
        val unregistered = mutableListOf<String>()

        override fun register(capability: Capability) { registered += capability.id }
        override fun unregister(id: String): Boolean { unregistered += id; return true }
        override suspend fun resolve(task: Task): Capability? = null
    }

    private class TestPlugin : HaloPlugin {
        override val descriptor = PluginDescriptor("plugin.test", "Test", "1", "Halo", "Test plugin")
        override val permissions = emptySet<PluginPermission>()
        private val capability = object : Capability {
            override val id = "capability.test.plugin"
            override suspend fun supports(task: Task): Boolean = task is DeviceTask
            override suspend fun execute(task: Task): Outcome = Outcome.Success("ok")
        }

        override fun capabilities(): List<Capability> = listOf(capability)
        override suspend fun supports(intent: HaloIntent): Boolean = false
        override suspend fun execute(intent: HaloIntent, context: PluginContext): PluginResult = PluginFailure("unsupported")
    }
}