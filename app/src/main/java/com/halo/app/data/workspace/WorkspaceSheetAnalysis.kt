package com.halo.app.data.workspace

import java.math.BigDecimal
import java.util.Locale

/** A literal, source-versioned table calculation. Values are never executed as formulas. */
internal enum class SheetCalculation { TOTAL, HIGHEST, LOWEST }

internal data class SheetNumber(val row: Int, val value: BigDecimal)
internal data class SheetAnswer(
    val operation: SheetCalculation,
    val value: BigDecimal?,
    val matchingRows: List<Int>,
    val numbers: List<SheetNumber>,
)

private val decimal = Regex("[+-]?(?:[0-9]{1,36}(?:[.,][0-9]{1,12})?|[.,][0-9]{1,12})")

internal fun sheetNumber(text: String?): BigDecimal? {
    val value = text?.trim() ?: return null
    return value.takeIf { it.length <= 50 && decimal.matches(it) }
        ?.replace(',', '.')?.toBigDecimalOrNull()
}

internal fun WorkspaceCsv.calculate(
    column: Int, header: Boolean, operation: SheetCalculation, rowText: String = "",
): SheetAnswer {
    require(column in 0 until rows.maxOf { it.size })
    require(rowText.length <= 120)
    val normalized = rowText.lowercase(Locale.ROOT).trim()
    val numbers = rows.mapIndexedNotNull { index, row ->
        if ((header && index == 0) || (normalized.isNotEmpty() &&
                row.none { it.lowercase(Locale.ROOT).contains(normalized) })) null
        else sheetNumber(row.getOrNull(column))?.let { SheetNumber(index, it) }
    }
    val value = when (operation) {
        SheetCalculation.TOTAL -> numbers.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO) { sum, item -> sum + item.value }
        SheetCalculation.HIGHEST -> numbers.maxOfOrNull { it.value }
        SheetCalculation.LOWEST -> numbers.minOfOrNull { it.value }
    }
    val matches = if (operation == SheetCalculation.TOTAL || value == null) emptyList()
        else numbers.filter { it.value.compareTo(value) == 0 }.map { it.row }
    return SheetAnswer(operation, value, matches, numbers)
}

internal fun WorkspaceCsv.sortRows(column: Int, header: Boolean, descending: Boolean): WorkspaceCsv {
    require(column in 0 until rows.maxOf { it.size })
    val data = if (header) rows.drop(1) else rows
    val nonempty = data.filter { !it.getOrNull(column).isNullOrBlank() }
    val empty = data.filter { it.getOrNull(column).isNullOrBlank() }
    val allNumbers = nonempty.all { sheetNumber(it.getOrNull(column)) != null }
    val ordered = if (allNumbers) nonempty.sortedWith(
        if (descending) compareByDescending { sheetNumber(it.getOrNull(column)) }
        else compareBy { sheetNumber(it.getOrNull(column)) },
    ) else nonempty.sortedWith(
        if (descending) compareByDescending { it.getOrNull(column).orEmpty().lowercase(Locale.ROOT) }
        else compareBy { it.getOrNull(column).orEmpty().lowercase(Locale.ROOT) },
    )
    return WorkspaceCsv((if (header) rows.take(1) else emptyList()) + ordered + empty)
        .also(WorkspaceCsv::requireValid)
}
