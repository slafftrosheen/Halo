package com.halo.app.data.everyday

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.organizer.*
import kotlinx.coroutines.flow.map

class RoomWorldClockRepository(private val database: AppDatabase, private val clock: java.time.Clock = java.time.Clock.systemUTC(),
    private val changed: () -> Unit = {}) : WorldClockRepository {
    private val dao get() = database.worldClockDao()
    private fun WorldClockEntity.record() = WorldClockRecord(id, label, zoneId, cityId, position, version, deletedAt?.let(java.time.Instant::ofEpochMilli))
    private fun WorldClockRecord.entity() = WorldClockEntity(id, label, zoneId, cityId, position, version, deletedAt?.toEpochMilli())
    override fun observe() = dao.observe().map { rows -> rows.map { it.record() } }
    override suspend fun all() = dao.all().map { it.record() }
    override suspend fun get(id: String) = dao.get(id)?.record()
    override suspend fun save(value: WorldClockRecord, expectedVersion: Long?): WorldClockRecord {
        val saved = database.withTransaction {
            dao.prune(clock.millis() - 30L * 86400 * 1000)
            require(value.deletedAt == null)
            val current = dao.get(value.id)
            if (current == null) {
                if (expectedVersion != null) throw OrganizerConflict("Clock no longer exists")
                val rows = dao.all(); require(rows.size < 24) { "Clock limit reached; remove a clock first" }
                value.copy(version = 1, position = rows.size).also { com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); dao.insert(it.entity()) }
            } else {
                if (expectedVersion != current.version || current.deletedAt != null) throw OrganizerConflict("Clock changed; reopen its settings")
                value.copy(version = current.version + 1, position = current.position).also { com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); check(dao.update(it.entity()) == 1) }
            }
        }
        runCatching(changed); return saved
    }
    override suspend fun remove(id: String, expectedVersion: Long): WorldClockRecord {
        val removed = database.withTransaction {
            dao.prune(clock.millis() - 30L * 86400 * 1000)
            val current = dao.get(id) ?: throw OrganizerConflict("Clock no longer exists")
            if (current.version != expectedVersion || current.deletedAt != null) throw OrganizerConflict("Clock changed or no longer exists")
            val deleted = current.copy(deletedAt = clock.millis(), version = current.version + 1)
            com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); check(dao.update(deleted) == 1)
            dao.all().forEachIndexed { index, row -> if (row.position != index) dao.update(row.copy(position = index, version = row.version + 1)) }
            deleted.record()
        }
        runCatching(changed); return removed
    }
    override suspend fun restore(id: String, deletedVersion: Long): WorldClockRecord {
        val restored = database.withTransaction {
            val current = dao.get(id) ?: throw OrganizerConflict("Clock no longer exists")
            if (current.version != deletedVersion || current.deletedAt == null) throw OrganizerConflict("Clock changed; this Undo has expired")
            val rows = dao.all(); require(rows.size < 24) { "Clock limit reached" }
            current.copy(deletedAt = null, version = current.version + 1, position = rows.size).also { com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); check(dao.update(it) == 1) }.record()
        }
        runCatching(changed); return restored
    }
    override suspend fun reorder(ids: List<String>, expectedVersions: Map<String, Long>) {
        database.withTransaction {
            val rows = dao.all().associateBy { it.id }
            if (ids.size != ids.distinct().size || ids.toSet() != rows.keys || expectedVersions != rows.mapValues { it.value.version })
                throw OrganizerConflict("Clock list changed; reopen arrangement")
            ids.forEachIndexed { index, id -> val row = rows.getValue(id)
                if (row.position != index) {
                    com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
                    check(dao.update(row.copy(position = index, version = row.version + 1)) == 1)
                }
            }
        }
        runCatching(changed)
    }
}
