package com.halo.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ModelTransferState {
    REMOTE,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    DOWNLOADED,
    FAILED,
    CANCELLED,
}

enum class ModelValidationState {
    UNKNOWN,
    VALIDATING,
    VALID,
    UNSUPPORTED_ARCHITECTURE,
    INVALID_GGUF,
    SIZE_MISMATCH,
}

@Entity(tableName = "model_artifacts")
data class ModelArtifactEntity(
    @PrimaryKey val id: String,

    val source: String,

    val repoId: String?,
    val revisionSha: String?,
    val filename: String,

    val displayName: String,

    val expectedBytes: Long?,
    val actualBytes: Long?,

    val quantization: String?,
    val architecture: String?,

    val localPath: String?,

    val validationState: String,
    val transferState: String,

    val createdAt: Long,
    val updatedAt: Long,

    val failureReason: String?,
)
