package com.halo.app.nativeactions

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class HaloVaultCopyLifetimeTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val issued = VaultCopyLifetime(createdAt = 50 * day, elapsedAt = day, boot = 12, days = 7)

    @Test fun eitherClockCanExpireAReadDuringTheOriginalBoot() {
        assertEquals(7 * day, issued.remaining(50 * day, day, 12))
        assertEquals(0L, issued.remaining(56 * day, 8 * day, 12))
        assertEquals(0L, issued.remaining(57 * day, 6 * day, 12))
        assertEquals(0L, issued.remaining(49 * day, day, 12))
    }

    @Test fun restartUsesTheOriginalWallClockDeadline() {
        assertEquals(day, issued.remaining(56 * day, 100, 13))
        assertEquals(0L, issued.remaining(57 * day, 100, 13))
    }
}
