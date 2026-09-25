package com.halo.app.backup

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.halo.app.data.AppDatabase
import com.halo.app.data.settings.HaloSettingsRepository
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed interface VaultStatus {
    data object NotConfigured : VaultStatus
    data class Healthy(val lastSuccessful: BackupRecordEntity?) : VaultStatus
    data object AccessLost : VaultStatus
    data class Failed(val reason: String) : VaultStatus
}

interface HaloVaultRepository {
    val status: StateFlow<VaultStatus>
    suspend fun createBackup(request: BrainExportRequest): BackupRecordEntity
    suspend fun listBackups(): List<BackupRecordEntity>
    suspend fun deleteBackup(id: String)
    suspend fun validate()
}

class AndroidHaloVaultRepository(
    private val context: Context,
    private val access: StorageAccessRepository,
    private val exporter: BrainExportRepository,
    private val importer: BrainImportRepository,
    private val database: AppDatabase,
    private val settings: HaloSettingsRepository,
) : HaloVaultRepository {
    private val records get() = database.backupRecordDao()
    private val _status = MutableStateFlow<VaultStatus>(VaultStatus.NotConfigured)
    override val status = _status.asStateFlow()

    override suspend fun createBackup(request: BrainExportRequest): BackupRecordEntity = withContext(Dispatchers.IO) {
        access.validatePersistedAccess()
        val treeUri = (access.state.value.backupLocation as? BackupLocationState.Available)?.uri
            ?: throw IllegalStateException("Halo Vault is unavailable")
        val tree = requireNotNull(DocumentFile.fromTreeUri(context, treeUri))
        val backups = tree.findFile("Backups") ?: tree.createDirectory("Backups")
            ?: error("Cannot create Backups directory")
        check(backups.isDirectory) { "Backups is not a folder" }
        val id = UUID.randomUUID().toString()
        val stamp = FILE_TIME.format(Instant.now())
        val finalName = "halo-brain_${stamp}_${id.take(8)}.halobrain"
        val temp = backups.createFile("application/zip", ".tmp-$finalName")
            ?: error("Cannot create backup document")
        var record = BackupRecordEntity(id, id, temp.uri.toString(), System.currentTimeMillis(), request.profile.name, null, null, BackupRecordState.WRITING.name)
        records.upsert(record)
        try {
            val result = context.contentResolver.openOutputStream(temp.uri, "w")?.use { exporter.export(it, request) }
                ?: error("Cannot open backup destination")
            val validationFile = importer.stage(temp.uri)
            try {
                require(importer.inspect(validationFile, null) is BrainArchiveValidation.Valid) { "Backup verification failed" }
            } finally {
                validationFile.delete()
            }
            val finalDocument = if (temp.renameTo(finalName)) temp else {
                val final = backups.createFile("application/zip", finalName) ?: error("Cannot finalize backup document")
                try {
                    val input = context.contentResolver.openInputStream(temp.uri) ?: error("Cannot read temporary backup")
                    val output = context.contentResolver.openOutputStream(final.uri, "w") ?: error("Cannot open final backup")
                    input.use { source -> output.use { destination -> source.copyTo(destination) } }
                    temp.delete()
                    final
                } catch (error: Throwable) {
                    final.delete()
                    throw error
                }
            }
            record = record.copy(
                archiveId = result.archiveId,
                destinationUri = finalDocument.uri.toString(),
                sizeBytes = result.bytesWritten,
                checksum = result.sha256,
                state = BackupRecordState.COMPLETE.name,
            )
            records.upsert(record)
            enforceRetention(backups)
            _status.value = VaultStatus.Healthy(record)
            record
        } catch (error: Throwable) {
            temp.delete()
            records.upsert(record.copy(state = BackupRecordState.FAILED.name))
            _status.value = VaultStatus.Failed(error.message ?: "Backup failed")
            throw error
        }
    }

    override suspend fun listBackups(): List<BackupRecordEntity> = withContext(Dispatchers.IO) { records.all() }

    override suspend fun deleteBackup(id: String) = withContext(Dispatchers.IO) {
        val record = records.all().firstOrNull { it.id == id } ?: return@withContext
        DocumentFile.fromSingleUri(context, android.net.Uri.parse(record.destinationUri))?.delete()
        records.delete(id)
    }

    override suspend fun validate() = withContext(Dispatchers.IO) {
        access.validatePersistedAccess()
        _status.value = when (access.state.value.backupLocation) {
            BackupLocationState.NotConfigured -> VaultStatus.NotConfigured
            is BackupLocationState.PermissionLost -> VaultStatus.AccessLost
            is BackupLocationState.Unavailable -> VaultStatus.Failed("Backup folder is unavailable")
            is BackupLocationState.Available -> {
                val latest = records.all().firstOrNull { it.state == BackupRecordState.COMPLETE.name }
                if (latest != null && DocumentFile.fromSingleUri(context, android.net.Uri.parse(latest.destinationUri))?.exists() != true) {
                    records.upsert(latest.copy(state = BackupRecordState.MISSING.name))
                    VaultStatus.Failed("Last backup is missing from the folder")
                } else VaultStatus.Healthy(latest)
            }
        }
    }

    private suspend fun enforceRetention(backups: DocumentFile) {
        val keep = settings.settings.first().backupRetention
        val currentDocuments = backups.listFiles().associateBy { it.uri.toString() }
        records.all().filter { it.state == BackupRecordState.COMPLETE.name && it.destinationUri in currentDocuments }
            .drop(keep).forEach { old ->
                if (currentDocuments[old.destinationUri]?.delete() == true) records.delete(old.id)
        }
    }

    private companion object {
        val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss").withZone(ZoneOffset.UTC)
    }
}
