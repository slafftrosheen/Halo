package com.halo.app.data

import androidx.room.*
import com.halo.app.data.notebook.*
import com.halo.runtime.cognition.context.WorkspaceContextSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    /** Raw provenance metadata for one object; used to restore a saved structured answer (P2). */
    @Query("SELECT metadata FROM object_provenance WHERE objectId = :id LIMIT 1")
    suspend fun provenanceMetadata(id: String): String?
}

/** Explicit promotion and selection. Notes enter context only through these actions. */
class WorkspaceContextStore(private val database: AppDatabase, private val notes: NotebookRepository, private val storage: com.halo.sdk.runtime.Storage) : WorkspaceContextSource {
    val useMemories = kotlinx.coroutines.flow.MutableStateFlow(storage.getBoolean("policy_memories", false))
    val cloudSharing = kotlinx.coroutines.flow.MutableStateFlow(storage.getBoolean("policy_cloud_workspace", false))
    fun setUseMemories(enabled: Boolean) { storage.putBoolean("policy_memories", enabled); useMemories.value = enabled }
    fun setCloudSharing(enabled: Boolean) { storage.putBoolean("policy_cloud_workspace", enabled); cloudSharing.value = enabled }
    fun remoteAllowed(type: String): Boolean = cloudSharing.value &&
        (type != "MEMORY" || storage.getBoolean("policy_cloud_memories", false))
    val workingSet = database.workspaceContextDao().observeWorkingSet()
    val memories = database.memoryRecordDao().observeActiveRecords().map { rows -> rows.filter { it.id.startsWith("mem_") }.map { row ->
        Memory(row.id.removePrefix("mem_").toIntOrNull() ?: 0,row.content,row.updatedAt,row.sourceObjectId,row.sourceRevision)
    } }
    suspend fun select(id: String) {
        requireNotNull(database.notebookDao().getWorkspaceObject(id))
        database.workspaceContextDao().add(WorkingSetEntry(id, System.currentTimeMillis()))
    }
    suspend fun remove(id: String) = database.workspaceContextDao().remove(id)
    suspend fun remember(id: String) {
        val note = requireNotNull(notes.getNote(id))
        com.halo.app.data.memory.RoomMemoryRepository(database).promoteCandidate(com.halo.app.data.memory.MemoryCandidate(note.body,WorkspaceObjectId(id),note.revision))
    }
    suspend fun correct(memory: Memory, text: String) {
        require(text.isNotBlank())
        com.halo.app.data.memory.RoomMemoryRepository(database).correctMemory("mem_${memory.id}",text)
    }
    suspend fun forget(id: Int) { com.halo.app.data.memory.RoomMemoryRepository(database).forgetMemory("mem_$id") }
    override suspend fun content(includeMemories: Boolean): List<String> = entries(includeMemories).map { it.text }
    override suspend fun entries(includeMemories: Boolean): List<com.halo.runtime.cognition.context.WorkspaceContextEntry> =
        database.workspaceContextDao().selectedIds().take(20).mapNotNull { id ->
            resolveContent(id)?.let { (type,text) ->
                if(type == "MEMORY" && !includeMemories) null else
                    com.halo.runtime.cognition.context.WorkspaceContextEntry(id,"Working set ${type.lowercase()}: ${text.take(8_000)}")
            }
        }

    override suspend fun contentFor(objectId: String): String? =
        resolveContent(objectId)?.let { (type, text) ->
            "Explicit ${type.lowercase()} reference: ${text.take(8_000)}"
        }

    override suspend fun isMemory(objectId: String): Boolean = database.notebookDao().getWorkspaceObject(objectId)?.type == "MEMORY"

    private suspend fun resolveContent(id: String): Pair<String, String>? {
        val item = database.notebookDao().getWorkspaceObject(id) ?: return null
        if (item.lifecycle != "ACTIVE") return null
        val text = when (item.type) {
            "NOTE" -> notes.getNote(id)?.body
            "MEMORY" -> database.memoryRecordDao().getRecord(id)?.takeIf { it.status == "ACTIVE" && (it.validUntil == null || it.validUntil > System.currentTimeMillis()) &&
                (it.scope == "GLOBAL" || it.scopeId in database.workspaceContextDao().selectedIds().take(20)) &&
                com.halo.runtime.cognition.memory.MemorySensitivityClassifier.classify(it.content) != com.halo.runtime.cognition.memory.MemorySensitivity.SECRET }?.content
            "FILE" -> database.workspaceFileDao().getFile(id)?.let {
                "${it.displayName} (${it.mimeType})"
            }
            "CONVERSATION" -> database.conversationDao().turns(id).takeLast(3)
                .joinToString("\n") { "User: ${it.userText}\nHalo: ${it.assistantText}" }
            else -> item.title
        }?.takeIf { it.isNotBlank() } ?: return null
        return item.type to text
    }
}
