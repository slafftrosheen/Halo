package com.halo.app.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.providers.llamacpp.ModelDownloadManager
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.runtime.Storage
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidProviderFactoryTest {

    private class FakeStorage : Storage {
        private val data = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = data[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { data[key] = value }
        override fun getInt(key: String, defaultValue: Int) = data[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = data[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }

    @Test
    fun `inference provider is identical object instance in aiProviders`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = FakeStorage()
        val downloadManager = ModelDownloadManager(context)
        val factory = AndroidProviderFactory(context, downloadManager, storage)

        val providers = factory.createProviders()
        val inference = providers.inference
        val aiProvider = providers.aiProviders.first { it.id == inference.id }

        assertSame(inference, aiProvider)
    }

    @Test
    fun `registry preserves typed inference boundary`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = FakeStorage()
        val downloadManager = ModelDownloadManager(context)
        val providers = AndroidProviderFactory(context, downloadManager, storage).createProviders()

        assertTrue(providers.aiRegistry.inference.any { it === providers.inference })
        assertTrue(providers.aiRegistry.inference.all { provider ->
            providers.aiProviders.any { it === provider }
        })
        assertTrue(providers.aiRegistry.handoff.all { provider ->
            providers.aiProviders.any { it === provider }
        })
    }

    @Test
    fun `default Providers construction retains the typed inference provider`() {
        val inference = object : AiInferenceProvider {
            override val id: String = "test-inference"
            override val displayName: String = "Test inference"
            override val isLocal: Boolean = true
            override val status = kotlinx.coroutines.flow.MutableStateFlow(com.halo.sdk.provider.ProviderStatus.READY)
            override suspend fun getCapabilities() = setOf(com.halo.sdk.provider.AiCapability.TEXT)
            override suspend fun generate(
                request: com.halo.sdk.provider.AiRequest,
                context: com.halo.sdk.provider.AiContext
            ) = com.halo.sdk.provider.AiResponse(
                text = request.text.orEmpty(),
                providerId = id,
                modelId = request.modelId
            )
            override fun stream(
                request: com.halo.sdk.provider.AiRequest,
                context: com.halo.sdk.provider.AiContext
            ) = kotlinx.coroutines.flow.emptyFlow<com.halo.sdk.provider.AiStreamEvent>()
            override suspend fun listModels() = emptyList<com.halo.sdk.provider.AiModel>()
        }

        val providers = Providers(
            inference = inference,
            speechRecognition = error("unused in this test"),
            speechSynthesis = error("unused in this test"),
            wakeWord = error("unused in this test")
        )

        assertSame(inference, providers.aiProviders.single())
        assertSame(inference, providers.aiRegistry.inference.single())
    }
}
