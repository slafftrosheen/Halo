package com.halo.app.data

import androidx.room.*
import com.halo.runtime.cognition.conversation.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

import com.halo.app.data.notebook.WorkspaceObjectEntity

@Entity(tableName = "conversation_turns")
data class PersistedTurn(@PrimaryKey val id: String, val conversationId: String, val userText: String,
    val assistantText: String, val timestamp: Long, val metadata: String)

@Entity(tableName = "conversations")
data class PersistedConversation(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val active: Boolean,
    val title: String = "",
    val updatedAt: Long = 0L,
    val archived: Boolean = false
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversation_turns ORDER BY timestamp") fun observeAll(): Flow<List<PersistedTurn>>
    @Query("SELECT * FROM conversations WHERE archived = 0 ORDER BY updatedAt DESC") fun observeConversations(): Flow<List<PersistedConversation>>
    @Query("SELECT * FROM conversation_turns WHERE conversationId = :id ORDER BY timestamp") suspend fun turns(id: String): List<PersistedTurn>
    @Query("SELECT * FROM conversations WHERE active = 1 LIMIT 1") suspend fun active(): PersistedConversation?
    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1") suspend fun getConversation(id: String): PersistedConversation?
    @Query("UPDATE conversations SET active = 0") suspend fun deactivate()
    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id") suspend fun updateTitle(id: String, title: String, updatedAt: Long)
    @Query("UPDATE conversations SET archived = 1, active = 0, updatedAt = :updatedAt WHERE id = :id") suspend fun archive(id: String, updatedAt: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun conversation(value: PersistedConversation)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(value: PersistedTurn)
    @Query("DELETE FROM conversation_turns WHERE conversationId = :id") suspend fun clear(id: String)
}

class RoomConversationStore(private val db: AppDatabase, scope: CoroutineScope) : ConversationStore {
    private val dao = db.conversationDao()
    private val mutex = Mutex()
    private var conversationId = ""
    private val _turns = MutableStateFlow<List<ConversationTurn>>(emptyList())
    override val turns = _turns.asStateFlow()
    val history = dao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val ready = scope.async(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        conversationId = dao.active()?.id ?: UUID.randomUUID().toString().also { id ->
            ensureConversationObject(id, "New conversation", now)
        }
        reload()
    }

    private suspend fun ensureConversationObject(id: String, title: String, timestamp: Long) {
        dao.conversation(PersistedConversation(id = id, createdAt = timestamp, active = true, title = title, updatedAt = timestamp))
        db.notebookDao().insertWorkspaceObject(
            WorkspaceObjectEntity(
                id = id,
                type = "CONVERSATION",
                title = title.ifBlank { "New conversation" },
                createdAt = timestamp,
                updatedAt = timestamp,
                source = "CONVERSATION",
                lifecycle = "ACTIVE",
                inInbox = false,
                indexingState = "READY",
                contentRef = "conversation:$id"
            )
        )
    }

    override suspend fun awaitReady() { ready.await() }
    private suspend fun reload() { _turns.value = dao.turns(conversationId).map { it.domain() } }
    override suspend fun append(userText: String, assistantText: String, metadata: Map<String, String>): ConversationTurn {
        awaitReady()
        return mutex.withLock {
            val now = System.currentTimeMillis()
            val currentConv = dao.getConversation(conversationId)
            var currentTitle = currentConv?.title.orEmpty()
            if (currentTitle.isBlank() || currentTitle == "New conversation") {
                currentTitle = userText.trim().take(60).ifEmpty { "Conversation" }
                dao.updateTitle(conversationId, currentTitle, now)
                db.notebookDao().updateTitle(conversationId, currentTitle, now)
            } else {
                dao.updateTitle(conversationId, currentTitle, now)
                db.notebookDao().updateTitle(conversationId, currentTitle, now)
            }

            val row = PersistedTurn(UUID.randomUUID().toString(), conversationId, userText, assistantText,
                now, JSONObject(metadata).toString())
            dao.insert(row); reload(); row.domain()
        }
    }

    /** RUNTIME-018: stable commit identity deduplicates resumed-task commits. */
    override suspend fun appendIfAbsent(
        commitId: String,
        userText: String,
        assistantText: String,
        metadata: Map<String, String>,
    ): ConversationTurn {
        awaitReady()
        mutex.withLock {
            val existing = dao.turns(conversationId).firstOrNull { persisted ->
                runCatching { JSONObject(persisted.metadata).optString("commitId") }.getOrNull() == commitId
            }
            if (existing != null) return existing.domain()
        }
        val turn = append(userText, assistantText, metadata + mapOf("commitId" to commitId))
        return turn
    }
    override suspend fun clear() { awaitReady(); mutex.withLock { dao.clear(conversationId); reload() } }
    suspend fun newConversation() {
        awaitReady(); mutex.withLock {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            db.withTransaction {
                dao.deactivate()
                ensureConversationObject(id, "New conversation", now)
            }
            conversationId = id; reload()
        }
    }
    suspend fun open(id: String) {
        awaitReady(); mutex.withLock {
            val now = System.currentTimeMillis()
            db.withTransaction {
                dao.deactivate()
                val conv = dao.getConversation(id)
                if (conv != null) {
                    dao.conversation(conv.copy(active = true, updatedAt = now))
                } else {
                    ensureConversationObject(id, "Conversation", now)
                }
            }
            conversationId = id; reload()
        }
    }
    suspend fun renameConversation(id: String, title: String) {
        awaitReady()
        val now = System.currentTimeMillis()
        val newTitle = title.trim().take(80).ifEmpty { "Conversation" }
        mutex.withLock {
            dao.updateTitle(id, newTitle, now)
            db.notebookDao().updateTitle(id, newTitle, now)
            reload()
        }
    }
    override fun recentMessages(maxTurns: Int): List<AIMessage> = turns.value.takeLast(maxTurns).flatMap {
        listOf(AIMessage(Role.USER, it.userText), AIMessage(Role.ASSISTANT, it.assistantText))
    }
    private fun PersistedTurn.domain(): ConversationTurn {
        val json = JSONObject(metadata)
        return ConversationTurn(id, userText, assistantText, timestamp, conversationId,
            json.keys().asSequence().associateWith { json.getString(it) })
    }
}
