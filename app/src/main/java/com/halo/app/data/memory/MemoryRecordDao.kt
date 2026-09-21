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
