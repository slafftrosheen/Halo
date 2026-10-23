package com.halo.app.data.notebook

import com.halo.app.data.workspace.contentVersion
import com.halo.app.data.workspace.isReadable

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

enum class CanvasNodeType { TEXT, NOTE, IMAGE, FILE, SKETCH, INK_SELECTION, GROUP, TAG }

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
    val revision: Long = 0,
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

data class CanvasImageSearchSource(
    val canvasId: String,
    val nodeId: String,
    val objectId: String,
    val canvasTitle: String,
    val displayName: String,
    val mimeType: String,
)

@Dao
interface NotebookCanvasDao {
    @Query(
        """
        SELECT n.canvasId AS canvasId, n.id AS nodeId, f.objectId AS objectId,
               c.title AS canvasTitle, f.displayName AS displayName, f.mimeType AS mimeType
        FROM canvas_nodes n
        INNER JOIN workspace_objects c ON c.id = n.canvasId
        INNER JOIN workspace_objects source ON source.id = n.refObjectId
        INNER JOIN workspace_files f ON f.objectId = source.id
        WHERE n.type = 'IMAGE' AND c.type = 'CANVAS' AND c.lifecycle = 'ACTIVE'
          AND source.lifecycle = 'ACTIVE' AND source.type IN ('FILE', 'MEDIA')
          AND LOWER(f.mimeType) IN ('image/png', 'image/jpeg')
        ORDER BY n.updatedAtEpochMs DESC
        LIMIT 256
        """
    )
    fun observeImageSearchSources(): Flow<List<CanvasImageSearchSource>>

    @Query("DELETE FROM canvas_edges WHERE canvasId = :canvasId")
    suspend fun clearEdges(canvasId: String)
    @Query("DELETE FROM canvas_nodes WHERE canvasId = :canvasId")
    suspend fun clearNodes(canvasId: String)
    @Query("UPDATE canvas_nodes SET width = :width, height = :height WHERE id = :nodeId AND canvasId = :canvasId")
    suspend fun resizeNode(canvasId: String, nodeId: String, width: Float, height: Float)
    @Query("UPDATE canvas_nodes SET zIndex = :zIndex WHERE id = :nodeId AND canvasId = :canvasId")
    suspend fun layerNode(canvasId: String, nodeId: String, zIndex: Int)
    @Query("SELECT * FROM workspace_objects WHERE id = :canvasId LIMIT 1")
    fun observeWorkspaceObject(canvasId: String): Flow<WorkspaceObjectEntity?>

    @Query("SELECT * FROM canvas_documents WHERE objectId = :canvasId LIMIT 1")
    fun observeDocument(canvasId: String): Flow<CanvasDocumentEntity?>

    @Query("SELECT * FROM canvas_documents WHERE objectId = :canvasId LIMIT 1")
    suspend fun getDocument(canvasId: String): CanvasDocumentEntity?

    @Query("SELECT * FROM canvas_nodes WHERE canvasId = :canvasId ORDER BY zIndex ASC, createdAtEpochMs ASC")
    fun observeNodes(canvasId: String): Flow<List<CanvasNodeEntity>>

    @Query("SELECT * FROM canvas_edges WHERE canvasId = :canvasId ORDER BY createdAtEpochMs ASC")
    fun observeEdges(canvasId: String): Flow<List<CanvasEdgeEntity>>

    @Query("SELECT * FROM canvas_nodes WHERE canvasId = :canvasId ORDER BY zIndex ASC")
    suspend fun getNodes(canvasId: String): List<CanvasNodeEntity>

    @Query("SELECT * FROM canvas_nodes WHERE canvasId=:canvasId AND id=:nodeId LIMIT 1")
    suspend fun getNode(canvasId: String, nodeId: String): CanvasNodeEntity?


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
    suspend fun drawingDraft(canvasId: String): CanvasDrawingDraft? = notebookDao.getDraft(canvasId)?.structureJson?.let(CanvasDrawingDraft::decode)
    suspend fun saveDrawingDraft(canvasId: String, draft: CanvasDrawingDraft) {
        requireWritable(canvasId)
        notebookDao.upsertDraft(NoteDraftEntity(canvasId, "", now(), structureJson = draft.encode()))
    }
    suspend fun clearDrawingDraft(canvasId: String) = notebookDao.deleteLegacyDraft(canvasId)
    suspend fun snapshot(canvasId: String): Pair<List<CanvasNodeEntity>, List<CanvasEdgeEntity>> = database.withTransaction {
        canvasDao.getNodes(canvasId) to canvasDao.getEdges(canvasId)
    }
    suspend fun sourceObjectVersion(objectId: String): String? {
        val objectRow = notebookDao.getWorkspaceObject(objectId)?.takeIf { it.lifecycle == "ACTIVE" } ?: return null
        val note = notebookDao.getNote(objectId)
        val file = database.workspaceFileDao().getFile(objectId)
        return "${objectRow.updatedAt}:${note?.revision}:${file?.sha256}:${file?.modifiedAt}"
    }
    suspend fun addReferences(canvasId: String, ids: List<String>) = database.withTransaction {
        require(ids.size <= 32)
        val existing = canvasDao.getNodes(canvasId).mapNotNull { it.refObjectId }.toSet()
        ids.distinct().filterNot { it in existing }.forEachIndexed { index, id -> addReference(canvasId, id, index * 280f, 0f) }
    }
    suspend fun tagNodes(canvasId: String, ids: Set<String>, label: String): Pair<String, CanvasUndoSnapshot> = database.withTransaction {
        requireWritable(canvasId)
        val (nodes, edges) = snapshot(canvasId)
        val selected = nodes.filter { it.id in ids }
        require(selected.size == ids.size && selected.isNotEmpty())
        val tag = addTagNode(canvasId, selected.minOf { it.x }, selected.minOf { it.y } - 115f, label)
        selected.forEach { connect(canvasId, it.id, tag, "tag") }
        tag to CanvasUndoSnapshot(nodes, edges, boardFingerprint(canvasDao.getNodes(canvasId), canvasDao.getEdges(canvasId)))
    }
    suspend fun connectNodes(canvasId: String, ids: Set<String>, to: String, label: String): CanvasUndoSnapshot = database.withTransaction {
        requireWritable(canvasId)
        val (nodes, edges) = snapshot(canvasId)
        require(ids.isNotEmpty())
        ids.filter { it != to }.forEach { connect(canvasId, it, to, label) }
        CanvasUndoSnapshot(nodes, edges, boardFingerprint(canvasDao.getNodes(canvasId), canvasDao.getEdges(canvasId)))
    }

    suspend fun applyOrganization(proposal: CanvasOrganizationProposal): CanvasUndoSnapshot? = database.withTransaction {
        proposal.validate()
        val canvasId = proposal.canvasId
        requireWritable(canvasId)
        // Stable operation ids make a repeated approval/retry a no-op after process recreation.
        val receipt = "organization:${proposal.id}"
        val receiptMetadata = database.workspaceContextDao().provenanceMetadata(canvasId)
        val receipts = receiptMetadata?.let { runCatching { org.json.JSONObject(it).optJSONArray("organizationReceipts") }.getOrNull() }
        val receiptIds = receipts?.let { array -> (0 until array.length()).map(array::getString) }.orEmpty()
        if (proposal.id in receiptIds || database.workspaceContextDao().captureObject(canvasId, receipt) != null) return@withTransaction null
        val (beforeNodes, beforeEdges) = snapshot(canvasId)
        check(proposal.sourceEdgesFingerprint == null || proposal.sourceEdgesFingerprint == canvasEdgesFingerprint(
            beforeEdges.filter { it.fromNodeId in proposal.sourceFingerprints || it.toNodeId in proposal.sourceFingerprints })) { "Source connections changed. Create a new preview." }
        val domain = beforeNodes.associate { it.id to CanvasNode(it.id, CanvasNodeType.valueOf(it.type), it.refObjectId,
            it.text, it.x, it.y, it.width, it.height, it.zIndex, it.updatedAtEpochMs) }
        check(proposal.sourceFingerprints.all { (id, fingerprint) -> domain[id]?.let(::canvasNodeFingerprint) == fingerprint }) { "Source cards changed. Create a new preview." }
        check(proposal.sourceObjectVersions.all { (id, revision) -> sourceObjectVersion(id) == revision }) { "Source items changed. Create a new preview." }
        val originX = proposal.sourceFingerprints.keys.mapNotNull(domain::get).minOf { it.x }
        val originY = proposal.sourceFingerprints.keys.mapNotNull(domain::get).minOf { it.y }
        val stamp = now()
        val regrouped = proposal.groups.flatMap { it.memberIds }.toSet()
        beforeEdges.filter { it.label == "group" && it.toNodeId in regrouped }.forEach { canvasDao.deleteEdge(canvasId, it.id) }
        val rowHeight = proposal.groups.maxOf { group -> group.memberIds.sumOf { domain.getValue(it).height.toDouble() + 28.0 }.toFloat() + 100f }
        proposal.groups.forEachIndexed { index, group ->
            val groupId = "${proposal.id}:group:$index"
            val x = originX + index % 3 * 560f
            val y = originY + index / 3 * (rowHeight + 40f)
            val height = (group.memberIds.sumOf { domain.getValue(it).height.toDouble() + 28.0 }.toFloat() + 80f).coerceAtLeast(340f)
            canvasDao.insertNode(CanvasNodeEntity(groupId, canvasId, "GROUP", null, group.title, x, y,
                520f, height, -1, stamp, stamp))
            var memberY = y + 64f
            group.memberIds.forEach { memberId ->
                val member = domain.getValue(memberId)
                check(canvasDao.updateNodePosition(canvasId, memberId, x + 28f, memberY, stamp) == 1)
                memberY += member.height + 28f
                canvasDao.insertEdge(CanvasEdgeEntity("${proposal.id}:member:$memberId", canvasId, groupId, memberId, "group", stamp))
            }
        }
        // The receipt is durable even when users opt out of the linked checklist note.
        val priorProvenance = database.workspaceContextDao().provenanceFor(canvasId)
        val metadata = receiptMetadata?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } ?: org.json.JSONObject()
        metadata.put("organizationReceipts", org.json.JSONArray((receiptIds + proposal.id).takeLast(128)))
        database.workspaceContextDao().provenance(com.halo.app.data.ObjectProvenance(canvasId,
            priorProvenance?.sourceId ?: canvasId, priorProvenance?.revision ?: receipt, metadata.toString()))
        if (proposal.createLinkedNote) {
            val noteId = "${proposal.id}:project-note"
            val body = buildString {
                proposal.groups.forEach { group ->
                    append("## ").append(group.title).append('\n')
                    group.memberIds.forEach { id ->
                        val node = domain.getValue(id)
                        append("- ").append(when (node.type) {
                            CanvasNodeType.SKETCH -> "[Sketch]"
                            CanvasNodeType.INK_SELECTION -> "[Saved ink selection]"
                            else -> CanvasFileMarkupCodec.displayTitle(node.text).take(300)
                        }).append('\n')
                        append("  Source: ").append(node.refObjectId ?: "canvas:$canvasId#${node.id}").append('\n')
                    }
                }
                if (proposal.nextSteps.isNotEmpty()) { append("\n## Approved next steps\n"); proposal.nextSteps.forEach { append("- [ ] ").append(it).append('\n') } }
            }
            notebookDao.insertNoteObject(WorkspaceObjectEntity(noteId, "NOTE", notebookDao.getWorkspaceObject(canvasId)!!.title,
                stamp, stamp, WorkspaceObjectSource.USER_CREATED.name, "ACTIVE", true, "READY", "note:$noteId"), NoteEntity(noteId, body, 1))
            database.workspaceContextDao().provenance(com.halo.app.data.ObjectProvenance(noteId, canvasId, receipt, "Approved project checklist"))
            addReference(canvasId, noteId, originX, originY - 220f)
        }
        touch(canvasId)
        CanvasUndoSnapshot(beforeNodes, beforeEdges, boardFingerprint(canvasDao.getNodes(canvasId), canvasDao.getEdges(canvasId)))
    }

    suspend fun undo(canvasId: String, snapshot: CanvasUndoSnapshot) = database.withTransaction {
        requireWritable(canvasId)
        check(boardFingerprint(canvasDao.getNodes(canvasId), canvasDao.getEdges(canvasId)) == snapshot.afterFingerprint) { "Canvas changed. Undo would overwrite newer edits." }
        canvasDao.clearEdges(canvasId); canvasDao.clearNodes(canvasId)
        snapshot.nodes.forEach { canvasDao.insertNode(it) }; snapshot.edges.forEach { canvasDao.insertEdge(it) }
        touch(canvasId)
    }

    suspend fun manualEdit(canvasId: String, ids: Set<String>, action: String): CanvasUndoSnapshot = database.withTransaction {
        requireWritable(canvasId)
        val (nodes, edges) = snapshot(canvasId)
        require(ids.isNotEmpty() && ids.all { id -> nodes.any { it.id == id } })
        val selected = nodes.filter { it.id in ids }
        when (action) {
            "Clear" -> selected.forEach { canvasDao.deleteNodeEdges(canvasId, it.id); canvasDao.deleteNode(canvasId, it.id) }
            "Ungroup" -> selected.filter { it.type == "GROUP" }.forEach { canvasDao.deleteNodeEdges(canvasId, it.id); canvasDao.deleteNode(canvasId, it.id) }
            "Arrange" -> selected.forEachIndexed { index, node -> canvasDao.updateNodePosition(canvasId, node.id,
                selected.minOf { it.x } + index % 3 * 280f, selected.minOf { it.y } + index / 3 * 320f, now()) }
            "Bring forward" -> selected.forEachIndexed { index, node -> canvasDao.layerNode(canvasId, node.id, (nodes.maxOfOrNull { it.zIndex } ?: 0) + index + 1) }
            else -> error("Unknown local operation")
        }
        touch(canvasId)
        CanvasUndoSnapshot(nodes, edges, boardFingerprint(canvasDao.getNodes(canvasId), canvasDao.getEdges(canvasId)))
    }
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
                    revision = node.updatedAtEpochMs,
                )
            },
            edges = edges.map { CanvasEdge(it.id, it.fromNodeId, it.toNodeId, it.label) },
        )
    }

    fun observeImageSearchSources(): Flow<List<CanvasImageSearchSource>> = canvasDao.observeImageSearchSources()

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

    /** Group is a persistent spatial zone. Moving it moves cards currently inside its bounds. */
    suspend fun addGroupNode(canvasId: String, x: Float, y: Float, title: String): String =
        addNode(canvasId, CanvasNodeType.GROUP, null, title.trim().ifBlank { "Group" }.take(80), x, y)

    /** A reusable-looking, editable label card; the tagged card links to it with edge label 'tag'. */
    suspend fun addTagNode(canvasId: String, x: Float, y: Float, title: String): String {
        val value = title.trim().trimStart('#').trim()
        require(value.isNotEmpty())
        return addNode(canvasId, CanvasNodeType.TAG, null, value.take(50), x, y)
    }

    /** Sketch strokes are versioned text in the existing node row; no schema migration. */
    suspend fun addSketchNode(canvasId: String, x: Float, y: Float, strokes: String): String {
        require(CanvasSketchCodec.decode(strokes).isNotEmpty())
        return addNode(canvasId, CanvasNodeType.SKETCH, null, strokes, x, y)
    }

    /** Persist only the explicit selection reference; source strokes remain in their original card. */
    suspend fun addInkSelection(canvasId: String, context: com.halo.sdk.runtime.ink.InkSelectionContext): String = database.withTransaction {
        requireWritable(canvasId)
        require(context.workspaceObjectId == canvasId) { "Only selections from saved Canvas sketches can be bookmarked yet." }
        require(context.workspaceObjectId.length <= com.halo.sdk.runtime.ink.InkSelectionContext.MAX_REFERENCE_LENGTH)
        val nodes = canvasDao.getNodes(canvasId)
        val source = nodes.firstOrNull { it.id == context.pageId && it.type == CanvasNodeType.SKETCH.name }
        requireNotNull(source) { "Selection source is no longer on this Canvas." }
        require(InkSelectionBookmarkCodec.resolve(context, canvasId, CanvasNodeType.SKETCH, source.id,
            source.refObjectId, source.text) != null) {
            "Source changed. Select the region again."
        }
        val id = insertInkSelectionBookmark(canvasId, source, context, nodes)
        touch(canvasId)
        id
    }

    private suspend fun insertInkSelectionBookmark(
        canvasId: String,
        source: CanvasNodeEntity,
        context: com.halo.sdk.runtime.ink.InkSelectionContext,
        currentNodes: List<CanvasNodeEntity>,
    ): String {
        val id = newId()
        val stamp = now()
        val payload = InkSelectionBookmarkCodec.encode(context)
        val layer = (currentNodes.maxOfOrNull { it.zIndex } ?: -1) + 1
        canvasDao.insertNode(CanvasNodeEntity(id, canvasId, CanvasNodeType.INK_SELECTION.name, null, payload,
            source.x + source.width + 32f, source.y, 220f, 150f, layer, stamp, stamp))
        canvasDao.insertEdge(CanvasEdgeEntity(newId(), canvasId, source.id, id, "selection", stamp))
        return id
    }

    suspend fun addReference(canvasId: String, objectId: String, x: Float, y: Float, image: Boolean = false): String = database.withTransaction {
        require(objectId != canvasId)
        val item = requireNotNull(notebookDao.getWorkspaceObject(objectId))
        require(item.lifecycle == WorkspaceObjectLifecycle.ACTIVE.name)
        val type = when (item.type) {
            WorkspaceObjectType.NOTE.name -> CanvasNodeType.NOTE
            WorkspaceObjectType.MEDIA.name -> CanvasNodeType.IMAGE
            else -> CanvasNodeType.FILE
        }
        val imageSource = image || notebookDao.getAssetsForObject(objectId).any { it.type == "IMAGE" }
        addNode(canvasId, if (imageSource) CanvasNodeType.IMAGE else type, objectId, item.title, x, y)
    }

    private suspend fun addNode(canvasId: String, type: CanvasNodeType, reference: String?, text: String, x: Float, y: Float): String = database.withTransaction {
        val item = requireNotNull(notebookDao.getWorkspaceObject(canvasId))
        require(item.lifecycle == WorkspaceObjectLifecycle.ACTIVE.name)
        require(x.isFinite() && y.isFinite())
        val id = newId()
        val timestamp = now()
        val layer = (canvasDao.getNodes(canvasId).maxOfOrNull { it.zIndex } ?: -1) + 1
        canvasDao.insertNode(CanvasNodeEntity(id, canvasId, type.name, reference, text, x, y,
            if (type == CanvasNodeType.GROUP) 460f else if (type == CanvasNodeType.TAG) 168f else 240f,
            when (type) {
                CanvasNodeType.GROUP -> 340f
                CanvasNodeType.TAG -> 96f
                CanvasNodeType.IMAGE, CanvasNodeType.SKETCH, CanvasNodeType.FILE -> 280f
                else -> 150f
            }, layer, timestamp, timestamp))
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

    suspend fun updateText(canvasId: String, nodeId: String, text: String, expectedText: String? = null): Boolean = database.withTransaction {
        requireWritable(canvasId)
        val node = canvasDao.getNodes(canvasId).firstOrNull { it.id == nodeId } ?: return@withTransaction false
        if (node.refObjectId != null) return@withTransaction false
        check(expectedText == null || node.text == expectedText) { "Source changed. Review it again." }
        if (node.type == CanvasNodeType.SKETCH.name) require(CanvasSketchCodec.decode(text).isNotEmpty())
        val changed = canvasDao.updateNodeText(canvasId, nodeId, text, now()) == 1
        if (changed) touch(canvasId)
        changed
    }

    /** Keep imported PDF/image original bytes unchanged; only card-local marks are saved. */
    suspend fun saveFileMarkup(canvasId: String, nodeId: String, pages: Map<Int, String>, expectedText: String? = null,
        expectedFileVersion: String? = null, selection: com.halo.sdk.runtime.ink.InkSelectionContext? = null): Boolean = database.withTransaction {
        requireWritable(canvasId)
        val node = canvasDao.getNodes(canvasId).firstOrNull { it.id == nodeId } ?: return@withTransaction false
        if (node.refObjectId == null || node.type !in setOf(CanvasNodeType.FILE.name, CanvasNodeType.IMAGE.name)) return@withTransaction false
        check(expectedText == null || node.text == expectedText) { "Annotations changed. Reopen the source before saving." }
        val file = database.workspaceFileDao().getFile(node.refObjectId)
        check(notebookDao.getWorkspaceObject(node.refObjectId)?.lifecycle == "ACTIVE" && file?.isReadable() == true) { "Source unavailable" }
        val currentFileVersion = requireNotNull(file).contentVersion()
        if (expectedFileVersion != null) check(expectedFileVersion == currentFileVersion) { "Source file changed. Reopen it before saving." }
        val text = CanvasFileMarkupCodec.merge(node.text, pages)
        if (selection != null) {
            check(selection.workspaceObjectId == node.refObjectId) { "Selection belongs to a different source." }
            val pageNumber = selection.pageId?.removePrefix("page-")?.toIntOrNull()
                ?.takeIf { it in 1..9999 } ?: error("Selection page is invalid.")
            val baseRevision = expectedFileVersion ?: currentFileVersion ?: error("Source version is unavailable.")
            require(InkSelectionBookmarkCodec.resolve(selection, canvasId, CanvasNodeType.valueOf(node.type), node.id,
                node.refObjectId, text, baseRevision) != null) {
                "Source changed. Select the region again."
            }
        }
        val changed = canvasDao.updateNodeText(canvasId, nodeId, text, now()) == 1
        if (selection != null) {
            check(changed) { "Could not save the annotated source." }
            insertInkSelectionBookmark(canvasId, node, selection, canvasDao.getNodes(canvasId))
        }
        if (changed) touch(canvasId)
        changed
    }

    /** A group drag is one transaction, including every explicitly selected member. */
    suspend fun saveEditorPending(canvasId: String, texts: Map<String, String>, positions: Map<String, Pair<Float, Float>>,
        viewport: Triple<Float, Float, Float>?) = database.withTransaction {
        requireWritable(canvasId)
        // A removed card is never recreated by a late lifecycle flush.
        val ids = canvasDao.getNodes(canvasId).map { it.id }.toSet()
        texts.filterKeys { it in ids }.forEach { (id, text) -> updateText(canvasId, id, text) }
        moveNodes(canvasId, positions.filterKeys { it in ids })
        viewport?.let { saveViewport(canvasId, it.first, it.second, it.third) }
    }

    suspend fun moveNodes(canvasId: String, positions: Map<String, Pair<Float, Float>>): Boolean = database.withTransaction {
        requireWritable(canvasId)
        val available = canvasDao.getNodes(canvasId).map { it.id }.toSet()
        require(positions.keys.all { it in available })
        val stamp = now()
        positions.forEach { (id, point) ->
            require(point.first.isFinite() && point.second.isFinite())
            check(canvasDao.updateNodePosition(canvasId, id, point.first, point.second, stamp) == 1)
        }
        if (positions.isNotEmpty()) touch(canvasId)
        true
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

    /** Reviewed text is a normal Inbox note, with durable provenance and a reference card. */
    suspend fun saveReviewedText(canvasId: String, sourceNodeId: String, expectedText: String,
        text: String, kind: String, operationId: String, expectedFileVersion: String? = null): String = database.withTransaction {
        requireWritable(canvasId)
        require(text.isNotBlank() && text.length <= 12_000)
        val prior = canvasDao.getNodes(canvasId).firstOrNull { it.id == operationId }
        if (prior != null) return@withTransaction prior.id
        val source = requireNotNull(canvasDao.getNodes(canvasId).firstOrNull { it.id == sourceNodeId }) { "Source removed" }
        check(source.text == expectedText) { "Source changed. Review it again." }
        val sourceId = source.refObjectId ?: source.id
        if (source.refObjectId != null) {
            check(notebookDao.getWorkspaceObject(source.refObjectId)?.lifecycle == "ACTIVE") { "Source unavailable" }
            val file = database.workspaceFileDao().getFile(source.refObjectId)
            check(expectedFileVersion == null || expectedFileVersion == (file?.contentVersion())) { "Source file changed" }
        }
        val noteId = "$operationId:note"
        val timestamp = now()
        notebookDao.insertNoteObject(WorkspaceObjectEntity(noteId, "NOTE", "$kind · ${if (source.type == "SKETCH") "Sketch" else CanvasFileMarkupCodec.displayTitle(source.text).take(80)}",
            timestamp, timestamp, WorkspaceObjectSource.USER_CREATED.name, "ACTIVE", true, "READY", "note:$noteId"),
            NoteEntity(noteId, text, 1))
        database.workspaceContextDao().provenance(com.halo.app.data.ObjectProvenance(noteId, sourceId,
            expectedFileVersion ?: source.updatedAtEpochMs.toString(), org.json.JSONObject()
                .put("kind", kind).put("sourceNodeId", sourceNodeId).put("canvasId", canvasId)
                .put("sourceDigest", java.security.MessageDigest.getInstance("SHA-256").digest(source.text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
                .put("reviewedAt", timestamp).put("sourceCreatedAt", source.createdAtEpochMs).toString()))
        canvasDao.insertNode(CanvasNodeEntity(operationId, canvasId, CanvasNodeType.NOTE.name, noteId, kind,
            source.x + source.width + 28f, source.y, 240f, 150f, source.zIndex + 1, timestamp, timestamp))
        connect(canvasId, source.id, operationId, kind)
        touch(canvasId)
        operationId
    }
}
