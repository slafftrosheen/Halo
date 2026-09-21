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

@Entity(tableName = "conversation_turns")
data class PersistedTurn(@PrimaryKey val id: String, val conversationId: String, val userText: String,
    val assistantText: String, val timestamp: Long, val metadata: String)
@Entity(tableName = "conversations")
data class PersistedConversation(@PrimaryKey val id: String, val createdAt: Long, val active: Boolean)
@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversation_turns ORDER BY timestamp") fun observeAll(): Flow<List<PersistedTurn>>
    @Query("SELECT * FROM conversation_turns WHERE conversationId = :id ORDER BY timestamp") suspend fun turns(id: String): List<PersistedTurn>
    @Query("SELECT * FROM conversations WHERE active = 1 LIMIT 1") suspend fun active(): PersistedConversation?
    @Query("UPDATE conversations SET active = 0") suspend fun deactivate()
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
        conversationId = dao.active()?.id ?: UUID.randomUUID().toString().also {
            dao.conversation(PersistedConversation(it, System.currentTimeMillis(), true))
        }
        reload()
    }
    override suspend fun awaitReady() { ready.await() }
    private suspend fun reload() { _turns.value = dao.turns(conversationId).map { it.domain() } }
    override suspend fun append(userText: String, assistantText: String, metadata: Map<String, String>): ConversationTurn {
        awaitReady()
        return mutex.withLock {
            val row = PersistedTurn(UUID.randomUUID().toString(), conversationId, userText, assistantText,
                System.currentTimeMillis(), JSONObject(metadata).toString())
            dao.insert(row); reload(); row.domain()
        }
    }
    override suspend fun clear() { awaitReady(); mutex.withLock { dao.clear(conversationId); reload() } }
    suspend fun newConversation() {
        awaitReady(); mutex.withLock {
            val id = UUID.randomUUID().toString()
            db.withTransaction { dao.deactivate(); dao.conversation(PersistedConversation(id, System.currentTimeMillis(), true)) }
            conversationId = id; reload()
        }
    }
    suspend fun open(id: String) {
        awaitReady(); mutex.withLock {
            db.withTransaction { dao.deactivate(); dao.conversation(PersistedConversation(id, System.currentTimeMillis(), true)) }
            conversationId = id; reload()
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
