package com.halo.app.ui.adaptive.v13

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DynamicSurfaceTest {
    private val simplePen = Signals(DayPart.MORNING, InputKind.PEN, FormFactor.PHONE, null, ExperienceLevel.SIMPLE)
    private val write = Block("write", Slot.TOOLS, primary = true)
    private val draw = Block("draw", Slot.TOOLS, ExperienceLevel.STANDARD)
    private val penRule = SurfaceRule("pen", setOf(Condition.Input(InputKind.PEN)), Change.MakePrimary(draw.id), 0)
    private val notes = SurfaceSpec("notes", setOf(Anchor.BACK, Anchor.TITLE), listOf(write, draw), listOf(penRule))

    @Test fun penRevealsAndPrioritizesARealDrawingActionInSimpleMode() {
        val resolved = SurfaceResolver.resolve(notes, simplePen, Pins())
        assertEquals(listOf(draw, write), resolved.visible)
        assertEquals(draw, resolved.primary)
        assertEquals(listOf(penRule), resolved.applied)
        assertEquals(emptyList<Block>(), resolved.tucked)
        val finger = SurfaceResolver.resolve(notes, simplePen.copy(input = InputKind.FINGER), Pins())
        assertEquals(listOf(write), finger.visible)
        assertEquals(listOf(draw), finger.tucked)
    }

    @Test fun pinnedHiddenFrozenAndAbsentActionsTakePrecedence() {
        assertEquals(write, SurfaceResolver.resolve(notes, simplePen, Pins(frozen = true)).primary)
        assertEquals(emptyList<SurfaceRule>(), SurfaceResolver.resolve(notes, simplePen, Pins(hidden = setOf(draw.id))).applied)
        assertEquals(listOf(write, draw), SurfaceResolver.resolve(notes, simplePen, Pins(pinned = setOf(write.id))).visible)
        assertEquals(emptyList<SurfaceRule>(), SurfaceResolver.resolve(notes, simplePen, Pins(pinned = setOf(draw.id))).applied)
        val absent = notes.copy(blocks = listOf(draw.copy(availableAtLevel = false)), rules = listOf(
            SurfaceRule("missing", emptySet(), Change.Reveal("absent"), 0)))
        assertNull(SurfaceResolver.resolve(absent, simplePen, Pins()).primary)
        assertEquals(emptyList<SurfaceRule>(), SurfaceResolver.resolve(absent, simplePen, Pins()).applied)
    }

    @Test fun earlyOrMorningCountsOnceAndOnlyTwoDistinctRulesApply() {
        val weather = Block("weather", Slot.MAIN, ExperienceLevel.PRO)
        val task = Block("task", Slot.MAIN, ExperienceLevel.PRO)
        val planner = SurfaceSpec("today", emptySet(), listOf(write, draw, weather, task), listOf(
            SurfaceRule("missing", emptySet(), Change.Reveal("old"), 0),
            SurfaceRule("early", setOf(Condition.Parts(setOf(DayPart.EARLY, DayPart.MORNING))), Change.Promote(weather.id), 0),
            SurfaceRule("duplicate", emptySet(), Change.Reveal(weather.id), 0),
            penRule, SurfaceRule("third", emptySet(), Change.Reveal(task.id), 0),
        ))
        assertEquals(listOf("early", "pen"), SurfaceResolver.resolve(planner, simplePen, Pins()).applied.map(SurfaceRule::id))
    }
}
