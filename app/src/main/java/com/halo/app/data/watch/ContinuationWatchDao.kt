package com.halo.app.data.watch

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContinuationWatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatch(entity: ContinuationWatchEntity)

    @Update
    suspend fun updateWatch(entity: ContinuationWatchEntity)

    @Query("SELECT * FROM continuation_watches WHERE id = :id LIMIT 1")
    suspend fun getWatchById(id: String): ContinuationWatchEntity?

    @Query("SELECT * FROM continuation_watches WHERE deduplicationKey = :key AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveWatchByDedupKey(key: String): ContinuationWatchEntity?

    @Query("SELECT * FROM continuation_watches WHERE status = 'ACTIVE'")
    fun observeActiveWatches(): Flow<List<ContinuationWatchEntity>>

    @Query("SELECT * FROM continuation_watches WHERE status = 'ACTIVE'")
    suspend fun getAllActiveWatches(): List<ContinuationWatchEntity>

    @Query("SELECT * FROM continuation_watches WHERE runId = :runId AND status = 'ACTIVE'")
    suspend fun getActiveWatchesByRunId(runId: String): List<ContinuationWatchEntity>

    @Query("SELECT * FROM continuation_watches ORDER BY createdAt DESC LIMIT 100")
    fun observeRecentWatches(): Flow<List<ContinuationWatchEntity>>

    @Query("UPDATE continuation_watches SET claimState = 'CLAIMED', status = 'MATCH_CLAIMED', matchedAt = :nowMs WHERE id = :id AND status = 'ACTIVE' AND claimState = 'UNCLAIMED'")
    suspend fun claimWatchTransaction(id: String, nowMs: Long): Int

    @Query("UPDATE continuation_watches SET status = 'SATISFIED' WHERE id = :id")
    suspend fun satisfyWatch(id: String): Int

    @Query("UPDATE continuation_watches SET status = 'EXPIRED' WHERE id = :id")
    suspend fun expireWatch(id: String): Int

    @Query("UPDATE continuation_watches SET status = 'CANCELLED' WHERE id = :id AND status = 'ACTIVE'")
    suspend fun cancelWatch(id: String): Int

    @Query("UPDATE continuation_watches SET status = 'CANCELLED' WHERE runId = :runId AND status = 'ACTIVE'")
    suspend fun cancelActiveWatchesByRunId(runId: String): Int
}
