package com.halo.app.ui.workspace

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.*
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
class WorkspaceCsvLayoutTest {
    @get:Rule val compose = createComposeRule()
    private fun render(dark: Boolean, scale: Float, done: String, file: String) {
        var dismissed = false
        val block = NoteBlock("block", 0, NoteBlockType.CODE, "Name,Value\r\nSynthetic,=1+1", language = WorkspaceTextFormat.CSV.blockLanguage)
        val document = WorkspaceTextDocument(NotebookDocument("fixture", "Synthetic table", 1, listOf(block), WorkspaceLifecycle.ACTIVE, 1, 1), WorkspaceTextFormat.CSV, block)
        compose.setContent { HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
            WorkspaceCsvDialog(document, false, false, WorkspaceTextDocuments.HistoryStatus(), { _, _, _ -> }, {}, {}, { dismissed = true })
        } }
        compose.onNodeWithText(done).assertIsDisplayed().assertIsEnabled()
        val output = File("build/outputs/issue52/$file.png").also { it.parentFile.mkdirs() }
        compose.onNodeWithTag("studio-dialog").captureRoboImage(output)
        compose.onNodeWithText(done).performClick()
        assertTrue(dismissed)
    }
    @Test fun lightPhone360() = render(false, 1f, "Done", "csv-light-360")
    @Test @Config(qualifiers = "ru-rRU-w740dp-h360dp-land")
    fun russianShortLandscape200() = render(true, 2f, "Готово", "csv-dark-ru-landscape-200")
    @Test @Config(qualifiers = "lv-rLV-w412dp-h900dp")
    fun latvianLargePhone200() = render(false, 2f, "Gatavs", "csv-light-lv-412-200")
}
