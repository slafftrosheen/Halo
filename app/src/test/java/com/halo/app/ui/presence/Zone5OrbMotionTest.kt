package com.halo.app.ui.presence

import com.halo.app.R
import org.junit.Assert.*
import org.junit.Test

class Zone5OrbMotionTest {
    private val capture = HaloSignalPresentation(state = HaloSignalState.CAPTURING, label = "Listening")
    @Test fun authoredSmallSizesAnimateOnlyActiveCaptureAndRespectTheme() {
        for (size in listOf(64f, 72f)) {
            assertEquals(R.raw.halo5_motion_orb_listening_dark, zone5OrbMotionResource(capture, size, true, true))
            assertEquals(R.raw.halo5_motion_orb_listening_light, zone5OrbMotionResource(capture, size, false, true))
        }
        assertNull(zone5OrbMotionResource(capture.copy(state = HaloSignalState.IDLE), 72f, true, true))
        assertNull(zone5OrbMotionResource(capture.copy(state = HaloSignalState.LISTENING), 72f, true, true))
    }
    @Test fun compactAndRestrictedStatesStayStaticWithoutChangingLegacyThreshold() {
        for (size in listOf(48f, 56f, 80f)) assertNull(zone5OrbMotionResource(capture, size, true, true))
        assertNull(zone5OrbMotionResource(capture, 72f, true, false))
        assertFalse(PresenceArtwork.CAPTURING_VOICE.motionPolicy(72f, false, true).animate)
        assertTrue(PresenceArtwork.CAPTURING_VOICE.motionPolicy(80f, false, true).animate)
    }
    @Test fun measuredProgressAndResultStatesNeverAutoplayIndeterminateOrSuccessMotion() {
        val thinking = HaloSignalPresentation(state = HaloSignalState.THINKING, label = "Thinking")
        assertNotNull(zone5OrbMotionResource(thinking, 64f, true, true))
        assertNull(zone5OrbMotionResource(thinking.copy(progress = .4f), 64f, true, true))
        assertNull(zone5OrbMotionResource(thinking.copy(artwork = PresenceArtwork.SUCCESS), 72f, true, true))
    }
}
