package com.halo.app.screen

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.presence.*
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.app.language.uiText
import com.halo.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
class ScreenWorkspaceVisualTest {
    @get:Rule val compose = createComposeRule()
    @Test fun lightPhone360() = capture()
    @Test @Config(qualifiers = "en-rUS-w412dp-h850dp") fun darkPhone412() = capture(dark = true)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun russian200() = capture(scale = 2f)
    @Test @Config(qualifiers = "lv-rLV-w412dp-h850dp") fun latvian200() = capture(scale = 2f, dark = true)
    @Test @Config(qualifiers = "en-rUS-w800dp-h900dp") fun widePlacement() = capture(wide = true)
    @Test @Config(qualifiers = "en-rUS-w700dp-h300dp") fun shortLandscape200ActiveStop() = capture(scale = 2f, full = true, active = true)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun editPreview200() = capture(scale = 2f, editing = true)
    @Test @Config(qualifiers = "en-rUS-w412dp-h850dp") fun ambientPrivateMasks() = capture(scale = 1.4f, full = true)

    @Test @Config(qualifiers = "en-rUS-w412dp-h1000dp") fun nativeSetupPermissionOfflineAndErrorStates() {
        val states = listOf("Needs setup", "Permission needed", "Offline · showing saved content", "Needs attention")
        val cards = states.mapIndexed { index, _ -> ScreenCard("automation_result", "fixture:$index", CardSize.COMPACT, false) }
        compose.setContent { HostlessHaloTheme { Surface {
            ScreenWorkspaceGrid(cards, 2, Modifier.fillMaxSize().padding(16.dp)) { cell, _ ->
                ScreenWidgetCard(ScreenDisplay("Routine fixture", "Last saved result", states[cell.index], null,
                    sourceTitle = true, sourceSummary = true), cards[cell.index], false, false, false, {})
            }
        } } }
        states.forEach { compose.onNodeWithText(it).assertExists() }
        compose.onRoot().captureRoboImage()
    }

    private fun capture(scale: Float = 1f, dark: Boolean = false, full: Boolean = false,
        active: Boolean = false, editing: Boolean = false, wide: Boolean = false) {
        val appearance = ScreenAppearance(ScreenFont.ATKINSON, ScreenBackground.MINT, true)
        var orbLabel = ""
        var shortcutsLabel = ""
        var settingsLabel = ""
        val cards = listOf(ScreenCard("clock", null, CardSize.STANDARD, true,
            placement = if (wide) ScreenPlacement(0, 0, 8, 2) else null),
            ScreenCard("notebook", "fixture:note", CardSize.COMPACT, false,
                placement = if (wide) ScreenPlacement(8, 0, 4, 1) else null),
            ScreenCard("research", "fixture:research", CardSize.COMPACT, false,
                placement = if (wide) ScreenPlacement(8, 1, 4, 1) else null),
            ScreenCard("weather", null, CardSize.COMPACT, false))
        compose.setContent { HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
            orbLabel = uiText(if (active) "Stop" else "Talk")
            shortcutsLabel = stringResource(R.string.halo_orb_show_shortcuts)
            settingsLabel = uiText("Screen settings")
            Surface(color = screenBackgroundColor(appearance), contentColor = MaterialTheme.colorScheme.onSurface) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    ScreenWorkspaceChrome(true, editing, full, true, active, !active, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, { size ->
                        HaloOrbLauncher(HaloSignalPresentation(reducedMotion = true),
                            HaloOrbInteraction(if (active) HaloOrbPrimary.STOP else HaloOrbPrimary.TALK, active),
                            HaloOrbShortcut.defaults, {}, {}, {}, {}, size)
                    })
                    val configuration = LocalConfiguration.current
                    val columns = ScreenGrid.columns((configuration.screenWidthDp - 32).toFloat(), scale)
                    ScreenWorkspaceGrid(cards, columns, Modifier.weight(1f).fillMaxWidth()) { cell, geometry ->
                        val card = cards[cell.index]
                        val locale = configuration.locales[0].language
                        val data = when (card.widgetType) {
                            "clock" -> ScreenDisplay("12:34", when (locale) {
                                "ru" -> "Пятница, 23 октября"; "lv" -> "Piektdiena, 23. oktobris"; else -> "Friday, 23 October"
                            }, "On this phone", null, sourceTitle = true, sourceSummary = true)
                            "notebook" -> ScreenDisplay(if (full) "PRIVATE-TITLE-SENTINEL" else "Notebook", when (locale) {
                                "ru" -> "Продолжить сохранённую заметку."; "lv" -> "Turpināt saglabāto piezīmi."; else -> "Continue your saved note."
                            }, "Saved on this phone", null, true, sourceTitle = true, sourceSummary = true)
                            "research" -> ScreenDisplay("Research fixture", if (full) "PRIVATE-BODY-SENTINEL" else "Saved findings with original source links.",
                                "Last update failed", null, true, Instant.parse("2026-10-23T00:00:00Z"), sourceTitle = true, sourceSummary = true)
                            else -> ScreenDisplay("Weather", "A weather source has not been connected.", "Needs setup", null)
                        }
                        ScreenWidgetCard(data, card, full && data.sensitive, full, editing,
                            {}, appearance, editControls = if (editing) ({
                                ScreenGridHandles(cell, geometry, columns, cards.size, screenWidgetTitle(card.widgetType), { _, _ -> }, { _, _ -> }, {})
                            }) else null)
                    }
                }
            }
        } }
        compose.waitForIdle()
        if (full) {
            compose.onNodeWithText("PRIVATE-TITLE-SENTINEL").assertDoesNotExist()
            compose.onNodeWithText("PRIVATE-BODY-SENTINEL").assertDoesNotExist()
        }
        if (active) compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
        if (!full) {
            compose.onNodeWithContentDescription(orbLabel).assertIsDisplayed().assertWidthIsAtLeast(48.dp)
            compose.onNodeWithContentDescription(shortcutsLabel).assertIsDisplayed().assertWidthIsAtLeast(48.dp)
            compose.onNodeWithContentDescription(settingsLabel).assertIsDisplayed().assertWidthIsAtLeast(48.dp)
        }
        compose.onRoot().captureRoboImage()
    }
}
