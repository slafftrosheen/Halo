package com.halo.app.data.notebook

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.halo.app.data.AppDatabase
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction

enum class NoteBlockType { PARAGRAPH, HEADING, CHECKLIST, QUOTE, CODE }

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
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class NoteBlock(
    val id: String,
    val position: Int,
    val type: NoteBlockType,
    val text: String,
    val checked: Boolean,
)

@Dao
interface NotebookDocumentDao {
    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId ORDER BY position ASC")
    fun observeBlocks(noteId: String): Flow<List<NoteBlockEntity>>

    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId ORDER BY position ASC")
    suspend fun getBlocks(noteId: String): List<NoteBlockEntity>

    @Query("SELECT COUNT(*) FROM note_blocks WHERE noteId = :noteId")
    suspend fun count(noteId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(block: NoteBlockEntity)

    @Query("UPDATE note_blocks SET text = :text, updatedAtEpochMs = :updatedAt WHERE id = :blockId AND noteId = :noteId")
    suspend fun updateText(noteId: String, blockId: String, text: String, updatedAt: Long): Int

    @Query("UPDATE note_blocks SET type = :type, updatedAtEpochMs = :updatedAt WHERE id = :blockId AND noteId = :noteId")
    suspend fun updateType(noteId: String, blockId: String, type: String, updatedAt: Long): Int

    @Query("UPDATE note_blocks SET checked = :checked, updatedAtEpochMs = :updatedAt WHERE id = :blockId AND noteId = :noteId")
    suspend fun updateChecked(noteId: String, blockId: String, checked: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM note_blocks WHERE id = :blockId AND noteId = :noteId")
    suspend fun delete(noteId: String, blockId: String): Int

    @Query("UPDATE note_blocks SET position = :position WHERE id = :blockId AND noteId = :noteId")
    suspend fun updatePosition(noteId: String, blockId: String, position: Int): Int

    @Transaction
    suspend fun resequence(noteId: String, orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id ->
            updatePosition(noteId, id, -index - 1)
        }
        orderedIds.forEachIndexed { index, id ->
            updatePosition(noteId, id, index)
        }
    }

    @Transaction
    suspend fun insertAndResequence(block: NoteBlockEntity, orderedIds: List<String>) {
        insert(block)
        resequence(block.noteId, orderedIds)
    }

    @Transaction
    suspend fun deleteAndResequence(noteId: String, blockId: String, orderedIds: List<String>): Boolean {
        if (delete(noteId, blockId) != 1) return false
        resequence(noteId, orderedIds)
        return true
    }
    @Query("DELETE FROM note_blocks WHERE noteId = :noteId")
    suspend fun deleteAllBlocks(noteId: String)

    @Transaction
    suspend fun replaceDocument(noteId: String, newBlocks: List<NoteBlockEntity>) {
        deleteAllBlocks(noteId)
        newBlocks.forEach { insert(it) }
    }
}

class NotebookDocumentRepository(
    private val dao: NotebookDocumentDao,
    private val notebookDao: NotebookDao,
    private val database: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun observe(noteId: String): Flow<List<NoteBlock>> =
        dao.observeBlocks(noteId).map { rows ->
            rows.map { row ->
                NoteBlock(
                    id = row.id,
                    position = row.position,
                    type = runCatching { NoteBlockType.valueOf(row.type) }.getOrDefault(NoteBlockType.PARAGRAPH),
                    text = row.text,
                    checked = row.checked,
                )
            }
        }

    suspend fun ensureDocument(noteId: String, legacyBody: String) {
        if (dao.count(noteId) != 0) return
        val timestamp = now()
        dao.insert(
            NoteBlockEntity(
                id = newId(), noteId = noteId, position = 0,
                type = NoteBlockType.PARAGRAPH.name, text = legacyBody,
                checked = false, createdAtEpochMs = timestamp, updatedAtEpochMs = timestamp,
            )
        )
    }

    private suspend fun updateBodyProjection(noteId: String) {
        val blocks = dao.getBlocks(noteId).map {
            NoteBlock(it.id, it.position, runCatching { NoteBlockType.valueOf(it.type) }.getOrDefault(NoteBlockType.PARAGRAPH), it.text, it.checked)
        }
        val text = NotebookDocumentCodec.serialize(blocks)
        notebookDao.updateNoteBody(noteId, text)
    }

    suspend fun addBlock(noteId: String, afterPosition: Int, type: NoteBlockType): String {
        return database.withTransaction {
            val rows = dao.getBlocks(noteId)
            val id = newId()
            val insertAt = (afterPosition + 1).coerceIn(0, rows.size)
            val timestamp = now()
            val block = NoteBlockEntity(
                    id = id, noteId = noteId, position = rows.size,
                    type = type.name, text = "", checked = false,
                    createdAtEpochMs = timestamp, updatedAtEpochMs = timestamp,
                )
            val ids = rows.map(NoteBlockEntity::id).toMutableList().apply { add(insertAt, id) }
            dao.insertAndResequence(block, ids)
            updateBodyProjection(noteId)
            id
        }
    }

    suspend fun updateText(noteId: String, blockId: String, text: String): Boolean {
        return database.withTransaction {
            val updated = dao.updateText(noteId, blockId, text, now()) == 1
            if (updated) updateBodyProjection(noteId)
            updated
        }
    }

    suspend fun updateType(noteId: String, blockId: String, type: NoteBlockType): Boolean {
        return database.withTransaction {
            val updated = dao.updateType(noteId, blockId, type.name, now()) == 1
            if (updated) updateBodyProjection(noteId)
            updated
        }
    }

    suspend fun updateChecked(noteId: String, blockId: String, checked: Boolean): Boolean {
        return database.withTransaction {
            val updated = dao.updateChecked(noteId, blockId, checked, now()) == 1
            if (updated) updateBodyProjection(noteId)
            updated
        }
    }

    suspend fun deleteBlock(noteId: String, blockId: String): Boolean {
        return database.withTransaction {
            val rows = dao.getBlocks(noteId)
            if (rows.size <= 1) return@withTransaction false
            val deleted = dao.deleteAndResequence(
                noteId,
                blockId,
                rows.filterNot { it.id == blockId }.map(NoteBlockEntity::id),
            )
            if (deleted) updateBodyProjection(noteId)
            deleted
        }
    }

    suspend fun move(noteId: String, blockId: String, direction: Int): Boolean {
        return database.withTransaction {
            val ids = dao.getBlocks(noteId).map(NoteBlockEntity::id).toMutableList()
            val from = ids.indexOf(blockId)
            val to = from + direction
            if (from < 0 || to !in ids.indices) return@withTransaction false
            val moved = ids.removeAt(from)
            ids.add(to, moved)
            dao.resequence(noteId, ids)
            updateBodyProjection(noteId)
            true
        }
    }

    suspend fun replaceFromText(noteId: String, text: String) {
        database.withTransaction {
            val timestamp = now()
            val newBlock = NoteBlockEntity(
                id = newId(), noteId = noteId, position = 0,
                type = NoteBlockType.PARAGRAPH.name, text = text,
                checked = false, createdAtEpochMs = timestamp, updatedAtEpochMs = timestamp,
            )
            dao.replaceDocument(noteId, listOf(newBlock))
            updateBodyProjection(noteId)
        }
    }
}

