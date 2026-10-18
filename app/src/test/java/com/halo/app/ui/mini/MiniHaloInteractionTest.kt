package com.halo.app.ui.mini

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.theme.HaloTheme
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
    @Test fun pendingReviewOpensActivityAndBlocksNewToolRequests() {
        var screen: String? = null
        var requests = 0
        compose.setContent { HaloTheme { Surface {
            MiniHaloContent(HaloSignalPresentation(reducedMotion = true), "", {}, false, null, null,
                {}, {}, {}, { requests++ }, { screen = it }, {}, reviewRequired = true)
        } } }
        compose.onNodeWithText("Review").performClick()
        assertEquals("activity", screen)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("My tools").performClick()
        compose.onNodeWithText("Open camera").performScrollTo().assertIsNotEnabled()
        assertEquals(0, requests)
    }
    @Test fun busyKeepsStopAndEditingAvailableWithoutSendingAgain() {
        var sent = 0; var stopped = 0; var edited = ""
        compose.setContent { HaloTheme(darkTheme = false) { Surface {
            MiniHaloContent(HaloSignalPresentation(reducedMotion = true), "My draft", { edited = it }, true,
                null, null, {}, { sent++ }, { stopped++ }, {}, {}, {})
        } } }
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
        compose.onNodeWithText("Stop").performClick()
        compose.onNodeWithText("My draft").performTextInput(" more")
        assertEquals(0,sent); assertEquals(1,stopped); assertTrue(edited.contains("more"))
    }
    @Test fun longReplyCannotHideTheComposerOrFullAppHandoff() {
        compose.setContent { HaloTheme(darkTheme = false) { Surface {
            MiniHaloContent(HaloSignalPresentation(reducedMotion = true), "Follow up", {}, false,
                "A long answer. ".repeat(400), null, {}, {}, {}, {}, {}, {})
        } } }
        compose.onNodeWithContentDescription("Send").assertIsDisplayed()
        compose.onNodeWithText("Open Halo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test fun toolsUseTheExistingRequestCallbackAndOpenIsSeparate() {
        var requested: String? = null; var screen: String? = null
        compose.setContent { HaloTheme(darkTheme = false) { Surface {
            MiniHaloContent(HaloSignalPresentation(reducedMotion = true), "", {}, false, null, null,
                {}, {}, {}, { requested = it }, { screen = it }, {})
        } } }
        compose.onNodeWithText("My tools").performClick()
        assertNull(requested)
        compose.onNodeWithText("Open camera").performScrollTo().performClick()
        assertEquals("open camera",requested); assertNull(screen)
        compose.onNodeWithText("Open Halo").performClick()
        assertEquals("home",screen)
    }
}
