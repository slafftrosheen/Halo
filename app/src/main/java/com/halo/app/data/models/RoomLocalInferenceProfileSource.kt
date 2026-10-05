package com.halo.app.data.models

import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.runtime.cognition.context.LocalInferenceProfileSource
import com.halo.runtime.cognition.context.LocalInferenceRuntimeProfile
import com.halo.providers.litert.LiteRtLocalProvider.ProviderIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

/** Room and physical artifact verification stay entirely behind this app adapter. */
class RoomLocalInferenceProfileSource(
    private val inventory: LocalModelInventoryRepository,
    private val repository: ModelRuntimeProfileRepository,
    private val device: DeviceRuntimeIdentity,
    private val provider: () -> LiteRtLocalProvider?,
    private val healthTracker: com.halo.runtime.cognition.context.LocalInferenceRuntimeHealthTracker? = null,
    private val runtimeVersion: String = LiteRtLocalProvider.RUNTIME_VERSION,
    private val compatibilityEpoch: Int = COMPATIBILITY_EPOCH,
) : LocalInferenceProfileSource {
    fun runtimeHealth(providerId: String, modelId: String): com.halo.runtime.cognition.context.LocalInferenceRuntimeHealth? {
        val identity = provider()?.localExecutionIdentity(modelId) ?: return null
        return healthTracker?.snapshot(providerId, modelId, identity.split('|').getOrNull(2), identity)
    }
    val lastPolicy get() = healthTracker?.lastPolicy

    private data class Identity(val provider: String, val model: String, val sha: String,
        val device: String, val runtime: String, val epoch: Int, val backend: String,
        val context: Int, val threads: Int, val gpu: Int, val inventory: Long, val calibrationRevision: Long)
    private data class Cached(val entity: ModelRuntimeProfileEntity?, val atNs: Long)
    private val cache = LinkedHashMap<Identity, Cached>()
    private val mutex = kotlinx.coroutines.sync.Mutex()

    /** Consumes previously verified inventory; no resolve/refresh, file stat, or byte reads. */
    fun trustedArtifactIdentity(modelId: String): String? =
        (inventory.inventoryState.value as? com.halo.providers.litert.models.LocalModelInventoryState.Ready)
            ?.models?.firstOrNull { it.id == modelId && it.valid }?.sha256

    /** Explicit benchmark boundary. The model manager alone verifies physical bytes. */
    suspend fun artifactFingerprint(modelId: String): String? =
        inventory.verifyPhysicalArtifact(modelId)?.takeIf { it.valid }?.sha256

    suspend fun entity(providerId: String, modelId: String): ModelRuntimeProfileEntity? {
        if (providerId != ProviderIds.LITERT) return null
        val state = inventory.inventoryState.value as? com.halo.providers.litert.models.LocalModelInventoryState.Ready
            ?: return null
        val descriptor = state.models.firstOrNull { it.id == modelId && it.valid } ?: return null
        val fingerprint = descriptor.sha256 ?: return null
        val local = provider() ?: return null
        val resident = local.modelSession.takeIf { it.isLoaded() && it.loadedModelId == modelId }
        if (resident?.loadedArtifactSha256?.let { it != fingerprint } == true) return null
        val config = resident?.activeConfig ?: local.loadConfigFor(descriptor)
        val backend = resident?.activeBackend?.name ?: local.backendResolutionForDescriptor(descriptor).effective.name
        val context = resident?.effectiveContextSize ?: config.contextSize.takeUnless { config.useModelDefaultContext }
        if (context == null) return null
        val gpu = resident?.activeGpuLayers ?: if (backend == "CPU") 0 else config.gpuLayers
        val key = Identity(providerId, modelId, fingerprint, device.fingerprint, runtimeVersion, compatibilityEpoch,
            backend, context, config.cpuThreads, gpu, state.revision, repository.revision)
        return mutex.withLock {
            val cached = cache[key]
            if (cached != null && (cached.entity != null || System.nanoTime() - cached.atNs < 2_000_000_000L))
                return@withLock cached.entity
            val result = withContext(Dispatchers.IO) {
                repository.preferred(modelId, fingerprint, device.fingerprint, runtimeVersion,
                    compatibilityEpoch, "LITERT", backend, context, config.cpuThreads, gpu)
            }
            cache[key] = Cached(result, System.nanoTime())
            while (cache.size > 32) cache.remove(cache.keys.first())
            result
        }
    }

    override suspend fun profile(providerId: String, modelId: String): LocalInferenceRuntimeProfile? =
        entity(providerId, modelId)?.let { p ->
            LocalInferenceRuntimeProfile(providerId, modelId, p.backend, p.contextSize, p.loadMs, p.ttftMs,
                p.generationTokensPerSecond.toDouble(), p.peakEstimatedMemoryBytes, p.isStable, p.testedAtEpochMs)
        }

    companion object { const val COMPATIBILITY_EPOCH = 1 }
}
