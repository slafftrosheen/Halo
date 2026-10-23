package com.halo.app.data.workspace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.ObjectProvenance
import com.halo.app.data.notebook.*
import com.halo.sdk.runtime.ink.InkCodec
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class WorkspacePdfAnnotations(val document: NotebookDocument, val source: WorkspaceFileVersion,
    val block: NoteBlock, val markup: CanvasFileMarkup) {
    val id get() = document.noteId
    val revision get() = document.revision
}

/** Annotation notes link an exact immutable PDF. Vector marks never overwrite or masquerade as an edited PDF. */
class WorkspaceFileAnnotations(private val context: Context, private val database: AppDatabase,
    private val notes: NotebookRepository, private val documents: NotebookDocumentRepository,
    private val files: WorkspaceFileRepository, private val versions: WorkspaceFileVersions) {
    suspend fun create(source: WorkspaceFileVersion, title: String): WorkspacePdfAnnotations {
        require(title.isNotBlank() && title.length <= 120)
        require(versions.current(source).mimeType == "application/pdf")
        val pinned = versions.pin(source)
        require(CanvasPdfPageTools.pageCount(context, pinned) > 0)
        val exact = WorkspaceFileVersion(pinned.objectId.value, requireNotNull(pinned.version))
        return database.withTransaction {
            versions.current(exact)
            val note = notes.createNote(title = title)
            val block = NoteBlock(UUID.randomUUID().toString(), 0, NoteBlockType.CODE, title, language = LANGUAGE)
            check(documents.commit(note.id, 1, listOf(block), title) is CommitOutcome.Committed)
            database.workspaceContextDao().provenance(ObjectProvenance(note.id, exact.objectId, exact.version,
                JSONObject().put("operation", "file-pdf-annotations").put("blockId", block.id)
                    .put("nativePdfEditing", false).toString()))
            check(database.notebookDao().setNoteContentRef(note.id, "file-tools:${note.id}") == 1)
            requireNotNull(get(note.id))
        }
    }
    suspend fun get(id: String): WorkspacePdfAnnotations? {
        val document = documents.load(id) ?: return null
        if (document.lifecycle != WorkspaceLifecycle.ACTIVE) return null
        val block = document.blocks.singleOrNull()?.takeIf { it.type == NoteBlockType.CODE && it.language == LANGUAGE } ?: return null
        val provenance = requireNotNull(database.workspaceContextDao().provenanceFor(id))
        val metadata = JSONObject(provenance.metadata)
        require(metadata.getString("operation") == "file-pdf-annotations" && metadata.getString("blockId") == block.id)
        val markup = CanvasFileMarkupCodec.decode(block.text)
        require(CanvasFileMarkupCodec.merge(markup.title, markup.pages) == block.text) { "Unrecognized annotation document" }
        require(markup.pages.values.sumOf { encoded -> requireNotNull(InkCodec.decode(encoded)).strokes.sumOf { it.points.size } } <= 65_536)
        return WorkspacePdfAnnotations(document, WorkspaceFileVersion(provenance.sourceId, provenance.revision), block, markup)
    }
    suspend fun current(id: String, revision: Long): WorkspacePdfAnnotations = requireNotNull(get(id)).also {
        check(it.revision == revision) { "Annotations changed. Reopen before editing." }
        require(versions.current(it.source).mimeType == "application/pdf")
    }
    suspend fun setPage(id: String, revision: Long, page: Int, encodedInk: String): WorkspacePdfAnnotations =
        setPages(id, revision, mapOf(page to encodedInk))
    private fun merged(before: WorkspacePdfAnnotations, updates: Map<Int, String>): String {
        require(updates.size <= CanvasFileMarkupCodec.MAX_PAGES)
        val safe = updates.mapValues { (_, encoded) ->
            val ink = requireNotNull(InkCodec.decode(encoded))
            if (ink.strokes.isEmpty()) "" else encoded
        }
        val content = CanvasFileMarkupCodec.merge(before.block.text, safe)
        val next = CanvasFileMarkupCodec.decode(content)
        require(next.pages.values.sumOf { value -> requireNotNull(InkCodec.decode(value)).strokes.sumOf { it.points.size } } <= 65_536)
        return content
    }
    suspend fun setPages(id: String, revision: Long, updates: Map<Int, String>, acknowledgeRecoverySequence: Long = 0): WorkspacePdfAnnotations {
        val before = current(id, revision)
        val file = versions.current(before.source)
        val count = CanvasPdfPageTools.pageCount(context, file)
        require(updates.keys.all { it in 1..count })
        val content = merged(before, updates)
        return database.withTransaction {
            current(id, revision)
            check(documents.apply(id, revision, DocumentCommand.SetText(before.block.id, content),
                acknowledgeDraftSequence = acknowledgeRecoverySequence) is CommitOutcome.Committed)
            requireNotNull(get(id))
        }
    }
    suspend fun saveRecovery(id: String, revision: Long, updates: Map<Int, String>, sequence: Long) = database.withTransaction {
        require(sequence > 0)
        val before = current(id, revision)
        val content = merged(before, updates)
        documents.saveRecovery(id, listOf(before.block.copy(text = content)), revision, sequence)
    }
    suspend fun recovery(id: String): RecoveryDraft? = documents.loadRecovery(id)
    suspend fun preview(id: String, revision: Long, page: Int): Bitmap {
        val note = current(id, revision)
        val output = CanvasPdfPageTools.preview(context, versions.current(note.source), page)
        return try { draw(note, page, output); output } catch (failure: Throwable) { output.recycle(); throw failure }
    }
    suspend fun exportPageImage(id: String, revision: Long, page: Int): WorkspaceFile {
        val note = current(id, revision)
        val bitmap = CanvasPdfPageTools.exportBitmap(context, versions.current(note.source), page)
        val bytes = try { draw(note, page, bitmap); CanvasImageTools.encodeBitmap(bitmap, CanvasImageFormat.PNG) }
            finally { bitmap.recycle() }
        return files.createManagedFile(note.document.title.take(80) + "-page-$page.png", "image/png", bytes, id, revision.toString(),
            JSONObject().put("operation", "file-pdf-annotation-page-image").put("pdfSourceId", note.source.objectId)
                .put("pdfSourceVersion", note.source.version).put("page", page).put("rasterized", true)
                .put("nativePdfEditing", false).toString()) { current(id, revision) }
    }
    private suspend fun draw(note: WorkspacePdfAnnotations, page: Int, output: Bitmap) {
        coroutineContext.ensureActive()
        val document = note.markup.pages[page]?.let { requireNotNull(InkCodec.decode(it)) } ?: return
        val aspect = document.aspectMilli.takeIf { it > 0 }?.div(1000f)
        val width = if (aspect == null) output.width.toFloat() else minOf(output.width.toFloat(), output.height * aspect)
        val height = if (aspect == null) output.height.toFloat() else width / aspect
        val left = (output.width - width) / 2; val top = (output.height - height) / 2
        WorkspaceInkBitmapRenderer.draw(Canvas(output), document, RectF(left, top, left + width, top + height),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff18203a.toInt() }, minOf(width, height) * .006f)
        coroutineContext.ensureActive()
    }
    companion object { const val LANGUAGE = "halo-file-pdf-ink-v1" }
}
