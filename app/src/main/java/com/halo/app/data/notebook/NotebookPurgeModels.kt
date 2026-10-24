package com.halo.app.data.notebook

import androidx.room.*

/** Content-free domain receipt, retained after the original note is gone. */
@Entity(tableName = "notebook_purges", indices = [Index(value = ["state", "lastAttemptOrder"])])
data class NotebookPurgeEntity(
    @PrimaryKey val noteId: String,
    val sourceVersion: String,
    val recoveryVersion: String,
    val includeRecovery: Boolean,
    val revision: Long,
    val state: String,
    val requestedAt: Long,
    val pending: Int,
    val exclusiveCleared: Int,
    val sharedRetained: Int,
    val externalRetained: Int,
    val reason: String?,
    @ColumnInfo(defaultValue = "0") val lastAttemptOrder: Long = 0,
)

/** Existing asset rows keep paths; this journal keeps only exact identities awaiting cleanup. */
@Entity(tableName = "notebook_purge_files", foreignKeys = [ForeignKey(entity = NotebookPurgeEntity::class,
    parentColumns = ["noteId"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)], indices = [Index("noteId")])
data class NotebookPurgeFileEntity(@PrimaryKey val assetId: String, val noteId: String, val identity: String)

data class NotebookPurgeReceipt(val record: NotebookPurgeEntity, val sourcePresent: Boolean) {
    val version: String get() = "note-purge:" + NotebookNoteIdentity.digest(listOf(record.noteId, record.sourceVersion, record.recoveryVersion, record.includeRecovery, record.revision,
        record.state, record.pending, record.exclusiveCleared, record.sharedRetained, record.externalRetained, record.reason, record.lastAttemptOrder, sourcePresent).joinToString("|"))
}

sealed interface NotebookPurgeResult {
    data class Saved(val receipt: NotebookPurgeReceipt, val alreadyRecorded: Boolean = false) : NotebookPurgeResult
    data class Refused(val reason: String) : NotebookPurgeResult
}

@Dao
interface NotebookPurgeDao {
    @Query("SELECT * FROM notebook_purges WHERE noteId=:id") suspend fun get(id: String): NotebookPurgeEntity?
    @Insert suspend fun insert(receipt: NotebookPurgeEntity)
    @Update suspend fun update(receipt: NotebookPurgeEntity)
    @Insert suspend fun insertFiles(files: List<NotebookPurgeFileEntity>)
    @Query("SELECT * FROM notebook_purge_files WHERE noteId=:id ORDER BY assetId LIMIT 1") suspend fun nextFile(id: String): NotebookPurgeFileEntity?
    @Query("DELETE FROM notebook_purge_files WHERE assetId=:id") suspend fun removeFile(id: String): Int
    @Query("DELETE FROM notebook_purge_files WHERE noteId=:id") suspend fun removeFilesForNote(id: String): Int
    @Query("SELECT noteId FROM notebook_purges WHERE state='PENDING' ORDER BY lastAttemptOrder,requestedAt,noteId LIMIT :limit") suspend fun pending(limit: Int): List<String>
    @Query("SELECT COALESCE(MAX(lastAttemptOrder),0)+1 FROM notebook_purges") suspend fun nextAttemptOrder(): Long
    @Query("SELECT COUNT(*) FROM notebook_purges WHERE state='PENDING'") suspend fun pendingCount(): Int
    @Query("SELECT * FROM workspace_assets WHERE id=:id") suspend fun asset(id: String): com.halo.app.data.workspace.WorkspaceAssetEntity?
    @Query("SELECT pathOrUri FROM workspace_assets WHERE objectId!=:noteId") suspend fun otherAssetPaths(noteId: String): List<String>
    @Query("SELECT uri FROM workspace_files UNION SELECT ownedCopyPath FROM workspace_files WHERE ownedCopyPath IS NOT NULL") suspend fun filePaths(): List<String>
    @Query("DELETE FROM workspace_assets WHERE id=:id") suspend fun removeAsset(id: String): Int
}
