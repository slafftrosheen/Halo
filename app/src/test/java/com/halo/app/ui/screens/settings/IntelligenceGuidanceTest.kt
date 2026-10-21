package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.ai.GenerationProof
import com.halo.app.ai.ProviderConnectionState
import com.halo.app.ui.theme.HostlessHaloTheme as HaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class IntelligenceGuidanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rawModelIdentifierIsNeverTheUserFacingSummary() {
        assertEquals("No model selected", intelligenceModelSummary(null))
        assertEquals("No model selected", intelligenceModelSummary(" "))
        assertEquals("Model selected", intelligenceModelSummary("provider/internal/sha123"))
    }

    @Test fun readinessNeverSkipsMissingConfigurationConnectionOrModel() {
        assertEquals(IntelligenceStep.CONFIGURE, intelligenceNextStep(false, ProviderConnectionState.READY, true, GenerationProof.PROVEN))
        assertEquals(IntelligenceStep.CHECK_CONNECTION, intelligenceNextStep(true, ProviderConnectionState.OFFLINE, true, GenerationProof.PROVEN))
        assertEquals(IntelligenceStep.CHOOSE_MODEL, intelligenceNextStep(true, ProviderConnectionState.READY, false, GenerationProof.PROVEN))
        assertEquals(IntelligenceStep.TEST_REPLY, intelligenceNextStep(true, ProviderConnectionState.READY, true, GenerationProof.FAILED))
        assertEquals(IntelligenceStep.WAIT, intelligenceNextStep(true, ProviderConnectionState.READY, true, GenerationProof.CHECKING))
    }

    @Test fun setupOnNarrowLargeTextScreenIsExplicitAndMakesNoProviderCall() {
        val calls = mutableListOf<String>()
        compose.setContent { HaloTheme(darkTheme = true, fontScale = 2f) {
            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                IntelligenceGuidance(IntelligenceStep.CONFIGURE, { calls += "setup" }, { calls += "models" }, { calls += "connection" }, { calls += "reply" })
            }
        } }
        assertTrue(calls.isEmpty())
        compose.onNodeWithText("Set up answers").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("setup"), calls)
    }

    @Test fun replyCheckIsExplicitAndRunningCheckCannotBeStartedAgain() {
        val step = mutableStateOf(IntelligenceStep.TEST_REPLY)
        var replies = 0
        compose.setContent { HaloTheme(darkTheme = false) {
            IntelligenceGuidance(step.value, {}, {}, {}, { replies++; step.value = IntelligenceStep.WAIT })
        } }
        assertEquals(0, replies)
        compose.onNodeWithText("Check answers").performClick()
        compose.onNodeWithText("Check answers").assertDoesNotExist()
        compose.onNodeWithText("Check connection").assertDoesNotExist()
        assertEquals(1, replies)
    }

    @Test fun providerProofDoesNotPromiseEveryModelOrFutureRequest() {
        compose.setContent { HaloTheme { IntelligenceGuidance(IntelligenceStep.VERIFIED, {}, {}, {}, {}) } }
        compose.onNodeWithText("halo answered").assertExists()
        compose.onNodeWithText("halo answered with this choice. Future requests show their own result.").assertExists()
        compose.onNodeWithText("Check answers").assertDoesNotExist()
    }
}
