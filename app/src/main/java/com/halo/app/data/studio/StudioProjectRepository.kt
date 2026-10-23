package com.halo.app.data.studio

import androidx.room.*
import com.halo.app.data.AppDatabase
import com.halo.app.data.ObjectProvenance
import com.halo.app.data.notebook.WorkspaceObjectEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "studio_projects", foreignKeys = [ForeignKey(entity = WorkspaceObjectEntity::class,
    parentColumns = ["id"], childColumns = ["objectId"], onDelete = ForeignKey.CASCADE)])
data class StudioProjectEntity(@PrimaryKey val objectId: String, val revision: Long,
    val committed: String, val session: String)

@Entity(tableName = "studio_project_versions", primaryKeys = ["objectId", "revision"],
    foreignKeys = [ForeignKey(entity = StudioProjectEntity::class, parentColumns = ["objectId"],
        childColumns = ["objectId"], onDelete = ForeignKey.CASCADE)])
data class StudioProjectVersion(val objectId: String, val revision: Long, val document: String, val savedAt: Long)

@Dao
interface StudioProjectDao {
    @Query("SELECT * FROM studio_projects WHERE objectId = :id")
    suspend fun get(id: String): StudioProjectEntity?
    @Query("SELECT * FROM studio_projects WHERE objectId = :id")
    fun observe(id: String): Flow<StudioProjectEntity?>
    @Query("SELECT w.* FROM workspace_objects w INNER JOIN studio_projects s ON s.objectId=w.id WHERE w.lifecycle='ACTIVE' ORDER BY w.updatedAt DESC LIMIT 100")
    fun observeProjects(): Flow<List<WorkspaceObjectEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: StudioProjectEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVersion(version: StudioProjectVersion)
    @Query("SELECT * FROM studio_project_versions WHERE objectId=:id ORDER BY revision DESC LIMIT 100")
    suspend fun versions(id: String): List<StudioProjectVersion>
    @Query("SELECT * FROM studio_project_versions WHERE objectId=:id AND revision=:revision")
    suspend fun version(id: String, revision: Long): StudioProjectVersion?
    @Query("UPDATE studio_projects SET revision=revision+1, committed=:committed, session=:session WHERE objectId=:id AND revision=:expected")
    suspend fun update(id: String, expected: Long, committed: String, session: String): Int
}

data class StudioSession(val current: StudioDocument, val undo: List<StudioDocument> = emptyList(),
    val redo: List<StudioDocument> = emptyList()) {
    fun apply(command: StudioCommand): StudioSession {
        val next = current.apply(command)
        if (next == current) return this
        // Selection is durable, but should not make Undo consume a purely navigational step.
        return if (command is StudioCommand.Select) copy(current = next)
        else StudioSession(next, (undo + current).takeLast(StudioDocument.MAX_HISTORY), emptyList()).bounded()
    }
    fun undo(): StudioSession {
        check(undo.isNotEmpty())
        return StudioSession(undo.last(), undo.dropLast(1), (redo + current).takeLast(StudioDocument.MAX_HISTORY)).bounded()
    }
    fun redo(): StudioSession {
        check(redo.isNotEmpty())
        return StudioSession(redo.last(), (undo + current).takeLast(StudioDocument.MAX_HISTORY), redo.dropLast(1)).bounded()
    }
    private fun bounded(): StudioSession {
        var result = this
        while (true) {
            val snapshots = result.undo + result.redo
            val bytes = snapshots.sumOf { StudioDocumentCodec.encode(it).length.toLong() }
            val points = snapshots.sumOf { doc -> doc.layers.sumOf { layer ->
                com.halo.sdk.runtime.ink.InkCodec.decode(layer.ink)?.strokes?.sumOf { it.points.size } ?: 0
            } }
            if (bytes <= MAX_HISTORY_CHARS && points <= 262_144) return result
            result = if (result.undo.isNotEmpty()) result.copy(undo = result.undo.drop(1))
                else result.copy(redo = result.redo.drop(1))
        }
    }
    fun encode(): String {
        current.requireValid()
        require(undo.size <= StudioDocument.MAX_HISTORY && redo.size <= StudioDocument.MAX_HISTORY)
        val value = bounded()
        return JSONObject().put("schema", 1).put("current", StudioDocumentCodec.encode(value.current))
            .put("undo", JSONArray(value.undo.map(StudioDocumentCodec::encode)))
            .put("redo", JSONArray(value.redo.map(StudioDocumentCodec::encode))).toString()
            .also { require(it.length <= MAX_SESSION_CHARS) }
    }
    companion object {
        private const val MAX_HISTORY_CHARS = 4_000_000
        private const val MAX_SESSION_CHARS = 9_000_000
        fun decode(encoded: String): StudioSession {
            require(encoded.length in 2..MAX_SESSION_CHARS)
            val root = JSONObject(encoded)
            require(root.keys().asSequence().toSet() == setOf("schema", "current", "undo", "redo"))
            require(root.get("schema") == 1)
            fun history(key: String): List<StudioDocument> {
                val array = root.getJSONArray(key)
                require(array.length() <= StudioDocument.MAX_HISTORY)
                return (0 until array.length()).map { StudioDocumentCodec.decode(array.get(it) as String) }
            }
            val result = StudioSession(StudioDocumentCodec.decode(root.get("current") as String), history("undo"), history("redo"))
            // Refuse envelopes whose stated history exceeds the supported budget; never silently discard on load.
            require(result == result.bounded())
            return result
        }
    }
}

data class StudioProject(val id: String, val title: String, val revision: Long,
    val committed: StudioDocument, val session: StudioSession) {
    val hasUnsavedChanges: Boolean get() = session.current.copy(selectedLayerId = null) != committed.copy(selectedLayerId = null)
}

/** All persisted state belongs to the existing Workspace/Room transaction owner. */
class StudioProjectRepository(private val database: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis, private val newId: () -> String = { UUID.randomUUID().toString() }) {
    private val dao get() = database.studioProjectDao()
    fun observeProjects(): Flow<List<WorkspaceObjectEntity>> = dao.observeProjects()
    fun observe(id: String): Flow<StudioProject?> = combine(dao.observe(id), database.notebookCanvasDao().observeWorkspaceObject(id)) { row, header ->
        if (row == null || header?.lifecycle != "ACTIVE" || header.type != "ARTIFACT" || header.contentRef != "studio:$id") null else decode(row, header.title)
    }
    suspend fun get(id: String): StudioProject? = database.withTransaction { current(id) }
    private suspend fun current(id: String): StudioProject? {
        val header = database.notebookDao().getWorkspaceObject(id) ?: return null
        if (header.lifecycle != "ACTIVE" || header.type != "ARTIFACT" || header.contentRef != "studio:$id") return null
        return dao.get(id)?.let { decode(it, header.title) }
    }
    private fun decode(row: StudioProjectEntity, title: String): StudioProject {
        require(row.revision in 1..MAX_REVISION && row.objectId.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        require(title.isNotBlank() && title.length <= 120)
        return StudioProject(row.objectId, title, row.revision, StudioDocumentCodec.decode(row.committed), StudioSession.decode(row.session))
    }
    suspend fun create(title: String, document: StudioDocument = StudioDocument(), sourceProject: String? = null, sourceRevision: Long? = null): StudioProject = database.withTransaction {
        document.requireValid(); require(title.isNotBlank() && title.length <= 120)
        val id = newId(); val timestamp = now()
        database.notebookDao().insertWorkspaceObject(WorkspaceObjectEntity(id, "ARTIFACT", title, timestamp, timestamp,
            "USER_CREATED", "ACTIVE", false, "READY", "studio:$id"))
        val encoded = StudioDocumentCodec.encode(document)
        dao.insert(StudioProjectEntity(id, 1, encoded, StudioSession(document).encode()))
        dao.insertVersion(StudioProjectVersion(id, 1, encoded, timestamp))
        if (sourceProject != null) {
            val source = requireNotNull(database.notebookDao().getWorkspaceObject(sourceProject))
            val revision = sourceRevision ?: requireNotNull(dao.get(sourceProject)).revision
            require(revision > 0)
            database.workspaceContextDao().provenance(ObjectProvenance(id, source.id, revision.toString(),
                JSONObject().put("operation", "studio-save-as").toString()))
        }
        requireNotNull(current(id))
    }
    suspend fun apply(id: String, expectedRevision: Long, command: StudioCommand): StudioProject =
        edit(id, expectedRevision) { it.copy(session = it.session.apply(command)) }
    suspend fun undo(id: String, expectedRevision: Long): StudioProject = edit(id, expectedRevision) { it.copy(session = it.session.undo()) }
    suspend fun redo(id: String, expectedRevision: Long): StudioProject = edit(id, expectedRevision) { it.copy(session = it.session.redo()) }
    suspend fun save(id: String, expectedRevision: Long): StudioProject = database.withTransaction {
        val result = edit(id, expectedRevision) { it.copy(committed = it.session.current) }
        if (dao.version(id, result.revision) == null) dao.insertVersion(StudioProjectVersion(id, result.revision,
            StudioDocumentCodec.encode(result.committed), now()))
        result
    }
    suspend fun versions(id: String): List<StudioProjectVersion> = database.withTransaction {
        requireNotNull(current(id)); dao.versions(id)
    }
    suspend fun restoreVersion(id: String, expectedRevision: Long, version: Long): StudioProject = database.withTransaction {
        val saved = requireNotNull(dao.version(id, version))
        val document = StudioDocumentCodec.decode(saved.document)
        edit(id, expectedRevision) { project -> project.copy(session = StudioSession(document, (project.session.undo + project.session.current).takeLast(StudioDocument.MAX_HISTORY))) }
    }
    suspend fun discardDraft(id: String, expectedRevision: Long): StudioProject = edit(id, expectedRevision) { it.copy(session = StudioSession(it.committed)) }
    suspend fun rename(id: String, expectedRevision: Long, title: String): StudioProject {
        require(title.isNotBlank() && title.length <= 120)
        return edit(id, expectedRevision) { it.copy(title = title) }
    }
    suspend fun saveAs(id: String, expectedRevision: Long, title: String): StudioProject = database.withTransaction {
        val source = requireNotNull(current(id)); check(source.revision == expectedRevision) { "Project changed. Reopen it before saving a copy." }
        create(title, source.session.current, source.id)
    }
    private suspend fun edit(id: String, expectedRevision: Long, change: (StudioProject) -> StudioProject): StudioProject = database.withTransaction {
        val existing = requireNotNull(current(id)) { "Project unavailable" }
        check(existing.revision == expectedRevision) { "Project changed. Reopen it before editing." }
        val updated = change(existing)
        check(expectedRevision < MAX_REVISION) { "Project version limit reached" }
        if (updated == existing) return@withTransaction existing
        check(dao.update(id, expectedRevision, StudioDocumentCodec.encode(updated.committed), updated.session.encode()) == 1)
        check(database.notebookDao().updateTitle(id, updated.title, now()) == 1)
        requireNotNull(current(id))
    }
    companion object { const val MAX_REVISION = 9_007_199_254_740_991L }

}
