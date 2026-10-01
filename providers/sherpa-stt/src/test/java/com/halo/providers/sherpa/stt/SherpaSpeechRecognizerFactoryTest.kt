package com.halo.providers.sherpa.stt

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SherpaSpeechRecognizerFactoryTest {
    @Test fun sharedFactoryUsesDescriptorAndExplicitLanguageWithoutLoadingNativeModel() {
        for (language in listOf("lv", "auto")) {
            val provider = SherpaSpeechRecognizerFactory.create(File("not-installed"), SherpaSpeechModelCatalog.whisperBase, language, null)
                as SherpaOfflineSpeechRecognitionProvider
            assertEquals(SherpaSpeechModelCatalog.whisperBase.providerId, provider.metadata.id)
            assertFalse(provider.isResident)
            assertEquals(if (language == "auto") "" else "lv", provider.createModelConfig().whisper.language)
        }
    }
    @Test fun sharedFactoryRejectsUnsupportedLanguageBeforeModelConstruction() {
        try {
            SherpaSpeechRecognizerFactory.create(File("not-installed"), SherpaSpeechModelCatalog.whisperBase, "unsupported", null)
            fail("Expected unsupported language")
        } catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("does not support")) }
    }
}
