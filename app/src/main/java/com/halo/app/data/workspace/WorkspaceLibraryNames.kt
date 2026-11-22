package com.halo.app.data.workspace

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.notebook.WorkspaceObjectType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest

@Entity(tableName = "workspace_library_title_aliases", foreignKeys = [ForeignKey(
    entity = WorkspaceObjectEntity::class, parentColumns = ["id"], childColumns = ["objectId"], onDelete = ForeignKey.CASCADE,
)])
data class WorkspaceLibraryTitleAlias(
    @PrimaryKey val objectId: String,
    val oldTitle: String,
    val newTitle: String,
    val expiresAt: Long,
)

@Dao
interface WorkspaceLibraryNameDao {
    @Query("""SELECT a.* FROM workspace_library_title_aliases a JOIN workspace_objects w ON w.id = a.objectId
        WHERE w.lifecycle = 'ACTIVE' AND w.title = a.newTitle""")
    fun observeCurrent(): Flow<List<WorkspaceLibraryTitleAlias>>

    @Query("DELETE FROM workspace_library_title_aliases WHERE expiresAt <= :now")
    suspend fun pruneExpired(now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(alias: WorkspaceLibraryTitleAlias)
}

private const val ALIAS_LIFETIME_MS = 30L * 24 * 60 * 60 * 1000

/** The alias is local UI history. The Brain archive keeps the committed owner titles only. */
class WorkspaceLibraryNames(private val database: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<Map<String, WorkspaceLibraryTitleAlias>> = database.workspaceLibraryNameDao().observeCurrent()
        .onStart { database.workspaceLibraryNameDao().pruneExpired(clock()) }
        .transformLatest { rows ->
            var now = clock()
            var current = rows.filter { it.expiresAt > now }
            emit(current.associateBy(WorkspaceLibraryTitleAlias::objectId))
            while (current.isNotEmpty()) {
                delay((current.minOf { it.expiresAt } - now).coerceAtLeast(1))
                now = clock()
                current = current.filter { it.expiresAt > now }
                emit(current.associateBy(WorkspaceLibraryTitleAlias::objectId))
                database.workspaceLibraryNameDao().pruneExpired(now)
            }
        }

    suspend fun rename(expected: WorkspaceObject, proposed: String, expectedFileVersion: String? = null,
        admitted: suspend () -> Boolean): Boolean = database.withTransaction {
        val title = proposed.trim()
        if (title.isBlank() || title.length > 160 || title.any(Char::isISOControl)) return@withTransaction false
        val notebook = database.notebookDao()
        val row = notebook.getWorkspaceObject(expected.id.value) ?: return@withTransaction false
        if (row.lifecycle != "ACTIVE" || row.type != expected.type.name || row.updatedAt != expected.updatedAt ||
            row.title != expected.title || row.contentRef != expected.contentRef || title == row.title) return@withTransaction false
        val file = database.workspaceFileDao().getFile(row.id)
        when (expected.type) {
            WorkspaceObjectType.NOTE -> if (row.contentRef != "note:${row.id}" || notebook.getDraft(row.id) != null) return@withTransaction false
            WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA -> if (file == null || !file.isReadable() ||
                file.ownership != ContentOwnership.HALO_OWNED.name || file.contentVersion() != expectedFileVersion ||
                title.contains('/') || title.contains('\\') || suffix(file.displayName) != suffix(title)) return@withTransaction false
            else -> return@withTransaction false
        }
        if (!admitted()) return@withTransaction false
        val now = clock()
        check(notebook.updateTitle(row.id, title, maxOf(now, row.updatedAt + 1)) == 1)
        if (file != null) check(database.workspaceFileDao().renameDisplayName(row.id, title) == 1)
        val aliases = database.workspaceLibraryNameDao()
        aliases.pruneExpired(now)
        val former = file?.displayName ?: row.title
        if (former.isNotBlank()) aliases.save(WorkspaceLibraryTitleAlias(row.id, former, title, now + ALIAS_LIFETIME_MS))
        true
    }

    private fun suffix(title: String): String = title.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
}
