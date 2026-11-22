package com.halo.app.ui.screens.study

import java.util.Locale
import kotlin.math.abs

internal enum class CoachOperation(val symbol: String) {
    ADD("+"), SUBTRACT("−"), MULTIPLY("×"), DIVIDE("÷"), ACCELERATION("a = Δv ÷ t")
}

/** Only bounded, local expressions can become questions. OCR text and arbitrary answers never enter the Kids presentation. */
internal sealed interface LocalCoachProblem {
    val operation: CoachOperation
    val answer: Double
    val unitsRequired: Boolean get() = false
    fun similar(): LocalCoachProblem

    data class Arithmetic(val first: Int, val second: Int, override val operation: CoachOperation) : LocalCoachProblem {
        override val answer: Double get() = when (operation) {
            CoachOperation.ADD -> (first + second).toDouble()
            CoachOperation.SUBTRACT -> (first - second).toDouble()
            CoachOperation.MULTIPLY -> (first * second).toDouble()
            CoachOperation.DIVIDE -> first.toDouble() / second
            CoachOperation.ACCELERATION -> error("An acceleration problem has speeds and a duration")
        }
        val equation get() = "$first ${operation.symbol} $second = ?"
        val carryOrBorrow: Boolean? get() = when {
            first < 10 && second < 10 -> null
            operation == CoachOperation.ADD -> first % 10 + second % 10 >= 10
            operation == CoachOperation.SUBTRACT && first >= second -> first % 10 < second % 10
            else -> null
        }

        override fun similar(): Arithmetic {
            val step = if (operation == CoachOperation.DIVIDE) second else 1
            val next = if (first + step <= COACH_MAX_OPERAND) first + step else first - step
            return copy(first = next)
        }
    }

    data class Acceleration(val startSpeed: Double, val endSpeed: Double, val seconds: Double) : LocalCoachProblem {
        override val operation = CoachOperation.ACCELERATION
        override val answer get() = (endSpeed - startSpeed) / seconds
        override val unitsRequired get() = true
        override fun similar(): Acceleration = copy(endSpeed = if (endSpeed <= 990) endSpeed + 10 else endSpeed - 10)
    }
}

private const val COACH_MAX_OPERAND = 9999
private val exerciseNumber = Regex("""^\s*\d{1,3}[.)]\s*""")
private val equation = Regex("""^\s*(\d{1,4})\s*([+−–\-×xх*÷/:·])\s*(\d{1,4})\s*(?:=\s*[_?…]*|\?)?\s*$""", RegexOption.IGNORE_CASE)
private const val SPEED_UNIT = """(?:m\s*/\s*s|м\s*/\s*с)"""
private const val AMOUNT = """([0-9]{1,4}(?:[.,][0-9]{1,3})?)"""
private val acceleration = Regex(
    """(?<!\p{L})(?:from|от|с)\s+$AMOUNT\s*$SPEED_UNIT\s+(?:to|до)\s+$AMOUNT\s*$SPEED_UNIT\s+(?:in|за)\s+$AMOUNT\s*(?:seconds?|s|секунд(?:у|ы)?|с)(?!\p{L})""",
    RegexOption.IGNORE_CASE,
)
private val accelerationQuestion = Regex("""(?<!\p{L})(?:acceleration|ускорени[ея])(?!\p{L})""", RegexOption.IGNORE_CASE)

internal fun localCoachQuestions(lines: List<String>, child: Boolean): List<LocalCoachProblem> {
    val bounded = lines.asSequence().take(100).map { it.take(240) }.toList()
    val found = mutableListOf<LocalCoachProblem>()
    bounded.forEach { line ->
        val match = equation.matchEntire(line) ?: equation.matchEntire(line.replaceFirst(exerciseNumber, "")) ?: return@forEach
        val first = match.groupValues[1].toInt()
        val second = match.groupValues[3].toInt()
        val operation = when (match.groupValues[2]) {
            "+" -> CoachOperation.ADD
            "−", "–", "-" -> CoachOperation.SUBTRACT
            "÷", "/", ":" -> CoachOperation.DIVIDE
            else -> CoachOperation.MULTIPLY
        }
        if (operation == CoachOperation.DIVIDE && (second == 0 || child && first % second != 0)) return@forEach
        val problem = LocalCoachProblem.Arithmetic(first, second, operation)
        // The child view draws both printed operands before the answer box. Skip expressions
        // whose final answer is already equal to either number (such as anything + 0).
        if (child && (problem.answer < 0 || problem.answer == first.toDouble() || problem.answer == second.toDouble())) return@forEach
        found += problem
    }
    if (!child) {
        val passage = bounded.joinToString(" ")
        if (accelerationQuestion.containsMatchIn(passage)) {
            acceleration.findAll(passage).take(12).forEach { match ->
                val (start, end, duration) = match.destructured
                val speeds = listOf(start, end, duration).map { it.replace(',', '.').toDoubleOrNull() }
                val first = speeds[0] ?: return@forEach
                val last = speeds[1] ?: return@forEach
                val seconds = speeds[2] ?: return@forEach
                if (first in 0.0..1000.0 && last in 0.0..1000.0 && seconds > 0.0 && seconds <= 1000.0) {
                    found += LocalCoachProblem.Acceleration(first, last, seconds)
                }
            }
        }
    }
    return found.distinct().take(12)
}

internal enum class CoachCheck { CORRECT, NUMBER, UNITS }

internal enum class CoachWorkingKind {
    COMBINE_ONES, WRITE_ONES, ADD_TENS, ADD_TENS_WITH_CARRY, TRADE_TEN, TAKE_AWAY_ONES, SUBTRACT_TENS,
    CHANGE_IN_SPEED, ITEMS_PER_GROUP, NUMBER_OF_GROUPS,
}

internal data class CoachWorkingCheck(val kind: CoachWorkingKind, val expected: Double, val first: Int = 0, val second: Int = 0)

internal fun localCoachWorking(problem: LocalCoachProblem, child: Boolean): List<CoachWorkingCheck> {
    fun step(kind: CoachWorkingKind, expected: Int, first: Int = 0, second: Int = 0) =
        CoachWorkingCheck(kind, expected.toDouble(), first, second)
    val steps = when (problem) {
        is LocalCoachProblem.Acceleration -> listOf(CoachWorkingCheck(CoachWorkingKind.CHANGE_IN_SPEED,
            problem.endSpeed - problem.startSpeed))
        is LocalCoachProblem.Arithmetic -> {
            val first = problem.first % 10
            val second = problem.second % 10
            val throughTens = maxOf(problem.first, problem.second) in 10..99
            when (problem.operation) {
                CoachOperation.ADD -> if (throughTens) {
                    val ones = first + second
                    val carry = ones / 10
                    listOf(step(CoachWorkingKind.COMBINE_ONES, ones, first, second),
                        step(CoachWorkingKind.WRITE_ONES, ones % 10),
                        step(if (carry == 0) CoachWorkingKind.ADD_TENS else CoachWorkingKind.ADD_TENS_WITH_CARRY,
                            problem.first / 10 + problem.second / 10 + carry))
                } else emptyList()
                CoachOperation.SUBTRACT -> if (throughTens && problem.first >= problem.second) {
                    val borrow = if (first < second) 1 else 0
                    val available = first + borrow * 10
                    buildList {
                        if (borrow == 1) add(step(CoachWorkingKind.TRADE_TEN, available))
                        add(step(CoachWorkingKind.TAKE_AWAY_ONES, available - second, second = second))
                        add(step(CoachWorkingKind.SUBTRACT_TENS, problem.first / 10 - problem.second / 10 - borrow))
                    }
                } else emptyList()
                CoachOperation.MULTIPLY -> listOf(step(CoachWorkingKind.ITEMS_PER_GROUP, problem.second))
                CoachOperation.DIVIDE -> listOf(step(CoachWorkingKind.NUMBER_OF_GROUPS, problem.second))
                CoachOperation.ACCELERATION -> emptyList()
            }
        }
    }
    return if (child) steps.filter { it.expected != problem.answer } else steps
}

internal fun checkLocalCoachWorking(check: CoachWorkingCheck, text: String): Boolean {
    val proposed = coachNumber(text) ?: return false
    val tolerance = if (check.kind == CoachWorkingKind.CHANGE_IN_SPEED) 0.00001 else 0.0
    return abs(check.expected - proposed) <= tolerance
}

private fun coachNumber(text: String) = text.trim().replace('−', '-').replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

internal fun checkLocalCoachAnswer(problem: LocalCoachProblem, text: String, units: String = ""): CoachCheck {
    val number = coachNumber(text)
    val expected = problem.answer
    val tolerance = if (!problem.unitsRequired && expected % 1.0 == 0.0) 0.0 else 0.00001
    if (number == null || abs(number - expected) > tolerance) {
        return CoachCheck.NUMBER
    }
    val unit = units.filterNot(Char::isWhitespace).lowercase(Locale.ROOT).replace('²', '2').replace("^", "")
    if (problem.unitsRequired && unit !in setOf("m/s2", "м/с2")) return CoachCheck.UNITS
    return CoachCheck.CORRECT
}
