package com.halo.app.ui.presence

import com.halo.app.R

/** Only ongoing canonical voice/task states migrate here. Result/decision one-shots need IDs. */
internal fun zone5OrbMotionResource(signal: HaloSignalPresentation, size: Float, dark: Boolean,
    motionAllowed: Boolean): Int? {
    if (!motionAllowed || size !in setOf(64f, 72f)) return null
    return when (signal.presenceArtwork()) {
        PresenceArtwork.CAPTURING_VOICE -> if (dark) R.raw.halo5_motion_orb_listening_dark else R.raw.halo5_motion_orb_listening_light
        PresenceArtwork.THINKING, PresenceArtwork.PLANNING, PresenceArtwork.ACTING ->
            if (signal.progress != null) null else if (dark) R.raw.halo5_motion_orb_thinking_dark else R.raw.halo5_motion_orb_thinking_light
        PresenceArtwork.SPEAKING -> if (dark) R.raw.halo5_motion_orb_speaking_dark else R.raw.halo5_motion_orb_speaking_light
        else -> null
    }
}
