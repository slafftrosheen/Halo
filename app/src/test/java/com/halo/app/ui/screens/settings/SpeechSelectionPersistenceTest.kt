package com.halo.app.ui.screens.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.app.ai.DefaultSpeechProviderSelector
import com.halo.app.ai.SpeechSelectionRepository
import com.halo.host.android.AndroidStorage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpeechSelectionPersistenceTest {
    @Test fun existingTinySelectionAndModelSwitchPersist() {
        val storage = AndroidStorage(ApplicationProvider.getApplicationContext<Context>())
        storage.putString(SpeechSelectionRepository.STT_KEY, "speech.sherpa_onnx.whisper_tiny")
        val original = DefaultSpeechProviderSelector(SpeechSelectionRepository(storage))
        assertEquals("speech.sherpa_onnx.whisper_tiny", original.sttSelection.value.id)
        original.selectStt("speech.sherpa_onnx.whisper_base")
        val restored = DefaultSpeechProviderSelector(SpeechSelectionRepository(storage))
        assertEquals("speech.sherpa_onnx.whisper_base", restored.sttSelection.value.id)
    }
}
