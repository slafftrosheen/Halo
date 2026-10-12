package com.halo.app.ui.design

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h800dp")
class HaloGuidanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun frameHasOnlyOneActionAndDisabledStateStopsFurtherTaps() {
        val enabled = mutableStateOf(true)
        var calls = 0
        compose.setContent { HaloTheme {
            HaloFocusAction("Continue", HaloGlyph.NEXT, enabled.value, { calls++; enabled.value = false })
        } }
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun unknownProgressDoesNotPresentAMeasuredPercentage() {
        compose.setContent { HaloTheme { HaloProgressPresentation("Downloading") } }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    @Test fun emptyStateDoesNotActUntilUserChoosesItsAction() {
        var calls = 0
        compose.setContent { HaloTheme {
            HaloEmptyState("No notes", "Create a note to begin", HaloGlyph.NOTE,
                action = HaloHeaderAction("Create note") { calls++ })
        } }
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Create note").performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun attentionRequiresExplicitMotionForegroundAndPlatformPermission() {
        assertTrue(haloAttentionMotionAllowed(true, true, false, true))
        assertFalse(haloAttentionMotionAllowed(true, true, true, true))
        assertFalse(haloAttentionMotionAllowed(true, true, false, false))
        assertFalse(haloAttentionMotionAllowed(true, false, false, true))
        assertFalse(haloAttentionMotionAllowed(false, true, false, true))
    }

    @Test fun invalidAndUnknownMeasurementsStayIndeterminate() {
        assertNull(haloMeasuredProgress(null, 10))
        assertNull(haloMeasuredProgress(1, null))
        assertNull(haloMeasuredProgress(0, 0))
        assertNull(haloMeasuredProgress(-1, 10))
        assertNull(haloMeasuredProgress(11, 10))
        assertEquals(0.5f, haloMeasuredProgress(5, 10)!!, 0f)
        assertEquals(1f, haloMeasuredProgress(Long.MAX_VALUE, Long.MAX_VALUE)!!, 0f)
    }
}
