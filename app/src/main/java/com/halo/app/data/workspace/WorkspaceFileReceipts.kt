package com.halo.app.data.workspace

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class WorkspaceBatchPhase { RUNNING, COMPLETED, PARTIAL, CANCELLED, INTERRUPTED }
enum class WorkspaceBatchItemPhase { PENDING, SAVED, FAILED }
data class WorkspaceBatchItem(val source: WorkspaceFileVersion, val phase: WorkspaceBatchItemPhase = WorkspaceBatchItemPhase.PENDING,
    val pinnedVersion: String? = null, val output: WorkspaceFileVersion? = null)
data class WorkspaceBatchReceipt(val id: String, val revision: Long, val title: String, val phase: WorkspaceBatchPhase,
    val items: List<WorkspaceBatchItem>)

/** Durable receipts are ordinary canonical Notebook documents. They never replay work after interruption. */
class WorkspaceFileReceipts(private val database: AppDatabase, private val notes: NotebookRepository,
    private val documents: NotebookDocumentRepository) {
    private val running = ConcurrentHashMap.newKeySet<String>()
    suspend fun create(title: String, sources: List<WorkspaceFileVersion>): WorkspaceBatchReceipt {
        val result = database.withTransaction {
        require(title.isNotBlank() && title.length <= 120 && sources.size in 1..6 && sources.map { it.objectId }.distinct().size == sources.size)
        val note = notes.createNote(title = title)
        val receipt = WorkspaceBatchReceipt(note.id, 1, title, WorkspaceBatchPhase.RUNNING, sources.map { WorkspaceBatchItem(it) })
        check(documents.commit(note.id, 1, listOf(NoteBlock(UUID.randomUUID().toString(), 0, NoteBlockType.CODE,
            encode(receipt), language = LANGUAGE)), title) is CommitOutcome.Committed)
        check(database.notebookDao().setNoteContentRef(note.id, "file-tools:${note.id}") == 1)
        raw(note.id)
        }
        running += result.id
        return result
    }
    fun release(id: String) { running -= id }
    suspend fun get(id: String): WorkspaceBatchReceipt = raw(id).let {
        if (it.phase == WorkspaceBatchPhase.RUNNING && id !in running) it.copy(phase = WorkspaceBatchPhase.INTERRUPTED) else it
    }
    private suspend fun raw(id: String): WorkspaceBatchReceipt {
        val document = requireNotNull(documents.load(id))
        require(document.lifecycle == WorkspaceLifecycle.ACTIVE)
        val block = requireNotNull(document.blocks.singleOrNull())
        require(block.type == NoteBlockType.CODE && block.language == LANGUAGE)
        return decode(id, document.revision, document.title, block.text)
    }
    /** Can run inside managed-file publication: its output row and receipt update commit together. */
    suspend fun saved(id: String, index: Int, pinned: WorkspaceFileVersion, output: WorkspaceFile): WorkspaceBatchReceipt = update(id) { receipt ->
        require(receipt.phase == WorkspaceBatchPhase.RUNNING && index in receipt.items.indices)
        val previous = receipt.items[index]
        require(previous.phase == WorkspaceBatchItemPhase.PENDING && previous.source.objectId == pinned.objectId)
        receipt.copy(items = receipt.items.mapIndexed { at, item -> if (at == index) item.copy(phase = WorkspaceBatchItemPhase.SAVED,
            pinnedVersion = pinned.version, output = WorkspaceFileVersion(output.objectId.value, requireNotNull(output.version))) else item })
    }
    suspend fun failed(id: String, index: Int): WorkspaceBatchReceipt = update(id) { receipt ->
        require(receipt.phase == WorkspaceBatchPhase.RUNNING && index in receipt.items.indices && receipt.items[index].phase == WorkspaceBatchItemPhase.PENDING)
        receipt.copy(items = receipt.items.mapIndexed { at, item -> if (at == index) item.copy(phase = WorkspaceBatchItemPhase.FAILED) else item })
    }
    suspend fun finish(id: String, cancelled: Boolean = false): WorkspaceBatchReceipt = update(id) { receipt ->
        require(receipt.phase == WorkspaceBatchPhase.RUNNING)
        receipt.copy(phase = when {
            cancelled -> WorkspaceBatchPhase.CANCELLED
            receipt.items.all { it.phase == WorkspaceBatchItemPhase.SAVED } -> WorkspaceBatchPhase.COMPLETED
            else -> WorkspaceBatchPhase.PARTIAL
        })
    }
    private suspend fun update(id: String, change: (WorkspaceBatchReceipt) -> WorkspaceBatchReceipt): WorkspaceBatchReceipt = database.withTransaction {
        val before = raw(id)
        val block = requireNotNull(documents.load(id)).blocks.single()
        val after = change(before)
        check(documents.apply(id, before.revision, DocumentCommand.SetText(block.id, encode(after))) is CommitOutcome.Committed)
        raw(id)
    }
    companion object {
        const val LANGUAGE = "halo-file-batch-v1"
        private fun reference(value: WorkspaceFileVersion) = JSONObject().put("id", value.objectId).put("version", value.version)
        private fun readReference(value: JSONObject): WorkspaceFileVersion {
            require(value.keys().asSequence().toSet() == setOf("id", "version"))
            return WorkspaceFileVersion(value.get("id") as String, value.get("version") as String)
        }
        fun encode(receipt: WorkspaceBatchReceipt): String = JSONObject().put("schema", 1).put("phase", receipt.phase.name)
            .put("items", JSONArray().apply { receipt.items.forEach { item -> put(JSONObject().put("source", reference(item.source))
                .put("phase", item.phase.name).put("pinnedVersion", item.pinnedVersion ?: JSONObject.NULL)
                .put("output", item.output?.let(::reference) ?: JSONObject.NULL)) } }).toString().also { require(it.length <= 20_000) }
        fun decode(id: String, revision: Long, title: String, encoded: String): WorkspaceBatchReceipt {
            require(encoded.length in 2..20_000)
            val root = JSONObject(encoded)
            require(root.keys().asSequence().toSet() == setOf("schema", "phase", "items") && root.get("schema") == 1)
            val rows = root.getJSONArray("items")
            require(rows.length() in 1..6)
            val items = (0 until rows.length()).map { index ->
                val item = rows.getJSONObject(index)
                require(item.keys().asSequence().toSet() == setOf("source", "phase", "pinnedVersion", "output"))
                val phase = WorkspaceBatchItemPhase.valueOf(item.get("phase") as String)
                val pinned = if (item.isNull("pinnedVersion")) null else (item.get("pinnedVersion") as String).also { require(it.length in 1..200) }
                val output = if (item.isNull("output")) null else readReference(item.getJSONObject("output"))
                require((phase == WorkspaceBatchItemPhase.SAVED) == (pinned != null && output != null))
                require(phase == WorkspaceBatchItemPhase.SAVED || pinned == null && output == null)
                WorkspaceBatchItem(readReference(item.getJSONObject("source")), phase, pinned, output)
            }
            require(items.map { it.source.objectId }.distinct().size == items.size)
            val phase = WorkspaceBatchPhase.valueOf(root.get("phase") as String)
            if (phase == WorkspaceBatchPhase.COMPLETED) require(items.all { it.phase == WorkspaceBatchItemPhase.SAVED })
            if (phase == WorkspaceBatchPhase.PARTIAL) require(items.none { it.phase == WorkspaceBatchItemPhase.PENDING } && items.any { it.phase == WorkspaceBatchItemPhase.FAILED })
            return WorkspaceBatchReceipt(id, revision, title, phase, items)
        }
    }
}
