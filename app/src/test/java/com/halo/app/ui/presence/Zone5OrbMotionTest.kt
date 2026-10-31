package com.halo.app.ui.presence

import org.junit.Assert.*
import org.junit.Test

class Zone5OrbMotionTest {
    private val capture = HaloSignalPresentation(state = HaloSignalState.CAPTURING, label = "Listening")
    @Test fun authoredSmallSizesUseTheSameThickFamilyAsTheLargeOrb() {
        for (size in listOf(64f, 72f)) {
            assertEquals("halo/halo_orb_capturing_voice_dark.json", zone5OrbMotionAsset(capture, size, true, true))
            assertEquals("halo/halo_orb_capturing_voice_light.json", zone5OrbMotionAsset(capture, size, false, true))
            assertEquals("halo/halo_kids_orb_capturing_voice_dark.json", zone5OrbMotionAsset(capture, size, true, true, kids = true))
        }
        assertEquals("halo/halo_orb_idle_dark.json", zone5OrbMotionAsset(capture.copy(state = HaloSignalState.IDLE), 72f, true, true))
        assertNull(zone5OrbMotionAsset(capture.copy(state = HaloSignalState.LISTENING), 72f, true, true))
    }
    @Test fun compactAndRestrictedStatesStayStaticWithoutChangingLegacyThreshold() {
        for (size in listOf(48f, 56f, 80f)) assertNull(zone5OrbMotionAsset(capture, size, true, true))
        assertNull(zone5OrbMotionAsset(capture, 72f, true, false))
        assertFalse(PresenceArtwork.CAPTURING_VOICE.motionPolicy(72f, false, true).animate)
        assertTrue(PresenceArtwork.CAPTURING_VOICE.motionPolicy(80f, false, true).animate)
    }
    @Test fun measuredProgressAndResultStatesNeverAutoplayIndeterminateOrSuccessMotion() {
        val thinking = HaloSignalPresentation(state = HaloSignalState.THINKING, label = "Thinking")
        assertEquals("halo/halo_orb_thinking_dark.json", zone5OrbMotionAsset(thinking, 64f, true, true))
        assertNull(zone5OrbMotionAsset(thinking.copy(progress = .4f), 64f, true, true))
        assertNull(zone5OrbMotionAsset(thinking.copy(artwork = PresenceArtwork.SUCCESS), 72f, true, true))
    }
}
