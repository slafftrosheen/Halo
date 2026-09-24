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
    ): ModelRuntimeProfileEntity? = dao.getBestProfile(
        artifactId = artifactId,
        artifactFingerprint = artifactFingerprint,
        deviceFingerprint = deviceFingerprint,
        runtimeVersion = runtimeVersion,
        compatibilityEpoch = compatibilityEpoch,
    )

    suspend fun save(profile: ModelRuntimeProfileEntity) {
        dao.insertProfile(profile)
    }

    suspend fun invalidateArtifact(artifactId: String) {
        dao.deleteByArtifactId(artifactId)
    }
}
