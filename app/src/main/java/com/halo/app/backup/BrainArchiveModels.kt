package com.halo.app.backup

import com.squareup.moshi.JsonClass

object BrainArchiveSchema { const val CURRENT = 3 }

enum class BrainExportProfile { BRAIN_ONLY, BRAIN_WITH_FILES, FULL_USER_BACKUP }
enum class BrainImportMode { MERGE, REPLACE }
enum class ImportConflictPolicy { KEEP_LOCAL, USE_BACKUP, DUPLICATE }

@JsonClass(generateAdapter = true)
data class HaloBrainManifest(
    val format: String = "halo-brain",
    val schemaVersion: Int = BrainArchiveSchema.CURRENT,
    val archiveId: String,
    val createdAtEpochMs: Long,
    val appVersionName: String,
    val appVersionCode: Long,
    val databaseSchemaVersion: Int,
    val profile: BrainExportProfile,
    val encrypted: Boolean,
    val state: String = "COMPLETE",
    val sections: List<BrainArchiveSection>,
)

@JsonClass(generateAdapter = true)
data class BrainArchiveSection(val name: String, val version: Int = 1, val itemCount: Int)

@JsonClass(generateAdapter = true)
data class ArchiveCompletion(val archiveId: String, val manifestSha256: String, val checksumListSha256: String)

data class ArchiveChecksum(val path: String, val sha256: String)

data class BrainExportRequest(
    val profile: BrainExportProfile = BrainExportProfile.BRAIN_ONLY,
    val encrypt: Boolean = false,
    val password: CharArray? = null,
    val includeActivityHistory: Boolean = false,
)

sealed interface BrainExportProgress {
    data object Preparing : BrainExportProgress
    data class WritingSection(val name: String, val completed: Int, val total: Int) : BrainExportProgress
    data class WritingFile(val name: String, val bytesWritten: Long, val totalBytes: Long?) : BrainExportProgress
    data object Finalizing : BrainExportProgress
}

data class BrainExportResult(val archiveId: String, val bytesWritten: Long, val sha256: String)

interface BrainExportRepository {
    suspend fun export(
        destination: java.io.OutputStream,
        request: BrainExportRequest,
        progress: (BrainExportProgress) -> Unit = {},
    ): BrainExportResult
}

@JsonClass(generateAdapter = true)
data class BrainArchiveInfo(
    val manifest: HaloBrainManifest,
    val sourceBytes: Long,
    val encrypted: Boolean,
)

data class ImportOperation(val objectId: String, val type: String, val description: String)
data class ImportConflict(
    val objectId: String,
    val type: String,
    val localRevision: Int?,
    val incomingRevision: Int?,
    val localUpdatedAt: Long,
    val incomingUpdatedAt: Long,
)

data class BrainImportPlan(
    val archive: BrainArchiveInfo,
    val create: List<ImportOperation>,
    val update: List<ImportOperation>,
    val conflicts: List<ImportConflict>,
    val skipped: List<ImportOperation>,
    val missingFiles: List<String>,
)

sealed interface BrainArchiveValidation {
    data class Valid(val info: BrainArchiveInfo, val plan: BrainImportPlan) : BrainArchiveValidation
    data class UnsupportedVersion(val version: Int) : BrainArchiveValidation
    data class Corrupt(val reason: String) : BrainArchiveValidation
    data object WrongPassword : BrainArchiveValidation
}

data class ArchiveSecurityLimits(
    val maxArchiveBytes: Long = 2L * 1024 * 1024 * 1024,
    val maxEntries: Int = 50_000,
    val maxSingleEntryBytes: Long = 512L * 1024 * 1024,
    val maxTotalExpandedBytes: Long = 4L * 1024 * 1024 * 1024,
    val maxPathLength: Int = 240,
    val maxCompressionRatio: Int = 200,
)

data class BrainImportResult(val created: Int, val updated: Int, val skipped: Int)
