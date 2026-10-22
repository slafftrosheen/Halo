package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w640dp-h360dp-land")
class HomeComposerVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun landscapeEditorFitsAnActual48dpRow() = capture(1f)
    @Test fun landscapeLargeTextEditorFitsAnActual48dpRow() = capture(2f)
    private fun capture(scale: Float) {
        compose.setContent { HostlessHaloTheme(fontScale = scale) {
            val configuration = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current)
            configuration.screenHeightDp = 360
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides configuration) {
            Box(Modifier.width(640.dp).height(48.dp)) {
                HaloComposer("issue45_keyboard_probe", {}, {}, {}, {}, true, true, false, "Talk", false,
                    onAttach = {}, onTools = {})
            }
            }
        } }
        val field = compose.onNodeWithContentDescription("Message")
        field.assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        field.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val text = layouts.single()
        val visibleHeight = compose.onNodeWithTag("home_composer_editor", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        assertTrue("Editable line must fit its visible viewport", text.getLineBottom(0) - text.getLineTop(0) <= visibleHeight)
        compose.onNodeWithContentDescription("Send").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onRoot().captureRoboImage()
    }
}
