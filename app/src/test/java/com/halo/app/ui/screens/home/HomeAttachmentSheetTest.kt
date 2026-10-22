package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class HomeAttachmentSheetTest {
    @get:Rule val compose = createComposeRule()
    @Test fun expandedChooserPreservesLargeTextAndKeepsDismissalReachable() {
        var selected = 0; var closed = 0
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            HomeAttachmentSheet({ closed++ }) {
                listOf(R.string.home_attach_photos, R.string.home_attach_file, R.string.home_attach_audio,
                    R.string.home_attach_notebook, R.string.home_attach_selection).forEach { label ->
                    TextButton({ selected++ }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(label)) }
                }
            }
        } }
        assertEquals(0, selected)
        compose.onNodeWithText("Done").assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Photos").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2f, layouts.single().layoutInput.density.fontScale, 0.01f)
        compose.onNodeWithText("Screen / Ink selection").performScrollTo().performClick()
        assertEquals(1, selected)
        compose.onNodeWithText("Done").assertIsDisplayed().performClick()
        assertEquals(1, closed)
    }
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun russianAttachmentLargeText() = capture()
    @Test @Config(qualifiers = "lv-rLV-w640dp-h360dp") fun shortLandscapeAttachmentLargeText() = capture()
    private fun capture() {
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            HomeAttachmentSheet({}) {
                HomeAttachmentSourceActions({}, {}, {}, {}, {}, {})
            }
        } }
        compose.onNodeWithText(uiTextForTestDone()).assertIsDisplayed()
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onLast().captureRoboImage()
    }
    private fun uiTextForTestDone() = if (org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.locales[0].language == "ru") "Готово" else "Gatavs"
}
