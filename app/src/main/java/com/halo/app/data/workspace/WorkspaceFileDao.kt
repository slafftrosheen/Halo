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

    @Query("SELECT * FROM workspace_files WHERE objectId = :id LIMIT 1")
    fun observeFile(id: String): Flow<WorkspaceFileEntity?>

    @Query("SELECT * FROM workspace_files WHERE objectId = :objectId LIMIT 1")
    suspend fun getFile(objectId: String): WorkspaceFileEntity?

    @Query("SELECT * FROM workspace_files")
    fun observeFiles(): Flow<List<WorkspaceFileEntity>>

    @Query("SELECT * FROM workspace_files")
    suspend fun allFiles(): List<WorkspaceFileEntity>
}
