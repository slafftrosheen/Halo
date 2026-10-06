package com.halo.app.data.models

import com.halo.providers.litert.runtime.*
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.sdk.provider.*
import java.util.UUID

/** Explicit completed-suite boundary; never called by inference or discovery. */
class LocalRoleQualificationRepository(private val dao: LocalRoleQualificationDao,
    val device: DeviceRuntimeIdentity) {
    val history = dao.observeDevice(device.fingerprint)
    suspend fun save(row: LocalModelRoleQualification): Boolean {
        if (row.failure in setOf(LocalRoleProbeFailure.CANCELLED, LocalRoleProbeFailure.ARTIFACT_CHANGED,
                LocalRoleProbeFailure.RESOURCE_BUSY, LocalRoleProbeFailure.ARTIFACT_UNAVAILABLE) ||
            !row.artifactFingerprint.matches(Regex("[0-9a-f]{64}")) || row.backend == null ||
            row.threads == null || row.gpuLayers == null) return false
        val backend = row.backend ?: return false
        val threads = row.threads ?: return false
        val gpuLayers = row.gpuLayers ?: return false
        val vision = row.vision
        val pressure = row.memoryPressure?.takeIf { it in setOf("NORMAL", "LOW", "CRITICAL") } ?: "NORMAL"
        val unstable = row.failure != null || row.fast?.verdict != LocalRoleProbeVerdict.PASS ||
            row.chat?.failure == LocalRoleProbeFailure.GENERATION_FAILED ||
            row.reasoning?.failure == LocalRoleProbeFailure.GENERATION_FAILED ||
            row.agentic?.failure == LocalRoleProbeFailure.GENERATION_FAILED || pressure == "CRITICAL"
        dao.insert(LocalRoleQualificationEntity(UUID.randomUUID().toString(), row.modelId,
            row.artifactFingerprint, device.fingerprint, "LITERT", LiteRtLocalProvider.RUNTIME_VERSION,
            LOCAL_ROLE_QUALIFICATION_COMPAT_EPOCH, backend.name, row.contextTokens, threads,
            gpuLayers, ROLE_PROBE_SUITE_VERSION, row.completedAtMs, row.artifactBytes, row.loadMs,
            row.fast?.coldTtftMs, row.fast?.warmTtftMs?.joinToString(",").orEmpty(),
            row.fast?.verdict?.name ?: "NOT_RUN", row.fast?.successfulWarmSamples ?: 0,
            row.fast?.medianWarmTtftMs, row.chat?.verdict?.name ?: "NOT_RUN", row.chat?.passed ?: 0,
            row.chat?.total ?: 0, row.reasoning?.verdict?.name ?: "NOT_RUN", row.reasoning?.passed ?: 0,
            row.reasoning?.total ?: 0, row.agentic?.verdict?.name ?: "NOT_RUN",
            row.agentic?.protocol?.name ?: "NONE", row.agentic?.initialToolPass ?: false,
            row.agentic?.continuationPass ?: false, row.vision?.verdict?.name ?: "NOT_APPLICABLE",
            vision?.verdict == LocalRoleProbeVerdict.PASS && vision.eligibility == "VISION_LAB_PROVEN",
            !unstable, pressure, row.failure?.name ?: row.fast?.failure?.name ?: row.chat?.failure?.name ?: row.reasoning?.failure?.name ?: row.agentic?.failure?.name))
        return true
    }
}
