package com.halo.app.ui.screens.home

import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.util.Base64

enum class ComposerDraftFailure { READ, WRITE, TOO_LARGE, FULL }
data class ComposerDraftState(val text: String = "", val revision: Long = 0, val saved: Boolean = false,
    val failure: ComposerDraftFailure? = null)
data class ComposerDraftIdentity(val conversationId: String, val revision: Long, val literal: String)

/** Local editor state only. Loading a draft never submits it to cognition. */
class ComposerDraftStore(private val storage: Storage, private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val canPersistConversation: (suspend (String) -> Boolean)? = null) {
    companion object {
        const val STORAGE_KEY = "composer.drafts.v1"
        private const val MAX_TEXT = 100_000
        private const val MAX_RECORDS = 128
        private const val MAX_ENCODED = 700_000
    }
    private val lock = Any()
    private val disk = Mutex()
    private val mutable = MutableStateFlow<Map<String, ComposerDraftState>>(emptyMap())
    val states = mutable.asStateFlow()
    private val mutableCleanupFailure = MutableStateFlow(false)
    val cleanupFailure = mutableCleanupFailure.asStateFlow()
    private var generation = 0L
    private var loaded = false
    private var unreadable = false
    private var persisted = linkedMapOf<String, String>()
    private val pendingMigrations = mutableSetOf<String>()
    private val deletedIds = mutableSetOf<String>()
    private val deletedRevisions = mutableMapOf<String, Long>()
    private var restoredTemporary = false
    private val temporaryMoves = linkedMapOf<Long, ComposerDraftIdentity>()
    fun hasRestoredTemporary(): Boolean = synchronized(lock) { restoredTemporary }
    private fun checkId(id: String) { require(id.length <= 128 && '\u0000' !in id) }
    fun current(id: String): ComposerDraftState? = synchronized(lock) { mutable.value[id] }
    /** Exact rendered identity, optionally following one real revision-bound startup move. */
    fun resolveIdentity(id: String, revision: Long, literal: String): ComposerDraftIdentity? = synchronized(lock) {
        val value = mutable.value[id]
        if (id !in deletedIds && value?.revision == revision && value.text == literal)
            return@synchronized ComposerDraftIdentity(id, revision, literal)
        if (id.isNotEmpty()) return@synchronized null
        temporaryMoves[revision]?.takeIf { moved -> moved.literal == literal && moved.conversationId !in deletedIds &&
            mutable.value[moved.conversationId]?.let { it.revision == moved.revision && it.text == literal } == true }
    }
    fun resolveSubmission(id: String, revision: Long, literal: String, activeConversation: String?, temporaryTarget: String?): ComposerDraftIdentity? {
        if (activeConversation.isNullOrBlank()) return null
        val exact = resolveIdentity(id, revision, literal) ?: return null
        return exact.takeIf { it.conversationId == activeConversation ||
            id.isEmpty() && it.conversationId.isEmpty() && temporaryTarget == activeConversation }
    }
    private fun publish(id: String, state: ComposerDraftState) { mutable.value = mutable.value + (id to state) }
    /** Called under lock: revoke only the editor seen before the canonical deletion check. */
    private fun revokeDeleted(id: String, observedRevision: Long?): Long? {
        val value = mutable.value[id]
        val previousFloor = deletedRevisions[id] ?: -1L
        deletedIds.add(id)
        deletedRevisions[id] = maxOf(previousFloor, observedRevision ?: 0L)
        if (value?.revision != observedRevision) return null
        if (value != null && value.text.isEmpty() && value.revision <= previousFloor) return value.revision
        val marker = ++generation
        publish(id, ComposerDraftState(revision = marker))
        deletedRevisions[id] = marker
        return marker
    }
    fun save(conversationId: String, text: String): Job {
        checkId(conversationId)
        val revision = synchronized(lock) {
            (++generation).also { publish(conversationId, ComposerDraftState(text, it)) }
        }
        return write(conversationId, revision)
    }
    /** Compare literal text AND editor revision, including edits which later return to the same text. */
    fun replaceIfCurrent(id: String, revision: Long, text: String, replacement: String): Long? = synchronized(lock) {
        val value = mutable.value[id] ?: return@synchronized null
        if (id in deletedIds) return@synchronized null
        if (value.revision != revision || value.text != text) return@synchronized null
        val next = ++generation
        publish(id, ComposerDraftState(replacement, next))
        write(id, next)
        next
    }
    fun retry(id: String): Job = current(id)?.let { write(id, it.revision) } ?: scope.launch(dispatcher) { load(id) }
    private suspend fun readDisk() {
        if (loaded) return
        if (unreadable) throw IOException("Unreadable draft snapshot")
        try {
            val raw = storage.getString(STORAGE_KEY)
            persisted = if (raw == null) linkedMapOf() else decode(raw)
            synchronized(lock) { restoredTemporary = !persisted[""].isNullOrEmpty() }
            loaded = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { unreadable = true; throw IOException("Unreadable draft snapshot") }
    }
    suspend fun load(conversationId: String): String = withContext(dispatcher) {
        checkId(conversationId)
        disk.withLock {
            try {
                readDisk()
                val beforeCheck = current(conversationId)
                val available = if (conversationId.isNotBlank()) canPersistConversation?.invoke(conversationId) else null
                if (available == false) {
                    synchronized(lock) { revokeDeleted(conversationId, beforeCheck?.revision) }
                    return@withLock
                }
                synchronized(lock) {
                    if (available == true && deletedIds.remove(conversationId)) {
                        // Restoration grants a new editor identity, never an old queued write.
                        // Input entered during the exact existence check already has a newer identity.
                        val floor = deletedRevisions.remove(conversationId) ?: beforeCheck?.revision ?: 0L
                        if (current(conversationId)?.revision == beforeCheck?.revision && (current(conversationId)?.revision ?: 0L) <= floor) {
                            val revision = ++generation
                            publish(conversationId, ComposerDraftState(revision = revision, saved = conversationId !in persisted))
                            if (conversationId in persisted) write(conversationId, revision)
                        }
                    }
                    if (conversationId !in mutable.value) publish(conversationId,
                        ComposerDraftState(persisted[conversationId].orEmpty(), saved = true))
                    else mutable.value[conversationId]?.takeIf { it.failure == ComposerDraftFailure.READ }?.let {
                        publish(conversationId, if (it.revision == 0L) ComposerDraftState(persisted[conversationId].orEmpty(), saved = true)
                            else it.copy(saved = false, failure = ComposerDraftFailure.WRITE))
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { synchronized(lock) {
                val value = mutable.value[conversationId] ?: ComposerDraftState()
                publish(conversationId, value.copy(saved = false, failure = ComposerDraftFailure.READ))
            } }
        }
        current(conversationId)?.text.orEmpty()
    }
    fun reload(id: String): Job = scope.launch(dispatcher) {
        disk.withLock { if (!loaded) unreadable = false }
        load(id)
    }
    private fun write(id: String, revision: Long): Job = scope.launch(dispatcher) {
        disk.withLock {
            if (current(id)?.revision != revision) return@withLock
            var failure = ComposerDraftFailure.WRITE
            try {
                if (unreadable) { failure = ComposerDraftFailure.READ; throw IOException() }
                readDisk()
                val available = if (id.isNotBlank()) canPersistConversation?.invoke(id) else null
                if (available == false || available == null && synchronized(lock) { id in deletedIds }) {
                    synchronized(lock) { revokeDeleted(id, revision) }
                    return@withLock
                }
                synchronized(lock) { if (available == true && current(id)?.revision == revision) {
                    val floor = deletedRevisions[id]
                    if (floor != null && revision <= floor && current(id)?.text?.isNotEmpty() == true) return@withLock
                    deletedIds.remove(id); deletedRevisions.remove(id)
                } }
                val value = current(id)?.takeIf { it.revision == revision } ?: return@withLock
                if (value.text.length > MAX_TEXT) { failure = ComposerDraftFailure.TOO_LARGE; throw IOException() }
                val next = LinkedHashMap(persisted).apply {
                    if (id in pendingMigrations) remove("")
                    if (value.text.isEmpty()) remove(id) else put(id, value.text)
                }
                failure = ComposerDraftFailure.FULL
                val encoded = encode(next)
                failure = ComposerDraftFailure.WRITE
                if (current(id)?.revision != revision) return@withLock
                storage.putString(STORAGE_KEY, encoded)
                persisted = next
                if (pendingMigrations.remove(id)) synchronized(lock) { restoredTemporary = false }
                synchronized(lock) {
                    mutable.value[id]?.takeIf { it.revision == revision }?.let { publish(id, it.copy(saved = true, failure = null)) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { synchronized(lock) {
                mutable.value[id]?.takeIf { it.revision == revision }?.let {
                    publish(id, it.copy(saved = false, failure = if (unreadable) ComposerDraftFailure.READ else failure))
                }
            } }
        }
    }
    /** Explicit user confirmation is required by the UI before replacing corrupt backing bytes. */
    fun replaceUnreadable(id: String, expectedRevision: Long? = null): Job = scope.launch(dispatcher) {
        disk.withLock {
            if (!unreadable) return@withLock
            val value = current(id) ?: return@withLock
            if (expectedRevision != null && value.revision != expectedRevision) return@withLock
            if (id.isNotBlank() && canPersistConversation?.invoke(id) == false) return@withLock
            try {
                require(value.text.length <= MAX_TEXT)
                val next = linkedMapOf<String, String>().apply { if (value.text.isNotEmpty()) put(id, value.text) }
                storage.putString(STORAGE_KEY, encode(next))
                persisted = next; loaded = true; unreadable = false
                synchronized(lock) { restoredTemporary = false }
                synchronized(lock) { mutable.value[id]?.takeIf { it.revision == value.revision }?.let { publish(id, it.copy(saved = true, failure = null)) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { synchronized(lock) { current(id)?.let { publish(id, it.copy(saved = false, failure = ComposerDraftFailure.READ)) } } }
        }
    }
    /** Only the initial not-yet-identified editor may move to its first canonical conversation. */
    fun migrateTemporary(to: String, expectedSourceRevision: Long? = null, expectedTargetRevision: Long? = null): Job = scope.launch(dispatcher) {
        checkId(to); if (to.isBlank()) return@launch
        disk.withLock {
            try {
                readDisk()
                if (canPersistConversation?.invoke(to) == false) return@withLock
                val sourceState = current("")
                val targetState = current(to)
                if (expectedSourceRevision != null && sourceState?.revision != expectedSourceRevision ||
                    expectedTargetRevision != null && (targetState?.revision ?: 0L) != expectedTargetRevision) return@withLock
                val source = sourceState?.text ?: persisted[""].orEmpty()
                val target = targetState?.text ?: persisted[to].orEmpty()
                if (source.isEmpty() || target.isNotEmpty()) return@withLock
                val revision = synchronized(lock) {
                    // Reject typing/clearing that raced the source/target snapshot.
                    if (current("") != sourceState || current(to) != targetState) return@withLock
                    publish("", ComposerDraftState(revision = ++generation))
                    (++generation).also {
                        publish(to, ComposerDraftState(source, it))
                        sourceState?.let { before -> temporaryMoves[before.revision] = ComposerDraftIdentity(to, it, source) }
                        while (temporaryMoves.size > 32) temporaryMoves.remove(temporaryMoves.keys.first())
                    }
                }
                pendingMigrations.add(to)
                try {
                    require(source.length <= MAX_TEXT)
                    val next = LinkedHashMap(persisted).apply { remove(""); put(to, source) }
                    storage.putString(STORAGE_KEY, encode(next)); persisted = next; pendingMigrations.remove(to)
                    synchronized(lock) { restoredTemporary = false }
                    synchronized(lock) {
                        current(to)?.takeIf { it.revision == revision }?.let { publish(to, it.copy(saved = true)) }
                        current("")?.takeIf { it.revision == revision - 1 }?.let { publish("", it.copy(saved = true)) }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    synchronized(lock) { current(to)?.takeIf { it.revision == revision }?.let {
                        publish(to, it.copy(failure = if (source.length > MAX_TEXT) ComposerDraftFailure.TOO_LARGE else ComposerDraftFailure.WRITE))
                    } }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { synchronized(lock) { val value = current("") ?: ComposerDraftState(); publish("", value.copy(failure = if (unreadable) ComposerDraftFailure.READ else ComposerDraftFailure.WRITE)) } }
        }
    }
    /** Caller supplies exact canonical existence/lifecycle checks, never a paginated history list. */
    suspend fun pruneDeleted(isDeleted: suspend (String) -> Boolean) = withContext(dispatcher) {
        disk.withLock {
            try {
                readDisk()
                val observed = synchronized(lock) {
                    (persisted.keys + mutable.value.keys).filter { it.isNotBlank() }.associateWith { mutable.value[it]?.revision }
                }
                val ids = observed.keys.filter { isDeleted(it) }.toSet()
                if (ids.isEmpty()) { mutableCleanupFailure.value = false; return@withLock }
                // Revoke before I/O, even when removal fails. Newer typing keeps its own identity.
                val revoked = synchronized(lock) { ids.associateWith { revokeDeleted(it, observed[it]) } }
                if (ids.any { it in persisted }) {
                    val next = LinkedHashMap(persisted).apply { ids.forEach(::remove) }
                    storage.putString(STORAGE_KEY, encode(next)); persisted = next
                }
                synchronized(lock) { revoked.forEach { (id, revision) ->
                    current(id)?.takeIf { revision != null && it.revision == revision && it.text.isEmpty() }
                        ?.let { publish(id, it.copy(saved = true)) }
                } }
                mutableCleanupFailure.value = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableCleanupFailure.value = true }
        }
    }
    private fun encode(records: Map<String, String>): String {
        require(records.size <= MAX_RECORDS)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(1); out.writeInt(records.size)
            records.forEach { (id, text) ->
                checkId(id); require(text.length <= MAX_TEXT)
                out.writeUTF(id)
                val body = text.toByteArray(Charsets.UTF_8); out.writeInt(body.size); out.write(body)
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray()).also { require(it.length <= MAX_ENCODED) }
    }
    private fun decode(raw: String): LinkedHashMap<String, String> {
        require(raw.length <= MAX_ENCODED)
        return DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(raw))).use { input ->
            require(input.readInt() == 1)
            val count = input.readInt(); require(count in 0..MAX_RECORDS)
            linkedMapOf<String, String>().apply {
                repeat(count) {
                    val id = input.readUTF(); checkId(id); require(!containsKey(id))
                    val size = input.readInt(); require(size in 0..MAX_TEXT * 4 && size <= input.available())
                    val bytes = ByteArray(size); input.readFully(bytes)
                    val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                    require(text.length <= MAX_TEXT); put(id, text)
                }
                require(input.available() == 0)
            }
        }
    }
}
