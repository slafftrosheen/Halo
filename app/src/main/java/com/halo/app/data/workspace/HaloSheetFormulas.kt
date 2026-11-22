package com.halo.app.data.workspace

import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

internal data class HaloSheetCell(val row: Int, val column: Int) {
    val address get() = sheetColumnName(column) + (row + 1)
}

internal fun sheetColumnName(column: Int): String = if (column < 26) ('A' + column).toString()
    else "${'A' + (column / 26 - 1)}${'A' + column % 26}"

/** Only arithmetic and the named range functions can affect displayed values. CSV stores source cells. */
internal class HaloSheetFormulas(private val table: WorkspaceCsv) {
    private val cache = mutableMapOf<HaloSheetCell, BigDecimal?>()

    fun display(row: Int, column: Int): String {
        val raw = table.rows.getOrNull(row)?.getOrNull(column).orEmpty()
        if (!raw.startsWith('=')) return raw
        val cell = HaloSheetCell(row, column)
        val result = if (cell in cache) cache[cell] else value(cell, HashSet(), Budget()).also { cache[cell] = it }
        return result?.stripTrailingZeros()?.toPlainString() ?: "#ERROR"
    }

    private class Budget(var cells: Int = WorkspaceCsv.MAX_CELLS + 100)

    private fun value(cell: HaloSheetCell, visiting: MutableSet<HaloSheetCell>, budget: Budget): BigDecimal? {
        if (--budget.cells < 0 || visiting.size >= 32) return null
        if (cell in cache) return cache[cell]
        val raw = table.rows.getOrNull(cell.row)?.getOrNull(cell.column).orEmpty()
        if (!raw.startsWith('=')) return sheetNumber(raw)
        if (!visiting.add(cell)) return null
        val result = evaluate(raw.drop(1).trim().uppercase(Locale.ROOT), visiting, budget)
        visiting.remove(cell)
        if (result != null) cache[cell] = result
        return result
    }

    private fun evaluate(formula: String, visiting: MutableSet<HaloSheetCell>, budget: Budget): BigDecimal? {
        if (formula.length > 120) return null
        parseCell(formula)?.let { return value(it, visiting, budget) }
        sheetNumber(formula)?.let { return it }
        function.matchEntire(formula)?.destructured?.let { (name, from, to) ->
            val start = parseCell(from) ?: return null
            val end = parseCell(to) ?: return null
            val columnCount = maxOf(0, end.column - start.column + 1)
            val rowCount = maxOf(0, end.row - start.row + 1)
            if (columnCount * rowCount !in 1..WorkspaceCsv.MAX_CELLS) return null
            val values = mutableListOf<BigDecimal>()
            for (row in start.row..end.row) for (column in start.column..end.column) {
                val raw = table.rows.getOrNull(row)?.getOrNull(column).orEmpty()
                val current = value(HaloSheetCell(row, column), visiting, budget)
                if (budget.cells < 0 || current == null && raw.startsWith('=')) return null
                if (current != null) values.add(current)
            }
            return when (name) {
                "SUM" -> values.fold(BigDecimal.ZERO, BigDecimal::add)
                "AVERAGE" -> values.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add)
                    ?.divide(values.size.toBigDecimal(), MathContext.DECIMAL64)
                "MAX" -> values.maxOrNull()
                "MIN" -> values.minOrNull()
                "COUNT" -> values.size.toBigDecimal()
                else -> null
            }
        }
        fun operand(token: String) = parseCell(token)?.let { value(it, visiting, budget) } ?: sheetNumber(token)
        val (left, operator, right) = arithmetic.matchEntire(formula)?.destructured ?: return null
        val a = operand(left) ?: return null
        val b = operand(right) ?: return null
        return when (operator) {
            "+" -> a.add(b, MathContext.DECIMAL64)
            "-" -> a.subtract(b, MathContext.DECIMAL64)
            "*" -> a.multiply(b, MathContext.DECIMAL64)
            "/" -> b.takeIf { it.signum() != 0 }?.let { a.divide(it, MathContext.DECIMAL64) }
            else -> null
        }
    }

    companion object {
        private val address = Regex("([A-Z]{1,2})([1-9][0-9]{0,3})")
        private val function = Regex("(SUM|AVERAGE|MAX|MIN|COUNT)\\(\\s*([A-Z]{1,2}[0-9]+)\\s*:\\s*([A-Z]{1,2}[0-9]+)\\s*\\)")
        private val arithmetic = Regex("(-?[0-9]+(?:[.][0-9]+)?|[A-Z]{1,2}[0-9]+)\\s*([+*/-])\\s*(-?[0-9]+(?:[.][0-9]+)?|[A-Z]{1,2}[0-9]+)")
        fun parseCell(raw: String): HaloSheetCell? {
            val (letters, row) = address.matchEntire(raw)?.destructured ?: return null
            val column = letters.fold(0) { result, char -> result * 26 + (char - 'A') + 1 } - 1
            return HaloSheetCell(row.toInt() - 1, column).takeIf { it.row in 0 until WorkspaceCsv.MAX_ROWS && it.column in 0 until WorkspaceCsv.MAX_COLUMNS }
        }
    }
}
