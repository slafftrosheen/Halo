package com.halo.app.data.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.workspace.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

data class StudioSelectionSnapshot(val projectId: String, val projectRevision: Long, val layerId: String,
    val selection: com.halo.sdk.runtime.ink.InkSelectionContext, val ownerToken: String)

/** Shared local use cases. UI and assistant use identical optimistic revision and source checks. */
class StudioService(private val context: Context, val projects: StudioProjectRepository,
    private val files: WorkspaceFileRepository, private val workspace: WorkspaceRepository,
    private val renderer: StudioRenderer, private val assets: NotebookAssetRepository,
    private val canvases: com.halo.app.data.notebook.NotebookCanvasRepository,
    private val provenance: com.halo.app.data.WorkspaceContextDao, val uiRequests: StudioUiRequests) {
    private data class Binding(val projectId: String, val token: String)
    private val active = java.util.concurrent.atomic.AtomicReference<Binding?>(null)
    fun attachProject(id: String): String = java.util.UUID.randomUUID().toString().also {
        active.set(Binding(id, it)); uiRequests.attached(id)
    }
    fun acknowledgeAttachment(id: String) { if (active.get()?.projectId == id) uiRequests.attached(id) }
    fun detachProject(token: String) {
        val binding = active.get()
        if (binding?.token == token && active.compareAndSet(binding, null)) clearSelection(binding.projectId)
    }
    suspend fun currentProject(): StudioProject? {
        val binding = active.get() ?: return null
        val project = projects.get(binding.projectId)
        return project.takeIf { active.get() == binding }
    }
    private val inkOwner = java.util.concurrent.atomic.AtomicReference<Binding?>(null)
    fun attachInkSelectionOwner(projectId: String, token: String) { inkOwner.set(Binding(projectId, token)) }
    fun detachInkSelectionOwner(token: String) {
        val owner = inkOwner.get()
        if (owner?.token == token && inkOwner.compareAndSet(owner, null)) clearSelection(owner.projectId, token)
    }
    private val selection = java.util.concurrent.atomic.AtomicReference<StudioSelectionSnapshot?>(null)
    fun clearSelection(projectId: String, ownerToken: String? = null) {
        val current = selection.get()
        if (current?.projectId == projectId && (ownerToken == null || current.ownerToken == ownerToken)) selection.compareAndSet(current, null)
    }
    suspend fun publishSelection(projectId: String, layerId: String, baseRevision: Long, ownerToken: String,
        context: com.halo.sdk.runtime.ink.InkSelectionContext, expectedRevision: Long): Boolean {
        val owner = Binding(projectId, ownerToken)
        if (inkOwner.get() != owner) return false
        val project = projects.get(projectId) ?: return false
        // The digest anchor is frozen at input-session start, but publication must
        // still match the revision actually persisted by that session's writer.
        if (project.revision != expectedRevision) return false
        val layer = project.session.current.layers.find { it.id == layerId && it.kind == StudioLayerKind.INK } ?: return false
        val ink = requireNotNull(com.halo.sdk.runtime.ink.InkCodec.decode(layer.ink))
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest((baseRevision.toString() + "\n" + com.halo.sdk.runtime.ink.InkCodec.encode(ink)).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        if (context.workspaceObjectId != projectId || context.pageId != layerId || context.sourceRevision != digest ||
            !context.selectedStrokeIds.all { id -> ink.strokes.any { it.strokeId == id } }) return false
        if (inkOwner.get() != owner) return false
        selection.set(StudioSelectionSnapshot(projectId, project.revision, layerId, context, ownerToken))
        return true
    }
    suspend fun selectedInk(projectId: String): StudioSelectionSnapshot? {
        val snapshot = selection.get()?.takeIf { it.projectId == projectId } ?: return null
        if (projects.get(projectId)?.revision != snapshot.projectRevision ||
            inkOwner.get() != Binding(projectId, snapshot.ownerToken) || selection.get() != snapshot) {
            selection.compareAndSet(snapshot, null); return null
        }
        return snapshot
    }
    suspend fun requestOpen(id: String): StudioUiRequest {
        requireNotNull(projects.get(id)) { "Project unavailable" }
        return uiRequests.offer(id, StudioUiRequestKind.OPEN)
    }
    suspend fun requestAiReview(id: String, revision: Long, prompt: String): StudioUiRequest {
        current(id, revision)
        return uiRequests.offer(id, StudioUiRequestKind.AI_REVIEW, revision, prompt,
            com.halo.sdk.provider.RemoteDispatchContext.provenance.get())
    }
    val availableImages = combine(files.observeFiles(), workspace.observe(WorkspaceQuery())) { sources, objects ->
        val active = objects.mapTo(HashSet()) { it.id.value }
        sources.filter { it.objectId.value in active && it.version != null && it.mimeType.startsWith("image/") &&
            it.availability in setOf(WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL) }
            .sortedBy { it.displayName.lowercase(java.util.Locale.ROOT) }.take(100)
    }
    suspend fun importImage(projectId: String, expectedRevision: Long, objectId: String, ensureAbsent: Boolean = false, expectedSourceVersion: String? = null): StudioProject {
        val project = current(projectId, expectedRevision)
        val (file, size) = pinnedImage(objectId, expectedSourceVersion)
        val version = requireNotNull(file.sha256)
        if (ensureAbsent && project.session.current.layers.any { it.source == StudioSource(objectId, version) }) return project
        val layer = StudioLayer(name = file.displayName.take(120).ifBlank { "Image" }, kind = StudioLayerKind.IMAGE,
            source = StudioSource(objectId, version))
        val command = if (project.session.current.layers.isEmpty()) StudioCommand.Batch(listOf(
            StudioCommand.Resize(size.first, size.second), StudioCommand.Add(layer))) else StudioCommand.Add(layer)
        return projects.apply(projectId, expectedRevision, command)
    }
    private suspend fun pinnedImage(objectId: String, expectedSourceVersion: String?): Pair<WorkspaceFile, Pair<Int, Int>> {
        require(workspace.get(WorkspaceObjectId(objectId))?.lifecycle == WorkspaceObjectLifecycle.ACTIVE) { "Image source unavailable" }
        val original = requireNotNull(files.getFile(WorkspaceObjectId(objectId)))
        require(original.mimeType.startsWith("image/")) { "Choose an image" }
        // Pin an immutable bounded managed copy before a layer can refer to mutable external bytes.
        val file = files.pinLocalCopy(WorkspaceObjectId(objectId), expectedSourceVersion ?: requireNotNull(original.version), 20_000_000)
        val version = requireNotNull(file.sha256)
        val size = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth in 1..32768 && bounds.outHeight in 1..32768) { "Unsupported image dimensions" }
            val scale = minOf(1f, 2048f / maxOf(bounds.outWidth, bounds.outHeight))
            (bounds.outWidth * scale).roundToInt().coerceAtLeast(1) to (bounds.outHeight * scale).roundToInt().coerceAtLeast(1)
        }
        require(files.getFile(WorkspaceObjectId(objectId))?.version == version &&
            workspace.get(WorkspaceObjectId(objectId))?.lifecycle == WorkspaceObjectLifecycle.ACTIVE) { "Image source changed" }
        return file to size
    }
    /** Explicit source handoff creates one independent valid project; it never appends to a previously open project. */
    suspend fun createFromImage(title: String, objectId: String, expectedSourceVersion: String, handoffId: String? = null): StudioProject {
        if (handoffId != null) projects.handoff(objectId, handoffId, expectedSourceVersion, title)?.let { return it }
        val (file, size) = pinnedImage(objectId, expectedSourceVersion)
        val source = StudioSource(objectId, requireNotNull(file.sha256))
        val document = StudioDocument(size.first, size.second, layers = listOf(StudioLayer(name = file.displayName.take(120).ifBlank { "Image" },
            kind = StudioLayerKind.IMAGE, source = source)))
        return projects.create(title, document, sourceImage = source, handoffId = handoffId, observedSourceVersion = expectedSourceVersion, validateSource = {
            require(workspace.get(WorkspaceObjectId(objectId))?.lifecycle == WorkspaceObjectLifecycle.ACTIVE &&
                files.getFile(WorkspaceObjectId(objectId))?.version == source.version) { "Image source changed" }
        })
    }
    suspend fun importAttachment(projectId: String, expectedRevision: Long, noteId: String, attachmentId: String, ensureAbsent: Boolean = false): StudioProject {
        current(projectId, expectedRevision)
        require(workspace.get(WorkspaceObjectId(noteId))?.lifecycle == WorkspaceObjectLifecycle.ACTIVE) { "Image source unavailable" }
        val source = requireNotNull(assets.currentStitchAttachment(noteId, attachmentId)) { "Image attachment unavailable" }
        require(source.type == "IMAGE")
        val existingId = provenance.captureObject(noteId, source.assetId)
        val existing = existingId?.let { files.getFile(WorkspaceObjectId(it)) }
        if (existing != null && existing.ownership == ContentOwnership.HALO_OWNED && existing.sha256 != null &&
            workspace.get(existing.objectId)?.lifecycle == WorkspaceObjectLifecycle.ACTIVE &&
            provenance.provenanceFor(existing.objectId.value)?.metadata?.let { JSONObject(it).optString("operation") } == "studio-import-attachment")
            return importImage(projectId, expectedRevision, existing.objectId.value, ensureAbsent)
        val bytes = assets.readImage(source)
        val metadata = JSONObject().put("operation", "studio-import-attachment").put("attachmentId", attachmentId)
            .put("assetId", source.assetId).toString()
        val file = files.createManagedFile(source.displayName?.take(120)?.ifBlank { null } ?: "Image", source.mimeType,
            bytes, noteId, source.assetId, metadata) {
                current(projectId, expectedRevision)
                require(assets.currentStitchAttachment(noteId, attachmentId) == source) { "Image attachment changed" }
            }
        return importImage(projectId, expectedRevision, file.objectId.value, ensureAbsent)
    }
    suspend fun edit(id: String, expectedRevision: Long, operation: String, fields: Map<String, Any?>): StudioProject {
        val project = current(id, expectedRevision)
        if (operation == "delete_selected_ink") {
            require(fields.isEmpty())
            val snapshot = requireNotNull(selectedInk(id)) { "Ink selection is stale. Select it again." }
            check(snapshot.projectRevision == expectedRevision)
            val layer = requireNotNull(project.session.current.layers.find { it.id == snapshot.layerId })
            val ink = requireNotNull(com.halo.sdk.runtime.ink.InkCodec.decode(layer.ink))
            val ids = snapshot.selection.selectedStrokeIds.toSet()
            val result = projects.apply(id, expectedRevision, StudioCommand.Replace(layer.copy(ink =
                com.halo.sdk.runtime.ink.InkCodec.encode(ink.copy(strokes = ink.strokes.filterNot { it.strokeId in ids })))))
            clearSelection(id)
            return result
        }
        return when (val request = StudioEditParser.parse(project.session.current, operation, fields)) {
            is StudioEditRequest.Command -> projects.apply(id, expectedRevision, request.value)
            StudioEditRequest.Undo -> projects.undo(id, expectedRevision)
            StudioEditRequest.Redo -> projects.redo(id, expectedRevision)
            is StudioEditRequest.Restore -> projects.restoreVersion(id, expectedRevision, request.revision)
            is StudioEditRequest.Import -> importImage(id, expectedRevision, request.objectId, expectedSourceVersion = request.sourceVersion)
        }
    }
    suspend fun inkBackdrop(document: StudioDocument, layer: StudioLayer): Bitmap = renderer.inkBackdrop(document, layer)
    fun inkWidthFraction(document: StudioDocument, layer: StudioLayer): Float = renderer.inkWidthFraction(document, layer)
    suspend fun previewDocument(document: StudioDocument): Bitmap = renderer.render(document, true)
    suspend fun preview(id: String, expectedRevision: Long): Bitmap = renderer.render(current(id, expectedRevision).session.current, true)
    suspend fun aiReference(project: StudioProject): ByteArray {
        current(project.id, project.revision)
        val bitmap = renderer.render(project.session.current, true)
        return try { CanvasImageTools.encodeBitmap(bitmap, CanvasImageFormat.PNG) } finally { bitmap.recycle() }
    }
    /** Keeping a proposal creates an independent project, so local edits made meanwhile are never overwritten. */
    suspend fun keepAiCopy(source: StudioProject, result: com.halo.sdk.provider.AiImageResult, title: String): StudioProject {
        requireNotNull(projects.get(source.id)) { "Original project unavailable" }
        val dimensions = withContext(Dispatchers.IO) {
            require(result.bytes.size in 1..20_000_000)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size, bounds)
            require(bounds.outWidth in 1..16384 && bounds.outHeight in 1..16384)
            val mime = requireNotNull(bounds.outMimeType)
            require(mime in setOf("image/png", "image/jpeg", "image/webp"))
            Triple(bounds.outWidth, bounds.outHeight, mime)
        }
        require(result.providerId.length in 1..200 && result.modelId.length in 1..500)
        val metadata = JSONObject().put("operation", "studio-ai-proposal-kept").put("projectRevision", source.revision)
            .put("providerReportedId", result.providerId).put("providerReportedModel", result.modelId)
            .put("document", JSONObject(StudioDocumentCodec.encode(source.session.current))).toString()
        val extension = when (dimensions.third) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
        val file = files.createManagedFile(title.take(100) + "." + extension, dimensions.third, result.bytes,
            source.id, source.revision.toString(), metadata) { requireNotNull(projects.get(source.id)) }
        val scale = minOf(1f, 2048f / maxOf(dimensions.first, dimensions.second))
        val document = StudioDocument((dimensions.first * scale).roundToInt().coerceAtLeast(1),
            (dimensions.second * scale).roundToInt().coerceAtLeast(1), layers = listOf(StudioLayer(name = title.take(120),
                kind = StudioLayerKind.IMAGE, source = StudioSource(file.objectId.value, requireNotNull(file.sha256)))))
        return projects.create(title.take(120), document, source.id, source.revision)
    }
    suspend fun insertOutput(canvasId: String, outputId: String, expectedFileVersion: String, expectedCanvasVersion: String? = null): String {
        val file = requireNotNull(files.getFile(WorkspaceObjectId(outputId)))
        require(file.version == expectedFileVersion && file.ownership == ContentOwnership.HALO_OWNED &&
            file.mimeType in setOf("image/png", "image/jpeg") &&
            workspace.get(WorkspaceObjectId(outputId))?.lifecycle == WorkspaceObjectLifecycle.ACTIVE) { "Saved image unavailable" }
        val origin = requireNotNull(provenance.provenanceFor(outputId))
        require(JSONObject(origin.metadata).optString("operation") == "studio-export") { "Choose a saved Studio output" }
        val board = requireNotNull(canvases.observe(canvasId).first()) { "Canvas unavailable" }
        val x = board.nodes.maxOfOrNull { it.x.toDouble() + it.width }?.plus(24.0)?.coerceIn(-100_000.0, 100_000.0)?.toFloat() ?: 0f
        return canvases.addReference(canvasId, outputId, x, 0f, image = true, expectedCanvasVersion = expectedCanvasVersion)
    }
    suspend fun export(id: String, expectedRevision: Long, format: CanvasImageFormat): WorkspaceFile {
        val project = current(id, expectedRevision)
        val document = project.session.current.copy(recipe = project.session.current.recipe.copy(format = format))
        val bitmap = renderer.render(document, false)
        val bytes = try { CanvasImageTools.encodeBitmap(bitmap, format) } finally { bitmap.recycle() }
        val metadata = JSONObject().put("operation", "studio-export").put("projectId", id)
            .put("projectRevision", expectedRevision).put("document", JSONObject(StudioDocumentCodec.encode(document))).toString()
        return files.createManagedFile(project.title.take(100) + "." + format.extension, format.mimeType, bytes,
            id, expectedRevision.toString(), metadata) {
                current(id, expectedRevision)
                for (source in document.layers.mapNotNull { it.source }.distinct()) {
                    require(workspace.get(WorkspaceObjectId(source.objectId))?.lifecycle == WorkspaceObjectLifecycle.ACTIVE &&
                        files.getFile(WorkspaceObjectId(source.objectId))?.version == source.version) { "Image source changed before export" }
                }
            }
    }
    private suspend fun current(id: String, expectedRevision: Long): StudioProject = requireNotNull(projects.get(id)) {
        "Project unavailable"
    }.also { check(it.revision == expectedRevision) { "Project changed. Reopen it before continuing." } }
}
