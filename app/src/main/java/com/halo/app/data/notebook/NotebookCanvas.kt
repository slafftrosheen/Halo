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
    @Query("SELECT * FROM canvas_documents WHERE objectId = :canvasId LIMIT 1")
    fun observeDocument(canvasId: String): Flow<CanvasDocumentEntity?>

    @Query("SELECT * FROM canvas_nodes WHERE canvasId = :canvasId ORDER BY zIndex ASC, createdAtEpochMs ASC")
    fun observeNodes(canvasId: String): Flow<List<CanvasNodeEntity>>

    @Query("SELECT * FROM canvas_edges WHERE canvasId = :canvasId ORDER BY createdAtEpochMs ASC")
    fun observeEdges(canvasId: String): Flow<List<CanvasEdgeEntity>>

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
    ) { document, nodes, edges ->
        document ?: return@combine null
        val workspace = notebookDao.getWorkspaceObject(canvasId) ?: return@combine null
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
                id = id, type = WorkspaceObjectType.ARTIFACT.name,
                title = title, createdAt = timestamp, updatedAt = timestamp,
                source = WorkspaceObjectSource.USER_CREATED.name,
                lifecycle = WorkspaceObjectLifecycle.ACTIVE.name,
                inInbox = true, indexingState = "READY", contentRef = "canvas:$id",
            ))
            canvasDao.insertDocument(CanvasDocumentEntity(id, 0f, 0f, 1f, 1))
        }
        return id
    }

    suspend fun addTextNode(canvasId: String, x: Float, y: Float): String {
        val id = newId()
        val timestamp = now()
        canvasDao.insertNode(
            CanvasNodeEntity(
                id = id, canvasId = canvasId, type = CanvasNodeType.TEXT.name,
                refObjectId = null, text = "", x = x, y = y,
                width = 240f, height = 150f, zIndex = timestamp.toInt(),
                createdAtEpochMs = timestamp, updatedAtEpochMs = timestamp,
            )
        )
        return id
    }

    suspend fun updateText(canvasId: String, nodeId: String, text: String): Boolean =
        canvasDao.updateNodeText(canvasId, nodeId, text, now()) == 1

    suspend fun moveNode(canvasId: String, nodeId: String, x: Float, y: Float): Boolean =
        canvasDao.updateNodePosition(canvasId, nodeId, x, y, now()) == 1

    suspend fun deleteNode(canvasId: String, nodeId: String): Boolean =
        canvasDao.deleteNode(canvasId, nodeId) == 1

    suspend fun saveViewport(canvasId: String, x: Float, y: Float, scale: Float): Boolean =
        canvasDao.updateViewport(canvasId, x, y, scale.coerceIn(0.35f, 2.5f)) == 1
}
