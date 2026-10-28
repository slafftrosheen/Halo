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
import kotlinx.coroutines.ensureActive

enum class LocalRolePackPhase { IDLE, PREFLIGHT, INSTALLING, PAUSED, FAILED, ROLLING_BACK, ROLLBACK_FAILED, COMPLETE }
const val LOCAL_ROLE_PACK_REPAIR_REQUIRED = "PACK_ARTIFACT_REPAIR_REQUIRED"
const val LOCAL_ROLE_PACK_REVIEW_REQUIRED = "PACK_SOURCE_REVIEW_REQUIRED"
private class LocalRolePackRepairRequired : IllegalStateException()
interface LocalRolePackControl {
    val state: StateFlow<LocalRolePackInstallState>
    val available: Boolean
    /** Exact source/license disclosure acknowledgement only; never grants qualification. */
    val sourceReviewAcknowledged: Boolean get() = false
    fun acknowledgeSourceReview(manifestFingerprint: String, acknowledged: Boolean): Boolean = false
    /** Inventory estimate only. Never downloads, selects roles or grants qualification. */
    suspend fun downloadPreview(): LocalRolePackDownloadPreview? = null
    /** Explicit read through current physical owner checks; null while installing/unavailable. */
    suspend fun offlineInventory(): OfflinePackInventory? = null
    suspend fun conversationExtensionState(): OfflineConversationExtensionState? = null
    fun requestConversationExtension(command: LocalRolePackCommand, expectedTransactionId: String): Boolean = false
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
    private var conversationJob: Job? = null
    private val conversationMutation = Mutex()
    @Volatile private var conversationMessage: String? = null
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

    override val sourceReviewAcknowledged: Boolean
        get() = runCatching { storage.getString(SOURCE_REVIEW_KEY) == PrivateOfflinePack.fingerprint }.getOrDefault(false)

    override fun acknowledgeSourceReview(manifestFingerprint: String, acknowledged: Boolean): Boolean =
        synchronized(installJobLock) {
            if (manifestFingerprint != PrivateOfflinePack.fingerprint) return@synchronized false
            runCatching {
                if (acknowledged) storage.putString(SOURCE_REVIEW_KEY, manifestFingerprint)
                else storage.remove(SOURCE_REVIEW_KEY)
                val saved = sourceReviewAcknowledged == acknowledged
                if (saved && acknowledged && mutableState.value.message == LOCAL_ROLE_PACK_REVIEW_REQUIRED)
                    publish(mutableState.value.copy(message = null))
                saved
            }.getOrDefault(false)
        }

    private fun conversationReceipt(): Pair<String?, Boolean> {
        val raw = storage.getString(CONVERSATION_KEY) ?: return null to false
        val fields = raw.takeIf { it.length < 256 }?.split(':')
        val id = fields?.getOrNull(1)?.takeIf { value ->
            runCatching { java.util.UUID.fromString(value).toString() == value }.getOrDefault(false)
        }
        return id to (fields == null || fields.size != 2 || fields[0] != PrivateOfflinePack.fingerprint || id == null)
    }

    override suspend fun conversationExtensionState(): OfflineConversationExtensionState = withContext(Dispatchers.IO) {
        val model = PrivateOfflinePack.conversationCandidate
        val (receipt, recovery) = conversationReceipt()
        val busy = synchronized(installJobLock) { conversationJob?.isActive == true } || models.isDownloading(model.id)
        val physical = if (!busy) models.verifyPhysicalArtifact(model.id) else null
        val intact = models.catalogModel(model.id) == model && CuratedLocalCatalog.matchesInstalledArtifact(model, physical?.sha256,
            physical?.bytes ?: 0L, physical?.verified == true)
        val partial = (models.partialDownloadBytes(model.id) ?: 0L).coerceIn(0L, model.expectedBytes)
        val remaining = if (intact) 0L else model.expectedBytes - partial
        val storageEstimate = com.halo.providers.litert.models.LiteRtStoragePreflight.evaluate(
            remaining.coerceAtLeast(1L), models.modelDir.usableSpace)
        OfflineConversationExtensionState(receipt ?: "NONE", models.catalogModel(model.id) == model && model.directDownloadAllowed,
            busy, recovery, when {
                recovery -> "RECOVERY_REQUIRED"
                busy -> "ACQUIRING"
                intact -> "FILES_INSTALLED_UNQUALIFIED"
                conversationMessage != null -> "FAILED"
                partial > 0L -> "PAUSED"
                else -> "IDLE"
            }, intact, remaining, if (intact) 0L else storageEstimate.requiredBytes,
            storageEstimate.freeBytes, intact || storageEstimate.sufficient, conversationMessage)
    }

    /** Optional CHAT acquisition uses this same owner and existing LiteRT transfers.
     * Its durable receipt cannot select a role or authorize inference. Rollback removes
     * only manager-proven ownership; pre-existing/independently resumed files are retained.
     */
    override fun requestConversationExtension(command: LocalRolePackCommand, expectedTransactionId: String): Boolean = runCatching { synchronized(installJobLock) {
        val model = PrivateOfflinePack.conversationCandidate
        val (observed, recovery) = conversationReceipt()
        if (expectedTransactionId != (observed ?: "NONE")) return@synchronized false
        if (command == LocalRolePackCommand.START_OR_RESUME && !sourceReviewAcknowledged) return@synchronized false
        if (command == LocalRolePackCommand.PAUSE) {
            if (recovery || conversationJob?.isActive != true && (observed == null || !models.isDownloading(model.id))) return@synchronized false
            observed?.let { models.cancelOwnedDownload(model.id, it) }
            conversationJob?.cancel()
            return@synchronized true
        }
        if (conversationJob?.isActive == true || models.isDownloading(model.id)) return@synchronized false
        if (command != LocalRolePackCommand.KEEP_FILES && recovery) return@synchronized false
        if (command == LocalRolePackCommand.START_OR_RESUME &&
            (models.catalogModel(model.id) != model || !model.directDownloadAllowed)) return@synchronized false
        if (command in setOf(LocalRolePackCommand.ROLLBACK, LocalRolePackCommand.KEEP_FILES) &&
            storage.getString(CONVERSATION_KEY) == null) return@synchronized false
        conversationJob = scope.launch { conversationMutation.withLock {
            var operationOwner: String? = null
            try {
                val (current, currentRecovery) = conversationReceipt()
                if (expectedTransactionId != (current ?: "NONE")) return@withLock
                check(command == LocalRolePackCommand.KEEP_FILES || !currentRecovery)
                conversationMessage = null
                when (command) {
                    LocalRolePackCommand.START_OR_RESUME -> {
                        check(sourceReviewAcknowledged) { LOCAL_ROLE_PACK_REVIEW_REQUIRED }
                        check(models.catalogModel(model.id) == model && model.directDownloadAllowed)
                        val physical = models.verifyPhysicalArtifact(model.id)
                        if (CuratedLocalCatalog.matchesInstalledArtifact(model, physical?.sha256,
                                physical?.bytes ?: 0L, physical?.verified == true)) return@withLock
                        // Retain an independently installed damaged artifact for explicit repair.
                        check(!File(models.modelDir, "${model.id}.litertlm").exists() ||
                            current != null && models.acquisitionOwner(model.id) == current)
                        val owner = current ?: java.util.UUID.randomUUID().toString()
                        operationOwner = owner
                        if (current == null) storage.putString(CONVERSATION_KEY, "${PrivateOfflinePack.fingerprint}:$owner")
                        val remaining = (model.expectedBytes - (models.partialDownloadBytes(model.id) ?: 0L)
                            .coerceIn(0L, model.expectedBytes)).coerceAtLeast(1L)
                        check(com.halo.providers.litert.models.LiteRtStoragePreflight.evaluate(
                            remaining, models.modelDir.usableSpace).sufficient)
                        kotlin.coroutines.coroutineContext.ensureActive()
                        models.downloadCatalogModel(model, acquisitionOwner = owner)
                        while (true) {
                            when (models.downloadStates.value[model.id]) {
                                DownloadState.Installed -> {
                                    val installed = models.verifyPhysicalArtifact(model.id)
                                    check(CuratedLocalCatalog.matchesInstalledArtifact(model, installed?.sha256,
                                        installed?.bytes ?: 0L, installed?.verified == true))
                                    break
                                }
                                is DownloadState.Failed -> error("Conversation acquisition failed")
                                is DownloadState.Paused, DownloadState.NotInstalled -> break
                                else -> delay(300)
                            }
                        }
                    }
                    LocalRolePackCommand.ROLLBACK -> {
                        val owner = requireNotNull(current)
                        models.cancelOwnedDownload(model.id, owner)
                        if (models.acquisitionOwner(model.id) == owner) check(models.deleteModel(model.id, owner))
                        models.discardOwnedPartial(model.id, owner)
                        storage.remove(CONVERSATION_KEY)
                    }
                    LocalRolePackCommand.KEEP_FILES -> storage.remove(CONVERSATION_KEY)
                    LocalRolePackCommand.PAUSE -> Unit // Handled synchronously above.
                }
            } catch (cancelled: CancellationException) {
                // Pause can precede receipt persistence/manager registration. Cancel again
                // after START reaches its next suspension, using only this operation's owner.
                operationOwner?.let { runCatching { models.cancelOwnedDownload(model.id, it) } }
                throw cancelled
            }
            catch (_: Exception) {
                conversationMessage = "Conversation setup did not finish. Read file status; repair independent damaged files or retry the exact acquisition. Existing files were retained."
            }
        } }
        true
    } }.getOrDefault(false)

    override suspend fun offlineInventory(): OfflinePackInventory? = withContext(Dispatchers.IO) {
        if (!available || mutableState.value.busy) return@withContext null
        transaction.withLock {
            if (!available || mutableState.value.busy) return@withLock null
            speech.refresh()
            val intent = requireNotNull(intentCatalog)
            val physical = models.verifyPhysicalArtifact(intentId)
            val chat = PrivateOfflinePack.conversationCandidate
            val chatPhysical = models.verifyPhysicalArtifact(chat.id)
            val voices = supertonic?.packs?.verifiedInstalled().orEmpty()
            val selection = supertonic?.packs?.selection()
            val selectedIntact = voices.any { it.fingerprint == selection?.fingerprint }
            OfflinePackInventory(PrivateOfflinePack.fingerprint, buildSet {
                if (models.catalogModel(chat.id) == chat && CuratedLocalCatalog.matchesInstalledArtifact(chat,
                        chatPhysical?.sha256, chatPhysical?.bytes ?: 0L, chatPhysical?.verified == true))
                    add(OfflinePackRole.CONVERSATION)
                if (CuratedLocalCatalog.matchesInstalledArtifact(intent, physical?.sha256,
                        physical?.bytes ?: 0L, physical?.verified == true)) add(OfflinePackRole.ACTION_INTENT)
                if (speech.verifyInstalled(WhisperSmall.id)) add(OfflinePackRole.RECOGNITION)
                if (speech.verifyInstalled(SherpaSpeechModelCatalog.SILERO_VAD_ID)) add(OfflinePackRole.VOICE_ACTIVITY)
                if (voices.any { it.officialArchiveSha256 == SupertonicPackStore.OFFICIAL_SHA256 }) add(OfflinePackRole.SPEECH_OUTPUT)
            }, voices.mapTo(mutableSetOf()) { it.fingerprint }, selectedIntact, selectedIntact && selection?.audibleConfirmed == true)
        }
    }

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
        if (command == LocalRolePackCommand.START_OR_RESUME && !sourceReviewAcknowledged) return false
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
            if (!sourceReviewAcknowledged) {
                publish(mutableState.value.copy(message = LOCAL_ROLE_PACK_REVIEW_REQUIRED))
                return
            }
            installJob = scope.launch { transaction.withLock {
                if (expected != null && expected != (journal?.transactionId ?: "NONE")) return@withLock
                if (mutableState.value.recoveryRequired || mutableState.value.phase == LocalRolePackPhase.ROLLBACK_FAILED) return@withLock
                if (!sourceReviewAcknowledged) {
                    publish(mutableState.value.copy(message = LOCAL_ROLE_PACK_REVIEW_REQUIRED))
                    return@withLock
                }
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
        val beforeInspection = mutableState.value
        try {
            val active = if (journal == null || journal?.phase == LocalRolePackPhase.COMPLETE || journal?.phase == LocalRolePackPhase.IDLE) {
                // Exact disk verification can take time. Publish busy before inspecting so
                // repeated taps are suppressed and Pause can cancel the read-only preflight.
                publish(beforeInspection.copy(phase = LocalRolePackPhase.PREFLIGHT, message = null))
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
            if (!speech.verifyInstalled(WhisperSmall.id)) {
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
            check(speech.verifyInstalled(WhisperSmall.id)) { "Whisper Small did not pass its pinned file checks." }

            current = current.copy(stage = LocalRolePackStage.SPEECH_ACTIVITY)
            persist(current)
            speech.refresh()
            if (!speech.verifyInstalled(SherpaSpeechModelCatalog.SILERO_VAD_ID)) {
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
            check(speech.verifyInstalled(SherpaSpeechModelCatalog.SILERO_VAD_ID)) { "Silero VAD did not pass its pinned file checks." }

            current = current.copy(stage = LocalRolePackStage.SPEECH_OUTPUT)
            persist(current)
            val tts = supertonic ?: error("The Supertonic speech provider is unavailable on this installation.")
            val installedTts = tts.packs.verifiedOfficialInstalled().mapTo(mutableSetOf()) { it.fingerprint }
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
            check(retainedBaselinePack || newlyInstalled.isNotEmpty() || tts.packs.verifiedOfficialInstalled().isNotEmpty()) {
                "No verified local speech output pack is installed."
            }
            finish()
        } catch (cancelled: CancellationException) {
            if (operationJournal == null) publish(beforeInspection)
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
        val ttsFingerprints = supertonic?.packs?.verifiedInstalled()?.mapTo(mutableSetOf()) { it.fingerprint }.orEmpty()
        if (supertonic?.installedPacks().orEmpty().any { it.fingerprint !in ttsFingerprints })
            throw LocalRolePackRepairRequired()
        if (existing == null && models.partialDownloadBytes(intentId) != null) {
            error("An interrupted intent download already exists. Resume or discard it in model settings before starting this bundle.")
        }
        val whisperDirectory = File(speechRoot, WhisperSmall.directoryName)
        if (!speech.verifyInstalled(WhisperSmall.id) && whisperDirectory.listFiles().orEmpty().isNotEmpty()) {
            throw LocalRolePackRepairRequired()
        }
        val vadFile = File(speechRoot, SherpaSpeechModelCatalog.SILERO_VAD_FILE)
        if (!speech.verifyInstalled(SherpaSpeechModelCatalog.SILERO_VAD_ID) && (vadFile.exists() ||
                File(speechRoot, "${SherpaSpeechModelCatalog.SILERO_VAD_FILE}.verified").exists() ||
                File(speechRoot, "${SherpaSpeechModelCatalog.SILERO_VAD_FILE}.partial").exists())) {
            throw LocalRolePackRepairRequired()
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
            whisperSmallInstalled = speech.verifyInstalled(WhisperSmall.id),
            vadInstalled = speech.verifyInstalled(SherpaSpeechModelCatalog.SILERO_VAD_ID),
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
        val whisperInstalled = speech.verifyInstalled(WhisperSmall.id)
        val vadInstalled = speech.verifyInstalled(SherpaSpeechModelCatalog.SILERO_VAD_ID)
        val intentModel = requireNotNull(intentCatalog)
        val intentPartial = (models.partialDownloadBytes(intentId) ?: 0L).takeIf { it in 0L..intentModel.expectedBytes } ?: 0L
        val intentRemaining = (intentModel.expectedBytes - intentPartial).coerceAtLeast(0L)
        val intentBytes = if (intentNow) 0L else intentRemaining
        val planContext = kotlin.coroutines.coroutineContext
        val whisperRemaining = com.halo.providers.sherpa.stt.SpeechModelInstaller.remainingBytes(WhisperSmall, File(speechRoot, WhisperSmall.directoryName), verifyInstalledBytes = true) { planContext.ensureActive() }
        val speechBytes = if (whisperInstalled) 0L else whisperRemaining
        val vadRemaining = com.halo.providers.sherpa.stt.SpeechModelInstaller.remainingBytes(SherpaSpeechModelCatalog.sileroVad, speechRoot, verifyInstalledBytes = true) { planContext.ensureActive() }
        val vadBytes = if (vadInstalled) 0L else vadRemaining
        val ttsNowInstalled = supertonic?.packs?.verifiedOfficialInstalled()?.isNotEmpty() == true
        val ttsDownloadBytes = if (ttsNowInstalled) 0L else
            supertonic?.packs?.remainingOfficialDownloadBytes() ?: SupertonicPackStore.OFFICIAL_BYTES
        val ttsBytes = if (ttsNowInstalled) 0L else
            Math.addExact(ttsDownloadBytes, SupertonicPackStore.INSTALL_RESERVE_BYTES + 1L)
        // Match the model owner's resume preflight, including reserve when a full
        // staging file still needs verification/finalization but no network bytes.
        val intentStorageBytes = if (intentNow) 0L else
            com.halo.providers.litert.models.LiteRtStoragePreflight.evaluate(
                intentBytes.coerceAtLeast(1L), Long.MAX_VALUE).requiredBytes
        val speechReserve = if (!whisperInstalled || !vadInstalled) {
            com.halo.providers.sherpa.stt.SpeechModelInstaller.STORAGE_RESERVE_BYTES
        } else 0L
        val speechStaging = maxOf(
            com.halo.providers.sherpa.stt.SpeechModelInstaller.publicationStagingBytes(
                WhisperSmall, File(speechRoot, WhisperSmall.directoryName)) { planContext.ensureActive() },
            com.halo.providers.sherpa.stt.SpeechModelInstaller.publicationStagingBytes(
                SherpaSpeechModelCatalog.sileroVad, speechRoot) { planContext.ensureActive() })
        val speechStorageBytes = Math.addExact(Math.addExact(speechBytes, vadBytes),
            Math.addExact(speechStaging, speechReserve))
        val storageNeeds = listOfNotNull(
            models.modelDir.takeIf { intentStorageBytes > 0L }?.let { it to intentStorageBytes },
            speechRoot.takeIf { speechStorageBytes > 0L }?.let { it to speechStorageBytes },
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
        check(speech.verifyInstalled(WhisperSmall.id)) { "Whisper Small did not pass final file checks." }
        check(speech.verifyInstalled(SherpaSpeechModelCatalog.SILERO_VAD_ID)) { "Silero VAD did not pass final file checks." }
        check(supertonic?.packs?.verifiedOfficialInstalled()?.isNotEmpty() == true) { "No verified publisher speech output pack is installed." }
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
        error is LocalRolePackRepairRequired -> LOCAL_ROLE_PACK_REPAIR_REQUIRED
        error.message?.contains("Not enough free space", ignoreCase = true) == true -> error.message.orEmpty()
        error.message?.contains("pinned", ignoreCase = true) == true -> "A required model is not available in the pinned catalog. Update Halo and try again."
        else -> "The model setup did not finish. Check storage and connection, then resume or roll back."
    }

    private companion object {
        const val SOURCE_REVIEW_KEY = "local_role_pack_source_review_v1"
        const val CONVERSATION_KEY = "local_role_pack_conversation_v1"
        const val JOURNAL_KEY = "local_role_pack_transaction_v1"
        const val RECOVERY_MESSAGE = "halo can't verify the previous setup record. Keep the downloaded files and clear the record before starting again."
        const val CLEAR_FAILED_MESSAGE = "The setup record could not be cleared. Reopen halo and try again. Your downloaded files are still kept."
        val WhisperSmall = SherpaSpeechModelCatalog.whisperSmall
    }
}
