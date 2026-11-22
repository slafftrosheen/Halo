package com.halo.app.data.toolkit

import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale
import kotlin.math.*

internal object HaloCalcEngine {
    private val math = MathContext.DECIMAL128
    private val hundred = BigDecimal(100)
    private val operand = """[+-]?\d+(?:[.,]\d+)?"""
    private val portion = Regex("""^($operand)\s*%\s*(?:of|от)\s*($operand)$""", RegexOption.IGNORE_CASE)
    private val split = Regex("""^(?:split|divide|раздели(?:ть)?)\s+[$€£₽]?\s*($operand)\s+(?:by|among|на)\s+($operand)$""", RegexOption.IGNORE_CASE)
    private val calculationPrefix = Regex("""^(?:what is|calculate|evaluate|сколько будет|посчитай|вычисли)\s+""", RegexOption.IGNORE_CASE)

    data class Calculation(val expression: String, val result: BigDecimal, val steps: List<String>)
    data class Sample(val x: Double, val y: Double?)

    fun calculate(input: String): Calculation {
        val expression = normalize(input)
        val steps = mutableListOf<String>()
        return Calculation(expression, Parser(expression, null, steps).parse(), steps)
    }

    fun graph(input: String, from: Double, through: Double, count: Int = 161): List<Sample> {
        require(from.isFinite() && through.isFinite() && abs(from) <= 1_000_000 && abs(through) <= 1_000_000)
        require(through - from >= 1e-6 && count in 2..241)
        val expression = normalize(input.trim().replaceFirst(Regex("""^y\s*=\s*""", RegexOption.IGNORE_CASE), ""))
        val samples = List(count) { index ->
            val x = from + (through - from) * index / (count - 1)
            val y = try {
                Parser(expression, BigDecimal.valueOf(x)).parse().toDouble().takeIf { it.isFinite() }
            } catch (_: ArithmeticException) { null }
            Sample(x, y)
        }
        require(samples.any { it.y != null })
        return samples
    }

    fun convert(input: String, from: CalcUnit, to: CalcUnit, suppliedRate: String? = null): BigDecimal {
        val value = calculate(input).result
        require(from.kind == to.kind)
        if (from == to) return value
        if (from.kind == CalcUnitKind.CURRENCY) {
            val rate = requireNotNull(suppliedRate).trim().takeIf { it.length <= 32 }
                ?.replace(',', '.')?.toBigDecimalOrNull() ?: throw IllegalArgumentException("A source rate is required")
            require(rate > BigDecimal.ZERO && rate <= BigDecimal("100000000") && abs(rate.scale().toLong()) < 30)
            return magnitude(value.multiply(rate, math))
        }
        return magnitude(to.fromBase(from.toBase(value, math), math))
    }

    fun display(value: BigDecimal): String {
        val compact = value.round(MathContext(14)).stripTrailingZeros()
        return if (compact.precision() - compact.scale() in -6..16) compact.toPlainString() else compact.toEngineeringString()
    }

    private fun normalize(raw: String): String {
        require(raw.length <= 180)
        val input = calculationPrefix.replace(raw.trim(), "").trimEnd('?').trim()
        require(input.isNotEmpty())
        val expression = portion.matchEntire(input)?.let { it.groupValues[1] + "% * " + it.groupValues[2] }
            ?: split.matchEntire(input)?.let { it.groupValues[1] + " / " + it.groupValues[2] } ?: input
        return expression.replace('×', '*').replace('÷', '/').replace('−', '-').replace(',', '.').replace("π", "pi")
    }

    private fun magnitude(number: BigDecimal): BigDecimal {
        if (abs(number.precision().toLong() - number.scale()) > 500) throw ArithmeticException("Result exceeds the supported range")
        return number
    }

    private class Parser(private val source: String, private val variable: BigDecimal?, private val steps: MutableList<String>? = null) {
        private var position = 0
        private var depth = 0

        fun parse(): BigDecimal = sum().also { require(peek() == null) { "Unexpected input" } }

        private fun sum(): BigDecimal {
            var value = product()
            while (true) value = when {
                eat('+') -> combine(value, "+", product())
                eat('-') -> combine(value, "−", product())
                else -> return value
            }
        }

        private fun product(): BigDecimal {
            var value = signed()
            while (true) value = when {
                eat('*') -> combine(value, "×", signed())
                eat('/') -> combine(value, "÷", signed())
                else -> return value
            }
        }

        private fun signed(): BigDecimal {
            require(++depth <= 32) { "Expression is nested too deeply" }
            try {
                if (eat('+')) return signed()
                if (eat('-')) {
                    val value = signed()
                    val negative = value.negate(math)
                    return if (steps == null) negative else record("−" + display(value), negative)
                }
                var value = atom()
                if (eat('%')) value = combine(value, "÷", hundred)
                return if (eat('^')) combine(value, "^", signed()) else value
            } finally { depth-- }
        }

        private fun atom(): BigDecimal {
            if (eat('(')) return sum().also { require(eat(')')) { "Missing closing bracket" } }
            skipSpaces()
            val start = position
            if (peek()?.isLetter() == true) {
                while (source.getOrNull(position)?.isLetter() == true) position++
                return when (val name = source.substring(start, position).lowercase(Locale.ROOT)) {
                    "x" -> requireNotNull(variable) { "x is available in graphs" }
                    "pi" -> BigDecimal.valueOf(PI)
                    "e" -> BigDecimal.valueOf(E)
                    else -> {
                        require(eat('(')) { "Use brackets after a function" }
                        val argument = sum()
                        require(eat(')')) { "Missing closing bracket" }
                        val result = evaluateFunction(name, argument)
                        if (steps != null) record(name + "(" + display(argument) + ")", result) else result
                    }
                }
            }
            while (source.getOrNull(position)?.let { it in '0'..'9' || it == '.' } == true) position++
            require(position > start)
            if (source.getOrNull(position)?.lowercaseChar() == 'e') {
                position++
                if (source.getOrNull(position) in listOf('+', '-')) position++
                val first = position
                while (source.getOrNull(position)?.let { it in '0'..'9' } == true) position++
                require(position - first in 1..3) { "Use an exponent of at most three digits" }
            }
            return magnitude(source.substring(start, position).toBigDecimal().round(math))
        }

        private fun combine(left: BigDecimal, operator: String, right: BigDecimal): BigDecimal {
            val next = when (operator) {
                "+" -> magnitude(left.add(right, math))
                "−" -> magnitude(left.subtract(right, math))
                "×" -> magnitude(left.multiply(right, math))
                "÷" -> magnitude(left.divide(right, math))
                else -> power(left, right)
            }
            return if (steps == null) next else record(display(left) + " " + operator + " " + display(right), next)
        }

        private fun record(operation: String, result: BigDecimal): BigDecimal {
            steps?.add(operation + " = " + display(result))
            return result
        }

        private fun eat(character: Char): Boolean {
            if (peek() != character) return false
            position++
            return true
        }

        private fun skipSpaces() { while (source.getOrNull(position)?.isWhitespace() == true) position++ }
        private fun peek(): Char? { skipSpaces(); return source.getOrNull(position) }
    }

    private fun power(base: BigDecimal, exponent: BigDecimal): BigDecimal {
        if (exponent.abs() > BigDecimal(100)) throw ArithmeticException("Exponent exceeds the supported range")
        val exact = runCatching { exponent.intValueExact() }.getOrNull()
        if (exact != null) return magnitude(base.pow(exact, math))
        return approximate(base.toDouble().pow(exponent.toDouble()))
    }

    private fun evaluateFunction(name: String, argument: BigDecimal): BigDecimal {
        val value = argument.toDouble()
        return when (name) {
            "abs" -> argument.abs(math)
            "sqrt" -> {
                if (argument.signum() < 0) throw ArithmeticException("Negative square root")
                approximate(sqrt(value))
            }
            "sin" -> approximate(sin(value))
            "cos" -> approximate(cos(value))
            "tan" -> approximate(tan(value))
            "ln" -> approximate(ln(value))
            "log" -> approximate(log10(value))
            "exp" -> approximate(exp(value))
            else -> throw IllegalArgumentException("Unknown function")
        }
    }

    private fun approximate(value: Double): BigDecimal {
        if (!value.isFinite()) throw ArithmeticException("Result is not finite")
        return magnitude(BigDecimal.valueOf(value))
    }
}

internal enum class CalcUnitKind { LENGTH, MASS, TIME, TEMPERATURE, CURRENCY }

internal enum class CalcUnit(val kind: CalcUnitKind, val symbol: String, private val factor: String = "1") {
    MM(CalcUnitKind.LENGTH, "mm", "0.001"),
    CM(CalcUnitKind.LENGTH, "cm", "0.01"),
    M(CalcUnitKind.LENGTH, "m"),
    KM(CalcUnitKind.LENGTH, "km", "1000"),
    IN(CalcUnitKind.LENGTH, "in", "0.0254"),
    FT(CalcUnitKind.LENGTH, "ft", "0.3048"),
    MI(CalcUnitKind.LENGTH, "mi", "1609.344"),
    G(CalcUnitKind.MASS, "g", "0.001"),
    KG(CalcUnitKind.MASS, "kg"),
    LB(CalcUnitKind.MASS, "lb", "0.45359237"),
    S(CalcUnitKind.TIME, "s"),
    MIN(CalcUnitKind.TIME, "min", "60"),
    H(CalcUnitKind.TIME, "h", "3600"),
    C(CalcUnitKind.TEMPERATURE, "°C"),
    F(CalcUnitKind.TEMPERATURE, "°F"),
    K(CalcUnitKind.TEMPERATURE, "K"),
    EUR(CalcUnitKind.CURRENCY, "EUR"),
    USD(CalcUnitKind.CURRENCY, "USD"),
    GBP(CalcUnitKind.CURRENCY, "GBP"),
    PLN(CalcUnitKind.CURRENCY, "PLN"),
    RUB(CalcUnitKind.CURRENCY, "RUB");

    fun toBase(value: BigDecimal, math: MathContext): BigDecimal = when (this) {
        F -> value.subtract(BigDecimal(32), math).multiply(BigDecimal(5), math).divide(BigDecimal(9), math)
        K -> value.subtract(BigDecimal("273.15"), math)
        else -> value.multiply(BigDecimal(factor), math)
    }

    fun fromBase(value: BigDecimal, math: MathContext): BigDecimal = when (this) {
        F -> value.multiply(BigDecimal(9), math).divide(BigDecimal(5), math).add(BigDecimal(32), math)
        K -> value.add(BigDecimal("273.15"), math)
        else -> value.divide(BigDecimal(factor), math)
    }
}
