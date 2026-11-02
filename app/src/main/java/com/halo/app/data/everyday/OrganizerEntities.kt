package com.halo.app.data.everyday

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "organizer_records", indices = [Index(value = ["kind", "deletedAt"]), Index(value = ["parentId"])])
data class OrganizerEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val parentId: String?,
    val version: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val body: String,
)

@Entity(tableName = "organizer_occurrences", primaryKeys = ["recordId", "originalStart"],
    foreignKeys = [ForeignKey(entity = OrganizerEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)])
data class OrganizerOccurrenceEntity(val recordId: String, val originalStart: Long, val body: String)

@Entity(tableName = "organizer_deliveries", indices = [Index(value = ["recordId"])],
    foreignKeys = [ForeignKey(entity = OrganizerEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)])
data class OrganizerDeliveryEntity(@PrimaryKey val occurrenceKey: String, val recordId: String,
    val recordVersion: Long, val triggerAt: Long, val state: String, val updatedAt: Long,
    val detail: String?, val snoozedUntil: Long?, val snoozeElapsedDeadlineMs: Long?, val snoozeBootId: String?)

/** Transactional intent to reconcile OS schedules. Removed only after reconciling that exact version. */
@Entity(tableName = "organizer_schedule_outbox",
    foreignKeys = [ForeignKey(entity = OrganizerEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)])
data class OrganizerScheduleEntity(@PrimaryKey val recordId: String, val version: Long)

@Entity(tableName = "organizer_changes", indices = [Index(value = ["at"])])
data class OrganizerChangeEntity(@PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val recordId: String, val version: Long, val action: String, val at: Long)

@Dao
interface OrganizerDao {
    @Query("SELECT id,version FROM organizer_records ORDER BY id") suspend fun authorityRows(): List<OrganizerAuthorityRow>
    @Query("SELECT * FROM organizer_changes ORDER BY sequence DESC LIMIT 100") fun observeChanges(): Flow<List<OrganizerChangeEntity>>
    @Query("DELETE FROM organizer_records") suspend fun clearRecords()
    @Query("DELETE FROM organizer_changes") suspend fun clearChanges()
    @Query("DELETE FROM organizer_deliveries WHERE recordId=:id") suspend fun clearReceipts(id: String)
    @Query("SELECT * FROM organizer_records WHERE deletedAt IS NULL ORDER BY updatedAt DESC, id")
    fun observe(): Flow<List<OrganizerEntity>>
    @Query("SELECT * FROM organizer_records WHERE :includeDeleted OR deletedAt IS NULL ORDER BY updatedAt DESC, id")
    suspend fun records(includeDeleted: Boolean): List<OrganizerEntity>
    @Query("SELECT * FROM organizer_records WHERE id = :id") suspend fun get(id: String): OrganizerEntity?
    @Query("SELECT COUNT(*) FROM organizer_records") suspend fun count(): Int
    @Insert suspend fun insert(record: OrganizerEntity)
    @Update suspend fun update(record: OrganizerEntity): Int
    @Query("DELETE FROM organizer_records WHERE id = :id AND version = :version AND deletedAt IS NOT NULL")
    suspend fun purgeDeleted(id: String, version: Long): Int
    @Query("SELECT * FROM organizer_occurrences WHERE recordId = :id ORDER BY originalStart")
    suspend fun overrides(id: String): List<OrganizerOccurrenceEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putOverride(value: OrganizerOccurrenceEntity)
    @Query("DELETE FROM organizer_occurrences WHERE recordId = :id") suspend fun clearOverrides(id: String)
    @Query("DELETE FROM organizer_occurrences WHERE recordId = :id AND originalStart = :start")
    suspend fun removeOverride(id: String, start: Long): Int
    @Query("SELECT * FROM organizer_deliveries WHERE recordId = :id ORDER BY triggerAt")
    suspend fun receipts(id: String): List<OrganizerDeliveryEntity>
    @Query("SELECT * FROM organizer_deliveries ORDER BY triggerAt")
    fun observeReceipts(): Flow<List<OrganizerDeliveryEntity>>
    @Query("SELECT * FROM organizer_deliveries ORDER BY occurrenceKey")
    suspend fun allReceipts(): List<OrganizerDeliveryEntity>
    @Query("SELECT * FROM organizer_deliveries WHERE occurrenceKey = :key") suspend fun receipt(key: String): OrganizerDeliveryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putReceipt(value: OrganizerDeliveryEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun enqueue(value: OrganizerScheduleEntity)
    @Query("SELECT recordId FROM organizer_schedule_outbox ORDER BY recordId") suspend fun pending(): List<String>
    @Query("DELETE FROM organizer_schedule_outbox WHERE recordId = :id AND version = :version")
    suspend fun acknowledge(id: String, version: Long): Int
    @Insert suspend fun change(value: OrganizerChangeEntity)
    @Query("SELECT * FROM organizer_changes WHERE at >= :since ORDER BY sequence") suspend fun changes(since: Long): List<OrganizerChangeEntity>
    @Query("DELETE FROM organizer_changes WHERE at < :before") suspend fun pruneChanges(before: Long)
}

data class OrganizerAuthorityRow(val id: String,val version: Long)
