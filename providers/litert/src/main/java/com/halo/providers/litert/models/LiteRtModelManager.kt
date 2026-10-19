package com.halo.providers.litert.models

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.halo.providers.litert.LiteRtFailureStage
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

data class LiteRtAcquisitionDiagnostics(
    val diagnosticsId: String = UUID.randomUUID().toString(),
    val modelId: String?,
    val stage: LiteRtFailureStage,
    val artifactFingerprint: String?,
    val exceptionClass: String,
    val nativeMessage: String?,
    val recordedAtMs: Long = System.currentTimeMillis(),
) {
    fun technicalSummary(): String =
        "id=${diagnosticsId.take(8)} stage=$stage model=${modelId ?: "unknown"} " +
            "fingerprint=${artifactFingerprint?.take(12) ?: "unknown"} type=$exceptionClass" +
            (nativeMessage?.let { " message=${it.take(300)}" } ?: "")
}

/**
 * Owns the on-disk LiteRT model directory: acquisition, verification, inventory and deletion.
 *
 * Invariant: a model is only reported as installed after the physical file on disk has been
 * re-resolved through the inventory and found to match its pinned fingerprint. "The download
 * finished" is never treated as proof that a model is ready.
 *
 * Partial downloads are first-class: a 1-2 GB artifact that loses the network must be resumable
 * instead of being thrown away and restarted.
 */
class LiteRtModelManager(
    private val context: Context,
    private val modelRoot: File = File(context.noBackupFilesDir, "models"),
    private val downloader: LiteRtModelDownloader = OkHttpLiteRtModelDownloader(),
    /** Pinned artifacts this manager treats as authoritative. Injectable for tests. */
    private val catalog: List<LiteRtCatalogModel> = LiteRtModelCatalog.BUILT_IN_MODELS,
    private val backgroundDownloads: Boolean = false,
) : LocalModelInventoryRepository {
    private val hubCatalogFile = File(context.noBackupFilesDir, "litert-hub-catalog.json")
    private val hubModels = runCatching { HuggingFaceLiteRtCatalog.decode(hubCatalogFile.readText()) }.getOrDefault(emptyList())
    private val mutableCatalog = MutableStateFlow((catalog + hubModels).distinctBy { it.sha256 })
    val catalogState = mutableCatalog.asStateFlow()
    val catalogSyncState = MutableStateFlow("Offline catalog")
    @Volatile private var sessionHubToken: String? = null
    private val tokenApplied = MutableStateFlow(false)
    val hubTokenApplied = tokenApplied.asStateFlow()
    fun applyHubToken(value: String): Boolean {
        val clean = value.trim()
        if (!isHuggingFaceReadToken(clean)) return false
        sessionHubToken = clean; tokenApplied.value = true
        return true
    }
    fun clearHubToken() { sessionHubToken = null; tokenApplied.value = false }
    private val importMutex = Mutex()
    private val importing = MutableStateFlow<String?>(null)
    val importStatus = importing.asStateFlow()
    private val catalogSyncMutex = Mutex()

    suspend fun syncCatalog(query: String = "") = catalogSyncMutex.withLock {
        catalogSyncState.value = "Syncing models…"
        try {
            val discovered = HuggingFaceLiteRtCatalog(authToken = { sessionHubToken }).discover(query)
            val merged = (mutableCatalog.value + discovered).distinctBy { it.sha256 }.take(1000)
            withContext(Dispatchers.IO) {
                val temp = File(hubCatalogFile.path + ".part")
                temp.writeText(HuggingFaceLiteRtCatalog.encode(merged.filter { it.id.startsWith("hf-") }))
                check(temp.renameTo(hubCatalogFile)) { "Cannot save model catalog" }
            }
            mutableCatalog.value = merged
            catalogSyncState.value = "Models updated"
            refresh(ModelInventoryRefreshReason.USER_REQUEST)
        } catch (cancelled: CancellationException) { catalogSyncState.value = "Sync cancelled"; throw cancelled }
        catch (_: Exception) { catalogSyncState.value = "Couldn't sync models. Your saved catalog is available." }
    }
    val modelDir: File get() = modelRoot

    @Volatile
    private var residentModelIdProvider: () -> String? = { null }

    fun observeResidentModel(provider: () -> String?) {
        residentModelIdProvider = provider
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inventoryMutex = Mutex()
    private val downloadLock = Any()
    private val downloadJobs = mutableMapOf<String, Job>()

    /** path|length|modified -> physical SHA-256. Avoids rehashing an unchanged multi-GB artifact. */
    private val physicalFingerprints = ConcurrentHashMap<String, String>()

    private val _inventoryState = MutableStateFlow<LocalModelInventoryState>(LocalModelInventoryState.Loading)
    override val inventoryState: StateFlow<LocalModelInventoryState> = _inventoryState.asStateFlow()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val _acquisitionDiagnostics = MutableStateFlow<LiteRtAcquisitionDiagnostics?>(null)
    val acquisitionDiagnostics: StateFlow<LiteRtAcquisitionDiagnostics?> = _acquisitionDiagnostics.asStateFlow()

    private val _legacyGgufFiles = MutableStateFlow<List<File>>(emptyList())
    val legacyGgufFiles: StateFlow<List<File>> = _legacyGgufFiles.asStateFlow()

    private val inventoryRevision = java.util.concurrent.atomic.AtomicLong()

    init {
        scope.launch { refresh(ModelInventoryRefreshReason.STARTUP) }
        if(backgroundDownloads) scope.launch {
            val seen=mutableSetOf<java.util.UUID>()
            androidx.work.WorkManager.getInstance(context).getWorkInfosByTagFlow(LiteRtDownloadWorker.TAG).collect { infos ->
                infos.groupBy { it.tags.firstOrNull { tag -> tag.startsWith("halo-model:") } }
                    .values.mapNotNull { group -> group.maxByOrNull { info ->
                        info.tags.firstOrNull { it.startsWith("halo-model-time:") }?.removePrefix("halo-model-time:")?.toLongOrNull() ?: 0L
                    } }.forEach { info ->
                    val id=info.tags.firstOrNull { it.startsWith("halo-model:") }?.removePrefix("halo-model:") ?: return@forEach
                    when(info.state) {
                        androidx.work.WorkInfo.State.ENQUEUED, androidx.work.WorkInfo.State.BLOCKED -> setDownloadState(id,DownloadState.Queued)
                        androidx.work.WorkInfo.State.RUNNING -> {
                            val bytes=info.progress.getLong("bytes",0); val total=catalogModel(id)?.expectedBytes
                            setDownloadState(id, if(info.progress.getString("phase")=="Verifying") DownloadState.Verifying(bytes,total)
                                else DownloadState.Downloading(bytes,total,progressOf(bytes,total ?: 0L)))
                        }
                        androidx.work.WorkInfo.State.SUCCEEDED -> if(seen.add(info.id)) completeBackgroundDownload(id)
                        androidx.work.WorkInfo.State.CANCELLED -> if(seen.add(info.id)) {
                            setDownloadState(id,DownloadState.Paused(partialDownloadBytes(id) ?: 0,catalogModel(id)?.expectedBytes))
                        }
                        androidx.work.WorkInfo.State.FAILED -> if(seen.add(info.id)) {
                            val kind=runCatching { ModelDownloadFailure.valueOf(info.outputData.getString("kind").orEmpty()) }.getOrDefault(ModelDownloadFailure.IO_FAILURE)
                            setDownloadState(id,DownloadState.Failed(kind,info.outputData.getString("message") ?: "Model download failed",resumable=partialDownloadBytes(id)!=null))
                        }
                    }
                }
            }
        }
    }

    override suspend fun verifyPhysicalArtifact(modelId: String): LocalModelDescriptor? {
        val snapshot = refreshSnapshot(ModelInventoryRefreshReason.INVALIDATED, verifyModelId = modelId)
        return (snapshot as? LocalModelInventoryState.Ready)?.models?.firstOrNull { it.id == modelId && it.valid }
    }

    override fun invalidate(reason: String) {
        scope.launch { refresh(ModelInventoryRefreshReason.INVALIDATED) }
    }

    override suspend fun refresh(reason: ModelInventoryRefreshReason) { refreshSnapshot(reason) }

    /** Terminal work must release its progress state before inventory reconciliation. */
    internal suspend fun completeBackgroundDownload(modelId: String) {
        setDownloadState(modelId, DownloadState.NotInstalled)
        val snapshot = refreshSnapshot(ModelInventoryRefreshReason.DOWNLOAD_COMPLETED)
        if ((snapshot as? LocalModelInventoryState.Ready)?.models?.none { it.id == modelId } != false) {
            setDownloadState(modelId, DownloadState.Failed(
                ModelDownloadFailure.IO_FAILURE,
                "Downloaded model could not be found. Retry the download.",
                resumable = partialDownloadBytes(modelId) != null,
            ))
        }
    }

    /** Return the scan's immutable result before another refresh can publish Loading. */
    private suspend fun refreshSnapshot(reason: ModelInventoryRefreshReason, verifyModelId: String? = null): LocalModelInventoryState = inventoryMutex.withLock {
        // Explicit integrity checks bypass the cache, while completed downloads only hash new files.
        if (verifyModelId != null) {
            val path = (_inventoryState.value as? LocalModelInventoryState.Ready)?.models
                ?.firstOrNull { it.id == verifyModelId }?.path
                ?: File(modelRoot, "$verifyModelId.litertlm").absolutePath
            physicalFingerprints.keys.removeAll { it.startsWith("$path|") }
        } else if (reason == ModelInventoryRefreshReason.INVALIDATED || reason == ModelInventoryRefreshReason.USER_REQUEST) {
            physicalFingerprints.clear()
        }
        // Keep the last usable inventory visible while refreshing. Publishing Loading here made
        // listModels() temporarily empty and routed installed models as unavailable.
        if (_inventoryState.value !is LocalModelInventoryState.Ready) {
            _inventoryState.value = LocalModelInventoryState.Loading
        }
        runCatching {
            withContext(Dispatchers.IO) { scanModelRoot() }
        }.onSuccess { descriptors ->
            if (_acquisitionDiagnostics.value?.stage == LiteRtFailureStage.INVENTORY) {
                _acquisitionDiagnostics.value = null
            }
            _inventoryState.value = LocalModelInventoryState.Ready(descriptors, System.currentTimeMillis(), inventoryRevision.incrementAndGet())
            reconcileDownloadStates(descriptors)
            Log.i(
                TAG,
                "$EVENT_INVENTORY reason=$reason models=${descriptors.size} " +
                    "verified=${descriptors.count { it.verified }}",
            )
        }.onFailure { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.e(TAG, "HALO_LITERT_INVENTORY_FAILED reason=$reason", error)
            _acquisitionDiagnostics.value = LiteRtAcquisitionDiagnostics(
                modelId = null,
                stage = LiteRtFailureStage.INVENTORY,
                artifactFingerprint = null,
                exceptionClass = error::class.java.simpleName,
                nativeMessage = error.message,
            )
            _inventoryState.value = LocalModelInventoryState.Failed(error.message ?: "Model inventory scan failed")
        }
        _inventoryState.value
    }

    /**
     * Resolves a descriptor without ever reporting MODEL_MISSING because the inventory happened to
     * still be loading at startup. Never called while holding [inventoryMutex].
     */
    override suspend fun resolveModel(modelId: String): LocalModelDescriptor? {
        var state = inventoryState.value
        if (state is LocalModelInventoryState.Loading) {
            // Reuse an in-flight scan rather than queuing a second full-model
            // fingerprint pass behind the inventory mutex on cold startup.
            state = inventoryMutex.withLock { inventoryState.value }
        }
        if (state !is LocalModelInventoryState.Ready) {
            state = refreshSnapshot(ModelInventoryRefreshReason.USER_REQUEST)
        }
        val cached = (state as? LocalModelInventoryState.Ready)?.models?.firstOrNull { it.id == modelId }
            ?: return null
        val unchanged = withContext(Dispatchers.IO) {
            File(cached.path).let { it.isFile && it.length() == cached.bytes && it.lastModified() == cached.modifiedAt }
        }
        if (unchanged) return cached
        // A cached valid row is not proof that its file still exists. Reconcile
        // changed bytes before readiness, role filtering or inference consumes it.
        return (refreshSnapshot(ModelInventoryRefreshReason.INVALIDATED, verifyModelId = modelId)
            as? LocalModelInventoryState.Ready)?.models?.firstOrNull { it.id == modelId }
    }

    // ---------------------------------------------------------------------------------------
    // Download
    // ---------------------------------------------------------------------------------------

    fun downloadCatalogModel(model: LiteRtCatalogModel, hubToken: String? = null, licenseAcknowledged: Boolean = false) {
        Log.i(TAG, "$EVENT_REQUESTED id=${model.id}")
        val token = hubToken?.trim()?.takeIf { it.isNotBlank() } ?: sessionHubToken
        val validToken = token?.takeIf(::isHuggingFaceReadToken)
        val authenticatedGate = model.access == LiteRtCatalogAccess.GATED_IMPORT_ONLY && licenseAcknowledged && validToken != null
        if (!model.directDownloadAllowed && !authenticatedGate) {
            // Gated / manual artifacts must never be fetched by the built-in downloader.
            Log.w(TAG, "$EVENT_FAILED id=${model.id} reason=IMPORT_ONLY access=${model.access}")
            _acquisitionDiagnostics.value = LiteRtAcquisitionDiagnostics(
                modelId = model.id,
                stage = LiteRtFailureStage.DOWNLOAD,
                artifactFingerprint = model.sha256,
                exceptionClass = "ImportOnlyArtifact",
                nativeMessage = model.importGuidance,
            )
            setDownloadState(
                model.id,
                DownloadState.Failed(
                    ModelDownloadFailure.AUTH_REQUIRED,
                    model.importGuidance ?: "This artifact must be imported from device.",
                    "access=${model.access}",
                    resumable = false,
                ),
            )
            return
        }
        if(backgroundDownloads && validToken == null) {
            setDownloadState(model.id,DownloadState.Queued)
            val request=LiteRtDownloadWorker.request(model)
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("halo-model:${model.id}",androidx.work.ExistingWorkPolicy.KEEP,request)
            return
        }
        synchronized(downloadLock) {
            if (downloadJobs[model.id]?.isCompleted == false) {
                Log.i(TAG, "$EVENT_FAILED id=${model.id} reason=ALREADY_DOWNLOADING")
                return
            }
            setDownloadState(model.id, DownloadState.Queued)
            downloadJobs[model.id] = scope.launch {
                try {
                    runDownload(model, validToken)
                } finally {
                    synchronized(downloadLock) { downloadJobs.remove(model.id) }
                }
            }
        }
    }

    /** Resumes an interrupted transfer, or starts one when no partial exists. */
    fun resumeDownload(model: LiteRtCatalogModel) = downloadCatalogModel(model)

    fun isDownloading(modelId: String): Boolean = downloadJobs[modelId]?.isCompleted == false ||
        _downloadStates.value[modelId].let { it is DownloadState.Queued || it is DownloadState.Preparing || it is DownloadState.Downloading || it is DownloadState.Verifying }

    /** Cancels an in-flight download. Never touches an existing verified artifact. */
    fun cancelDownload(modelId: String) {
        if(backgroundDownloads) androidx.work.WorkManager.getInstance(context).cancelUniqueWork("halo-model:$modelId")
        val job = synchronized(downloadLock) { downloadJobs[modelId] }
        job?.cancel(CancellationException("Model download cancelled by user"))
    }

    /** Bytes of a stored partial, or null when there is nothing to resume. */
    fun partialDownloadBytes(modelId: String): Long? {
        val part = File(modelRoot, "$modelId.litertlm.part")
        return part.takeIf { it.exists() && it.length() > 0L }?.length()
    }

    /** Explicit "discard download": removes the partial artifact and its metadata. */
    fun discardPartial(modelId: String) {
        if(isDownloading(modelId)) return
        val partFile = File(modelRoot, "$modelId.litertlm.part")
        val metaFile = LiteRtPartialDownloadMetadata.sidecarFor(partFile)
        val deleted = partFile.exists() && partFile.delete()
        metaFile.delete()
        Log.i(TAG, "HALO_LITERT_DISCARD_PARTIAL id=$modelId deleted=$deleted")
        setDownloadState(modelId, DownloadState.NotInstalled)
    }

    /** Preflight for a catalog artifact, so the UI can warn before starting 1-2 GB of transfer. */
    fun storagePreflight(model: LiteRtCatalogModel): LiteRtStoragePreflightResult =
        LiteRtStoragePreflight.check(modelRoot, model.expectedBytes)

    internal suspend fun runDownload(model: LiteRtCatalogModel, hubToken: String? = null) {
        val key=modelRoot.canonicalPath + "/" + model.id
        transferMutexes.computeIfAbsent(key) { Mutex() }.withLock { runDownloadLocked(model,hubToken) }
    }
    private suspend fun runDownloadLocked(model: LiteRtCatalogModel, hubToken: String?) {
        val partFile = File(modelRoot, "${model.id}.litertlm.part")
        val finalFile = File(modelRoot, "${model.id}.litertlm")
        val sidecarFile = File(modelRoot, "${model.id}.halo-model.json")
        val sidecarPart = File(modelRoot, "${model.id}.halo-model.json.part")
        val partialMetaFile = LiteRtPartialDownloadMetadata.sidecarFor(partFile)

        try {
            setDownloadState(model.id, DownloadState.Preparing)
            Log.i(TAG, "$EVENT_PREPARING id=${model.id}")
            // 1. model directory
            if (!modelRoot.exists() && !modelRoot.mkdirs()) {
                throw IOException("Unable to create model directory ${modelRoot.absolutePath}")
            }
            if (sidecarPart.exists()) sidecarPart.delete()

            val resumeFrom = resolveResumeOffset(model, partFile, partialMetaFile)
            // Reserve only the remaining transfer: an interrupted prefix already occupies disk.
            val preflight = LiteRtStoragePreflight.check(modelRoot, (model.expectedBytes-resumeFrom).coerceAtLeast(1L))
            Log.i(TAG, "$EVENT_STORAGE id=${model.id} ${preflight.describe()}")
            if (!preflight.sufficient) {
                _acquisitionDiagnostics.value = LiteRtAcquisitionDiagnostics(
                    modelId = model.id,
                    stage = LiteRtFailureStage.DOWNLOAD,
                    artifactFingerprint = model.sha256,
                    exceptionClass = "InsufficientStorage",
                    nativeMessage = preflight.describe(),
                )
                setDownloadState(
                    model.id,
                    DownloadState.Failed(
                        ModelDownloadFailure.DISK_FULL,
                        preflight.describe(),
                        preflight.describe(),
                        resumable = partialDownloadBytes(model.id) != null,
                    ),
                )
                return
            }

            // 3-4. resume only against an exactly matching pinned artifact
            if (resumeFrom > 0L) {
                Log.i(TAG, "$EVENT_RESUME id=${model.id} from=$resumeFrom expected=${model.expectedBytes}")
            }
            writePartialMetadata(partialMetaFile, model, resumeFrom)

            setDownloadState(
                model.id,
                DownloadState.Downloading(
                    downloadedBytes = resumeFrom,
                    totalBytes = model.expectedBytes,
                    progress = progressOf(resumeFrom, model.expectedBytes),
                    resumedFrom = resumeFrom,
                ),
            )
            Log.i(TAG, "$EVENT_START id=${model.id} from=$resumeFrom expected=${model.expectedBytes}")

            var lastLoggedBytes = resumeFrom
            var lastMetadataBytes = resumeFrom
            // 5-8. stream, fsync/close, verify byte count, verify SHA (inside the downloader)
            val transfer = if(hubToken == null) downloader else OkHttpLiteRtModelDownloader(authToken = { hubToken })
            val result = transfer.download(model, partFile, { progress ->
                if (progress.downloadedBytes - lastLoggedBytes >= PROGRESS_LOG_INTERVAL_BYTES) {
                    lastLoggedBytes = progress.downloadedBytes
                    Log.i(
                        TAG,
                        "$EVENT_PROGRESS id=${model.id} bytes=${progress.downloadedBytes} " +
                            "total=${progress.totalBytes}",
                    )
                }
                if (progress.downloadedBytes - lastMetadataBytes >= PROGRESS_METADATA_INTERVAL_BYTES) {
                    lastMetadataBytes = progress.downloadedBytes
                    writePartialMetadata(partialMetaFile, model, progress.downloadedBytes)
                }
                setDownloadState(
                    model.id,
                    DownloadState.Downloading(
                        progress.downloadedBytes,
                        progress.totalBytes,
                        progress.progress,
                        resumedFrom = resumeFrom,
                    ),
                )
            }, resumeFrom)

            when (result) {
                is DownloadResult.Failure -> handleDownloadFailure(model, partFile, partialMetaFile, result)
                is DownloadResult.Success -> {
                    partialMetaFile.delete()
                    installVerifiedArtifact(
                        model = model,
                        verifiedFile = result.file,
                        finalFile = finalFile,
                        sidecarFile = sidecarFile,
                        sidecarPart = sidecarPart,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            // A cancelled transfer keeps its partial so "Resume" is meaningful.
            val partialBytes = partFile.takeIf { it.exists() }?.length() ?: 0L
            val resumable = partialBytes in 1 until model.expectedBytes
            if (resumable) {
                writePartialMetadata(partialMetaFile, model, partialBytes)
            } else {
                partFile.delete()
                partialMetaFile.delete()
            }
            _acquisitionDiagnostics.value = LiteRtAcquisitionDiagnostics(
                modelId = model.id,
                stage = LiteRtFailureStage.CANCEL,
                artifactFingerprint = model.sha256,
                exceptionClass = cancelled::class.java.simpleName,
                nativeMessage = cancelled.message,
            )
            setDownloadState(
                model.id,
                if (resumable) DownloadState.Paused(partialBytes, model.expectedBytes)
                else DownloadState.NotInstalled,
            )
            Log.i(TAG, "$EVENT_FAILED id=${model.id} kind=CANCELLED resumable=$resumable partialBytes=$partialBytes")
            throw cancelled
        } catch (error: Throwable) {
            val partialBytes = partFile.takeIf { it.exists() }?.length() ?: 0L
            val resumable = partialBytes in 1 until model.expectedBytes
            if (resumable) writePartialMetadata(partialMetaFile, model, partialBytes)
            _acquisitionDiagnostics.value = LiteRtAcquisitionDiagnostics(
                modelId = model.id,
                stage = LiteRtFailureStage.DOWNLOAD,
                artifactFingerprint = model.sha256,
                exceptionClass = error::class.java.simpleName,
                nativeMessage = error.message,
            )
            Log.e(TAG, "$EVENT_FAILED id=${model.id} kind=IO_FAILURE", error)
            setDownloadState(
                model.id,
                DownloadState.Failed(
                    ModelDownloadFailure.IO_FAILURE,
                    "The model could not be saved to this device.",
                    "${error::class.java.simpleName}: ${error.message}",
                    resumable = resumable,
                ),
            )
        }
    }

    private fun handleDownloadFailure(
        model: LiteRtCatalogModel,
        partFile: File,
        partialMetaFile: File,
        failure: DownloadResult.Failure,
    ) {
        val partialBytes = partFile.takeIf { it.exists() }?.length() ?: 0L
        val resumable = failure.kind.isResumable && partialBytes in 1 until model.expectedBytes
        if (resumable) {
            writePartialMetadata(partialMetaFile, model, partialBytes)
        } else {
            // Hash mismatch, oversized partial, or a dead revision: never keep it.
            partFile.delete()
            partialMetaFile.delete()
        }
        _acquisitionDiagnostics.value = LiteRtAcquisitionDiagnostics(
            modelId = model.id,
            stage = when (failure.kind) {
                ModelDownloadFailure.HASH_MISMATCH,
                ModelDownloadFailure.SIZE_MISMATCH -> LiteRtFailureStage.HASH_VERIFY
                ModelDownloadFailure.CANCELLED -> LiteRtFailureStage.CANCEL
                else -> LiteRtFailureStage.DOWNLOAD
            },
            artifactFingerprint = model.sha256,
            exceptionClass = failure.cause?.let { it::class.java.simpleName } ?: failure.kind.name,
            nativeMessage = failure.technicalMessage,
        )
        Log.e(
            TAG,
            "$EVENT_FAILED id=${model.id} kind=${failure.kind} resumable=$resumable " +
                "partialBytes=$partialBytes detail=${failure.technicalMessage}",
        )
        setDownloadState(
            model.id,
            when {
                failure.kind == ModelDownloadFailure.CANCELLED && resumable ->
                    DownloadState.Paused(partialBytes, model.expectedBytes)
                failure.kind == ModelDownloadFailure.CANCELLED -> DownloadState.NotInstalled
                else -> DownloadState.Failed(failure.kind, failure.userMessage, failure.technicalMessage, resumable)
            },
        )
    }

    private fun resolveResumeOffset(
        model: LiteRtCatalogModel,
        partFile: File,
        partialMetaFile: File,
    ): Long {
        if (!partFile.exists()) {
            partialMetaFile.delete()
            return 0L
        }
        val existing = partFile.length()
        if (existing <= 0L || existing > model.expectedBytes) {
            Log.i(TAG, "HALO_LITERT_PARTIAL_STALE id=${model.id} bytes=$existing expected=${model.expectedBytes}")
            partFile.delete()
            partialMetaFile.delete()
            return 0L
        }
        val metadata = LiteRtPartialDownloadMetadata.fromFile(partialMetaFile)
        if (metadata == null || !metadata.matches(model) || metadata.downloadedBytes > existing || metadata.downloadedBytes < 0L) {
            Log.i(
                TAG,
                "HALO_LITERT_PARTIAL_UNPINNED id=${model.id} bytes=$existing " +
                    "declared=${metadata?.downloadedBytes ?: "none"} matches=${metadata?.matches(model) == true}",
            )
            partFile.delete()
            partialMetaFile.delete()
            return 0L
        }
        return existing
    }

    private fun writePartialMetadata(
        partialMetaFile: File,
        model: LiteRtCatalogModel,
        downloadedBytes: Long,
        etag: String? = null,
        lastModified: String? = null,
    ) {
        runCatching {
            val metadata = LiteRtPartialDownloadMetadata.forArtifact(model, downloadedBytes)
                .copy(etag = etag, lastModified = lastModified)
            partialMetaFile.writeText(metadata.toJson())
        }.onFailure { error ->
            Log.w(TAG, "HALO_LITERT_PARTIAL_METADATA_WRITE_FAILED id=${model.id}", error)
        }
    }

    private suspend fun installVerifiedArtifact(
        model: LiteRtCatalogModel,
        verifiedFile: File,
        finalFile: File,
        sidecarFile: File,
        sidecarPart: File,
    ) {
        setDownloadState(model.id, DownloadState.Verifying(verifiedFile.length(), model.expectedBytes))
        Log.i(TAG, "$EVENT_VERIFY id=${model.id} bytes=${verifiedFile.length()} sha256=${model.sha256}")

        // 9. sidecar staged as .json.part
        val metadata = InstalledLiteRtModelMetadata.fromCatalog(model, sourceUrl = model.downloadUrl)
        sidecarPart.writeText(metadata.toJson())

        // 10-11. move model, then sidecar, into their final paths
        moveIntoPlace(verifiedFile, finalFile)
        moveIntoPlace(sidecarPart, sidecarFile)
        physicalFingerprints.remove(fingerprintKey(finalFile))

        // 12. refresh inventory
        refresh(ModelInventoryRefreshReason.DOWNLOAD_COMPLETED)

        // 13. the inventory must resolve the exact model as usable
        val installed = resolveModel(model.id)
        if (installed == null || !installed.valid) {
            Log.e(
                TAG,
                "$EVENT_FAILED id=${model.id} reason=NOT_RESOLVED_AFTER_INSTALL " +
                    "resolved=${installed?.id} validation=${installed?.validation}",
            )
            setDownloadState(
                model.id,
                DownloadState.Failed(
                    ModelDownloadFailure.HASH_MISMATCH,
                    OkHttpLiteRtModelDownloader.INTEGRITY_USER_MESSAGE,
                    "Inventory did not resolve ${model.id} as valid after install " +
                        "(validation=${installed?.validation?.name ?: "missing"})",
                    resumable = false,
                ),
            )
            _acquisitionDiagnostics.value = LiteRtAcquisitionDiagnostics(
                modelId = model.id,
                stage = LiteRtFailureStage.HASH_VERIFY,
                artifactFingerprint = model.sha256,
                exceptionClass = ModelDownloadFailure.HASH_MISMATCH.name,
                nativeMessage = "Installed artifact did not resolve as valid " +
                    "(${installed?.validation?.name ?: "missing"})",
            )
            return
        }

        // 14. only now is the model actually installed
        setDownloadState(model.id, DownloadState.Installed)
        _acquisitionDiagnostics.value = null
        Log.i(
            TAG,
            "$EVENT_INSTALLED id=${model.id} bytes=${finalFile.length()} sha256=${installed.sha256} " +
                "validation=${installed.validation}",
        )
    }

    // ---------------------------------------------------------------------------------------
    // Import (SAF)
    // ---------------------------------------------------------------------------------------

    suspend fun importModelFromUri(uri: Uri): Result<LocalModelDescriptor> = withContext(Dispatchers.IO) {
        importMutex.withLock {
        importing.value = "Preparing model import…"
        var temporaryImport: File? = null
        try { runCatching {
            val contentResolver = context.contentResolver
            if (!modelRoot.exists() && !modelRoot.mkdirs()) {
                throw IOException("Unable to create model directory ${modelRoot.absolutePath}")
            }

            var filename = "imported_model"
            var expectedBytes: Long? = null
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameColumn >= 0) filename = cursor.getString(nameColumn) ?: filename
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) expectedBytes = cursor.getLong(sizeColumn).takeIf { it > 0 }
                }
            }
            if (!filename.endsWith(".litertlm", ignoreCase = true)) {
                throw IllegalArgumentException("Imported model must be a .litertlm file")
            }

            if (expectedBytes != null && expectedBytes!! > (modelRoot.usableSpace - 64L * 1024 * 1024).coerceAtLeast(0))
                throw IOException("Not enough free storage to import this model. Free space and try again.")
            val tempFile = File(modelRoot, "import_${UUID.randomUUID()}.litertlm.part")
            temporaryImport = tempFile
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var copied = 0L
                        var lastReported = -1L
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            copied += read
                            if (copied - lastReported >= 1024 * 1024) {
                                importing.value = "Importing model: ${copied / 1_000_000} MB" +
                                    (expectedBytes?.let { " / ${it / 1_000_000} MB" } ?: "")
                                lastReported = copied
                            }
                        }
                        if (expectedBytes != null && copied != expectedBytes)
                            throw IOException("The file provider returned an incomplete model. Download the full file and import again.")
                        output.flush()
                        output.fd.sync()
                    }
                } ?: throw IOException("Unable to open URI input stream")
            } catch (error: Throwable) {
                tempFile.delete()
                throw error
            }

            val bytes = tempFile.length()
            if (bytes == 0L) {
                tempFile.delete()
                throw IllegalArgumentException("Imported file was empty")
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }

            // A byte-identical known artifact (including a gated one) is canonicalized instead of
            // duplicated, so a licensed Gemma import adopts the catalog identity.
            val catalogMatch = mutableCatalog.value.firstOrNull { it.sha256.equals(sha256, ignoreCase = true) }
            val cleanName = filename.dropLast(".litertlm".length)
            val modelId = catalogMatch?.id ?: fingerprintModelId(cleanName, sha256)
            val finalFile = File(modelRoot, "$modelId.litertlm")

            if (isDownloading(modelId)) {
                tempFile.delete()
                throw IllegalStateException("Cancel the download for this model before importing it.")
            }
            if (finalFile.exists()) {
                refresh(ModelInventoryRefreshReason.USER_REQUEST)
                val existing = resolveModel(modelId)
                if (existing?.valid == true) {
                    tempFile.delete()
                    return@runCatching existing
                }
                if (residentModelIdProvider() == modelId) {
                    tempFile.delete()
                    throw IllegalStateException("Unload this model before replacing its damaged file.")
                }
                // The incoming bytes have their own computed hash. A damaged existing copy can
                // now be replaced instead of making every subsequent import fail forever.
            }

            importing.value = "Registering imported model…"
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            moveIntoPlace(tempFile, finalFile)
            physicalFingerprints.remove(fingerprintKey(finalFile))

            val metadata = catalogMatch?.let { InstalledLiteRtModelMetadata.fromCatalog(it, sourceUrl = null) }
                ?: InstalledLiteRtModelMetadata(
                    id = modelId,
                    displayName = cleanName.replace('_', ' ').replace('-', ' '),
                    sourceUrl = null,
                    sha256 = sha256,
                    bytes = finalFile.length(),
                    // Unknown metadata: let LiteRT-LM use the model's own default context.
                    contextWindowTokens = InstalledLiteRtModelMetadata.UNKNOWN_CONTEXT_TOKENS,
                    contextKnown = false,
                    supportsVision = false,
                    supportsTools = false,
                )
            File(modelRoot, "$modelId.halo-model.json").writeText(metadata.toJson())

            refresh(ModelInventoryRefreshReason.DOWNLOAD_COMPLETED)
            resolveModel(modelId)?.takeIf { it.valid }
                ?: throw IllegalStateException("Model import failed to register")
        }.onFailure { if (it is CancellationException) throw it }
        } finally { temporaryImport?.delete(); importing.value = null }
        }
    }

    /**
     * Native LiteRT metadata can reveal a vision-capable *imported* generator
     * that lacked a publisher-sidecar capability declaration. This merely
     * exposes the model to Vision Lab: it is NOT a successful visual probe.
     *
     * Only an explicit user inspection calls this; no startup native load or
     * automatic qualification. Re-check physical SHA before changing metadata.
     */
    suspend fun recordInspectedVision(modelId: String, expectedSha: String, nativeVision: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            importMutex.withLock {
                val descriptor = verifyPhysicalArtifact(modelId) ?: return@withLock false
                if (!descriptor.valid || descriptor.sha256 != expectedSha ||
                    descriptor.validation != LocalModelValidation.UNVERIFIED) return@withLock false
                val sidecarFile = File(modelRoot, "$modelId.halo-model.json")
                val previous = runCatching {
                    InstalledLiteRtModelMetadata.fromJson(sidecarFile.readText())
                }.getOrNull() ?: return@withLock false
                if (previous.sha256 != expectedSha || previous.bytes != descriptor.bytes ||
                    previous.id != descriptor.id) return@withLock false
                if (previous.supportsVision != nativeVision) {
                    val staged = File(modelRoot, "$modelId.halo-model.json.part")
                    try {
                        staged.writeText(previous.copy(supportsVision = nativeVision).toJson())
                        moveIntoPlace(staged, sidecarFile)
                    } finally { staged.delete() }
                    refresh(ModelInventoryRefreshReason.INVALIDATED)
                }
                true
            }
        }

    // ---------------------------------------------------------------------------------------
    // Deletion
    // ---------------------------------------------------------------------------------------

    suspend fun deleteModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        if (residentModelIdProvider() == modelId || isDownloading(modelId)) return@withContext false
        val descriptor = resolveModel(modelId) ?: return@withContext false
        val modelFile = File(descriptor.path)
        val sidecarFile = File(modelRoot, "$modelId.halo-model.json")
        val cacheFolder = File(context.filesDir, "litertlm_cache")

        var deleted = false
        if (modelFile.exists()) deleted = modelFile.delete()
        if (sidecarFile.exists()) sidecarFile.delete()
        val partFile = File(modelRoot, "$modelId.litertlm.part")
        partFile.delete()
        LiteRtPartialDownloadMetadata.sidecarFor(partFile).delete()
        File(modelRoot, "$modelId.halo-model.json.part").delete()
        physicalFingerprints.remove(fingerprintKey(modelFile))

        if (cacheFolder.exists()) {
            val cachePrefix = "${modelId.replace('/', '_')}-"
            cacheFolder.listFiles { f -> f.isDirectory && f.name.startsWith(cachePrefix) }?.forEach {
                it.deleteRecursively()
            }
        }

        refresh(ModelInventoryRefreshReason.INVALIDATED)
        deleted
    }

    suspend fun deleteLegacyGgufFiles(): Long = withContext(Dispatchers.IO) {
        var deletedBytes = 0L
        _legacyGgufFiles.value.forEach { file ->
            if (file.exists()) {
                deletedBytes += file.length()
                file.delete()
            }
        }
        _legacyGgufFiles.value = emptyList()
        deletedBytes
    }

    fun getAllCatalogModels(): List<LiteRtCatalogModel> = mutableCatalog.value

    fun catalogModel(modelId: String): LiteRtCatalogModel? = mutableCatalog.value.firstOrNull { it.id == modelId }

    // ---------------------------------------------------------------------------------------
    // Inventory scan
    // ---------------------------------------------------------------------------------------

    private fun scanModelRoot(): List<LocalModelDescriptor> {
        if (!modelRoot.exists()) modelRoot.mkdirs()

        _legacyGgufFiles.value = modelRoot
            .listFiles { f -> f.isFile && f.name.endsWith(".gguf", ignoreCase = true) }
            ?.toList()
            .orEmpty()

        val candidates = modelRoot
            .listFiles { f -> f.isFile && f.name.endsWith(".litertlm", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()

        val descriptors = mutableListOf<LocalModelDescriptor>()
        for (candidate in candidates) {
            val physical = physicalFingerprint(candidate)
            val catalogBySha = mutableCatalog.value.firstOrNull { it.sha256.equals(physical.sha256, ignoreCase = true) }
            val catalogById = mutableCatalog.value.firstOrNull { it.id == candidate.nameWithoutExtension }
            val catalogMatch = catalogById ?: catalogBySha

            // A byte-identical copy of a pinned artifact is canonicalized to the catalog id so an
            // import never produces a second, duplicate copy of the same multi-GB file.
            var located: File = candidate
            if (catalogBySha != null && candidate.name != "${catalogBySha.id}.litertlm") {
                val canonical = File(modelRoot, "${catalogBySha.id}.litertlm")
                if (canonical.exists()) {
                    descriptors += duplicateDescriptor(candidate, physical, catalogBySha)
                    continue
                }
                Log.w(TAG, "HALO_LITERT_INVENTORY_CANONICALIZED from=${candidate.name} to=${canonical.name}")
                located = try {
                    moveIntoPlace(candidate, canonical)
                    canonical
                } catch (error: Throwable) {
                    Log.e(TAG, "HALO_LITERT_INVENTORY_CANONICALIZE_FAILED from=${candidate.name}", error)
                    candidate
                }
            }

            descriptors += describe(located, physical, catalogMatch)
        }
        return descriptors
    }

    private fun duplicateDescriptor(
        file: File,
        physical: PhysicalFingerprint,
        catalog: LiteRtCatalogModel,
    ): LocalModelDescriptor = LocalModelDescriptor(
        id = file.nameWithoutExtension,
        displayName = file.nameWithoutExtension.replace('_', ' ').replace('-', ' '),
        path = file.absolutePath,
        bytes = physical.bytes,
        modifiedAt = file.lastModified(),
        sha256 = physical.sha256,
        contextWindowTokens = catalog.contextWindowTokens,
        supportsVision = catalog.supportsVision,
        supportsTools = catalog.supportsTools,
        validation = LocalModelValidation.INCOMPATIBLE,
        validationDetail = "Duplicate copy of ${catalog.displayName}. Delete one copy.",
        supportsText = catalog.supportsText,
        supportsAudio = catalog.supportsAudio,
        supportsThinking = catalog.supportsThinking,
        family = catalog.family,
        quantizationLabel = catalog.quantizationLabel,
        catalogAccess = catalog.access,
        maturity = catalog.maturity,
        minDeviceRamGb = catalog.minDeviceRamGb,
    )

    private fun describe(
        file: File,
        physical: PhysicalFingerprint,
        catalogMatch: LiteRtCatalogModel?,
    ): LocalModelDescriptor {
        val sidecarFile = File(modelRoot, "${file.nameWithoutExtension}.halo-model.json")
        val sidecar = if (sidecarFile.exists()) {
            runCatching { InstalledLiteRtModelMetadata.fromJson(sidecarFile.readText()) }.getOrNull()
        } else {
            null
        }

        if (catalogMatch != null) {
            val bytesOk = physical.bytes == catalogMatch.expectedBytes
            val shaOk = physical.sha256.equals(catalogMatch.sha256, ignoreCase = true)
            val validation = if (bytesOk && shaOk) {
                LocalModelValidation.VERIFIED_ARTIFACT
            } else {
                LocalModelValidation.CORRUPT
            }
            val detail = if (validation == LocalModelValidation.CORRUPT) {
                "Installed file does not match the pinned ${catalogMatch.displayName} artifact. " +
                    "Expected ${catalogMatch.expectedBytes} bytes / ${catalogMatch.sha256.take(12)}…, " +
                    "found ${physical.bytes} bytes / ${physical.sha256.take(12)}…."
            } else {
                null
            }
            if (validation == LocalModelValidation.CORRUPT) {
                Log.w(
                    TAG,
                    "HALO_LITERT_INVENTORY_STALE id=${catalogMatch.id} bytes=${physical.bytes} " +
                        "sha256=${physical.sha256} expectedBytes=${catalogMatch.expectedBytes} " +
                        "expectedSha256=${catalogMatch.sha256}",
                )
            }
            return LocalModelDescriptor(
                id = catalogMatch.id,
                displayName = catalogMatch.displayName,
                path = file.absolutePath,
                bytes = physical.bytes,
                modifiedAt = file.lastModified(),
                sha256 = physical.sha256,
                contextWindowTokens = catalogMatch.resolvedContextTokens ?: catalogMatch.contextWindowTokens,
                supportsVision = catalogMatch.supportsVision,
                supportsTools = catalogMatch.supportsTools,
                validation = validation,
                useModelDefaultContext = catalogMatch.runtimeProfile.useModelDefaultContext,
                validationDetail = detail,
                supportsText = catalogMatch.supportsText,
                supportsAudio = catalogMatch.supportsAudio,
                supportsThinking = catalogMatch.supportsThinking,
                family = catalogMatch.family,
                quantizationLabel = catalogMatch.quantizationLabel,
                catalogAccess = catalogMatch.access,
                maturity = catalogMatch.maturity,
                minDeviceRamGb = catalogMatch.minDeviceRamGb,
            )
        }

        // Not a pinned artifact: an arbitrary user import.
        if (physical.bytes == 0L) {
            return LocalModelDescriptor(
                id = file.nameWithoutExtension,
                displayName = file.nameWithoutExtension,
                path = file.absolutePath,
                bytes = 0L,
                modifiedAt = file.lastModified(),
                sha256 = physical.sha256,
                contextWindowTokens = DEFAULT_CONTEXT_FOR_DISPLAY,
                supportsVision = false,
                supportsTools = false,
                validation = LocalModelValidation.INCOMPATIBLE,
                useModelDefaultContext = true,
                validationDetail = "The imported file is empty.",
            )
        }

        val sidecarDisagrees = sidecar != null &&
            (sidecar.bytes != physical.bytes || !sidecar.sha256.equals(physical.sha256, ignoreCase = true))

        val contextKnown = sidecar?.contextKnown ?: false
        val declaredContext = sidecar?.contextWindowTokens?.takeIf { it > 0 } ?: DEFAULT_CONTEXT_FOR_DISPLAY

        return LocalModelDescriptor(
            id = file.nameWithoutExtension,
            displayName = sidecar?.displayName
                ?: file.nameWithoutExtension.replace('_', ' ').replace('-', ' '),
            path = file.absolutePath,
            bytes = physical.bytes,
            modifiedAt = file.lastModified(),
            sha256 = physical.sha256,
            contextWindowTokens = declaredContext,
            supportsVision = sidecar?.supportsVision ?: false,
            supportsTools = sidecar?.supportsTools ?: false,
            validation = if (sidecarDisagrees) LocalModelValidation.CORRUPT else LocalModelValidation.UNVERIFIED,
            useModelDefaultContext = !contextKnown,
            validationDetail = if (sidecarDisagrees) {
                "The file no longer matches its recorded fingerprint " +
                    "(recorded ${sidecar?.bytes} bytes / ${sidecar?.sha256?.take(12)}…, " +
                    "found ${physical.bytes} bytes / ${physical.sha256.take(12)}…)."
            } else {
                "Imported model — not verified against a pinned artifact."
            },
            supportsText = sidecar?.supportsText ?: true,
            supportsAudio = sidecar?.supportsAudio ?: false,
            supportsThinking = sidecar?.supportsThinking ?: false,
            family = sidecar?.family,
            quantizationLabel = sidecar?.quantizationLabel,
            catalogAccess = sidecar?.access,
            maturity = sidecar?.maturity,
            minDeviceRamGb = sidecar?.minDeviceRamGb,
        )
    }

    private fun reconcileDownloadStates(descriptors: List<LocalModelDescriptor>) {
        val partials = storedPartials()
        _downloadStates.update { current ->
            val next = current.toMutableMap()
            descriptors.forEach { descriptor ->
                val existing = next[descriptor.id]
                if (existing is DownloadState.Queued || existing is DownloadState.Preparing ||
                    existing is DownloadState.Downloading || existing is DownloadState.Verifying
                ) return@forEach
                next[descriptor.id] = when (descriptor.validation) {
                    LocalModelValidation.VERIFIED_ARTIFACT -> DownloadState.Installed
                    LocalModelValidation.CORRUPT, LocalModelValidation.INCOMPATIBLE -> DownloadState.Failed(
                        ModelDownloadFailure.HASH_MISMATCH,
                        CORRUPT_USER_MESSAGE,
                        descriptor.validationDetail,
                        resumable = partialDownloadBytes(descriptor.id) != null,
                    )
                    LocalModelValidation.UNVERIFIED -> DownloadState.NotInstalled
                }
            }
            // Drop bookkeeping for models that are no longer on disk; keep failures so the user sees them.
            next.keys.toList().forEach { id ->
                if (descriptors.none { it.id == id } && next[id] == DownloadState.Installed) next.remove(id)
            }
            // A stored partial outlives the process: surface it as resumable instead of NotInstalled.
            partials.forEach { (id, bytes) ->
                val existing = next[id]
                if (existing is DownloadState.Queued ||
                    existing is DownloadState.Preparing ||
                    existing is DownloadState.Downloading ||
                    existing is DownloadState.Verifying ||
                    existing == DownloadState.Installed
                ) {
                    return@forEach
                }
                if (descriptors.any { it.id == id && it.valid }) return@forEach
                next[id] = DownloadState.Paused(bytes, mutableCatalog.value.firstOrNull { it.id == id }?.expectedBytes)
            }
            next
        }
    }

    private fun storedPartials(): Map<String, Long> =
        modelRoot
            .listFiles { f -> f.isFile && f.name.endsWith(".litertlm.part", ignoreCase = true) }
            ?.filter { it.length() > 0L }
            ?.associate { it.name.removeSuffix(".litertlm.part") to it.length() }
            .orEmpty()

    private fun setDownloadState(modelId: String, state: DownloadState) {
        _downloadStates.update { it + (modelId to state) }
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private data class PhysicalFingerprint(val bytes: Long, val sha256: String)

    private fun fingerprintKey(file: File): String = "${file.absolutePath}|${file.length()}|${file.lastModified()}"

    private fun physicalFingerprint(file: File): PhysicalFingerprint {
        val key = fingerprintKey(file)
        val cached = physicalFingerprints[key]
        if (cached != null) return PhysicalFingerprint(file.length(), cached)

        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                bytes += read
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (bytes > 0L) {
            physicalFingerprints[key] = sha
        }
        return PhysicalFingerprint(bytes, sha)
    }

    /**
     * Same-directory move. `ATOMIC_MOVE` is not guaranteed on every Android filesystem, so a
     * plain replace-copy move is used as a fallback. Source and target always share [modelRoot].
     */
    private fun moveIntoPlace(source: File, target: File) {
        val sourcePath = source.toPath()
        val targetPath = target.toPath()
        try {
            Files.move(
                sourcePath,
                targetPath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (atomicUnsupported: AtomicMoveNotSupportedException) {
            Log.w(TAG, "HALO_LITERT_MOVE_ATOMIC_UNSUPPORTED from=${source.name} to=${target.name}")
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (unsupported: UnsupportedOperationException) {
            Log.w(TAG, "HALO_LITERT_MOVE_ATOMIC_UNSUPPORTED from=${source.name} to=${target.name}")
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun fingerprintModelId(cleanName: String, sha256: String): String {
        val slug = cleanName.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_')
        val prefix = slug.ifBlank { "imported_model" }
        return "${prefix}_${sha256.take(8)}"
    }

    companion object {
        private val transferMutexes = ConcurrentHashMap<String, Mutex>()
        private const val TAG = "LiteRtModelManager"
        private const val PROGRESS_LOG_INTERVAL_BYTES = 8L * 1024L * 1024L
        private const val PROGRESS_METADATA_INTERVAL_BYTES = 32L * 1024L * 1024L
        private const val DEFAULT_CONTEXT_FOR_DISPLAY = 4096

        const val EVENT_INVENTORY = "HALO_LITERT_INVENTORY"
        const val EVENT_REQUESTED = "HALO_LITERT_DOWNLOAD_REQUESTED"
        const val EVENT_PREPARING = "HALO_LITERT_DOWNLOAD_PREPARING"
        const val EVENT_START = "HALO_LITERT_DOWNLOAD_START"
        const val EVENT_PROGRESS = OkHttpLiteRtModelDownloader.EVENT_PROGRESS
        const val EVENT_VERIFY = "HALO_LITERT_VERIFY"
        const val EVENT_INSTALLED = "HALO_LITERT_DOWNLOAD_INSTALLED"
        const val EVENT_FAILED = "HALO_LITERT_DOWNLOAD_FAILED"
        const val EVENT_RESUME = "HALO_LITERT_RESUME"
        const val EVENT_STORAGE = "HALO_LITERT_STORAGE"

        const val CORRUPT_USER_MESSAGE = "Model file is damaged or stale — re-download it."

        internal fun progressOf(downloadedBytes: Long, totalBytes: Long?): Float? =
            totalBytes?.takeIf { it > 0L }?.let { (downloadedBytes.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
    }
}
