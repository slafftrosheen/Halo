package com.halo.app.storage

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StorageUsage(
    val databaseBytes: Long = 0,
    val workspaceBytes: Long = 0,
    val modelBytes: Long = 0,
    val speechModelBytes: Long = 0,
    val diagnosticBytes: Long = 0,
    val cacheBytes: Long = 0,
)

interface StorageUsageRepository {
    val usage: StateFlow<StorageUsage>
    suspend fun refresh()
    suspend fun clearModels()
    suspend fun clearDiagnostics()
    suspend fun clearCache()
}

class AndroidStorageUsageRepository(
    private val context: Context,
    private val layout: HaloStorageLayout,
) : StorageUsageRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _usage = MutableStateFlow(StorageUsage())
    override val usage = _usage.asStateFlow()

    init { scope.launch { refresh() } }

    override suspend fun refresh() = withContext(Dispatchers.IO) {
        val database = context.getDatabasePath(layout.databaseName)
        _usage.value = StorageUsage(
            databaseBytes = listOf(database, java.io.File(database.path + "-wal"), java.io.File(database.path + "-shm")).sumOf { if (it.isFile) it.length() else 0L },
            workspaceBytes = directoryBytes(layout.managedWorkspaceFiles) + directoryBytes(layout.generatedArtifacts),
            modelBytes = directoryBytes(layout.models),
            speechModelBytes = directoryBytes(layout.speechModels),
            diagnosticBytes = directoryBytes(layout.diagnostics) + directoryBytes(layout.forensicCaptures),
            cacheBytes = directoryBytes(context.cacheDir),
        )
    }

    override suspend fun clearModels() { clearDirectory(layout.models); refresh() }
    override suspend fun clearDiagnostics() { clearDirectory(layout.diagnostics); clearDirectory(layout.forensicCaptures); refresh() }
    override suspend fun clearCache() { clearDirectory(context.cacheDir); refresh() }

    private fun directoryBytes(root: java.io.File): Long =
        if (!root.exists()) 0 else root.walkTopDown().filter(java.io.File::isFile).sumOf(java.io.File::length)

    private suspend fun clearDirectory(root: java.io.File) = withContext(Dispatchers.IO) {
        if (!root.exists()) return@withContext
        root.walkBottomUp().filter { it != root }.forEach { it.delete() }
        root.mkdirs()
    }
}
