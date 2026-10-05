package com.halo.app.data.models

/**
 * Persistence boundary for device/model-specific runtime measurements.
 * A profile is reusable only when the model bytes and runtime compatibility
 * identity still match; a filename or catalog id alone is not sufficient.
 */
class ModelRuntimeProfileRepository(
    private val dao: ModelRuntimeProfileDao,
) {
    suspend fun preferred(
        artifactId: String,
        artifactFingerprint: String,
        deviceFingerprint: String,
        runtimeVersion: String,
        compatibilityEpoch: Int,
        runtime: String = "LITERT",
        backend: String? = null,
        contextSize: Int? = null,
        threads: Int? = null,
        gpuLayers: Int? = null,
    ): ModelRuntimeProfileEntity? = dao.getBestProfile(
        artifactId = artifactId,
        artifactFingerprint = artifactFingerprint,
        deviceFingerprint = deviceFingerprint,
        runtimeVersion = runtimeVersion,
        compatibilityEpoch = compatibilityEpoch,
        runtime = runtime,
        backend = backend,
        contextSize = contextSize,
        threads = threads,
        gpuLayers = gpuLayers,
    )

    suspend fun save(profile: ModelRuntimeProfileEntity) {
        require(profile.failureReason == null || profile.failureReason in setOf(
            "LOAD_FAILED", "GENERATION_FAILED", "GPU_PROCESS_DEATH", "TIMEOUT", "RESOURCE_BUSY", "ARTIFACT_MISMATCH"))
        require(!profile.isStable || (profile.textGenerationProven && profile.failureReason == null))
        dao.saveMeasurement(profile)
    }

    suspend fun hasCompatibleCalibration(artifactId: String, deviceFingerprint: String, runtime: String,
        runtimeVersion: String, compatibilityEpoch: Int): Boolean = dao.getProfilesForModel(artifactId, deviceFingerprint)
        .any { it.isStable && it.textGenerationProven && it.runtime == runtime &&
            it.runtimeVersion == runtimeVersion && it.compatibilityEpoch == compatibilityEpoch }

    suspend fun markTextGenerationProven(profile: ModelRuntimeProfileEntity): Int = dao.markTextGenerationProven(
        profile.id, profile.artifactId, profile.artifactFingerprint, profile.deviceFingerprint,
        profile.runtime, profile.backend, profile.contextSize, profile.threads, profile.gpuLayers,
        profile.runtimeVersion, profile.compatibilityEpoch,
    )

    suspend fun markNativeToolCallingProven(profile: ModelRuntimeProfileEntity): Int = dao.markNativeToolCallingProven(
        profile.id, profile.artifactId, profile.artifactFingerprint, profile.deviceFingerprint,
        profile.runtime, profile.backend, profile.contextSize, profile.threads, profile.gpuLayers,
        profile.runtimeVersion, profile.compatibilityEpoch,
    )

    suspend fun markPromptToolLoopProven(profile: ModelRuntimeProfileEntity): Int = dao.markPromptToolLoopProven(
        profile.id, profile.artifactId, profile.artifactFingerprint, profile.deviceFingerprint,
        profile.runtime, profile.backend, profile.contextSize, profile.threads, profile.gpuLayers,
        profile.runtimeVersion, profile.compatibilityEpoch,
    )

    suspend fun markAgenticContinuationProven(profile: ModelRuntimeProfileEntity): Int = dao.markAgenticContinuationProven(
        profile.id, profile.artifactId, profile.artifactFingerprint, profile.deviceFingerprint,
        profile.runtime, profile.backend, profile.contextSize, profile.threads, profile.gpuLayers,
        profile.runtimeVersion, profile.compatibilityEpoch,
    )

    suspend fun invalidateArtifact(artifactId: String) {
        dao.deleteByArtifactId(artifactId)
    }
}
