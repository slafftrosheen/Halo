package com.halo.app.data.workspace

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(entity: WorkspaceFileEntity)

    @Query("UPDATE workspace_files SET availability=:availability WHERE objectId=:id AND uri=:uri AND ownership=:ownership AND sha256 IS :sha256 AND modifiedAt IS :modifiedAt AND permissionPersisted=:permissionPersisted")
    suspend fun updateAvailabilityIfCurrent(id: String, uri: String, ownership: String, sha256: String?,
        modifiedAt: Long?, permissionPersisted: Boolean, availability: String): Int

    @Query("SELECT * FROM workspace_files WHERE objectId = :id LIMIT 1")
    fun observeFile(id: String): Flow<WorkspaceFileEntity?>

    @Query("SELECT * FROM workspace_files WHERE objectId = :objectId LIMIT 1")
    suspend fun getFile(objectId: String): WorkspaceFileEntity?

    @Query("SELECT * FROM workspace_files")
    fun observeFiles(): Flow<List<WorkspaceFileEntity>>

    @Query("SELECT * FROM workspace_files")
    suspend fun allFiles(): List<WorkspaceFileEntity>
}
