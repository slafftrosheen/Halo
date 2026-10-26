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
    @Query("SELECT * FROM conversation_turns WHERE id = :id LIMIT 1")
    suspend fun turnById(id: String): PersistedTurn?
    @Query("SELECT * FROM conversation_turns WHERE metadata LIKE :pattern ESCAPE '\\' ORDER BY timestamp DESC")
    suspend fun turnsForCommit(pattern: String): List<PersistedTurn>

    @Query("SELECT conversation_turns.* FROM conversation_turns INNER JOIN conversations ON conversations.id = conversation_turns.conversationId INNER JOIN workspace_objects ON workspace_objects.id = conversations.id WHERE conversationId = :id AND conversations.archived = 0 AND workspace_objects.lifecycle = 'ACTIVE' AND (userText LIKE :query ESCAPE '\\' OR assistantText LIKE :query ESCAPE '\\') ORDER BY timestamp DESC LIMIT :limit")
    suspend fun searchCurrent(id: String, query: String, limit: Int): List<PersistedTurn>
    @Query("""SELECT conversations.* FROM conversations
        INNER JOIN workspace_objects ON workspace_objects.id = conversations.id
        WHERE conversations.archived = 0 AND workspace_objects.type = 'CONVERSATION'
          AND workspace_objects.lifecycle = :lifecycle
          AND EXISTS (SELECT 1 FROM conversation_turns WHERE conversationId = conversations.id)
          AND (conversations.title LIKE :query ESCAPE '\' OR EXISTS
            (SELECT 1 FROM conversation_turns WHERE conversationId = conversations.id
             AND (userText LIKE :query ESCAPE '\' OR assistantText LIKE :query ESCAPE '\')))
        ORDER BY conversations.updatedAt DESC, conversations.id LIMIT :limit OFFSET :offset""")
    suspend fun savedChats(lifecycle: String, query: String, limit: Int, offset: Int): List<PersistedConversation>
    @Query("SELECT * FROM conversation_turns WHERE conversationId = :id ORDER BY timestamp, id LIMIT :limit OFFSET :offset")
    suspend fun savedChatTurns(id: String, limit: Int, offset: Int): List<PersistedTurn>
    @Query("SELECT * FROM conversation_turns ORDER BY timestamp") fun observeAll(): Flow<List<PersistedTurn>>
    // Workspace lifecycle owns visibility and recovery for saved chats. A
    // trashed transcript must never appear in a regular saved-chats list.
    @Query("""SELECT conversations.* FROM conversations
        INNER JOIN workspace_objects ON workspace_objects.id = conversations.id
        WHERE conversations.archived = 0 AND workspace_objects.lifecycle = 'ACTIVE'
        ORDER BY conversations.updatedAt DESC""")
    fun observeConversations(): Flow<List<PersistedConversation>>
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
    private val mutableActiveConversation = MutableStateFlow<String?>(null)
    val activeConversation = mutableActiveConversation.asStateFlow()
    private var conversationId = ""
        set(value) { field = value; mutableActiveConversation.value = value.ifBlank { null } }
    private val _turns = MutableStateFlow<List<ConversationTurn>>(emptyList())
    private val _historyLoaded = MutableStateFlow(false)
    override val turns = _turns.asStateFlow()
    override fun activeConversationId(): String? = conversationId.ifBlank { null }
    val historyLoaded = _historyLoaded.asStateFlow()
    val history = dao.observeAll()
        .onEach { _historyLoaded.value = true }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val conversations = dao.observeConversations()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val ready = scope.async(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val previous = dao.active()?.takeIf { active ->
            !active.archived &&
                db.notebookDao().getWorkspaceObject(active.id)?.lifecycle == "ACTIVE"
        }
        conversationId = previous?.id ?: UUID.randomUUID().toString().also { id ->
            db.withTransaction {
                dao.deactivate()
                ensureConversationObject(id, "New conversation", now)
            }
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
    override suspend fun isConversationAvailable(id: String): Boolean {
        awaitReady()
        return dao.getConversation(id)?.archived == false && db.notebookDao().getWorkspaceObject(id)?.lifecycle == "ACTIVE"
    }
    override suspend fun turnsForConversation(id: String): List<ConversationTurn> {
        awaitReady()
        return if (isConversationAvailable(id)) dao.turns(id).map { it.domain() } else emptyList()
    }
    override suspend fun append(userText: String, assistantText: String, metadata: Map<String, String>): ConversationTurn {
        awaitReady()
        return appendIfAbsent(conversationId, UUID.randomUUID().toString(), userText, assistantText, metadata)
    }
    override suspend fun appendIfAbsent(commitId: String, userText: String, assistantText: String,
        metadata: Map<String, String>): ConversationTurn {
        awaitReady()
        return appendIfAbsent(conversationId, commitId, userText, assistantText, metadata)
    }
    override suspend fun appendIfAbsent(conversationId: String, commitId: String, userText: String,
        assistantText: String, metadata: Map<String, String>): ConversationTurn {
        awaitReady()
        return mutex.withLock {
            db.withTransaction {
                // LIKE only selects candidates. Decoded JSON is the exact global identity authority.
                val escaped = JSONObject.quote(commitId).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                dao.turnsForCommit("%$escaped%").firstOrNull {
                    runCatching { JSONObject(it.metadata).optString("commitId") }.getOrNull() == commitId
                }?.let { return@withTransaction it.domain() }
                require(isConversationAvailable(conversationId)) { "Conversation unavailable" }
                val now = System.currentTimeMillis()
                val title = dao.getConversation(conversationId)!!.title.let {
                    if (it.isBlank() || it == "New conversation") userText.trim().take(60).ifEmpty { "Conversation" } else it
                }
                dao.updateTitle(conversationId, title, now)
                db.notebookDao().updateTitle(conversationId, title, now)
                val row = PersistedTurn(UUID.randomUUID().toString(), conversationId, userText, assistantText,
                    now, JSONObject(metadata + ("commitId" to commitId)).toString())
                dao.insert(row)
                if (this@RoomConversationStore.conversationId == conversationId) reload()
                row.domain()
            }
        }
    }
    override suspend fun clear() { awaitReady(); mutex.withLock { dao.clear(conversationId); reload() } }
    override suspend fun clear(conversationId: String) {
        awaitReady()
        mutex.withLock {
            require(isConversationAvailable(conversationId)) { "Conversation unavailable" }
            dao.clear(conversationId)
            if (this.conversationId == conversationId) reload()
        }
    }
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
    suspend fun open(id: String) { activateExistingConversation(id) }
    override suspend fun activateExistingConversation(id: String): Boolean {
        awaitReady()
        return mutex.withLock {
            if (!isConversationAvailable(id)) return@withLock false
            db.withTransaction {
                dao.deactivate()
                dao.conversation(dao.getConversation(id)!!.copy(active = true))
            }
            conversationId = id; reload(); true
        }
    }
    /**
     * NB08 reversible Delete: lifecycle-only trash/restore, no removal of turns.
     * Both operations share the conversation mutex and a Room transaction with
     * activation, so a deleted active chat cannot remain the active writer.
     * Other callers must still pass isConversationAvailable before opening.
     */
    suspend fun trashSavedConversation(id: String, expectedVersion: String? = null, admit: () -> Unit = {}): Boolean {
        awaitReady()
        return mutex.withLock {
            val now = System.currentTimeMillis()
            val moved = db.withTransaction {
                admit()
                if (expectedVersion != null) {
                    if (id == conversationId) throw SavedChatAdmissionException()
                    try { savedChatSnapshotLocked(id, "ACTIVE", expectedVersion, 0, 1) }
                    catch (_: IllegalArgumentException) { throw SavedChatAdmissionException() }
                }
                val conversation = dao.getConversation(id) ?: return@withTransaction false
                val workspace = db.notebookDao().getWorkspaceObject(id) ?: return@withTransaction false
                if (conversation.archived || workspace.type != "CONVERSATION" ||
                    workspace.lifecycle != "ACTIVE") return@withTransaction false
                admit()
                if (db.notebookDao().setLifecycle(id, "TRASHED", now) != 1)
                    return@withTransaction false
                if (conversationId == id) {
                    // Keep an active chat available for new requests without
                    // resurrecting the deleted transcript on next app launch.
                    dao.deactivate()
                    val nextId = UUID.randomUUID().toString()
                    ensureConversationObject(nextId, "New conversation", now)
                    conversationId = nextId
                }
                admit()
                true
            }
            if (moved) reload()
            moved
        }
    }

    suspend fun restoreSavedConversation(id: String, expectedVersion: String? = null, admit: () -> Unit = {}): Boolean {
        awaitReady()
        return mutex.withLock {
            db.withTransaction {
                admit()
                if (expectedVersion != null) {
                    try { savedChatSnapshotLocked(id, "TRASHED", expectedVersion, 0, 1) }
                    catch (_: IllegalArgumentException) { throw SavedChatAdmissionException() }
                }
                val conversation = dao.getConversation(id) ?: return@withTransaction false
                val workspace = db.notebookDao().getWorkspaceObject(id) ?: return@withTransaction false
                if (conversation.archived || workspace.type != "CONVERSATION" ||
                    workspace.lifecycle != "TRASHED") return@withTransaction false
                admit()
                (db.notebookDao().setLifecycle(id, "ACTIVE", System.currentTimeMillis()) == 1).also { admit() }
            }
        }
    }

    /** Bounded explicit saved-history reads. No active-chat change and no turn metadata exposure. */
    suspend fun listSavedChats(lifecycle: String, query: String, offset: Int, limit: Int, admit: () -> Unit = {}): SavedChatPage {
        require(lifecycle in setOf("ACTIVE", "TRASHED") && query.length <= 120 && offset in 0..10000 && limit in 1..20)
        awaitReady()
        return mutex.withLock { db.withTransaction {
            admit()
            val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            val rows = dao.savedChats(lifecycle, "%$escaped%", limit + 1, offset)
            admit()
            SavedChatPage(rows.take(limit).map { SavedChatSummary(it.id, it.title.take(160), lifecycle, it.updatedAt) },
                if (rows.size > limit) offset + limit else null)
        } }
    }

    suspend fun savedChat(id: String, lifecycle: String, expectedVersion: String? = null,
        offset: Int = 0, limit: Int = 5, admit: () -> Unit = {}): SavedChatSnapshot {
        require(com.halo.app.data.workspace.WorkspaceActionIds.isValid(id))
        require(lifecycle in setOf("ACTIVE", "TRASHED") && offset in 0..2048 && limit in 1..8)
        require(offset == 0 || expectedVersion != null) { "Later pages require the observed version" }
        awaitReady()
        return mutex.withLock { db.withTransaction {
            admit()
            savedChatSnapshotLocked(id, lifecycle, expectedVersion, offset, limit).also { admit() }
        } }
    }

    /** Must be called under this owner's mutex and Room transaction, including mutation admission. */
    private suspend fun savedChatSnapshotLocked(id: String, lifecycle: String, expectedVersion: String?, offset: Int, limit: Int): SavedChatSnapshot {
        val row = requireNotNull(dao.getConversation(id)) { "Saved chat unavailable" }
        val objectRow = requireNotNull(db.notebookDao().getWorkspaceObject(id)) { "Saved chat unavailable" }
        require(!row.archived && objectRow.type == "CONVERSATION" && objectRow.lifecycle == lifecycle) { "Saved chat unavailable" }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        var bytesRead = 0
        fun field(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            bytesRead += bytes.size
            require(bytesRead <= 2_000_000) { "Saved chat exceeds bounded tool read; use Saved chats" }
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array()); digest.update(bytes)
        }
        listOf(row.id, row.title, row.createdAt.toString(), row.updatedAt.toString(), objectRow.lifecycle,
            objectRow.updatedAt.toString(), objectRow.contentRef).forEach(::field)
        var total = 0
        val selected = mutableListOf<SavedChatTurn>()
        while (true) {
            val page = dao.savedChatTurns(id, 64, total)
            require(total + page.size <= 2048) { "Saved chat exceeds bounded tool read; use Saved chats" }
            page.forEachIndexed { index, turn ->
                listOf(turn.id, turn.timestamp.toString(), turn.userText, turn.assistantText, turn.metadata).forEach(::field)
                if (total + index in offset until offset + limit) selected += SavedChatTurn(turn.id, turn.timestamp,
                    turn.userText.take(1000), turn.assistantText.take(1000), turn.userText.length > 1000 || turn.assistantText.length > 1000)
            }
            total += page.size
            if (page.size < 64) break
        }
        val version = "chat:" + digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(expectedVersion == null || version == expectedVersion) { "Saved chat changed" }
        return SavedChatSnapshot(row.id, row.title.take(160), lifecycle, version, row.updatedAt,
            selected, total, if (offset + selected.size < total) offset + selected.size else null)
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
