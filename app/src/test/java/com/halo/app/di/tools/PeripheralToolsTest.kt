package com.halo.app.di.tools

import com.halo.app.di.tools.host.PeripheralTools
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.hardware.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PeripheralToolsTest {
    private val session = PeripheralInventorySession { "current-token" }.apply {
        update(listOf(PeripheralObservation("usb:/private/path", PeripheralProfile(PeripheralKind.USB, "Glasses", vendorId = 1, productId = 2))),
            InventoryAvailability.AVAILABLE, InventoryAvailability.AVAILABLE, 0, 1)
    }
    private var accessible = true
    private var remoteAllowed = false
    private var changeAccessDuringRead = false
    private val inventory = object : PeripheralInventory {
        override val state = MutableStateFlow(session.state)
        override suspend fun snapshot(): PeripheralInventorySnapshot {
            if (changeAccessDuringRead) accessible = false
            state.value = session.state
            return state.value
        }
    }
    private val tools = PeripheralTools(inventory, { accessible }, { remoteAllowed })
    private fun call(name: String, args: Map<String, Any> = emptyMap()) = ToolCall("inventory-test", name, args)
    private fun details(version: Long = session.state.sourceVersion) = call(PeripheralTools.DETAILS,
        mapOf("token" to "current-token", "sourceVersion" to version))

    @Test fun registeredQueriesHaveClosedContractsAndReadOnlySecurity() {
        val descriptors = DescriptorCatalog.buildDescriptors(HaloToolDescriptors.ExecutorKind.DEVICE_INSIGHT to tools)
        val registry = ToolRegistry(listOf(tools), descriptors)
        assertEquals(PeripheralTools.NAMES, descriptors.map { it.name }.toSet())
        assertEquals(emptyList<ToolOperabilityProblem>(), registry.operabilityReport(listOf(ToolFeatureOperability("peripherals", PeripheralTools.NAMES))))
        descriptors.forEach { assertEquals(ToolRiskClass.READ_ONLY, it.security.risk) }
    }
    @Test fun deniedOrMissingScopeCannotRead() = runBlocking {
        assertTrue(tools.execute(call(PeripheralTools.LIST)) is ToolExecutionResult.Rejected)
        withContext(RunToolScope(AgentRunId("test"))) {
            accessible = false
            assertTrue(tools.execute(details()) is ToolExecutionResult.Rejected)
        }
    }
    @Test fun remoteDisclosureAndAccessAreRecheckedAfterRead() = runBlocking {
        withContext(RunToolScope(AgentRunId("test"), targetIsLocal = false)) {
            assertTrue(tools.execute(call(PeripheralTools.LIST)) is ToolExecutionResult.Rejected)
            remoteAllowed = true
            changeAccessDuringRead = true
            assertTrue(tools.execute(call(PeripheralTools.LIST)) is ToolExecutionResult.Rejected)
        }
    }
    @Test fun exactDetailsAreAdvertisedEvidenceWithoutPrivatePathOrQualification() = runBlocking {
        withContext(RunToolScope(AgentRunId("test"))) {
            val result = tools.execute(details()) as ToolExecutionResult.Success
            val body = JSONObject(result.output)
            assertEquals("OS_ADVERTISED", body.getString("evidence"))
            assertFalse(body.getBoolean("hardwareQualified"))
            assertFalse(result.output.contains("/private/path"))
            assertTrue(tools.execute(details(version = 0)) is ToolExecutionResult.Rejected)
            session.stop()
            assertTrue(tools.execute(details()) is ToolExecutionResult.Rejected)
        }
    }
    @Test fun extraAndFractionalArgumentsAreRejected() = runBlocking {
        withContext(RunToolScope(AgentRunId("test"))) {
            assertTrue(tools.execute(call(PeripheralTools.LIST, mapOf("openUsb" to true))) is ToolExecutionResult.Rejected)
            assertTrue(tools.execute(call(PeripheralTools.DETAILS, mapOf("token" to "current-token", "sourceVersion" to 1.5))) is ToolExecutionResult.Rejected)
        }
    }
}
