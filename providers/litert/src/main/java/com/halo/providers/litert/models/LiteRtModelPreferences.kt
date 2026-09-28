package com.halo.providers.litert.models

import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.runtime.Storage

/**
 * Effective sampling/output defaults for one artifact.
 *
 * These are *defaults*, never forced values: an explicit request value still wins.
 */
data class LiteRtGenerationDefaults(
    val maxOutputTokens: Int,
    val temperature: Double,
    val topP: Double,
    val topK: Int,
)

/**
 * Persisted, per-model developer preferences.
 *
 * Development needs to know *which* accelerator is broken. AUTO/GPU/CPU must therefore be
 * selectable per model from the Model Browser instead of being buried in an adb-only override, and
 * it must survive process restarts.
 *
 * Resolution order (highest first):
 *   1. explicit request override (handled by the caller)
 *   2. user/developer model preference (this class)
 *   3. catalog runtime profile
 *   4. Halo safe default
 */
class LiteRtModelPreferences(private val storage: Storage) {

    /** Stored developer override for [modelId], or null when the developer has not chosen one. */
    fun developerBackend(modelId: String): LocalComputeBackend? =
        storage.getString(backendKey(modelId))
            ?.trim()
            ?.uppercase()
            ?.let { stored -> LocalComputeBackend.entries.firstOrNull { it.name == stored } }

    fun setDeveloperBackend(modelId: String, backend: LocalComputeBackend) {
        storage.putString(backendKey(modelId), backend.name)
    }

    fun clearDeveloperBackend(modelId: String) {
        storage.remove(backendKey(modelId))
    }

    /**
     * The backend Halo will actually ask for.
     *
     * Explicit GPU means "do not silently CPU fallback". Explicit CPU means "never initialize GPU".
     * A profile that forbids CPU fallback is promoted to explicit GPU so the runtime's AUTO
     * fallback path can never contradict the model profile.
     */
    fun requestedBackend(model: LiteRtCatalogModel): LocalComputeBackend {
        developerBackend(model.id)?.let { return it }
        val profile = model.runtimeProfile
        return when {
            profile.preferredBackend != LocalComputeBackend.AUTO -> profile.preferredBackend
            !profile.allowCpuFallback -> LocalComputeBackend.GPU
            else -> LocalComputeBackend.AUTO
        }
    }

    /** Per-model context override, or null when the catalog profile decides. */
    fun contextOverride(modelId: String): Int? =
        storage.getInt(contextKey(modelId), NO_OVERRIDE).takeIf { it > 0 }

    fun setContextOverride(modelId: String, tokens: Int?) {
        if (tokens == null || tokens <= 0) storage.remove(contextKey(modelId))
        else storage.putInt(contextKey(modelId), tokens)
    }

    /**
     * Builds the runtime load configuration for [descriptor], preferring the catalog entry when the
     * artifact is known.
     */
    fun buildLoadConfig(
        model: LiteRtCatalogModel?,
        descriptor: LocalModelDescriptor,
    ): RuntimeLoadConfig {
        val profile = model?.runtimeProfile
        val userContext = model?.let { contextOverride(it.id) }
        val useModelDefault = userContext == null &&
            (profile?.useModelDefaultContext ?: descriptor.useModelDefaultContext)
        val contextTokens = userContext
            ?: profile?.knownContextTokens
            ?: descriptor.contextWindowTokens
        val backend = model?.let(::requestedBackend) ?: LocalComputeBackend.AUTO
        return RuntimeLoadConfig(
            contextSize = contextTokens,
            computeBackend = backend,
            useModelDefaultContext = useModelDefault,
        )
    }

    /**
     * Sampling/output defaults for one artifact.
     *
     * A user temperature preference (the existing intelligence slider) outranks the catalog
     * profile; the catalog profile outranks the Halo default.
     */
    fun generationDefaults(model: LiteRtCatalogModel?, requestMaxOutputTokens: Int? = null): LiteRtGenerationDefaults {
        val profile = model?.runtimeProfile
        val userTemperature = storage.getInt(USER_TEMPERATURE_KEY, NO_OVERRIDE)
            .takeIf { it >= 0 }
            ?.let { it / 100.0 }
        return LiteRtGenerationDefaults(
            maxOutputTokens = requestMaxOutputTokens
                ?: profile?.defaultMaxOutputTokens
                ?: DEFAULT_MAX_OUTPUT_TOKENS,
            temperature = userTemperature ?: profile?.defaultTemperature ?: DEFAULT_TEMPERATURE,
            topP = profile?.defaultTopP ?: DEFAULT_TOP_P,
            topK = profile?.defaultTopK ?: DEFAULT_TOP_K,
        )
    }

    private fun backendKey(modelId: String) = "litert_dev_backend_$modelId"

    private fun contextKey(modelId: String) = "litert_context_$modelId"

    companion object {
        private const val NO_OVERRIDE = Int.MIN_VALUE
        private const val USER_TEMPERATURE_KEY = "ai_temperature"

        const val DEFAULT_MAX_OUTPUT_TOKENS = 512
        const val DEFAULT_TEMPERATURE = 0.7
        const val DEFAULT_TOP_P = 0.9
        const val DEFAULT_TOP_K = 40
    }
}
