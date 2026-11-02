package com.halo.app.data.everyday

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "everyday_briefs", indices = [Index(value = ["generatedAt"])])
data class BriefEntity(@PrimaryKey val id: String, val generatedAt: Long, val body: String)
@Entity(tableName = "everyday_brief_baselines", indices = [Index(value = ["capturedAt"])])
data class BriefBaselineEntity(@PrimaryKey val key: String, val capturedAt: Long, val body: String)
@Entity(tableName = "everyday_brief_settings")
data class BriefSettingsEntity(@PrimaryKey val id: Int = 1, val version: Long, val body: String)
@Entity(tableName = "everyday_brief_deliveries")
data class BriefDeliveryEntity(@PrimaryKey val key: String, val documentId: String, val at: Long, val state: String)

@Dao
interface BriefDao {
    @Query("DELETE FROM everyday_brief_settings") suspend fun clearSettings()
    @Query("SELECT * FROM everyday_briefs ORDER BY generatedAt DESC, id LIMIT 60") fun observe(): Flow<List<BriefEntity>>
    @Query("SELECT * FROM everyday_briefs ORDER BY generatedAt DESC, id LIMIT :limit") suspend fun recent(limit: Int): List<BriefEntity>
    @Query("SELECT * FROM everyday_briefs WHERE id=:id") suspend fun get(id: String): BriefEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: BriefEntity)
    @Query("DELETE FROM everyday_briefs WHERE id=:id") suspend fun remove(id: String)
    @Query("DELETE FROM everyday_briefs WHERE generatedAt<:before OR id NOT IN (SELECT id FROM everyday_briefs ORDER BY generatedAt DESC,id LIMIT 60)") suspend fun prune(before: Long)
    @Query("DELETE FROM everyday_briefs") suspend fun clearBriefs()
    @Query("SELECT * FROM everyday_brief_baselines WHERE `key`=:key") suspend fun baseline(key: String): BriefBaselineEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun baseline(value: BriefBaselineEntity): Long
    @Query("DELETE FROM everyday_brief_baselines WHERE capturedAt<:before") suspend fun pruneBaselines(before: Long)
    @Query("DELETE FROM everyday_brief_baselines") suspend fun clearBaselines()
    @Query("SELECT * FROM everyday_brief_settings WHERE id=1") suspend fun settings(): BriefSettingsEntity?
    @Query("SELECT * FROM everyday_brief_settings WHERE id=1") fun observeSettings(): Flow<BriefSettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun settings(value: BriefSettingsEntity)
    @Query("SELECT * FROM everyday_brief_deliveries WHERE at>=:since") suspend fun deliveries(since: Long): List<BriefDeliveryEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun claim(value: BriefDeliveryEntity): Long
    @Query("UPDATE everyday_brief_deliveries SET state='CLAIMED', at=:at WHERE `key`=:key AND documentId=:documentId AND at=:previousAt AND state='RETRYABLE'")
    suspend fun reclaim(key: String, documentId: String, previousAt: Long, at: Long): Int
    @Query("""UPDATE everyday_brief_deliveries SET state=:state WHERE `key`=:key AND documentId=:documentId AND
        (state IN ('CLAIMED','RETRYABLE','QUEUED') OR state=:state OR
        (state='COMPLETION_UNKNOWN' AND :state NOT IN ('CLAIMED','RETRYABLE','CANCELLED','QUEUED')))""")
    suspend fun delivered(key: String, documentId: String, state: String): Int
    @Query("DELETE FROM everyday_brief_deliveries WHERE at<:before") suspend fun pruneDeliveries(before: Long)
    @Query("DELETE FROM everyday_brief_deliveries") suspend fun clearDeliveries()
}
