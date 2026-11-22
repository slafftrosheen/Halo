package com.halo.app.ui.toolkit

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.halo.app.R
import com.halo.app.data.workspace.*
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.instruments.HaloInstrumentAction
import com.halo.app.ui.instruments.HaloInstrumentBar
import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.LocalShellHaloOrb
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner
import com.halo.app.ui.theme.HaloViewMode
import com.halo.app.ui.theme.rememberVisualPreferences
import com.halo.app.ui.workspace.WorkspaceFileToolsViewModel
import com.halo.app.ui.zone6.Zone6ButtonSheet
import com.halo.app.ui.zone6.haloEditablePageTitle
import com.halo.app.ui.zone6.rememberZone6PageSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SheetDialog { CREATE, SORT, ASK, APPEARANCE, FORMULA, ASK_FORMULA, FILTER, DUPLICATES, TABS, FILE, EXPLAIN, CHART, NUMBER_FORMAT, FREEZE, COLOR_RULE, ENTRY_RULE }
private data class SelectedCell(val revision: Long, val row: Int, val column: Int)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun HaloSheetsRoute(savedState: SavedStateHandle, objectId: String?, sourceVersion: String?, onBack: () -> Unit) {
    val model: WorkspaceFileToolsViewModel = viewModel(factory = WorkspaceFileToolsViewModel.Factory(
        ManualDependencyGraph.workspaceFileTools, null, savedState))
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(objectId, sourceVersion) {
        val id = objectId ?: return@LaunchedEffect
        val version = sourceVersion ?: return@LaunchedEffect
        if (savedState.get<Boolean>("sheets_handoff_claimed") == true) return@LaunchedEffect
        try {
            val source = ManualDependencyGraph.workspaceContinuity.current(id, version)
            check(WorkspaceDestination.SHEETS in source.destinations)
            savedState["sheets_handoff_claimed"] = true
            model.open(id)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { model.reportError() }
    }
    val objects by model.objects.collectAsStateWithLifecycle()
    val files by model.files.collectAsStateWithLifecycle()
    val sheets by produceState(emptyList<WorkspaceTextDocument>(), objects, state.text?.revision) {
        value = withContext(Dispatchers.IO) {
            objects.take(150).mapNotNull { item ->
                try { model.tools.text.get(item.id.value)?.takeIf { it.format == WorkspaceTextFormat.CSV } }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: IllegalArgumentException) { null }
            }
        }
    }
    val sourceFiles = files.filter { WorkspaceTextFormat.fromFile(it.displayName, it.mimeType) == WorkspaceTextFormat.CSV }
    val document = state.text?.takeIf { it.format == WorkspaceTextFormat.CSV }
    val table = remember(document?.text) { document?.let { WorkspaceCsv.parse(it.text) } }
    val formulaGrid = remember(table) { table?.let(::HaloSheetFormulas) }
    val scope = rememberCoroutineScope()
    val setting = rememberZone6PageSetting("sheets.headers.v1")
    val freezeSetting = rememberZone6PageSetting("sheets.freeze.v1.${document?.id.orEmpty()}")
    val colorSetting = rememberZone6PageSetting("sheets.colour.v1.${document?.id.orEmpty()}")
    val numberSetting = rememberZone6PageSetting("sheets.number.v1.${document?.id.orEmpty()}")
    val frozenRows = freezeSetting.value?.substringBefore('/')?.toIntOrNull()?.coerceIn(0, 5) ?: 0
    val frozenColumns = freezeSetting.value?.substringAfter('/')?.toIntOrNull()?.coerceIn(0, 3) ?: 0
    val colorRule = remember(colorSetting.value) { HaloSheetColorRule.decode(colorSetting.value) }
    val numberFields = remember(numberSetting.value) { numberSetting.value.orEmpty().split('/') }
    val header = setting.value != "false"
    val shell = LocalShellHaloOrb.current
    val easy = rememberVisualPreferences().viewMode == HaloViewMode.EASY
    var dialog by remember { mutableStateOf<SheetDialog?>(null) }
    var cell by remember { mutableStateOf<SelectedCell?>(null) }
    var activeCell by remember(document?.id) { mutableStateOf<HaloSheetCell?>(null) }
    var rangeAnchor by remember(document?.id) { mutableStateOf<HaloSheetCell?>(null) }
    var rangeMode by rememberSaveable(document?.id) { mutableStateOf(false) }
    var visibleFilter by rememberSaveable(document?.id) { mutableStateOf("") }
    val numberFormat = HaloSheetNumberFormat.entries.firstOrNull { it.name == numberFields.getOrNull(0) } ?: HaloSheetNumberFormat.RAW
    val formatDecimals = numberFields.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 6) ?: 2
    val formattedColumn = numberFields.getOrNull(2)?.toIntOrNull() ?: -1
    var column by rememberSaveable(document?.id) { mutableIntStateOf(0) }
    var rowFilter by rememberSaveable(document?.id) { mutableStateOf("") }
    var operation by rememberSaveable(document?.id) { mutableStateOf<SheetCalculation?>(null) }
    var savedError by remember { mutableStateOf(false) }
    var entryRule by remember(document?.id) { mutableStateOf(document?.id?.let(model.tools.text::sheetEntryRule)) }
    val scroll = rememberScrollState()
    val sheetWidthDp = (LocalConfiguration.current.screenWidthDp - 32).coerceAtLeast(120)
    val columnCount = table?.rows?.maxOf { it.size } ?: 0
    val activeColumn = column.coerceIn(0, (columnCount - 1).coerceAtLeast(0))
    val cursor = activeCell?.takeIf { table != null && it.row in table.rows.indices && it.column in 0 until columnCount }
    val anchor = rangeAnchor?.takeIf { table != null && it.row in table.rows.indices && it.column in 0 until columnCount }
    val selectedRows = if (cursor != null && anchor != null) minOf(cursor.row, anchor.row)..maxOf(cursor.row, anchor.row) else IntRange.EMPTY
    val selectedColumns = if (cursor != null && anchor != null) minOf(cursor.column, anchor.column)..maxOf(cursor.column, anchor.column) else IntRange.EMPTY
    val answer = remember(table, activeColumn, header, operation, rowFilter) {
        if (table != null && operation != null) table.calculate(activeColumn, header, requireNotNull(operation), rowFilter)
        else null
    }

    val openCreate = { dialog = SheetDialog.CREATE }
    val openCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importCsv(uri)
    }
    val addRow = {
        document?.let { model.editCsv(it.id, it.revision, WorkspaceCsvCommand.AddRow) }
        Unit
    }
    val goBack = { if (document != null) model.closeSelection() else onBack() }
    val toolbar = buildList {
        if (document == null) add(ShellPageAction("page.sheets.create", stringResource(R.string.sheets_create),
            HaloGlyph.FILE, !state.busy, openCreate))
        else {
            add(ShellPageAction("page.sheets.add", stringResource(R.string.sheets_add_row), HaloGlyph.FILE,
                !state.busy && (table?.rows?.size ?: 0) < WorkspaceCsv.MAX_ROWS, addRow))
            add(ShellPageAction("page.sheets.sum", stringResource(R.string.sheets_total), HaloGlyph.ACTION) {
                rowFilter = ""; operation = SheetCalculation.TOTAL
            })
            add(ShellPageAction("page.sheets.sort", stringResource(R.string.sheets_sort), HaloGlyph.TOOL, !state.busy) {
                dialog = SheetDialog.SORT
            })
            add(ShellPageAction("page.sheets.ask", stringResource(R.string.sheets_ask), HaloGlyph.QUESTION) {
                dialog = SheetDialog.ASK
            })
        }
        add(ShellPageAction("page.sheets.appearance", stringResource(R.string.coach_edit_page), HaloGlyph.SETTINGS) {
            savedError = false; dialog = SheetDialog.APPEARANCE
        })
        add(ShellPageAction("page.sheets.back", stringResource(R.string.sheets_back), HaloGlyph.PREVIOUS, !state.busy, goBack))
    }
    BindShellPageActions(ShellPageOwner("sheets", sourceIdentity = document?.let { "${it.id}:${it.revision}" },
        canonicalSourceVersion = document?.revision?.toString(), activeToolId = "sheets",
        canonicalToolIds = listOf("halo_text_create", "halo_text_import", "halo_text_get", "halo_text_edit", "halo_sheets_analyze", "halo_sheets_delete"),
        helpDescription = stringResource(R.string.sheets_help), guidanceBlocked = state.busy || dialog != null || cell != null), toolbar)
    BackHandler(enabled = document != null && dialog == null && cell == null) { if (!state.busy) model.closeSelection() }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
        item {
            Text(document?.document?.title ?: stringResource(R.string.toolkit_sheets),
                modifier = Modifier.haloEditablePageTitle().semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.error) Text(stringResource(R.string.file52_error), color = MaterialTheme.colorScheme.error)
            if (document != null && table != null) {
                val ready = !state.busy
                val hasData = table.rows.size > (if (header) 1 else 0)
                val totalFirst = if (header) 2 else 1
                val totalColumn = sheetColumnName(activeColumn)
                val totalFormula = "=SUM($totalColumn$totalFirst:$totalColumn${table.rows.size})"
                val previousTotal = "=SUM($totalColumn$totalFirst:$totalColumn${table.rows.size - 1})"
                val alreadyTotalled = table.rows.lastOrNull()?.getOrNull(activeColumn) == previousTotal
                val fallbackCell = HaloSheetCell(if (header && table.rows.size > 1) 1 else 0, activeColumn)
                fun openFormula(next: SheetDialog) {
                    val target = cursor ?: fallbackCell
                    activeCell = target
                    cell = SelectedCell(document.revision, target.row, target.column)
                    dialog = next
                }
                HaloInstrumentBar("sheets", mapOf(
                    "sheets.cell_select_range" to HaloInstrumentAction({ rangeMode = !rangeMode; rangeAnchor = null }, ready, selected = rangeMode),
                    "sheets.formula_bar" to HaloInstrumentAction({ openFormula(SheetDialog.FORMULA) }, ready),
                    "sheets.ask_in_words" to HaloInstrumentAction({ openFormula(SheetDialog.ASK_FORMULA) }, ready && hasData),
                    "sheets.fill_down_series" to HaloInstrumentAction({
                        model.editCsv(document.id, document.revision, WorkspaceCsvCommand.FillDown(selectedRows.first, selectedRows.last,
                            selectedColumns.first, selectedColumns.last))
                    }, ready && selectedRows.first < selectedRows.last, working = state.busy),
                    "sheets.number_format" to HaloInstrumentAction({ dialog = SheetDialog.NUMBER_FORMAT }, ready),
                    "sheets.sort" to HaloInstrumentAction({ dialog = SheetDialog.SORT }, ready && hasData),
                    "sheets.filter" to HaloInstrumentAction({ dialog = SheetDialog.FILTER }, ready && hasData, selected = visibleFilter.isNotBlank()),
                    "sheets.colour_rules" to HaloInstrumentAction({ dialog = SheetDialog.COLOR_RULE }, ready && hasData, selected = colorRule != null),
                    "sheets.charts" to HaloInstrumentAction({ dialog = SheetDialog.CHART }, ready && hasData),
                    "sheets.freeze_rows_columns" to HaloInstrumentAction({ dialog = SheetDialog.FREEZE }, ready, selected = frozenRows > 0 || frozenColumns > 0),
                    "sheets.add_up_button" to HaloInstrumentAction({
                        model.editCsv(document.id, document.revision, WorkspaceCsvCommand.AddTotal(activeColumn, totalFormula))
                    }, ready && hasData && !alreadyTotalled && table.rows.size < WorkspaceCsv.MAX_ROWS, working = state.busy),
                    "sheets.explain_this_sheet" to HaloInstrumentAction({ dialog = SheetDialog.EXPLAIN }, ready),
                    "sheets.check_entries" to HaloInstrumentAction({ dialog = SheetDialog.ENTRY_RULE }, ready && hasData, selected = entryRule != null),
                    "sheets.remove_duplicates" to HaloInstrumentAction({ dialog = SheetDialog.DUPLICATES }, ready && table.rows.size > (if (header) 2 else 1)),
                    "sheets.sheet_tabs" to HaloInstrumentAction({ dialog = SheetDialog.TABS }, ready),
                    "sheets.import_export" to HaloInstrumentAction({ dialog = SheetDialog.FILE }, ready),
                ), primary = "sheets.add_up_button")
            } else HaloInstrumentBar("sheets", mapOf(
                "sheets.sheet_tabs" to HaloInstrumentAction({ dialog = SheetDialog.TABS }, !state.busy),
                "sheets.import_export" to HaloInstrumentAction({ dialog = SheetDialog.FILE }, !state.busy),
            ))
            state.output?.let { saved -> Text(uiText("Saved a copy of %1\$s in Workspace.", saved.displayName)) }
            if (!shell) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                toolbar.filter { !easy || it.id.substringAfterLast('.') in setOf("add","sum","sort","ask","create") }
                    .forEach { TextButton(it.onInvoke, enabled = it.enabled) { Text(it.title) } }
            }
        }
        if (table == null || document == null) {
            item { Text(stringResource(R.string.sheets_help)) }
            itemsIndexed(sheets, key = { _, sheet -> sheet.id }) { _, sheet ->
                OutlinedButton({ model.open(sheet.id) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(sheet.document.title)
                }
            }
            itemsIndexed(sourceFiles, key = { _, file -> "file:" + file.objectId.value }) { _, file ->
                TextButton({ model.importText(file) }, enabled = !state.busy && file.version != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.sheets_open_copy, file.displayName))
                }
            }
        } else {
            item {
                var choosing by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton({ choosing = true }) {
                        Text(stringResource(R.string.sheets_column, activeColumn + 1))
                        if (header) table.rows[0].getOrNull(activeColumn)?.takeIf(String::isNotBlank)?.let {
                            Text(" • $it", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    DropdownMenu(choosing, { choosing = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                        repeat(columnCount) { index ->
                            val label = table.rows[0].getOrNull(index).orEmpty()
                            DropdownMenuItem(text = { Text(if (header && label.isNotBlank()) label else
                                stringResource(R.string.sheets_column, index + 1)) }, onClick = { column = index; choosing = false })
                        }
                    }
                }
                cursor?.let { selected ->
                    val raw = table.rows.getOrNull(selected.row)?.getOrNull(selected.column).orEmpty()
                    TextButton(onClick = {
                        cell = SelectedCell(document.revision, selected.row, selected.column)
                        dialog = SheetDialog.FORMULA
                    }, enabled = !state.busy) {
                        Text("${selected.address}    ${raw.ifBlank { "ƒx" }}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            val visibleRows = table.rows.indices.filter { row ->
                row < frozenRows || (header && row == 0) || visibleFilter.isBlank() || (0 until columnCount).any { col ->
                    formulaGrid?.display(row, col).orEmpty().contains(visibleFilter, ignoreCase = true)
                }
            }
            val colorMatches = colorRule?.let { rule ->
                val values = table.rows.indices.filterNot { header && it == 0 }.associateWith { formulaGrid?.display(it, rule.column).orEmpty() }
                rule.matches(values)
            }.orEmpty()
            val pinnedColumns = frozenColumns.coerceAtMost(columnCount)
            @Composable fun SheetRow(index: Int) {
                @Composable fun Cell(col: Int) {
                        val location = HaloSheetCell(index, col)
                        val selected = cursor == location || index in selectedRows && col in selectedColumns
                        val calculated = formulaGrid?.display(index, col).orEmpty()
                        val value = if (col == formattedColumn) formatHaloSheetNumber(calculated, numberFormat, formatDecimals) else calculated
                        val rule = entryRule?.takeIf { it.column == col && (!it.header || index > 0) }
                        val invalid = rule != null && !rule.accepts(calculated)
                        val defaultWidth = if (easy) 142 else 110
                        val width = if (col < pinnedColumns) minOf(defaultWidth, sheetWidthDp / (pinnedColumns + 1)) else defaultWidth
                        Text(value, modifier = Modifier.width(width.dp).heightIn(min = 48.dp)
                            .then(if (colorRule?.column == col && index in colorMatches) Modifier.background(MaterialTheme.colorScheme.tertiaryContainer) else Modifier)
                            .then(if (invalid) Modifier.border(BorderStroke(2.dp, androidx.compose.ui.graphics.Color(0xFFE7A070))) else Modifier)
                            .then(if (selected) Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) else Modifier)
                            .clickable(enabled = !state.busy) {
                                activeCell = location
                                column = col
                                if (rangeMode) {
                                    if (rangeAnchor == null) rangeAnchor = location
                                } else cell = SelectedCell(document.revision, index, col)
                            }.padding(8.dp),
                            textAlign = if (sheetNumber(calculated) != null) TextAlign.End else TextAlign.Start,
                            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth()
                    .background(if (index in answer?.matchingRows.orEmpty()) MaterialTheme.colorScheme.secondaryContainer
                        else if (index == 0 && header || index < frozenRows) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.surface),
                    verticalAlignment = Alignment.CenterVertically) {
                    repeat(pinnedColumns) { Cell(it) }
                    if (pinnedColumns < columnCount) {
                        Row(Modifier.weight(1f).horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
                            for (col in pinnedColumns until columnCount) Cell(col)
                        }
                    }
                }
            }
            if (frozenRows > 0) stickyHeader(key = "sheet-frozen-rows") {
                Column { visibleRows.filter { it < frozenRows }.forEach { SheetRow(it) } }
            }
            itemsIndexed(visibleRows.filter { it >= frozenRows }, key = { _, row -> row }) { _, index -> SheetRow(index) }
            if (answer != null) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val value = answer.value?.stripTrailingZeros()?.toPlainString()
                        Text(if (value == null) stringResource(R.string.sheets_no_numbers) else stringResource(
                            when (answer.operation) {
                                SheetCalculation.TOTAL -> R.string.sheets_together
                                SheetCalculation.HIGHEST -> R.string.sheets_highest_answer
                                SheetCalculation.LOWEST -> R.string.sheets_lowest_answer
                            }, value), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.sheets_count, answer.numbers.size))
                        val bars = answer.numbers.take(8)
                        val range = bars.maxOfOrNull { it.value.abs().toDouble() }?.coerceAtLeast(1.0) ?: 1.0
                        if (answer.operation != SheetCalculation.TOTAL) bars.forEach { point ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.sheets_row, point.row + 1), Modifier.width(78.dp))
                                Box(Modifier.weight(1f)) {
                                    Box(Modifier.fillMaxWidth((point.value.abs().toDouble() / range).toFloat()).height(10.dp)
                                        .background(MaterialTheme.colorScheme.tertiary))
                                }
                                Text(point.value.stripTrailingZeros().toPlainString())
                            }
                        }
                    }
                }
            }
        }
    }

    if (dialog == SheetDialog.CREATE) {
        var title by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(R.string.sheets_create)) }, text = {
            OutlinedTextField(title, { title = it.take(120) }, singleLine = true, label = { Text(stringResource(R.string.sheets_name)) })
        }, confirmButton = {
            val name = stringResource(R.string.sheets_header_name)
            val amount = stringResource(R.string.sheets_header_amount)
            Button({ model.create(title.trim(), WorkspaceTextFormat.CSV, WorkspaceCsv(listOf(listOf(name, amount), listOf("", ""))).encode())
                dialog = null }, enabled = title.isNotBlank() && !state.busy) { Text(stringResource(R.string.sheets_create)) }
        }, dismissButton = { TextButton({ dialog = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
    if (dialog == SheetDialog.SORT && document != null) Zone6ButtonSheet(stringResource(R.string.sheets_sort), { dialog = null }) {
        listOf(false to R.string.sheets_ascending, true to R.string.sheets_descending).forEach { (descending, label) ->
            TextButton({
                model.editCsv(document.id, document.revision, WorkspaceCsvCommand.SortRows(activeColumn, header, descending))
                dialog = null
            }, enabled = !state.busy) { Text(stringResource(label)) }
        }
    }
    if (dialog == SheetDialog.ASK) {
        var query by remember { mutableStateOf(rowFilter) }
        Zone6ButtonSheet(stringResource(R.string.sheets_ask), { dialog = null }) {
            OutlinedTextField(query, { query = it.take(120) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true, label = { Text(stringResource(R.string.sheets_filter)) })
            listOf(SheetCalculation.TOTAL to R.string.sheets_total, SheetCalculation.HIGHEST to R.string.sheets_highest,
                SheetCalculation.LOWEST to R.string.sheets_lowest).forEach { (kind, label) ->
                TextButton({ operation = kind; rowFilter = query; dialog = null }) { Text(stringResource(label)) }
            }
        }
    }
    if (dialog == SheetDialog.APPEARANCE) Zone6ButtonSheet(stringResource(R.string.coach_edit_page), { dialog = null }) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sheets_use_headers), Modifier.weight(1f))
            Switch(header, { next ->
                scope.launch { savedError = !setting.apply(setting.value, next.toString(), "true") }
            })
        }
        if (savedError) Text(stringResource(R.string.page_layout_error))
    }
    if (dialog == SheetDialog.FILTER) {
        var query by remember { mutableStateOf(visibleFilter) }
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(uiText("Filter rows")) }, text = {
            OutlinedTextField(query, { query = it.take(120) }, singleLine = true, label = { Text(uiText("Contains text")) })
        }, confirmButton = { TextButton(onClick = { visibleFilter = query.trim(); dialog = null }) { Text(uiText("Apply")) } },
            dismissButton = { TextButton(onClick = { visibleFilter = ""; dialog = null }) { Text(uiText("Clear filter")) } })
    }
    if (dialog == SheetDialog.DUPLICATES && document != null && table != null) {
        var compareColumn by remember(document.revision) { mutableStateOf(false) }
        val compare = activeColumn.takeIf { compareColumn }
        val removalCount = table.rows.size - table.removeDuplicates(compare, header).rows.size
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(uiText("Remove duplicates")) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText("The first occurrence is kept. %1\$d repeated rows would be removed.", removalCount))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText("Compare only column %1\$s", sheetColumnName(activeColumn)), Modifier.weight(1f))
                    Switch(compareColumn, { compareColumn = it })
                }
            }
        }, confirmButton = { TextButton(onClick = {
            model.editCsv(document.id, document.revision, WorkspaceCsvCommand.RemoveDuplicates(compare, header)) { dialog = null }
        }, enabled = !state.busy && removalCount > 0) { Text(uiText("Remove %1\$d rows", removalCount)) } },
            dismissButton = { TextButton(onClick = { dialog = null }, enabled = !state.busy) { Text(uiText("Cancel")) } })
    }
    if (dialog == SheetDialog.TABS) Zone6ButtonSheet(uiText("Sheets"), { dialog = null }) {
        TextButton(onClick = openCreate, enabled = !state.busy) { Text(stringResource(R.string.sheets_create)) }
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            sheets.forEach { item ->
                TextButton(onClick = { dialog = null; model.open(item.id) }, enabled = !state.busy && item.id != document?.id) {
                    Text(item.document.title.ifBlank { uiText("Untitled sheet") })
                }
            }
        }
    }
    if (dialog == SheetDialog.FILE) Zone6ButtonSheet(uiText("Import / export"), { dialog = null }) {
        TextButton(onClick = {
            dialog = null
            openCsv.launch(arrayOf("text/csv", "text/plain", "application/csv", "application/vnd.ms-excel"))
        }, enabled = !state.busy) { Text(uiText("Import CSV")) }
        val source = document?.let { doc -> state.selected?.takeIf { it.id == doc.id && it.version == doc.revision.toString() } }
        TextButton(onClick = { source?.let { model.exportText(it, WorkspaceTextFormat.CSV) }; dialog = null },
            enabled = !state.busy && source != null) { Text(uiText("Save CSV copy")) }
    }
    if (dialog == SheetDialog.NUMBER_FORMAT) HaloSheetNumberFormatSheet(numberFormat, formatDecimals, { (format, decimals) ->
        scope.launch {
            savedError = !numberSetting.apply(numberSetting.value, "${format.name}/$decimals/$activeColumn", "")
            if (savedError) model.reportError() else dialog = null
        }
    }, { dialog = null })
    if (dialog == SheetDialog.FREEZE) HaloSheetFreezeSheet(frozenRows, frozenColumns, { rows, columns ->
        scope.launch {
            savedError = !freezeSetting.apply(freezeSetting.value, "$rows/$columns", "0/0")
            if (savedError) model.reportError() else dialog = null
        }
    }, { dialog = null })
    if (dialog == SheetDialog.COLOR_RULE) HaloSheetColorRuleSheet(activeColumn, colorRule, onDismiss = { dialog = null }) { next ->
        scope.launch {
            savedError = !colorSetting.apply(colorSetting.value, next?.encode().orEmpty(), "")
            if (savedError) model.reportError() else dialog = null
        }
    }
    if (dialog == SheetDialog.ENTRY_RULE && document != null) HaloSheetEntryRuleSheet(activeColumn, header, entryRule,
        !state.busy, onApply = { rule -> model.setSheetEntryRule(document, rule) { entryRule = rule; dialog = null } },
        onDismiss = { if (!state.busy) dialog = null })
    if (dialog == SheetDialog.EXPLAIN && table != null) Zone6ButtonSheet(uiText("About this sheet"), { dialog = null }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText("%1\$d data rows and %2\$d columns.", table.rows.size - (if (header) 1 else 0), columnCount))
            if (header) Text(uiText("Columns: %1\$s", table.rows.first().filter(String::isNotBlank).joinToString().take(220)))
            val formulaCount = table.rows.sumOf { row -> row.count { it.startsWith('=') } }
            if (formulaCount > 0) Text(uiText("%1\$d cells contain calculations.", formulaCount))
            val count = table.rows.indices.count { sheetNumber(formulaGrid?.display(it, activeColumn)) != null }
            Text(uiText("Column %1\$s contains %2\$d numbers.", sheetColumnName(activeColumn), count))
        }
    }
    if (dialog == SheetDialog.CHART && table != null && formulaGrid != null) {
        val rows = if (selectedRows.isEmpty()) table.rows.indices else selectedRows
        val chartColumn = if (selectedColumns.isEmpty()) activeColumn else selectedColumns.last
        val values = remember(table, rows, chartColumn, header) {
            rows.asSequence().filterNot { header && it == 0 }.mapNotNull { r ->
                sheetNumber(formulaGrid.display(r, chartColumn))?.toDouble()?.let { (r + 1).toString() to it }
            }.take(40).toList()
        }
        HaloSheetChartSheet(table.rows.firstOrNull()?.getOrNull(chartColumn).orEmpty().takeIf { header }.orEmpty(), values) { dialog = null }
    }
    val selected = cell
    if (selected != null && document != null && table != null) {
        var value by remember(selected, dialog) { mutableStateOf(table.rows.getOrNull(selected.row)?.getOrNull(selected.column).orEmpty()) }
        val asking = dialog == SheetDialog.ASK_FORMULA
        val editingFormula = dialog == SheetDialog.FORMULA || asking
        var phrase by remember(selected, dialog) { mutableStateOf("") }
        var proposal by remember(selected, dialog) { mutableStateOf<String?>(null) }
        val location = HaloSheetCell(selected.row, selected.column)
        val finalValue = if (asking) proposal else value
        val formulaResult = remember(table, location, finalValue) {
            finalValue?.takeIf { it.startsWith('=') }?.let { next ->
                runCatching { HaloSheetFormulas(table.set(location.row, location.column, next)).display(location.row, location.column) }.getOrNull()
            }
        }
        val title = if (asking) uiText("Ask in words") else if (editingFormula) uiText("Formula for %1\$s", location.address) else stringResource(R.string.file52_edit_cell)
        val liveRule = entryRule?.takeIf { it.column == selected.column && (!it.header || selected.row > 0) }
        val invalid = finalValue != null && liveRule?.accepts(formulaResult ?: finalValue) == false
        val close = { cell = null; dialog = null }
        AlertDialog(onDismissRequest = { if (!state.busy) close() }, title = { Text(title) }, text = {
            Column {
                if (asking) {
                    OutlinedTextField(phrase, { phrase = it.take(120); proposal = null }, label = { Text(uiText("Example: add up B2 to B8")) })
                    TextButton(onClick = { proposal = sheetFormulaFromWords(phrase, location, activeColumn, header, table.rows.size) ?: "" },
                        enabled = phrase.isNotBlank()) { Text(uiText("Preview formula")) }
                    if (proposal == "") Text(uiText("Try adding up, averaging, counting, or finding the highest or lowest value."))
                    else proposal?.let { Text(it) }
                } else {
                    val choices = liveRule?.takeIf { it.kind == HaloSheetEntryKind.LIST }?.choices.orEmpty()
                    if (choices.isNotEmpty()) {
                        var choosing by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton({ choosing = true }) { Text(value.ifBlank { uiText("Choose an entry") }) }
                            DropdownMenu(choosing, { choosing = false }) {
                                DropdownMenuItem(text = { Text(uiText("Clear entry")) }, onClick = { value = ""; choosing = false })
                                choices.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { value = choice; choosing = false }) }
                            }
                        }
                    } else OutlinedTextField(value, { value = it.take(WorkspaceCsv.MAX_CELL_CHARS) }, maxLines = 6, isError = invalid)
                    if (editingFormula) Text(uiText("For example, =SUM(B2:B8) or =B2+B3."))
                }
                formulaResult?.let { Text(uiText("Preview: %1\$s", it), color = if (it == "#ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
                if (invalid) Text(uiText("This entry doesn't match the column rule."), color = MaterialTheme.colorScheme.error)
                if (state.error) Text(stringResource(R.string.file52_error), color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = {
            Button({ model.editCsv(document.id, selected.revision,
                WorkspaceCsvCommand.SetCell(selected.row, selected.column, requireNotNull(finalValue))) { close() } },
                enabled = !state.busy && !invalid && selected.revision == document.revision && finalValue != null && (!asking || finalValue.isNotBlank() && formulaResult != "#ERROR")) {
                Text(stringResource(android.R.string.ok))
            }
        }, dismissButton = { TextButton(close, enabled = !state.busy) { Text(stringResource(android.R.string.cancel)) } })
    }
}
