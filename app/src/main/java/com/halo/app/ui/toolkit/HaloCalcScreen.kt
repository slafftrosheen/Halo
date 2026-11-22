package com.halo.app.ui.toolkit

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.data.toolkit.CalcUnit
import com.halo.app.data.toolkit.CalcUnitKind
import com.halo.app.data.toolkit.HaloCalcEngine
import com.halo.app.data.toolkit.CurrencyReferenceRates
import com.halo.app.data.toolkit.HaloCurrencyRates
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.instruments.HaloInstrumentAction
import com.halo.app.ui.instruments.HaloInstrumentBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class CalcPane(@StringRes val label: Int) {
    KEYS(R.string.halo11_tool_calc_keys),
    WORDS(R.string.halo11_tool_calc_words),
    TAPE(R.string.halo11_tool_calc_tape),
    UNITS(R.string.halo11_tool_calc_convert),
    GRAPH(R.string.halo11_tool_calc_graph),
}

private data class CalcHistoryLine(val input: String, val value: String)

private class CalcTape(context: Context, child: Boolean) {
    private val store = context.getSharedPreferences(if (child) "halo_calc_kids_tape" else "halo_calc_tape", Context.MODE_PRIVATE)

    fun recent(): List<CalcHistoryLine> = synchronized(lock) {
        val rows = JSONArray(store.getString("calculations", "[]"))
        (rows.length() - 1 downTo (rows.length() - historyLimit).coerceAtLeast(0)).map { index ->
            val row = rows.getJSONObject(index)
            CalcHistoryLine(row.getString("input"), row.getString("value"))
        }
    }

    fun append(line: CalcHistoryLine) = synchronized(lock) {
        val stored = JSONArray(store.getString("calculations", "[]"))
        val rows = JSONArray()
        for (index in (stored.length() - historyLimit + 1).coerceAtLeast(0) until stored.length()) rows.put(stored.get(index))
        rows.put(JSONObject().put("input", line.input).put("value", line.value))
        check(store.edit().putString("calculations", rows.toString()).commit())
    }

    private companion object {
        const val historyLimit = 100
        val lock = Any()
    }
}

@Composable
internal fun HaloCalcScreen(onBack: () -> Unit, onCoach: (() -> Unit)? = null) {
    val family = ManualDependencyGraph.kidsPreferences
    val child by family.enabled.collectAsStateWithLifecycle()
    val familyReady by family.loadedState.collectAsStateWithLifecycle()
    if (!familyReady) {
        Halo11ToolHeader(R.string.halo11_tool_calc_title, onBack)
        return
    }
    key(child) { CalcWorkspace(child, onBack, onCoach) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalcWorkspace(child: Boolean, onBack: () -> Unit, onCoach: (() -> Unit)?) {
    val context = LocalContext.current.applicationContext
    val tape = remember(context, child) { CalcTape(context, child) }
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf(CalcPane.KEYS) }
    var expression by rememberSaveable { mutableStateOf("") }
    var exactResult by rememberSaveable { mutableStateOf<String?>(null) }
    var resolvedExpression by rememberSaveable { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(emptyList<CalcHistoryLine>()) }
    var saving by remember { mutableStateOf(false) }
    var explaining by remember { mutableStateOf(false) }
    var error by remember { mutableIntStateOf(0) }
    LaunchedEffect(tape) {
        history = withContext(Dispatchers.IO) { runCatching { tape.recent() }.getOrDefault(emptyList()) }
    }

    fun write(value: String) {
        expression = value.take(180)
        exactResult = null
        resolvedExpression = null
        error = 0
    }

    fun evaluate() {
        val input = expression.trim()
        if (saving || input.isEmpty()) return
        val calculated = try { HaloCalcEngine.calculate(input) }
        catch (_: IllegalArgumentException) { error = R.string.halo11_tool_calc_invalid; return }
        catch (_: ArithmeticException) { error = R.string.halo11_tool_calc_invalid; return }
        val value = calculated.result.stripTrailingZeros().toString()
        exactResult = value
        resolvedExpression = calculated.expression
        error = 0
        saving = true
        scope.launch {
            try {
                history = withContext(Dispatchers.IO) {
                    tape.append(CalcHistoryLine(input, HaloCalcEngine.display(calculated.result)))
                    tape.recent()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = R.string.halo11_tool_calc_tape_failed }
            finally { saving = false }
        }
    }

    fun explain() {
        if (!child) explaining = true
        else onCoach?.invoke()
    }

    fun select(pane: CalcPane) { selected = pane }
    val explanationEnabled = if (child) onCoach != null else exactResult != null
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Halo11ToolHeader(R.string.halo11_tool_calc_title, onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (selected) {
                CalcPane.KEYS, CalcPane.WORDS -> {
                    HaloTextField(expression, ::write, modifier = Modifier.fillMaxWidth(),
                        kind = VoiceFieldKind.TEXT, singleLine = true,
                        label = { Text(stringResource(R.string.halo11_tool_calc_input)) })
                    Text(exactResult?.toBigDecimalOrNull()?.let(HaloCalcEngine::display).orEmpty(),
                        style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Monospace))
                    if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                    if (selected == CalcPane.KEYS) {
                        CalcKeypad(append = { key ->
                            val current = exactResult
                            write(if (current != null && key in setOf("+", "−", "×", "÷", "^", "%")) current + key else expression + key)
                        }, clear = { write("") }, erase = { write(expression.dropLast(1)) })
                    } else Text(stringResource(R.string.halo11_tool_calc_example), style = MaterialTheme.typography.bodySmall)
                    Button(::evaluate, enabled = !saving && expression.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.halo11_tool_recalculate))
                    }
                    if (explanationEnabled) TextButton(::explain) { Text(stringResource(R.string.halo11_tool_calc_explain)) }
                    if (child && onCoach == null)
                        Text(stringResource(R.string.halo11_tool_calc_explain_child), style = MaterialTheme.typography.bodySmall)
                }
                CalcPane.TAPE -> CalcTapeContent(history) { write(it.input); select(CalcPane.KEYS) }
                CalcPane.UNITS -> CalcConversion(initial = exactResult ?: expression, adult = !child)
                CalcPane.GRAPH -> CalcGraph()
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcPane.entries.forEach { pane ->
                FilterChip(selected == pane, { select(pane) }, label = { Text(stringResource(pane.label)) })
            }
        }
        HaloInstrumentBar("calc", mapOf(
            "calc.keys" to HaloInstrumentAction({ select(CalcPane.KEYS) }, selected = selected == CalcPane.KEYS),
            "calc.tape" to HaloInstrumentAction({ select(CalcPane.TAPE) }, selected = selected == CalcPane.TAPE),
            "calc.words" to HaloInstrumentAction({ select(CalcPane.WORDS) }, selected = selected == CalcPane.WORDS),
            "calc.units" to HaloInstrumentAction({ select(CalcPane.UNITS) }, selected = selected == CalcPane.UNITS),
            "calc.graph" to HaloInstrumentAction({ select(CalcPane.GRAPH) }, selected = selected == CalcPane.GRAPH),
            "calc.explain" to HaloInstrumentAction(::explain, enabled = explanationEnabled),
        ), primary = "calc.keys")
    }
    if (explaining && !child) {
        val result = exactResult?.toBigDecimalOrNull()?.let(HaloCalcEngine::display).orEmpty()
        val stages = remember(resolvedExpression) {
            resolvedExpression?.let { input -> runCatching { HaloCalcEngine.calculate(input).steps }.getOrDefault(emptyList()) }.orEmpty()
        }
        AlertDialog(onDismissRequest = { explaining = false },
            title = { Text(stringResource(R.string.halo11_tool_calc_explain)) },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.halo11_tool_calc_explain_eq, resolvedExpression.orEmpty()))
                if (stages.isEmpty()) Text(stringResource(R.string.halo11_tool_calc_explain_order))
                else stages.forEach { stage -> Text(stage, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)) }
                Text(result, style = MaterialTheme.typography.titleLarge)
            } },
            confirmButton = { TextButton({ explaining = false }) { Text(stringResource(R.string.halo11_tool_close)) } },
        )
    }
}

@Composable
internal fun Halo11ToolHeader(@StringRes title: Int, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        TextButton(onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.halo11_tool_back)) }
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun CalcKeypad(append: (String) -> Unit, clear: () -> Unit, erase: () -> Unit) {
    var scientific by rememberSaveable { mutableStateOf(false) }
    TextButton({ scientific = !scientific }) {
        Text(stringResource(if (scientific) R.string.halo11_tool_calc_basic else R.string.halo11_tool_calc_scientific))
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        val rows = listOf("7 8 9 ÷", "4 5 6 ×", "1 2 3 −", "0 . % +", "( ) ^ π") +
            if (scientific) listOf("sqrt( abs( sin( cos(", "tan( ln( log( exp(") else emptyList()
        rows.forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.split(' ').forEach { token ->
                FilledTonalButton({ append(token) }, Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 3.dp)) { Text(token) }
            }
        } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(erase, Modifier.weight(1f)) { Text(stringResource(R.string.halo11_tool_calc_delete)) }
            TextButton(clear, Modifier.weight(1f)) { Text(stringResource(R.string.halo11_tool_calc_clear)) }
        }
    }
}

@Composable
private fun CalcTapeContent(lines: List<CalcHistoryLine>, reuse: (CalcHistoryLine) -> Unit) {
    Text(stringResource(R.string.halo11_tool_calc_tape), style = MaterialTheme.typography.titleLarge)
    if (lines.isEmpty()) Text(stringResource(R.string.halo11_tool_calc_empty))
    lines.forEach { line ->
        Card({ reuse(line) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(line.input, style = MaterialTheme.typography.bodyMedium)
                Text(line.value, style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace))
            }
        }
    }
}

@Composable
private fun CalcConversion(initial: String, adult: Boolean) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf(initial) }
    var from by rememberSaveable { mutableStateOf(CalcUnit.M) }
    var to by rememberSaveable { mutableStateOf(CalcUnit.CM) }
    var rate by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var resultDate by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var quote by remember { mutableStateOf<CurrencyReferenceRates?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshFailed by remember { mutableStateOf(false) }
    val needsRate = from.kind == CalcUnitKind.CURRENCY && from != to
    val sourceRate = quote?.crossRate(from, to)
    LaunchedEffect(adult) {
        if (adult) quote = HaloCurrencyRates.saved(context)
    }
    OutlinedTextField(input, { input = it.take(180); result = null }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(stringResource(R.string.halo11_tool_calc_input)) })
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        UnitMenu(from, CalcUnit.entries, R.string.halo11_tool_calc_from, Modifier.weight(1f)) {
            from = it
            if (to.kind != it.kind) to = CalcUnit.entries.first { unit -> unit.kind == it.kind }
            rate = ""; result = null
        }
        UnitMenu(to, CalcUnit.entries.filter { it.kind == from.kind }, R.string.halo11_tool_calc_to, Modifier.weight(1f)) {
            to = it; rate = ""; result = null
        }
    }
    if (needsRate) {
        if (adult) {
            if (sourceRate != null) {
                Text(stringResource(R.string.halo11_calc_ecb_saved, quote?.date.toString(), from.symbol, sourceRate, to.symbol),
                    style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = {
                val family = ManualDependencyGraph.kidsPreferences
                val revision = runCatching { family.adultActionRevision() }.getOrNull() ?: return@TextButton
                refreshing = true
                refreshFailed = false
                scope.launch {
                    try {
                        quote = HaloCurrencyRates.refresh(context) {
                            !ManualDependencyGraph.isHaloPaused() && runCatching { family.requireAdultAction(revision) }.isSuccess
                        }
                        result = null
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { refreshFailed = true }
                    finally { refreshing = false }
                }
            }, enabled = !refreshing && to != CalcUnit.RUB && from != CalcUnit.RUB,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (refreshing) R.string.halo11_calc_ecb_refreshing else R.string.halo11_calc_ecb_refresh))
            }
            if (refreshFailed) Text(stringResource(R.string.halo11_calc_ecb_failed),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (from == CalcUnit.RUB || to == CalcUnit.RUB) {
                Text(stringResource(R.string.halo11_calc_ecb_rub), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(stringResource(R.string.halo11_tool_calc_manual))
        OutlinedTextField(rate, { rate = it.take(32); result = null }, Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            label = { Text(stringResource(R.string.halo11_tool_calc_rate, from.symbol, to.symbol)) })
    }
    Button(onClick = {
        failed = false
        val reference = rate.isBlank() && needsRate
        resultDate = quote?.date?.toString().takeIf { reference }
        result = runCatching { HaloCalcEngine.display(HaloCalcEngine.convert(input, from, to,
            if (needsRate) rate.takeIf(String::isNotBlank) ?: sourceRate else null)) }
            .onFailure { failed = true }.getOrNull()
    }, enabled = input.isNotBlank() && (!needsRate || rate.isNotBlank() || sourceRate != null)) {
        Text(stringResource(R.string.halo11_tool_calc_convert_action))
    }
    result?.let { value ->
        val displayed = when {
            !needsRate -> "$value ${to.symbol}"
            resultDate != null -> stringResource(R.string.halo11_calc_ecb_output, value, to.symbol, resultDate.orEmpty())
            else -> stringResource(R.string.halo11_tool_calc_rate_output, value, to.symbol)
        }
        Text(displayed,
            style = MaterialTheme.typography.titleLarge)
    }
    if (failed) Text(stringResource(R.string.halo11_tool_calc_invalid), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun UnitMenu(
    selected: CalcUnit, units: List<CalcUnit>, @StringRes label: Int, modifier: Modifier = Modifier,
    onSelect: (CalcUnit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton({ open = true }, Modifier.fillMaxWidth()) {
            Text(stringResource(label) + ": " + selected.symbol)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            units.forEach { unit -> DropdownMenuItem({ Text(unit.symbol) }, onClick = { open = false; onSelect(unit) }) }
        }
    }
}

@Composable
private fun CalcGraph() {
    var formula by rememberSaveable { mutableStateOf("x^2") }
    var first by rememberSaveable { mutableStateOf("-10") }
    var last by rememberSaveable { mutableStateOf("10") }
    var samples by remember { mutableStateOf(emptyList<HaloCalcEngine.Sample>()) }
    var plotted by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var point by remember { mutableIntStateOf(0) }
    OutlinedTextField(formula, { formula = it.take(180) }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(stringResource(R.string.halo11_tool_calc_formula)) })
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(first, { first = it.take(16) }, Modifier.weight(1f), singleLine = true,
            label = { Text(stringResource(R.string.halo11_tool_calc_min)) })
        OutlinedTextField(last, { last = it.take(16) }, Modifier.weight(1f), singleLine = true,
            label = { Text(stringResource(R.string.halo11_tool_calc_max)) })
    }
    Button(onClick = {
        failed = false
        samples = runCatching { HaloCalcEngine.graph(formula, first.replace(',', '.').toDouble(), last.replace(',', '.').toDouble()) }
            .onFailure { failed = true }.getOrDefault(emptyList())
        point = samples.size / 2
        plotted = formula
    }) { Text(stringResource(R.string.halo11_tool_calc_plot)) }
    if (samples.isNotEmpty()) {
        val selected = samples[point]
        CalcGraphCanvas(samples, point, { point = it }, label = stringResource(R.string.halo11_tool_calc_graph_summary, plotted))
        Slider(point.toFloat(), { point = it.roundToInt() }, valueRange = 0f..samples.lastIndex.toFloat(),
            modifier = Modifier.semantics { contentDescription = plotted })
        val y = selected.y?.toBigDecimal()?.let(HaloCalcEngine::display) ?: stringResource(R.string.halo11_tool_calc_missing_y)
        Text(stringResource(R.string.halo11_tool_calc_trace, HaloCalcEngine.display(selected.x.toBigDecimal()), y))
        Text(stringResource(R.string.halo11_tool_calc_tap_trace), style = MaterialTheme.typography.bodySmall)
    }
    if (failed) Text(stringResource(R.string.halo11_tool_calc_invalid), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun CalcGraphCanvas(samples: List<HaloCalcEngine.Sample>, selected: Int, onSelect: (Int) -> Unit, label: String) {
    val axis = MaterialTheme.colorScheme.outline
    val ink = MaterialTheme.colorScheme.primary
    val ys = remember(samples) { samples.mapNotNull { it.y }.sorted() }
    val low = ys[ys.size / 40]
    val high = ys[ys.lastIndex - ys.size / 40]
    val fit = maxOf(abs(high), abs(low), 1e-300)
    val lowNormalized = low / fit
    val range = high / fit - lowNormalized
    val current by rememberUpdatedState(onSelect)
    Canvas(Modifier.fillMaxWidth().height(210.dp).semantics { contentDescription = label }
        .pointerInput(samples.size) {
            detectTapGestures { offset ->
                current((offset.x / size.width * samples.lastIndex).roundToInt().coerceIn(samples.indices))
            }
        }) {
        fun x(index: Int) = size.width * index / samples.lastIndex
        fun y(value: Double) = if (range <= 1e-14) size.height / 2
            else (size.height * (1 - (value / fit - lowNormalized) / range)).toFloat()
        if (0.0 in low..high) drawLine(axis, Offset(0f, y(0.0)), Offset(size.width, y(0.0)))
        for (index in 1..samples.lastIndex) {
            val before = samples[index - 1].y
            val after = samples[index].y
            if (before == null || after == null) continue
            val start = y(before)
            val end = y(after)
            if (start in 0f..size.height && end in 0f..size.height && abs(start - end) < size.height * .7f)
                drawLine(ink, Offset(x(index - 1), start), Offset(x(index), end), strokeWidth = 2.dp.toPx())
        }
        drawLine(axis, Offset(x(selected), 0f), Offset(x(selected), size.height))
        samples[selected].y?.let { y(it) }?.takeIf { it in 0f..size.height }?.let { dot ->
            drawCircle(ink, radius = 4.dp.toPx(), center = Offset(x(selected), dot))
        }
    }
}
