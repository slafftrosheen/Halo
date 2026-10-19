package com.halo.app.ai

import android.content.Context
import androidx.room.InvalidationTracker
import com.halo.app.data.AppDatabase
import com.halo.providers.litert.LiteRtEmbeddingProvider
import com.halo.providers.litert.LiteRtArtifactInspector
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
                val old=store.read() ?: return@withLock
                val keep=old.chunks.filter { permitted(it.workspace,it.sourceId,it.sourceVersion) }
                store.replace(old.copy(chunks=keep))
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
            require(!enabled || (localSearchAccess().enabled && selected().isNotEmpty())) {
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
                if (old != null && (old.sha256 != catalog.sha256 || old.dimension != dimension))
                    store.replace(null)
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
            } else {
                store.read()?.let { old ->
                    store.replace(old.copy(chunks=old.chunks.filter { chunk ->
                        chunk.workspace == "personal" && chunk.sourceId in sourceIds
                    }))
                }
            }
        }
        Unit
    }

    suspend fun buildLocalSearchIndex(): Int {
        val target = requireNotNull(selectedSearchModel()) { "SELECT_LOCAL_SEARCH_MODEL" }
        check(localSearchAccess().enabled && selected().isNotEmpty()) { "SELECT_LOCAL_SEARCH_SOURCES" }
        synchronize(target.modelId,target.sha256,target.dimension)
        return size()
    }

    suspend fun searchLocalSources(query: String): List<SemanticReference> {
        require(query.isNotBlank() && query.length<=4096)
        val target = requireNotNull(selectedSearchModel()) { "SELECT_LOCAL_SEARCH_MODEL" }
        check(localSearchAccess().enabled && selected().isNotEmpty()) { "SELECT_LOCAL_SEARCH_SOURCES" }
        return referencesForContext(target.modelId,target.sha256,target.dimension,query.trim())
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
    private suspend fun permitted(workspace: String,id: String,version: String): Boolean = try {
        storage.getBoolean("semantic_enabled",false) && workspace=="personal" && id in selected() && source(id)?.version==version
    } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { false }
    private fun index(modelId: String,sha: String,dimension: Int)=LocalSemanticIndex(
        embeddingExecutor(modelId),store,
        EmbeddingSpace(sha,dimension,true,"litertlm/0.18.0",
            if(models.catalogModel(modelId)?.let { it.sha256 == sha && it.family == "EmbeddingGemma2" &&
                    it.task == com.halo.providers.litert.models.LiteRtArtifactTask.EMBEDDING } == true)
                "embeddinggemma2-search-asymmetric-v1" else "text-special-tokens-v1"),
        { resources.snapshot.value.any { it.executing } },::permitted)
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
