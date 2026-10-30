package com.halo.app.ui.shell

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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

/** Synthetic host gestures and bounds; physical touch and TalkBack remain separate gates. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w390dp-h844dp")
class HaloZone5InteractionTest {
    @get:Rule val compose = createComposeRule()
    private val invoked = mutableListOf<String>()
    private val actions = (1..7).map { HaloOrbContextAction("edit-$it", "Edit $it", HaloGlyph.TOOL) }

    private fun show(compact: Boolean = false, scale: Float = 1f,
        route: State<String> = mutableStateOf("notebook/note/source"), count: State<Int> = mutableStateOf(7),
        kids: Boolean = false, destructive: Boolean = false,
        entryId: State<String?> = mutableStateOf(null)) {
        compose.setContent { HostlessHaloTheme(fontScale = scale) {
            val band = remember { mutableStateOf<ShellUtilityProjection?>(null) }
            CompositionLocalProvider(LocalShellUtilityBand provides band) {
                val currentActions = if (route.value.startsWith("notebook/canvas")) actions.take(count.value).map {
                    it.copy(id = it.id.replace("edit", "draw"), title = it.title.replace("Edit", "Draw"))
                } else actions.take(count.value).mapIndexed { index, action ->
                    if (destructive && index == 0) action.copy(id = "delete", title = "Delete", glyph = HaloGlyph.TRASH) else action
                }
                Column(Modifier.fillMaxSize()) {
                    Text("Unsaved document", Modifier.weight(1f))
                    HaloZone(route.value, 844.dp, compact, currentActions,
                        { invoked += it.id }, { invoked += it }, 2,
                        utility = { Text("Document tools") }, profile = { Text("Local profile control") },
                        backdrop = {}, orb = { size ->
                            HaloOrbLauncher(HaloSignalPresentation(state = HaloSignalState.IDLE, label = "Ready", reducedMotion = true),
                                HaloOrbInteraction(HaloOrbPrimary.TALK, false), emptySet(), {},
                                { invoked += "voice" }, {}, {}, size = size,
                                contextActions = currentActions, onContextAction = { invoked += it.id })
                        }, destinations = ShellOrbCatalog.globalDestinations(kids), entryId = entryId.value)
                }
            }
        } }
    }

    @Test fun lightStaticOrbRetainsNonTextContrast() {
        show()
        val pixels = compose.onNodeWithContentDescription("Talk").captureToImage().toPixelMap()
        val background = pixels[0, 0].luminance()
        var contrast = 1f
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val ink = pixels[x, y].luminance()
            contrast = maxOf(contrast, (maxOf(ink, background) + .05f) / (minOf(ink, background) + .05f))
        }
        assertTrue("The static ring must contrast at least 3:1 with its surface, got $contrast", contrast >= 3f)
    }

    @Test fun replacingAnEntryWithTheSameRoutePatternDismissesItsOldDisclosure() {
        val entryId = mutableStateOf<String?>("note-a")
        show(entryId = entryId)
        compose.onNodeWithText("Menu").performClick()
        compose.onNode(hasText("Settings") and hasClickAction()).assertIsDisplayed()
        compose.runOnIdle { entryId.value = "note-b" }
        compose.onNode(hasText("Settings") and hasClickAction() and hasAnyAncestor(isDialog())).assertDoesNotExist()
        assertTrue(invoked.isEmpty())
    }

    @Test fun kidsMenuKeepsTheAllowlistAndIdentifiesTheActiveStudyInstrument() {
        show(route = mutableStateOf("study"), kids = true)
        compose.onNodeWithText("Menu").performClick()
        compose.onNode(hasText("Study") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("Settings") and hasClickAction() and hasAnyAncestor(isDialog())).assertDoesNotExist()
    }

    @Test fun destructiveCommandsRemainInTheFullListWithoutBecomingDefaultSatellites() {
        show(destructive = true)
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.onNodeWithText("All actions").performClick()
        compose.onNode(hasText("Delete") and hasClickAction()).assertIsDisplayed()
        assertTrue(invoked.isEmpty())
    }

    @Test fun removingAllActionsKeepsTheOrbOnItsBottomAnchor() {
        val count = mutableStateOf(3)
        show(count = count)
        val before = compose.onNodeWithContentDescription("Talk").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { count.value = 0 }
        assertEquals(before.bottom, compose.onNodeWithContentDescription("Talk").fetchSemanticsNode().boundsInRoot.bottom, .1f)
    }

    @Test fun switchingInstrumentDoesNotCarryItsPreviousScrollPosition() {
        val route = mutableStateOf("notebook/note/source")
        show(route = route)
        compose.onNodeWithText("Edit 2").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Edit 1").assertIsNotDisplayed()
        compose.runOnIdle { route.value = "notebook/canvas/board" }
        compose.onNodeWithText("Draw 1").assertIsDisplayed()
        assertTrue(invoked.isEmpty())
    }

    @Test fun activityAndNotificationsSelectTheirOwnMenuDestination() {
        val route = mutableStateOf("activity")
        show(route = route)
        for (destination in listOf("activity", "notifications")) {
            compose.runOnIdle { route.value = destination }
            compose.onNodeWithText("Menu").performClick()
            compose.onNode(hasText("Activities & notifications") and hasClickAction()).assertIsSelected()
            compose.onNode(hasText("Workspace") and hasClickAction()).assertIsNotSelected()
            compose.onNodeWithText("Close").performScrollTo().performClick()
        }
        assertTrue(invoked.isEmpty())
    }

    @Test fun menuExposesRootsAndProfileWithoutInstrumentActionsOrVoice() {
        show()
        compose.onNodeWithText("Menu").performClick()
        for (label in listOf("HaloScreen", "Home", "Workspace", "Devices", "Activities & notifications", "Settings"))
            compose.onNode(hasText(label) and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("Local profile control").assertIsDisplayed()
        compose.onNode(hasText("Edit 1") and hasClickAction() and hasAnyAncestor(isDialog())).assertDoesNotExist()
        compose.onNode(hasText("Workspace") and hasClickAction()).assertIsSelected().performClick()
        assertEquals(listOf("notebook"), invoked)
        compose.onNodeWithText("Unsaved document").assertIsDisplayed()
    }

    @Test fun holdAndSwipeOpenTheSameMenuAndNeverStartVoice() {
        show()
        compose.onNodeWithContentDescription("Talk").performTouchInput { longClick() }
        compose.onNode(hasText("Settings") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Talk").performTouchInput { swipeUp(durationMillis = 250) }
        compose.onNode(hasText("Settings") and hasClickAction()).assertIsDisplayed()
        assertTrue(invoked.isEmpty())
    }

    @Test fun actionListUsesCurrentInstrumentWhileDragCannotExecute() {
        show()
        compose.onNodeWithText("Edit 2").performTouchInput { swipeLeft() }
        assertTrue(invoked.isEmpty())
        compose.onNodeWithText("All actions").performClick()
        compose.onNode(hasText("Edit 7") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("edit-7"), invoked)
    }

    @Test fun compactLargeTextKeepsVoiceMenuAndFullListSeparate() {
        show(compact = true, scale = 2f)
        val nodes = listOf(compose.onNodeWithContentDescription("Talk"), compose.onNodeWithText("Menu"),
            compose.onNodeWithText("All actions"))
        val bounds = nodes.map { it.assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        for (i in bounds.indices) for (j in i + 1 until bounds.size) assertFalse(bounds[i].overlaps(bounds[j]))
        compose.onNodeWithText("All actions").performClick()
        compose.onNode(hasText("Edit 1") and hasClickAction()).assertIsDisplayed().performClick()
        assertEquals(listOf("edit-1"), invoked)
    }
}
