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
    @Query("SELECT objectId FROM working_set ORDER BY addedAt DESC") suspend fun selectedIds(): List<String>
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
    suspend fun select(id: String) {
        requireNotNull(database.notebookDao().getWorkspaceObject(id))
        database.workspaceContextDao().add(WorkingSetEntry(id, System.currentTimeMillis()))
    }
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
    override suspend fun content(includeMemories: Boolean): List<String> {
        val selected = database.workspaceContextDao().selectedIds().take(20).mapNotNull { id ->
            val item = database.notebookDao().getWorkspaceObject(id) ?: return@mapNotNull null
            if (item.lifecycle != "ACTIVE") return@mapNotNull null
            val text = when (item.type) {
                "NOTE" -> notes.getNote(id)?.body
                "MEMORY" -> database.memoryRecordDao().getRecord(id)?.content
                "FILE" -> database.workspaceFileDao().getFile(id)?.let {
                    "${it.displayName} (${it.mimeType})"
                }
                "CONVERSATION" -> database.conversationDao().turns(id).takeLast(3)
                    .joinToString("\n") { "User: ${it.userText}\nHalo: ${it.assistantText}" }
                else -> item.title
            }?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            "Working set ${item.type.lowercase()}: ${text.take(8_000)}"
        }
        val memories = if (includeMemories && useMemories.value) {
            database.memoryDao().snapshot().map { "Remembered: ${it.content}" }
        } else emptyList()
        return selected + memories
    }
}
