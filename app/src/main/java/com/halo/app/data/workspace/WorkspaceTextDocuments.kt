package com.halo.app.data.workspace

import android.content.Context
import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.ObjectProvenance
import com.halo.app.data.notebook.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class WorkspaceTextDocument(val document: NotebookDocument, val format: WorkspaceTextFormat, val block: NoteBlock) {
    val id get() = document.noteId
    val revision get() = document.revision
    val text get() = block.text
}

/** Literal-file adapter onto the existing canonical note document and managed-file owners. */
class WorkspaceTextDocuments(private val context: Context, private val database: AppDatabase,
    private val notes: NotebookRepository, val documents: NotebookDocumentRepository,
    private val files: WorkspaceFileRepository, private val workspace: WorkspaceRepository) {
    private val versions = WorkspaceFileVersions(files, workspace)
    data class HistoryStatus(val canUndo: Boolean = false, val canRedo: Boolean = false)
    private data class History(val revision: Long, val undo: List<String>, val redo: List<String>)
    private val historyLock = Any()
    private val history = linkedMapOf<String, History>()
    fun historyStatus(id: String, revision: Long): HistoryStatus = synchronized(historyLock) {
        history[id]?.takeIf { it.revision == revision }?.let { HistoryStatus(it.undo.isNotEmpty(), it.redo.isNotEmpty()) } ?: HistoryStatus()
    }
    private fun bounded(values: List<String>): List<String> {
        var result = values.takeLast(16)
        while (result.sumOf { it.length.toLong() } > 1_000_000) result = result.drop(1)
        return result
    }
    private fun remember(before: WorkspaceTextDocument, after: WorkspaceTextDocument) = synchronized(historyLock) {
        val previous = history.remove(before.id)?.takeIf { it.revision == before.revision }
        history[before.id] = History(after.revision, bounded(previous?.undo.orEmpty() + before.text), emptyList())
        while (history.size > 8) history.remove(history.keys.first())
    }

    private fun adapt(document: NotebookDocument?): WorkspaceTextDocument? {
        document ?: return null
        if (document.lifecycle != WorkspaceLifecycle.ACTIVE) return null
        val block = document.blocks.singleOrNull()?.takeIf { it.type == NoteBlockType.CODE } ?: return null
        val format = WorkspaceTextFormat.fromLanguage(block.language) ?: return null
        WorkspaceTextEncoding.validate(block.text)
        return WorkspaceTextDocument(document, format, block)
    }
    fun observe(id: String): Flow<WorkspaceTextDocument?> = documents.observe(id).map(::adapt)
    suspend fun get(id: String): WorkspaceTextDocument? = adapt(documents.load(id))
    private fun validate(format: WorkspaceTextFormat, text: String) {
        WorkspaceTextEncoding.validate(text)
        if (format == WorkspaceTextFormat.CSV) WorkspaceCsv.parse(text)
    }
    suspend fun create(title: String, format: WorkspaceTextFormat, text: String = ""): WorkspaceTextDocument = database.withTransaction {
        require(title.isNotBlank() && title.length <= 120)
        validate(format, text)
        val note = notes.createNote(title = title)
        val block = NoteBlock(UUID.randomUUID().toString(), 0, NoteBlockType.CODE, text, language = format.blockLanguage)
        check(documents.commit(note.id, 1, listOf(block), title) is CommitOutcome.Committed) { "Cannot create document" }
        requireNotNull(get(note.id))
    }
    suspend fun current(id: String, expectedRevision: Long): WorkspaceTextDocument = requireNotNull(get(id)).also {
        check(it.revision == expectedRevision) { "Document changed. Reopen it before editing." }
    }
    suspend fun replace(id: String, expectedRevision: Long, text: String): WorkspaceTextDocument = database.withTransaction {
        val current = current(id, expectedRevision)
        validate(current.format, text)
        check(documents.apply(id, expectedRevision, DocumentCommand.SetText(current.block.id, text)) is CommitOutcome.Committed) {
            "Document changed or could not be saved"
        }
        requireNotNull(get(id)).also { remember(current, it) }
    }
    suspend fun undo(id: String, expectedRevision: Long): WorkspaceTextDocument = historyStep(id, expectedRevision, true)
    suspend fun redo(id: String, expectedRevision: Long): WorkspaceTextDocument = historyStep(id, expectedRevision, false)
    private suspend fun historyStep(id: String, expectedRevision: Long, undo: Boolean): WorkspaceTextDocument = database.withTransaction {
        val before = current(id, expectedRevision)
        val previous = synchronized(historyLock) { requireNotNull(history[id]?.takeIf { it.revision == expectedRevision }) }
        val target = (if (undo) previous.undo else previous.redo).lastOrNull() ?: error("No session history")
        WorkspaceTextEncoding.validate(target)
        check(documents.apply(id, expectedRevision, DocumentCommand.SetText(before.block.id, target)) is CommitOutcome.Committed)
        val after = requireNotNull(get(id))
        synchronized(historyLock) {
            history[id] = if (undo) History(after.revision, previous.undo.dropLast(1), bounded(previous.redo + before.text))
                else History(after.revision, bounded(previous.undo + before.text), previous.redo.dropLast(1))
        }
        after
    }
    suspend fun replaceRange(id: String, expectedRevision: Long, start: Int, end: Int, expectedText: String,
        replacement: String): WorkspaceTextDocument = database.withTransaction {
        val before = current(id, expectedRevision)
        require(start in 0..before.text.length && end in start..before.text.length)
        require(before.text.substring(start, end) == expectedText) { "Selected text changed" }
        replace(id, expectedRevision, before.text.replaceRange(start, end, replacement))
    }
    suspend fun editCsv(id: String, expectedRevision: Long, command: WorkspaceCsvCommand): WorkspaceTextDocument = database.withTransaction {
        val current = current(id, expectedRevision)
        require(current.format == WorkspaceTextFormat.CSV)
        val table = WorkspaceCsv.parse(current.text)
        val changed = when (command) {
            is WorkspaceCsvCommand.SetCell -> table.set(command.row, command.column, command.value)
            is WorkspaceCsvCommand.InsertRow -> table.insertRow(command.index)
            is WorkspaceCsvCommand.DeleteRow -> table.deleteRow(command.index)
            is WorkspaceCsvCommand.InsertColumn -> table.insertColumn(command.index)
            is WorkspaceCsvCommand.DeleteColumn -> table.deleteColumn(command.index)
        }
        replace(id, expectedRevision, changed.encode())
    }
    suspend fun import(sourceId: String, expectedVersion: String): WorkspaceTextDocument {
        val original = requireSource(sourceId, expectedVersion)
        val format = requireNotNull(WorkspaceTextFormat.fromFile(original.displayName, original.mimeType)) { "Unsupported text format" }
        val source = files.pinLocalCopy(WorkspaceObjectId(sourceId), expectedVersion, WorkspaceTextEncoding.MAX_BYTES.toLong())
        // The file owner compares the observed version under its shared relink/copy lock.
        val bytes = readBounded(source)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(sha == source.sha256)
        val text = WorkspaceTextEncoding.decode(bytes)
        validate(format, text)
        return database.withTransaction {
            requireSource(sourceId, requireNotNull(source.version))
            val document = create(source.displayName.take(120), format, text)
            database.workspaceContextDao().provenance(ObjectProvenance(document.id, sourceId, requireNotNull(source.version),
                JSONObject().put("operation", "file-document-import").put("format", format.name)
                    .put("blockId", document.block.id).put("encoding", "UTF-8").toString()))
            document
        }
    }
    suspend fun export(id: String, expectedRevision: Long, format: WorkspaceTextFormat? = null): WorkspaceFile {
        val current = current(id, expectedRevision)
        val target = format ?: current.format
        // CSV conversion must be explicit valid CSV, never a guessed split that discards quoted fields.
        validate(target, current.text)
        val bytes = WorkspaceTextEncoding.encode(current.text)
        val name = current.document.title.take(100).let { if (it.endsWith("." + target.extension, true)) it else it + "." + target.extension }
        return files.createManagedFile(name, target.mime, bytes, id, expectedRevision.toString(),
            JSONObject().put("operation", "file-document-export").put("format", target.name)
                .put("blockId", current.block.id).put("encoding", "UTF-8").toString()) {
            val observed = current(id, expectedRevision)
            check(observed.block == current.block)
        }
    }
    /** Explicit conversion of an ordinary rich note; managed literal-file blocks use exact export instead. */
    suspend fun exportNote(id: String, expectedRevision: Long, format: WorkspaceTextFormat): WorkspaceFile {
        get(id)?.let { return export(id, expectedRevision, format) }
        require(format != WorkspaceTextFormat.CSV) { "An ordinary note is not a CSV table" }
        val document = requireNotNull(documents.load(id))
        require(document.lifecycle == WorkspaceLifecycle.ACTIVE && document.revision == expectedRevision)
        val text = if (document.blocks.isEmpty()) requireNotNull(notes.getNote(id)).body
            else if (format == WorkspaceTextFormat.MARKDOWN) NotebookDocumentCodec.serialize(document.blocks)
            else NotebookDocumentCodec.plainText(document.blocks)
        val bytes = WorkspaceTextEncoding.encode(text)
        return files.createManagedFile(document.title.take(100) + "." + format.extension, format.mime, bytes, id,
            expectedRevision.toString(), JSONObject().put("operation", "file-note-export").put("format", format.name)
                .put("projection", true).toString()) {
            val current = requireNotNull(documents.load(id))
            require(current.lifecycle == WorkspaceLifecycle.ACTIVE && current.revision == expectedRevision)
        }
    }
    suspend fun requireSource(id: String, version: String): WorkspaceFile = versions.current(WorkspaceFileVersion(id, version))
    private suspend fun readBounded(file: WorkspaceFile): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(file.uri).use { input ->
            requireNotNull(input)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= WorkspaceTextEncoding.MAX_BYTES)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }
}

sealed interface WorkspaceCsvCommand {
    data class SetCell(val row: Int, val column: Int, val value: String) : WorkspaceCsvCommand
    data class InsertRow(val index: Int) : WorkspaceCsvCommand
    data class DeleteRow(val index: Int) : WorkspaceCsvCommand
    data class InsertColumn(val index: Int) : WorkspaceCsvCommand
    data class DeleteColumn(val index: Int) : WorkspaceCsvCommand
}
