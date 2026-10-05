package com.halo.app.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "model_runtime_profiles",
    indices = [
        Index(value = ["artifactId", "deviceFingerprint"]),
        Index(
            name = "index_model_runtime_profiles_profile_variant",
            value = [
                "artifactId", "artifactFingerprint", "deviceFingerprint", "runtime", "backend",
                "contextSize", "threads", "gpuLayers", "runtimeVersion", "compatibilityEpoch", "isStable"
            ],
            unique = true
        )
    ]
)
data class ModelRuntimeProfileEntity(
    @PrimaryKey val id: String,
    val artifactId: String,
    val artifactFingerprint: String,
    val deviceFingerprint: String,
    val runtime: String,
    val backend: String,
    val contextSize: Int?,
    val threads: Int,
    val gpuLayers: Int,
    val loadMs: Long,
    val ttftMs: Long,
    val promptTokensPerSecond: Float,
    val generationTokensPerSecond: Float,
    val peakEstimatedMemoryBytes: Long? = null,
    val textGenerationProven: Boolean = false,
    val nativeToolCallingProven: Boolean = false,
    val haloPromptToolLoopProven: Boolean = false,
    val agenticContinuationProven: Boolean = false,
    val testedAtEpochMs: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
    val runtimeVersion: String = "0.4.0",
    val compatibilityEpoch: Int = 1,
    val isStable: Boolean = true,
    val failureReason: String? = null
)
