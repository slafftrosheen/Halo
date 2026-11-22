package com.halo.app.ui.toolkit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.halo.app.data.workspace.HaloSheetCell
import com.halo.app.data.workspace.HaloSheetEntryKind
import com.halo.app.data.workspace.HaloSheetEntryRule
import com.halo.app.data.workspace.HaloSheetFormulas
import com.halo.app.data.workspace.sheetColumnName
import com.halo.app.data.workspace.sheetNumber
import com.halo.app.language.uiText
import com.halo.app.ui.zone6.Zone6ButtonSheet
import java.text.NumberFormat
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal enum class HaloSheetColorMode(val label: String) {
    ABOVE("Above"), BELOW("Below"), BETWEEN("Between"), TOP("Top values"), DUPLICATES("Duplicates"),
}

internal data class HaloSheetColorRule(val column: Int, val mode: HaloSheetColorMode, val lower: String, val upper: String) {
    fun valid(): Boolean = column in 0 until 64 && when (mode) {
        HaloSheetColorMode.ABOVE, HaloSheetColorMode.BELOW -> sheetNumber(lower) != null
        HaloSheetColorMode.BETWEEN -> {
            val min = sheetNumber(lower)
            val max = sheetNumber(upper)
            min != null && max != null && min <= max
        }
        HaloSheetColorMode.TOP -> lower.toIntOrNull()?.let { it in 1..1000 } == true
        HaloSheetColorMode.DUPLICATES -> true
    }

    fun matches(values: Map<Int, String>): Set<Int> {
        if (mode == HaloSheetColorMode.DUPLICATES) {
            val repetitions = values.values.filter(String::isNotBlank).groupingBy { it }.eachCount()
            return values.filterValues { (repetitions[it] ?: 0) >= 2 }.keys
        }
        val numbers = values.mapNotNull { (row, value) -> sheetNumber(value)?.let { row to it } }
        if (mode == HaloSheetColorMode.TOP) return numbers.sortedByDescending { it.second }
            .take(lower.toInt()).mapTo(mutableSetOf()) { it.first }
        val min = sheetNumber(lower) ?: return emptySet()
        val max = sheetNumber(upper) ?: BigDecimal.ZERO
        return numbers.filter { (_, number) ->
            when (mode) {
                HaloSheetColorMode.ABOVE -> number > min
                HaloSheetColorMode.BELOW -> number < min
                HaloSheetColorMode.BETWEEN -> number >= min && number <= max
                else -> false
            }
        }.mapTo(mutableSetOf()) { it.first }
    }

    fun encode() = listOf(column, mode.name, lower, upper).joinToString("/")

    companion object {
        fun decode(raw: String?): HaloSheetColorRule? = raw?.split('/')?.takeIf { it.size == 4 }?.let { (col, kind, min, max) ->
            runCatching { HaloSheetColorRule(col.toInt(), HaloSheetColorMode.valueOf(kind), min, max).takeIf { it.valid() } }.getOrNull()
        }
    }
}

@Composable
internal fun HaloSheetFreezeSheet(savedRows: Int, savedColumns: Int, onApply: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var rows by remember { mutableIntStateOf(savedRows) }
    var columns by remember { mutableIntStateOf(savedColumns) }
    Zone6ButtonSheet(uiText("Freeze rows and columns"), onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText("Rows: %1\$d", rows))
            Slider(rows.toFloat(), { rows = it.toInt() }, valueRange = 0f..5f, steps = 4)
            Text(uiText("Columns: %1\$d", columns))
            Slider(columns.toFloat(), { columns = it.toInt() }, valueRange = 0f..3f, steps = 2)
            TextButton(onClick = { onApply(rows, columns) }) { Text(uiText("Apply")) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HaloSheetColorRuleSheet(column: Int, saved: HaloSheetColorRule?, onDismiss: () -> Unit, onApply: (HaloSheetColorRule?) -> Unit) {
    val current = saved?.takeIf { it.column == column }
    var kind by remember(column, current) { mutableStateOf(current?.mode ?: HaloSheetColorMode.ABOVE) }
    var lower by remember(column, current) { mutableStateOf(current?.lower.orEmpty()) }
    var upper by remember(column, current) { mutableStateOf(current?.upper.orEmpty()) }
    val rule = HaloSheetColorRule(column, kind, lower.trim(), upper.trim())
    Zone6ButtonSheet(uiText("Colour rules for column %1\$s", sheetColumnName(column)), onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HaloSheetColorMode.entries.forEach { option ->
                    FilterChip(kind == option, { kind = option; lower = ""; upper = "" }, label = { Text(uiText(option.label)) })
                }
            }
            if (kind != HaloSheetColorMode.DUPLICATES) OutlinedTextField(lower, { lower = it.take(50) }, singleLine = true,
                label = { Text(uiText(if (kind == HaloSheetColorMode.TOP) "How many values" else "Value")) })
            if (kind == HaloSheetColorMode.BETWEEN) OutlinedTextField(upper, { upper = it.take(50) }, singleLine = true,
                label = { Text(uiText("Upper value")) })
            TextButton(onClick = { onApply(rule) }, enabled = rule.valid()) { Text(uiText("Apply")) }
            if (saved != null) TextButton(onClick = { onApply(null) }) { Text(uiText("Clear colour rule")) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HaloSheetEntryRuleSheet(column: Int, header: Boolean, saved: HaloSheetEntryRule?, ready: Boolean,
    onApply: (HaloSheetEntryRule?) -> Unit, onDismiss: () -> Unit) {
    val current = saved?.takeIf { it.column == column }
    var kind by remember(column, current) { mutableStateOf(current?.kind ?: HaloSheetEntryKind.LIST) }
    var lower by remember(column, current) { mutableStateOf(current?.lower.orEmpty()) }
    var upper by remember(column, current) { mutableStateOf(current?.upper.orEmpty()) }
    val rule = HaloSheetEntryRule(column, kind, lower, upper, header)
    Zone6ButtonSheet(uiText("Allowed entries for column %1\$s", sheetColumnName(column)), onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HaloSheetEntryKind.entries.forEach { option ->
                    FilterChip(kind == option, { kind = option; lower = ""; upper = "" }, label = { Text(uiText(option.label)) }, enabled = ready)
                }
            }
            if (kind == HaloSheetEntryKind.LIST) OutlinedTextField(lower, { lower = it.take(1200) }, enabled = ready,
                label = { Text(uiText("One allowed value per line")) }, minLines = 2, maxLines = 5)
            else {
                val date = kind == HaloSheetEntryKind.DATE
                OutlinedTextField(lower, { lower = it.take(50) }, singleLine = true, enabled = ready,
                    label = { Text(uiText(if (date) "First date (YYYY-MM-DD)" else "Minimum")) })
                OutlinedTextField(upper, { upper = it.take(50) }, singleLine = true, enabled = ready,
                    label = { Text(uiText(if (date) "Last date (YYYY-MM-DD)" else "Maximum")) })
            }
            TextButton(onClick = { onApply(rule) }, enabled = ready && rule.valid()) { Text(uiText("Apply")) }
            if (saved != null) TextButton(onClick = { onApply(null) }, enabled = ready) { Text(uiText("Clear entry rule")) }
        }
    }
}

internal enum class HaloSheetNumberFormat(val displayName: String) {
    RAW("General"), NUMBER("Number"), CURRENCY("Currency"), DATE("Date"), TIME("Time"), PERCENT("Percent"),
}

internal fun formatHaloSheetNumber(value: String, style: HaloSheetNumberFormat, decimals: Int): String {
    val number = sheetNumber(value) ?: return value
    val format = when (style) {
        HaloSheetNumberFormat.RAW -> return value
        HaloSheetNumberFormat.NUMBER -> NumberFormat.getNumberInstance()
        HaloSheetNumberFormat.CURRENCY -> NumberFormat.getCurrencyInstance()
        HaloSheetNumberFormat.PERCENT -> NumberFormat.getPercentInstance()
        HaloSheetNumberFormat.DATE -> return runCatching {
            val days = number.toLong()
            require(days in -365_000L..365_000L)
            LocalDate.of(1899, 12, 30).plusDays(days).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }.getOrDefault(value)
        HaloSheetNumberFormat.TIME -> return runCatching {
            val seconds = number.remainder(java.math.BigDecimal.ONE).multiply(86400.toBigDecimal()).toLong()
            LocalTime.ofSecondOfDay(Math.floorMod(seconds, 86400L)).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        }.getOrDefault(value)
    }
    format.minimumFractionDigits = decimals
    format.maximumFractionDigits = decimals
    return format.format(number)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HaloSheetNumberFormatSheet(saved: HaloSheetNumberFormat, savedDecimals: Int,
    onApply: (Pair<HaloSheetNumberFormat, Int>) -> Unit, onDismiss: () -> Unit) {
    var style by remember { mutableStateOf(saved) }
    var decimals by remember { mutableStateOf(savedDecimals) }
    Zone6ButtonSheet(uiText("Number format"), onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HaloSheetNumberFormat.entries.forEach { type ->
                    FilterChip(type == style, { style = type }, label = { Text(uiText(type.displayName)) })
                }
            }
            if (style in setOf(HaloSheetNumberFormat.NUMBER, HaloSheetNumberFormat.CURRENCY, HaloSheetNumberFormat.PERCENT)) {
                Text(uiText("Decimal places: %1\$d", decimals))
                Slider(decimals.toFloat(), { decimals = it.toInt() }, valueRange = 0f..6f, steps = 5)
            }
            TextButton(onClick = { onApply(style to decimals) }) { Text(uiText("Apply to selected column")) }
        }
    }
}

@Composable
internal fun HaloSheetChartSheet(title: String, data: List<Pair<String, Double>>, onDismiss: () -> Unit) {
    var lineChart by remember { mutableStateOf(false) }
    Zone6ButtonSheet(uiText("Chart"), onDismiss) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (data.isEmpty()) Text(uiText("The selected range contains no numbers."))
            else {
                val foreground = MaterialTheme.colorScheme.primary
                val axis = MaterialTheme.colorScheme.outlineVariant
                if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!lineChart, { lineChart = false }, label = { Text(uiText("Bar")) })
                    FilterChip(lineChart, { lineChart = true }, label = { Text(uiText("Line")) })
                }
                val minimum = minOf(data.minOf { it.second }, 0.0)
                val maximum = maxOf(data.maxOf { it.second }, 0.0)
                val range = (maximum - minimum).coerceAtLeast(1e-12)
                Canvas(Modifier.fillMaxWidth().height(170.dp).padding(vertical = 6.dp)) {
                    fun y(value: Double) = size.height * (1 - ((value - minimum) / range).toFloat())
                    val zero = y(0.0)
                    drawLine(axis, Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())
                    val cellWidth = size.width / data.size
                    if (lineChart) {
                        val path = Path()
                        data.forEachIndexed { index, (_, number) ->
                            val point = Offset(cellWidth * (index + .5f), y(number))
                            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                            drawCircle(foreground, 3.dp.toPx(), point)
                        }
                        drawPath(path, foreground, style = Stroke(2.dp.toPx()))
                    } else data.forEachIndexed { index, (_, number) ->
                        val end = y(number)
                        drawRect(foreground, Offset(cellWidth * (index + .12f), minOf(zero, end)),
                            Size(cellWidth * .76f, kotlin.math.abs(end - zero)))
                    }
                }
                Text(uiText("Rows %1\$s–%2\$s · %3\$d numbers", data.first().first, data.last().first, data.size))
            }
        }
    }
}

internal fun sheetFormulaFromWords(phrase: String, target: HaloSheetCell, column: Int, header: Boolean, rows: Int): String? {
    val words = phrase.lowercase(Locale.ROOT)
    val function = when {
        words.contains("average") || words.contains("mean") -> "AVERAGE"
        words.contains("highest") || words.contains("maximum") || words.contains("largest") -> "MAX"
        words.contains("lowest") || words.contains("minimum") || words.contains("smallest") -> "MIN"
        words.contains("count") || words.contains("how many") -> "COUNT"
        words.contains("add") || words.contains("total") || words.contains("sum") -> "SUM"
        else -> return null
    }
    val range = Regex("\\b[A-Z]{1,2}[1-9][0-9]{0,3}\\b").findAll(phrase.uppercase(Locale.ROOT))
        .mapNotNull { HaloSheetFormulas.parseCell(it.value) }.take(2).toList()
    if (range.size == 2) return "=$function(${range[0].address}:${range[1].address})"
    val label = sheetColumnName(column)
    val start = if (header) 2 else 1
    val end = if (target.column == column) target.row else rows
    if (end < start) return null
    return "=$function($label$start:$label$end)"
}
