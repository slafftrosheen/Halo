package com.halo.app.ui.presence

/** The user's preferred thick-ring family at the authored Zone sizes. Result one-shots still need IDs. */
internal fun zone5OrbMotionAsset(signal: HaloSignalPresentation, size: Float, dark: Boolean,
    motionAllowed: Boolean, kids: Boolean = false): String? {
    if (!motionAllowed || size !in setOf(64f, 72f)) return null
    val artwork = when (signal.presenceArtwork()) {
        PresenceArtwork.IDLE -> PresenceArtwork.IDLE
        PresenceArtwork.CAPTURING_VOICE -> PresenceArtwork.CAPTURING_VOICE
        PresenceArtwork.THINKING, PresenceArtwork.PLANNING, PresenceArtwork.ACTING ->
            if (signal.progress != null) null else if (signal.presenceArtwork() == PresenceArtwork.ACTING) PresenceArtwork.ACTING else PresenceArtwork.THINKING
        PresenceArtwork.SPEAKING -> PresenceArtwork.SPEAKING
        else -> null
    } ?: return null
    val prefix = if (kids) "halo_kids_orb_" else "halo_orb_"
    return "halo/$prefix${artwork.key}_${if (dark) "dark" else "light"}.json"
}
