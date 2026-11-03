package com.halo.app.data.everyday

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.briefing.*
import java.time.*
import kotlinx.coroutines.flow.map

class RoomBriefRepository(private val db: AppDatabase, private val clock: Clock = Clock.systemUTC(),
    private val settingsChanged: () -> Unit = {},private val beforePublish: suspend (EverydayBrief)->Unit = {}) : BriefRepository {
    private val dao get() = db.briefDao()
    override fun observe() = dao.observe().map { rows -> rows.map { BriefCodec.document(it.body) } }
    override fun observeSettings() = dao.observeSettings().map { it?.let { row -> BriefCodec.settings(row.body) } ?: BriefSettings() }
    override suspend fun recent(limit: Int): List<EverydayBrief> { require(limit in 1..60); return dao.recent(limit).map { BriefCodec.document(it.body) } }
    override suspend fun get(id: String) = dao.get(id)?.let { BriefCodec.document(it.body) }
    private fun baselineKey(date: LocalDate, zone: ZoneId, audience: BriefAudience) = "$date|${zone.id}|${audience.name}"
    override suspend fun baseline(date: LocalDate, zone: ZoneId, audience: BriefAudience) = dao.baseline(baselineKey(date, zone, audience))?.let { BriefCodec.baseline(it.body) }
    override suspend fun publish(projection: BriefProjection): EverydayBrief = db.withTransaction {
        val document = projection.document
        com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
        beforePublish(document)
        com.halo.app.everyday.EverydayEffectAuthority.requireCurrent()
        dao.get(document.id)?.let { return@withTransaction BriefCodec.document(it.body) }
        if (document.period == BriefPeriod.MORNING) {
            val baseline = projection.baseline
            require(document.date == baseline.date && document.zoneId == baseline.zoneId && document.audience == baseline.audience)
            val encoded = BriefCodec.encode(baseline); BriefCodec.baseline(encoded)
            dao.baseline(BriefBaselineEntity(baselineKey(baseline.date, ZoneId.of(baseline.zoneId), baseline.audience), baseline.capturedAt.toEpochMilli(), encoded))
        }
        val encoded = BriefCodec.encode(document); BriefCodec.document(encoded)
        dao.insert(BriefEntity(document.id, document.generatedAt.toEpochMilli(), encoded))
        val before = clock.instant().minusSeconds(30 * 86400).toEpochMilli()
        dao.prune(before); dao.pruneBaselines(before); dao.pruneDeliveries(before)
        com.halo.app.everyday.EverydayEffectAuthority.requireCurrent()
        document
    }
    override suspend fun remove(id: String) = db.withTransaction { com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); com.halo.app.everyday.EverydayReadAuthority.invalidate(); dao.remove(id) }
    override suspend fun clear() = db.withTransaction { com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); com.halo.app.everyday.EverydayReadAuthority.invalidate(); dao.clearBriefs(); dao.clearBaselines(); dao.clearDeliveries() }
    override suspend fun settings() = dao.settings()?.let { BriefCodec.settings(it.body) } ?: BriefSettings()
    override suspend fun deliveryKeys(since: Instant) = dao.deliveries(since.toEpochMilli())
        .filter { it.state !in setOf("CLAIMED", "RETRYABLE") }.map { it.key }.toSet()
    override suspend fun deliveries(since: Instant) = dao.deliveries(since.toEpochMilli()).map {
        BriefDeliveryRecord(it.key, it.documentId, Instant.ofEpochMilli(it.at), it.state)
    }
    override suspend fun claimDelivery(key: String, documentId: String, at: Instant): Boolean = db.withTransaction {
        require(key.length <= 200 && dao.get(documentId) != null)
        dao.claim(BriefDeliveryEntity(key, documentId, at.toEpochMilli(), "CLAIMED")) != -1L
    }
    override suspend fun reclaimDelivery(previous: BriefDeliveryRecord, at: Instant): Boolean =
        dao.reclaim(previous.key, previous.documentId, previous.at.toEpochMilli(), at.toEpochMilli()) == 1
    override suspend fun finishDelivery(key: String, documentId: String, state: String) {
        require(state in setOf("DELIVERED", "DISPATCHED", "PLAYBACK_COMPLETED", "COMPLETION_UNKNOWN", "QUEUED", "SURFACED_SILENTLY", "SUPPRESSED", "EXPIRED", "FAILED", "CANCELLED", "RETRYABLE"))
        dao.delivered(key, documentId, state)
    }
    override suspend fun saveSettings(value: BriefSettings, expectedVersion: Long): BriefSettings {
        val saved = db.withTransaction {
            val current = settings()
            check(current.version == expectedVersion) { "Brief settings changed; reopen settings" }
            val next = value.copy(version = current.version + 1)
            val encoded = BriefCodec.encode(next); BriefCodec.settings(encoded)
            com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
            com.halo.app.everyday.EverydayReadAuthority.invalidate()
            dao.settings(BriefSettingsEntity(version = next.version, body = encoded))
            next
        }
        runCatching(settingsChanged)
        return saved
    }
}
