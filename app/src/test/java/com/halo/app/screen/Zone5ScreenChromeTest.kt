package com.halo.app.screen

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.presence.LocalShellHaloOrb
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class Zone5ScreenChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hostedClockKeepsDateSourceAndOpenAction() = clock(1f)
    @Test fun hostedClockAt200PercentWrapsWithoutLosingDateOrSource() = clock(2f)

    private fun clock(scale: Float) {
        var opens = 0
        compose.setContent { HostlessHaloTheme(darkTheme = true, fontScale = scale) {
            CompositionLocalProvider(LocalShellHaloOrb provides true) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        ScreenWidgetCard(ScreenDisplay("12:34 PM", "Saturday, October 31, 2026", "On this phone", null,
                            sourceTitle = true, sourceSummary = true), ScreenCard("clock", null, CardSize.STANDARD, true),
                            false, false, false, { opens++ })
                    }
                }
            }
        } }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("12:34 PM", useUnmergedTree = true).assertIsDisplayed()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("Clock text must fit its measured width at font scale $scale", layouts.single().hasVisualOverflow)
        assertTrue("The clock should keep the approved display hierarchy", layouts.single().layoutInput.style.fontSize.value >= 52f)
        compose.onNodeWithText("Saturday, October 31, 2026", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("On this phone", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("12:34 PM") and hasClickAction()).performClick()
        assertEquals(1, opens)
        compose.onNode(isRoot()).captureRoboImage()
    }

    @Test fun hostedEditModeNamesThePreviewAndKeepsApplyAndCancelExplicit() {
        var applied = 0; var cancelled = 0
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellHaloOrb provides true) {
                ScreenWorkspaceChrome(true, true, false, true, false, true,
                    {}, {}, {}, {}, { applied++ }, { cancelled++ }, {}, {}, {}, {}, null, shellZone = true)
            }
        } }
        compose.onNodeWithText("Preview").assertIsDisplayed()
        compose.onNodeWithText("Home").assertDoesNotExist()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, applied); assertEquals(1, cancelled)
    }
}
