package com.halo.app.ui.presence

/** The user's preferred thick-ring family at the authored Zone sizes. Result one-shots still need IDs. */
internal fun zone5OrbMotionAsset(signal: HaloSignalPresentation, size: Float, dark: Boolean,
    motionAllowed: Boolean, kids: Boolean = false, dock: Boolean = false): String? {
    // The dock supplies 48dp in the keyboard row, 56dp on regular pages and 60dp for Kids.
    val fits = if (dock) size in 48f..72f else size == 64f || size == 72f
    if (!motionAllowed || !fits) return null
    val artwork = when (signal.presenceArtwork()) {
        PresenceArtwork.IDLE -> PresenceArtwork.IDLE
        PresenceArtwork.LISTENING -> PresenceArtwork.LISTENING.takeIf { dock }
        PresenceArtwork.CAPTURING_VOICE -> PresenceArtwork.CAPTURING_VOICE
        PresenceArtwork.CAPTURING_VISION -> PresenceArtwork.CAPTURING_VISION.takeIf { dock }
        PresenceArtwork.THINKING, PresenceArtwork.PLANNING, PresenceArtwork.ACTING ->
            if (signal.progress != null) null else if (signal.presenceArtwork() == PresenceArtwork.ACTING) PresenceArtwork.ACTING else PresenceArtwork.THINKING
        PresenceArtwork.SPEAKING -> PresenceArtwork.SPEAKING
        else -> null
    } ?: return null
    val prefix = if (kids) "halo_kids_orb_" else "halo_orb_"
    return "halo/$prefix${artwork.key}_${if (dark) "dark" else "light"}.json"
}
