package com.halo.app.ui.presence

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.shell.ShellOrbCatalog
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Hostless rendered layout evidence; never physical TalkBack/IME/audio qualification. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class GlobalOrbLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dark360CollapsedHasCenteredTalkAndSeparatePrivacyAndMenuTargets() = layout(false, false)
    @Test @Config(qualifiers = "w412dp-h900dp") fun light412LargeTextMenuKeepsRareDestinationsReachable() = layout(true, true, 2f)
    @Test @Config(qualifiers = "w800dp-h360dp") fun landscapeMenuRemainsScrollable() = layout(true, false)
    @Test @Config(qualifiers = "w800dp-h1000dp") fun tabletUsesFullAccessibleListWithReducedMotion() = layout(true, false, 2f)
    @Test @Config(qualifiers = "w360dp-h220dp") fun crampedLargeTextWindowKeepsOrbAndMenuReachable() = layout(false, false, 2f)

    private fun layout(open: Boolean, light: Boolean, scale: Float = 1f) {
        var talk = 0
        var selected: String? = null
        compose.setContent { HostlessHaloTheme(darkTheme = !light, fontScale = scale) {
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                    HaloOrbLauncher(HaloSignalPresentation(reducedMotion = true), HaloOrbInteraction(HaloOrbPrimary.TALK, false),
                        emptySet(), {}, { talk++ }, {}, {}, destinations = ShellOrbCatalog.destinations(false),
                        onNavigate = { selected = it }, contextActions = ShellOrbCatalog.context("home", false),
                        hint = "Tap to talk. Hold for shortcuts. Menu opens every page.",
                        leading = { OutlinedButton(onClick = {}, modifier = Modifier.widthIn(max = 112.dp).heightIn(min = 48.dp)) {
                            Text("Private")
                        } })
                }
            }
        } }
        compose.onNodeWithContentDescription("Talk").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show Halo shortcuts").assertIsDisplayed()
        val talkBounds = compose.onNodeWithContentDescription("Talk").fetchSemanticsNode().boundsInRoot
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(kotlin.math.abs(talkBounds.center.x - rootBounds.center.x) < 1f)
        val privacy = compose.onNodeWithText("Private").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(privacy.right < talkBounds.left)
        if (open) {
            compose.onNodeWithContentDescription("Show Halo shortcuts").performClick()
            compose.onNodeWithText("Advanced").performScrollTo().assertIsDisplayed()
            compose.onAllNodes(isRoot()).onLast().captureRoboImage()
            compose.onNodeWithText("Advanced").performClick()
            assertEquals("advanced_settings", selected)
            assertEquals(0, talk)
        } else compose.onRoot().captureRoboImage()
    }
}
