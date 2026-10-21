package com.halo.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.backup.*
import com.halo.app.data.settings.HaloSettingsRepository
import com.halo.app.storage.StorageUsage
import com.halo.app.storage.StorageUsageRepository
import com.halo.app.storage.HaloStorageLayout
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.data.workspace.WorkspaceFileAvailability
import com.halo.app.data.workspace.WorkspaceFileRepository
import com.halo.app.data.notebook.WorkspaceObjectId
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class BackupFeedback(val template: String, val arguments: List<Any> = emptyList(), val failed: Boolean = false) {
    fun english(): String = if (arguments.isEmpty()) template else String.format(java.util.Locale.ROOT, template, *arguments.toTypedArray())
}

data class StorageBackupUiState(
    val access: StorageAccessState = StorageAccessState(),
    val usage: StorageUsage = StorageUsage(),
    val vaultStatus: VaultStatus = VaultStatus.NotConfigured,
    val automaticBackup: AutomaticBackupSchedule = AutomaticBackupSchedule.OFF,
    val retention: Int = 5,
    val busy: Boolean = false,
    val progress: String? = null,
    val progressFeedback: BackupFeedback? = null,
    val importValidation: BrainArchiveValidation? = null,
    val relinkFiles: List<WorkspaceFile> = emptyList(),
    val message: String? = null,
    val feedback: BackupFeedback? = null,
    val canUndoRestore: Boolean = false,
)

class StorageBackupViewModel(
    private val context: Context,
    private val accessRepository: StorageAccessRepository,
    private val vaultRepository: HaloVaultRepository,
    private val exporter: BrainExportRepository,
    private val importer: BrainImportRepository,
    private val usageRepository: StorageUsageRepository,
    private val settingsRepository: HaloSettingsRepository,
    private val storageLayout: HaloStorageLayout,
    private val workspaceFiles: WorkspaceFileRepository,
    private val canClearModels: suspend () -> Boolean,
    private val onModelsCleared: () -> Unit,
    private val onAppearanceRestored: suspend () -> Unit,
) : ViewModel() {
    private val operation = MutableStateFlow(OperationState())
    private var stagedImport: java.io.File? = null

    init {
        viewModelScope.launch {
            workspaceFiles.observeFiles().collect { files ->
                operation.value = operation.value.copy(relinkFiles = files.filter { it.availability == WorkspaceFileAvailability.NEEDS_RELINK })
            }
        }
    }

    val uiState: StateFlow<StorageBackupUiState> = combine(
        accessRepository.state,
        usageRepository.usage,
        vaultRepository.status,
        settingsRepository.settings,
        operation,
    ) { access, usage, vault, settings, op ->
        StorageBackupUiState(
            access = access,
            usage = usage,
            vaultStatus = vault,
            automaticBackup = runCatching { AutomaticBackupSchedule.valueOf(settings.automaticBackup) }.getOrDefault(AutomaticBackupSchedule.OFF),
            retention = settings.backupRetention,
            busy = op.busy,
            progress = op.progress?.english(),
            progressFeedback = op.progress,
            importValidation = op.validation,
            relinkFiles = op.relinkFiles,
            message = op.feedback?.english(),
            feedback = op.feedback,
            canUndoRestore = op.canUndoRestore,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageBackupUiState())

    fun selectBackupFolder(uri: Uri) = launchOperation {
        accessRepository.setBackupTree(uri)
        vaultRepository.validate()
        BackupFeedback("Halo Vault connected")
    }

    fun removeBackupFolder() = launchOperation {
        accessRepository.clearBackupTree()
        BrainBackupScheduler.schedule(context, AutomaticBackupSchedule.OFF)
        settingsRepository.setAutomaticBackup(AutomaticBackupSchedule.OFF.name)
        BackupFeedback("Folder access removed. Existing backups were not deleted.")
    }

    fun exportTo(uri: Uri, profile: BrainExportProfile, protect: Boolean, password: String) = launchOperation {
        try {
            check(protect && password.isNotBlank()) { "Enter the export password again after the interruption" }
            val request = BrainExportRequest(profile, protect, password.takeIf { protect }?.toCharArray())
            val result = context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                exporter.export(output, request) { progress ->
                    operation.value = operation.value.copy(progress = progress.label())
                }
            } ?: error("Unable to open export destination")
            BackupFeedback("Brain Dump exported (%1\$s)", listOf(formatBytes(result.bytesWritten)))
        } catch (error: Throwable) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw error
        }
    }

    fun createVaultBackup() = launchOperation {
        val result = vaultRepository.createBackup(BrainExportRequest(BrainExportProfile.FULL_USER_BACKUP))
        BackupFeedback("Backup completed (%1\$s)", listOf(formatBytes(result.sizeBytes ?: 0)))
    }
    fun saveBackupPassword(password: String, onSaved: () -> Unit = {}) = launchOperation {
        vaultRepository.setPassword(password)
        withContext(Dispatchers.Main) { onSaved() }
        BackupFeedback("Backup password saved")
    }

    fun inspect(uri: Uri, password: String?) = launchOperation {
        operation.value = operation.value.copy(validation = null)
        stagedImport?.delete()
        stagedImport = null
        val staged = importer.stage(uri)
        stagedImport = staged
        val validation = importer.inspect(staged, password?.takeIf(String::isNotBlank)?.toCharArray())
        operation.value = operation.value.copy(validation = validation)
        when (validation) {
            is BrainArchiveValidation.Valid -> BackupFeedback("Backup inspected. Review the plan before restoring.")
            BrainArchiveValidation.WrongPassword -> BackupFeedback("Password required or incorrect", failed = true)
            is BrainArchiveValidation.UnsupportedVersion -> BackupFeedback("This backup was created by a newer Halo version", failed = true)
            is BrainArchiveValidation.Corrupt -> BackupFeedback("This backup could not be validated. Choose another file or check its password.", failed = true)
        }
    }

    fun restore(mode: BrainImportMode, password: String?, conflictPolicies: Map<String, ImportConflictPolicy> = emptyMap()) = launchOperation {
        val staged = stagedImport ?: error("Choose a Brain Dump first")
        val result = importer.apply(staged, mode, password?.takeIf(String::isNotBlank)?.toCharArray(), conflictPolicies)
        onAppearanceRestored()
        staged.delete()
        stagedImport = null
        usageRepository.refresh()
        operation.value = operation.value.copy(validation = null)
        BackupFeedback("Restore completed: %1\$d created, %2\$d updated, %3\$d kept local", listOf(result.created, result.updated, result.skipped))
    }

    fun undoRestore() = launchOperation {
        val safety = storageLayout.restoreSafety.listFiles { file -> file.extension == "halobrain" }.orEmpty()
            .filter { System.currentTimeMillis() - it.lastModified() <= java.util.concurrent.TimeUnit.DAYS.toMillis(7) }
            .maxByOrNull(java.io.File::lastModified) ?: error("No local restore snapshot remains")
        val result = importer.apply(safety, BrainImportMode.REPLACE)
        onAppearanceRestored()
        usageRepository.refresh()
        BackupFeedback("Previous brain restored: %1\$d objects", listOf(result.created + result.updated))
    }

    fun relinkFile(objectId: String, uri: Uri) = launchOperation {
        workspaceFiles.relink(WorkspaceObjectId(objectId), uri)
        BackupFeedback("File reconnected")
    }

    fun setAutomaticBackup(schedule: AutomaticBackupSchedule) = viewModelScope.launch {
        if (schedule != AutomaticBackupSchedule.OFF && vaultRepository.status.value == VaultStatus.NeedsPassword) {
            operation.value = operation.value.copy(feedback = BackupFeedback("Set a backup password first", failed = true))
            return@launch
        }
        if (schedule != AutomaticBackupSchedule.OFF && accessRepository.state.value.backupLocation !is BackupLocationState.Available) {
            operation.value = operation.value.copy(feedback = BackupFeedback("Choose a backup folder first", failed = true))
            return@launch
        }
        settingsRepository.setAutomaticBackup(schedule.name)
        BrainBackupScheduler.schedule(context, schedule)
    }

    fun setRetention(count: Int) = viewModelScope.launch { settingsRepository.setBackupRetention(count) }
    fun clearModels() = launchOperation {
        check(canClearModels()) { "A local model is active. Stop inference before clearing downloads." }
        usageRepository.clearModels()
        onModelsCleared()
        BackupFeedback("Model downloads cleared")
    }
    fun clearDiagnostics() = launchOperation { usageRepository.clearDiagnostics(); BackupFeedback("Diagnostics cleared") }
    fun clearCache() = launchOperation {
        stagedImport = null
        operation.value = operation.value.copy(validation = null)
        usageRepository.clearCache()
        BackupFeedback("Temporary cache cleared")
    }
    fun refresh() = viewModelScope.launch {
        accessRepository.validatePersistedAccess(); vaultRepository.validate(); usageRepository.refresh()
        refreshRecovery()
    }
    private suspend fun refreshRecovery() {
        val available = withContext(Dispatchers.IO) {
            storageLayout.restoreSafety.listFiles { file -> file.extension == "halobrain" }.orEmpty().any {
                System.currentTimeMillis() - it.lastModified() <= java.util.concurrent.TimeUnit.DAYS.toMillis(7)
            }
        }
        operation.value = operation.value.copy(canUndoRestore = available)
    }
    fun clearMessage() { operation.value = operation.value.copy(feedback = null) }

    private fun launchOperation(block: suspend () -> BackupFeedback) {
        if (operation.value.busy) return
        operation.value = operation.value.copy(busy = true, feedback = null, progress = null)
        viewModelScope.launch {
            try {
                val message = try {
                    withContext(Dispatchers.IO) { block() }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    BackupFeedback(when (error.message) {
                        "Enter the export password again after the interruption" -> "Enter the export password again after the interruption"
                        "A local model is active. Stop inference before clearing downloads." -> "A local model is active. Stop inference before clearing downloads."
                        else -> "The operation did not finish. Check access, storage and the password, then try again."
                    }, failed = true)
                }
                operation.value = operation.value.copy(feedback = message)
                refreshRecovery()
            } finally {
                operation.value = operation.value.copy(busy = false, progress = null)
            }
        }
    }

    private data class OperationState(
        val busy: Boolean = false,
        val progress: BackupFeedback? = null,
        val validation: BrainArchiveValidation? = null,
        val relinkFiles: List<WorkspaceFile> = emptyList(),
        val feedback: BackupFeedback? = null,
        val canUndoRestore: Boolean = false,
    )

    companion object {
        fun suggestedFilename(): String = "halo-brain-${LocalDate.now()}-${UUID.randomUUID().toString().take(8)}.halobrain"
    }
}

private fun BrainExportProgress.label(): BackupFeedback = when (this) {
    BrainExportProgress.Preparing -> BackupFeedback("Preparing")
    BrainExportProgress.Finalizing -> BackupFeedback("Finalizing")
    is BrainExportProgress.WritingSection -> BackupFeedback("Writing %1\$s · %2\$d/%3\$d", listOf(name, completed, total))
    is BrainExportProgress.WritingFile -> BackupFeedback("Writing %1\$s · %2\$s", listOf(name, formatBytes(bytesWritten)))
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return "%.1f %s".format(value, units[unit])
}
