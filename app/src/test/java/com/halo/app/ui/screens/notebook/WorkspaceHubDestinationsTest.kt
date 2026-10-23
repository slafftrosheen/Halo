package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
class WorkspaceHubDestinationsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun light360() = verify()
    @Test @Config(qualifiers = "en-rUS-w412dp-h850dp") fun dark412() = verify(dark = true)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun russian200() = verify(scale = 2f)
    @Test @Config(qualifiers = "lv-rLV-w412dp-h850dp") fun latvian200Dark() = verify(scale = 2f, dark = true)
    @Test @Config(qualifiers = "en-rUS-w700dp-h300dp") fun shortLandscape200() = verify(scale = 2f)

    private fun verify(scale: Float = 1f, dark: Boolean = false) {
        val chosen = mutableListOf<WorkspaceHubDestination>()
        val labels = linkedMapOf<WorkspaceHubDestination, String>()
        var studio = ""
        var scope = ""
        compose.setContent {
            HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
                WorkspaceHubDestination.entries.forEach {
                    labels[it] = if (it == WorkspaceHubDestination.COLLECTIONS)
                        stringResource(R.string.workspace49_collections) else uiText(it.label)
                }
                studio = stringResource(R.string.workspace49_studio)
                scope = stringResource(R.string.workspace49_memory_scope)
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                        WorkspaceHubDestinations(chosen::add)
                    }
                }
            }
        }
        val first = labels.getValue(WorkspaceHubDestination.NOTES)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(first, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(scale, layouts.single().layoutInput.density.fontScale, .01f)
        compose.onRoot().captureRoboImage()
        assertVisibleTextFits(layouts.single())
        for ((destination, label) in labels) {
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()
            assertEquals(destination, chosen.last())
            val textLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
            assertVisibleTextFits(textLayouts.single())
        }
        compose.onNodeWithText(scope).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(studio).performScrollTo().assertIsDisplayed().assertHasNoClickAction()
        assertEquals(WorkspaceHubDestination.entries.toList(), chosen)
    }
    private fun assertVisibleTextFits(layout: androidx.compose.ui.text.TextLayoutResult) {
        // Paragraph width includes the offered allocation, while BasicText may use its
        // smaller intrinsic width. Inspect actual lines rather than that allocation.
        assertFalse(layout.didOverflowHeight)
        for (line in 0 until layout.lineCount) {
            assertFalse(layout.isLineEllipsized(line))
            val visibleWidth = layout.getLineRight(line) - layout.getLineLeft(line)
            assertTrue("Visible line width $visibleWidth exceeds ${layout.size.width}",
                visibleWidth <= layout.size.width + .5f)
            assertTrue(layout.getLineBottom(line) <= layout.size.height + .5f)
        }
    }
}
