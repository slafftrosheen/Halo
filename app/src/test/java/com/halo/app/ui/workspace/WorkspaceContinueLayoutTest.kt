package com.halo.app.ui.workspace

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.data.notebook.WorkspaceObjectType
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
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspaceContinueLayoutTest {
    @get:Rule val compose = createComposeRule()
    private fun render(dark: Boolean, scale: Float, chat: String, done: String, name: String) {
        var destination: WorkspaceDestination? = null
        var dismissed = false
        val item = WorkspaceContinuityItem("hidden-object-id", "hidden-version", "Synthetic artwork", WorkspaceObjectType.FILE,
            WorkspaceDestination.entries.toSet(), listOf(WorkspaceSourceLink("hidden-source-id", "hidden-source-version", "Synthetic source", WorkspaceSourceState.EARLIER_VERSION, 1)))
        compose.setContent { HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
            WorkspaceContinueDialog(item, emptyList(), false, false, false, false,
                { destination = it }, {}, {}, {}, {}, { dismissed = true })
        } }
        compose.onNodeWithText(done).assertIsDisplayed()
        compose.onNodeWithText("hidden-object-id").assertDoesNotExist()
        compose.onNodeWithText("hidden-version").assertDoesNotExist()
        val path = File("build/outputs/issue53/$name.png").also { it.parentFile?.mkdirs() }
        compose.onNodeWithTag("studio-dialog").captureRoboImage(path)
        compose.onNodeWithText(chat).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(WorkspaceDestination.CHAT, destination)
        compose.onNodeWithText(done).assertIsDisplayed().performClick()
        assertTrue(dismissed)
    }
    @Test fun englishPhone() = render(false, 1f, "Use in Chat", "Done", "continue-en-360")
    @Test @Config(qualifiers = "lv-rLV-w412dp-h900dp")
    fun latvian200() = render(false, 2f, "Izmantot sarunā", "Gatavs", "continue-lv-200")
    @Test @Config(qualifiers = "ru-rRU-w740dp-h360dp-land")
    fun russianShortLandscape200() = render(true, 2f, "Использовать в чате", "Готово", "continue-ru-landscape-200")
}
