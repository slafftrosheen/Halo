package com.halo.app.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.engines.speech.UnavailableSpeechRecognitionProvider
import com.halo.providers.litert.models.LiteRtModelManager
import com.halo.providers.sherpa.stt.ReloadableSpeechRecognitionProvider
import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.host.AudioFrame
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.BufferedSpeechRecognitionProvider
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.RecognitionResult
import com.halo.sdk.provider.SpeechRequest
import com.halo.sdk.provider.SpeechResult
import com.halo.sdk.provider.SpeechSynthesisProvider
import com.halo.sdk.provider.WakeWordDetector
import com.halo.sdk.provider.WakeWordResult
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
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

    private fun createFactory(): AndroidProviderFactory {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ManualDependencyGraph.init(context)
        return AndroidProviderFactory(
            context = context,
            modelManager = LiteRtModelManager(context),
            storage = FakeStorage(),
            nativeResourceCoordinator = NativeResourceCoordinator()
        )
    }

    @Test
    fun `inference provider is identical object instance in aiProviders`() {
        val providers = createFactory().createProviders()
        val inference = providers.inference
        val aiProvider = providers.aiProviders.first { it.id == inference.id }

        assertSame(inference, aiProvider)
    }

    @Test
    fun `registry preserves typed inference boundary`() {
        val providers = createFactory().createProviders()

        assertTrue(providers.aiRegistry.inference.any { it === providers.inference })
        assertTrue(providers.aiRegistry.inference.all { provider ->
            providers.aiProviders.any { it === provider }
        })
        assertTrue(providers.aiRegistry.handoff.all { provider ->
            providers.aiProviders.any { it === provider }
        })
    }

    @Test
    fun `default speech path never falls back to system recognizer`() = runBlocking {
        val speech = createFactory().createProviders().speechRecognition
        val activeSpeech = (speech as? ReloadableSpeechRecognitionProvider)?.delegate ?: speech

        assertTrue(activeSpeech is UnavailableSpeechRecognitionProvider || activeSpeech.health())
        if (activeSpeech is UnavailableSpeechRecognitionProvider) {
            assertFalse(activeSpeech.health())
            assertFalse(speech.health())
        }
    }

    @Test
    fun `default Providers construction retains the typed inference provider`() {
        val inference = object : AiInferenceProvider {
            override val id: String = "test-inference"
            override val displayName: String = "Test inference"
            override val isLocal: Boolean = true
            override val status = MutableStateFlow(ProviderStatus.READY)
            override suspend fun getCapabilities() = setOf(AiCapability.TEXT)
            override suspend fun generate(
                request: AiRequest,
                context: AiContext
            ) = AiResponse(
                text = request.text.orEmpty(),
                providerId = id,
                modelId = request.modelId
            )
            override fun stream(
                request: AiRequest,
                context: AiContext
            ) = emptyFlow<AiStreamEvent>()
            override suspend fun listModels() = emptyList<AiModel>()
        }

        val providers = Providers(
            inference = inference,
            speechRecognition = object : BufferedSpeechRecognitionProvider {
                override suspend fun recognize(audio: AudioBuffer) =
                    RecognitionResult("", "en", 0f)
                override suspend fun health() = true
            },
            speechSynthesis = object : SpeechSynthesisProvider {
                override suspend fun synthesize(request: SpeechRequest) =
                    SpeechResult(0, true)
                override fun stream(text: String) = emptyFlow<AudioFrame>()
                override suspend fun health() = true
            },
            wakeWord = object : WakeWordDetector {
                override fun process(frame: AudioFrame) =
                    WakeWordResult(detected = false)
                override suspend fun health() = true
            }
        )

        assertSame(inference, providers.aiProviders.first())
        assertSame(inference, providers.aiRegistry.inference.first())
    }

    @Test
    fun `explicit aiProviders cannot accidentally remove typed inference provider`() {
        val inference = object : AiInferenceProvider {
            override val id: String = "typed-inference"
            override val displayName: String = "Typed inference"
            override val isLocal: Boolean = true
            override val status = MutableStateFlow(ProviderStatus.READY)
            override suspend fun getCapabilities() = setOf(AiCapability.TEXT)
            override suspend fun generate(
                request: AiRequest,
                context: AiContext
            ) = AiResponse(
                text = request.text.orEmpty(),
                providerId = id,
                modelId = request.modelId
            )
            override fun stream(
                request: AiRequest,
                context: AiContext
            ) = emptyFlow<AiStreamEvent>()
            override suspend fun listModels() = emptyList<AiModel>()
        }

        val providers = Providers(
            inference = inference,
            speechRecognition = object : BufferedSpeechRecognitionProvider {
                override suspend fun recognize(audio: AudioBuffer) =
                    RecognitionResult("", "en", 0f)
                override suspend fun health() = true
            },
            speechSynthesis = object : SpeechSynthesisProvider {
                override suspend fun synthesize(request: SpeechRequest) =
                    SpeechResult(0, true)
                override fun stream(text: String) = emptyFlow<AudioFrame>()
                override suspend fun health() = true
            },
            wakeWord = object : WakeWordDetector {
                override fun process(frame: AudioFrame) =
                    WakeWordResult(detected = false)
                override suspend fun health() = true
            },
            aiProviders = emptyList()
        )

        assertTrue(providers.aiProviders.isEmpty())
        assertSame(inference, providers.aiRegistry.inference.single())
    }
}
