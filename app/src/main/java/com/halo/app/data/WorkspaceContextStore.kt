package com.halo.app.data

import androidx.room.*
import com.halo.app.data.notebook.*
import com.halo.runtime.cognition.context.WorkspaceContextSource
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "working_set")
data class WorkingSetEntry(
    @PrimaryKey val objectId: String,
    val addedAt: Long = 0L,
    val source: String = "USER"
)

@Entity(tableName = "object_provenance")
data class ObjectProvenance(@PrimaryKey val objectId: String, val sourceId: String, val revision: String, val metadata: String)

@Dao
interface WorkspaceContextDao {
    @Query("SELECT objectId FROM working_set") fun observeWorkingSet(): Flow<List<String>>
    @Query("SELECT notes.body FROM notes INNER JOIN working_set ON notes.objectId = working_set.objectId INNER JOIN workspace_objects ON workspace_objects.id = notes.objectId WHERE workspace_objects.lifecycle = 'ACTIVE'")
    suspend fun selectedContent(): List<String>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(entry: WorkingSetEntry)
    @Query("DELETE FROM working_set WHERE objectId = :id") suspend fun remove(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun provenance(value: ObjectProvenance)
}

/** Explicit promotion and selection. Notes enter context only through these actions. */
class WorkspaceContextStore(private val database: AppDatabase, private val notes: NotebookRepository, private val storage: com.halo.sdk.runtime.Storage) : WorkspaceContextSource {
    val useMemories = kotlinx.coroutines.flow.MutableStateFlow(storage.getBoolean("policy_memories", false))
    val cloudSharing = kotlinx.coroutines.flow.MutableStateFlow(storage.getBoolean("policy_cloud_workspace", false))
    fun setUseMemories(enabled: Boolean) { storage.putBoolean("policy_memories", enabled); useMemories.value = enabled }
    fun setCloudSharing(enabled: Boolean) { storage.putBoolean("policy_cloud_workspace", enabled); cloudSharing.value = enabled }
    val workingSet = database.workspaceContextDao().observeWorkingSet()
    val memories = database.memoryDao().getAllMemories()
    suspend fun select(id: String) { requireNotNull(notes.getNote(id)); database.workspaceContextDao().add(WorkingSetEntry(id)) }
    suspend fun remove(id: String) = database.workspaceContextDao().remove(id)
    suspend fun remember(id: String) {
        val note = requireNotNull(notes.getNote(id))
        database.memoryDao().insertMemory(Memory(content = note.body, sourceObjectId = id, sourceRevision = note.revision))
    }
    suspend fun correct(memory: Memory, text: String) {
        require(text.isNotBlank())
        database.memoryDao().updateMemory(memory.copy(content = text.trim(), timestamp = System.currentTimeMillis()))
    }
    suspend fun forget(id: Int) = database.memoryDao().deleteMemoryById(id)
    override suspend fun content(includeMemories: Boolean): List<String> =
        database.workspaceContextDao().selectedContent().map { "Working set: $it" } +
            if (includeMemories) database.memoryDao().snapshot().map { "Remembered: ${it.content}" } else emptyList()
}
