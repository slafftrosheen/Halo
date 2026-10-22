package com.halo.app.ai

import android.content.Context
import androidx.room.InvalidationTracker
import com.halo.app.data.AppDatabase
import com.halo.app.data.study.StudyJson
import com.halo.providers.litert.LiteRtEmbeddingProvider
import com.halo.providers.litert.LiteRtArtifactInspector
import com.halo.providers.litert.LocalEmbeddingInput
import com.halo.providers.litert.LocalMediaEmbeddingRequest
import com.halo.providers.litert.LocalMediaEmbeddingSafety
import com.halo.sdk.runtime.multimodal.Pcm16WavEncoder
import com.halo.providers.litert.models.LiteRtArtifactTask
import com.halo.providers.litert.models.LiteRtModelManager
import com.halo.runtime.cognition.workload.*
import com.halo.sdk.provider.EmbeddingSpace
import com.halo.sdk.provider.EmbeddingProvider
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File

/** Explicitly selected local source scope. Existing Notebook/Canvas/Study records remain authoritative. */
class WorkspaceSemanticService(private val context: Context,private val db: AppDatabase,private val storage: Storage,
    private val models: LiteRtModelManager,private val resources: NativeResourceCoordinator,scope: CoroutineScope,
    private val embeddingFactory: (String) -> EmbeddingProvider = { LiteRtEmbeddingProvider(models, resources, it) }) {
    private val store=FileSemanticIndexStore(File(context.noBackupFilesDir,"semantic/index-v1.json"))
    private val imageStore=FileImageSemanticIndexStore(File(context.noBackupFilesDir,"semantic/image-index-v1.json"))
    private val audioStore=FileAudioSemanticIndexStore(File(context.noBackupFilesDir,"semantic/audio-index-v1.json"))
    private val videoStore=FileVideoSemanticIndexStore(File(context.noBackupFilesDir,"semantic/video-index-v1.json"))
    private val changed=Channel<Unit>(Channel.CONFLATED)
    private val mutation=Mutex()
    private val embeddingProviders = java.util.concurrent.ConcurrentHashMap<String, EmbeddingProvider>()
    /** One native owner per model, shared by queries, indexing and explicit diagnostics. */
    internal fun embeddingExecutor(modelId: String): EmbeddingProvider =
        embeddingProviders.computeIfAbsent(modelId) { embeddingFactory(it) }
    private val observer=object: InvalidationTracker.Observer("workspace_objects","notes","canvas_nodes","study_sessions","study_practice_items","workspace_assets","note_attachments","workspace_files") {
        override fun onInvalidated(tables: Set<String>) { changed.trySend(Unit) }
    }
    init {
        db.invalidationTracker.addObserver(observer)
        scope.launch {
            try { for(ignored in changed) { mutation.withLock {
                store.read()?.let { old ->
                    val keep=old.chunks.filter { permitted(it.workspace,it.sourceId,it.sourceVersion) }
                    store.replace(old.copy(chunks=keep))
                }
                imageStore.read()?.let { imageSnapshot ->
                    val imageKeep = imageSnapshot.entries.filter { imageSearchSourceExists(it.sourceId) &&
                        storage.getBoolean("semantic_enabled",false) && it.sourceId in selected() }
                    if (imageKeep.size != imageSnapshot.entries.size)
                        imageStore.replace(imageSnapshot.copy(entries=imageKeep))
                }
                audioStore.read()?.let { audioSnapshot ->
                    val audioKeep = audioSnapshot.entries.filter { audioSearchSourceExists(it.sourceId) &&
                        storage.getBoolean("semantic_enabled",false) && it.sourceId in selected() }
                    if (audioKeep.size != audioSnapshot.entries.size)
                        audioStore.replace(audioSnapshot.copy(entries=audioKeep))
                }
                videoStore.read()?.let { videoSnapshot ->
                    val videoKeep = videoSnapshot.entries.filter { videoSourceMetadata(it.sourceId) != null &&
                        storage.getBoolean("semantic_enabled",false) && it.sourceId in selected() }
                    if (videoKeep.size != videoSnapshot.entries.size)
                        videoStore.replace(videoSnapshot.copy(entries=videoKeep))
                }
            } } } finally { db.invalidationTracker.removeObserver(observer); changed.close() }
        }
    }
    private fun selected(): Set<String> = try {
        storage.getString("semantic_source_ids")?.let { encoded ->
            val json=JSONArray(encoded)
            (0 until minOf(json.length(),64)).map { json.getString(it) }.toSet()
        }.orEmpty()
    } catch (_: Exception) { emptySet() }

    /** Only explicit source consent can enable the private semantic index. */
    data class LocalSearchAccess(val enabled: Boolean, val sourceIds: Set<String>,
        val assistantContextEnabled: Boolean)
    fun localSearchAccess(): LocalSearchAccess =
        LocalSearchAccess(storage.getBoolean("semantic_enabled",false),selected(),
            storage.getBoolean("semantic_assistant_context_enabled",false))

    /** Search consent does not itself permit proactive inclusion in local conversations.
     * The turn preparer separately checks ON_DEVICE before invoking this bridge.
     */
    suspend fun configureAssistantContext(enabled: Boolean) = withContext(Dispatchers.IO) {
        mutation.withLock {
            val hasTextSources = selected().any { it.startsWith("note:") || it.startsWith("canvas:") || it.startsWith("study:") }
            require(!enabled || (localSearchAccess().enabled && hasTextSources)) {
                "SELECT_LOCAL_SEARCH_SOURCES"
            }
            storage.putBoolean("semantic_assistant_context_enabled",enabled)
        }
    }

    fun selectedSearchModel(): WorkloadPreferences.EmbeddingChoice? =
        WorkloadPreferences(storage).selectedEmbedding()

    /** The user action "Use for local search" inspects exact native metadata.
     * No benchmark or background download is involved; no unknown imported
     * artifact inherits the EmbeddingGemma catalog's advertised modalities.
     */
    suspend fun prepareSearchModel(modelId: String): WorkloadPreferences.EmbeddingChoice =
        withContext(Dispatchers.IO) {
            val catalog = requireNotNull(models.catalogModel(modelId)) { "SEARCH_MODEL_NOT_IN_CATALOG" }
            require(catalog.task == LiteRtArtifactTask.EMBEDDING) { "NOT_AN_EMBEDDING_MODEL" }
            val physical = models.verifyPhysicalArtifact(modelId)
                ?.takeIf { it.valid && it.verified && it.sha256 == catalog.sha256 }
                ?: error("SEARCH_MODEL_NOT_VERIFIED")
            val metadata = LiteRtArtifactInspector(resources).inspect(physical)
            require(metadata.task == "EMBEDDING" && "CPU" in metadata.textBackends) {
                "SEARCH_MODEL_NOT_SUPPORTED_ON_DEVICE"
            }
            val dimension = metadata.embeddingDimension?.takeIf { it in 1..16384 }
                ?: error("SEARCH_MODEL_DIMENSION_UNAVAILABLE")
            mutation.withLock {
                val old = selectedSearchModel()
                if (old != null && (old.sha256 != catalog.sha256 || old.dimension != dimension)) {
                    store.replace(null)
                    imageStore.replace(null)
                    audioStore.replace(null)
                    videoStore.replace(null)
                }
                WorkloadPreferences(storage).selectEmbedding(modelId,catalog.sha256,dimension)
            }
            requireNotNull(selectedSearchModel())
        }

    suspend fun configure(enabled: Boolean,sourceIds: Set<String>)=withContext(Dispatchers.IO) {
        require(sourceIds.size<=64 && sourceIds.all {
            it.length in 1..200 &&
                (it.startsWith("note:") || it.startsWith("canvas:") || it.startsWith("canvas-image:") ||
                    it.startsWith("study:") || it.startsWith("attachment:") || it.startsWith("workspace-audio:"))
        })
        mutation.withLock {
            // Update permission and cached bytes together, rather than waiting
            // for a Room invalidation that might never follow a user toggle.
            val consentScopeChanged = selected() != sourceIds
            storage.putBatch(strings=mapOf("semantic_source_ids" to JSONArray(sourceIds.sorted()).toString()),
                booleans=mapOf("semantic_enabled" to enabled))
            // Consent to use a specific set of excerpts cannot silently transfer
            // to newly allowed sources after a selection change.
            if (!enabled || consentScopeChanged)
                storage.putBoolean("semantic_assistant_context_enabled",false)
            if(!enabled) {
                store.replace(null)
                imageStore.replace(null)
                audioStore.replace(null)
                videoStore.replace(null)
            } else {
                store.read()?.let { old ->
                    store.replace(old.copy(chunks=old.chunks.filter { chunk ->
                        chunk.workspace == "personal" && chunk.sourceId in sourceIds
                    }))
                }
                imageStore.read()?.let { old ->
                    imageStore.replace(old.copy(entries=old.entries.filter { it.sourceId in sourceIds }))
                }
                audioStore.read()?.let { old ->
                    audioStore.replace(old.copy(entries=old.entries.filter { it.sourceId in sourceIds }))
                }
                videoStore.read()?.let { old ->
                    videoStore.replace(old.copy(entries=old.entries.filter { it.sourceId in sourceIds }))
                }
            }
        }
        Unit
    }

    suspend fun buildLocalSearchIndex(): Int {
        val target = requireNotNull(selectedSearchModel()) { "SELECT_LOCAL_SEARCH_MODEL" }
        check(localSearchAccess().enabled && selected().isNotEmpty()) { "SELECT_LOCAL_SEARCH_SOURCES" }
        synchronize(target.modelId,target.sha256,target.dimension)
        synchronizeImageIndex(target.modelId,target.sha256,target.dimension)
        synchronizeAudioIndex(target.modelId,target.sha256,target.dimension)
        synchronizeVideoIndex(target.modelId,target.sha256,target.dimension)
        return size() + imageIndexSize(target.modelId,target.sha256,target.dimension) +
            audioIndexSize(target.modelId,target.sha256,target.dimension) +
            videoIndexSize(target.modelId,target.sha256,target.dimension)
    }

    suspend fun searchLocalSources(query: String): List<SemanticReference> {
        require(query.isNotBlank() && query.length<=4096)
        val target = requireNotNull(selectedSearchModel()) { "SELECT_LOCAL_SEARCH_MODEL" }
        check(localSearchAccess().enabled && selected().isNotEmpty()) { "SELECT_LOCAL_SEARCH_SOURCES" }
        val text = referencesForContext(target.modelId,target.sha256,target.dimension,query.trim())
        val hasSelectedImages = selected().any { imageSearchSourceExists(it) }
        val hasSelectedAudio = selected().any { audioSearchSourceExists(it) }
        val hasSelectedVideos = selected().any { it.startsWith("attachment:") && videoSourceMetadata(it) != null }
        if (!hasSelectedImages && !hasSelectedAudio && !hasSelectedVideos) return text
        if (hasSelectedImages) synchronizeImageIndex(target.modelId,target.sha256,target.dimension)
        if (hasSelectedAudio) synchronizeAudioIndex(target.modelId,target.sha256,target.dimension)
        if (hasSelectedVideos) synchronizeVideoIndex(target.modelId,target.sha256,target.dimension)
        val images = if (hasSelectedImages) searchLocalImages(target.modelId,target.sha256,target.dimension,query.trim()) else emptyList()
        val audio = if (hasSelectedAudio) searchLocalAudio(target.modelId,target.sha256,target.dimension,query.trim()) else emptyList()
        val videos = if (hasSelectedVideos) searchLocalVideoFrames(target.modelId,target.sha256,target.dimension,query.trim()) else emptyList()
        return (text + images + audio + videos)
            .sortedByDescending { it.score }.take(8)
    }
    private suspend fun source(id: String): SemanticSource? {
        val parts=id.split(':',limit=2); if(parts.size!=2) return null
        val key=parts[1]
        return when(parts[0]) {
            "note" -> db.notebookDao().getNote(key)?.takeIf { it.lifecycle=="ACTIVE" }?.let {
                SemanticSource(id,it.revision.toString(),"personal",it.body.take(65536),"Notebook:$key",it.updatedAt)
            }
            "canvas" -> db.notebookDao().getWorkspaceObject(key)?.takeIf { it.lifecycle=="ACTIVE" && it.type=="CANVAS" }?.let { board ->
                val nodes=db.notebookCanvasDao().getNodes(key)
                val text=nodes.filter { it.refObjectId==null }.joinToString("\n") { it.id+":"+it.text }.take(65536)
                val version=java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                SemanticSource(id,version,"personal",text,"Canvas:$key",board.updatedAt)
            }
            "workspace-text" -> {
                val owner = db.notebookDao().getWorkspaceObject(key)?.takeIf {
                    it.lifecycle == "ACTIVE" && it.type in setOf("FILE", "MEDIA", "ARTIFACT")
                } ?: return null
                val file = db.workspaceFileDao().getFile(key)?.takeIf {
                    (it.mimeType.startsWith("text/") || it.mimeType == "application/json") &&
                        it.availability in setOf("AVAILABLE", "OWNED_LOCAL")
                } ?: return null
                val bytes = try {
                    withContext(Dispatchers.IO) {
                        val input = if (file.ownedCopyPath != null && file.ownership == "HALO_OWNED") {
                            val canonical = runCatching { File(file.ownedCopyPath).canonicalFile }.getOrNull()
                                ?: return@withContext null
                            val root = com.halo.app.storage.haloStorageLayout(context).managedWorkspaceFiles.canonicalFile
                            if (!canonical.path.startsWith(root.path + File.separator) || !canonical.isFile)
                                return@withContext null
                            canonical.inputStream()
                        } else {
                            val uri = android.net.Uri.parse(file.uri)
                            if (file.ownership != "EXTERNAL_REFERENCE" || uri.scheme != "content" ||
                                !file.permissionPersisted || file.availability != "AVAILABLE") return@withContext null
                            context.contentResolver.openInputStream(uri) ?: return@withContext null
                        }
                        input.use { it.readBytesBounded(2 * 1024 * 1024) }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { return null }
                if (bytes == null) return null
                val text = try {
                    Charsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\ufeff")
                        .take(65536)
                } catch (_: Exception) { return null }
                val version = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                SemanticSource(id, version, "personal", text, owner.title, owner.updatedAt)
            }
            "study" -> db.studyDao().getSession(key)?.takeIf { it.lifecycle=="ACTIVE" }?.let { session ->
                val cards=db.studyDao().getPracticeItems(key).filter { card -> card.accepted }
                // Study source IDs are explicit user selections. Resolve each through the same
                // active Notebook/Canvas/text-file source path used by local search, so edits and
                // removals invalidate this Study snapshot instead of leaving stale passages.
                val selectedSources = StudyJson.decodeIds(session.sourceObjectIdsJson)
                    .distinct().take(4).mapNotNull { objectId ->
                        source("note:$objectId") ?: source("canvas:$objectId") ?: source("workspace-text:$objectId")
                    }
                val text = buildString {
                    append("Study goal:\n").append(session.goal.orEmpty().take(1_000))
                    append("\nAccepted practice:\n")
                    cards.forEach { card ->
                        if (length >= 4_000) return@forEach
                        val remaining = (4_000 - length).coerceAtLeast(0)
                        append((card.question + "\n" + card.answer + "\n").take(remaining))
                    }
                    selectedSources.forEach { selectedSource ->
                        if (length >= 65_536) return@forEach
                        append("\nSelected source: ").append(selectedSource.title)
                            .append(" [").append(selectedSource.version).append("]\n")
                        val remaining = (65_536 - length).coerceAtLeast(0)
                        append(selectedSource.text.take(remaining))
                    }
                }.take(65_536)
                val version=java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
                SemanticSource(id,version,"personal",text,"Study:$key",session.updatedAtEpochMs)
            }
            "attachment" -> {
                val ids=key.split(':',limit=2)
                if(ids.size!=2 || db.notebookDao().getNote(ids[0])?.lifecycle!="ACTIVE") null
                else db.workspaceAssetDao().observeAttachments(ids[0]).first().firstOrNull { it.id==ids[1] &&
                    it.mimeType in setOf("text/plain","text/markdown") }?.let { asset ->
                    val text=withContext(Dispatchers.IO) {
                        val uri=android.net.Uri.parse(asset.pathOrUri)
                        val input=if(uri.scheme=="content") context.contentResolver.openInputStream(uri)
                            else {
                                val file=File(if(uri.scheme=="file") requireNotNull(uri.path) else asset.pathOrUri)
                                require(file.canonicalPath.startsWith(context.filesDir.canonicalPath+File.separator))
                                file.inputStream()
                            }
                        input?.use { it.reader(Charsets.UTF_8).readTextBounded() }.orEmpty()
                    }
                    val version=java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
                    SemanticSource(id,version,"personal",text,"Attachment:${asset.assetId}",0L)
                }
            }
            else -> null
        }
    }
    private data class SelectedImagePayload(
        val sourceId: String,
        val sourceVersion: String,
        val title: String,
        val provenance: String,
        val bytes: ByteArray,
    )

    private data class CanvasImageMetadata(
        val canvasId: String,
        val nodeId: String,
        val objectId: String,
        val canvasTitle: String,
        val file: com.halo.app.data.workspace.WorkspaceFileEntity,
    )

    private data class SelectedAudioSource(
        val sourceId: String,
        val decoderId: String,
        val uri: android.net.Uri,
        val sourceVersion: String,
        val title: String,
        val provenance: String,
    )

    private data class SelectedVideoSource(
        val sourceId: String,
        val attachment: com.halo.app.data.workspace.NoteAttachment,
        val uri: android.net.Uri,
        val sourceVersion: String,
        val title: String,
        val provenance: String,
    )

    /** Reads only one explicitly selected image attachment, with its owner and permission rechecked. */
    private suspend fun imageSourceMetadata(sourceId: String): com.halo.app.data.workspace.NoteAttachment? {
        val parts = sourceId.removePrefix("attachment:").split(':', limit = 2)
        if (!sourceId.startsWith("attachment:") || parts.size != 2) return null
        val noteId = parts[0]
        val attachmentId = parts[1]
        if (noteId.isBlank() || attachmentId.isBlank()) return null
        val note = db.notebookDao().getNote(noteId)?.takeIf { it.lifecycle == "ACTIVE" } ?: return null
        if (db.notebookDao().getWorkspaceObject(noteId)?.type != "NOTE") return null
        return db.workspaceAssetDao().observeAttachments(noteId).first().firstOrNull {
            it.id == attachmentId && it.type == "IMAGE" && it.mimeType.startsWith("image/")
        }
    }

    /** Canonical file-backed image node on an active Canvas; the node and object are re-resolved on every use. */
    private suspend fun canvasImageSourceMetadata(sourceId: String): CanvasImageMetadata? {
        val parts = sourceId.removePrefix("canvas-image:").split(':', limit = 2)
        if (!sourceId.startsWith("canvas-image:") || parts.size != 2 || parts.any(String::isBlank)) return null
        val canvas = db.notebookDao().getWorkspaceObject(parts[0])
            ?.takeIf { it.lifecycle == "ACTIVE" && it.type == "CANVAS" } ?: return null
        val node = db.notebookCanvasDao().getNodes(canvas.id).firstOrNull {
            it.id == parts[1] && it.type == com.halo.app.data.notebook.CanvasNodeType.IMAGE.name
        } ?: return null
        val objectId = node.refObjectId ?: return null
        val source = db.notebookDao().getWorkspaceObject(objectId)
            ?.takeIf { it.lifecycle == "ACTIVE" && it.type in setOf("FILE", "MEDIA") } ?: return null
        val file = db.workspaceFileDao().getFile(source.id) ?: return null
        if (file.mimeType.lowercase() !in setOf("image/png", "image/jpeg")) return null
        return CanvasImageMetadata(canvas.id,node.id,source.id,canvas.title,file)
    }

    private suspend fun imageSearchSourceExists(sourceId: String): Boolean =
        when {
            sourceId.startsWith("attachment:") -> imageSourceMetadata(sourceId) != null
            sourceId.startsWith("canvas-image:") -> canvasImageSourceMetadata(sourceId) != null
            else -> false
        }

    private suspend fun selectedImage(sourceId: String): SelectedImagePayload? = withContext(Dispatchers.IO) {
        if (!storage.getBoolean("semantic_enabled",false) || sourceId !in selected()) return@withContext null
        if (sourceId.startsWith("canvas-image:")) {
            val source = canvasImageSourceMetadata(sourceId) ?: return@withContext null
            val file = source.file
            if (file.sizeBytes != null && file.sizeBytes > LocalMediaEmbeddingSafety.MAX_IMAGE_BYTES)
                throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_INPUT_INVALID")
            if (file.ownedCopyPath != null && file.ownership != "HALO_OWNED")
                throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_SOURCE_UNSUPPORTED")
            val ownedFile = file.ownedCopyPath?.let { path ->
                val canonical = try { File(path).canonicalFile }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_SOURCE_UNSUPPORTED",failure) }
                val root = com.halo.app.storage.haloStorageLayout(context).managedWorkspaceFiles.canonicalFile
                if (!canonical.path.startsWith(root.path + File.separator))
                    throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_SOURCE_OUTSIDE_APP_STORAGE")
                canonical
            }
            val input = if (ownedFile != null) {
                if (!ownedFile.isFile || file.availability !in setOf("OWNED_LOCAL", "AVAILABLE"))
                    throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_SOURCE_PERMISSION_EXPIRED")
                try { ownedFile.inputStream() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_SOURCE_PERMISSION_EXPIRED",failure) }
            } else {
                val uri = android.net.Uri.parse(file.uri)
                if (file.ownership != "EXTERNAL_REFERENCE" || uri.scheme != "content" ||
                    !file.permissionPersisted || file.availability != "AVAILABLE")
                    throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_SOURCE_PERMISSION_EXPIRED")
                try { requireNotNull(context.contentResolver.openInputStream(uri)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_SOURCE_PERMISSION_EXPIRED",failure) }
            }
            val bytes = input.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > LocalMediaEmbeddingSafety.MAX_IMAGE_BYTES)
                        throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_INPUT_INVALID")
                    output.write(buffer,0,count)
                }
                output.toByteArray()
            }
            LocalMediaEmbeddingSafety.check(LocalEmbeddingInput.Image(bytes))
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val live = canvasImageSourceMetadata(sourceId)
            if (live?.objectId != source.objectId || sourceId !in selected()) return@withContext null
            val label = file.displayName.ifBlank { "Picture" }
            return@withContext SelectedImagePayload(sourceId,sha,
                "${source.canvasTitle.ifBlank { "Canvas" }} · $label".take(400),
                "Canvas image:${source.canvasId}:${source.nodeId}".take(400),bytes)
        }
        val parts = sourceId.removePrefix("attachment:").split(':', limit = 2)
        if (!sourceId.startsWith("attachment:") || parts.size != 2) return@withContext null
        val attachment = imageSourceMetadata(sourceId) ?: return@withContext null
        if (attachment.mimeType !in setOf("image/png", "image/jpeg"))
            throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_INPUT_INVALID")
        if (attachment.bytes != null && attachment.bytes > LocalMediaEmbeddingSafety.MAX_IMAGE_BYTES)
            throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_INPUT_INVALID")
        val input = when (attachment.storageType) {
            "MANAGED_FILE" -> {
                val file = try { File(attachment.pathOrUri).canonicalFile }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_SOURCE_UNSUPPORTED",failure) }
                val root = context.filesDir.canonicalFile
                if (!file.path.startsWith(root.path + File.separator))
                    throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_SOURCE_OUTSIDE_APP_STORAGE")
                try { file.inputStream() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_SOURCE_PERMISSION_EXPIRED",failure) }
            }
            "APP_OWNED", "EXTERNAL_URI" -> {
                val uri = android.net.Uri.parse(attachment.pathOrUri)
                if (uri.scheme != "content" || !attachment.permissionPersisted)
                    throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_SOURCE_PERMISSION_EXPIRED")
                try { requireNotNull(context.contentResolver.openInputStream(uri)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_SOURCE_PERMISSION_EXPIRED",failure) }
            }
            else -> throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_SOURCE_UNSUPPORTED")
        }
        val bytes = input.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > LocalMediaEmbeddingSafety.MAX_IMAGE_BYTES)
                    throw com.halo.providers.litert.EmbeddingUnavailableException("IMAGE_INPUT_INVALID")
                output.write(buffer,0,count)
            }
            output.toByteArray()
        }
        LocalMediaEmbeddingSafety.check(LocalEmbeddingInput.Image(bytes))
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val note = db.notebookDao().getNote(attachment.noteId)?.takeIf { it.lifecycle == "ACTIVE" }
            ?: return@withContext null
        val label = attachment.caption?.takeIf { it.isNotBlank() }
            ?: attachment.displayName?.takeIf { it.isNotBlank() }
            ?: "Picture"
        SelectedImagePayload(sourceId,sha,
            "${note.title.ifBlank { "Untitled" }} · $label".take(400),
            "Notebook picture:${attachment.noteId}".take(400),bytes)
    }

    /** Reads only audio attachment metadata for an exact opted-in source ID. */
    private suspend fun audioSourceMetadata(sourceId: String): com.halo.app.data.workspace.NoteAttachment? {
        val parts = sourceId.removePrefix("attachment:").split(':', limit = 2)
        if (!sourceId.startsWith("attachment:") || parts.size != 2) return null
        val noteId = parts[0]
        val attachmentId = parts[1]
        if (noteId.isBlank() || attachmentId.isBlank()) return null
        val note = db.notebookDao().getNote(noteId)?.takeIf { it.lifecycle == "ACTIVE" } ?: return null
        if (db.notebookDao().getWorkspaceObject(noteId)?.type != "NOTE") return null
        return db.workspaceAssetDao().observeAttachments(noteId).first().firstOrNull {
            it.id == attachmentId && it.mimeType.startsWith("audio/")
        }
    }

    /** Resolves one explicitly selected active Inbox/workspace audio file. */
    private suspend fun workspaceAudioSourceMetadata(sourceId: String): com.halo.app.data.workspace.WorkspaceFileEntity? {
        val objectId = sourceId.removePrefix("workspace-audio:")
        if (!sourceId.startsWith("workspace-audio:") || objectId.isBlank()) return null
        val owner = db.notebookDao().getWorkspaceObject(objectId)?.takeIf {
            it.lifecycle == "ACTIVE" && it.type in setOf("FILE", "MEDIA", "ARTIFACT")
        } ?: return null
        return db.workspaceFileDao().getFile(owner.id)?.takeIf {
            it.mimeType.startsWith("audio/") && it.availability in setOf("AVAILABLE", "OWNED_LOCAL")
        }
    }

    private suspend fun audioSearchSourceExists(sourceId: String): Boolean =
        audioSourceMetadata(sourceId) != null || workspaceAudioSourceMetadata(sourceId) != null

    /** Reads only video metadata for one exact selected attachment owned by an active note. */
    private suspend fun videoSourceMetadata(sourceId: String): com.halo.app.data.workspace.NoteAttachment? {
        val parts = sourceId.removePrefix("attachment:").split(':', limit = 2)
        if (!sourceId.startsWith("attachment:") || parts.size != 2) return null
        val noteId = parts[0]
        val attachmentId = parts[1]
        if (noteId.isBlank() || attachmentId.isBlank()) return null
        val note = db.notebookDao().getNote(noteId)?.takeIf { it.lifecycle == "ACTIVE" } ?: return null
        if (db.notebookDao().getWorkspaceObject(noteId)?.type != "NOTE") return null
        return db.workspaceAssetDao().observeAttachments(noteId).first().firstOrNull {
            it.id == attachmentId && it.mimeType.startsWith("video/")
        }
    }

    /** Hashes only the selected, permission-checked video; it never stores its bytes. */
    private suspend fun selectedVideoSource(sourceId: String): SelectedVideoSource? = withContext(Dispatchers.IO) {
        if (!storage.getBoolean("semantic_enabled",false) || sourceId !in selected()) return@withContext null
        val attachment = videoSourceMetadata(sourceId) ?: return@withContext null
        if (attachment.mimeType !in SUPPORTED_VIDEO_MIME_TYPES)
            throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_DECODE_UNSUPPORTED")
        if (attachment.bytes != null && attachment.bytes > MAX_VIDEO_SOURCE_BYTES)
            throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_SOURCE_TOO_LARGE")
        val uri = when (attachment.storageType) {
            "MANAGED_FILE" -> {
                val file = try { File(attachment.pathOrUri).canonicalFile }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_SOURCE_UNSUPPORTED",failure) }
                val root = context.filesDir.canonicalFile
                if (!file.path.startsWith(root.path + File.separator))
                    throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_SOURCE_OUTSIDE_APP_STORAGE")
                android.net.Uri.fromFile(file)
            }
            "APP_OWNED", "EXTERNAL_URI" -> {
                val contentUri = android.net.Uri.parse(attachment.pathOrUri)
                if (contentUri.scheme != "content" || !attachment.permissionPersisted)
                    throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_SOURCE_PERMISSION_EXPIRED")
                contentUri
            }
            else -> throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_SOURCE_UNSUPPORTED")
        }
        val input = try {
            if (uri.scheme == "file") File(requireNotNull(uri.path)).inputStream()
            else requireNotNull(context.contentResolver.openInputStream(uri))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_SOURCE_PERMISSION_EXPIRED",failure)
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        var total = 0L
        input.use { stream ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_VIDEO_SOURCE_BYTES)
                    throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_SOURCE_TOO_LARGE")
                digest.update(buffer,0,count)
            }
        }
        if (total == 0L) throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_DECODE_UNSUPPORTED")
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        val note = db.notebookDao().getNote(attachment.noteId)?.takeIf { it.lifecycle == "ACTIVE" }
            ?: return@withContext null
        val label = attachment.caption?.takeIf { it.isNotBlank() }
            ?: attachment.displayName?.takeIf { it.isNotBlank() }
            ?: "Video clip"
        SelectedVideoSource(sourceId,attachment,uri,sha,
            "${note.title.ifBlank { "Untitled" }} · $label",
            "Notebook video frames:${attachment.noteId}")
    }

    /** Extracts four short-lived, bounded interior frames; no audio track or frame bytes are retained. */
    private suspend fun selectedVideoFrames(source: SelectedVideoSource): List<ByteArray> = withContext(Dispatchers.IO) {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            if (source.uri.scheme == "file") retriever.setDataSource(requireNotNull(source.uri.path))
            else retriever.setDataSource(context,source.uri)
            val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_DURATION_UNSUPPORTED")
            val width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            if (width !in 1..MAX_VIDEO_DIMENSION || height !in 1..MAX_VIDEO_DIMENSION ||
                width.toLong() * height > MAX_VIDEO_PIXELS)
                throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_DECODE_UNSUPPORTED")
            val timestamps = try { VideoFrameSampling.timestamps(duration) }
                catch (failure: IllegalArgumentException) {
                    throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_DURATION_UNSUPPORTED",failure)
                }
            timestamps.map { timestampMs ->
                currentCoroutineContext().ensureActive()
                val frame = retriever.getFrameAtTime(timestampMs * 1_000L,
                    android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_FRAME_UNAVAILABLE")
                var scaled: android.graphics.Bitmap? = null
                try {
                    if (frame.width !in 1..MAX_VIDEO_DIMENSION || frame.height !in 1..MAX_VIDEO_DIMENSION ||
                        frame.width.toLong() * frame.height > MAX_VIDEO_PIXELS)
                        throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_DECODE_UNSUPPORTED")
                    val scale = minOf(1f,MAX_VIDEO_FRAME_LONG_EDGE.toFloat() / maxOf(frame.width,frame.height))
                    val output = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(frame,
                        (frame.width * scale).toInt().coerceAtLeast(1),
                        (frame.height * scale).toInt().coerceAtLeast(1),true) else frame
                    if (output !== frame) scaled = output
                    val bytes = java.io.ByteArrayOutputStream().use { stream ->
                        check(output.compress(android.graphics.Bitmap.CompressFormat.JPEG,82,stream)) {
                            "VIDEO_FRAME_UNAVAILABLE"
                        }
                        stream.toByteArray()
                    }
                    if (bytes.size > MAX_VIDEO_FRAME_BYTES)
                        throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_FRAME_UNAVAILABLE")
                    LocalMediaEmbeddingSafety.check(LocalEmbeddingInput.Image(bytes))
                    bytes
                } finally {
                    scaled?.recycle()
                    frame.recycle()
                }
            }.also { frames ->
                val live = selectedVideoSource(source.sourceId)
                if (live?.sourceVersion != source.sourceVersion)
                    throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_SOURCE_CHANGED")
                require(frames.size == VideoFrameSampling.FRAME_COUNT)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: com.halo.providers.litert.EmbeddingUnavailableException) { throw failure }
        catch (failure: Exception) {
            throw com.halo.providers.litert.EmbeddingUnavailableException("VIDEO_DECODE_UNSUPPORTED",failure)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Hashes the current compressed source and checks its persisted read permission. */
    private suspend fun selectedAudioSource(sourceId: String): SelectedAudioSource? = withContext(Dispatchers.IO) {
        if (!storage.getBoolean("semantic_enabled",false) || sourceId !in selected()) return@withContext null
        val attachment = audioSourceMetadata(sourceId)
        val workspaceFile = if (attachment == null) workspaceAudioSourceMetadata(sourceId) else null
        if (attachment == null && workspaceFile == null) return@withContext null
        val declaredSize = attachment?.bytes ?: workspaceFile?.sizeBytes
        if (declaredSize != null && declaredSize > MAX_AUDIO_SOURCE_BYTES)
            throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_SOURCE_TOO_LARGE")
        val uri = if (attachment != null) {
            when (attachment.storageType) {
                "MANAGED_FILE" -> {
                    val file = try { File(attachment.pathOrUri).canonicalFile }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_SOURCE_UNSUPPORTED",failure) }
                    val root = context.filesDir.canonicalFile
                    if (!file.path.startsWith(root.path + File.separator))
                        throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_SOURCE_OUTSIDE_APP_STORAGE")
                    android.net.Uri.fromFile(file)
                }
                "APP_OWNED", "EXTERNAL_URI" -> {
                    val contentUri = android.net.Uri.parse(attachment.pathOrUri)
                    if (contentUri.scheme != "content" || !attachment.permissionPersisted)
                        throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_SOURCE_PERMISSION_EXPIRED")
                    contentUri
                }
                else -> throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_SOURCE_UNSUPPORTED")
            }
        } else {
            val file = requireNotNull(workspaceFile)
            if (file.ownedCopyPath != null && file.ownership == "HALO_OWNED") {
                val owned = try { File(file.ownedCopyPath).canonicalFile }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_SOURCE_UNSUPPORTED",failure) }
                val root = com.halo.app.storage.haloStorageLayout(context).managedWorkspaceFiles.canonicalFile
                if (!owned.path.startsWith(root.path + File.separator) || !owned.isFile)
                    throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_SOURCE_OUTSIDE_APP_STORAGE")
                android.net.Uri.fromFile(owned)
            } else {
                val contentUri = android.net.Uri.parse(file.uri)
                val capturedMemo = workspaceVoiceMemoFile(file)
                when {
                    capturedMemo != null -> android.net.Uri.fromFile(capturedMemo)
                    file.ownership == "EXTERNAL_REFERENCE" && contentUri.scheme == "content" &&
                        file.permissionPersisted && file.availability == "AVAILABLE" -> contentUri
                    else -> throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_SOURCE_PERMISSION_EXPIRED")
                }
            }
        }
        val input = try { requireNotNull(context.contentResolver.openInputStream(uri)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_SOURCE_PERMISSION_EXPIRED",failure) }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        var total = 0L
        input.use { stream ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_AUDIO_SOURCE_BYTES)
                    throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_SOURCE_TOO_LARGE")
                digest.update(buffer,0,count)
            }
        }
        if (total == 0L) throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_INPUT_INVALID")
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        val decoderId: String
        val title: String
        val provenance: String
        if (attachment != null) {
            val note = db.notebookDao().getNote(attachment.noteId)?.takeIf { it.lifecycle == "ACTIVE" }
                ?: return@withContext null
            val label = attachment.caption?.takeIf { it.isNotBlank() }
                ?: attachment.displayName?.takeIf { it.isNotBlank() }
                ?: "Audio memo"
            decoderId = attachment.id
            title = "${note.title.ifBlank { "Untitled" }} · $label"
            provenance = "Notebook audio:${attachment.noteId}"
        } else {
            val file = requireNotNull(workspaceFile)
            val owner = db.notebookDao().getWorkspaceObject(file.objectId)?.takeIf { it.lifecycle == "ACTIVE" }
                ?: return@withContext null
            decoderId = file.objectId
            title = owner.title.ifBlank { file.displayName.ifBlank { "Audio memo" } }
            provenance = "Inbox audio:${file.objectId}"
        }
        SelectedAudioSource(sourceId,decoderId,uri,sha,title.take(400),provenance.take(400))
    }

    /** Only the app's own Inbox recorder URI may bypass persistable external grants. */
    private fun workspaceVoiceMemoFile(file: com.halo.app.data.workspace.WorkspaceFileEntity): File? {
        val uri = android.net.Uri.parse(file.uri)
        if (file.ownership != "EXTERNAL_REFERENCE" || uri.scheme != "content" ||
            uri.authority != "${context.packageName}.captures") return null
        val parts = uri.pathSegments
        if (parts.size != 3 || parts[0] != "workspace-owned" || parts[1] != "voice-inbox" ||
            !parts[2].startsWith("memo_") || !parts[2].endsWith(".m4a")) return null
        return try {
            val root = File(context.filesDir, "workspace/owned/voice-inbox").canonicalFile
            val candidate = File(root, parts[2]).canonicalFile
            candidate.takeIf { it.path.startsWith(root.path + File.separator) && it.isFile }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }

    /** Normalize one explicitly selected recording to bounded mono 16 kHz WAV in memory. */
    private suspend fun selectedAudioPayload(source: SelectedAudioSource): ByteArray {
        val decoded = try {
            com.halo.app.capture.MemoPcmDecoder(context).decode(source.uri,"semantic:${source.decoderId}")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (failure.message?.contains("12 MB") == true)
                throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_SOURCE_TOO_LARGE",failure)
            if (failure.message?.contains("two minutes") == true)
                throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_DURATION_UNSUPPORTED",failure)
            throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_INPUT_INVALID",failure)
        }
        if (decoded.durationMs !in 1..MAX_AUDIO_DURATION_MS)
            throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_DURATION_UNSUPPORTED")
        val live = selectedAudioSource(source.sourceId)
        if (live?.sourceVersion != source.sourceVersion)
            throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_SOURCE_CHANGED")
        val frames = decoded.audio.frames
        require(frames.isNotEmpty() && frames.all { it.sampleRate == 16_000 && it.channels == 1 }) {
            "AUDIO_INPUT_INVALID"
        }
        val sampleCount = frames.sumOf { it.samples.size }
        require(sampleCount in 1..(16_000 * 120)) { "AUDIO_DURATION_UNSUPPORTED" }
        val pcm = java.nio.ByteBuffer.allocate(sampleCount * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        frames.forEach { frame -> frame.samples.forEach { sample -> pcm.putShort(sample) } }
        val wav = Pcm16WavEncoder.encode(pcm.array(),16_000,1)
        if (wav.size > LocalMediaEmbeddingSafety.MAX_AUDIO_BYTES)
            throw com.halo.providers.litert.EmbeddingUnavailableException("AUDIO_INPUT_INVALID")
        LocalMediaEmbeddingSafety.check(LocalEmbeddingInput.Audio(wav))
        return wav
    }

    private suspend fun permitted(workspace: String,id: String,version: String): Boolean = try {
        storage.getBoolean("semantic_enabled",false) && workspace=="personal" && id in selected() && source(id)?.version==version
    } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { false }
    private fun spaceFor(modelId: String,sha: String,dimension: Int) =
        EmbeddingSpace(sha,dimension,true,"litertlm/0.18.0",
            if(models.catalogModel(modelId)?.let { it.sha256 == sha && it.family == "EmbeddingGemma2" &&
                    it.task == LiteRtArtifactTask.EMBEDDING } == true)
                "embeddinggemma2-search-asymmetric-v1" else "text-special-tokens-v1")

    private fun imageSearchSpace(modelId: String,sha: String,dimension: Int): EmbeddingSpace {
        val model = requireNotNull(models.catalogModel(modelId)) { "SEARCH_IMAGE_MODEL_UNSUPPORTED" }
        require(model.id == com.halo.providers.litert.models.LiteRtModelCatalog.EMBEDDINGGEMMA2_740M.id &&
            model.sha256 == sha && model.revision == com.halo.providers.litert.models.LiteRtModelCatalog.EMBEDDINGGEMMA2_740M.revision &&
            model.task == LiteRtArtifactTask.EMBEDDING) { "SEARCH_IMAGE_MODEL_UNSUPPORTED" }
        return EmbeddingSpace(sha,dimension,true,"litertlm/0.18.0",
            LocalMediaEmbeddingSafety.CROSS_MODAL_IMAGE_SEARCH_V1)
    }

    private fun audioSearchSpace(modelId: String,sha: String,dimension: Int): EmbeddingSpace {
        val model = requireNotNull(models.catalogModel(modelId)) { "SEARCH_AUDIO_MODEL_UNSUPPORTED" }
        require(model.id == com.halo.providers.litert.models.LiteRtModelCatalog.EMBEDDINGGEMMA2_740M.id &&
            model.sha256 == sha && model.revision == com.halo.providers.litert.models.LiteRtModelCatalog.EMBEDDINGGEMMA2_740M.revision &&
            model.task == LiteRtArtifactTask.EMBEDDING) { "SEARCH_AUDIO_MODEL_UNSUPPORTED" }
        return EmbeddingSpace(sha,dimension,true,"litertlm/0.18.0",
            LocalMediaEmbeddingSafety.CROSS_MODAL_AUDIO_SEARCH_V1)
    }

    private fun videoSearchSpace(modelId: String,sha: String,dimension: Int): EmbeddingSpace {
        val model = requireNotNull(models.catalogModel(modelId)) { "SEARCH_VIDEO_MODEL_UNSUPPORTED" }
        require(model.id == com.halo.providers.litert.models.LiteRtModelCatalog.EMBEDDINGGEMMA2_740M.id &&
            model.sha256 == sha && model.revision == com.halo.providers.litert.models.LiteRtModelCatalog.EMBEDDINGGEMMA2_740M.revision &&
            model.task == LiteRtArtifactTask.EMBEDDING) { "SEARCH_VIDEO_MODEL_UNSUPPORTED" }
        return EmbeddingSpace(sha,dimension,true,"litertlm/0.18.0",
            LocalMediaEmbeddingSafety.CROSS_MODAL_VIDEO_FRAME_SEARCH_V2)
    }

    /** Rebuild only from exact allowlisted Notebook attachments and Canvas image nodes; media bytes are never stored. */
    private suspend fun synchronizeImageIndex(modelId: String,sha: String,dimension: Int) = mutation.withLock {
        check(storage.getBoolean("semantic_enabled",false)) { "SEMANTIC_INDEX_OPT_IN_REQUIRED" }
        val imageIds = selected().filter { imageSearchSourceExists(it) }
        if (imageIds.isEmpty()) {
            imageStore.replace(null)
            return@withLock
        }
        val currentSpace = imageSearchSpace(modelId,sha,dimension)
        val old = imageStore.read()?.takeIf { it.space == currentSpace }
        check(resources.snapshot.value.none { it.executing }) { "NATIVE_RESOURCE_BUSY" }
        val provider = embeddingExecutor(modelId) as? LiteRtEmbeddingProvider
            ?: error("LITERT_MEDIA_ADAPTER_REQUIRED")
        val previous = old?.entries.orEmpty().associateBy { it.sourceId }
        val prepared = linkedMapOf<String,ImageSemanticEntry>()
        val pending = mutableListOf<SelectedImagePayload>()

        suspend fun embedPending() {
            if (pending.isEmpty()) return
            currentCoroutineContext().ensureActive()
            check(resources.snapshot.value.none { it.executing }) { "NATIVE_RESOURCE_BUSY" }
            val batch = pending.toList()
            val embedded = provider.embedMedia(LocalMediaEmbeddingRequest(sha,dimension,
                batch.map { LocalEmbeddingInput.Image(it.bytes) },
                LocalMediaEmbeddingSafety.CROSS_MODAL_IMAGE_SEARCH_V1))
            embedded.requireCompatible(currentSpace)
            require(embedded.vectors.size == batch.size)
            batch.zip(embedded.vectors).forEach { (source,vector) ->
                require(vector.size == dimension && vector.all { it.isFinite() })
                // Recheck the canonical attachment and bytes after native work; only the same
                // still-selected source revision can enter the durable vector index.
                val live = selectedImage(source.sourceId)
                if (live?.sourceVersion == source.sourceVersion && source.sourceId in selected() &&
                    storage.getBoolean("semantic_enabled",false)) {
                    prepared[source.sourceId] = ImageSemanticEntry(source.sourceId,source.sourceVersion,
                        source.sourceVersion,source.title,source.provenance,vector)
                }
            }
            pending.clear()
        }

        for (sourceId in imageIds) {
            currentCoroutineContext().ensureActive()
            val source = selectedImage(sourceId) ?: continue
            val cached = previous[sourceId]
            if (cached != null && cached.sourceVersion == source.sourceVersion &&
                cached.contentSha256 == source.sourceVersion) {
                prepared[sourceId] = cached.copy(title=source.title,provenance=source.provenance)
            } else {
                pending += source
                if (pending.size == 8) embedPending()
            }
        }
        embedPending()
        currentCoroutineContext().ensureActive()
        val stillSelected = selected().toSet()
        val finalEntries = prepared.values.filter { it.sourceId in stillSelected &&
            imageSearchSourceExists(it.sourceId) && storage.getBoolean("semantic_enabled",false) }
        imageStore.replace(ImageSemanticSnapshot(currentSpace,finalEntries))
    }

    private suspend fun imageIndexSize(modelId: String,sha: String,dimension: Int): Int {
        val space = runCatching { imageSearchSpace(modelId,sha,dimension) }.getOrNull() ?: return 0
        val allowed = selected()
        return imageStore.read()?.takeIf { it.space == space }?.entries?.count {
            it.sourceId in allowed && imageSearchSourceExists(it.sourceId)
        } ?: 0
    }

    /** Builds only from exact selected audio attachments; WAV bytes are transient and never indexed. */
    private suspend fun synchronizeAudioIndex(modelId: String,sha: String,dimension: Int) = mutation.withLock {
        check(storage.getBoolean("semantic_enabled",false)) { "SEMANTIC_INDEX_OPT_IN_REQUIRED" }
        val audioIds = selected().filter { audioSearchSourceExists(it) }
        check(audioIds.size <= MAX_INDEXED_AUDIO_ATTACHMENTS) { "AUDIO_SOURCE_COUNT_LIMIT" }
        if (audioIds.isEmpty()) {
            audioStore.replace(null)
            return@withLock
        }
        val currentSpace = audioSearchSpace(modelId,sha,dimension)
        val old = audioStore.read()?.takeIf { it.space == currentSpace }
        check(resources.snapshot.value.none { it.executing }) { "NATIVE_RESOURCE_BUSY" }
        val provider = embeddingExecutor(modelId) as? LiteRtEmbeddingProvider
            ?: error("LITERT_MEDIA_ADAPTER_REQUIRED")
        val previous = old?.entries.orEmpty().associateBy { it.sourceId }
        val prepared = linkedMapOf<String,AudioSemanticEntry>()
        for (sourceId in audioIds) {
            currentCoroutineContext().ensureActive()
            val source = selectedAudioSource(sourceId) ?: continue
            val cached = previous[sourceId]
            if (cached != null && cached.sourceVersion == source.sourceVersion &&
                cached.contentSha256 == source.sourceVersion) {
                prepared[sourceId] = cached.copy(title=source.title,provenance=source.provenance)
                continue
            }
            val wav = selectedAudioPayload(source)
            check(resources.snapshot.value.none { it.executing }) { "NATIVE_RESOURCE_BUSY" }
            val embedded = provider.embedMedia(LocalMediaEmbeddingRequest(sha,dimension,
                listOf(LocalEmbeddingInput.Audio(wav)),LocalMediaEmbeddingSafety.CROSS_MODAL_AUDIO_SEARCH_V1))
            embedded.requireCompatible(currentSpace)
            val vector = embedded.vectors.single()
            require(vector.size == dimension && vector.all { it.isFinite() })
            // Re-hash the authoritative source after native work before committing its vector.
            val live = selectedAudioSource(sourceId)
            if (live?.sourceVersion == source.sourceVersion && sourceId in selected() &&
                storage.getBoolean("semantic_enabled",false)) {
                prepared[sourceId] = AudioSemanticEntry(sourceId,source.sourceVersion,source.sourceVersion,
                    source.title,source.provenance,vector)
            }
        }
        currentCoroutineContext().ensureActive()
        val stillSelected = selected().toSet()
        val finalEntries = prepared.values.filter { it.sourceId in stillSelected &&
            audioSearchSourceExists(it.sourceId) && storage.getBoolean("semantic_enabled",false) }
        audioStore.replace(AudioSemanticSnapshot(currentSpace,finalEntries))
    }

    private suspend fun audioIndexSize(modelId: String,sha: String,dimension: Int): Int {
        val space = runCatching { audioSearchSpace(modelId,sha,dimension) }.getOrNull() ?: return 0
        val allowed = selected()
        return audioStore.read()?.takeIf { it.space == space }?.entries?.count {
            it.sourceId in allowed && audioSearchSourceExists(it.sourceId)
        } ?: 0
    }

    /** Indexes four interior frames per explicitly selected short video; originals stay untouched. */
    private suspend fun synchronizeVideoIndex(modelId: String,sha: String,dimension: Int) = mutation.withLock {
        check(storage.getBoolean("semantic_enabled",false)) { "SEMANTIC_INDEX_OPT_IN_REQUIRED" }
        val videoIds = selected().filter { it.startsWith("attachment:") && videoSourceMetadata(it) != null }
        check(videoIds.size <= MAX_INDEXED_VIDEO_ATTACHMENTS) { "VIDEO_SOURCE_COUNT_LIMIT" }
        if (videoIds.isEmpty()) {
            videoStore.replace(null)
            return@withLock
        }
        val currentSpace = videoSearchSpace(modelId,sha,dimension)
        val old = videoStore.read()?.takeIf { it.space == currentSpace }
        check(resources.snapshot.value.none { it.executing }) { "NATIVE_RESOURCE_BUSY" }
        val provider = embeddingExecutor(modelId) as? LiteRtEmbeddingProvider
            ?: error("LITERT_MEDIA_ADAPTER_REQUIRED")
        val previous = old?.entries.orEmpty().associateBy { it.sourceId }
        val prepared = linkedMapOf<String,VideoSemanticEntry>()
        for (sourceId in videoIds) {
            currentCoroutineContext().ensureActive()
            val source = selectedVideoSource(sourceId) ?: continue
            val cached = previous[sourceId]
            if (cached != null && cached.sourceVersion == source.sourceVersion &&
                cached.contentSha256 == source.sourceVersion) {
                prepared[sourceId] = cached.copy(title=source.title,provenance=source.provenance)
                continue
            }
            val frames = selectedVideoFrames(source)
            check(resources.snapshot.value.none { it.executing }) { "NATIVE_RESOURCE_BUSY" }
            val embedded = provider.embedMedia(LocalMediaEmbeddingRequest(sha,dimension,
                frames.map { LocalEmbeddingInput.Image(it) },
                LocalMediaEmbeddingSafety.CROSS_MODAL_VIDEO_FRAME_SEARCH_V2))
            embedded.requireCompatible(currentSpace)
            require(embedded.vectors.size == VideoFrameSampling.FRAME_COUNT)
            val vector = VideoFrameSampling.aggregate(embedded.vectors,dimension)
            val live = selectedVideoSource(sourceId)
            if (live?.sourceVersion == source.sourceVersion && sourceId in selected() &&
                storage.getBoolean("semantic_enabled",false)) {
                prepared[sourceId] = VideoSemanticEntry(sourceId,source.sourceVersion,
                    source.sourceVersion,source.title.take(400),source.provenance.take(400),vector)
            }
        }
        currentCoroutineContext().ensureActive()
        val stillSelected = selected().toSet()
        val finalEntries = prepared.values.filter { it.sourceId in stillSelected &&
            videoSourceMetadata(it.sourceId) != null && storage.getBoolean("semantic_enabled",false) }
        videoStore.replace(VideoSemanticSnapshot(currentSpace,finalEntries))
    }

    private suspend fun videoIndexSize(modelId: String,sha: String,dimension: Int): Int {
        val space = runCatching { videoSearchSpace(modelId,sha,dimension) }.getOrNull() ?: return 0
        val allowed = selected()
        return videoStore.read()?.takeIf { it.space == space }?.entries?.count {
            it.sourceId in allowed && videoSourceMetadata(it.sourceId) != null
        } ?: 0
    }

    /** Cross-modal text query uses the documented retrieval-query prefix; candidate images stay raw. */
    private suspend fun searchLocalImages(modelId: String,sha: String,dimension: Int,query: String): List<SemanticReference> {
        val space = imageSearchSpace(modelId,sha,dimension)
        val snapshot = imageStore.read()?.takeIf { it.space == space } ?: return emptyList()
        val allowed = selected()
        val candidates = snapshot.entries.filter { it.sourceId in allowed && imageSearchSourceExists(it.sourceId) }
        if (!storage.getBoolean("semantic_enabled",false) || candidates.isEmpty()) return emptyList()
        check(resources.snapshot.value.none { it.executing }) { "NATIVE_RESOURCE_BUSY" }
        val provider = embeddingExecutor(modelId) as? LiteRtEmbeddingProvider
            ?: error("LITERT_MEDIA_ADAPTER_REQUIRED")
        val queryVector = provider.embedMedia(LocalMediaEmbeddingRequest(sha,dimension,
            listOf(LocalEmbeddingInput.Text("task: search result | query: ${query.trim()}")),
            LocalMediaEmbeddingSafety.CROSS_MODAL_IMAGE_SEARCH_V1))
        queryVector.requireCompatible(space)
        val queryValues = queryVector.vectors.single()
        val live = candidates.filter { entry ->
            val source = selectedImage(entry.sourceId)
            source?.sourceVersion == entry.sourceVersion && entry.sourceId in selected() &&
                storage.getBoolean("semantic_enabled",false)
        }
        return live.map { entry ->
            val vector = entry.vector
            val dot = queryValues.indices.sumOf { queryValues[it].toDouble() * vector[it] }
            val qNorm = kotlin.math.sqrt(queryValues.sumOf { it.toDouble() * it })
            val dNorm = kotlin.math.sqrt(vector.sumOf { it.toDouble() * it })
            SemanticReference(entry.sourceId,entry.sourceVersion,"personal",0,entry.title,
                entry.provenance,if (qNorm > 0 && dNorm > 0) dot / (qNorm * dNorm) else 0.0)
        }.sortedByDescending { it.score }.take(8)
    }

    /** Query text uses the retrieval prefix; audio source vectors are unprefixed and mono 16 kHz. */
    private suspend fun searchLocalAudio(modelId: String,sha: String,dimension: Int,query: String): List<SemanticReference> {
        val space = audioSearchSpace(modelId,sha,dimension)
        val snapshot = audioStore.read()?.takeIf { it.space == space } ?: return emptyList()
        val allowed = selected()
        val candidates = snapshot.entries.filter { it.sourceId in allowed && audioSearchSourceExists(it.sourceId) }
        if (!storage.getBoolean("semantic_enabled",false) || candidates.isEmpty()) return emptyList()
        check(resources.snapshot.value.none { it.executing }) { "NATIVE_RESOURCE_BUSY" }
        val provider = embeddingExecutor(modelId) as? LiteRtEmbeddingProvider
            ?: error("LITERT_MEDIA_ADAPTER_REQUIRED")
        val queryVector = provider.embedMedia(LocalMediaEmbeddingRequest(sha,dimension,
            listOf(LocalEmbeddingInput.Text("task: search result | query: ${query.trim()}")),
            LocalMediaEmbeddingSafety.CROSS_MODAL_AUDIO_SEARCH_V1))
        queryVector.requireCompatible(space)
        val queryValues = queryVector.vectors.single()
        val live = candidates.filter { entry ->
            val source = selectedAudioSource(entry.sourceId)
            source?.sourceVersion == entry.sourceVersion && entry.sourceId in selected() &&
                storage.getBoolean("semantic_enabled",false)
        }
        return live.map { entry ->
            val vector = entry.vector
            val dot = queryValues.indices.sumOf { queryValues[it].toDouble() * vector[it] }
            val qNorm = kotlin.math.sqrt(queryValues.sumOf { it.toDouble() * it })
            val dNorm = kotlin.math.sqrt(vector.sumOf { it.toDouble() * it })
            SemanticReference(entry.sourceId,entry.sourceVersion,"personal",0,entry.title,
                entry.provenance,if (qNorm > 0 && dNorm > 0) dot / (qNorm * dNorm) else 0.0)
        }.sortedByDescending { it.score }.take(8)
    }

    /** Text queries compare with the unit-normalized mean of each video's sampled frame vectors. */
    private suspend fun searchLocalVideoFrames(modelId: String,sha: String,dimension: Int,query: String): List<SemanticReference> {
        val space = videoSearchSpace(modelId,sha,dimension)
        val snapshot = videoStore.read()?.takeIf { it.space == space } ?: return emptyList()
        val allowed = selected()
        val candidates = snapshot.entries.filter { it.sourceId in allowed && videoSourceMetadata(it.sourceId) != null }
        if (!storage.getBoolean("semantic_enabled",false) || candidates.isEmpty()) return emptyList()
        check(resources.snapshot.value.none { it.executing }) { "NATIVE_RESOURCE_BUSY" }
        val provider = embeddingExecutor(modelId) as? LiteRtEmbeddingProvider
            ?: error("LITERT_MEDIA_ADAPTER_REQUIRED")
        val queryVector = provider.embedMedia(LocalMediaEmbeddingRequest(sha,dimension,
            listOf(LocalEmbeddingInput.Text("task: search result | query: ${query.trim()}")),
            LocalMediaEmbeddingSafety.CROSS_MODAL_VIDEO_FRAME_SEARCH_V2))
        queryVector.requireCompatible(space)
        val queryValues = queryVector.vectors.single()
        val live = candidates.filter { entry ->
            val source = selectedVideoSource(entry.sourceId)
            source?.sourceVersion == entry.sourceVersion && entry.sourceId in selected() &&
                storage.getBoolean("semantic_enabled",false)
        }
        return live.map { entry ->
            val vector = entry.vector
            val dot = queryValues.indices.sumOf { queryValues[it].toDouble() * vector[it] }
            val qNorm = kotlin.math.sqrt(queryValues.sumOf { it.toDouble() * it })
            val dNorm = kotlin.math.sqrt(vector.sumOf { it.toDouble() * it })
            SemanticReference(entry.sourceId,entry.sourceVersion,"personal",0,entry.title,
                entry.provenance,if (qNorm > 0 && dNorm > 0) dot / (qNorm * dNorm) else 0.0)
        }.sortedByDescending { it.score }.take(8)
    }

    private fun index(modelId: String,sha: String,dimension: Int)=LocalSemanticIndex(
        embeddingExecutor(modelId),store,spaceFor(modelId,sha,dimension),
        { resources.snapshot.value.any { it.executing } },::permitted)

    /** Counts only live, currently permitted chunks for the selected embedding space.
     * A stale index from an earlier model or role must not show as ready in Notebook.
     */
    suspend fun readyIndexSize(): Int {
        val choice = selectedSearchModel() ?: return 0
        if (!localSearchAccess().enabled) return 0
        val snapshot = store.read()?.takeIf {
            it.space == spaceFor(choice.modelId,choice.sha256,choice.dimension)
        }
        val textCount = snapshot?.chunks?.count { permitted(it.workspace,it.sourceId,it.sourceVersion) } ?: 0
        return textCount + imageIndexSize(choice.modelId,choice.sha256,choice.dimension) +
            audioIndexSize(choice.modelId,choice.sha256,choice.dimension)
    }
    suspend fun synchronize(modelId: String,sha: String,dimension: Int)=mutation.withLock {
        check(storage.getBoolean("semantic_enabled",false))
        val sources=selected().mapNotNull { source(it) }
        index(modelId,sha,dimension).synchronize("personal",sources,true)
    }
    /** Excerpts/provenance are local context inputs only, never actions or exported telemetry. */
    suspend fun referencesForContext(modelId: String,sha: String,dimension: Int,query: String): List<SemanticReference> =
        index(modelId,sha,dimension).query("personal",query,storage.getBoolean("semantic_enabled",false))
    /**
     * Local-context bridge for a user-enabled semantic workspace. Merely
     * selecting an embedding model is not consent to read Notebook or Study:
     * semantic_enabled and the exact source-ID allowlist are both required.
     * The native adapter verifies model bytes and the index validates vector
     * space identity and live source revision/permissions at query time.
     */
    suspend fun selectedContextExcerpts(query: String): List<String> {
        if(!storage.getBoolean("semantic_enabled",false) ||
            !storage.getBoolean("semantic_assistant_context_enabled",false) ||
            query.isBlank() || query.length > 4096)
            return emptyList()
        val binding=WorkloadPreferences(storage).selectedEmbedding() ?: return emptyList()
        return referencesForContext(binding.modelId,binding.sha256,binding.dimension,query)
            .take(3).map { reference ->
                "Source (untrusted local data): ${reference.provenance}. " +
                    "Excerpt: ${reference.excerpt.take(480)}"
            }
    }

    suspend fun size()=store.read()?.chunks?.size ?: 0
    private fun java.io.Reader.readTextBounded(): String {
        val chars=CharArray(65536); var count=0
        while(count<chars.size) { val n=read(chars,count,chars.size-count); if(n<0) break; count+=n }
        return String(chars,0,count)
    }
    private fun java.io.InputStream.readBytesBounded(maxBytes: Int): ByteArray? {
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 8192))
        val buffer = ByteArray(8192)
        while (true) {
            val remainingIncludingOverflowProbe = maxBytes - output.size() + 1
            val count = read(buffer, 0, minOf(buffer.size, remainingIncludingOverflowProbe))
            if (count < 0) break
            output.write(buffer, 0, count)
            if (output.size() > maxBytes) return null
        }
        return output.toByteArray()
    }
    suspend fun contextSegments(modelId: String,sha: String,dimension: Int,query: String)=
        referencesForContext(modelId,sha,dimension,query).map { ref ->
            com.halo.runtime.cognition.context.ContextSegment("semantic:${ref.sourceId}:${ref.offset}",
                com.halo.runtime.cognition.context.ContextSegmentKind.RETRIEVED_DOCUMENT,40,(ref.excerpt.length+3)/4,
                com.halo.runtime.cognition.context.SegmentContent.InlineText(ref.excerpt),
                com.halo.runtime.cognition.context.ContextProvenance(ref.sourceId,
                    com.halo.runtime.cognition.context.ContextTrust.EXTERNAL_UNTRUSTED,false,ref.sourceVersion))
        }

    private companion object {
        const val MAX_AUDIO_SOURCE_BYTES = 12_000_000L
        const val MAX_AUDIO_DURATION_MS = 120_000L
        const val MAX_INDEXED_AUDIO_ATTACHMENTS = 8
        const val MAX_VIDEO_SOURCE_BYTES = 24_000_000L
        const val MAX_VIDEO_FRAME_LONG_EDGE = 1_024
        const val MAX_VIDEO_FRAME_BYTES = 1_048_576
        const val MAX_VIDEO_DIMENSION = 1_920
        const val MAX_VIDEO_PIXELS = 4_000_000L
        const val MAX_INDEXED_VIDEO_ATTACHMENTS = 4
        val SUPPORTED_VIDEO_MIME_TYPES = setOf("video/mp4","video/webm","video/3gpp","video/quicktime")
    }
}
