package com.halo.providers.litert.models

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.halo.providers.litert.LiteRtFailureStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : LocalModelInventoryRepository {
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

    init {
        scope.launch { refresh(ModelInventoryRefreshReason.STARTUP) }
    }

    override fun invalidate(reason: String) {
        scope.launch { refresh(ModelInventoryRefreshReason.INVALIDATED) }
    }

    override suspend fun refresh(reason: ModelInventoryRefreshReason) = inventoryMutex.withLock {
        _inventoryState.value = LocalModelInventoryState.Loading
        runCatching {
            withContext(Dispatchers.IO) { scanModelRoot() }
        }.onSuccess { descriptors ->
            if (_acquisitionDiagnostics.value?.stage == LiteRtFailureStage.INVENTORY) {
                _acquisitionDiagnostics.value = null
            }
            _inventoryState.value = LocalModelInventoryState.Ready(descriptors, System.currentTimeMillis())
            reconcileDownloadStates(descriptors)
            Log.i(
                TAG,
                "$EVENT_INVENTORY reason=$reason models=${descriptors.size} " +
                    "verified=${descriptors.count { it.verified }}",
            )
        }.onFailure { error ->
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
        Unit
    }

    /**
     * Resolves a descriptor without ever reporting MODEL_MISSING because the inventory happened to
     * still be loading at startup. Never called while holding [inventoryMutex].
     */
    override suspend fun resolveModel(modelId: String): LocalModelDescriptor? {
        var state = inventoryState.value
        if (state !is LocalModelInventoryState.Ready) {
            refresh(ModelInventoryRefreshReason.USER_REQUEST)
            state = inventoryState.value
        }
        return (state as? LocalModelInventoryState.Ready)?.models?.firstOrNull { it.id == modelId }
    }

    // ---------------------------------------------------------------------------------------
    // Download
    // ---------------------------------------------------------------------------------------

    fun downloadCatalogModel(model: LiteRtCatalogModel) {
        Log.i(TAG, "$EVENT_REQUESTED id=${model.id}")
        if (!model.directDownloadAllowed) {
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
        synchronized(downloadLock) {
            if (downloadJobs[model.id]?.isActive == true) {
                Log.i(TAG, "$EVENT_FAILED id=${model.id} reason=ALREADY_DOWNLOADING")
                return
            }
            setDownloadState(model.id, DownloadState.Queued)
            downloadJobs[model.id] = scope.launch {
                try {
                    runDownload(model)
                } finally {
                    synchronized(downloadLock) { downloadJobs.remove(model.id) }
                }
            }
        }
    }

    /** Resumes an interrupted transfer, or starts one when no partial exists. */
    fun resumeDownload(model: LiteRtCatalogModel) = downloadCatalogModel(model)

    fun isDownloading(modelId: String): Boolean = downloadJobs[modelId]?.isActive == true

    /** Cancels an in-flight download. Never touches an existing verified artifact. */
    fun cancelDownload(modelId: String) {
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

    private suspend fun runDownload(model: LiteRtCatalogModel) {
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

            // 2. storage preflight: never begin a 1-2 GB transfer without room for it
            val preflight = LiteRtStoragePreflight.check(modelRoot, model.expectedBytes)
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
            val resumeFrom = resolveResumeOffset(model, partFile, partialMetaFile)
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
            val result = downloader.download(model, partFile, { progress ->
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
        if (existing <= 0L || existing >= model.expectedBytes) {
            Log.i(TAG, "HALO_LITERT_PARTIAL_STALE id=${model.id} bytes=$existing expected=${model.expectedBytes}")
            partFile.delete()
            partialMetaFile.delete()
            return 0L
        }
        val metadata = LiteRtPartialDownloadMetadata.fromFile(partialMetaFile)
        if (metadata == null || !metadata.matches(model) || metadata.downloadedBytes != existing) {
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
        runCatching {
            val contentResolver = context.contentResolver
            if (!modelRoot.exists() && !modelRoot.mkdirs()) {
                throw IOException("Unable to create model directory ${modelRoot.absolutePath}")
            }

            var filename = "imported_model"
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    filename = cursor.getString(0) ?: filename
                }
            }
            if (!filename.endsWith(".litertlm", ignoreCase = true)) {
                throw IllegalArgumentException("Imported model must be a .litertlm file")
            }

            val tempFile = File(modelRoot, "import_${System.currentTimeMillis()}.litertlm.part")
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
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
            val catalogMatch = catalog.firstOrNull { it.sha256.equals(sha256, ignoreCase = true) }
            val cleanName = filename.substringBeforeLast(".litertlm")
            val modelId = catalogMatch?.id ?: fingerprintModelId(cleanName, sha256)
            val finalFile = File(modelRoot, "$modelId.litertlm")

            if (finalFile.exists()) {
                tempFile.delete()
                // Refresh first: the duplicate physically exists, so resolve must see it.
                refresh(ModelInventoryRefreshReason.USER_REQUEST)
                return@runCatching resolveModel(modelId)?.takeIf { it.valid }
                    ?: throw IllegalStateException(
                        "A file for '$modelId' already exists but is not usable " +
                            "(${resolveModel(modelId)?.validation})"
                    )
            }

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
        }
    }

    // ---------------------------------------------------------------------------------------
    // Deletion
    // ---------------------------------------------------------------------------------------

    suspend fun deleteModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        if (residentModelIdProvider() == modelId) return@withContext false
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

    fun getAllCatalogModels(): List<LiteRtCatalogModel> = catalog

    fun catalogModel(modelId: String): LiteRtCatalogModel? = catalog.firstOrNull { it.id == modelId }

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
            val catalogBySha = catalog.firstOrNull { it.sha256.equals(physical.sha256, ignoreCase = true) }
            val catalogById = catalog.firstOrNull { it.id == candidate.nameWithoutExtension }
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
        val next = _downloadStates.value.toMutableMap()
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
        storedPartials().forEach { (id, bytes) ->
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
            next[id] = DownloadState.Paused(bytes, catalog.firstOrNull { it.id == id }?.expectedBytes)
        }
        _downloadStates.value = next
    }

    private fun storedPartials(): Map<String, Long> =
        modelRoot
            .listFiles { f -> f.isFile && f.name.endsWith(".litertlm.part", ignoreCase = true) }
            ?.filter { it.length() > 0L }
            ?.associate { it.name.removeSuffix(".litertlm.part") to it.length() }
            .orEmpty()

    private fun setDownloadState(modelId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value + (modelId to state)
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
