package com.halo.app.di.tools

import com.halo.app.di.tools.host.NativeDeviceStatusTools
import com.halo.app.embodiment.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.wearables.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class NativeDeviceStatusToolsTest {
    private class Owner : NativeDeviceStatusAccess {
        var state = NativeSnapshot(stage = NativeStage.READY, batteryPercent = 80, model = "Frame", cameraAvailable = false)
        var reads = 0
        var writes = 0
        var beforeDispatch: () -> Unit = {}
        override suspend fun get(deviceId: String): NativeDeviceStatus? {
            reads++
            return if (deviceId == "selected") NativeDeviceStatus(deviceId, "BRILLIANT_FRAME", state,
                setOf(NativeAction.BATTERY, NativeAction.DEVICE_INFO)) else null
        }
        override suspend fun refresh(deviceId: String, action: NativeAction, sourceVersion: String, requestAllowed: () -> Boolean): NativeDeviceStatus? {
            beforeDispatch()
            if (deviceId != "selected" || state.sourceVersion != sourceVersion || !requestAllowed()) return null
            writes++; state = state.copy(sourceVersion = UUID.randomUUID().toString())
            return get(deviceId)
        }
    }
    private val owner = Owner()
    private var access = true
    private var remote = false
    private val tools = NativeDeviceStatusTools(owner, { access }, { remote })
    private fun get(id: String = "selected") = ToolCall("test", NativeDeviceStatusTools.GET, mapOf("deviceId" to id))
    private fun refresh(version: String = owner.state.sourceVersion, operation: String = "battery") =
        ToolCall("test", NativeDeviceStatusTools.REFRESH,
            mapOf("deviceId" to "selected", "sourceVersion" to version, "operation" to operation))

    @Test fun registeredContractsAndPoliciesAreClosed() {
        val descriptors = DescriptorCatalog.buildDescriptors(HaloToolDescriptors.ExecutorKind.DEVICE to tools)
        val registry = ToolRegistry(listOf(tools), descriptors)
        assertEquals(emptyList<ToolOperabilityProblem>(), registry.operabilityReport(listOf(
            ToolFeatureOperability("native-device-status", NativeDeviceStatusTools.NAMES))))
        val refresh = descriptors.single { it.name == NativeDeviceStatusTools.REFRESH }
        assertEquals(ReplaySafety.NEVER_REPLAY, refresh.security.replaySafety)
        assertEquals(ConfirmationPolicy.USER_POLICY, refresh.security.confirmation)
    }
    @Test fun missingScopeOrAccessDoesNotReadOrWrite() = runBlocking {
        assertTrue(tools.execute(get()) is ToolExecutionResult.Rejected)
        withContext(RunToolScope(AgentRunId("test"))) {
            access = false
            assertTrue(tools.execute(refresh()) is ToolExecutionResult.Rejected)
        }
        assertEquals(0, owner.reads); assertEquals(0, owner.writes)
    }
    @Test fun cachedStatusCannotClaimFreshnessOrHardwareQualification() = runBlocking {
        withContext(RunToolScope(AgentRunId("test"))) {
            val result = tools.execute(get()) as ToolExecutionResult.Success
            assertEquals(ActionTruth.ACKNOWLEDGED, result.actionTruth)
            val json = JSONObject(result.output)
            assertFalse(json.getBoolean("freshResponse")); assertFalse(json.getBoolean("hardwareQualified"))
            assertEquals("Frame", json.getString("model"))
            assertTrue(tools.execute(get("other")) is ToolExecutionResult.Rejected)
        }
        assertEquals(0, owner.writes)
    }
    @Test fun onlyTwoFixedReadOperationsAreAccepted() = runBlocking {
        withContext(RunToolScope(AgentRunId("test"))) {
            assertTrue(tools.execute(refresh(operation = "frame.update()")) is ToolExecutionResult.Rejected)
            assertTrue(tools.execute(refresh().let { it.copy(arguments = it.arguments + ("lua" to "print(1)")) }) is ToolExecutionResult.Rejected)
            assertTrue(tools.execute(refresh(version = "not-a-source-version")) is ToolExecutionResult.Rejected)
        }
        assertEquals(0, owner.writes)
    }
    @Test fun refreshConsumesSourceAndReportsResponseOnly() = runBlocking {
        withContext(RunToolScope(AgentRunId("test"))) {
            val call = refresh()
            val result = tools.execute(call) as ToolExecutionResult.Success
            assertTrue(JSONObject(result.output).getBoolean("freshResponse"))
            assertEquals(ActionTruth.ACKNOWLEDGED, result.actionTruth)
            assertTrue(tools.execute(call) is ToolExecutionResult.Rejected)
        }
        assertEquals(1, owner.writes)
    }
    @Test fun remoteDisclosureAndRevocationAreCheckedAtDispatch() = runBlocking {
        withContext(RunToolScope(AgentRunId("test"), targetIsLocal = false)) {
            assertTrue(tools.execute(get()) is ToolExecutionResult.Rejected)
            remote = true
            assertTrue(tools.execute(get()) is ToolExecutionResult.Success)
            owner.beforeDispatch = { remote = false }
            assertTrue(tools.execute(refresh()) is ToolExecutionResult.Rejected)
        }
        assertEquals(0, owner.writes)
    }
}
