package com.halo.app.ui.screens.home

import com.halo.app.online.OnlineProblem
import org.junit.Assert.*
import org.junit.Test

class HomeToolCatalogTest {
    private val ready = HomeToolCatalogInput(
        runtimeTools = HomeToolCatalog.descriptors.flatMap { it.toolIds }.associateWith { HomeRuntimeToolReadiness(true) },
        privateMode = false, onlineAllowedByFamily = true, connected = true,
        answerReadiness = HomeAnswerReadiness.READY, webProblem = null, mapHandler = true,
        researchEnabled = true, speechReady = true, microphoneGranted = true,
        attachmentsAvailable = true, imageConfigured = true)
    private fun entry(id: String, input: HomeToolCatalogInput = ready) =
        HomeToolCatalog.project(input).entries.single { it.descriptor.id == id }

    @Test fun identitiesAreUniqueAndEveryGroupHasDiscoverableEntries() {
        assertEquals(HomeToolCatalog.descriptors.size, HomeToolCatalog.descriptors.map { it.id }.toSet().size)
        assertEquals(HomeToolGroup.entries.toSet(), HomeToolCatalog.descriptors.map { it.group }.toSet())
    }
    @Test fun plannedToolsCannotBeUsedEvenWithAllOwnersAvailable() {
        listOf("work.python", "work.calculate").forEach {
            assertEquals(HomeToolStatus.IN_DEVELOPMENT, entry(it).status)
            assertFalse(entry(it).canUse)
            assertEquals(HomeToolRecovery.NONE, entry(it).recovery)
        }
    }
    @Test fun absentBindingCannotAdvertiseReady() {
        val e = entry("phone.timer", ready.copy(runtimeTools = emptyMap()))
        assertFalse(e.canUse)
        assertEquals(HomeToolReason.UNAVAILABLE, e.reason)
    }
    @Test fun disabledAndDeniedBindingsKeepTheirActualRecovery() {
        listOf(HomeToolReason.DISABLED to HomeToolRecovery.SKILLS,
            HomeToolReason.ACCESS to HomeToolRecovery.PERMISSIONS,
            HomeToolReason.DEVICE to HomeToolRecovery.DEVICES).forEach { (reason, recovery) ->
            val e = entry("phone.apps", ready.copy(runtimeTools = mapOf("halo_open_app" to HomeRuntimeToolReadiness(false, reason, recovery))))
            assertFalse(e.canUse); assertEquals(reason, e.reason); assertEquals(recovery, e.recovery)
        }
    }
    @Test fun privateModeKeepsLocalToolsAndRejectsOnlineEntries() {
        val private = ready.copy(privateMode = true)
        assertTrue(entry("create.note", private).canUse)
        assertTrue(entry("phone.timer", private).canUse)
        assertEquals(HomeToolReason.POWER, entry("find.web", private).reason)
        assertEquals(HomeToolReason.POWER, entry("find.maps", private).reason)
    }
    @Test fun staleRemoteAnswerInPrivateOffersLocalProviderRecovery() {
        val e = entry("phone.timer", ready.copy(privateMode = true, answerOffDevice = true))
        assertFalse(e.canUse)
        assertEquals(HomeToolRecovery.ANSWERS, e.recovery)
    }
    @Test fun familyRestrictionBlocksRemoteButKeepsLocalEditing() {
        val restricted = ready.copy(onlineAllowedByFamily = false, answerOffDevice = true)
        assertEquals(HomeToolReason.CHILD, entry("phone.timer", restricted).reason)
        assertEquals(HomeToolReason.CHILD, entry("create.image", restricted).reason)
        assertTrue(entry("work.notebook", restricted).canUse)
    }
    @Test fun localNetworkAnswerDoesNotRequireValidatedInternet() {
        assertTrue(entry("phone.timer", ready.copy(connected = false, answerOffDevice = true)).canUse)
        assertFalse(entry("phone.timer", ready.copy(connected = false, answerOffDevice = true, answerRequiresInternet = true)).canUse)
        assertFalse(entry("find.web", ready.copy(connected = false)).canUse)
    }
    @Test fun unverifiedProviderIsLimitedAndNotAConnectionSuccess() {
        val e = entry("phone.apps", ready.copy(answerReadiness = HomeAnswerReadiness.UNCHECKED))
        assertEquals(HomeToolStatus.LIMITED, e.status)
        assertEquals(HomeToolReason.UNCHECKED, e.reason)
    }
    @Test fun imageEditorIsLimitedAndMissingConfigurationHasEditorRecovery() {
        assertEquals(HomeToolReason.IMAGE_REVIEW, entry("create.image").reason)
        assertEquals(HomeToolRecovery.NOTEBOOK, entry("create.image", ready.copy(imageConfigured = false)).recovery)
        assertEquals(HomeToolAction.NOTEBOOK, entry("create.image").descriptor.action)
    }
    @Test fun mapsRequireHandlerAndOnlyPrepareAHandoff() {
        assertEquals(HomeToolReason.HANDOFF, entry("find.maps").reason)
        assertEquals(HomeToolAction.MAPS_DRAFT, entry("find.maps").descriptor.action)
        val missing = entry("find.maps", ready.copy(mapHandler = false))
        assertFalse(missing.canUse); assertEquals(HomeToolRecovery.ANDROID_APPS, missing.recovery)
    }
    @Test fun webUsesCanonicalOwnerPreflightAndResearchUsesPolicy() {
        assertEquals(HomeToolReason.WEB_KEY, entry("find.web", ready.copy(webProblem = OnlineProblem.KEY_REQUIRED)).reason)
        assertEquals(HomeToolReason.RESEARCH_OFF, entry("find.research", ready.copy(researchEnabled = false)).reason)
    }
    @Test fun stoppingVoiceIsAvailableEvenAfterAccessAndProviderAreRevoked() {
        val e = entry("phone.voice", HomeToolCatalogInput(voiceModeActive = true))
        assertTrue(e.canUse)
        assertEquals(HomeToolStatus.READY, e.status)
        assertFalse(entry("phone.voice", HomeToolCatalogInput()).canUse)
    }
    @Test fun repeatIsOnlyPresentWhenCurrentConversationHasAReply() {
        assertFalse(HomeToolCatalog.project(ready).entries.any { it.descriptor.id == "phone.repeat" })
        assertTrue(entry("phone.repeat", ready.copy(repeatVoiceAvailable = true)).canUse)
    }
    @Test fun busyRuntimeCannotBeCancelledByStartingVoiceFromTools() {
        assertFalse(entry("phone.voice", ready.copy(voiceStartAllowed = false)).canUse)
        assertTrue(entry("phone.voice", ready.copy(voiceStartAllowed = false, voiceModeActive = true)).canUse)
        assertFalse(homeVoiceStartAllowed(com.halo.sdk.provider.CognitiveSessionState.Reasoning("fixture"),
            com.halo.sdk.runtime.SpeechSessionState.Idle, false, false, false))
        assertFalse(homeVoiceStartAllowed(com.halo.sdk.provider.CognitiveSessionState.Idle,
            com.halo.sdk.runtime.SpeechSessionState.Speaking, false, false, false))
        assertFalse(homeVoiceStartAllowed(com.halo.sdk.provider.CognitiveSessionState.Idle,
            com.halo.sdk.runtime.SpeechSessionState.Idle, true, false, false))
        assertTrue(homeVoiceStartAllowed(com.halo.sdk.provider.CognitiveSessionState.Idle,
            com.halo.sdk.runtime.SpeechSessionState.PassiveListening, false, false, false))
    }
    @Test fun importsNeverTreatUnsupportedImagePdfOrAudioAsReadableChatContext() {
        assertEquals(HomeImportedSourceKind.IMAGE, homeImportedSourceKind("image/png"))
        assertEquals(HomeImportedSourceKind.OTHER, homeImportedSourceKind("image/svg+xml"))
        assertEquals(HomeImportedSourceKind.TEXT, homeImportedSourceKind("application/json"))
        listOf("application/pdf", "audio/wav", "application/zip").forEach {
            assertFalse(HomeImportedSource("saved", "Source", homeImportedSourceKind(it)).canAttach)
        }
    }
    @Test fun studioFileCanvasAndStudyEntriesBindFunctionalOperationsAndUnavailableOwnersHaveRecovery() {
        val expected = mapOf("create.studio" to "halo_studio_create", "create.document" to "halo_text_create",
            "work.files" to "halo_text_edit", "work.canvas" to "halo_canvas_add_text", "work.study" to "halo_study_with_halo")
        expected.forEach { (id, operation) ->
            val current = entry(id)
            assertTrue(operation in current.descriptor.toolIds)
            assertFalse(current.descriptor.editorOnly)
            assertEquals(HomeToolStatus.READY, current.status)
            assertFalse(entry(id, ready.copy(runtimeTools = emptyMap())).canUse)
        }
    }

}
