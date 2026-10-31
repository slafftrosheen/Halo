package com.halo.app.ui.zone5

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
    @Test fun hostedChildControlsLiveInTheZoneWithOneOrbAndQuarterHeight() = showHosted(false, 1f)
    @Test fun compactChildKeepsTypedHelpAndGatedOptionsInAllActions() = showHosted(true, 2f)

    private fun showHosted(compact: Boolean, scale: Float) {
        var typed = 0; var helped = 0; var parent = 0
        compose.setContent { HostlessHaloTheme(fontScale = scale, palette = HaloPalette(HaloAccent.ROSE, HaloTone.WARM)) {
            val page = remember { mutableStateOf<com.halo.app.ui.presence.ShellPageProjection?>(null) }
            val band = remember { mutableStateOf<com.halo.app.ui.presence.ShellUtilityProjection?>(null) }
            CompositionLocalProvider(LocalShellHaloOrb provides true,
                com.halo.app.ui.presence.LocalShellPageActions provides page,
                com.halo.app.ui.presence.LocalShellUtilityBand provides band) {
                KidsBrandTheme {
                    KidsZoneBinding(KidsDestination.HOME, null, "Talk or type", false, false,
                        { typed++ }, { helped++ }, {}, { parent++ })
                }
                val actions = page.value?.actions.orEmpty().map {
                    com.halo.app.ui.presence.HaloOrbContextAction("page:${it.id}", it.title, it.glyph, enabled = it.enabled)
                }
                val invoke: (com.halo.app.ui.presence.HaloOrbContextAction) -> Unit = { page.value?.invoke(it.id.removePrefix("page:")) }
                Column(Modifier.width(360.dp).height(800.dp)) {
                    Column(Modifier.weight(1f).fillMaxWidth().testTag("kids-work")) {
                        KidsHomeHero(HaloSignalPresentation(), {}, {}, {}, true)
                        KidsActivityHeader(KidsDestination.LEARN, HaloSignalPresentation(), "My lesson", {}, {})
                        KidsVoiceDock("Talk or type", false, false, {}, {}, {}, {})
                        KidsBrandTheme { KidsHomeChoices(onSelect = {}) }
                    }
                    com.halo.app.ui.shell.HaloZone("home", 800.dp, compact, actions, invoke, {}, 0,
                        utility = { band.value?.let { com.halo.app.ui.presence.RenderShellUtilityBand(it) } },
                        orb = { size -> com.halo.app.ui.presence.HaloOrbLauncher(HaloSignalPresentation(reducedMotion = true),
                            com.halo.app.ui.presence.HaloOrbInteraction(com.halo.app.ui.presence.HaloOrbPrimary.TALK, false),
                            emptySet(), {}, {}, {}, {}, size = size, contextActions = actions, onContextAction = invoke) },
                        profile = {}, backdrop = {}, kids = true)
                }
            }
        } }
        compose.onNodeWithText("Back to choices").assertDoesNotExist()
        compose.onNodeWithText("Grown-up space").assertDoesNotExist()
        compose.onNode(hasText("Type instead") and hasAnyAncestor(hasTestTag("kids-work"))).assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Talk").assertCountEquals(1)
        compose.onNodeWithContentDescription("Talk").assertWidthIsAtLeast(56.dp).assertHeightIsAtLeast(56.dp)
        if (!compact) {
            compose.onNodeWithTag("halo-zone5").assertHeightIsEqualTo(200.dp)
            compose.onNodeWithTag("kids-work").assertHeightIsEqualTo(600.dp)
            compose.onNodeWithText("Type instead").assertHeightIsAtLeast(56.dp).performClick()
            compose.onNodeWithText("What can I say?").assertHeightIsAtLeast(56.dp).performClick()
        }
        compose.onRoot().captureRoboImage()
        compose.onNodeWithText("All actions").performClick()
        compose.onNode(hasText("Grown-up space") and hasClickAction()).performScrollTo().performClick()
        assertEquals(1, parent)
        if (compact) {
            compose.onNodeWithText("All actions").performClick()
            compose.onNode(hasText("Type instead") and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithText("All actions").performClick()
            compose.onNode(hasText("What can I say?") and hasClickAction()).performScrollTo().performClick()
        }
        assertEquals(1, typed); assertEquals(1, helped)
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
