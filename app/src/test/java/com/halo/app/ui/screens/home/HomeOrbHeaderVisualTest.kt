package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.presence.*
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.app.language.uiText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "ru-rRU-w360dp-h800dp")
class HomeOrbHeaderVisualTest {
    @get:Rule val compose = createComposeRule()
    @Test fun homeHeaderAt360And200PercentKeepsOrbAndWords() {
        val signal = HaloSignalPresentation(reducedMotion = true)
        var talkLabel = ""
        compose.setContent { HostlessHaloTheme(fontScale = 2f) { Surface {
            talkLabel = uiText("Talk")
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                HomePresenceHeader(signal, "Готов, когда вы готовы", compact = true, orbControl = { face ->
                    HaloOrbLauncher(signal, HaloOrbInteraction(HaloOrbPrimary.TALK, false), HaloOrbShortcut.defaults,
                        {}, {}, {}, {}, face.size, face.content)
                })
            }
        } } }
        compose.onNodeWithContentDescription(talkLabel).assertIsDisplayed().assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Показать ярлыки Halo").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
        compose.onNodeWithText("Готов, когда вы готовы").assertIsDisplayed()
        compose.onRoot().captureRoboImage()
    }
}
