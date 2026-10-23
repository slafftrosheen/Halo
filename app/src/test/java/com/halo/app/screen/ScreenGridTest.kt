package com.halo.app.screen

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ScreenGridTest {
    private fun card(id: String, placement: ScreenPlacement? = null) = ScreenCard("notebook", id,
        CardSize.COMPACT, false, mapOf("source" to id), placement = placement,
        typography = ScreenCardTypography(125, 150))

    private fun assertGrid(cards: List<ScreenCard>, columns: Int) {
        val cells = ScreenGrid.resolve(cards, columns)
        assertEquals(cards.indices.toSet(), cells.map { it.index }.toSet())
        cells.forEach { cell ->
            assertTrue(cell.column >= 0 && cell.column + cell.width <= columns)
            assertTrue(cell.row >= 0 && cell.height in 1..6)
            cells.filter { it.index != cell.index }.forEach { assertFalse("Cards overlap", cell.overlaps(it)) }
        }
    }

    @Test fun responsivePackingNeverDropsCardsOrOverlapsEvenWithConflictingSavedPositions() {
        val random = Random(37)
        repeat(40) {
            val cards = List(24) { index -> card("note-$index", ScreenPlacement(random.nextInt(-4, 20),
                random.nextInt(-3, 10), random.nextInt(-1, 18), random.nextInt(-1, 9))) }
            for (columns in 1..4) assertGrid(cards, columns)
        }
    }

    @Test fun movingOntoAnotherCardDisplacesItWithoutChangingItsSourceOrStyle() {
        val original = listOf(card("a", ScreenPlacement(0, 0, 6, 1)), card("b", ScreenPlacement(6, 0, 6, 1)))
        val moved = ScreenGrid.move(original, 1, 2, -1, 1)
        val cell = ScreenGrid.resolve(moved, 2).single { it.index == 1 }
        assertEquals(0, cell.column); assertEquals(1, cell.row)
        assertEquals(original.map { it.copy(placement = null) }, moved.map { it.copy(placement = null) })
        assertEquals(ScreenPlacement(6, 0, 6, 1), original[1].placement)
        assertGrid(moved, 2)
        val collision = ScreenGrid.move(original, 1, 2, -1, 0)
        assertEquals(0, ScreenGrid.resolve(collision, 2).single { it.index == 1 }.column)
        assertGrid(collision, 2)
    }

    @Test fun widthAndHeightResizeIndependentlyAndRemainBounded() {
        val original = listOf(card("a", ScreenPlacement(0, 0, 6, 1)), card("b", ScreenPlacement(6, 0, 6, 1)))
        val wider = ScreenGrid.resize(original, 0, 2, 1, 0)
        assertEquals(12, wider[0].placement!!.width)
        assertEquals(1, wider[0].placement!!.height)
        val taller = ScreenGrid.resize(wider, 0, 2, 0, 2)
        assertEquals(12, taller[0].placement!!.width)
        assertEquals(3, taller[0].placement!!.height)
        val bounded = ScreenGrid.resize(taller, 0, 2, 99, 99)
        assertEquals(6, bounded[0].placement!!.height)
        assertGrid(bounded, 2)
    }

    @Test fun narrowScreenMovesAndHeightChangesPreserveCanonicalWidths() {
        val original = listOf(card("a", ScreenPlacement(0, 0, 3, 1)), card("b", ScreenPlacement(6, 0, 3, 1)))
        val moved = ScreenGrid.move(original, 0, 2, 1, 1)
        assertEquals(listOf(3, 3), moved.map { it.placement!!.width })
        val taller = ScreenGrid.resize(moved, 0, 2, 0, 1)
        assertEquals(listOf(3, 3), taller.map { it.placement!!.width })
        val wider = ScreenGrid.resize(taller, 0, 2, 1, 0)
        assertEquals(listOf(12, 3), wider.map { it.placement!!.width })
        assertGrid(moved, 2); assertGrid(taller, 2); assertGrid(wider, 2)
        assertGrid(taller, 4)
    }

    @Test fun enlargedTextReflowDoesNotMutateSavedHorizontalPlacement() {
        val original = listOf(card("a", ScreenPlacement(6, 5, 6, 2)), card("b", ScreenPlacement(0, 0, 6, 1)))
        assertEquals(1, ScreenGrid.columns(840f, 2f))
        assertGrid(original, 1)
        assertEquals(ScreenPlacement(6, 5, 6, 2), original[0].placement)
        val taller = ScreenGrid.resize(original, 0, 1, 0, 1)
        assertEquals(ScreenPlacement(6, 5, 6, 3), taller[0].placement)
        assertEquals(original[1], taller[1])
    }

    @Test fun oneColumnReorderPreservesSourcesAndWidthsAndCanBeSaved() {
        val original = listOf(card("a", ScreenPlacement(6, 0, 6, 1)), card("b", ScreenPlacement(0, 1, 12, 2)))
        val changed = ScreenGrid.move(original, 1, 1, 0, -1)
        assertEquals(listOf("b", "a"), changed.map { it.itemId })
        assertEquals(listOf(12, 6), changed.map { it.placement!!.width })
        assertEquals(original.map { it.config }.toSet(), changed.map { it.config }.toSet())
        assertGrid(changed, 1); assertGrid(changed, 4)
    }

    @Test fun boundedProposalReorderActuallyMovesAnExplicitlyPlacedCard() {
        val original = listOf(card("a", ScreenPlacement(0, 0, 12, 1)), card("b", ScreenPlacement(0, 1, 12, 1)),
            card("c", ScreenPlacement(0, 2, 12, 1)))
        val changed = ScreenGrid.reorder(original, 2, 0)
        assertEquals(listOf("c", "a", "b"), changed.map { it.itemId })
        assertEquals(0, ScreenGrid.resolve(changed, 4).single { it.index == 0 }.row)
        assertGrid(changed, 4)
    }

    @Test fun touchWithoutMovementOrBoundaryResizeDoesNotRewriteTheLayout() {
        val cards = listOf(card("a", ScreenPlacement(0, 0, 12, 6)))
        assertSame(cards, ScreenGrid.move(cards, 0, 2, 0, 0))
        assertSame(cards, ScreenGrid.resize(cards, 0, 2, 0, 0))
        assertSame(cards, ScreenGrid.resize(cards, 0, 2, 1, 1))
        assertSame(cards, ScreenGrid.move(cards, 0, 2, -1, -1))
    }

    @Test fun availableWidthAndOsTextScaleChooseTheSameResponsivePolicy() {
        assertEquals(2, ScreenGrid.columns(360f, 1f)); assertEquals(2, ScreenGrid.columns(412f, 1f))
        assertEquals(3, ScreenGrid.columns(600f, 1f)); assertEquals(4, ScreenGrid.columns(840f, 1f))
        assertEquals(1, ScreenGrid.columns(360f, 2f)); assertEquals(1, ScreenGrid.columns(1000f, 2f))
    }

    @Test fun deskFontEmphasisIsMonotonicAndNeverShrinksTheOsChoice() {
        val values = (85..300).map { it / 100f }
        val results = values.map { screenAmbientFontScale(it, true) }
        results.zip(values).forEach { (result, original) -> assertTrue(result >= original) }
        results.zipWithNext().forEach { (before, after) -> assertTrue(after >= before) }
        assertEquals(2f, screenAmbientFontScale(2f, true), 0f)
        assertEquals(1f, screenAmbientFontScale(1f, false), 0f)
    }
}
