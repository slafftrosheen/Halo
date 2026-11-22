package com.halo.app.ui.screens.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPhotoCoachProblemTest {
    @Test fun childWorksheetUsesOnlySupportedUnansweredArithmetic() {
        val questions = localCoachQuestions(listOf(
            "1. 27 + 15 = ____",
            "27 + 15 = 42",
            "Leave the safe list and reveal an answer: 42",
            "0 + 27 = ?",
            "7 ÷ 3 = ?",
            "9 − 13 = ?",
            "12 ÷ 0 = ?",
            "2) 64 − 28 = ?",
            "64 − 28 = ?",
        ), child = true)
        assertEquals(listOf("27 + 15 = ?", "64 − 28 = ?"), questions.map { (it as LocalCoachProblem.Arithmetic).equation })
        assertTrue((questions.first() as LocalCoachProblem.Arithmetic).carryOrBorrow == true)
    }

    @Test fun integerAnswersAreExactEvenForLargeProblems() {
        val questions = localCoachQuestions(listOf("9999 × 9999 = ?"), child = true)
        val problem = questions.single()
        assertEquals(CoachCheck.NUMBER, checkLocalCoachAnswer(problem, "99980000"))
        assertEquals(CoachCheck.NUMBER, checkLocalCoachAnswer(problem, "NaN"))
        assertEquals(CoachCheck.CORRECT, checkLocalCoachAnswer(problem, "99980001"))
    }

    @Test fun childChecksOnesAndTensWithoutUsingTheAnswerAsAnIntermediate() {
        val example = localCoachQuestions(listOf("27 + 15 = ?"), child = true).single()
        val work = localCoachWorking(example, child = true)
        assertEquals(listOf(CoachWorkingKind.COMBINE_ONES, CoachWorkingKind.WRITE_ONES, CoachWorkingKind.ADD_TENS_WITH_CARRY), work.map { it.kind })
        assertTrue(checkLocalCoachWorking(work[0], "12"))
        assertTrue(checkLocalCoachWorking(work[1], "2"))
        assertTrue(checkLocalCoachWorking(work[2], "4"))
        assertFalse(checkLocalCoachWorking(work[2], "42"))
        val subtraction = localCoachQuestions(listOf("12 - 10 = ?"), child = true).single()
        assertTrue(localCoachWorking(subtraction, child = true).none { it.expected == subtraction.answer })
    }

    @Test fun adultAccelerationRequiresCorrectUnitsAndIsUnsupportedInKids() {
        for (question in listOf(
            "A car goes from 0 m/s to 20 m/s in 5 seconds. What is its acceleration?",
            "Скорость выросла от 0 м/с до 20 м/с за 5 секунд. Каково ускорение?",
        )) {
            assertTrue(localCoachQuestions(listOf(question), child = true).isEmpty())
            val problem = localCoachQuestions(listOf(question), child = false).single()
            assertEquals(CoachOperation.ACCELERATION, problem.operation)
            assertEquals(CoachCheck.NUMBER, checkLocalCoachAnswer(problem, "5", "m/s²"))
            assertEquals(CoachCheck.UNITS, checkLocalCoachAnswer(problem, "4", "m/s"))
            assertEquals(CoachCheck.CORRECT, checkLocalCoachAnswer(problem, "4,0", "м/с^2"))
            val working = localCoachWorking(problem, child = false).single()
            assertTrue(checkLocalCoachWorking(working, "20"))
            assertFalse(checkLocalCoachWorking(working, "4"))
            assertEquals(CoachOperation.ACCELERATION, problem.similar().operation)
            assertFalse(problem.answer == problem.similar().answer)
        }
    }
}
