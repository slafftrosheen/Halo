package com.halo.app.ai

import com.halo.sdk.runtime.Storage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.async

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest = org.robolectric.annotation.Config.NONE, sdk = [34])
class RuntimeAiPolicyTest {
    @Test fun workloadChoicesAreIndependentAndCannotBypassPrivate() {
        val modes = PrivacyModeStore(MapStorage())
        val storage = modes.storage
        val preferences = WorkloadPreferences(storage)
        val workload = com.halo.sdk.provider.LocalWorkload.entries.first()
        modes.acknowledgeSharing(); modes.set(PrivacyMode.CLOUD)
        assertTrue(preferences.profile(workload).allowRemote)
        preferences.save(workload, preferences.profile(workload).copy(allowRemote = false))
        assertFalse(preferences.profile(workload).allowRemote)
        modes.set(PrivacyMode.PRIVATE)
        assertFalse(preferences.profile(workload).allowRemote)
    }
    @Test fun modeAndProfileChoicesSurviveRestartWithoutGrantingSpending() {
        val disk = MapStorage()
        val modes = PrivacyModeStore(disk)
        val storage = modes.storage
        assertFalse(storage.readRuntimeAiContextPolicy().allowCloudImages)
        modes.acknowledgeSharing(); modes.set(PrivacyMode.CLOUD)
        storage.writeRuntimeAiContextPolicy(com.halo.sdk.provider.AiContextPolicy())
        assertTrue(PrivacyModeStore(disk).mode.value == PrivacyMode.CLOUD)
        assertFalse(storage.readRuntimeAiContextPolicy().allowCloudMemories)
        assertFalse(storage.getBoolean(com.halo.sdk.provider.PaidMediaPolicy.CONSENT_KEY, false))
        modes.set(PrivacyMode.PRIVATE)
        assertFalse(storage.getBoolean(KEY_CLOUD_IMAGES, true))
        assertFalse(storage.getBoolean(com.halo.sdk.provider.PaidMediaPolicy.CONSENT_KEY, true))
        assertTrue(storage.readRuntimeAiContextPolicy().includeMemories)
    }
    @Test fun privateModeCancelsAnActiveCloudOperation() = kotlinx.coroutines.test.runTest {
        val modes = kotlinx.coroutines.flow.MutableStateFlow(PrivacyMode.CLOUD)
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finished = kotlinx.coroutines.CompletableDeferred<Unit>()
        val task = async {
            whileCloudMode(modes) { started.complete(Unit)
                try { kotlinx.coroutines.awaitCancellation() } finally { finished.complete(Unit) } }
        }
        started.await(); modes.value = PrivacyMode.PRIVATE
        try { task.await(); org.junit.Assert.fail("Cloud work must stop") }
        catch (_: kotlinx.coroutines.CancellationException) { }
        finished.await()
    }
    @Test fun cloudModeEnablesAllContextWithoutIndividualSharingGrants() {
        val modes = PrivacyModeStore(MapStorage())
        modes.acknowledgeSharing(); modes.set(PrivacyMode.CLOUD)
        val storage = modes.storage
        val policy = storage.readRuntimeAiContextPolicy()
        assertTrue(policy.includeMemories && policy.includeWorkspace && policy.includeImages && policy.includeAudio && policy.includeVideo)
        assertTrue(policy.allowCloudImages && policy.allowCloudMemories && policy.allowCloudWorkspace && policy.allowCloudScreenContext)
    }
    @Test fun privateModeOverridesEveryOldCloudGrant() {
        val storage = MapStorage().apply { putString("ai_privacy_mode_v1", "PRIVATE")
            putBoolean(KEY_MEMORIES, true)
            putBoolean(KEY_CLOUD_IMAGES, true); putBoolean(KEY_CLOUD_WORKSPACE, true); putBoolean(KEY_CLOUD_SCREEN, true) }
        val policy = storage.readRuntimeAiContextPolicy()
        assertFalse(policy.allowCloudImages || policy.allowCloudMemories || policy.allowCloudWorkspace || policy.allowCloudScreenContext)
        assertTrue(policy.includeMemories && policy.includeWorkspace && policy.includeImages)
    }

    @Test
    fun persistedPolicyMapsDirectlyToRuntimeContextPolicy() {
        val storage = MapStorage().apply {
            putBoolean(KEY_CONVERSATION, false)
            putBoolean(KEY_DEVICE_STATE, true)
            putBoolean(KEY_RECENT_EVENTS, true)
            putBoolean(KEY_MEMORIES, true)
            putBoolean(KEY_TOOLS, false)
            putBoolean(KEY_LOCATION, true)
            putBoolean(KEY_IMAGES, false)
            putBoolean(KEY_AUDIO, true)
            putBoolean(KEY_VIDEO, true)
        }

        val policy = storage.readRuntimeAiContextPolicy()

        assertFalse(policy.includeConversation)
        assertTrue(policy.includeDeviceState)
        assertTrue(policy.includeRecentEvents)
        assertTrue(policy.includeMemories)
        assertFalse(policy.includeAvailableTools)
        assertTrue(policy.includeLocation)
        assertFalse(policy.includeImages)
        assertTrue(policy.includeAudio)
        assertTrue(policy.includeVideo)
    }

    private class MapStorage : Storage {
        private val values = mutableMapOf<String, Any?>()

        override fun getString(key: String, defaultValue: String?): String? =
            values[key] as? String ?: defaultValue

        override fun putString(key: String, value: String?) {
            values[key] = value
        }

        override fun getInt(key: String, defaultValue: Int): Int =
            values[key] as? Int ?: defaultValue

        override fun putInt(key: String, value: Int) {
            values[key] = value
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key] as? Boolean ?: defaultValue

        override fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }

        override fun clear() {
            values.clear()
        }
    }
}
