package com.halo.app.data.models

import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.runtime.cognition.context.LocalInferenceProfileSource
import com.halo.runtime.cognition.context.LocalInferenceRuntimeProfile
import com.halo.providers.litert.LiteRtLocalProvider.ProviderIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Room and physical artifact verification stay entirely behind this app adapter. */
class RoomLocalInferenceProfileSource(
    private val inventory: LocalModelInventoryRepository,
    private val repository: ModelRuntimeProfileRepository,
    private val device: DeviceRuntimeIdentity,
    private val provider: () -> LiteRtLocalProvider?,
    private val runtimeVersion: String = LiteRtLocalProvider.RUNTIME_VERSION,
    private val compatibilityEpoch: Int = COMPATIBILITY_EPOCH,
) : LocalInferenceProfileSource {
    suspend fun artifactFingerprint(modelId: String): String? = withContext(Dispatchers.IO) {
        try {
            val descriptor = inventory.resolveModel(modelId)?.takeIf { it.valid } ?: return@withContext null
            val file = File(descriptor.path)
            if (!file.isFile || file.length() != descriptor.bytes) return@withContext null
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    kotlin.coroutines.coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            actual.takeIf { descriptor.sha256 == null || actual.equals(descriptor.sha256, ignoreCase = true) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: java.io.IOException) { null }
    }

    suspend fun entity(providerId: String, modelId: String): ModelRuntimeProfileEntity? = withContext(Dispatchers.IO) {
        if (providerId != ProviderIds.LITERT) return@withContext null
        if (!repository.hasCompatibleCalibration(modelId, device.fingerprint, "LITERT", runtimeVersion, compatibilityEpoch)) return@withContext null
        val fingerprint = artifactFingerprint(modelId) ?: return@withContext null
        val local = provider() ?: return@withContext null
        val descriptor = inventory.resolveModel(modelId) ?: return@withContext null
        val resident = local.modelSession.takeIf { it.isLoaded() && it.loadedModelId == modelId }
        val config = resident?.activeConfig ?: local.loadConfigFor(descriptor)
        val backend = resident?.activeBackend?.name ?: local.backendResolutionForModel(modelId)?.effective?.name ?: return@withContext null
        val context = resident?.effectiveContextSize ?: config.contextSize.takeUnless { config.useModelDefaultContext }
        if (context == null) return@withContext null
        repository.preferred(modelId, fingerprint, device.fingerprint, runtimeVersion, compatibilityEpoch, "LITERT", backend,
            context, config.cpuThreads, resident?.activeGpuLayers ?: if (backend == "CPU") 0 else config.gpuLayers)
    }

    override suspend fun profile(providerId: String, modelId: String): LocalInferenceRuntimeProfile? =
        entity(providerId, modelId)?.let { p ->
            LocalInferenceRuntimeProfile(providerId, modelId, p.backend, p.contextSize, p.loadMs, p.ttftMs,
                p.generationTokensPerSecond.toDouble(), p.peakEstimatedMemoryBytes, p.isStable, p.testedAtEpochMs)
        }

    companion object { const val COMPATIBILITY_EPOCH = 1 }
}
