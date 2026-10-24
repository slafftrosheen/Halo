package com.halo.app.di.tools

import com.halo.app.data.notification.*
import com.halo.app.data.settings.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NotificationSettingsToolExecutorTest {
    private val authority = ToolExecutionContext("explicit-interaction", false)
    private fun call(name: String, args: Map<String, Any> = emptyMap()) = ToolCall("test", name, args)
    private fun json(value: ToolExecutionResult) = JSONObject((value as ToolExecutionResult.Success).output)
    private class Delivery : DigestDeliverySettingsRepository {
        override val state = MutableStateFlow(DigestDeliverySettings())
        var writes = 0
        override suspend fun setChannel(value: String) { state.value = state.value.copy(channel = value) }
        override suspend fun setWearableId(value: String) { state.value = state.value.copy(wearableId = value) }
        override suspend fun compareAndSet(expectedVersion: String, target: DigestDeliverySettings): Boolean {
            if (state.value.sourceVersion() != expectedVersion) return false
            writes++; state.value = target; return true
        }
    }
    private class Policies : NotificationPolicyControl {
        var revision = 0
        var writes = 0
        var value = NotificationAccessPolicy.BLOCK
        var failAfterWrite = false
        override suspend fun policySnapshot(pkg: String) = NotificationPolicySnapshot(pkg, value,
            notificationSettingsVersion("notification-policy", pkg, revision.toString(), value.name))
        override suspend fun compareAndSetPolicy(pkg: String, expectedVersion: String, policy: NotificationAccessPolicy): Boolean {
            if (policySnapshot(pkg).sourceVersion != expectedVersion) return false
            value = policy; revision++; writes++
            if (failAfterWrite) throw java.io.IOException("Persistence readback unknown")
            return true
        }
    }
    private class Fixture {
        val devices = DeviceSettingsFixture()
        val delivery = Delivery()
        val policies = Policies()
        var appVersion = notificationSettingsVersion("notification-app", "com.example.notes", "1")
        var installed = true
        val apps = NotificationSettingsApps {
            if (installed) listOf(NotificationSettingsApp("com.example.notes", "Untrusted app label", appVersion)) else emptyList()
        }
        val tool = NotificationSettingsToolExecutor(delivery, policies, apps, devices.owner) { true }
        suspend fun policyArgs(policy: String = "ALLOW"): Map<String, Any> = mapOf("packageName" to "com.example.notes",
            "appVersion" to appVersion, "sourceVersion" to policies.policySnapshot("com.example.notes").sourceVersion, "policy" to policy)
    }

    @Test fun sensitivePolicyExpansionRequiresGateAuthorityExactInstalledAppAndFreshPolicy() = runBlocking {
        val f = Fixture(); val args = f.policyArgs()
        assertTrue(f.tool.execute(call("halo_notification_policy_set", args)) is ToolExecutionResult.Rejected)
        val denied = NotificationSettingsToolExecutor(f.delivery, f.policies, f.apps, f.devices.owner)
        assertTrue(denied.execute(call("halo_notification_policy_set", args), authority) is ToolExecutionResult.Rejected)
        f.appVersion = notificationSettingsVersion("notification-app", "com.example.notes", "2")
        assertTrue(f.tool.execute(call("halo_notification_policy_set", args), authority) is ToolExecutionResult.Rejected)
        assertEquals(0, f.policies.writes)
        val current = f.policyArgs()
        val saved = json(f.tool.execute(call("halo_notification_policy_set", current), authority))
        assertEquals("ALLOW", saved.getString("policy")); assertTrue(saved.getBoolean("saved"))
        assertFalse(saved.getBoolean("androidAccessGranted")); assertEquals(1, f.policies.writes)
        assertTrue(f.tool.execute(call("halo_notification_policy_set", current), authority) is ToolExecutionResult.Rejected)
        f.installed = false
        assertTrue(f.tool.execute(call("halo_notification_policy_get", mapOf("packageName" to "com.example.notes"))) is ToolExecutionResult.Rejected)
    }

    @Test fun everyPolicyUsesCanonicalCasAndRejectsExtraKeysWithoutReadingContent() = runBlocking {
        val f = Fixture()
        for (policy in listOf("ALLOW", "SUMMARIZE_ONLY", "HIDE_CONTENT", "BLOCK")) {
            assertEquals(policy, json(f.tool.execute(call("halo_notification_policy_set", f.policyArgs(policy)), authority)).getString("policy"))
        }
        val args = f.policyArgs()
        for (invalid in listOf(args + ("grantAndroidAccess" to true), args + ("policy" to "EVERYTHING"), args - "appVersion")) {
            assertTrue(f.tool.execute(call("halo_notification_policy_set", invalid), authority) is ToolExecutionResult.Rejected)
        }
        assertEquals(4, f.policies.writes)
        val read = json(f.tool.execute(call("halo_notification_policy_get", mapOf("packageName" to "com.example.notes"))))
        assertEquals(setOf("packageName", "policy", "sourceVersion", "appVersion"), read.keys().asSequence().toSet())
    }

    @Test fun wearableDeliveryRequiresExactSavedEndpointVersionAndDoesNotSpeakOrFallback() = runBlocking {
        val f = Fixture(); f.devices.save()
        val source = f.delivery.state.value.sourceVersion()
        val args = mapOf("channel" to "wearable", "wearableId" to "AA:01", "sourceVersion" to source,
            "deviceVersion" to f.devices.owner.get("AA:01")!!.sourceVersion)
        assertTrue(f.tool.execute(call("halo_digest_delivery_set", args)) is ToolExecutionResult.Rejected)
        assertTrue(f.tool.execute(call("halo_digest_delivery_set", args + ("wearableId" to "AA:other")), authority) is ToolExecutionResult.Rejected)
        f.devices.owner.apply("AA:01", com.halo.app.embodiment.SavedDeviceCommand.AUTO_CONNECT, false)
        assertTrue(f.tool.execute(call("halo_digest_delivery_set", args), authority) is ToolExecutionResult.Rejected)
        val current = args + ("deviceVersion" to f.devices.owner.get("AA:01")!!.sourceVersion)
        val result = json(f.tool.execute(call("halo_digest_delivery_set", current), authority))
        assertEquals(DigestDeliverySettings("wearable", "AA:01"), f.delivery.state.value)
        assertFalse(result.getBoolean("delivered")); assertFalse(result.getBoolean("audible"))
        assertTrue(f.devices.retried.isEmpty())
        assertTrue(f.tool.execute(call("halo_digest_delivery_set", current), authority) is ToolExecutionResult.Rejected)
        assertEquals(1, f.delivery.writes)
    }

    @Test fun localDeliverySettingsKeepClosedSchemaAndExactCurrentConfiguration() = runBlocking {
        val f = Fixture()
        for (channel in listOf("android", "activity")) {
            val args = mapOf("channel" to channel, "wearableId" to "", "sourceVersion" to f.delivery.state.value.sourceVersion())
            assertTrue(f.tool.execute(call("halo_digest_delivery_set", args + ("deviceVersion" to "saved-device:" + "a".repeat(64))), authority) is ToolExecutionResult.Rejected)
            assertTrue(f.tool.execute(call("halo_digest_delivery_set", args + ("wearableId" to "phone")), authority) is ToolExecutionResult.Rejected)
            assertEquals(channel, json(f.tool.execute(call("halo_digest_delivery_set", args), authority)).getString("channel"))
        }
        assertEquals(2, f.delivery.writes)
        assertEquals("activity", json(f.tool.execute(call("halo_digest_delivery_get"))).getString("channel"))
    }

    @Test fun invalidPaginationAndUnknownActionCannotMutateAndListsAreBounded() = runBlocking {
        val f = Fixture()
        for (offset in listOf(-1, 0.5, 10001, "0")) {
            assertTrue(f.tool.execute(call("halo_notification_settings_apps", mapOf("offset" to offset))) is ToolExecutionResult.Rejected)
        }
        assertTrue(f.tool.execute(call("halo_notification_pair"), authority) is ToolExecutionResult.Rejected)
        assertEquals(0, f.policies.writes); assertEquals(0, f.delivery.writes)
        assertEquals(1, json(f.tool.execute(call("halo_notification_settings_apps"))).getJSONArray("apps").length())
    }

    @Test fun postWriteFailureReportsUnknownAndReadbackPreventsBlindReplay() = runBlocking {
        val f = Fixture(); val args = f.policyArgs(); f.policies.failAfterWrite = true
        val result = f.tool.execute(call("halo_notification_policy_set", args), authority) as ToolExecutionResult.Failed
        assertTrue(result.completionUnknown)
        assertEquals("ALLOW", json(f.tool.execute(call("halo_notification_policy_get", mapOf("packageName" to "com.example.notes")))).getString("policy"))
        assertTrue(f.tool.execute(call("halo_notification_policy_set", args), authority) is ToolExecutionResult.Rejected)
        assertEquals(1, f.policies.writes)
    }
}
