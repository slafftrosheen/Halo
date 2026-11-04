package com.halo.app.ui.shell

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Layout and synthetic gestures only: does not claim physical touch or microphone acceptance. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class HaloZoneLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val invoked = mutableListOf<String>()
    private val actions = (1..9).map { HaloOrbContextAction("tool-$it", "Action $it", HaloGlyph.TOOL) }

    @Test fun studioKeepsItsPrimaryGroupsWhenOnlyTheUtilityMustMove() {
        assertEquals(Zone5SecondaryPlacement(false, true),
            zone5SecondaryPlacement(200.dp, 84.dp, 60.dp, 56.dp, preferMenus = true))
        assertEquals(Zone5SecondaryPlacement(true, false),
            zone5SecondaryPlacement(200.dp, 84.dp, 60.dp, 56.dp))
        assertEquals(Zone5SecondaryPlacement(true, false),
            zone5SecondaryPlacement(130.dp, 84.dp, 24.dp, 56.dp, preferMenus = true))
        assertEquals(Zone5SecondaryPlacement(false, false),
            zone5SecondaryPlacement(240.dp, 84.dp, 60.dp, 56.dp, preferMenus = true))
    }

    private fun show(compact: Boolean = false, scale: Float = 1f, childHeader: Boolean = false,
        mode: com.halo.app.ai.PrivacyMode = com.halo.app.ai.PrivacyMode.PRIVATE, headerTool: Boolean = false,
        actionCount: Int = actions.size, route: String = "notebook") {
        val extraActions = if (headerTool) listOf(HaloOrbContextAction("page:page.note.tools", "More tools", HaloGlyph.TOOL)) else emptyList()
        val displayedActions = actions.take(actionCount)
        val shownActions = extraActions + if (childHeader) listOf(HaloOrbContextAction("page:test.back", "Back", HaloGlyph.PREVIOUS)) + displayedActions else displayedActions
        compose.setContent { HostlessHaloTheme(fontScale = scale) {
            val band = remember { mutableStateOf<ShellUtilityProjection?>(null) }
            CompositionLocalProvider(LocalShellUtilityBand provides band) {
                if (childHeader) BindShellUtilityBand(ShellPageOwner("notebook")) {
                    com.halo.app.ui.screens.notebook.ToolZoneHeader("Child", { invoked += "header-back" }) {
                        if (headerTool) com.halo.app.ui.screens.notebook.ToolZoneHeaderAction("page.note.tools") {
                            IconButton({ invoked += "header-tools" }) {
                                com.halo.app.ui.icons.HaloIcon(HaloGlyph.TOOL, "More tools")
                            }
                        }
                        IconButton({ invoked += "read" }) {
                            com.halo.app.ui.icons.HaloIcon(HaloGlyph.TOOL, "Read aloud")
                        }
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).testTag("zone5-working")) { Text("Working document") }
                    HaloZone(route, androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp, compact, shownActions, { invoked += it.id }, { invoked += it }, 2,
                        utility = {
                            if (childHeader) band.value?.let { RenderShellUtilityBand(it) }
                            else TextField("", {}, modifier = Modifier.fillMaxWidth(), label = { Text("Message") })
                        },
                        orb = { size -> HaloOrbLauncher(HaloSignalPresentation(state = HaloSignalState.IDLE, label = "Ready", reducedMotion = true),
                            HaloOrbInteraction(HaloOrbPrimary.TALK, false), emptySet(), {}, { invoked += "talk" }, {}, {},
                            size = size, contextActions = shownActions, onContextAction = { invoked += it.id }) },
                        profile = { com.halo.app.ui.components.CompactPrivacyModeButton(
                            mode, "Current mode", true, { invoked += "profile" }) }, backdrop = {}, profileStatus = { Text("Local") })
                }
            }
        } }
    }


    @Test fun normalFooterMovesSecondaryActionsIntoTheSheetBeforeTheyNeedScrolling() {
        show()
        compose.onNodeWithTag("halo-zone5").assertHeightIsEqualTo(200.dp)
        compose.onNodeWithTag("zone5-working").assertHeightIsEqualTo(600.dp)
        compose.onNodeWithText("Action 1").assertDoesNotExist()
        compose.onNodeWithText("All actions").performClick()
        compose.onNode(hasText("Action 1") and hasClickAction()).assertIsDisplayed().performClick()
        assertEquals(listOf("tool-1"), invoked)
    }
    @Test fun compactStudioActionsShowTheRelocatedSaveStatusOnce() {
        compose.setContent { HostlessHaloTheme {
            val page = remember { mutableStateOf<ShellPageProjection?>(null) }
            val utility = remember { mutableStateOf<ShellUtilityProjection?>(null) }
            CompositionLocalProvider(LocalShellPageActions provides page, LocalShellUtilityBand provides utility) {
                val owner = ShellPageOwner("studio", "artwork", statusLabel = "Draft kept on this phone")
                BindShellPageActions(owner, listOf(ShellPageAction("page.studio.layers", "Layers", HaloGlyph.NOTE) {}))
                BindShellUtilityBand(owner, includesStatus = true) {
                    com.halo.app.ui.screens.notebook.ToolZoneHeader("Artwork", {}, subtitle = "Draft kept on this phone") {}
                }
                val offered = listOf(HaloOrbContextAction("page:page.studio.layers", "Layers", HaloGlyph.NOTE))
                HaloZone("studio", 800.dp, true, offered, {}, {}, 0,
                    utility = { utility.value?.let { RenderShellUtilityBand(it) } },
                    orb = { size -> HaloOrbLauncher(HaloSignalPresentation(reducedMotion = true),
                        HaloOrbInteraction(HaloOrbPrimary.TALK, false), emptySet(), {}, {}, {}, {},
                        size = size, contextActions = offered) }, profile = {}, backdrop = {})
            }
        } }
        compose.onNodeWithText("All actions").performClick()
        compose.onAllNodesWithText("Draft kept on this phone").assertCountEquals(1)
        compose.onNode(isDialog()).captureRoboImage()
    }

    @Test @Config(qualifiers = "w360dp-h520dp")
    fun smallestNormalWindowKeepsExactQuarterAndCompleteCommands() {
        show()
        compose.onNodeWithTag("halo-zone5").assertHeightIsEqualTo(130.dp)
        compose.onNodeWithTag("zone5-working").assertHeightIsEqualTo(390.dp)
        compose.onNodeWithText("Action 1").assertDoesNotExist()
        compose.onNodeWithText("All actions").performClick()
        compose.onNode(hasText("Action 9") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("tool-9"), invoked)
    }

    @Test @Config(qualifiers = "w360dp-h960dp")
    fun manualOrbitDragKeepsTheOrbAnchoredAndNeverExecutes() {
        show()
        val before = compose.onNodeWithContentDescription("Talk").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Action 2").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithContentDescription("Talk").fetchSemanticsNode().boundsInRoot)
        assertTrue(invoked.isEmpty())
        compose.onNodeWithText("All actions").performClick()
        compose.onNode(hasText("Action 9") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("tool-9"), invoked)
    }

    @Test fun utilityDoesNotDuplicateSystemBackAndKeepsItsOtherControls() {
        show(childHeader = true, headerTool = true)
        compose.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("More tools").assertCountEquals(1)
        compose.onNodeWithContentDescription("Read aloud").assertIsDisplayed()
        compose.onNodeWithText("All actions").performClick()
        compose.onNode(hasText("Back") and hasClickAction()).assertDoesNotExist()
        compose.onNode(hasText("Action 9") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("tool-9"), invoked)
    }

    @Test @Config(qualifiers = "w360dp-h960dp")
    fun sparseActionsRemainLabelledAndDoNotCoverVoice() {
        show(actionCount = 1)
        val action = compose.onNodeWithText("Action 1").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val voice = compose.onNodeWithContentDescription("Talk").fetchSemanticsNode().boundsInRoot
        assertFalse(action.overlaps(voice))
        compose.onNodeWithText("Action 1").performTouchInput { click() }
        assertEquals(listOf("tool-1"), invoked)
    }

    @Test @Config(qualifiers = "w280dp-h520dp")
    fun narrowShortWindowUsesAListWithUnshrunkVoiceAndMenuTargets() {
        show(compact = true)
        compose.onNodeWithText("Action 1").assertDoesNotExist()
        val face = compose.onNodeWithContentDescription("Talk").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithText("Menu").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertFalse(face.overlaps(menu))
        assertTrue(face.width >= 48 && face.height >= 48)
        compose.onNodeWithText("All actions").performClick()
        compose.onNode(hasText("Action 1") and hasClickAction()).assertIsDisplayed()
    }

    @Test fun settingsUsesOneNavigationRowWithTheSameCompleteActionList() {
        show(route = "settings")
        compose.onNodeWithText("Action 1").assertDoesNotExist()
        val voice = compose.onNodeWithTag("zone5-orb-action").fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithText("Menu").fetchSemanticsNode().boundsInRoot
        val all = compose.onNodeWithText("All actions").fetchSemanticsNode().boundsInRoot
        assertEquals(voice.center.y, menu.center.y, 4f)
        assertEquals(voice.center.y, all.center.y, 4f)
        assertFalse(voice.overlaps(menu)); assertFalse(voice.overlaps(all))
        compose.onNodeWithText("All actions").performClick()
        compose.onNode(hasText("Action 9") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("tool-9"), invoked)
    }

    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp")
    fun russianLargeTextKeepsProfileReadOnlyUntilMenuDisclosure() {
        show(compact = true, scale = 2f)
        compose.onNodeWithContentDescription("Current mode").assertDoesNotExist()
        compose.onNodeWithText("Меню").assertIsDisplayed()
        compose.onNodeWithText("Все действия").assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Все действия").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        compose.onNode(isRoot()).captureRoboImage()
        println("ZONE5_RU_ACTION_LABEL width=${layouts.single().size.width} lines=${layouts.single().lineCount}")
        assertTrue("The compact action label must fit on two readable lines", layouts.single().lineCount <= 2)
        compose.onNodeWithText("Меню").performClick()
        compose.onNodeWithContentDescription("Current mode").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf("profile"), invoked)
    }

    @Test @Config(qualifiers = "lv-rLV-w412dp-h844dp")
    fun latvianLargeTextRetainsExplicitMenuAndActionList() {
        show(scale = 2f)
        compose.onNodeWithText("Izvēlne").assertIsDisplayed()
        compose.onNodeWithText("Visas darbības").assertIsDisplayed()
        compose.onNode(isRoot()).captureRoboImage()
    }

    @Test @Config(qualifiers = "ru-rRU-w844dp-h393dp")
    fun shortWide200PercentKeepsAUsableWorkAreaAboveTheDock() {
        show(compact = true, scale = 2f)
        val voice = compose.onNodeWithContentDescription("Говорить").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithText("Меню").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val all = compose.onNodeWithText("Все действия").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val work = compose.onNodeWithTag("zone5-working").fetchSemanticsNode().boundsInRoot
        assertTrue("The footer must leave at least 120dp for the working viewport: $work", work.height >= 120 * compose.density.density)
        assertFalse(voice.overlaps(menu)); assertFalse(voice.overlaps(all)); assertFalse(menu.overlaps(all))
        compose.onNodeWithText("Меню").performClick()
        compose.onNode(hasText("Настройки") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("settings"), invoked)
    }
}
