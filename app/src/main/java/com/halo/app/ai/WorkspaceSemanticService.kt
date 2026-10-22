package com.halo.app.ai

import android.content.Context
import androidx.room.InvalidationTracker
import com.halo.app.data.AppDatabase
import com.halo.providers.litert.LiteRtEmbeddingProvider
import com.halo.providers.litert.LiteRtArtifactInspector
import com.halo.providers.litert.LocalEmbeddingInput
import com.halo.providers.litert.LocalMediaEmbeddingRequest
import com.halo.providers.litert.LocalMediaEmbeddingSafety
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
    private val changed=Channel<Unit>(Channel.CONFLATED)
    private val mutation=Mutex()
    private val embeddingProviders = java.util.concurrent.ConcurrentHashMap<String, EmbeddingProvider>()
    /** One native owner per model, shared by queries, indexing and explicit diagnostics. */
    internal fun embeddingExecutor(modelId: String): EmbeddingProvider =
        embeddingProviders.computeIfAbsent(modelId) { embeddingFactory(it) }
    private val observer=object: InvalidationTracker.Observer("workspace_objects","notes","canvas_nodes","study_sessions","study_practice_items","workspace_assets","note_attachments") {
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
                    val imageKeep = imageSnapshot.entries.filter { imageSourceMetadata(it.sourceId) != null &&
                        storage.getBoolean("semantic_enabled",false) && it.sourceId in selected() }
                    if (imageKeep.size != imageSnapshot.entries.size)
                        imageStore.replace(imageSnapshot.copy(entries=imageKeep))
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
                }
                WorkloadPreferences(storage).selectEmbedding(modelId,catalog.sha256,dimension)
            }
            requireNotNull(selectedSearchModel())
        }

    suspend fun configure(enabled: Boolean,sourceIds: Set<String>)=withContext(Dispatchers.IO) {
        require(sourceIds.size<=64 && sourceIds.all {
            it.length in 1..200 &&
                (it.startsWith("note:") || it.startsWith("canvas:") ||
                    it.startsWith("study:") || it.startsWith("attachment:"))
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
            } else {
                store.read()?.let { old ->
                    store.replace(old.copy(chunks=old.chunks.filter { chunk ->
                        chunk.workspace == "personal" && chunk.sourceId in sourceIds
                    }))
                }
                imageStore.read()?.let { old ->
                    imageStore.replace(old.copy(entries=old.entries.filter { it.sourceId in sourceIds }))
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
        return size() + imageIndexSize(target.modelId,target.sha256,target.dimension)
    }

    suspend fun searchLocalSources(query: String): List<SemanticReference> {
        require(query.isNotBlank() && query.length<=4096)
        val target = requireNotNull(selectedSearchModel()) { "SELECT_LOCAL_SEARCH_MODEL" }
        check(localSearchAccess().enabled && selected().isNotEmpty()) { "SELECT_LOCAL_SEARCH_SOURCES" }
        val text = referencesForContext(target.modelId,target.sha256,target.dimension,query.trim())
        val hasSelectedImages = selected().any { it.startsWith("attachment:") && imageSourceMetadata(it) != null }
        if (!hasSelectedImages) return text
        synchronizeImageIndex(target.modelId,target.sha256,target.dimension)
        return (text + searchLocalImages(target.modelId,target.sha256,target.dimension,query.trim()))
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
            "study" -> db.studyDao().getSession(key)?.takeIf { it.lifecycle=="ACTIVE" }?.let {
                val cards=db.studyDao().getPracticeItems(key).filter { card -> card.accepted }
                val text=(it.goal.orEmpty()+"\n"+cards.joinToString("\n") { card -> card.question+"\n"+card.answer }).take(65536)
                val version=java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
                SemanticSource(id,version,"personal",text,"Study:$key",it.updatedAtEpochMs)
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
        val noteId: String,
        val attachmentId: String,
        val sourceVersion: String,
        val title: String,
        val provenance: String,
        val bytes: ByteArray,
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

    private suspend fun selectedImage(sourceId: String): SelectedImagePayload? = withContext(Dispatchers.IO) {
        if (!storage.getBoolean("semantic_enabled",false) || sourceId !in selected()) return@withContext null
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
        SelectedImagePayload(sourceId,attachment.noteId,attachment.id,sha,
            "${note.title.ifBlank { "Untitled" }} · $label",
            "Notebook picture:${attachment.noteId}",bytes)
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

    /** Rebuild only from the exact allowlisted image attachment IDs; media bytes are never stored in the index. */
    private suspend fun synchronizeImageIndex(modelId: String,sha: String,dimension: Int) = mutation.withLock {
        check(storage.getBoolean("semantic_enabled",false)) { "SEMANTIC_INDEX_OPT_IN_REQUIRED" }
        val imageIds = selected().filter { it.startsWith("attachment:") && imageSourceMetadata(it) != null }
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
            imageSourceMetadata(it.sourceId) != null && storage.getBoolean("semantic_enabled",false) }
        imageStore.replace(ImageSemanticSnapshot(currentSpace,finalEntries))
    }

    private suspend fun imageIndexSize(modelId: String,sha: String,dimension: Int): Int {
        val space = runCatching { imageSearchSpace(modelId,sha,dimension) }.getOrNull() ?: return 0
        val allowed = selected()
        return imageStore.read()?.takeIf { it.space == space }?.entries?.count {
            it.sourceId in allowed && imageSourceMetadata(it.sourceId) != null
        } ?: 0
    }

    /** Cross-modal text query uses the documented retrieval-query prefix; candidate images stay raw. */
    private suspend fun searchLocalImages(modelId: String,sha: String,dimension: Int,query: String): List<SemanticReference> {
        val space = imageSearchSpace(modelId,sha,dimension)
        val snapshot = imageStore.read()?.takeIf { it.space == space } ?: return emptyList()
        val allowed = selected()
        val candidates = snapshot.entries.filter { it.sourceId in allowed && imageSourceMetadata(it.sourceId) != null }
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
        return textCount + imageIndexSize(choice.modelId,choice.sha256,choice.dimension)
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
    suspend fun contextSegments(modelId: String,sha: String,dimension: Int,query: String)=
        referencesForContext(modelId,sha,dimension,query).map { ref ->
            com.halo.runtime.cognition.context.ContextSegment("semantic:${ref.sourceId}:${ref.offset}",
                com.halo.runtime.cognition.context.ContextSegmentKind.RETRIEVED_DOCUMENT,40,(ref.excerpt.length+3)/4,
                com.halo.runtime.cognition.context.SegmentContent.InlineText(ref.excerpt),
                com.halo.runtime.cognition.context.ContextProvenance(ref.sourceId,
                    com.halo.runtime.cognition.context.ContextTrust.EXTERNAL_UNTRUSTED,false,ref.sourceVersion))
        }
}
