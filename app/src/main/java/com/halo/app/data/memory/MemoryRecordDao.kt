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
}
