package com.halo.providers.sherpa.stt

import com.halo.sdk.provider.*
import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.runtime.HaloDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ReloadableModelSelectionTest {
    @Test fun failedConstructionCannotRetainClosedReadyDelegateAndSelectionCanRecover() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val selector = Selector()
        var fail = true
        val provider = ReloadableSpeechRecognitionProvider(File("test/sherpa-whisper-tiny"), selector = selector,
            dispatchers = HaloDispatchers(dispatcher, dispatcher, dispatcher), installed = { _, _ -> true },
            delegateFactory = { _, spec, language -> if (fail) error("Initialization failed") else Fake(spec, language) })
        runCurrent()
        assertFalse(provider.availability.value)
        assertEquals("Failed", provider.readiness.value.phase)
        fail = false
        provider.refresh(); runCurrent()
        assertTrue(provider.health())
        assertEquals("Installed", provider.readiness.value.phase)
        assertEquals("whisper-tiny", provider.readiness.value.modelId)
        provider.close()
    }
    private class Selector : SpeechProviderSelector {
        override val sttSelection = MutableStateFlow(SpeechProviderSelection("speech.sherpa_onnx.whisper_tiny"))
        override val ttsSelection = MutableStateFlow(SpeechProviderSelection("AUTOMATIC"))
        override fun selectStt(id: String) { sttSelection.value = SpeechProviderSelection(id) }
        override fun selectTts(id: String) { ttsSelection.value = SpeechProviderSelection(id) }
    }
    private class Fake(val spec: SherpaSpeechModelCatalog.ModelSpec, val language: String) : BufferedSpeechRecognitionProvider {
        var closed = false
        override val metadata = ProviderMetadata(id = spec.providerId)
        override suspend fun health() = !closed
        override suspend fun close() { closed = true }
        override suspend fun recognize(audio: AudioBuffer) = RecognitionResult(spec.id, language)
    }
    @Test fun switchClosesPreviousBeforeCreatingAndLanguageInvalidatesOnce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val selector = Selector()
        val language = MutableStateFlow("en")
        val created = mutableListOf<Fake>()
        val provider = ReloadableSpeechRecognitionProvider(File("test/sherpa-whisper-tiny"),
            getLanguage = { language.value }, selector = selector, languageChanges = language,
            dispatchers = HaloDispatchers(dispatcher, dispatcher, dispatcher), installed = { _, _ -> true },
            delegateFactory = { _, spec, lang ->
                assertTrue(created.lastOrNull()?.closed != false)
                Fake(spec, lang).also { created.add(it) }
            })
        runCurrent()
        assertEquals(1, created.size)
        assertEquals("speech.sherpa_onnx.whisper_tiny", provider.metadata.id)
        selector.selectStt(SherpaSpeechModelCatalog.whisperBase.providerId); runCurrent()
        assertTrue(created[0].closed)
        assertEquals(SherpaSpeechModelCatalog.whisperBase.providerId, provider.metadata.id)
        selector.selectStt(SherpaSpeechModelCatalog.whisperSmall.providerId); runCurrent()
        assertTrue(created[1].closed)
        language.value = "lv"; runCurrent()
        assertTrue(created[2].closed); assertEquals("lv", created.last().language)
        val count = created.size
        provider.refresh(); runCurrent(); assertEquals(count, created.size)
        provider.close(); assertTrue(created.last().closed)
    }
    @Test fun explicitMissingReportsUnavailable() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val selector = Selector().also { it.selectStt(SherpaSpeechModelCatalog.whisperSmall.providerId) }
        val provider = ReloadableSpeechRecognitionProvider(File("test/sherpa-whisper-tiny"), selector = selector,
            dispatchers = HaloDispatchers(dispatcher, dispatcher, dispatcher),
            installed = { _, spec -> spec == SherpaSpeechModelCatalog.whisperTiny })
        runCurrent()
        assertFalse(provider.health())
        assertEquals("Selected speech model is not installed.", provider.healthDetails().message)
        assertEquals(SherpaSpeechModelCatalog.whisperSmall.providerId, selector.sttSelection.value.id)
        provider.close()
    }
}
