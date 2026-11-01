package com.halo.app.everyday.organizer

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

data class WorldClockRecord(val id: String, val label: String, val zoneId: String, val cityId: String? = null,
    val position: Int = 0, val version: Long = 1, val deletedAt: Instant? = null) {
    init {
        require(id.matches(Regex("[A-Za-z0-9_.:-]{1,160}")) && label.isNotBlank() && label.length <= 120)
        require(cityId == null || cityId.matches(Regex("[0-9]{1,12}")))
        require(position in 0..23 && version > 0); ZoneId.of(zoneId)
    }
    fun at(instant: Instant) = instant.atZone(ZoneId.of(zoneId))
}
interface WorldClockRepository {
    fun observe(): Flow<List<WorldClockRecord>>
    suspend fun all(): List<WorldClockRecord>
    suspend fun get(id: String): WorldClockRecord?
    suspend fun save(value: WorldClockRecord, expectedVersion: Long?): WorldClockRecord
    suspend fun remove(id: String, expectedVersion: Long): WorldClockRecord
    suspend fun restore(id: String, deletedVersion: Long): WorldClockRecord
    /** Exact record versions fence reorder against concurrent settings/voice/widget changes. */
    suspend fun reorder(ids: List<String>, expectedVersions: Map<String, Long>)
}
