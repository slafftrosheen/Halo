package com.halo.app.storage

import android.content.Context
import java.io.File

data class HaloStorageLayout(
    val databaseName: String = "halo.db",
    val managedWorkspaceFiles: File,
    val generatedArtifacts: File,
    val models: File,
    val speechModels: File,
    val diagnostics: File,
    val forensicCaptures: File,
    val importStaging: File,
    val restoreStaging: File,
    val exportStaging: File,
    val secretFiles: File,
    val restoreSafety: File,
) {
    fun ensureDirectories() {
        listOf(
            managedWorkspaceFiles,
            generatedArtifacts,
            models,
            speechModels,
            diagnostics,
            forensicCaptures,
            importStaging,
            restoreStaging,
            exportStaging,
            secretFiles,
            restoreSafety,
        ).forEach { directory ->
            check(directory.exists() || directory.mkdirs()) {
                "Unable to create Halo storage directory: ${directory.name}"
            }
        }
    }
}

fun haloStorageLayout(context: Context): HaloStorageLayout {
    val persistent = context.filesDir
    val noBackup = context.noBackupFilesDir
    val cache = context.cacheDir
    return HaloStorageLayout(
        managedWorkspaceFiles = File(persistent, "workspace/owned"),
        generatedArtifacts = File(persistent, "workspace/artifacts"),
        models = File(noBackup, "models"),
        speechModels = File(noBackup, "speech"),
        diagnostics = File(noBackup, "diagnostics"),
        forensicCaptures = File(noBackup, "forensics"),
        importStaging = File(cache, "import"),
        restoreStaging = File(cache, "restore"),
        exportStaging = File(cache, "export"),
        secretFiles = File(noBackup, "secrets"),
        restoreSafety = File(noBackup, "restore-safety"),
    )
}
