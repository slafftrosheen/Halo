package com.halo.app.ui.presence

import com.halo.app.ui.screens.home.HomeToolCatalog
import com.halo.app.ui.screens.home.HomeToolGroup
import org.junit.Assert.*
import org.junit.Test

class HaloOrbActionsTest {
    @Test fun idleOffersTalkWithoutClaimingCapture() {
        assertEquals(HaloOrbInteraction(HaloOrbPrimary.TALK, false), haloOrbInteraction(false, false, false, false, false, false))
    }
    @Test fun everyActiveInputAndWorkPhaseOffersStop() {
        listOf(
            haloOrbInteraction(true, false, false, false, false, false),
            haloOrbInteraction(false, true, false, false, false, false),
            haloOrbInteraction(false, false, true, false, false, false),
            haloOrbInteraction(false, false, false, false, true, false),
        ).forEach { assertEquals(HaloOrbInteraction(HaloOrbPrimary.STOP, true), it) }
    }
    @Test fun speakingOffersInterruptionWhileReviewKeepsCancellationAvailable() {
        assertEquals(HaloOrbInteraction(HaloOrbPrimary.INTERRUPT, true), haloOrbInteraction(false, false, false, true, true, false))
        assertEquals(HaloOrbInteraction(HaloOrbPrimary.REVIEW, true), haloOrbInteraction(true, true, false, true, true, true))
    }
    @Test fun aRequestedMicrophoneIsWaitingUntilCaptureActuallyOpens() {
        val source = HaloSignalPresentation(state = HaloSignalState.LISTENING, artwork = PresenceArtwork.LISTENING)
        val requested = haloOrbInputPresentation(source, false, true, false)
        assertEquals(HaloSignalState.WAITING, requested.state)
        assertNotEquals("Listening…", requested.label)
        val active = haloOrbInputPresentation(source, true, true, false)
        assertEquals(HaloSignalState.CAPTURING, active.state)
        assertEquals(PresenceArtwork.CAPTURING_VOICE, active.artwork)
    }
    @Test fun recognitionAndClosedCaptureCannotKeepAFalseLiveMicrophone() {
        val source = HaloSignalPresentation(state = HaloSignalState.CAPTURING, artwork = PresenceArtwork.CAPTURING_VOICE)
        assertEquals(HaloSignalState.THINKING, haloOrbInputPresentation(source, false, true, true).state)
        assertEquals(HaloSignalState.IDLE, haloOrbInputPresentation(source, false, false, false).state)
    }
    @Test fun offlineAndFailureRemainActualSignalsWhenCaptureIsClosed() {
        listOf(HaloSignalState.OFFLINE, HaloSignalState.ERROR).forEach { state ->
            val signal = HaloSignalPresentation(state = state)
            assertEquals(signal, haloOrbInputPresentation(signal, false, false, false))
        }
    }
    @Test fun shortcutDecodeIsBoundedAndEmptyIsAnExplicitPreference() {
        assertEquals(HaloOrbShortcut.defaults, HaloOrbShortcut.decode(null))
        assertEquals(emptySet<HaloOrbShortcut>(), HaloOrbShortcut.decode(emptySet()))
        assertEquals(setOf(HaloOrbShortcut.PHONE), HaloOrbShortcut.decode(setOf("PHONE", "execute/arbitrary")))
    }
    @Test fun notebookShortcutUsesAnExistingDescriptorAndEveryGroupIsCanonical() {
        assertEquals(HomeToolGroup.WORK, HaloOrbShortcut.NOTEBOOK.group)
        assertTrue(HomeToolCatalog.descriptors.any { it.id == HaloOrbShortcut.NOTEBOOK.entry && it.group == HaloOrbShortcut.NOTEBOOK.group })
        HaloOrbShortcut.entries.forEach { shortcut -> assertTrue(HomeToolCatalog.descriptors.any { it.group == shortcut.group }) }
    }
}
