package com.halo.app.ui.zone5

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.presence.LocalShellHaloOrb
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.screens.kids.*
import com.halo.app.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
class Zone5KidsControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun hostedChildDockHasLabeledCallbacksWithoutASecondVoiceControl() {
        var typed = 0; var helped = 0
        compose.setContent { HostlessHaloTheme(palette = HaloPalette(HaloAccent.ROSE, HaloTone.WARM)) {
            KidsBrandTheme { CompositionLocalProvider(LocalShellHaloOrb provides true) {
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            KidsHomeHero(HaloSignalPresentation(reducedMotion = true), {}, {}, {}, false)
                            KidsHomeChoices(onSelect = {})
                        }
                        KidsVoiceDock("Talk or type", false, false, { error("Second voice control") }, { typed++ }, {}, { helped++ })
                    }
                }
            } }
        } }
        compose.onRoot().captureRoboImage()
        compose.onNodeWithText("Type instead").assertIsDisplayed().performClick()
        compose.onNodeWithText("What can I say?").assertIsDisplayed().performClick()
        assertEquals(1, typed); assertEquals(1, helped)
        compose.onNodeWithContentDescription("Talk to halo").assertDoesNotExist()
    }
    @Test @Config(qualifiers = "ru-rRU-w320dp-h800dp") fun russianLargeHeaderKeepsBackAndStopReachable() = captureHeader(2f)
    @Test @Config(qualifiers = "lv-rLV-w320dp-h800dp") fun latvianLargeHeaderKeepsBackAndStopReachable() = captureHeader(2f)
    private fun captureHeader(scale: Float) {
        var back = ""; var stop = ""
        compose.setContent { HostlessHaloTheme(darkTheme = false, fontScale = scale, palette = HaloPalette(HaloAccent.BLUE, HaloTone.COOL)) {
            back = com.halo.app.language.uiText("Back to choices"); stop = com.halo.app.language.uiText("Stop")
            Surface { KidsBrandTheme { KidsActivityHeader(KidsDestination.LEARN, HaloSignalPresentation(reducedMotion = true), onBack = {}, onStop = {}) } }
        } }
        compose.onNodeWithText(back).assertIsDisplayed(); compose.onNodeWithText(stop).assertIsDisplayed()
        compose.onRoot().captureRoboImage()
    }
}
