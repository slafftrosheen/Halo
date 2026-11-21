package com.halo.app.ui.toolkit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.halo.app.ui.icons.HaloGlyph
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

private enum class SheetDialog { CREATE, SORT, ASK, APPEARANCE }
private data class SelectedCell(val revision: Long, val row: Int, val column: Int)

@OptIn(ExperimentalLayoutApi::class)
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
    val scope = rememberCoroutineScope()
    val setting = rememberZone6PageSetting("sheets.headers.v1")
    val header = setting.value != "false"
    val shell = LocalShellHaloOrb.current
    val easy = rememberVisualPreferences().viewMode == HaloViewMode.EASY
    var dialog by remember { mutableStateOf<SheetDialog?>(null) }
    var cell by remember { mutableStateOf<SelectedCell?>(null) }
    var column by rememberSaveable(document?.id) { mutableIntStateOf(0) }
    var rowFilter by rememberSaveable(document?.id) { mutableStateOf("") }
    var operation by rememberSaveable(document?.id) { mutableStateOf<SheetCalculation?>(null) }
    var savedError by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val columnCount = table?.rows?.maxOf { it.size } ?: 0
    val activeColumn = column.coerceIn(0, (columnCount - 1).coerceAtLeast(0))
    val answer = remember(table, activeColumn, header, operation, rowFilter) {
        if (table != null && operation != null) table.calculate(activeColumn, header, requireNotNull(operation), rowFilter)
        else null
    }

    val openCreate = { dialog = SheetDialog.CREATE }
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
            }
            itemsIndexed(table.rows, key = { index, _ -> index }) { index, row ->
                Row(Modifier.fillMaxWidth().horizontalScroll(scroll)
                    .background(if (index in answer?.matchingRows.orEmpty()) MaterialTheme.colorScheme.secondaryContainer
                        else if (index == 0 && header) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.surface),
                    verticalAlignment = Alignment.CenterVertically) {
                    repeat(columnCount) { col ->
                        val value = row.getOrNull(col).orEmpty()
                        Text(value, modifier = Modifier.width(if (easy) 142.dp else 110.dp).heightIn(min = 48.dp)
                            .clickable(enabled = !state.busy) { cell = SelectedCell(document.revision, index, col) }.padding(8.dp),
                            textAlign = if (sheetNumber(value) != null) TextAlign.End else TextAlign.Start,
                            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
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
    val selected = cell
    if (selected != null && document != null && table != null) {
        var value by remember(selected) { mutableStateOf(table.rows.getOrNull(selected.row)?.getOrNull(selected.column).orEmpty()) }
        AlertDialog(onDismissRequest = { if (!state.busy) cell = null }, title = { Text(stringResource(R.string.file52_edit_cell)) }, text = {
            Column {
                OutlinedTextField(value, { value = it.take(WorkspaceCsv.MAX_CELL_CHARS) }, maxLines = 6)
                if (state.error) Text(stringResource(R.string.file52_error), color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = {
            Button({ model.editCsv(document.id, selected.revision,
                WorkspaceCsvCommand.SetCell(selected.row, selected.column, value)) { cell = null } },
                enabled = !state.busy && selected.revision == document.revision) {
                Text(stringResource(android.R.string.ok))
            }
        }, dismissButton = { TextButton({ cell = null }, enabled = !state.busy) { Text(stringResource(android.R.string.cancel)) } })
    }
}
