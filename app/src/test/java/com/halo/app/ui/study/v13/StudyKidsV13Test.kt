package com.halo.app.ui.study.v13

import com.halo.app.ui.kids.v13.KidsAgeLayout
import com.halo.app.ui.kids.v13.KidsEvent
import com.halo.app.ui.kids.v13.KidsWeek
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StudyKidsV13Test {
    @Test fun everyRungIsAlwaysAvailable() {
        HelpPlain.ladder.forEach { assertTrue(HelpPlain.available(it.stage, triesSoFar = 0, secondsOnQuestion = 0)) }
        assertEquals(listOf("SOLUTION"), HelpPlain.ladder.filter { it.askFirst }.map { it.stage })
        assertEquals(7, HelpPlain.ladder.size)
    }

    @Test fun resultsNeverUseRed() {
        listOf("CHECKS_OUT", "NEEDS_WORK", "INSUFFICIENT_EVIDENCE", "UNVERIFIED").forEach { HelpPlain.verdict(it) }
        assertEquals(Tone.APRICOT, HelpPlain.verdict("NEEDS_WORK").second)
        assertEquals("TRY", HelpPlain.pathStep("PRACTICE")); assertEquals("DONE", HelpPlain.pathStep("COMPLETE"))
    }

    @Test fun cardsComeBackLikeTheRepoPolicy() {
        assertEquals(4, ReviewPlain.comesBackInDays("ALMOST", 2))
        assertEquals(7, ReviewPlain.comesBackInDays("KNOWN", 2))
        assertEquals(15, ReviewPlain.comesBackInDays("KNOWN", 2, knownStreak = 1))
        assertEquals(2, ReviewPlain.comesBackInDays("AGAIN", 2))
        assertEquals(1, ReviewPlain.comesBackInDays("AGAIN", 4, wrongStreak = 1))
        assertEquals("in 4 days", ReviewPlain.phrase(4)); assertEquals(8, ReviewPlain.minutesFor(12))
    }

    @Test fun testPlanRestsAndEndsLight() {
        val mon = LocalDate.of(2026, 11, 23)
        val plan = ExamPlan.make(48, mon, mon.plusDays(11), setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        assertEquals(11, plan.size)
        assertEquals(48, plan.sumOf { it.newCards })
        assertTrue(plan.last().light && plan.last().newCards == 0)
        assertTrue(plan.filter { it.date.dayOfWeek == DayOfWeek.SATURDAY }.all { it.rest })
        assertEquals(6, plan.first().newCards)
    }

    @Test fun kidsSizedByAge() {
        assertEquals(72, KidsAgeLayout.forBand("EARLY").targetDp)
        assertEquals(2, KidsAgeLayout.forBand("EARLY").stops.size)
        assertEquals(4, KidsAgeLayout.forBand("OLDER").stops.size)
        assertEquals(3, KidsAgeLayout.home.size)
    }

    @Test fun grownUpWeekHasNoScores() {
        val week = KidsWeek.summary(listOf(KidsEvent("Dinosaurs", "ADVENTURE"), KidsEvent("Dinosaurs", "ADVENTURE"),
            KidsEvent("Adding to 20", "HOMEWORK", hints = 2), KidsEvent("Pip the dragon", "MADE")))
        assertEquals("Dinosaurs", week.first().topic)
        assertTrue(week.first { it.topic == "Adding to 20" }.neededHints)
        assertTrue(KidsWeek.check("He explored dinosaurs and needed hints on +6"))
        assertFalse(KidsWeek.check("Leo scored 8 points"))
        assertFalse(KidsWeek.check("Лео набрал 8 очков"))
        assertEquals("HINT", KidsWeek.homeworkHelp(insideWindow = true, askedForAnswer = true))
    }

    @Test fun noStringUsesScoreWords() {
        listOf("values", "values-ru").forEach { dir ->
            val xml = java.io.File("src/main/res/$dir/strings_learning_13.xml").readText()
            Regex("<string name=\"[a-z0-9_]+\">([^<]*)</string>").findAll(xml).forEach {
                assertTrue("$dir: ${it.groupValues[1]}", KidsWeek.check(it.groupValues[1]))
            }
        }
        assertTrue(KidsWeek.check("Карточек: 12"))
    }
}
