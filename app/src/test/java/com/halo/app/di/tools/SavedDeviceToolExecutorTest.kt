package com.halo.app.di.tools

import com.halo.app.embodiment.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.embodiment.SessionState
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SavedDeviceToolExecutorTest {
    // Sensitive ALWAYS-approved runtime calls intentionally do not set the direct-user shortcut flag.
    private val authority = ToolExecutionContext("explicit-interaction", false)
    private fun call(name: String, args: Map<String, Any> = emptyMap()) = ToolCall("test", name, args)
    private fun json(value: ToolExecutionResult) = JSONObject((value as ToolExecutionResult.Success).output)

    @Test fun writesRequireAdultConfigurationGateAndCurrentAuthorizedInteraction() = runBlocking {
        val f = DeviceSettingsFixture(); val session = f.save()
        val args = mapOf("deviceId" to "AA:01", "sourceVersion" to f.owner.get("AA:01")!!.sourceVersion)
        val tool = SavedDeviceToolExecutor(f.owner) { true }
        assertTrue(tool.execute(call("halo_saved_device_forget", args)) is ToolExecutionResult.Rejected)
        assertTrue(tool.execute(call("halo_saved_device_disconnect", args), ToolExecutionContext(null, true)) is ToolExecutionResult.Rejected)
        assertTrue(SavedDeviceToolExecutor(f.owner).execute(call("halo_saved_devices"), authority) is ToolExecutionResult.Rejected)
        assertNotNull(f.registry.getDevice("AA:01")); assertEquals(0, session.disconnects)
    }

    @Test fun exactTargetAndSourceVersionsRejectAnotherDeviceAndChangedSession() = runBlocking {
        val f = DeviceSettingsFixture(); val first = f.save(); val second = f.save("AA:02")
        val source = f.owner.get("AA:01")!!.sourceVersion
        val tool = SavedDeviceToolExecutor(f.owner) { true }
        assertTrue(tool.execute(call("halo_saved_device_forget", mapOf("deviceId" to "AA:02", "sourceVersion" to source)), authority) is ToolExecutionResult.Rejected)
        first.state.value = SessionState.HANDSHAKE
        assertTrue(tool.execute(call("halo_saved_device_disconnect", mapOf("deviceId" to "AA:01", "sourceVersion" to source)), authority) is ToolExecutionResult.Rejected)
        assertEquals(0, first.disconnects); assertEquals(0, second.disconnects)
        assertEquals(2, f.registry.getSavedDevices().size)
    }

    @Test fun retryUsesSameOwnerConsumesTokenAndNeverClaimsConnected() = runBlocking {
        val f = DeviceSettingsFixture(); val session = f.save()
        val source = f.owner.get("AA:01")!!.sourceVersion
        val tool = SavedDeviceToolExecutor(f.owner) { true }
        val request = call("halo_saved_device_retry", mapOf("deviceId" to "AA:01", "sourceVersion" to source))
        val result = tool.execute(request, authority)
        assertEquals(ActionTruth.DISPATCHED, (result as ToolExecutionResult.Success).actionTruth)
        assertTrue(json(result).getBoolean("requested")); assertFalse(json(result).getBoolean("connectionConfirmed"))
        assertEquals(listOf("AA:01"), f.retried); assertEquals(SessionState.DISCONNECTED, session.state.value)
        assertTrue(tool.execute(request, authority) is ToolExecutionResult.Rejected)
        assertEquals(1, f.retried.size)
    }

    @Test fun autoConnectAndForgetUseExistingRegistryAndSessionWithoutAffectingOtherDevice() = runBlocking {
        val f = DeviceSettingsFixture(); val session = f.save(auto = false); f.save("AA:02")
        val tool = SavedDeviceToolExecutor(f.owner) { true }
        val updated = tool.execute(call("halo_saved_device_autoconnect", mapOf("deviceId" to "AA:01",
            "sourceVersion" to f.owner.get("AA:01")!!.sourceVersion, "enabled" to true)), authority)
        assertTrue(json(updated).getBoolean("autoConnect")); assertTrue(f.registry.getDevice("AA:01")!!.autoConnect)
        assertEquals(listOf("AA:01"), f.retried)
        val forgotten = tool.execute(call("halo_saved_device_forget", mapOf("deviceId" to "AA:01",
            "sourceVersion" to f.owner.get("AA:01")!!.sourceVersion)), authority)
        assertFalse(json(forgotten).getBoolean("saved")); assertNull(f.registry.getDevice("AA:01"))
        assertNotNull(f.registry.getDevice("AA:02")); assertEquals(1, session.disconnects)
    }

    @Test fun connectAndDisconnectDispatchOnlyExistingExactSessionAndKeepTruthSeparate() = runBlocking {
        val f = DeviceSettingsFixture(); val session = f.save()
        val tool = SavedDeviceToolExecutor(f.owner) { true }
        for (name in listOf("halo_saved_device_connect", "halo_saved_device_disconnect")) {
            val result = tool.execute(call(name, mapOf("deviceId" to "AA:01", "sourceVersion" to f.owner.get("AA:01")!!.sourceVersion)), authority)
            assertEquals(ActionTruth.DISPATCHED, (result as ToolExecutionResult.Success).actionTruth)
            assertFalse(json(result).getBoolean("connectionConfirmed"))
            assertFalse(json(result).getBoolean("physicalDisconnectionConfirmed"))
        }
        assertEquals(1, session.connects); assertEquals(1, session.disconnects)
        f.sessions.unregisterSession("AA:01")
        assertTrue(tool.execute(call("halo_saved_device_connect", mapOf("deviceId" to "AA:01",
            "sourceVersion" to f.owner.get("AA:01")!!.sourceVersion)), authority) is ToolExecutionResult.Rejected)
    }

    @Test fun qualityValidatesIntegralRangePersistsReadbackAndRejectsStaleSource() = runBlocking {
        val f = DeviceSettingsFixture(); val tool = SavedDeviceToolExecutor(f.owner) { true }
        val source = json(tool.execute(call("halo_device_preferences"))).getString("sourceVersion")
        for (invalid in listOf(-1, 6, 2.5, "3", Double.NaN)) {
            assertTrue(tool.execute(call("halo_device_thumbnail_quality", mapOf("quality" to invalid, "sourceVersion" to source)), authority) is ToolExecutionResult.Rejected)
        }
        val saved = tool.execute(call("halo_device_thumbnail_quality", mapOf("quality" to 5, "sourceVersion" to source)), authority)
        assertEquals(5, json(saved).getInt("quality")); assertEquals(5, f.owner.thumbnailQuality.value)
        assertEquals("5", f.storage.getString("heycyan_ai_thumbnail_quality"))
        assertTrue(tool.execute(call("halo_device_thumbnail_quality", mapOf("quality" to 1, "sourceVersion" to source)), authority) is ToolExecutionResult.Rejected)
        assertNotEquals(source, f.owner.preferences().sourceVersion)
    }

    @Test fun restartAndBusySessionInvalidateOldAuthorityAndUnexpectedArgumentsNeverDispatch() = runBlocking {
        val f = DeviceSettingsFixture(); val session = f.save()
        val old = f.owner.get("AA:01")!!.sourceVersion
        val restarted = SavedDeviceActions(f.registry, f.sessions, f.storage) { true }
        assertNull(restarted.apply("AA:01", SavedDeviceCommand.FORGET, expectedVersion = old))
        session.state.value = SessionState.CAPABILITY_SYNC
        assertNull(f.owner.apply("AA:01", SavedDeviceCommand.RETRY, expectedVersion = f.owner.get("AA:01")!!.sourceVersion))
        assertTrue(SavedDeviceToolExecutor(f.owner) { true }.execute(call("halo_saved_device_connect",
            mapOf("deviceId" to "AA:01", "sourceVersion" to old, "pairingApproved" to true)), authority) is ToolExecutionResult.Rejected)
        assertEquals(0, session.connects); assertTrue(f.retried.isEmpty())
    }

    @Test fun transportFailureAfterDispatchIsUnknownAndConsumesObservedToken() = runBlocking {
        val f = DeviceSettingsFixture(); val session = f.save().apply { failConnect = true }
        val tool = SavedDeviceToolExecutor(f.owner) { true }
        val request = call("halo_saved_device_connect", mapOf("deviceId" to "AA:01", "sourceVersion" to f.owner.get("AA:01")!!.sourceVersion))
        val failed = tool.execute(request, authority) as ToolExecutionResult.Failed
        assertTrue(failed.completionUnknown)
        assertTrue(tool.execute(request, authority) is ToolExecutionResult.Rejected)
        assertEquals(1, session.connects)
    }

    @Test fun savedDeviceDiscoveryPagesExactIdsWithoutScanningAndRejectsFractionalOffsets() = runBlocking {
        val f = DeviceSettingsFixture()
        repeat(65) { index ->
            val identity = com.halo.sdk.runtime.DeviceIdentity(deviceId = "device-%03d".format(index), deviceType = "HEYCYAN_GLASSES", displayName = "Saved device")
            f.registry.saveDevice(com.halo.sdk.runtime.embodiment.DeviceRecord(identity))
        }
        val tool = SavedDeviceToolExecutor(f.owner) { true }
        val first = json(tool.execute(call("halo_saved_devices")))
        assertEquals(64, first.getJSONArray("devices").length())
        assertEquals(64, first.getInt("nextOffset"))
        val second = json(tool.execute(call("halo_saved_devices", mapOf("offset" to 64))))
        assertEquals(1, second.getJSONArray("devices").length())
        assertEquals("device-064", second.getJSONArray("devices").getJSONObject(0).getString("deviceId"))
        assertTrue(second.isNull("nextOffset"))
        assertTrue(tool.execute(call("halo_saved_devices", mapOf("offset" to 0.5))) is ToolExecutionResult.Rejected)
        assertTrue(f.sessions.activeSessions.value.isEmpty()); assertTrue(f.retried.isEmpty())
    }

    @Test fun coordinatorFalseNeverBecomesAConnectedOrDispatchedSuccessReceipt() = runBlocking {
        val f = DeviceSettingsFixture(); f.save()
        val owner = SavedDeviceActions(f.registry, f.sessions, f.storage) { false }
        val tool = SavedDeviceToolExecutor(owner) { true }
        val result = tool.execute(call("halo_saved_device_retry", mapOf("deviceId" to "AA:01",
            "sourceVersion" to owner.get("AA:01")!!.sourceVersion)), authority)
        assertTrue(result is ToolExecutionResult.Failed)
        assertTrue((result as ToolExecutionResult.Failed).completionUnknown)
        assertNotNull(f.registry.getDevice("AA:01"))
    }
}
