package com.halo.app.everyday.briefing

import java.time.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BriefNoticeAuthorityTest {
    @Test fun optOutDuringSuspendedValidationPreventsFinalDispatch() = runBlocking {
        var settings = BriefSettings(proactive = true, quietEnabled = false)
        val entered = CompletableDeferred<Unit>(); val resume = CompletableDeferred<Unit>()
        val authority = BriefNoticeAuthority({ settings }, { entered.complete(Unit); resume.await(); true })
        val pending = async { authority.allowed("brief", null) }
        entered.await(); settings = settings.copy(proactive = false, version = 1); resume.complete(Unit)
        assertFalse(pending.await())
    }
    @Test fun quietTimeAndExpiryAreCheckedAfterContentRead() = runBlocking {
        var now = Instant.parse("2026-11-01T21:59:59Z")
        val clock = object : Clock() {
            override fun instant() = now
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
        }
        val settings = BriefSettings(proactive = true)
        val entered = CompletableDeferred<Unit>(); val resume = CompletableDeferred<Unit>()
        val authority = BriefNoticeAuthority({ settings }, { entered.complete(Unit); resume.await(); true }, clock, { ZoneOffset.UTC })
        val pending = async { authority.allowed("brief", now.plusSeconds(1)) }
        entered.await(); now = now.plusSeconds(2); resume.complete(Unit)
        assertFalse(pending.await())
    }
}
