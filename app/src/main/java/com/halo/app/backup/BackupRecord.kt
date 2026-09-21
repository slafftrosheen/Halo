package com.halo.app.backup

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

enum class BackupRecordState { WRITING, COMPLETE, FAILED, MISSING }

@Entity(tableName = "backup_records")
data class BackupRecordEntity(
    @PrimaryKey val id: String,
    val archiveId: String,
    val destinationUri: String,
    val createdAt: Long,
    val profile: String,
    val sizeBytes: Long?,
    val checksum: String?,
    val state: String,
)

@Dao
interface BackupRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: BackupRecordEntity)

    @Query("SELECT * FROM backup_records ORDER BY createdAt DESC")
    suspend fun all(): List<BackupRecordEntity>

    @Query("DELETE FROM backup_records WHERE id = :id")
    suspend fun delete(id: String)
}
