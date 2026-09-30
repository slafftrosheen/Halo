package com.halo.app.data.notebook

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

enum class CanvasNodeType { TEXT, NOTE, IMAGE, FILE }

@Entity(
    tableName = "canvas_documents",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceObjectEntity::class,
            parentColumns = ["id"], childColumns = ["objectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CanvasDocumentEntity(
    @PrimaryKey val objectId: String,
    val viewportX: Float,
    val viewportY: Float,
    val viewportScale: Float,
    val revision: Int,
)

@Entity(
    tableName = "canvas_nodes",
    foreignKeys = [
        ForeignKey(
            entity = CanvasDocumentEntity::class,
            parentColumns = ["objectId"], childColumns = ["canvasId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("canvasId"), Index("refObjectId")],
)
data class CanvasNodeEntity(
    @PrimaryKey val id: String,
    val canvasId: String,
    val type: String,
    val refObjectId: String?,
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val zIndex: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "canvas_edges",
    foreignKeys = [
        ForeignKey(
            entity = CanvasDocumentEntity::class,
            parentColumns = ["objectId"], childColumns = ["canvasId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("canvasId"), Index("fromNodeId"), Index("toNodeId")],
)
data class CanvasEdgeEntity(
    @PrimaryKey val id: String,
    val canvasId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val label: String?,
    val createdAtEpochMs: Long,
)

data class CanvasNode(
    val id: String,
    val type: CanvasNodeType,
    val refObjectId: String?,
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val zIndex: Int,
)

data class CanvasEdge(val id: String, val fromNodeId: String, val toNodeId: String, val label: String?)

data class NotebookCanvas(
    val id: String,
    val title: String,
    val viewportX: Float,
    val viewportY: Float,
    val viewportScale: Float,
    val nodes: List<CanvasNode>,
    val edges: List<CanvasEdge>,
)

@Dao
interface NotebookCanvasDao {
    @Query("SELECT * FROM workspace_objects WHERE id = :canvasId LIMIT 1")
    fun observeWorkspaceObject(canvasId: String): Flow<WorkspaceObjectEntity?>

    @Query("SELECT * FROM canvas_documents WHERE objectId = :canvasId LIMIT 1")
    fun observeDocument(canvasId: String): Flow<CanvasDocumentEntity?>

    @Query("SELECT * FROM canvas_nodes WHERE canvasId = :canvasId ORDER BY zIndex ASC, createdAtEpochMs ASC")
    fun observeNodes(canvasId: String): Flow<List<CanvasNodeEntity>>

    @Query("SELECT * FROM canvas_edges WHERE canvasId = :canvasId ORDER BY createdAtEpochMs ASC")
    fun observeEdges(canvasId: String): Flow<List<CanvasEdgeEntity>>

    @Query("SELECT * FROM canvas_nodes WHERE canvasId = :canvasId ORDER BY zIndex ASC")
    suspend fun getNodes(canvasId: String): List<CanvasNodeEntity>

    @Query("SELECT * FROM canvas_edges WHERE canvasId = :canvasId")
    suspend fun getEdges(canvasId: String): List<CanvasEdgeEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEdge(entity: CanvasEdgeEntity)

    @Query("DELETE FROM canvas_edges WHERE canvasId = :canvasId AND (fromNodeId = :nodeId OR toNodeId = :nodeId)")
    suspend fun deleteNodeEdges(canvasId: String, nodeId: String)

    @Query("DELETE FROM canvas_edges WHERE canvasId = :canvasId AND id = :edgeId")
    suspend fun deleteEdge(canvasId: String, edgeId: String): Int

    @Query("UPDATE canvas_documents SET revision = revision + 1 WHERE objectId = :canvasId")
    suspend fun touchDocument(canvasId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocument(entity: CanvasDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNode(entity: CanvasNodeEntity)

    @Query("UPDATE canvas_nodes SET text = :text, updatedAtEpochMs = :updatedAt WHERE id = :nodeId AND canvasId = :canvasId")
    suspend fun updateNodeText(canvasId: String, nodeId: String, text: String, updatedAt: Long): Int

    @Query("UPDATE canvas_nodes SET x = :x, y = :y, updatedAtEpochMs = :updatedAt WHERE id = :nodeId AND canvasId = :canvasId")
    suspend fun updateNodePosition(canvasId: String, nodeId: String, x: Float, y: Float, updatedAt: Long): Int

    @Query("DELETE FROM canvas_nodes WHERE id = :nodeId AND canvasId = :canvasId")
    suspend fun deleteNode(canvasId: String, nodeId: String): Int

    @Query("UPDATE canvas_documents SET viewportX = :x, viewportY = :y, viewportScale = :scale, revision = revision + 1 WHERE objectId = :canvasId")
    suspend fun updateViewport(canvasId: String, x: Float, y: Float, scale: Float): Int

}

class NotebookCanvasRepository(
    private val database: AppDatabase,
    private val canvasDao: NotebookCanvasDao,
    private val notebookDao: NotebookDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun observe(canvasId: String): Flow<NotebookCanvas?> = combine(
        canvasDao.observeDocument(canvasId),
        canvasDao.observeNodes(canvasId),
        canvasDao.observeEdges(canvasId),
        canvasDao.observeWorkspaceObject(canvasId),
    ) { document, nodes, edges, workspace ->
        document ?: return@combine null
        workspace ?: return@combine null
        if (workspace.lifecycle != WorkspaceObjectLifecycle.ACTIVE.name) return@combine null
        NotebookCanvas(
            id = canvasId,
            title = workspace.title,
            viewportX = document.viewportX,
            viewportY = document.viewportY,
            viewportScale = document.viewportScale,
            nodes = nodes.map { node ->
                CanvasNode(
                    id = node.id,
                    type = runCatching { CanvasNodeType.valueOf(node.type) }.getOrDefault(CanvasNodeType.TEXT),
                    refObjectId = node.refObjectId,
                    text = node.text,
                    x = node.x, y = node.y, width = node.width, height = node.height, zIndex = node.zIndex,
                )
            },
            edges = edges.map { CanvasEdge(it.id, it.fromNodeId, it.toNodeId, it.label) },
        )
    }

    suspend fun create(title: String = "Untitled canvas"): String {
        val id = newId()
        val timestamp = now()
        database.withTransaction {
            notebookDao.insertObject(WorkspaceObjectEntity(
                id = id, type = WorkspaceObjectType.CANVAS.name,
                title = title, createdAt = timestamp, updatedAt = timestamp,
                source = WorkspaceObjectSource.USER_CREATED.name,
                lifecycle = WorkspaceObjectLifecycle.ACTIVE.name,
                inInbox = true, indexingState = "READY", contentRef = "canvas:$id",
            ))
            canvasDao.insertDocument(CanvasDocumentEntity(id, 0f, 0f, 1f, 1))
        }
        return id
    }

    suspend fun rename(canvasId: String, title: String): Boolean = database.withTransaction {
        requireWritable(canvasId)
        require(title.trim().isNotEmpty())
        val changed = notebookDao.updateTitle(canvasId, title.trim().take(160), now()) == 1
        if (changed) canvasDao.touchDocument(canvasId)
        changed
    }

    private suspend fun requireWritable(canvasId: String) {
        require(notebookDao.getWorkspaceObject(canvasId)?.lifecycle == WorkspaceObjectLifecycle.ACTIVE.name)
    }

    private suspend fun touch(canvasId: String) {
        val item = notebookDao.getWorkspaceObject(canvasId) ?: return
        notebookDao.updateTitle(canvasId, item.title, now())
        canvasDao.touchDocument(canvasId)
    }

    suspend fun addTextNode(canvasId: String, x: Float, y: Float, text: String = ""): String =
        addNode(canvasId, CanvasNodeType.TEXT, null, text, x, y)

    suspend fun addReference(canvasId: String, objectId: String, x: Float, y: Float, image: Boolean = false): String = database.withTransaction {
        require(objectId != canvasId)
        val item = requireNotNull(notebookDao.getWorkspaceObject(objectId))
        require(item.lifecycle == WorkspaceObjectLifecycle.ACTIVE.name)
        val type = when (item.type) {
            WorkspaceObjectType.NOTE.name -> CanvasNodeType.NOTE
            WorkspaceObjectType.MEDIA.name -> CanvasNodeType.IMAGE
            else -> CanvasNodeType.FILE
        }
        addNode(canvasId, if (image) CanvasNodeType.IMAGE else type, objectId, item.title, x, y)
    }

    private suspend fun addNode(canvasId: String, type: CanvasNodeType, reference: String?, text: String, x: Float, y: Float): String = database.withTransaction {
        val item = requireNotNull(notebookDao.getWorkspaceObject(canvasId))
        require(item.lifecycle == WorkspaceObjectLifecycle.ACTIVE.name)
        require(x.isFinite() && y.isFinite())
        val id = newId()
        val timestamp = now()
        val layer = (canvasDao.getNodes(canvasId).maxOfOrNull { it.zIndex } ?: -1) + 1
        canvasDao.insertNode(CanvasNodeEntity(id, canvasId, type.name, reference, text, x, y,
            240f, if (type == CanvasNodeType.IMAGE) 280f else 150f, layer, timestamp, timestamp))
        touch(canvasId)
        id
    }

    suspend fun duplicateNode(canvasId: String, nodeId: String): String = database.withTransaction {
        val node = requireNotNull(canvasDao.getNodes(canvasId).firstOrNull { it.id == nodeId })
        addNode(canvasId, CanvasNodeType.valueOf(node.type), node.refObjectId, node.text, node.x + 32f, node.y + 32f)
    }

    suspend fun connect(canvasId: String, from: String, to: String, label: String?): String = database.withTransaction {
        requireWritable(canvasId)
        require(from != to)
        val nodes = canvasDao.getNodes(canvasId).map { it.id }.toSet()
        require(from in nodes && to in nodes)
        val existing = canvasDao.getEdges(canvasId).firstOrNull { it.fromNodeId == from && it.toNodeId == to }
        if (existing != null) return@withTransaction existing.id
        val id = newId()
        canvasDao.insertEdge(CanvasEdgeEntity(id, canvasId, from, to, label?.trim()?.take(100)?.takeIf { it.isNotEmpty() }, now()))
        touch(canvasId)
        id
    }

    suspend fun disconnect(canvasId: String, edgeId: String): Boolean = database.withTransaction {
        requireWritable(canvasId)
        val changed = canvasDao.deleteEdge(canvasId, edgeId) == 1
        if (changed) touch(canvasId)
        changed
    }

    suspend fun updateText(canvasId: String, nodeId: String, text: String): Boolean = database.withTransaction {
        requireWritable(canvasId)
        val node = canvasDao.getNodes(canvasId).firstOrNull { it.id == nodeId } ?: return@withTransaction false
        if (node.refObjectId != null) return@withTransaction false
        val changed = canvasDao.updateNodeText(canvasId, nodeId, text, now()) == 1
        if (changed) touch(canvasId)
        changed
    }

    suspend fun moveNode(canvasId: String, nodeId: String, x: Float, y: Float): Boolean = database.withTransaction {
        requireWritable(canvasId)
        require(x.isFinite() && y.isFinite())
        val changed = canvasDao.updateNodePosition(canvasId, nodeId, x, y, now()) == 1
        if (changed) touch(canvasId)
        changed
    }

    suspend fun deleteNode(canvasId: String, nodeId: String): Boolean = database.withTransaction {
        requireWritable(canvasId)
        canvasDao.deleteNodeEdges(canvasId, nodeId)
        val changed = canvasDao.deleteNode(canvasId, nodeId) == 1
        if (changed) touch(canvasId)
        changed
    }

    suspend fun saveViewport(canvasId: String, x: Float, y: Float, scale: Float): Boolean {
        require(x.isFinite() && y.isFinite() && scale.isFinite())
        return canvasDao.updateViewport(canvasId, x, y, scale.coerceIn(0.35f, 2.5f)) == 1
    }
}
