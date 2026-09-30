package com.halo.app.data.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryRecordDao {
    @Query("SELECT * FROM memory_records WHERE status = 'ACTIVE' ORDER BY updatedAt DESC")
    fun observeActiveRecords(): Flow<List<MemoryRecordEntity>>

    @Query("SELECT * FROM memory_records WHERE id = :id LIMIT 1")
    suspend fun getRecord(id: String): MemoryRecordEntity?

    /**
     * MINING-P1-001 bounded retrieval. Candidate pools are sized by SQL LIMIT so a large memory
     * store never materialises into the prompt. No schema change: this is a query only.
     */
    @Query(
        "SELECT * FROM memory_records WHERE status = 'ACTIVE' AND scope IN (:scopes) " +
            "ORDER BY updatedAt DESC LIMIT :limit"
    )
    suspend fun recentActive(scopes: List<String>, limit: Int): List<MemoryRecordEntity>

    @Query(
        "SELECT * FROM memory_records WHERE status = 'ACTIVE' AND scope IN (:scopes) " +
            "AND lower(content) LIKE :pattern ESCAPE '\\' ORDER BY updatedAt DESC LIMIT :limit"
    )
    suspend fun searchActive(pattern: String, scopes: List<String>, limit: Int): List<MemoryRecordEntity>

    @Query("SELECT COUNT(*) FROM memory_records WHERE status = 'ACTIVE'")
    suspend fun activeCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(entity: MemoryRecordEntity)

    @Query("UPDATE memory_records SET content = :content, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: String, content: String, updatedAt: Long): Int

    @Query("UPDATE memory_records SET status = 'FORGOTTEN', updatedAt = :updatedAt WHERE id = :id")
    suspend fun forget(id: String, updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRevision(entity: MemoryRevisionEntity)

    @Query("SELECT * FROM memory_revisions WHERE memoryId = :memoryId ORDER BY changedAt DESC")
    fun observeRevisions(memoryId: String): Flow<List<MemoryRevisionEntity>>

    @Query("SELECT * FROM memory_records WHERE status = 'ACTIVE' AND scope = :scope AND ((scopeId IS NULL AND :scopeId IS NULL) OR scopeId = :scopeId) ORDER BY updatedAt DESC LIMIT 60")
    suspend fun relevant(scope: String, scopeId: String?): List<MemoryRecordEntity>
    @Query("UPDATE memory_records SET evidenceCount = MIN(evidenceCount + 1, 1000000), lastConfirmedAt = :at, userConfirmed = 1 WHERE id = :id AND status = 'ACTIVE'")
    suspend fun reinforce(id: String, at: Long)
    @Query("UPDATE memory_records SET usageCount = MIN(usageCount + 1, 1000000), lastUsedAt = :at WHERE id IN (:ids) AND status = 'ACTIVE'")
    suspend fun used(ids: List<String>, at: Long)

    @Query("SELECT * FROM memory_records WHERE status = 'ACTIVE' AND scope IN (:scopes) AND (scope = 'GLOBAL' OR scopeId IN (:scopeIds)) AND (validUntil IS NULL OR validUntil > :now) ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recentScoped(scopes: List<String>, scopeIds: List<String>, now: Long, limit: Int): List<MemoryRecordEntity>
    @Query("SELECT * FROM memory_records WHERE status = 'ACTIVE' AND scope IN (:scopes) AND (scope = 'GLOBAL' OR scopeId IN (:scopeIds)) AND (validUntil IS NULL OR validUntil > :now) AND lower(content) LIKE :pattern ESCAPE '\\' ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun searchScoped(pattern: String, scopes: List<String>, scopeIds: List<String>, now: Long, limit: Int): List<MemoryRecordEntity>
}
