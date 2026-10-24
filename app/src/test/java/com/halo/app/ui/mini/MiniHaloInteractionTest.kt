package com.halo.app.ui.mini

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.theme.HostlessHaloTheme as HaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class MiniHaloInteractionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun oneLocalOrbStartsConversationWhileEmptyComposerHasNoDuplicateMicrophone() {
        var talks = 0
        var sends = 0
        compose.setContent { HaloTheme { Surface {
            MiniHaloContent(HaloSignalPresentation(reducedMotion = true), "", {}, false, null, null,
                { talks++ }, { sends++ }, {}, {}, {})
        } } }
        compose.onNodeWithContentDescription("Talk").assertDoesNotExist()
        compose.onNodeWithContentDescription("Talk to halo", ignoreCase = true).assertIsDisplayed().performClick()
        assertEquals(1, talks)
        assertEquals(0, sends)
        compose.onNodeWithText("Use text").assertDoesNotExist()
    }
    @Test fun pendingReviewOpensActivityAndBlocksNewToolRequests() {
        var screen: String? = null
        compose.setContent { HaloTheme { Surface {
            MiniHaloContent(HaloSignalPresentation(reducedMotion = true), "", {}, false, null, null,
                {}, {}, {}, { screen = it }, {}, reviewRequired = true)
        } } }
        compose.onNodeWithText("Review").performClick()
        assertEquals("activity", screen)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("My tools").assertDoesNotExist()
        compose.onNodeWithText("Open camera").assertDoesNotExist()
    }
    @Test fun busyKeepsStopAndEditingAvailableWithoutSendingAgain() {
        var sent = 0; var stopped = 0; var edited = ""
        compose.setContent { HaloTheme(darkTheme = false) { Surface {
            MiniHaloContent(HaloSignalPresentation(reducedMotion = true), "My draft", { edited = it }, true,
                null, null, {}, { sent++ }, { stopped++ }, {}, {})
        } } }
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
        compose.onNodeWithText("Stop").performClick()
        compose.onNodeWithText("My draft").performTextInput(" more")
        assertEquals(0,sent); assertEquals(1,stopped); assertTrue(edited.contains("more"))
    }
    @Test fun longReplyCannotHideTheComposerOrFullAppHandoff() {
        compose.setContent { HaloTheme(darkTheme = false) { Surface {
            MiniHaloContent(HaloSignalPresentation(reducedMotion = true), "Follow up", {}, false,
                "A long answer. ".repeat(400), null, {}, {}, {}, {}, {})
        } } }
        compose.onNodeWithContentDescription("Send").assertIsDisplayed()
        compose.onNodeWithText("Open halo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test fun fullWidthLocalOrbStaysBelowEditorOutsideLongReplyScroller() {
        compose.setContent { HaloTheme { Surface {
            MiniHaloContent(HaloSignalPresentation(reducedMotion = true), "Keep my draft", {}, false,
                "A long answer. ".repeat(400), null, {}, {}, {}, {}, {},
                orbControl = { androidx.compose.material3.Text("Persistent Mini orb") })
        } } }
        val orb = compose.onNodeWithText("Persistent Mini orb").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val editor = compose.onNode(hasSetTextAction()).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Local Orb belongs below editor, never in the scrolling status row", orb.top >= editor.bottom)
        compose.onNodeWithText("Keep my draft").assertIsDisplayed()
    }

    @Test fun orbShortcutOpensTypedCatalogDiscoveryWithoutSubmittingCameraText() {
        var request: com.halo.app.ui.presence.HaloToolCatalogRequest? = null
        var talks = 0
        var screen: String? = null
        compose.setContent { HaloTheme(darkTheme = false) { Surface {
            MiniHaloContent(HaloSignalPresentation(reducedMotion = true), "", {}, false, null, null,
                { talks++ }, {}, {}, { screen = it }, {}, orbControl = { size ->
                    com.halo.app.ui.presence.HaloOrbLauncher(HaloSignalPresentation(reducedMotion = true),
                        com.halo.app.ui.presence.HaloOrbInteraction(com.halo.app.ui.presence.HaloOrbPrimary.TALK, false),
                        com.halo.app.ui.presence.HaloOrbShortcut.defaults, {}, {}, {}, { request = it }, size)
                })
        } } }
        compose.onNodeWithContentDescription("Show Halo shortcuts").performClick()
        assertNull(request)
        assertEquals(0, talks)
        compose.onNodeWithText("Find").performScrollTo().performClick()
        assertEquals(com.halo.app.ui.screens.home.HomeToolGroup.FIND, request?.group)
        assertNull(screen)
        compose.onNodeWithText("Open camera").assertDoesNotExist()
        compose.onNodeWithText("Open halo").performClick()
        assertEquals("home", screen)
    }
}
