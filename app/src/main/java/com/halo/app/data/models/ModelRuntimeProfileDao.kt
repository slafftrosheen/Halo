package com.halo.app.data.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelRuntimeProfileDao {

    @Query("SELECT * FROM model_runtime_profiles WHERE artifactId = :artifactId AND deviceFingerprint = :deviceFingerprint")
    suspend fun getProfilesForModel(artifactId: String, deviceFingerprint: String): List<ModelRuntimeProfileEntity>

    @Query("SELECT * FROM model_runtime_profiles WHERE artifactId = :artifactId AND artifactFingerprint = :artifactFingerprint AND deviceFingerprint = :deviceFingerprint AND runtimeVersion = :runtimeVersion AND compatibilityEpoch = :compatibilityEpoch AND isStable = 1 ORDER BY ttftMs ASC, generationTokensPerSecond DESC, loadMs ASC LIMIT 1")
    suspend fun getBestProfile(
        artifactId: String,
        artifactFingerprint: String,
        deviceFingerprint: String,
        runtimeVersion: String,
        compatibilityEpoch: Int,
    ): ModelRuntimeProfileEntity?

    @Query("SELECT * FROM model_runtime_profiles WHERE id = :id")
    suspend fun getProfileById(id: String): ModelRuntimeProfileEntity?

    @Query("SELECT * FROM model_runtime_profiles")
    fun observeAll(): Flow<List<ModelRuntimeProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ModelRuntimeProfileEntity)

    @Query("DELETE FROM model_runtime_profiles WHERE artifactId = :artifactId")
    suspend fun deleteByArtifactId(artifactId: String)
}
