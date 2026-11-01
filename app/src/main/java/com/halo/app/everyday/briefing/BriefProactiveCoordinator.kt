package com.halo.app.everyday.briefing

import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Once per period through the canonical attention owner; stable identity survives a pre-submit crash. */
class BriefProactiveCoordinator(private val repository: BriefRepository, private val briefs: EverydayBriefService,
    private val deliver: suspend (EverydayBrief) -> String, private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val canonicalOutcome: suspend (String) -> String? = { null }) {
    private val lock = Mutex()
    /** Read persisted canonical receipts, never a possibly uninitialized presentation StateFlow. */
    suspend fun reconcile() {
        val now = clock.instant()
        repository.deliveries(now.minusSeconds(2 * 86400)).filter { it.state in setOf("CLAIMED", "RETRYABLE", "QUEUED", "COMPLETION_UNKNOWN") }.forEach { row ->
            val outcome = canonicalOutcome(row.documentId)
            if (outcome != null) repository.finishDelivery(row.key, row.documentId, outcome)
            else if (row.state == "CLAIMED" && row.at.plusSeconds(300) <= now)
                repository.finishDelivery(row.key, row.documentId, "RETRYABLE")
        }
    }
    suspend fun runDue(): EverydayBrief? = lock.withLock {
        reconcile()
        val settings = repository.settings()
        val now = clock.instant(); val deviceZone = zone()
        val since = now.minusSeconds(2 * 86400)
        val done = repository.deliveryKeys(since)
        val period = BriefSchedulePolicy.due(settings, now, deviceZone, done) ?: return@withLock null
        val date = now.atZone(deviceZone).toLocalDate()
        val key = BriefSchedulePolicy.key(date, deviceZone, period)
        val previous = repository.deliveries(since).firstOrNull { it.key == key }
        if (previous?.state == "CLAIMED") return@withLock null
        val document = if (previous == null) briefs.build(period) else briefs.visible(previous.documentId)
        if (document == null) {
            repository.finishDelivery(key, requireNotNull(previous).documentId, "CANCELLED")
            return@withLock null
        }
        // Recovery preserves the original document and one-hour delivery expiry.
        if (now >= document.generatedAt.plusSeconds(3600)) {
            if (previous != null) repository.finishDelivery(key, previous.documentId, "EXPIRED")
            return@withLock document
        }
        val current = repository.settings(); val actualNow = clock.instant()
        if (current.version != settings.version || deviceZone != zone() || document.date != actualNow.atZone(deviceZone).toLocalDate() ||
            BriefSchedulePolicy.due(current, actualNow, deviceZone, done) != period) return@withLock document
        val claimed = if (previous == null) repository.claimDelivery(key, document.id, actualNow)
            else repository.reclaimDelivery(previous, actualNow)
        if (!claimed) return@withLock document
        try {
            val latest = repository.settings()
            val state = if (latest.version != current.version || latest.quiet(clock.instant().atZone(zone()).toLocalTime())) "CANCELLED"
                else canonicalOutcome(document.id) ?: deliver(document)
            repository.finishDelivery(key, document.id, state)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { repository.finishDelivery(key, document.id, canonicalOutcome(document.id) ?: "RETRYABLE") }
            throw cancelled
        } catch (_: Exception) { repository.finishDelivery(key, document.id, canonicalOutcome(document.id) ?: "RETRYABLE") }
        document
    }
}
