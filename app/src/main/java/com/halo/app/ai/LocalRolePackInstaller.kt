package com.halo.app.ai

import com.halo.app.audio.SupertonicPackStore
import com.halo.app.audio.SupertonicSpeechProvider
import com.halo.providers.litert.models.DownloadState
import com.halo.providers.litert.models.LiteRtModelManager
import com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog
import com.halo.providers.sherpa.stt.SpeechModelInventoryRepository
import com.halo.sdk.runtime.Storage
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class LocalRolePackPhase { IDLE, PREFLIGHT, INSTALLING, PAUSED, FAILED, ROLLING_BACK, ROLLBACK_FAILED, COMPLETE }
interface LocalRolePackControl {
    val state: StateFlow<LocalRolePackInstallState>
    val available: Boolean
    /** Inventory estimate only. Never downloads, selects roles or grants qualification. */
    suspend fun downloadPreview(): LocalRolePackDownloadPreview? = null
    fun request(command: LocalRolePackCommand, expectedTransactionId: String): Boolean
}

enum class LocalRolePackCommand { START_OR_RESUME, PAUSE, ROLLBACK, KEEP_FILES }

enum class LocalRolePackStage { INTENT, SPEECH_RECOGNITION, SPEECH_ACTIVITY, SPEECH_OUTPUT, DONE }

data class LocalRolePackDownloadEstimate(val stage: LocalRolePackStage, val remainingBytes: Long)
data class LocalRolePackStorageEstimate(val requiredBytes: Long, val availableBytes: Long)
data class LocalRolePackDownloadPreview(
    val downloads: List<LocalRolePackDownloadEstimate>,
    val storage: List<LocalRolePackStorageEstimate>,
) {
    val downloadBytes: Long get() = downloads.fold(0L) { total, item -> Math.addExact(total, item.remainingBytes) }
    val requiredStorageBytes: Long get() = storage.fold(0L) { total, item -> Math.addExact(total, item.requiredBytes) }
    val hasEnoughStorage: Boolean get() = storage.all { it.availableBytes >= it.requiredBytes }
}

data class LocalRolePackInstallState(
    val phase: LocalRolePackPhase = LocalRolePackPhase.IDLE,
    val stage: LocalRolePackStage = LocalRolePackStage.INTENT,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String? = null,
    val transactionId: String? = null,
    val hasSetupRecord: Boolean = false,
    val recoveryRequired: Boolean = false,
) {
    val busy: Boolean get() = phase == LocalRolePackPhase.PREFLIGHT || phase == LocalRolePackPhase.INSTALLING || phase == LocalRolePackPhase.ROLLING_BACK
    val canResume: Boolean get() = !recoveryRequired && (phase == LocalRolePackPhase.PAUSED || phase == LocalRolePackPhase.FAILED)
    val canRollback: Boolean get() = !recoveryRequired && phase in setOf(LocalRolePackPhase.PAUSED, LocalRolePackPhase.FAILED, LocalRolePackPhase.ROLLBACK_FAILED)
    val canKeepFiles: Boolean get() = hasSetupRecord && !busy
}

internal data class LocalRolePackJournal(
    val phase: LocalRolePackPhase,
    val stage: LocalRolePackStage,
    val intentExisted: Boolean,
    val intentVerified: Boolean,
    val planFingerprint: String,
    val whisperSmallInstalled: Boolean,
    val vadInstalled: Boolean,
    val ttsFingerprints: Set<String>,
    val message: String? = null,
    /** Null only for legacy journals, whose artifact ownership cannot be inferred. */
    val transactionId: String? = null,
) {
    fun encode(): String = listOf(
        phase.name, stage.name, intentExisted.toString(), intentVerified.toString(), planFingerprint,
        whisperSmallInstalled.toString(), vadInstalled.toString(), ttsFingerprints.sorted().joinToString(","),
        message.orEmpty(), transactionId.orEmpty()
    ).joinToString("\t")

    companion object {
        fun decode(value: String?): LocalRolePackJournal? = runCatching {
            if (value == null || value.length > 16000) return null
            val fields = value.split('\t')
            require(fields.size in 9..10)
            LocalRolePackJournal(
                phase = LocalRolePackPhase.valueOf(fields[0]),
                stage = LocalRolePackStage.valueOf(fields[1]),
                intentExisted = fields[2].toBooleanStrict(),
                intentVerified = fields[3].toBooleanStrict(),
                planFingerprint = fields[4].also { require(it.matches(Regex("[a-f0-9]{64}"))) },
                whisperSmallInstalled = fields[5].toBooleanStrict(),
                vadInstalled = fields[6].toBooleanStrict(),
                ttsFingerprints = fields[7].split(',').filter(String::isNotBlank).toSet().also { hashes -> require(hashes.size <= 64 && hashes.all { it.matches(Regex("[a-f0-9]{64}")) }) },
                message = fields[8].ifBlank { null },
                transactionId = fields.getOrNull(9)?.ifBlank { null }?.also { require(java.util.UUID.fromString(it).toString() == it) },
            )
        }.getOrNull()
    }
}

/**
 * Acquires a pinned candidate bundle through the existing model owners. The journal records
 * only model identities and pre-existing installation state; no user content or credentials.
 * Acquiring candidates never selects them or marks them qualified.
 */
class LocalRolePackInstaller(
    private val storage: Storage,
    private val models: LiteRtModelManager,
    private val speech: SpeechModelInventoryRepository,
    private val speechRoot: File,
    private val supertonic: SupertonicSpeechProvider?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : LocalRolePackControl {
    private val transaction = Mutex()
    private val installJobLock = Any()
    private var installJob: Job? = null
    private val intentId = "qwen3-0.6b-dynamic-int4"
    // Acquisition uses the same exact source pins as discovery; a matching ID
    // alone cannot substitute a different imported catalogue row into this bundle.
    private val intentCatalog get() = models.catalogModel(intentId)?.takeIf {
        CuratedLocalCatalog.entry(it)?.role == CuratedLocalRole.INTENT_CANDIDATE
    }
    @Volatile private var recordFingerprint: String? = null
    @Volatile private var journal = recoverJournal()
    private val mutableState = MutableStateFlow(journal?.toState() ?: if (recordFingerprint != null)
        LocalRolePackInstallState(phase = LocalRolePackPhase.ROLLBACK_FAILED, message = RECOVERY_MESSAGE,
            hasSetupRecord = true, recoveryRequired = true) else LocalRolePackInstallState())
    override val state: StateFlow<LocalRolePackInstallState> = mutableState.asStateFlow()
    override val available: Boolean get() = supertonic != null && intentCatalog != null

    override suspend fun downloadPreview(): LocalRolePackDownloadPreview? = withContext(Dispatchers.IO) {
        if (!available || mutableState.value.busy) return@withContext null
        transaction.withLock {
            if (!available || mutableState.value.busy) null else downloadPlan(prepareStorage = false)
        }
    }

    init {
        // WorkManager may continue a LiteRT transfer across process death. Resume reconciles
        // its physical inventory before advancing, so this state is only an invitation.
        if (journal?.phase == LocalRolePackPhase.PREFLIGHT || journal?.phase == LocalRolePackPhase.INSTALLING ||
            journal?.phase == LocalRolePackPhase.ROLLING_BACK) {
            val interruptedRollback = journal?.phase == LocalRolePackPhase.ROLLING_BACK
            val recovered = requireNotNull(journal).copy(
                phase = if (interruptedRollback) LocalRolePackPhase.ROLLBACK_FAILED else LocalRolePackPhase.PAUSED,
                message = if (interruptedRollback) "Rollback was interrupted. Retry rollback to finish removing newly downloaded models."
                else "Setup paused. Resume or roll back the downloaded models."
            )
            persistOrRecover(recovered)
        }
    }

    /** Typed adapters use an observed receipt identity; the UI invokes these same owner methods. */
    override fun request(command: LocalRolePackCommand, expectedTransactionId: String): Boolean {
        val observed = mutableState.value
        if (expectedTransactionId != (observed.transactionId ?: "NONE")) return false
        when (command) {
            LocalRolePackCommand.START_OR_RESUME -> { if (observed.busy || observed.recoveryRequired || observed.phase == LocalRolePackPhase.ROLLBACK_FAILED) return false; startOrResumeFor(expectedTransactionId) }
            LocalRolePackCommand.PAUSE -> { if (!observed.busy || observed.phase == LocalRolePackPhase.ROLLING_BACK) return false; pauseFor(expectedTransactionId) }
            LocalRolePackCommand.ROLLBACK -> { if (!observed.canRollback) return false; rollbackFor(expectedTransactionId) }
            LocalRolePackCommand.KEEP_FILES -> { if (!observed.canKeepFiles || !recordIsCurrent()) return false; keepDownloadedFilesFor(expectedTransactionId) }
        }
        return true // Request accepted only; poll status for actual owner progress.
    }
    fun startOrResume() = startOrResumeFor(null)
    private fun startOrResumeFor(expected: String?) {
        synchronized(installJobLock) {
            if (installJob?.isActive == true || mutableState.value.recoveryRequired) return
            installJob = scope.launch { transaction.withLock {
                if (expected != null && expected != (journal?.transactionId ?: "NONE")) return@withLock
                if (mutableState.value.recoveryRequired || mutableState.value.phase == LocalRolePackPhase.ROLLBACK_FAILED) return@withLock
                installTransaction()
            } }
        }
    }

    fun pause() = pauseFor(null)
    private fun pauseFor(expected: String?) {
        synchronized(installJobLock) {
            if (!mutableState.value.busy || mutableState.value.phase == LocalRolePackPhase.ROLLING_BACK ||
                expected != null && expected != (journal?.transactionId ?: "NONE")) return
            val stage = mutableState.value.stage
            installJob?.cancel()
            if (stage == LocalRolePackStage.INTENT) journal?.transactionId?.let { models.cancelOwnedDownload(intentId, it) }
            // Structured cancellation stops this setup's speech-output child, never an unrelated acquisition.
        }
    }

    fun rollback() = rollbackFor(null)
    private fun rollbackFor(expected: String?) {
        if (!mutableState.value.canRollback) return
        val running = synchronized(installJobLock) { installJob }
        running?.cancel()
        scope.launch {
            running?.cancelAndJoin()
            transaction.withLock {
                if (expected != null && expected != (journal?.transactionId ?: "NONE")) return@withLock
                if (mutableState.value.canRollback) rollbackTransaction()
            }
        }
    }

    /** Explicit receipt recovery only. Keeps every file, selection and role; never retries an acquisition. */
    fun keepDownloadedFiles() = keepDownloadedFilesFor(null)
    private fun keepDownloadedFilesFor(expected: String?) {
        val observedFingerprint = synchronized(installJobLock) {
            if (!mutableState.value.canKeepFiles) return
            recordFingerprint ?: return
        }
        scope.launch { transaction.withLock {
            if (!mutableState.value.canKeepFiles || expected != null && expected != (journal?.transactionId ?: "NONE")) return@withLock
            // A resumed transaction can publish a new receipt under the same ownership token.
            // This older request must neither clear it nor overwrite its current presentation.
            if (recordFingerprint != observedFingerprint) return@withLock
            try {
                check(recordIsCurrent(observedFingerprint))
                storage.remove(JOURNAL_KEY)
                recordFingerprint = null
                journal = null
                publish(LocalRolePackInstallState())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { publishKeepOnly(CLEAR_FAILED_MESSAGE) }
        } }
    }

    private suspend fun installTransaction() {
        var operationJournal: LocalRolePackJournal? = null
        try {
            val active = if (journal == null || journal?.phase == LocalRolePackPhase.COMPLETE || journal?.phase == LocalRolePackPhase.IDLE) {
                val initial = snapshotBaseline()
                persist(initial.copy(phase = LocalRolePackPhase.PREFLIGHT))
                requireNotNull(journal)
            } else requireNotNull(journal)
            operationJournal = active
            if (active.planFingerprint != currentPlanFingerprint()) {
                persist(active.copy(phase = LocalRolePackPhase.ROLLBACK_FAILED,
                    message = "The pinned setup changed. Keep existing files and clear this receipt before starting again."))
                return
            }
            check(active.transactionId != null) { "Keep existing downloads and start a new setup to establish ownership." }
            check(supertonic != null) { "The Supertonic speech provider is unavailable on this installation." }
            publish(active.copy(phase = LocalRolePackPhase.PREFLIGHT, message = null))
            preflight(active)
            var current = active.copy(phase = LocalRolePackPhase.INSTALLING, message = null)
            persist(current)

            val intent = intentCatalog ?: error("The pinned intent candidate is not in the local catalog.")
            require(intent.directDownloadAllowed) { "The intent candidate cannot be acquired by the built-in downloader." }
            current = current.copy(stage = LocalRolePackStage.INTENT)
            persist(current)
            installIntentCandidate(intent)

            current = current.copy(stage = LocalRolePackStage.SPEECH_RECOGNITION)
            persist(current)
            speech.refresh()
            if (speech.state.value.models[WhisperSmall.id] != true) {
                val result = speech.installOwned(WhisperSmall.id, requireNotNull(current.transactionId)) { progress ->
                    mutableState.value = mutableState.value.copy(
                        phase = LocalRolePackPhase.INSTALLING,
                        stage = LocalRolePackStage.SPEECH_RECOGNITION,
                        downloadedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes,
                        message = null,
                    )
                }
                result.getOrElse { throw it }
            }
            speech.refresh()
            check(speech.state.value.models[WhisperSmall.id] == true) { "Whisper Small did not pass its pinned file checks." }

            current = current.copy(stage = LocalRolePackStage.SPEECH_ACTIVITY)
            persist(current)
            speech.refresh()
            if (!speech.state.value.sileroInstalled) {
                val result = speech.installOwned(SherpaSpeechModelCatalog.SILERO_VAD_ID, requireNotNull(current.transactionId)) { progress ->
                    mutableState.value = mutableState.value.copy(
                        phase = LocalRolePackPhase.INSTALLING,
                        stage = LocalRolePackStage.SPEECH_ACTIVITY,
                        downloadedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes,
                        message = null,
                    )
                }
                result.getOrElse { throw it }
            }
            speech.refresh()
            check(speech.state.value.sileroInstalled) { "Silero VAD did not pass its pinned file checks." }

            current = current.copy(stage = LocalRolePackStage.SPEECH_OUTPUT)
            persist(current)
            val tts = supertonic ?: error("The Supertonic speech provider is unavailable on this installation.")
            val installedTts = tts.installedPacks().mapTo(mutableSetOf()) { it.fingerprint }
            val newlyInstalled = installedTts - current.ttsFingerprints
            val retainedBaselinePack = current.ttsFingerprints.any(installedTts::contains)
            if (!retainedBaselinePack && newlyInstalled.isEmpty()) {
                val fingerprint = coroutineScope {
                    val download = async { tts.packs.downloadOfficial(requireNotNull(current.transactionId) { "Keep existing downloads and start a new setup to establish ownership." }) }
                    while (!download.isCompleted) {
                        mutableState.value = mutableState.value.copy(
                            downloadedBytes = tts.packs.downloadProgress.value ?: 0L,
                            totalBytes = SupertonicPackStore.OFFICIAL_BYTES,
                        )
                        delay(500)
                    }
                    download.await()
                }
                check(tts.packs.resolve(fingerprint) != null) { "The Supertonic pack did not pass its manifest checks." }
            }
            check(retainedBaselinePack || newlyInstalled.isNotEmpty() || tts.installedPacks().isNotEmpty()) {
                "No verified local speech output pack is installed."
            }
            finish()
        } catch (cancelled: CancellationException) {
            val active = journal
            if (operationJournal != null && active != null && active.transactionId == operationJournal.transactionId &&
                active.phase !in setOf(LocalRolePackPhase.COMPLETE, LocalRolePackPhase.ROLLING_BACK)) {
                persistOrRecover(active.copy(phase = LocalRolePackPhase.PAUSED, message = "Setup paused. Resume to continue or roll back the downloaded models."))
            }
            throw cancelled
        } catch (error: Exception) {
            val active = journal
            if (operationJournal != null && active != null && active.transactionId == operationJournal.transactionId)
                persistOrRecover(active.copy(phase = LocalRolePackPhase.FAILED, message = safeMessage(error)))
            else if (active != null) publishKeepOnly()
            else publish(LocalRolePackInstallState(phase = LocalRolePackPhase.FAILED, message = safeMessage(error)))
        }
    }

    private suspend fun snapshotBaseline(): LocalRolePackJournal {
        speech.refresh()
        val existing = models.verifyPhysicalArtifact(intentId)
        val physicalFileExists = File(models.modelDir, "$intentId.litertlm").exists()
        if (physicalFileExists && existing == null) error("An existing intent artifact could not be verified; it was left untouched.")
        if (models.isDownloading(intentId)) error("An intent model download is already running. Let it finish before starting this bundle.")
        val ttsFingerprints = supertonic?.installedPacks()?.mapTo(mutableSetOf()) { it.fingerprint }.orEmpty()
        if (existing == null && models.partialDownloadBytes(intentId) != null) {
            error("An interrupted intent download already exists. Resume or discard it in model settings before starting this bundle.")
        }
        val whisperDirectory = File(speechRoot, WhisperSmall.directoryName)
        if (speech.state.value.models[WhisperSmall.id] != true && whisperDirectory.listFiles().orEmpty().isNotEmpty()) {
            error("Existing Whisper Small files were found. Finish or remove that download in voice settings before starting this bundle.")
        }
        val vadFile = File(speechRoot, SherpaSpeechModelCatalog.SILERO_VAD_FILE)
        if (!speech.state.value.sileroInstalled && (vadFile.exists() ||
                File(speechRoot, "${SherpaSpeechModelCatalog.SILERO_VAD_FILE}.verified").exists() ||
                File(speechRoot, "${SherpaSpeechModelCatalog.SILERO_VAD_FILE}.partial").exists())) {
            error("An existing VAD file could not be verified; it was left untouched.")
        }
        if (ttsFingerprints.isEmpty() && (supertonic?.packs?.hasOfficialPartial() == true || supertonic?.packs?.downloadProgress?.value != null)) {
            error("An interrupted Supertonic download already exists. Resume or discard it in voice settings before starting this bundle.")
        }
        return LocalRolePackJournal(
            phase = LocalRolePackPhase.PREFLIGHT,
            stage = LocalRolePackStage.INTENT,
            intentExisted = existing != null,
            intentVerified = existing?.verified == true && existing.sha256.equals(intentCatalog?.sha256, ignoreCase = true),
            planFingerprint = currentPlanFingerprint(),
            transactionId = java.util.UUID.randomUUID().toString(),
            whisperSmallInstalled = speech.state.value.models[WhisperSmall.id] == true,
            vadInstalled = speech.state.value.sileroInstalled,
            ttsFingerprints = ttsFingerprints,
        ).also { initial ->
            if (initial.intentExisted && !initial.intentVerified) {
                error("The existing intent candidate does not match the current pinned artifact; it was left untouched.")
            }
        }
    }

    private suspend fun preflight(baseline: LocalRolePackJournal) {
        check(baseline.planFingerprint == currentPlanFingerprint()) { "The pinned setup changed before acquisition." }
        val preview = downloadPlan(prepareStorage = true)
        check(preview.hasEnoughStorage) { "Not enough free space for starter pack files. Review available storage before downloading." }
    }

    /** Same estimate for user review and execution. Only execution may create storage directories. */
    private suspend fun downloadPlan(prepareStorage: Boolean): LocalRolePackDownloadPreview {
        val intentNow = models.verifyPhysicalArtifact(intentId)?.let { it.verified && it.sha256.equals(intentCatalog?.sha256, true) } == true
        speech.refresh()
        val speechInventory = speech.state.value
        val intentModel = requireNotNull(intentCatalog)
        val intentPartial = (models.partialDownloadBytes(intentId) ?: 0L).takeIf { it in 0L..intentModel.expectedBytes } ?: 0L
        val intentRemaining = (intentModel.expectedBytes - intentPartial).coerceAtLeast(0L)
        val intentBytes = if (intentNow) 0L else intentRemaining
        val whisperRemaining = com.halo.providers.sherpa.stt.SpeechModelInstaller.remainingBytes(WhisperSmall, File(speechRoot, WhisperSmall.directoryName))
        val speechBytes = if (speechInventory.models[WhisperSmall.id] == true) 0L else whisperRemaining
        val vadRemaining = com.halo.providers.sherpa.stt.SpeechModelInstaller.remainingBytes(SherpaSpeechModelCatalog.sileroVad, speechRoot)
        val vadBytes = if (speechInventory.sileroInstalled) 0L else vadRemaining
        val ttsNowInstalled = supertonic?.installedPacks()?.isNotEmpty() == true
        val ttsDownloadBytes = if (ttsNowInstalled) 0L else
            supertonic?.packs?.remainingOfficialDownloadBytes() ?: SupertonicPackStore.OFFICIAL_BYTES
        val ttsBytes = if (ttsNowInstalled) 0L else
            Math.addExact(ttsDownloadBytes, SupertonicPackStore.INSTALL_RESERVE_BYTES + 1L)
        // Match the model owner's resume preflight, including reserve when a full
        // staging file still needs verification/finalization but no network bytes.
        val intentStorageBytes = if (intentNow) 0L else
            com.halo.providers.litert.models.LiteRtStoragePreflight.evaluate(
                intentBytes.coerceAtLeast(1L), Long.MAX_VALUE).requiredBytes
        val speechReserve = if (speechInventory.models[WhisperSmall.id] != true || !speechInventory.sileroInstalled) {
            com.halo.providers.sherpa.stt.SpeechModelInstaller.STORAGE_RESERVE_BYTES
        } else 0L
        val storageNeeds = listOfNotNull(
            models.modelDir.takeIf { intentStorageBytes > 0L }?.let { it to intentStorageBytes },
            speechRoot.takeIf { speechBytes + vadBytes + speechReserve > 0L }?.let { it to (speechBytes + vadBytes + speechReserve) },
            supertonic?.packs?.storageRoot?.takeIf { ttsBytes > 0L }?.let { it to ttsBytes },
        ).map { (root, required) ->
            val location = if (prepareStorage) {
                check(root.isDirectory || root.mkdirs() || root.isDirectory) { "Unable to prepare model storage." }
                root
            } else generateSequence(root) { it.parentFile }.firstOrNull { it.isDirectory }
                ?: error("Model storage is unavailable.")
            val store = java.nio.file.Files.getFileStore(location.toPath())
            Triple(location, required, Triple(store.name(), store.type(), store.totalSpace))
        }.groupBy { (_, _, storeIdentity) -> storeIdentity }
        val storage = storageNeeds.values.map { entries ->
            val required = entries.fold(0L) { total, entry -> Math.addExact(total, entry.second) }
            LocalRolePackStorageEstimate(required, entries.minOf { it.first.usableSpace })
        }
        return LocalRolePackDownloadPreview(listOf(
            LocalRolePackDownloadEstimate(LocalRolePackStage.INTENT, intentBytes),
            LocalRolePackDownloadEstimate(LocalRolePackStage.SPEECH_RECOGNITION, speechBytes),
            LocalRolePackDownloadEstimate(LocalRolePackStage.SPEECH_ACTIVITY, vadBytes),
            LocalRolePackDownloadEstimate(LocalRolePackStage.SPEECH_OUTPUT, ttsDownloadBytes),
        ), storage)
    }

    private suspend fun installIntentCandidate(model: com.halo.providers.litert.models.LiteRtCatalogModel) {
        val existing = models.verifyPhysicalArtifact(intentId)
        if (existing?.verified == true && existing.sha256.equals(model.sha256, ignoreCase = true)) return
        val owner = requireNotNull(journal?.transactionId) { "Keep existing downloads and start a new setup." }
        check(!File(models.modelDir, "$intentId.litertlm").exists() || models.acquisitionOwner(intentId) == owner) { "An independent intent artifact needs repair; it was retained." }
        models.downloadCatalogModel(model, acquisitionOwner = owner)
        while (true) {
            val download = models.downloadStates.value[intentId]
            val (bytes, total) = when (download) {
                is DownloadState.Downloading -> download.downloadedBytes to (download.totalBytes ?: model.expectedBytes)
                is DownloadState.Verifying -> download.downloadedBytes to (download.totalBytes ?: model.expectedBytes)
                else -> 0L to model.expectedBytes
            }
            mutableState.value = mutableState.value.copy(downloadedBytes = bytes, totalBytes = total)
            when (download) {
                DownloadState.Installed -> {
                    val artifact = models.verifyPhysicalArtifact(intentId)
                    check(artifact?.verified == true && artifact.sha256.equals(model.sha256, ignoreCase = true)) {
                        "The intent candidate failed exact checksum verification."
                    }
                    return
                }
                is DownloadState.Failed -> error(download.userMessage)
                else -> delay(500)
            }
        }
    }

    private suspend fun finish() {
        check(journal?.planFingerprint == currentPlanFingerprint()) { "The pinned setup changed during acquisition." }
        val intent = requireNotNull(intentCatalog)
        val verifiedIntent = models.verifyPhysicalArtifact(intentId)
        check(verifiedIntent?.verified == true && verifiedIntent.sha256.equals(intent.sha256, ignoreCase = true)) {
            "The intent candidate failed final checksum verification."
        }
        speech.refresh()
        check(speech.state.value.models[WhisperSmall.id] == true) { "Whisper Small did not pass final file checks." }
        check(speech.state.value.sileroInstalled) { "Silero VAD did not pass final file checks." }
        check(supertonic?.installedPacks()?.isNotEmpty() == true) { "No verified local speech output pack is installed." }
        val complete = requireNotNull(journal).copy(phase = LocalRolePackPhase.COMPLETE, stage = LocalRolePackStage.DONE, message = null)
        persist(complete)
    }

    private suspend fun rollbackTransaction() {
        val baseline = journal ?: return publish(LocalRolePackInstallState())
        if (baseline.transactionId == null || baseline.planFingerprint != currentPlanFingerprint()) {
            persistOrRecover(baseline.copy(phase = LocalRolePackPhase.ROLLBACK_FAILED,
                message = "Setup ownership cannot be verified after this update. Keep the files and clear this receipt, or manage downloads individually."))
            return
        }
        if (!persistOrRecover(baseline.copy(phase = LocalRolePackPhase.ROLLING_BACK, message = null))) return
        val failures = mutableListOf<String>()
        suspend fun attempt(label: String, action: suspend () -> Unit) {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failures += label }
        }

        if (baseline.stage.ordinal >= LocalRolePackStage.INTENT.ordinal && !baseline.intentExisted) attempt("intent") {
            val owner = requireNotNull(baseline.transactionId) { "Legacy ownership unknown; keep downloads and clear this receipt." }
            models.cancelOwnedDownload(intentId, owner)
            if (models.acquisitionOwner(intentId) == owner) check(models.deleteModel(intentId, owner)) { "Intent artifact is in use, changed or could not be removed." }
            models.discardOwnedPartial(intentId, owner)
        }
        if (baseline.stage.ordinal >= LocalRolePackStage.SPEECH_RECOGNITION.ordinal && !baseline.whisperSmallInstalled) attempt("recognition") {
            speech.removeOwned(WhisperSmall.id, requireNotNull(baseline.transactionId)).getOrElse { throw it }
        }
        if (baseline.stage.ordinal >= LocalRolePackStage.SPEECH_ACTIVITY.ordinal && !baseline.vadInstalled) attempt("VAD") {
            speech.removeOwned(SherpaSpeechModelCatalog.SILERO_VAD_ID, requireNotNull(baseline.transactionId)).getOrElse { throw it }
        }
        if (baseline.stage.ordinal >= LocalRolePackStage.SPEECH_OUTPUT.ordinal) {
            attempt("speech output") {
                val tts = requireNotNull(supertonic)
                // The setup job has already been cancelled and joined; do not cancel another downloader.
                val owner = requireNotNull(baseline.transactionId) { "Legacy ownership is unknown; keep downloads and clear this setup receipt." }
                tts.packs.ownedBy(owner).forEach { fingerprint -> check(tts.removeOwnedPack(fingerprint, owner)) { "An acquired pack changed or is incomplete; it was retained." } }
                tts.packs.discardOwnedOfficialPartial(owner)
                // Independent shared archives are retained; only the exact setup receipt can authorize cleanup.

            }
        }
        if (failures.isEmpty()) {
            try {
                storage.remove(JOURNAL_KEY)
                recordFingerprint = null
                journal = null
                publish(LocalRolePackInstallState(phase = LocalRolePackPhase.IDLE, message = "Newly downloaded models were rolled back."))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { publishKeepOnly(CLEAR_FAILED_MESSAGE) }
        } else {
            persistOrRecover(baseline.copy(phase = LocalRolePackPhase.ROLLBACK_FAILED, message = "Some newly downloaded models are still in use. Close their current task and retry rollback."))
        }
    }

    private fun recoverJournal(): LocalRolePackJournal? {
        val encoded = storage.getString(JOURNAL_KEY)
        recordFingerprint = encoded?.let(::recordHash)
        val restored = LocalRolePackJournal.decode(encoded) ?: return null
        return if (restored.transactionId != null && restored.planFingerprint == currentPlanFingerprint()) restored else restored.copy(
            phase = LocalRolePackPhase.ROLLBACK_FAILED,
            message = RECOVERY_MESSAGE
        )
    }

    private fun recordIsCurrent(expected: String? = recordFingerprint): Boolean = runCatching {
        expected != null && storage.getString(JOURNAL_KEY)?.let(::recordHash) == expected
    }.getOrDefault(false)

    /** Bounded temporary encoding even for an oversized, unreadable persisted record. */
    private fun recordHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var offset = 0
        while (offset < value.length) {
            val count = minOf(4096, value.length - offset)
            for (index in 0 until count) {
                val code = value[offset + index].code
                buffer[index * 2] = (code ushr 8).toByte()
                buffer[index * 2 + 1] = code.toByte()
            }
            digest.update(buffer, 0, count * 2)
            offset += count
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun currentPlanFingerprint(): String {
        val intent = intentCatalog
        val identity = buildString {
            append("intent=").append(intent?.id).append(':').append(intent?.revision).append(':')
                .append(intent?.filename).append(':').append(intent?.expectedBytes).append(':').append(intent?.sha256)
                .append(':').append(intent?.minLiteRtLmVersion).append(':').append(intent?.runtimeProfile)
            append("|whisper=").append(WhisperSmall.id).append(':').append(WhisperSmall.revision).append(':')
                .append(WhisperSmall.expectedBytes).append(':').append(WhisperSmall.files.joinToString { "${it.name}:${it.expectedBytes}:${it.sha256}" })
                .append(':').append(WhisperSmall.sourceUrl).append(':').append(WhisperSmall.supportedLanguageTags.orEmpty().sorted())
            append("|vad=").append(SherpaSpeechModelCatalog.sileroVad.sourceUrl).append(':')
                .append(SherpaSpeechModelCatalog.sileroVad.files.joinToString { "${it.name}:${it.expectedBytes}:${it.sha256}" })
            append("|tts=").append(SupertonicPackStore.OFFICIAL_URL).append(':')
                .append(SupertonicPackStore.OFFICIAL_SHA256).append(':').append(SupertonicPackStore.OFFICIAL_BYTES)
                .append(':').append(SupertonicPackStore.RUNTIME)
        }
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun persist(value: LocalRolePackJournal) = synchronized(installJobLock) {
        val encoded = value.encode()
        storage.putString(JOURNAL_KEY, encoded)
        recordFingerprint = recordHash(encoded)
        journal = value
        publish(value)
    }

    private fun publishKeepOnly(message: String = RECOVERY_MESSAGE) {
        val retained = journal?.toState() ?: mutableState.value
        publish(retained.copy(phase = LocalRolePackPhase.ROLLBACK_FAILED, message = message,
            hasSetupRecord = recordFingerprint != null, recoveryRequired = true))
    }

    /** A second failed write must not strand the previous busy state or lose its durable receipt. */
    private fun persistOrRecover(value: LocalRolePackJournal): Boolean {
        try { persist(value); return true }
        catch (cancelled: CancellationException) { publishKeepOnly(); throw cancelled }
        catch (_: Exception) { publishKeepOnly(); return false }
    }

    private fun publish(value: LocalRolePackJournal) { mutableState.value = value.toState() }
    private fun publish(value: LocalRolePackInstallState) { mutableState.value = value }

    private fun LocalRolePackJournal.toState(): LocalRolePackInstallState {
        val recovery = transactionId == null || planFingerprint != currentPlanFingerprint()
        return LocalRolePackInstallState(phase = if (recovery) LocalRolePackPhase.ROLLBACK_FAILED else phase,
            stage = stage, message = if (recovery) RECOVERY_MESSAGE else message,
            transactionId = transactionId, hasSetupRecord = true, recoveryRequired = recovery)
    }

    private fun safeMessage(error: Exception): String = when {
        error.message?.contains("Not enough free space", ignoreCase = true) == true -> error.message.orEmpty()
        error.message?.contains("pinned", ignoreCase = true) == true -> "A required model is not available in the pinned catalog. Update Halo and try again."
        else -> "The model setup did not finish. Check storage and connection, then resume or roll back."
    }

    private companion object {
        const val JOURNAL_KEY = "local_role_pack_transaction_v1"
        const val RECOVERY_MESSAGE = "halo can't verify the previous setup record. Keep the downloaded files and clear the record before starting again."
        const val CLEAR_FAILED_MESSAGE = "The setup record could not be cleared. Reopen halo and try again. Your downloaded files are still kept."
        val WhisperSmall = SherpaSpeechModelCatalog.whisperSmall
    }
}
