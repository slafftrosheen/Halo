package com.halo.app.screen

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
class ScreenGridInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var cards by mutableStateOf(listOf(ScreenCard("clock", null, CardSize.COMPACT, true),
        ScreenCard("notebook", "note:one", CardSize.COMPACT, false)))
    private var opened = 0
    private val move = "Move card: Clock & date"
    private val resize = "Resize card: Clock & date"

    private fun show(columns: Int = 2) {
        compose.setContent { HostlessHaloTheme { Box(Modifier.width(360.dp).height(700.dp)) {
            ScreenWorkspaceGrid(cards, columns) { cell, geometry ->
                val card = cards[cell.index]
                ScreenWidgetCard(ScreenDisplay(if (card.widgetType == "clock") "12:34" else "Notebook", "Saved content", "Updated", null,
                    sourceTitle = true, sourceSummary = true), card, false, false, true,
                    { opened++ }, editControls = {
                        ScreenGridHandles(cell, geometry, columns, cards.size, card.config["label"] ?: screenWidgetTitle(card.widgetType),
                            { x, y -> cards = ScreenGrid.move(cards, cell.index, columns, x, y) },
                            { x, y -> cards = ScreenGrid.resize(cards, cell.index, columns, x, y) }, { opened++ })
                    })
            }
        } } }
        compose.waitForIdle()
    }

    private fun action(node: String, label: String) {
        val actions = compose.onNodeWithContentDescription(node).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        val selected = actions.single { it.label == label }
        compose.runOnIdle { assertTrue(selected.action()) }
        compose.waitForIdle()
    }

    @Test fun accessibleMoveAndTwoDimensionalResizeUseRealGridAndNeverOpenTheSource() {
        show()
        compose.onNodeWithContentDescription(move).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val before = compose.onNodeWithText("12:34").fetchSemanticsNode().boundsInRoot.left
        action(move, "Move right")
        val after = compose.onNodeWithText("12:34").fetchSemanticsNode().boundsInRoot.left
        assertTrue(after > before)
        action(resize, "Wider")
        assertEquals(12, cards[0].placement!!.width)
        action(resize, "Taller")
        assertEquals(2, cards[0].placement!!.height)
        val cells = ScreenGrid.resolve(cards, 2)
        assertFalse(cells[0].overlaps(cells[1]))
        assertEquals("note:one", cards[1].itemId)
        assertEquals(0, opened)
    }

    @Test fun keyboardArrowsMoveAndShiftArrowsResizeTheFocusedHandle() {
        show()
        compose.onNodeWithContentDescription(move).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithContentDescription(move).performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(6, cards[0].placement!!.column) }
        compose.onNodeWithContentDescription(move).performKeyInput {
            keyDown(Key.ShiftLeft); pressKey(Key.DirectionDown); keyUp(Key.ShiftLeft)
        }
        compose.runOnIdle { assertEquals(2, cards[0].placement!!.height); assertEquals(0, opened) }
    }

    @Test fun consecutiveOneColumnKeyboardMovesRetainTheFocusedHandle() {
        cards = cards + ScreenCard("device", null, CardSize.COMPACT, false)
        show(columns = 1)
        compose.onNodeWithContentDescription(move).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithContentDescription(move).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription(move).assertIsFocused()
        compose.runOnIdle { assertEquals(1, cards.indexOfFirst { it.widgetType == "clock" }) }
        compose.onNodeWithContentDescription(move).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription(move).assertIsFocused()
        compose.runOnIdle { assertEquals(2, cards.indexOfFirst { it.widgetType == "clock" }); assertEquals(0, opened) }
    }

    @Test fun legacyDuplicateReorderKeepsFocusOnTheSelectedPresentationCard() {
        cards = screenCardIdentities(listOf(cards[0], cards[0].copy(config = mapOf("label" to "Second clock")), cards[1]))
        val selectedId = cards[0].presentationId
        show(columns = 1)
        compose.onNodeWithContentDescription(move).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        repeat(2) {
            compose.onNodeWithContentDescription(move).performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithContentDescription(move).assertIsFocused()
        }
        compose.runOnIdle { assertEquals(selectedId, cards.last().presentationId); assertEquals(0, opened) }
    }

    @Test fun heldTouchDragMovesToTheNeighbouringTrackWithoutOpeningTheCard() {
        show()
        compose.onNodeWithContentDescription(move).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithContentDescription(move).performTouchInput { moveBy(Offset(190f, 0f)); up() }
        compose.runOnIdle { assertEquals(6, cards[0].placement!!.column); assertEquals(0, opened) }
    }

    @Test fun cancelledDragLeavesTheSavedPlacementUntouched() {
        show()
        val before = cards
        compose.onNodeWithContentDescription(move).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithContentDescription(move).performTouchInput { moveBy(Offset(190f, 0f)); cancel() }
        compose.runOnIdle { assertEquals(before, cards); assertEquals(0, opened) }
    }

    @Test fun resizeCornerAcceptsTouchDraggingInBothDimensions() {
        show()
        compose.onNodeWithContentDescription(resize).performTouchInput {
            down(center); moveBy(Offset(190f, 250f), delayMillis = 500); up()
        }
        compose.runOnIdle {
            assertEquals(12, cards[0].placement!!.width)
            assertTrue(cards[0].placement!!.height > 1)
            assertEquals(0, opened)
        }
    }
}
