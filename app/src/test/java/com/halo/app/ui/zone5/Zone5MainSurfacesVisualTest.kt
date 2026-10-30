package com.halo.app.ui.zone5

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.screen.*
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.ui.screens.home.HaloComposer
import com.halo.app.ui.screens.home.HomeTranscriptMessage
import com.halo.app.ui.screens.notebook.WorkspaceHeadingAndTabs
import com.halo.app.ui.screens.notebook.WorkspaceToolsList
import com.halo.app.ui.shell.HaloZone
import com.halo.app.ui.theme.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Production presentation components with synthetic content; no device, save or provider claim. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
class Zone5MainSurfacesVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enabledActionsNeverAnnounceDisabledReasons() {
        val enabled = mutableStateOf(true)
        compose.setContent { HostlessHaloTheme {
            LabelledSatellite5(HaloOrbContextAction("page:page.notebook.new-note", "New note", HaloGlyph.NOTE,
                enabled = enabled.value, disabledReason = "Source unavailable"), {})
        } }
        compose.onNodeWithText("New note", ignoreCase = true)
            .assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.StateDescription))
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithText("New note", ignoreCase = true).assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Source unavailable"))
    }
    @Test fun homeDarkBlue() = show("home", true, HaloAccent.BLUE, HaloTone.COOL)
    @Test fun homeLightRose() = show("home", false, HaloAccent.ROSE, HaloTone.WARM)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun homeRussian200() = show("home", true, HaloAccent.SAGE, HaloTone.SLATE, 2f)
    @Test fun toolsDarkSage() = show("tools", true, HaloAccent.SAGE, HaloTone.SLATE)
    @Test @Config(qualifiers = "lv-rLV-w360dp-h800dp") fun toolsLatvian200() = show("tools", false, HaloAccent.BLUE, HaloTone.COOL, 2f)
    @Test fun screenDarkBlue() = show("screen", true, HaloAccent.BLUE, HaloTone.COOL)
    @Test fun screenLightRosePreview() = show("screen", false, HaloAccent.ROSE, HaloTone.WARM, editing = true)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun screenRussian200Preview() = show("screen", true, HaloAccent.SAGE, HaloTone.SLATE, 2f, true)

    private fun show(page: String, dark: Boolean, accent: HaloAccent, tone: HaloTone, scale: Float = 1f, editing: Boolean = false) {
        var primary = Color.Unspecified
        var talk = ""; var menu = ""; var study = ""
        compose.setContent { HostlessHaloTheme(darkTheme = dark, palette = HaloPalette(accent, tone), fontScale = scale) {
            primary = MaterialTheme.colorScheme.primary
            talk = uiText("Talk"); menu = stringResource(R.string.zone5_menu); study = uiText("Study")
            CompositionLocalProvider(LocalShellHaloOrb provides true) {
                val actions = when(page) {
                    "home" -> listOf(HaloOrbContextAction("page:attach", "Attach", HaloGlyph.FILE),
                        HaloOrbContextAction("page:new-chat", stringResource(R.string.zone5_new_chat), HaloGlyph.NOTE),
                        HaloOrbContextAction("page:history", "History", HaloGlyph.ACTIVITY))
                    "screen" -> listOf(HaloOrbContextAction("page:page.screen.add", stringResource(R.string.zone5_widgets), HaloGlyph.NOTE),
                        HaloOrbContextAction("page:page.screen.customize", stringResource(R.string.zone5_arrange), HaloGlyph.SETTINGS),
                        HaloOrbContextAction("page:page.screen.appearance", "Appearance", HaloGlyph.APPEARANCE))
                    else -> emptyList()
                }
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
                    Column(Modifier.fillMaxSize()) {
                        when(page) {
                            "home" -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Zone5PageHeading(uiText("Home"))
                                HomeTranscriptMessage("visual-user", "Where can I find my saved work?", true)
                                HomeTranscriptMessage("visual-reply", "Your notes and files are in Workspace. Open Library to continue.", false)
                            }
                            "tools" -> LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                item { WorkspaceHeadingAndTabs(false, {}) }
                                item { WorkspaceToolsList({}, {}, {}, {}) }
                            }
                            else -> Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
                                Zone5PageHeading("HaloScreen")
                                val cards = listOf(ScreenCard("clock", null, CardSize.STANDARD, true),
                                    ScreenCard("weather", null, CardSize.COMPACT, false),
                                    ScreenCard("calendar", null, CardSize.COMPACT, false))
                                ScreenWorkspaceGrid(cards, ScreenGrid.columns(320f, scale), Modifier.fillMaxWidth().weight(1f)) { cell, _ ->
                                    val card = cards[cell.index]
                                    val data = when(card.widgetType) {
                                        "clock" -> ScreenDisplay("12:30", "Friday, 30 October", "On this phone", null, sourceTitle = true, sourceSummary = true)
                                        "weather" -> ScreenDisplay("Weather", "A weather source has not been connected.", "Needs setup", null)
                                        else -> ScreenDisplay("Calendar", "Allow calendar access to show an event.", "Permission needed", null)
                                    }
                                    ScreenWidgetCard(data, card, false, false, editing, {}, ScreenAppearance())
                                }
                            }
                        }
                        HaloZone(if (page == "tools") "notebook" else page, 800.dp, false, actions, {}, {}, 0,
                            utility = {
                                when(page) {
                                    "home" -> HaloComposer("", {}, {}, {}, {}, true, true, false, "Talk", false,
                                        onAttach = {}, onTools = {})
                                    "tools" -> Text(stringResource(R.string.zone5_tools_controls), style = MaterialTheme.typography.bodyMedium)
                                    else -> ScreenWorkspaceChrome(true, editing, false, true, false, true,
                                        {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, null, shellZone = true)
                                }
                            },
                            orb = { size -> HaloOrbLauncher(HaloSignalPresentation(label = "Ready", reducedMotion = true),
                                HaloOrbInteraction(HaloOrbPrimary.TALK, false), emptySet(), {}, {}, {}, {}, size = size,
                                contextActions = actions, onContextAction = {}) },
                            profile = {}, profileStatus = { Text(uiText("Local")) }, backdrop = {}, composer = page == "home")
                    }
                }
            }
        } }
        compose.onAllNodesWithContentDescription(talk).assertCountEquals(1)
        compose.onNodeWithText(menu).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        assertPaint(compose.onNodeWithContentDescription(talk), primary, "orb follows $accent/$tone")
        if (page == "tools") assertPaint(compose.onNodeWithText(study), primary, "tool glyph follows $accent/$tone")
        compose.onRoot().captureRoboImage()
    }

    private fun assertPaint(node: SemanticsNodeInteraction, expected: Color, message: String) {
        val pixels = node.captureToImage().toPixelMap()
        var found = false
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val p = pixels[x,y]
            if (kotlin.math.abs(p.red-expected.red)<.02f && kotlin.math.abs(p.green-expected.green)<.02f && kotlin.math.abs(p.blue-expected.blue)<.02f) found = true
        }
        assertTrue(message,found)
    }
}
