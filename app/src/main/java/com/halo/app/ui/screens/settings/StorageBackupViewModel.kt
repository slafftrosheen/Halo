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

data class StorageBackupUiState(
    val access: StorageAccessState = StorageAccessState(),
    val usage: StorageUsage = StorageUsage(),
    val vaultStatus: VaultStatus = VaultStatus.NotConfigured,
    val automaticBackup: AutomaticBackupSchedule = AutomaticBackupSchedule.OFF,
    val retention: Int = 5,
    val busy: Boolean = false,
    val progress: String? = null,
    val importValidation: BrainArchiveValidation? = null,
    val relinkFiles: List<WorkspaceFile> = emptyList(),
    val message: String? = null,
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
    private val onAppearanceRestored: () -> Unit,
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
            progress = op.progress,
            importValidation = op.validation,
            relinkFiles = op.relinkFiles,
            message = op.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageBackupUiState())

    fun selectBackupFolder(uri: Uri) = launchOperation {
        accessRepository.setBackupTree(uri)
        vaultRepository.validate()
        "Halo Vault connected"
    }

    fun removeBackupFolder() = launchOperation {
        accessRepository.clearBackupTree()
        BrainBackupScheduler.schedule(context, AutomaticBackupSchedule.OFF)
        settingsRepository.setAutomaticBackup(AutomaticBackupSchedule.OFF.name)
        "Folder access removed. Existing backups were not deleted."
    }

    fun exportTo(uri: Uri, profile: BrainExportProfile, protect: Boolean, password: String) = launchOperation {
        try {
            val request = BrainExportRequest(profile, protect, password.takeIf { protect }?.toCharArray())
            val result = context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                exporter.export(output, request) { progress ->
                    operation.value = operation.value.copy(progress = progress.label())
                }
            } ?: error("Unable to open export destination")
            "Brain Dump exported (${formatBytes(result.bytesWritten)})"
        } catch (error: Throwable) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw error
        }
    }

    fun createVaultBackup() = launchOperation {
        val result = vaultRepository.createBackup(BrainExportRequest(BrainExportProfile.FULL_USER_BACKUP))
        "Backup completed (${formatBytes(result.sizeBytes ?: 0)})"
    }

    fun inspect(uri: Uri, password: String?) = launchOperation {
        stagedImport?.delete()
        val staged = importer.stage(uri)
        stagedImport = staged
        val validation = importer.inspect(staged, password?.takeIf(String::isNotBlank)?.toCharArray())
        operation.value = operation.value.copy(validation = validation)
        when (validation) {
            is BrainArchiveValidation.Valid -> "Backup inspected"
            BrainArchiveValidation.WrongPassword -> "Password required or incorrect"
            is BrainArchiveValidation.UnsupportedVersion -> "This backup was created by a newer Halo version"
            is BrainArchiveValidation.Corrupt -> "Backup is not valid: ${validation.reason}"
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
        "Restore completed: ${result.created} created, ${result.updated} updated, ${result.skipped} kept local"
    }

    fun undoRestore() = launchOperation {
        val safety = storageLayout.restoreSafety.listFiles { file -> file.extension == "halobrain" }.orEmpty()
            .maxByOrNull(java.io.File::lastModified) ?: error("No local restore snapshot remains")
        val result = importer.apply(safety, BrainImportMode.REPLACE)
        onAppearanceRestored()
        usageRepository.refresh()
        "Previous brain restored: ${result.created + result.updated} objects"
    }

    fun relinkFile(objectId: String, uri: Uri) = launchOperation {
        workspaceFiles.relink(WorkspaceObjectId(objectId), uri)
        "File reconnected"
    }

    fun setAutomaticBackup(schedule: AutomaticBackupSchedule) = viewModelScope.launch {
        if (schedule != AutomaticBackupSchedule.OFF && accessRepository.state.value.backupLocation !is BackupLocationState.Available) {
            operation.value = operation.value.copy(message = "Choose a backup folder first")
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
        "Model downloads cleared"
    }
    fun clearDiagnostics() = launchOperation { usageRepository.clearDiagnostics(); "Diagnostics cleared" }
    fun clearCache() = launchOperation {
        stagedImport = null
        operation.value = operation.value.copy(validation = null)
        usageRepository.clearCache()
        "Temporary cache cleared"
    }
    fun refresh() = viewModelScope.launch { accessRepository.validatePersistedAccess(); vaultRepository.validate(); usageRepository.refresh() }
    fun clearMessage() { operation.value = operation.value.copy(message = null) }

    private fun launchOperation(block: suspend () -> String) {
        if (operation.value.busy) return
        operation.value = operation.value.copy(busy = true, message = null, progress = null)
        viewModelScope.launch {
            try {
                val message = try { block() } catch (error: CancellationException) { throw error } catch (error: Throwable) { error.message ?: "Operation failed" }
                operation.value = operation.value.copy(message = message)
            } finally {
                operation.value = operation.value.copy(busy = false, progress = null)
            }
        }
    }

    private data class OperationState(
        val busy: Boolean = false,
        val progress: String? = null,
        val validation: BrainArchiveValidation? = null,
        val relinkFiles: List<WorkspaceFile> = emptyList(),
        val message: String? = null,
    )

    companion object {
        fun suggestedFilename(): String = "halo-brain-${LocalDate.now()}-${UUID.randomUUID().toString().take(8)}.halobrain"
    }
}

private fun BrainExportProgress.label(): String = when (this) {
    BrainExportProgress.Preparing -> "Preparing"
    BrainExportProgress.Finalizing -> "Finalizing"
    is BrainExportProgress.WritingSection -> "Writing $name · $completed/$total"
    is BrainExportProgress.WritingFile -> "Writing $name · ${formatBytes(bytesWritten)}"
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return "%.1f %s".format(value, units[unit])
}
