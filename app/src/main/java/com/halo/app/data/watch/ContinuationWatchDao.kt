package com.halo.app.data.watch

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.halo.app.data.routine.automationClaimKeys
import kotlinx.coroutines.flow.Flow

@Dao
interface ContinuationWatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatch(entity: ContinuationWatchEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM private_library_claims WHERE fingerprint IN (:hashes))")
    suspend fun containsPrivateClaims(hashes: List<String>): Boolean

    @Transaction
    suspend fun savePublic(entity: ContinuationWatchEntity) {
        val refs = automationClaimKeys(listOfNotNull(entity.id, entity.runId, entity.interactionId),
            entity.conditionJson, entity.behaviorJson, entity.presentationText, entity.deduplicationKey)
        check(refs.chunked(400).none { containsPrivateClaims(it) }) {
            "Continuation watch is unavailable"
        }
        insertWatch(entity)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRestored(entity: ContinuationWatchEntity)
    @Query("DELETE FROM continuation_watches WHERE id = :id")
    suspend fun withdrawPrivateWatch(id: String): Int
    @Query("""SELECT id FROM continuation_watches WHERE id = :sourceId OR runId = :sourceId OR interactionId = :sourceId
        OR instr(conditionJson, :sourceId) > 0 OR instr(behaviorJson, :sourceId) > 0
        OR instr(presentationText, :sourceId) > 0 OR instr(deduplicationKey, :sourceId) > 0 ORDER BY id""")
    suspend fun privateCandidates(sourceId: String): List<String>

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

    @Query("UPDATE continuation_watches SET status = 'EXPIRED' WHERE id = :id AND status = 'ACTIVE'")
    suspend fun expireWatch(id: String): Int

    @Query("UPDATE continuation_watches SET status = 'CANCELLED' WHERE id = :id AND status = 'ACTIVE'")
    suspend fun cancelWatch(id: String): Int

    @Query("UPDATE continuation_watches SET status = 'CANCELLED' WHERE runId = :runId AND status = 'ACTIVE'")
    suspend fun cancelActiveWatchesByRunId(runId: String): Int
}
