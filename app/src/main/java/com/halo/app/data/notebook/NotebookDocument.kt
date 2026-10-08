package com.halo.app.data.notebook

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

@Entity(
    tableName = "note_blocks",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["objectId"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId"), Index(value = ["noteId", "position"], unique = true)],
)
data class NoteBlockEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val position: Int,
    val type: String,
    val text: String,
    val checked: Boolean,
    @ColumnInfo(defaultValue = "0") val indent: Int = 0,
    val refObjectId: String? = null,
    val language: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

/** A workspace object joined with its note row: the durable document header. */
data class NoteDocumentRow(
    val id: String,
    val title: String,
    val body: String,
    val revision: Int,
    val schemaVersion: Int,
    val lifecycle: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface NotebookDocumentDao {
    @Query(
        """
        SELECT w.id AS id, w.title AS title, n.body AS body, n.revision AS revision,
               n.schemaVersion AS schemaVersion, w.lifecycle AS lifecycle,
               w.createdAt AS createdAt, w.updatedAt AS updatedAt
        FROM workspace_objects w
        INNER JOIN notes n ON n.objectId = w.id
        WHERE w.id = :noteId AND w.type = 'NOTE'
        LIMIT 1
        """
    )
    fun observeDocument(noteId: String): Flow<NoteDocumentRow?>

    @Query(
        """
        SELECT w.id AS id, w.title AS title, n.body AS body, n.revision AS revision,
               n.schemaVersion AS schemaVersion, w.lifecycle AS lifecycle,
               w.createdAt AS createdAt, w.updatedAt AS updatedAt
        FROM workspace_objects w
        INNER JOIN notes n ON n.objectId = w.id
        WHERE w.id = :noteId AND w.type = 'NOTE'
        LIMIT 1
        """
    )
    suspend fun getDocument(noteId: String): NoteDocumentRow?

    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId ORDER BY position ASC")
    fun observeBlocks(noteId: String): Flow<List<NoteBlockEntity>>

    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId ORDER BY position ASC")
    suspend fun getBlocks(noteId: String): List<NoteBlockEntity>

    @Query("SELECT COUNT(*) FROM note_blocks WHERE noteId = :noteId")
    suspend fun count(noteId: String): Int

    @Query("SELECT revision FROM notes WHERE objectId = :noteId")
    suspend fun getRevision(noteId: String): Int?

    @Query("SELECT lifecycle FROM workspace_objects WHERE id = :noteId")
    suspend fun lifecycleOf(noteId: String): String?

    @Query("SELECT title FROM workspace_objects WHERE id = :noteId")
    suspend fun titleOf(noteId: String): String?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBlock(block: NoteBlockEntity)

    @Query("DELETE FROM note_blocks WHERE noteId = :noteId")
    suspend fun deleteAllBlocks(noteId: String)

    /**
     * Compare-and-swap commit of the document revision and its derived text projection.
     *
     * Returns 1 only when the stored revision still equals [expectedRevision] and the note is not
     * trashed. A returned 0 means "conflict or rejected" and never a silent overwrite.
     */
    @Query(
        """
        UPDATE notes
        SET body = :body, revision = revision + 1, schemaVersion = :schemaVersion
        WHERE objectId = :noteId
          AND revision = :expectedRevision
          AND EXISTS (
              SELECT 1 FROM workspace_objects w
              WHERE w.id = :noteId AND w.lifecycle != 'TRASHED'
          )
        """
    )
    suspend fun commitRevision(
        noteId: String,
        expectedRevision: Long,
        body: String,
        schemaVersion: Int,
    ): Int

    /** Derived-projection refresh that must not look like a user edit. */
    @Query(
        """
        UPDATE notes SET body = :body, schemaVersion = :schemaVersion
        WHERE objectId = :noteId
          AND EXISTS (SELECT 1 FROM workspace_objects w WHERE w.id = :noteId AND w.lifecycle != 'TRASHED')
        """
    )
    suspend fun updateDerivedBody(noteId: String, body: String, schemaVersion: Int): Int

    @Query(
        """
        UPDATE workspace_objects
        SET title = :title, updatedAt = :updatedAt
        WHERE id = :noteId AND type = 'NOTE' AND lifecycle != 'TRASHED'
        """
    )
    suspend fun updateDocumentMetadata(noteId: String, title: String, updatedAt: Long): Int

    @Query("DELETE FROM note_drafts WHERE objectId = :noteId AND sequence <= :sequence")
    suspend fun acknowledgeRecovery(noteId: String, sequence: Long): Int

    @Query("DELETE FROM note_drafts WHERE objectId = :noteId")
    suspend fun deleteRecovery(noteId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecovery(entity: NoteDraftEntity)

    @Query("SELECT * FROM note_drafts WHERE objectId = :noteId LIMIT 1")
    suspend fun getRecovery(noteId: String): NoteDraftEntity?

    @Query("UPDATE note_blocks SET position = :position WHERE id = :blockId AND noteId = :noteId")
    suspend fun updatePosition(noteId: String, blockId: String, position: Int): Int

    @Transaction
    suspend fun replaceBlocks(noteId: String, blocks: List<NoteBlockEntity>) {
        deleteAllBlocks(noteId)
        val staged = blocks.mapIndexed { index, block -> block.copy(position = index) }
        // Two passes keep the unique (noteId, position) index satisfied while inserting.
        staged.forEach { insertBlock(it.copy(position = it.position + 10_000)) }
        staged.forEachIndexed { index, block -> updatePosition(noteId, block.id, index) }
    }
}

/**
 * The single mutation owner for note content.
 *
 * All writers (typing, formatting, reorder, checkbox changes, paste, import, accepted AI proposals
 * and legacy body callers) go through [commit], which serializes mutations per document and compares
 * the expected revision. `notes.body` is only ever written as the derived text projection of the
 * committed blocks.
 */
class NotebookDocumentRepository(
    private val dao: NotebookDocumentDao,
    private val database: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun observe(noteId: String): Flow<NotebookDocument?> =
        combine(dao.observeDocument(noteId), dao.observeBlocks(noteId)) { row, blocks ->
            row?.toDocument(blocks.map(NoteBlockEntity::toDomain))
        }

    suspend fun load(noteId: String): NotebookDocument? =
        dao.getDocument(noteId)?.toDocument(dao.getBlocks(noteId).map(NoteBlockEntity::toDomain))

    /** Legacy body-only notes use the same canonical block initialization as the editor. */
    suspend fun loadLegacyDocument(noteId: String): NotebookDocument? {
        val row = dao.getDocument(noteId) ?: return null
        return ensureDocument(noteId, row.body)
    }

    /**
     * Deterministic migration for body-only notes: if the note has no blocks yet, its committed
     * body is parsed once into stable blocks. The derived text is refreshed without bumping the
     * revision, so an untouched document does not look edited.
     */
    suspend fun ensureDocument(noteId: String, legacyBody: String): NotebookDocument? =
        database.withTransaction {
            val row = dao.getDocument(noteId) ?: return@withTransaction null
            if (dao.count(noteId) == 0) {
                val parsed = NotebookDocumentCodec.parse(legacyBody, newId)
                val timestamp = now()
                dao.replaceBlocks(
                    noteId,
                    parsed.blocks.map { it.toEntity(noteId, timestamp) },
                )
                val derived = NotebookDocumentCodec.serialize(parsed.blocks)
                if (derived != row.body) {
                    dao.updateDerivedBody(noteId, derived, NotebookDocument.SCHEMA_VERSION)
                }
            }
            load(noteId)
        }

    /**
     * Apply one command at an expected revision.
     *
     * Unlike [commit], the current blocks are read *inside* the commit transaction, so the
     * read-modify-write cycle is atomic: nothing can be interleaved between reading the document and
     * swapping its revision. The revision compare-and-swap still rejects a raced writer.
     */
    suspend fun apply(
        noteId: String,
        expectedRevision: Long,
        command: DocumentCommand,
        title: String? = null,
        acknowledgeDraftSequence: Long = 0L,
    ): CommitOutcome = commitInternal(
        noteId = noteId,
        expectedRevision = expectedRevision,
        title = title,
        acknowledgeDraftSequence = acknowledgeDraftSequence,
        mutate = { blocks -> DocumentReducer.apply(blocks, command, newId).blocks },
    )

    suspend fun commit(
        noteId: String,
        expectedRevision: Long,
        blocks: List<NoteBlock>,
        title: String? = null,
        acknowledgeDraftSequence: Long = 0L,
    ): CommitOutcome = commitInternal(
        noteId = noteId,
        expectedRevision = expectedRevision,
        title = title,
        acknowledgeDraftSequence = acknowledgeDraftSequence,
        mutate = { blocks },
    )

    private suspend fun commitInternal(
        noteId: String,
        expectedRevision: Long,
        title: String? = null,
        acknowledgeDraftSequence: Long = 0L,
        mutate: (List<NoteBlock>) -> List<NoteBlock>,
    ): CommitOutcome {
        return try {
            database.withTransaction {
                val lifecycle = dao.lifecycleOf(noteId)
                    ?: return@withTransaction CommitOutcome.Rejected(DocumentRejection.MISSING)
                if (lifecycle == WorkspaceLifecycle.TRASHED.name) {
                    return@withTransaction CommitOutcome.Rejected(DocumentRejection.TRASHED)
                }
                val storedRevision = dao.getRevision(noteId)?.toLong()
                    ?: return@withTransaction CommitOutcome.Rejected(DocumentRejection.MISSING)
                val storedBlocks = dao.getBlocks(noteId).map(NoteBlockEntity::toDomain)
                val existingById = dao.getBlocks(noteId).associateBy { it.id }
                if (storedRevision != expectedRevision) {
                    return@withTransaction CommitOutcome.Conflict(storedRevision)
                }
                if (storedRevision == 0L) return@withTransaction CommitOutcome.Rejected(DocumentRejection.MISSING)

                val normalized = DocumentReducer.normalize(mutate(storedBlocks))
                val currentTitle = dao.titleOf(noteId).orEmpty()
                val derivedTitle = NotebookDocumentCodec.deriveTitle(normalized)
                val resolvedTitle = when {
                    title != null -> title.trim().take(120).ifEmpty { derivedTitle }
                    currentTitle.isBlank() -> derivedTitle
                    // An automatic title keeps following the body; a user title never does.
                    currentTitle == NotebookDocumentCodec.deriveTitle(storedBlocks) -> derivedTitle
                    else -> currentTitle
                }
                val derivedBody = NotebookDocumentCodec.serialize(normalized)
                val timestamp = now()
                val swapped = dao.commitRevision(
                    noteId = noteId,
                    expectedRevision = expectedRevision,
                    body = derivedBody,
                    schemaVersion = NotebookDocument.SCHEMA_VERSION,
                )
                // Belt and braces: the in-transaction check above already guarantees this.
                if (swapped != 1) {
                    return@withTransaction CommitOutcome.Conflict(dao.getRevision(noteId)?.toLong() ?: -1L)
                }
                dao.replaceBlocks(noteId, normalized.map { block ->
                    val existing = existingById[block.id]
                    block.toEntity(
                        noteId = noteId,
                        createdAt = existing?.createdAtEpochMs ?: timestamp,
                        updatedAt = if (existing != null && existing.toDomain() == block) {
                            existing.updatedAtEpochMs
                        } else {
                            timestamp
                        },
                    )
                })
                dao.updateDocumentMetadata(noteId, resolvedTitle, timestamp)
                // Only acknowledge the drafts covered by *this* commit's edit sequence.
                if (acknowledgeDraftSequence > 0) {
                    dao.acknowledgeRecovery(noteId, acknowledgeDraftSequence)
                }
                CommitOutcome.Committed(
                    revision = expectedRevision + 1,
                    updatedAt = timestamp,
                    acknowledgedDraftSequence = acknowledgeDraftSequence,
                )
            }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            CommitOutcome.Failed(error.message ?: "Could not save this note")
        }
    }

    /** Legacy body callers are routed here instead of writing `notes.body` directly. */
    suspend fun replaceFromText(noteId: String, text: String): CommitOutcome {
        val current = load(noteId) ?: return CommitOutcome.Rejected(DocumentRejection.MISSING)
        val parsed = NotebookDocumentCodec.parse(text, newId)
        return commit(noteId, current.revision, parsed.blocks, title = null)
    }

    suspend fun saveRecovery(
        noteId: String,
        blocks: List<NoteBlock>,
        baseRevision: Long,
        sequence: Long,
        title: String? = null,
    ) {
        val timestamp = now()
        dao.upsertRecovery(
            NoteDraftEntity(
                objectId = noteId,
                body = NotebookDocumentCodec.serialize(blocks),
                updatedAt = timestamp,
                baseRevision = baseRevision,
                sequence = sequence,
                structureJson = RecoveryJson.encode(blocks, title),
            )
        )
    }

    suspend fun loadRecovery(noteId: String): RecoveryDraft? = dao.getRecovery(noteId)?.let { entity ->
        val sequence = entity.sequence
        val restored = RecoveryJson.decode(entity.structureJson)
        RecoveryDraft(
            noteId = noteId,
            blocks = if (restored.blocks.isNotEmpty() && sequence > 0) {
                restored.blocks
            } else {
                // Legacy text-only recovery draft: parsed, never merged into newer structure.
                NotebookDocumentCodec.parse(entity.body, newId).blocks
            },
            title = restored.title,
            baseRevision = entity.baseRevision,
            sequence = sequence,
            updatedAt = entity.updatedAt,
        )
    }

    /** Used by the editor when a conflict is resolved by discarding the local draft. */
    suspend fun discardRecovery(noteId: String) = dao.deleteRecovery(noteId)
}

internal fun NoteBlockEntity.toDomain(): NoteBlock = NoteBlock(
    id = id,
    position = position,
    type = NoteBlockType.fromStored(type),
    text = text,
    checked = checked,
    indent = indent,
    refObjectId = refObjectId,
    language = language,
)

internal fun NoteBlock.toEntity(noteId: String, timestamp: Long): NoteBlockEntity = NoteBlockEntity(
    id = id,
    noteId = noteId,
    position = position,
    type = type.name,
    text = text,
    checked = checked,
    indent = indent,
    refObjectId = refObjectId,
    language = language,
    createdAtEpochMs = timestamp,
    updatedAtEpochMs = timestamp,
)

internal fun NoteBlock.toEntity(noteId: String, createdAt: Long, updatedAt: Long): NoteBlockEntity = NoteBlockEntity(
    id = id,
    noteId = noteId,
    position = position,
    type = type.name,
    text = text,
    checked = checked,
    indent = indent,
    refObjectId = refObjectId,
    language = language,
    createdAtEpochMs = createdAt,
    updatedAtEpochMs = updatedAt,
)

internal fun NoteDocumentRow.toDocument(blocks: List<NoteBlock>): NotebookDocument = NotebookDocument(
    noteId = id,
    title = title,
    revision = revision.toLong(),
    blocks = blocks,
    lifecycle = WorkspaceLifecycle.valueOf(lifecycle),
    updatedAt = updatedAt,
    createdAt = createdAt,
    schemaVersion = schemaVersion,
    titleIsDerived = blocks.isEmpty() || title == NotebookDocumentCodec.deriveTitle(blocks),
)

/** Minimal structural encoding for recovery drafts; no reflection and no schema negotiation. */
internal object RecoveryJson {
    data class Decoded(val blocks: List<NoteBlock>, val title: String?)

    fun encode(blocks: List<NoteBlock>, title: String?): String {
        val array = JSONArray()
        blocks.forEach { block ->
            array.put(
                JSONObject()
                    .put("id", block.id)
                    .put("type", block.type.name)
                    .put("text", block.text)
                    .put("checked", block.checked)
                    .put("indent", block.indent)
                    .put("ref", block.refObjectId ?: JSONObject.NULL)
                    .put("lang", block.language ?: JSONObject.NULL)
            )
        }
        return JSONObject()
            .put("schemaVersion", 2)
            .put("title", title ?: JSONObject.NULL)
            .put("blocks", array)
            .toString()
    }

    fun decode(raw: String?): Decoded {
        if (raw.isNullOrBlank()) return Decoded(emptyList(), null)
        return runCatching {
            // v1 was an array only. v2 wraps it with explicit-title metadata.
            val root = raw.trim()
            val objectRoot = root.takeIf { it.startsWith("{") }?.let(::JSONObject)
            val array = objectRoot?.optJSONArray("blocks") ?: JSONArray(root)
            val blocks = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                NoteBlock(
                    id = id,
                    position = index,
                    type = NoteBlockType.fromStored(item.optString("type")),
                    text = item.optString("text"),
                    checked = item.optBoolean("checked"),
                    indent = item.optInt("indent"),
                    refObjectId = item.optString("ref").takeIf { !item.isNull("ref") && it.isNotBlank() },
                    language = item.optString("lang").takeIf { !item.isNull("lang") && it.isNotBlank() },
                )
            }
            Decoded(
                blocks = blocks,
                title = objectRoot?.optString("title")
                    ?.takeIf { !objectRoot.isNull("title") && it.isNotBlank() },
            )
        }.getOrDefault(Decoded(emptyList(), null))
    }
}
