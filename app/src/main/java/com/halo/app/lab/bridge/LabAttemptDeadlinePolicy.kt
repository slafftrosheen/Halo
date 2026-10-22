package com.halo.app.lab.bridge

/** Validates the bounded stage budgets shared by Lab's persisted real plan and Halo's worker. */
internal object LabAttemptDeadlinePolicy {
    const val MIN_STAGE_MS = 1_000L
    const val MAX_STAGE_MS = 30 * 60_000L
    const val MAX_ATTEMPT_MS = 3 * MAX_STAGE_MS
    const val LEGACY_ATTEMPT_MS = 60_000L

    fun totalMs(loadMs: Long, executeMs: Long, unloadMs: Long): Long? {
        if (listOf(loadMs, executeMs, unloadMs).any { it !in MIN_STAGE_MS..MAX_STAGE_MS }) return null
        return runCatching { Math.addExact(Math.addExact(loadMs, executeMs), unloadMs) }
            .getOrNull()
            ?.takeIf { it <= MAX_ATTEMPT_MS }
    }

    /** A reboot resets elapsedRealtime; treat that as an expired deadline, never a fresh run. */
    fun deadlineElapsedMs(acceptedAtElapsedMs: Long, durationMs: Long, nowElapsedMs: Long): Long {
        if (acceptedAtElapsedMs <= 0L || durationMs !in 1L..MAX_ATTEMPT_MS || nowElapsedMs < acceptedAtElapsedMs) {
            return nowElapsedMs
        }
        return runCatching { Math.addExact(acceptedAtElapsedMs, durationMs) }
            .getOrDefault(nowElapsedMs)
    }
}
