package com.halo.app.ui.shell

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

    private fun show(compact: Boolean = false, scale: Float = 1f, childHeader: Boolean = false,
        mode: com.halo.app.ai.PrivacyMode = com.halo.app.ai.PrivacyMode.PRIVATE, headerTool: Boolean = false,
        actionCount: Int = actions.size) {
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
                    Box(Modifier.weight(1f)) { Text("Working document") }
                    HaloZone("notebook", 800.dp, compact, shownActions, { invoked += it.id }, { invoked += it }, 2,
                        utility = {
                            if (childHeader) band.value?.let { RenderShellUtilityBand(it) }
                            else TextField("", {}, modifier = Modifier.fillMaxWidth(), label = { Text("Message") })
                        },
                        orb = { size -> HaloOrbLauncher(HaloSignalPresentation(state = HaloSignalState.IDLE, label = "Ready", reducedMotion = true),
                            HaloOrbInteraction(HaloOrbPrimary.TALK, false), emptySet(), {}, { invoked += "talk" }, {}, {},
                            size = size, contextActions = shownActions, onContextAction = { invoked += it.id }) },
                        profile = { com.halo.app.ui.components.CompactPrivacyModeButton(
                            mode, "Current mode", true, { invoked += "profile" }) }, backdrop = {})
                }
            }
        } }
    }

    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp")
    fun russianCompactPowerDoesNotSqueezeHintOrMoveRootsAboveFooter() {
        show(compact = true, scale = 2f, mode = com.halo.app.ai.PrivacyMode.CLOUD)
        val root = compose.onNode(isRoot()).fetchSemanticsNode().boundsInRoot
        val mode = compose.onNodeWithContentDescription("Current mode").assertIsDisplayed().assertIsToggleable().fetchSemanticsNode().boundsInRoot
        val home = compose.onNodeWithContentDescription("Главная").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Compact footer must stay in the lower quarter", home.top >= root.height * .75f)
        assertTrue("Mode must not consume most of the row", mode.width <= root.width * .34f)
        compose.onNodeWithContentDescription("Current mode").performClick()
        assertEquals(listOf("profile"), invoked)
        compose.onNode(isRoot()).captureRoboImage()
    }

    @Test fun scopedKidsCompactMenuKeepsOnlyStudyAndUsesGuardedRoute() {
        val childActions = ShellOrbCatalog.context("home", kids = true)
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            HaloOrbLauncher(HaloSignalPresentation(state = HaloSignalState.IDLE, label = "Ready", reducedMotion = true),
                HaloOrbInteraction(HaloOrbPrimary.TALK, false), emptySet(), {}, {}, {},
                onTools = {}, showToolCatalog = false, destinations = emptyList(), size = 56.dp,
                contextActions = childActions, onContextAction = { action ->
                    ShellOrbCatalog.directContextRoute(action, kids = true)?.let { invoked += it }
                })
        } }
        compose.onNodeWithContentDescription("Show Halo shortcuts").performClick()
        compose.onNode(hasText("Study") and hasClickAction()).assertIsDisplayed()
        listOf("Workspace", "Studio", "Settings", "Activity", "Tools").forEach {
            compose.onNode(hasText(it) and hasClickAction()).assertDoesNotExist()
        }
        compose.onNode(hasText("Study") and hasClickAction()).performClick()
        assertEquals(listOf("study"), invoked)
    }

    @Test fun normalHeaderOmitsOnlyMatchingToolAndFullListContainsNoGlobalRoots() {
        show(childHeader = true, headerTool = true)
        compose.onAllNodesWithContentDescription("More tools").assertCountEquals(1)
        compose.onNodeWithContentDescription("Read aloud").assertIsDisplayed().performClick()
        assertEquals(listOf("read"), invoked)
        compose.onNodeWithContentDescription("Show Halo shortcuts").performClick()
        compose.onNode(hasText("More tools") and hasClickAction()).assertIsDisplayed()
        listOf("Home", "Workspace", "Activity", "Studio", "Devices", "Settings").forEach {
            compose.onNode(hasText(it) and hasClickAction()).assertDoesNotExist()
        }
    }

    @Test fun compactHeaderKeepsItsToolsWhenCarouselIsAbsent() {
        show(compact = true, scale = 2f, childHeader = true, headerTool = true)
        compose.onAllNodesWithContentDescription("More tools").assertCountEquals(1)
        compose.onNodeWithContentDescription("More tools").assertIsDisplayed().performClick()
        assertEquals(listOf("header-tools"), invoked)
        compose.onNodeWithContentDescription("Read aloud").assertIsDisplayed()
    }

    @Test fun normalChildShowsOneBackAndRetainsFullActionList() {
        show(childHeader = true)
        compose.onAllNodesWithContentDescription("Back").assertCountEquals(1)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("page:test.back"), invoked)
        compose.onNodeWithContentDescription("Show Halo shortcuts").performClick()
        compose.onNode(hasText("Back") and hasClickAction()).assertIsDisplayed()
    }

    @Test fun compactChildRetainsDirectHeaderBack() {
        show(compact = true, scale = 2f, childHeader = true)
        compose.onAllNodesWithContentDescription("Back").assertCountEquals(1)
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
        assertEquals(listOf("header-back"), invoked)
    }

    @Test fun rotationDoesNotMoveRootsOrLaunchAnAction() {
        show()
        val before = compose.onNodeWithContentDescription("Home").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, compose.onNodeWithContentDescription("Devices").fetchSemanticsNode().boundsInRoot.top)
        assertEquals(compose.onNodeWithContentDescription("Workspace").fetchSemanticsNode().boundsInRoot.top,
            compose.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithContentDescription("Action 2").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithContentDescription("Home").fetchSemanticsNode().boundsInRoot)
        assertTrue(invoked.isEmpty())
        compose.onNodeWithContentDescription("Devices").performClick()
        assertEquals(listOf("devices"), invoked)
        compose.onNode(isRoot()).captureRoboImage()
    }

    @Test fun curvedActionTouchDoesNotInvokePttOrTheFullList() {
        show()
        val action = compose.onNodeWithContentDescription("Action 2")
        val actionBounds = action.fetchSemanticsNode().boundsInRoot
        val ptt = compose.onNodeWithContentDescription("Talk").fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithContentDescription("Show Halo shortcuts").fetchSemanticsNode().boundsInRoot
        assertFalse("Action and PTT must be disjoint", actionBounds.overlaps(ptt))
        assertFalse("Action and explicit list must be disjoint", actionBounds.overlaps(list))
        action.performTouchInput { click(center) }
        assertEquals(listOf("tool-2"), invoked)
    }

    @Test fun singleCenteredToolKeepsItsTouchTargetAbovePtt() {
        show(actionCount = 1)
        val action = compose.onNodeWithContentDescription("Action 1")
        val bounds = action.fetchSemanticsNode().boundsInRoot
        val ptt = compose.onNodeWithContentDescription("Talk").fetchSemanticsNode().boundsInRoot
        assertFalse(bounds.overlaps(ptt))
        assertTrue(bounds.bottom <= ptt.top)
        action.performTouchInput { click(center) }
        assertEquals(listOf("tool-1"), invoked)
    }

    @Test fun twoSparseToolsDoNotCoverTheFixedPtt() {
        show(actionCount = 2)
        val ptt = compose.onNodeWithContentDescription("Talk").fetchSemanticsNode().boundsInRoot
        for (title in listOf("Action 1", "Action 2")) {
            assertFalse(compose.onNodeWithContentDescription(title).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot.overlaps(ptt))
        }
        compose.onNodeWithContentDescription("Action 2").performTouchInput { click(center) }
        assertEquals(listOf("tool-2"), invoked)
    }

    @Test @Config(qualifiers = "w280dp-h800dp")
    fun narrowWindowReflowsFourFixedDestinationsWithoutShrinkingTargets() {
        show()
        val roots = listOf("Home", "Workspace", "Devices", "Settings").map {
            compose.onNodeWithContentDescription(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        for (i in roots.indices) for (j in i + 1 until roots.size) {
            assertFalse("Fixed roots must not overlap", roots[i].overlaps(roots[j]))
        }
        compose.onNodeWithContentDescription("Talk").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show Halo shortcuts").assertIsDisplayed().performClick()
        compose.onNodeWithText("Action 1").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w340dp-h800dp")
    fun middleWidthUsesListInsteadOfSqueezingFourOrbitTargets() {
        show()
        listOf("Home", "Workspace", "Devices", "Settings").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Action 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show Halo shortcuts").performClick()
        compose.onNodeWithText("Action 1").assertIsDisplayed()
    }

    @Test fun compactOrbAndMenuHaveSeparateHitTargets() {
        show(compact = true, scale = 2f)
        val face = compose.onNodeWithContentDescription("Talk").fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithContentDescription("Show Halo shortcuts").fetchSemanticsNode().boundsInRoot
        assertTrue("Orb and list must not overlap", face.right <= menu.left || menu.right <= face.left)
        listOf("Home", "Workspace", "Devices", "Settings").forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed() }
        compose.onNode(isRoot()).captureRoboImage()
        compose.onNodeWithContentDescription("Show Halo shortcuts").performClick()
        compose.onNodeWithText("Action 1").assertIsDisplayed().performClick()
        assertEquals(listOf("tool-1"), invoked)
    }
}
