package com.halo.app.ui.onboarding

import com.halo.sdk.runtime.Storage
import org.junit.Assert.*
import org.junit.Test

class SetupProgressStoreTest {
    @Test fun guideSurvivesRestartWithoutPretendingPermissionWasGranted() {
        val storage = MemoryStorage()
        val first = SetupProgressStore(storage)
        first.startCapabilityGuide("microphone")
        first.markCapabilityRequested("microphone")
        val interrupted = SetupProgressStore(storage)
        assertEquals("microphone", interrupted.capabilityGuide.pendingId)
        assertEquals(setOf("microphone"), interrupted.capabilityGuide.attemptedIds)
        interrupted.advanceCapabilityGuide("microphone", "contacts", attempted = true)
        val resumed = SetupProgressStore(storage).capabilityGuide
        assertTrue(resumed.active)
        assertEquals("contacts", resumed.currentId)
        assertNull(resumed.pendingId)
        assertEquals(setOf("microphone"), resumed.attemptedIds)
        assertTrue(resumed.skippedIds.isEmpty())
        assertFalse(storage.values.keys.any { "granted" in it })
    }

    @Test fun guideSkipAndCompletionSurviveRestartWithoutChangingPrivacyOrCredentials() {
        val storage = MemoryStorage()
        storage.putString("ai_privacy_mode_v1", "PRIVATE")
        storage.putString("openrouter_api_key", "secret")
        val first = SetupProgressStore(storage)
        first.startCapabilityGuide("camera")
        first.advanceCapabilityGuide("camera", null, attempted = false)
        val resumed = SetupProgressStore(storage).capabilityGuide
        assertFalse(resumed.active)
        assertNull(resumed.currentId)
        assertEquals(setOf("camera"), resumed.skippedIds)
        assertTrue(resumed.attemptedIds.isEmpty())
        assertEquals("PRIVATE", storage.getString("ai_privacy_mode_v1"))
        assertEquals("secret", storage.getString("openrouter_api_key"))
    }

    @Test fun staleGuideResultCannotAdvanceAnotherCapability() {
        val store = SetupProgressStore(MemoryStorage())
        store.startCapabilityGuide("camera")
        store.markCapabilityRequested("microphone")
        store.advanceCapabilityGuide("microphone", "contacts", attempted = true)
        assertEquals("camera", store.capabilityGuide.currentId)
        assertTrue(store.capabilityGuide.attemptedIds.isEmpty())
    }

    @Test fun corruptGuideIsBoundedAndCannotInjectUnknownCapabilities() {
        val storage = MemoryStorage()
        storage.putString("setup_capability_guide_v1", "1|true|unknown|secret,contacts|camera|unknown")
        val guide = SetupProgressStore(storage).capabilityGuide
        assertFalse(guide.active)
        assertNull(guide.currentId)
        assertEquals(setOf("contacts"), guide.attemptedIds)
        assertEquals(setOf("camera"), guide.skippedIds)
        storage.putString("setup_capability_guide_v1", "x".repeat(2049))
        assertEquals(CapabilityGuideProgress(), SetupProgressStore(storage).capabilityGuide)
    }

    @Test fun restartRetainsStageChoicesAndExactInteractionButNotConfigurationChanges() {
        val storage=MemoryStorage(); storage.putString("openrouter_api_key","secret"); storage.putString("app_language_v1","lv")
        val first=SetupProgressStore(storage)
        first.intention("Notes and Canvas"); first.open(SetupStage.SPEECH); first.limited(true); first.bindInteraction("owned")
        val second=SetupProgressStore(storage)
        assertEquals(first.state.value,second.state.value)
        second.open(SetupStage.AI)
        assertEquals("secret",storage.getString("openrouter_api_key")); assertEquals("lv",storage.getString("app_language_v1"))
        assertEquals("owned",second.state.value.interactionId)
    }
    @Test fun previousReplyAndPendingApprovalCannotFinishVerifiedSetup() {
        val setup=SetupProgressStore(MemoryStorage()); setup.bindInteraction("exact")
        assertFalse(setup.verifyInteraction("old",true,false))
        assertFalse(setup.verifyInteraction("exact",true,true))
        assertFalse(setup.verifyInteraction("exact",false,false))
        assertFalse(setup.finish(false))
        assertTrue(setup.verifyInteraction("exact",true,false)); assertTrue(setup.finish(false))
        assertEquals(SetupOutcome.VERIFIED,setup.state.value.outcome)
    }
    @Test fun limitedExitIsDistinctAndBackIsBounded() {
        val setup=SetupProgressStore(MemoryStorage()); setup.back(); assertEquals(SetupStage.WELCOME,setup.state.value.stage)
        setup.open(SetupStage.FIRST); setup.next(); assertEquals(SetupStage.FIRST,setup.state.value.stage)
        assertTrue(setup.finish(true)); assertEquals(SetupOutcome.LIMITED,setup.state.value.outcome); assertFalse(setup.state.value.firstVerified)
    }
    @Test fun corruptStageRecoversAndReportCannotLeakStoredSecretsOrContent() {
        val storage=MemoryStorage(); storage.putString("setup_stage_v1","bad"); storage.putString("private_note","private words"); storage.putString("openrouter_api_key","secret")
        val setup=SetupProgressStore(storage); setup.bindInteraction("private-interaction-id")
        assertEquals(SetupStage.WELCOME,setup.state.value.stage)
        val report=setup.report(true,false,false,false,false)
        assertFalse(report.contains("secret")); assertFalse(report.contains("private words")); assertFalse(report.contains("private-interaction-id"))
        assertTrue(report.contains("outcome=INCOMPLETE"))
    }
    @Test fun diagnosticsAreOptInBoundedAndRejectCorruptFreeText() {
        val storage=MemoryStorage(); val setup=SetupProgressStore(storage)
        setup.diagnostic(SetupError.PROVIDER_CONNECTION); assertNull(storage.getString("setup_events_v1"))
        setup.enableDiagnostics(true)
        storage.putString("setup_events_v1","secret credential and private transcript")
        assertFalse(setup.report(false,false,false,false,false).contains("secret credential"))
        repeat(100) { setup.diagnostic(SetupError.SPEECH_TEST) }
        assertEquals(50,storage.getString("setup_events_v1")!!.lines().size)
        assertFalse(storage.getString("setup_events_v1")!!.contains("private"))
        setup.enableDiagnostics(false); assertNull(storage.getString("setup_events_v1"))
    }
    @Test fun dismissingIncompleteWizardPersistsWithoutFakeVerification() {
        val storage = MemoryStorage()
        val first = SetupProgressStore(storage)
        assertTrue(first.shouldAutoLaunch())
        first.open(SetupStage.AI)
        first.dismissAutomaticLaunch()
        assertEquals(SetupOutcome.INCOMPLETE, first.state.value.outcome)
        assertFalse(first.shouldAutoLaunch())
        val restarted = SetupProgressStore(storage)
        assertFalse(restarted.shouldAutoLaunch())
        assertEquals(SetupStage.AI, restarted.state.value.stage)
        assertFalse(restarted.state.value.firstVerified)
        restarted.open(SetupStage.SPEECH) // User can still re-enter from Settings.
        assertFalse(restarted.shouldAutoLaunch())
    }

    @Test fun completingWizardNeverRequiresAutomaticLaunch() {
        val storage = MemoryStorage()
        val wizard = SetupProgressStore(storage)
        assertTrue(wizard.finish(true))
        assertFalse(SetupProgressStore(storage).shouldAutoLaunch())
    }

    private class MemoryStorage : Storage {
        val values=mutableMapOf<String,Any>()
        override fun getString(key:String,defaultValue:String?)=values[key] as? String ?: defaultValue
        override fun putString(key:String,value:String?) { if(value==null) values.remove(key) else values[key]=value }
        override fun getInt(key:String,defaultValue:Int)=values[key] as? Int ?: defaultValue
        override fun putInt(key:String,value:Int) {values[key]=value}
        override fun getBoolean(key:String,defaultValue:Boolean)=values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key:String,value:Boolean) {values[key]=value}
        override fun remove(key:String) {values.remove(key)}
        override fun clear() {values.clear()}
    }
}
