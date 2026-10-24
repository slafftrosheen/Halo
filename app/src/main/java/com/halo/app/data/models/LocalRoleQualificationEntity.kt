package com.halo.app.data.models

import androidx.room.*
import com.halo.sdk.provider.*

/** Content-free history. No prompt, response, reasoning, tool arguments or exception fields. */
@Entity(tableName = "local_role_qualifications", indices = [Index(value = ["artifactId", "deviceFingerprint"])])
data class LocalRoleQualificationEntity(
    @PrimaryKey val id: String,
    val artifactId: String,
    val artifactFingerprint: String,
    val deviceFingerprint: String,
    val runtime: String,
    val runtimeVersion: String,
    val compatibilityEpoch: Int,
    val backend: String,
    val contextTokens: Int?,
    val threads: Int,
    val gpuLayers: Int,
    val probeSuiteVersion: Int,
    val testedAtMs: Long,
    val artifactBytes: Long,
    val loadMs: Long?,
    val coldTtftMs: Long?,
    val warmTtftSamples: String,
    val fastVerdict: String,
    val warmSamples: Int,
    val warmMedianMs: Long?,
    val chatVerdict: String,
    val chatPassed: Int,
    val chatTotal: Int,
    val reasoningVerdict: String,
    val reasoningPassed: Int,
    val reasoningTotal: Int,
    val agenticVerdict: String,
    val toolProtocol: String,
    val initialToolPass: Boolean,
    val continuationPass: Boolean,
    val visionVerdict: String,
    val verifiedVision: Boolean,
    val stable: Boolean,
    val memoryPressure: String,
    val failureCode: String?,
    @ColumnInfo(defaultValue = "NULL") val executionBuildIdentity: String? = null,
) {
    fun identity() = LocalRoleEvidenceIdentity(AiModelRef("provider.litert.local", artifactId),
        artifactFingerprint, deviceFingerprint, runtime, runtimeVersion, compatibilityEpoch,
        backend, contextTokens, threads, gpuLayers, probeSuiteVersion, executionBuildIdentity)
    fun evidence() = LocalRoleCandidateEvidence(identity(), testedAtMs, stable, memoryPressure,
        artifactBytes, loadMs, fastVerdict == "PASS", warmSamples, warmMedianMs,
        chatPassed, chatTotal, reasoningPassed, reasoningTotal, agenticVerdict == "PASS",
        ToolCallProtocol.entries.firstOrNull { it.name == toolProtocol } ?: ToolCallProtocol.NONE,
        continuationPass, verifiedVision)
}

@Dao
interface LocalRoleQualificationDao {
    @Insert suspend fun insert(row: LocalRoleQualificationEntity)
    // This table is append-only. SQLite insertion order survives owner recreation;
    // user-adjustable wall time and random UUIDs cannot determine the newest attempt.
    @Query("SELECT * FROM local_role_qualifications WHERE deviceFingerprint = :device ORDER BY rowid DESC")
    fun observeDevice(device: String): kotlinx.coroutines.flow.Flow<List<LocalRoleQualificationEntity>>
    @Query("SELECT * FROM local_role_qualifications WHERE deviceFingerprint = :device ORDER BY rowid DESC")
    suspend fun deviceHistory(device: String): List<LocalRoleQualificationEntity>
    @Query("SELECT * FROM local_role_qualifications WHERE deviceFingerprint = :device AND artifactId = :model ORDER BY rowid DESC")
    suspend fun history(device: String, model: String): List<LocalRoleQualificationEntity>
}
