package com.halo.app.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.host.android.AndroidStorage
import com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider
import com.halo.sdk.provider.Credential
import com.halo.app.ai.DefaultSpeechProviderSelector
import com.halo.app.ai.SpeechSelectionRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ElevenLabsConfigurationTest {
    @Test fun voiceModelStyleAndOutputSelectionSurviveProviderRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = UUID.randomUUID().toString()
        val storage = AndroidStorage(context, name)
        storage.putString("elevenlabs_tts_catalogue", """{"voices":[{"id":"voice-123","name":"Test voice"}],"models":[{"id":"model-123","name":"Test model"}],"at":1}""")
        val provider = ElevenLabsTtsProvider(storage)
        provider.setCredential(Credential(ElevenLabsTtsProvider.ID, "test-key"))
        provider.selectVoice("voice-123")
        provider.selectModel("model-123")
        provider.saveVoiceStyle(0.3, 0.8, 1.1)
        DefaultSpeechProviderSelector(SpeechSelectionRepository(storage)).selectTts(ElevenLabsTtsProvider.ID)
        val reopened = AndroidStorage(context, name)
        val restored = ElevenLabsTtsProvider(reopened)
        assertTrue(restored.state.value.configured)
        assertEquals("voice-123", restored.voiceId())
        assertEquals("model-123", restored.modelId())
        assertEquals(mapOf("stability" to 0.3, "similarity_boost" to 0.8, "speed" to 1.1), restored.voiceStyle())
        assertEquals(ElevenLabsTtsProvider.ID, DefaultSpeechProviderSelector(SpeechSelectionRepository(reopened)).ttsSelection.value.id)
        restored.clearCredential()
        assertFalse(ElevenLabsTtsProvider(AndroidStorage(context, name)).hasCredential())
    }
}
