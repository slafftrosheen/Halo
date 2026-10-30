package com.halo.app.ui.zone5

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Native VectorDrawable rendering contact sheet, inspected separately from production/device gates. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w390dp-h844dp")
class HaloZone5IconRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun semanticVectorsAt24_32_48DpInBothThemes() {
        compose.setContent {
            Column {
                for (dark in listOf(false, true)) HostlessHaloTheme(darkTheme = dark) {
                    androidx.compose.material3.Surface {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(if (dark) "Dark" else "Light")
                            for (id in listOf("action_attach", "action_import", "chat_new", "chat_history", "tool_study", "nav_workspace")) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    for (size in listOf(24.dp, 32.dp, 48.dp)) Zone5Icon(id, HaloGlyph.TOOL, Modifier.size(size))
                                    Text(id)
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Light").assertIsDisplayed()
        compose.onNodeWithText("Dark").assertIsDisplayed()
        compose.onNode(isRoot()).captureRoboImage()
    }
}
