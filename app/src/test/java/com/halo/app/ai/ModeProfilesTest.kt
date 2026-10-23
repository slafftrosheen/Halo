package com.halo.app.ai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ModeProfilesTest {
    @Test fun firstPowerActivationRequiresTheIndependentSharingAcknowledgment() {
        val store = owner()
        try { store.set(PrivacyMode.CLOUD); fail() } catch (_: IllegalStateException) { }
        assertEquals(PrivacyMode.PRIVATE, store.mode.value)
        assertFalse(store.sharingAcknowledged)
    }
    private val textRequest = RemoteDispatchRequest("openai", RemoteOperation.INFERENCE, setOf(RemoteDataCategory.TEXT))
    private fun owner(storage: Memory = Memory()) = PrivacyModeStore(storage).apply {
        remoteAllowed = { true }; configuredProviders = { setOf("openai", "provider.elevenlabs.tts", "web.public") }
    }
    private fun power(store: PrivacyModeStore) { store.acknowledgeSharing(); store.set(PrivacyMode.CLOUD) }
    private fun blocked(block: () -> Unit) {
        try { block(); fail("Dispatch should be blocked") } catch (_: RemoteDispatchBlocked) { }
    }

    @Test fun privateUpgradePreservesChoicesAndNeverGrantsSpending() {
        val disk = Memory().apply {
            putString(PRIVACY_MODE_KEY, "PRIVATE"); putString("active_ai_provider", "provider.litert.local")
            putString("active_ai_model", "local-model"); putBoolean(KEY_MEMORIES, false)
            putBoolean(PaidMediaPolicy.CONSENT_KEY, true); putString("openai_api_key", "fixture-secret")
        }
        val store = owner(disk)
        assertEquals(PrivacyMode.PRIVATE, store.mode.value)
        assertEquals("local-model", store.storage.getString("active_ai_model"))
        assertFalse(store.storage.getBoolean(KEY_MEMORIES, true))
        assertFalse(disk.getBoolean(PaidMediaPolicy.CONSENT_KEY))
        power(store)
        assertFalse(disk.getBoolean(PaidMediaPolicy.CONSENT_KEY))
        store.storage.putBatch(strings = mapOf("active_ai_provider" to "openai", "active_ai_model" to "power-model"),
            booleans = mapOf(KEY_MEMORIES to true))
        store.set(PrivacyMode.PRIVATE)
        assertEquals("local-model", store.storage.getString("active_ai_model"))
        assertFalse(store.storage.getBoolean(KEY_MEMORIES, true))
        val restart = owner(disk)
        restart.set(PrivacyMode.CLOUD)
        assertEquals("power-model", restart.storage.getString("active_ai_model"))
        assertTrue(restart.storage.getBoolean(KEY_MEMORIES))
        assertEquals("fixture-secret", disk.getString("openai_api_key"))
        assertFalse(disk.getString(MODE_PROFILES_KEY)!!.contains("fixture-secret"))
        assertFalse(disk.getString(MODE_PROFILES_KEY)!!.contains("openai_api_key"))
    }

    @Test fun legacyCloudKeepsSelectionButCannotDispatchWithoutDisclosure() {
        val disk = Memory().apply {
            putString(PRIVACY_MODE_KEY, "CLOUD"); putString("active_ai_provider", "openai")
            putString("active_ai_model", "selected-model")
        }
        val store = owner(disk)
        assertEquals(PrivacyMode.CLOUD, store.mode.value)
        assertEquals("selected-model", store.profile(PrivacyMode.CLOUD).modelId)
        blocked { store.acquire(textRequest, store.revision) {} }
        store.acknowledgeSharing()
        store.acquire(textRequest, store.revision) {}.finish(RemoteDispatchOutcome.COMPLETED)
        assertEquals(OnlineFeature.entries.toSet(), store.profile(PrivacyMode.CLOUD).onlineFeatures)
    }

    @Test fun corruptAndFutureSchemasFailClosedWithAnActionableError() {
        for (raw in listOf("broken", "{}", "{\"schema\":99}", "{\"schema\":1,\"active\":\"CLOUD\"}")) {
            val store = owner(Memory().apply { putString(MODE_PROFILES_KEY, raw); putString(PRIVACY_MODE_KEY, "CLOUD") })
            assertEquals(PrivacyMode.PRIVATE, store.mode.value)
            assertNotNull(store.error)
            blocked { store.acquire(textRequest, store.revision) {} }
        }
    }

    @Test fun failedRoutePreparationPublishesOnlyPrivateAndCancelsExistingCalls() {
        val store = owner(); power(store)
        var cancelled = 0
        val lease = store.acquire(textRequest, store.revision) { cancelled++ }
        var recovered = false
        try { store.set(PrivacyMode.CLOUD, recoverPrivate = {
            assertEquals(PrivacyMode.PRIVATE, store.mode.value); recovered = true
        }) { error("fixture preparation failure") }; fail() } catch (_: IllegalStateException) { }
        assertTrue(recovered)
        assertEquals(1, cancelled)
        assertEquals(PrivacyMode.PRIVATE, store.mode.value)
        blocked { lease.requireCurrent() }
        blocked { store.acquire(textRequest, store.revision) {} }
        assertNotNull(store.error)
    }

    @Test fun clearSettingsRevokesAuthorityAndCannotRetainAnActivePowerSnapshot() {
        val store = owner(); power(store)
        var cancelled = false
        val lease = store.acquire(textRequest, store.revision) { cancelled = true }
        store.storage.clear()
        assertTrue(cancelled)
        assertEquals(PrivacyMode.PRIVATE, store.mode.value)
        assertFalse(store.sharingAcknowledged)
        blocked { lease.requireCurrent() }
        blocked { store.acquire(textRequest, store.revision) {} }
    }

    @Test fun workspaceProvenanceGetsItsOwnFinalDispatchCheckAndReceiptCategory() {
        val store = owner(); power(store)
        val request = textRequest.copy(categories = setOf(RemoteDataCategory.TEXT, RemoteDataCategory.WORKSPACE))
        store.acquire(request, store.revision) {}.finish(RemoteDispatchOutcome.COMPLETED)
        assertTrue(RemoteDataCategory.WORKSPACE in store.activity.receipts.value.first().categories)
        store.edit(PrivacyMode.CLOUD) { it.copy(booleans = it.booleans + (KEY_CLOUD_WORKSPACE to false)) }
        blocked { store.acquire(request, store.revision) {} }
    }

    @Test fun activationPreparationIsBehindAClosedDispatchGate() {
        val store = owner(); store.acknowledgeSharing()
        store.set(PrivacyMode.CLOUD) { blocked { store.acquire(textRequest, store.revision) {} } }
        store.acquire(textRequest, store.revision) {}.close()
    }

    @Test fun failedPersistenceCannotPublishFullPowerOrOverwriteTheOtherProfile() {
        val disk = Memory(); val store = owner(disk); store.acknowledgeSharing()
        val original = store.profile(PrivacyMode.PRIVATE)
        disk.failWrites = true
        try { store.set(PrivacyMode.CLOUD); fail() } catch (_: IllegalStateException) { }
        assertEquals(PrivacyMode.PRIVATE, store.mode.value)
        assertEquals(original, store.profile(PrivacyMode.PRIVATE))
        blocked { store.acquire(textRequest, store.revision) {} }
    }

    @Test fun profileRevisionRejectsQueuedIntentEvenAfterModeReturnsToPower() {
        val store = owner(); power(store)
        val old = store.revision
        var cancelled = false
        val lease = store.acquire(textRequest, old) { cancelled = true }
        store.set(PrivacyMode.PRIVATE); store.set(PrivacyMode.CLOUD)
        assertTrue(cancelled)
        blocked { lease.requireCurrent() }
        blocked { store.acquire(textRequest, old) {} }
        store.acquire(textRequest, store.revision) {}.close()
    }

    @Test fun categoryFeatureKidsAndKnownProviderChecksApplyAtDispatch() {
        val store = owner(); power(store)
        blocked { store.acquire(textRequest.copy(providerId = "unregistered"), store.revision) {} }
        store.edit(PrivacyMode.CLOUD) { it.copy(onlineFeatures = it.onlineFeatures - OnlineFeature.WEB) }
        blocked { store.acquire(textRequest.copy(providerId = "web.public", operation = RemoteOperation.WEB), store.revision) {} }
        store.edit(PrivacyMode.CLOUD) { it.copy(booleans = it.booleans + (KEY_CLOUD_SCREEN to false)) }
        blocked { store.acquire(textRequest.copy(categories = setOf(RemoteDataCategory.SCREEN)), store.revision) {} }
        store.remoteAllowed = { false }
        blocked { store.acquire(textRequest, store.revision) {} }
    }

    @Test fun modeDoesNotGrantMediaConsentAndRevocationCancelsActiveMedia() {
        val store = owner(); power(store)
        val media = textRequest.copy(categories = setOf(RemoteDataCategory.IMAGES))
        blocked { store.acquire(media, store.revision) {} }
        store.storage.putBoolean(PaidMediaPolicy.CONSENT_KEY, true)
        var cancelled = false
        val lease = store.acquire(media, store.revision) { cancelled = true }
        store.storage.putBoolean(PaidMediaPolicy.CONSENT_KEY, false)
        assertTrue(cancelled)
        blocked { lease.requireCurrent() }
        store.set(PrivacyMode.PRIVATE); store.set(PrivacyMode.CLOUD)
        assertFalse(store.storage.getBoolean(PaidMediaPolicy.CONSENT_KEY))
    }

    @Test fun independentContextSpeechWorkloadAndImageChoicesSurviveRestart() {
        val disk = Memory(); val store = owner(disk)
        store.storage.putString("speech_tts_provider", "provider.android.tts")
        store.storage.putString("workspace_image_model", "private-unused")
        val local = store.storage.readRuntimeAiContextPolicy().copy(includeMemories = false, includeLocation = false)
        store.storage.writeRuntimeAiContextPolicy(local)
        power(store)
        store.storage.putString("speech_tts_provider", "provider.elevenlabs.tts")
        store.storage.putString("workspace_image_model", "power-image")
        store.storage.writeRuntimeAiContextPolicy(store.storage.readRuntimeAiContextPolicy().copy(includeMemories = true))
        assertTrue(WorkloadPreferences(store.storage).profile(LocalWorkload.CHAT).allowRemote)
        val restart = owner(disk)
        assertEquals("provider.elevenlabs.tts", restart.storage.getString("speech_tts_provider"))
        restart.set(PrivacyMode.PRIVATE)
        assertEquals("provider.android.tts", restart.storage.getString("speech_tts_provider"))
        assertEquals("private-unused", restart.storage.getString("workspace_image_model"))
        assertFalse(restart.storage.readRuntimeAiContextPolicy().includeMemories)
        assertFalse(WorkloadPreferences(restart.storage).profile(LocalWorkload.CHAT).allowRemote)
    }

    @Test fun receiptsAreBoundedContentFreeClearableAndDoNotGuessCost() {
        val disk = Memory(); val store = owner(disk); power(store)
        repeat(205) { store.acquire(textRequest, store.revision) {}.finish(RemoteDispatchOutcome.COMPLETED) }
        assertEquals(200, store.activity.receipts.value.size)
        assertTrue(store.activity.receipts.value.all { it.providerReportedCostUsd == null })
        store.acquire(textRequest, store.revision) {}.finish(RemoteDispatchOutcome.COMPLETED, "0.025")
        assertEquals("0.025", store.activity.receipts.value.first().providerReportedCostUsd)
        val late = store.acquire(textRequest, store.revision) {}
        late.finish(RemoteDispatchOutcome.REPLIED)
        late.reportProviderCostUsd("0.015")
        assertEquals("0.015", store.activity.receipts.value.first().providerReportedCostUsd)
        assertEquals(RemoteDispatchOutcome.REPLIED, store.activity.receipts.value.first().outcome)
        store.activity.clear()
        assertTrue(owner(disk).activity.receipts.value.isEmpty())
    }

    @Test fun providerReportedCostBeforeBodyCompletionSurvivesTheFinalOutcomeAndRestart() {
        val disk = Memory(); val store = owner(disk); power(store)
        val lease = store.acquire(textRequest, store.revision) {}
        lease.reportProviderCostUsd("0.019")
        assertEquals(RemoteDispatchOutcome.STARTED, store.activity.receipts.value.first().outcome)
        lease.finish(RemoteDispatchOutcome.REPLIED)
        lease.reportProviderCostUsd("0.999")
        val receipt = owner(disk).activity.receipts.value.first()
        assertEquals("0.019", receipt.providerReportedCostUsd)
        assertEquals(RemoteDispatchOutcome.REPLIED, receipt.outcome)
    }

    private class Memory : Storage {
        val strings = mutableMapOf<String, String?>(); val booleans = mutableMapOf<String, Boolean>()
        var failWrites = false
        override fun getString(key: String, defaultValue: String?) = strings[key] ?: defaultValue
        override fun putString(key: String, value: String?) { check(!failWrites); strings[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = booleans[key] ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { check(!failWrites); booleans[key] = value }
        override fun putBatch(strings: Map<String, String?>, booleans: Map<String, Boolean>) {
            check(!failWrites); this.strings.putAll(strings); this.booleans.putAll(booleans)
        }
        override fun getInt(key: String, defaultValue: Int) = defaultValue
        override fun putInt(key: String, value: Int) = error("No profile integers")
        override fun remove(key: String) { strings.remove(key); booleans.remove(key) }
        override fun clear() { strings.clear(); booleans.clear() }
    }
}
