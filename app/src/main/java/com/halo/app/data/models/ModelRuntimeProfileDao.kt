package com.halo.app.data.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelRuntimeProfileDao {

    @Query("SELECT * FROM model_runtime_profiles WHERE artifactId = :artifactId AND deviceFingerprint = :deviceFingerprint")
    suspend fun getProfilesForModel(artifactId: String, deviceFingerprint: String): List<ModelRuntimeProfileEntity>

    @Query("SELECT * FROM model_runtime_profiles WHERE artifactId = :artifactId AND artifactFingerprint = :artifactFingerprint AND deviceFingerprint = :deviceFingerprint AND runtimeVersion = :runtimeVersion AND compatibilityEpoch = :compatibilityEpoch AND runtime = :runtime AND (:backend IS NULL OR backend = :backend) AND (:contextSize IS NULL OR contextSize = :contextSize) AND (:threads IS NULL OR threads = :threads) AND (:gpuLayers IS NULL OR gpuLayers = :gpuLayers) AND textGenerationProven = 1 AND ttftMs >= 0 AND isStable = 1 ORDER BY ttftMs ASC, generationTokensPerSecond DESC, loadMs ASC LIMIT 1")
    suspend fun getBestProfile(
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
    ): ModelRuntimeProfileEntity?

    @Query("SELECT * FROM model_runtime_profiles WHERE id = :id")
    suspend fun getProfileById(id: String): ModelRuntimeProfileEntity?

    @Query("SELECT * FROM model_runtime_profiles")
    fun observeAll(): Flow<List<ModelRuntimeProfileEntity>>

    @Transaction
    suspend fun saveMeasurement(profile: ModelRuntimeProfileEntity) {
        val old = (getProfileById(profile.id) ?: if (profile.isStable) getBestProfile(profile.artifactId, profile.artifactFingerprint,
            profile.deviceFingerprint, profile.runtimeVersion, profile.compatibilityEpoch,
            profile.runtime, profile.backend, profile.contextSize, profile.threads, profile.gpuLayers) else null)?.takeIf {
            it.artifactId == profile.artifactId && it.artifactFingerprint == profile.artifactFingerprint &&
                it.deviceFingerprint == profile.deviceFingerprint && it.runtime == profile.runtime && it.backend == profile.backend &&
                it.contextSize == profile.contextSize && it.threads == profile.threads && it.gpuLayers == profile.gpuLayers &&
                it.runtimeVersion == profile.runtimeVersion && it.compatibilityEpoch == profile.compatibilityEpoch && it.isStable == profile.isStable
        }
        insertProfile(if (old == null) profile else profile.copy(
            nativeToolCallingProven = profile.nativeToolCallingProven || old.nativeToolCallingProven,
            haloPromptToolLoopProven = profile.haloPromptToolLoopProven || old.haloPromptToolLoopProven,
            agenticContinuationProven = profile.agenticContinuationProven || old.agenticContinuationProven,
        ))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ModelRuntimeProfileEntity)

    @Query("UPDATE model_runtime_profiles SET textGenerationProven = 1 WHERE id = :id AND artifactId = :artifactId AND artifactFingerprint = :artifactFingerprint AND deviceFingerprint = :deviceFingerprint AND runtime = :runtime AND backend = :backend AND contextSize IS :contextSize AND threads = :threads AND gpuLayers = :gpuLayers AND runtimeVersion = :runtimeVersion AND compatibilityEpoch = :compatibilityEpoch AND isStable = 1")
    suspend fun markTextGenerationProven(id: String, artifactId: String, artifactFingerprint: String, deviceFingerprint: String, runtime: String, backend: String, contextSize: Int?, threads: Int, gpuLayers: Int, runtimeVersion: String, compatibilityEpoch: Int): Int

    @Query("UPDATE model_runtime_profiles SET nativeToolCallingProven = 1 WHERE id = :id AND artifactId = :artifactId AND artifactFingerprint = :artifactFingerprint AND deviceFingerprint = :deviceFingerprint AND runtime = :runtime AND backend = :backend AND contextSize IS :contextSize AND threads = :threads AND gpuLayers = :gpuLayers AND runtimeVersion = :runtimeVersion AND compatibilityEpoch = :compatibilityEpoch AND isStable = 1")
    suspend fun markNativeToolCallingProven(id: String, artifactId: String, artifactFingerprint: String, deviceFingerprint: String, runtime: String, backend: String, contextSize: Int?, threads: Int, gpuLayers: Int, runtimeVersion: String, compatibilityEpoch: Int): Int

    @Query("UPDATE model_runtime_profiles SET haloPromptToolLoopProven = 1 WHERE id = :id AND artifactId = :artifactId AND artifactFingerprint = :artifactFingerprint AND deviceFingerprint = :deviceFingerprint AND runtime = :runtime AND backend = :backend AND contextSize IS :contextSize AND threads = :threads AND gpuLayers = :gpuLayers AND runtimeVersion = :runtimeVersion AND compatibilityEpoch = :compatibilityEpoch AND isStable = 1")
    suspend fun markPromptToolLoopProven(id: String, artifactId: String, artifactFingerprint: String, deviceFingerprint: String, runtime: String, backend: String, contextSize: Int?, threads: Int, gpuLayers: Int, runtimeVersion: String, compatibilityEpoch: Int): Int

    @Query("UPDATE model_runtime_profiles SET agenticContinuationProven = 1 WHERE id = :id AND artifactId = :artifactId AND artifactFingerprint = :artifactFingerprint AND deviceFingerprint = :deviceFingerprint AND runtime = :runtime AND backend = :backend AND contextSize IS :contextSize AND threads = :threads AND gpuLayers = :gpuLayers AND runtimeVersion = :runtimeVersion AND compatibilityEpoch = :compatibilityEpoch AND isStable = 1")
    suspend fun markAgenticContinuationProven(id: String, artifactId: String, artifactFingerprint: String, deviceFingerprint: String, runtime: String, backend: String, contextSize: Int?, threads: Int, gpuLayers: Int, runtimeVersion: String, compatibilityEpoch: Int): Int

    @Query("DELETE FROM model_runtime_profiles WHERE artifactId = :artifactId")
    suspend fun deleteByArtifactId(artifactId: String)
}
