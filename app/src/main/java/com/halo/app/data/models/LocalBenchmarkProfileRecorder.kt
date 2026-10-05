package com.halo.app.data.models

import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.runtime.LocalModelBenchmarkResult
import com.halo.providers.litert.runtime.LocalModelBenchmarkFailure

/** Calibration records contain only identity, numbers and bounded failure codes. */
class LocalBenchmarkProfileRecorder(
    private val repository: ModelRuntimeProfileRepository,
    private val source: RoomLocalInferenceProfileSource,
    private val device: DeviceRuntimeIdentity,
) {
    suspend fun record(result: LocalModelBenchmarkResult, verifiedBefore: String?) {
        val actual = source.artifactFingerprint(result.modelId) ?: return
        if (verifiedBefore == null || actual != verifiedBefore || result.artifactSha256?.let { !it.equals(actual, ignoreCase = true) } == true) return
        val stable = result.responseText.isNotBlank() && result.responseChars > 0
        save(result.modelId, actual, result.effectiveBackend.name, result.effectiveContextTokens,
            result.cpuThreads, result.gpuLayers, result.loadDurationMs, result.timeToFirstTokenMs,
            result.estimatedTokensPerSecond.toFloat(), result.peakHeapBytes, stable,
            if (stable) null else "GENERATION_FAILED")
    }

    suspend fun failure(modelId: String, verifiedBefore: String?, error: Throwable) {
        if (verifiedBefore == null || source.artifactFingerprint(modelId) != verifiedBefore) return
        val evidence = error as? LocalModelBenchmarkFailure ?: return
        save(modelId, verifiedBefore, evidence.backend?.name ?: "UNKNOWN", evidence.contextTokens,
            evidence.cpuThreads, evidence.gpuLayers, 0, 0, 0f, null, false, evidence.code.name)
    }

    private suspend fun save(modelId: String, sha: String, backend: String, context: Int?, threads: Int,
        gpuLayers: Int, load: Long, ttft: Long, throughput: Float, memory: Long?, stable: Boolean, failure: String?) {
        val identity = listOf(modelId, sha, device.fingerprint, "LITERT", backend, context, threads, gpuLayers,
            LiteRtLocalProvider.RUNTIME_VERSION, RoomLocalInferenceProfileSource.COMPATIBILITY_EPOCH, stable).joinToString("|")
        repository.save(ModelRuntimeProfileEntity(
            id = DeviceRuntimeIdentity.hash(identity), artifactId = modelId, artifactFingerprint = sha,
            deviceFingerprint = device.fingerprint, runtime = "LITERT", backend = backend, contextSize = context,
            threads = threads, gpuLayers = gpuLayers, loadMs = load, ttftMs = ttft,
            promptTokensPerSecond = 0f, generationTokensPerSecond = throughput,
            peakEstimatedMemoryBytes = memory, textGenerationProven = stable, isStable = stable,
            failureReason = failure, appVersion = com.halo.app.BuildConfig.VERSION_NAME, runtimeVersion = LiteRtLocalProvider.RUNTIME_VERSION,
            compatibilityEpoch = RoomLocalInferenceProfileSource.COMPATIBILITY_EPOCH,
        ))
    }
}
