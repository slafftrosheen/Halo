package com.halo.app.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.halo.app.ai.DefaultSpeechProviderSelector
import com.halo.app.ai.SpeechSelectionRepository
import com.halo.app.ui.theme.HostlessHaloTheme as HaloTheme
import com.halo.host.android.AndroidStorage
import com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider
import com.halo.sdk.provider.Credential
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ElevenLabsSettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun chooseVoiceModelAndEnableOutputFromConfigurationScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = UUID.randomUUID().toString()
        val storage = AndroidStorage(context, name)
        storage.putString(
            "elevenlabs_tts_catalogue",
            """{"voices":[{"id":"voice-123","name":"Test voice"}],"models":[{"id":"model-123","name":"Test model"}],"at":1}""",
        )
        val provider = ElevenLabsTtsProvider(storage)
        provider.setCredential(Credential(ElevenLabsTtsProvider.ID, "placeholder-secret"))
        provider.reloadConfiguration()
        val selector = DefaultSpeechProviderSelector(SpeechSelectionRepository(storage))

        compose.setContent {
            HaloTheme(darkTheme = false) {
                ElevenLabsSettingsScreen(
                    provider,
                    onBack = {},
                    onTestVoice = {},
                    onUseProvider = { selector.selectTts(provider.metadata.id) },
                )
            }
        }

        compose.waitUntil(5_000) { provider.state.value.catalogue != null }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText = "Test model")
        compose.onNodeWithText("Test model").performClick()
        compose.waitUntil(5_000) { provider.state.value.modelId == "model-123" }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText = "Test voice")
        compose.onNodeWithText("Test voice").performClick()
        compose.waitUntil(5_000) { provider.state.value.voiceId == "voice-123" }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText = "Use ElevenLabs for speech output")
        compose.onNodeWithText("Use ElevenLabs for speech output").performClick()
        compose.waitUntil(5_000) { selector.ttsSelection.value.id == ElevenLabsTtsProvider.ID }

        val restored = DefaultSpeechProviderSelector(SpeechSelectionRepository(AndroidStorage(context, name)))
        assertEquals(ElevenLabsTtsProvider.ID, restored.ttsSelection.value.id)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performScrollToNode(hasText: String) {
        performScrollToNode(androidx.compose.ui.test.hasText(hasText))
    }
}
