package com.halo.app.backup

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.halo.app.data.settings.HaloSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StorageAccessState(
    val backupLocation: BackupLocationState = BackupLocationState.NotConfigured,
    val persistedDocumentGrants: Int = 0,
)

sealed interface BackupLocationState {
    data object NotConfigured : BackupLocationState
    data class Available(val uri: Uri, val displayName: String?) : BackupLocationState
    data class PermissionLost(val uri: Uri) : BackupLocationState
    data class Unavailable(val uri: Uri, val reason: String) : BackupLocationState
}

interface StorageAccessRepository {
    val state: StateFlow<StorageAccessState>
    suspend fun setBackupTree(uri: Uri)
    suspend fun clearBackupTree()
    suspend fun validatePersistedAccess()
    suspend fun persistedBackupTree(): Uri?
}

class AndroidStorageAccessRepository(
    private val context: Context,
    private val settingsRepository: HaloSettingsRepository,
) : StorageAccessRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(StorageAccessState())
    override val state = _state.asStateFlow()
    private val resolver get() = context.contentResolver

    init { scope.launch { validatePersistedAccess() } }

    override suspend fun setBackupTree(uri: Uri) = withContext(Dispatchers.IO) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(uri, flags)
        settingsRepository.setBackupTreeUri(uri.toString())
        validatePersistedAccess()
    }

    override suspend fun clearBackupTree() = withContext(Dispatchers.IO) {
        persistedBackupTree()?.let { uri ->
            runCatching {
                resolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        settingsRepository.setBackupTreeUri(null)
        _state.value = StorageAccessState(BackupLocationState.NotConfigured, resolver.persistedUriPermissions.size)
    }

    override suspend fun validatePersistedAccess() = withContext(Dispatchers.IO) {
        val uri = settingsRepository.settings.first().backupTreeUri?.let(Uri::parse)
        val location = when {
            uri == null -> BackupLocationState.NotConfigured
            !hasPersistedGrant(resolver, uri) -> BackupLocationState.PermissionLost(uri)
            else -> runCatching {
                val tree = requireNotNull(DocumentFile.fromTreeUri(context, uri))
                check(tree.isDirectory && tree.canRead() && tree.canWrite()) { "Folder is not writable" }
                tree.name
            }.fold(
                onSuccess = { BackupLocationState.Available(uri, it) },
                onFailure = { BackupLocationState.Unavailable(uri, it.message ?: "Folder is unavailable") },
            )
        }
        _state.value = StorageAccessState(location, resolver.persistedUriPermissions.size)
    }

    override suspend fun persistedBackupTree(): Uri? =
        settingsRepository.settings.first().backupTreeUri?.let(Uri::parse)
}

fun hasPersistedGrant(resolver: ContentResolver, uri: Uri): Boolean =
    resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
