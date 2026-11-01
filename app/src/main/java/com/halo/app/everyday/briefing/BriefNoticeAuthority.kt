package com.halo.app.everyday.briefing

import java.time.*

/** Re-check opt-in and quiet time after suspending content validation, including queued releases. */
class BriefNoticeAuthority(private val settings: suspend () -> BriefSettings,
    private val available: suspend (String) -> Boolean, private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    suspend fun allowed(documentId: String, expiresAt: Instant?): Boolean {
        val before = settings()
        if (!before.proactive || !available(documentId)) return false
        val current = settings()
        val now = clock.instant()
        return current.version == before.version && current.proactive &&
            !current.quiet(now.atZone(zone()).toLocalTime()) && (expiresAt == null || now < expiresAt)
    }
}
