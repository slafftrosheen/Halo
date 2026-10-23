package com.halo.app.ui.components

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.R
import com.halo.app.ai.OnlineFeature
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.sdk.provider.RemoteDataCategory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
class ModeSharingVisualTest {
    @get:Rule val compose = createComposeRule()
    @Test fun english360() = capture()
    @Test @Config(qualifiers = "en-rUS-w412dp-h850dp") fun dark412() = capture(dark = true)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun russian200() = capture(scale = 2f)
    @Test @Config(qualifiers = "lv-rLV-w412dp-h850dp") fun latvian200Dark() = capture(scale = 2f, dark = true)
    @Test @Config(qualifiers = "en-rUS-w700dp-h300dp") fun landscape200() = capture(scale = 2f)
    @Test fun dismissNeverConfirmsSharing() = capture(dismiss = true)

    private fun capture(scale: Float = 1f, dark: Boolean = false, dismiss: Boolean = false) {
        var label = ""
        var cancel = ""
        var confirmed = 0
        var dismissed = 0
        compose.setContent {
            var visible by remember { mutableStateOf(true) }
            HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
                label = stringResource(R.string.mode47_enable_power)
                cancel = stringResource(android.R.string.cancel)
                if (visible) PowerSharingDisclosure(listOf("OpenRouter", "ElevenLabs"),
                    setOf(RemoteDataCategory.TEXT, RemoteDataCategory.WORKSPACE, RemoteDataCategory.SCREEN),
                    setOf(OnlineFeature.INFERENCE, OnlineFeature.SPEECH),
                    onConfirm = { confirmed++; visible = false }, onDismiss = { dismissed++; visible = false })
            }
        }
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onAllNodesWithText(label, useUnmergedTree = true).onFirst()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
        org.junit.Assert.assertTrue(layouts.isNotEmpty())
        org.junit.Assert.assertEquals(scale, layouts.first().layoutInput.density.fontScale, 0.01f)
        compose.onNode(hasText(label) and hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText(cancel) and hasClickAction()).assertIsDisplayed()
        compose.onNode(isDialog()).captureRoboImage()
        compose.onNode(hasText(if (dismiss) cancel else label) and hasClickAction()).performClick()
        assertEquals(if (dismiss) 0 else 1, confirmed)
        assertEquals(if (dismiss) 1 else 0, dismissed)
    }
}
