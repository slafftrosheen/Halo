package com.halo.providers.litert.models

import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.runtime.Storage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtModelPreferencesTest {
    @Test fun modelTuningPersistsWithoutChangingOtherModelsOrGlobalTemperature() {
        storage.putInt("ai_temperature", 30)
        val tuning = LiteRtUserTuning.preset(LiteRtTuningPreset.FAST).copy(cpuThreads = 2, streaming = false)
        preferences.setTuning(qwenControl.id, tuning)
        assertEquals(tuning, LiteRtModelPreferences(storage).tuning(qwenControl.id))
        assertEquals(256, preferences.generationDefaults(qwenControl).maxOutputTokens)
        assertEquals(64, preferences.generationDefaults(qwenControl, 64).maxOutputTokens)
        assertEquals(2, preferences.buildLoadConfig(qwenControl, descriptor(qwenControl)).cpuThreads)
        assertEquals(LiteRtUserTuning(), preferences.tuning(qwenLarge.id))
        preferences.setTuning(qwenControl.id, null)
        assertEquals(.3, preferences.generationDefaults(qwenControl).temperature, 1e-9)
        assertEquals(30, storage.getInt("ai_temperature", -1))
    }
    @Test fun invalidTuningNeverReachesStorageOrNativeSampling() {
        assertTrue(runCatching { LiteRtUserTuning(temperature = Double.NaN) }.isFailure)
        assertTrue(runCatching { LiteRtUserTuning(topP = 0.0) }.isFailure)
        assertTrue(runCatching { LiteRtUserTuning(topK = 0) }.isFailure)
        assertTrue(runCatching { LiteRtUserTuning(cpuThreads = 33) }.isFailure)
        assertTrue(runCatching { LiteRtUserTuning(maxOutputTokens = Int.MAX_VALUE) }.isFailure)
    }

    private val storage = InMemoryStorage()
    private val preferences = LiteRtModelPreferences(storage)

    private val qwenControl = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4
    private val qwenLarge = LiteRtModelCatalog.QWEN3_1_7B_DYNAMIC_INT4
    private val qwen25 = LiteRtModelCatalog.QWEN2_5_1_5B_INSTRUCT_Q8
    private val gemma = LiteRtModelCatalog.GEMMA3_1B_IT_INT4

    private fun descriptor(
        model: LiteRtCatalogModel,
        useModelDefaultContext: Boolean = false,
    ) = LocalModelDescriptor(
        id = model.id,
        displayName = model.displayName,
        path = "/tmp/${model.id}.litertlm",
        bytes = model.expectedBytes,
        modifiedAt = 0L,
        sha256 = model.sha256,
        contextWindowTokens = model.contextWindowTokens,
        supportsVision = model.supportsVision,
        supportsTools = model.supportsTools,
        validation = LocalModelValidation.VERIFIED_ARTIFACT,
        useModelDefaultContext = useModelDefaultContext,
    )

    @Test
    fun `no developer override falls back to the catalog profile`() {
        assertNull(preferences.developerBackend(qwenLarge.id))
        assertEquals(LocalComputeBackend.GPU, preferences.requestedBackend(qwenLarge))
        assertEquals(LocalComputeBackend.AUTO, preferences.requestedBackend(qwenControl))
    }

    @Test
    fun `a developer override outranks the catalog profile and persists`() {
        preferences.setDeveloperBackend(qwenLarge.id, LocalComputeBackend.CPU)
        assertEquals(LocalComputeBackend.CPU, preferences.requestedBackend(qwenLarge))
        // Re-reading from the same storage must survive a new instance (process restart).
        assertEquals(
            LocalComputeBackend.CPU,
            LiteRtModelPreferences(storage).developerBackend(qwenLarge.id),
        )

        // An explicit AUTO choice is a real choice, not "no preference".
        preferences.setDeveloperBackend(qwenLarge.id, LocalComputeBackend.AUTO)
        assertEquals(LocalComputeBackend.AUTO, preferences.requestedBackend(qwenLarge))

        preferences.clearDeveloperBackend(qwenLarge.id)
        assertEquals(LocalComputeBackend.GPU, preferences.requestedBackend(qwenLarge))
    }

    @Test
    fun `a profile that forbids cpu fallback is promoted to explicit gpu`() {
        val model = qwenControl.copy(
            id = "gpu-only-test",
            runtimeProfile = qwenControl.runtimeProfile.copy(
                preferredBackend = LocalComputeBackend.AUTO,
                allowCpuFallback = false,
            ),
        )
        assertEquals(LocalComputeBackend.GPU, preferences.requestedBackend(model))
    }

    @Test
    fun `load config uses the catalog profile context and backend`() {
        val config = preferences.buildLoadConfig(qwenLarge, descriptor(qwenLarge))
        assertEquals(LocalComputeBackend.GPU, config.computeBackend)
        assertEquals(4096, config.contextSize)
        assertFalse(config.useModelDefaultContext)

        val gemmaConfig = preferences.buildLoadConfig(gemma, descriptor(gemma))
        assertTrue(gemmaConfig.useModelDefaultContext)
    }

    @Test
    fun `a per-model context override wins over the profile`() {
        preferences.setContextOverride(qwenControl.id, 2048)
        val config = preferences.buildLoadConfig(qwenControl, descriptor(qwenControl))
        assertEquals(2048, config.contextSize)
        assertFalse(config.useModelDefaultContext)

        val gemmaOverride = preferences.buildLoadConfig(gemma, descriptor(gemma))
        assertTrue(gemmaOverride.useModelDefaultContext)
        preferences.setContextOverride(gemma.id, 4096)
        assertFalse(preferences.buildLoadConfig(gemma, descriptor(gemma)).useModelDefaultContext)
    }

    @Test
    fun `an unknown model falls back to safe Halo defaults`() {
        val imported = LocalModelDescriptor(
            id = "imported",
            displayName = "Imported",
            path = "/tmp/imported.litertlm",
            bytes = 1_200_000L,
            modifiedAt = 0L,
            sha256 = "a".repeat(64),
            contextWindowTokens = 4096,
            supportsVision = false,
            supportsTools = false,
            validation = LocalModelValidation.UNVERIFIED,
            useModelDefaultContext = true,
        )
        val config = preferences.buildLoadConfig(null, imported)
        assertEquals(LocalComputeBackend.AUTO, config.computeBackend)
        assertTrue(config.useModelDefaultContext)

        val defaults = preferences.generationDefaults(null)
        assertEquals(LiteRtModelPreferences.DEFAULT_MAX_OUTPUT_TOKENS, defaults.maxOutputTokens)
        assertEquals(LiteRtModelPreferences.DEFAULT_TEMPERATURE, defaults.temperature, 1e-9)
    }

    @Test
    fun `sampling defaults come from the profile and explicit request wins`() {
        val defaults = preferences.generationDefaults(qwen25)
        assertEquals(20, defaults.topK)
        assertEquals(0.8, defaults.topP, 1e-9)
        assertEquals(0.7, defaults.temperature, 1e-9)
        assertEquals(LiteRtModelPreferences.DEFAULT_MAX_OUTPUT_TOKENS, defaults.maxOutputTokens)

        val explicit = preferences.generationDefaults(qwen25, requestMaxOutputTokens = 16)
        assertEquals(16, explicit.maxOutputTokens)
    }

    @Test
    fun `a user temperature preference outranks the catalog profile`() {
        storage.putInt("ai_temperature", 30)
        assertEquals(0.3, preferences.generationDefaults(qwen25).temperature, 1e-9)
    }

    private class InMemoryStorage : Storage {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?): String? =
            (values[key] as? String) ?: defaultValue
        override fun putString(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
        }
        override fun getInt(key: String, defaultValue: Int): Int = (values[key] as? Int) ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            (values[key] as? Boolean) ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
