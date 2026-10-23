package com.halo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.workspace.*

private data class FileToolCell(val revision: Long, val row: Int, val column: Int, val value: String)

/** Windowed table view over one canonical CSV block; edits retain the exact visible revision. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkspaceCsvDialog(document: WorkspaceTextDocument, busy: Boolean, error: Boolean,
    history: WorkspaceTextDocuments.HistoryStatus,
    onEdit: (Long, WorkspaceCsvCommand, () -> Unit) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onDismiss: () -> Unit) {
    var cell by remember(document.id) { mutableStateOf<FileToolCell?>(null) }
    var rowStart by remember(document.id) { mutableIntStateOf(0) }
    var columnStart by remember(document.id) { mutableIntStateOf(0) }
    val table = remember(document.text) { runCatching { WorkspaceCsv.parse(document.text) }.getOrNull() }
    val selected = cell
    if (selected != null) {
        var value by remember(selected) { mutableStateOf(selected.value) }
        var limit by remember(selected) { mutableStateOf(false) }
        StudioAlertDialog(onDismissRequest = { if (!busy) cell = null }, title = { Text(stringResource(R.string.file52_edit_cell)) }, text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.file52_cell_position, selected.row + 1, selected.column + 1))
                OutlinedTextField(value, { next ->
                    if (next.length <= WorkspaceCsv.MAX_CELL_CHARS) { value = next; limit = false } else limit = true
                }, modifier = Modifier.fillMaxWidth(), enabled = !busy, label = { Text(stringResource(R.string.file52_value)) }, minLines = 2, maxLines = 8)
                if (limit) Text(stringResource(R.string.file52_cell_limit), color = MaterialTheme.colorScheme.error)
                if (error) Text(stringResource(R.string.file52_error), color = MaterialTheme.colorScheme.error)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onEdit(selected.revision, WorkspaceCsvCommand.InsertRow(selected.row), { cell = null }) }, enabled = !busy) { Text(stringResource(R.string.file52_insert_row)) }
                    TextButton(onClick = { onEdit(selected.revision, WorkspaceCsvCommand.DeleteRow(selected.row), { cell = null }) }, enabled = !busy && (table?.rows?.size ?: 0) > 1) { Text(stringResource(R.string.file52_delete_row)) }
                    TextButton(onClick = { onEdit(selected.revision, WorkspaceCsvCommand.InsertColumn(selected.column), { cell = null }) }, enabled = !busy) { Text(stringResource(R.string.file52_insert_column)) }
                    TextButton(onClick = { onEdit(selected.revision, WorkspaceCsvCommand.DeleteColumn(selected.column), { cell = null }) }, enabled = !busy && (table?.rows?.maxOfOrNull { it.size } ?: 0) > 1) { Text(stringResource(R.string.file52_delete_column)) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }, confirmButton = { TextButton(onClick = { onEdit(selected.revision, WorkspaceCsvCommand.SetCell(selected.row, selected.column, value), { cell = null }) }, enabled = !busy && !limit) {
            Text(stringResource(R.string.file52_apply))
        } }, dismissButton = { TextButton(onClick = { cell = null }, enabled = !busy) { Text(stringResource(R.string.file52_cancel)) } })
        return
    }
    StudioAlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.file52_edit_table)) }, text = {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.file52_csv_help), style = MaterialTheme.typography.bodySmall)
            if (error) Text(stringResource(R.string.file52_error), color = MaterialTheme.colorScheme.error)
            if (table == null) Text(stringResource(R.string.file52_csv_invalid), color = MaterialTheme.colorScheme.error)
            else BoxWithConstraints {
                val columnsPerPage = if (LocalDensity.current.fontScale >= 1.4f) 1 else if (maxWidth >= 500.dp) 3 else 2
                val rowsPerPage = 5
                val columns = table.rows.maxOf { it.size }
                val firstRow = rowStart.coerceIn(0, table.rows.lastIndex)
                val firstColumn = columnStart.coerceIn(0, columns - 1)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { rowStart = (firstRow - rowsPerPage).coerceAtLeast(0) }, enabled = !busy && firstRow > 0) { Text(stringResource(R.string.file52_previous)) }
                        Text(stringResource(R.string.file52_row, firstRow + 1), Modifier.padding(8.dp))
                        TextButton(onClick = { rowStart = firstRow + rowsPerPage }, enabled = !busy && firstRow + rowsPerPage < table.rows.size) { Text(stringResource(R.string.file52_next)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { columnStart = (firstColumn - columnsPerPage).coerceAtLeast(0) }, enabled = !busy && firstColumn > 0) { Text(stringResource(R.string.file52_previous)) }
                        Text(stringResource(R.string.file52_column, firstColumn + 1), Modifier.padding(8.dp))
                        TextButton(onClick = { columnStart = firstColumn + columnsPerPage }, enabled = !busy && firstColumn + columnsPerPage < columns) { Text(stringResource(R.string.file52_next)) }
                    }
                    for (row in firstRow until minOf(firstRow + rowsPerPage, table.rows.size)) {
                        Text(stringResource(R.string.file52_row, row + 1), style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (column in firstColumn until minOf(firstColumn + columnsPerPage, columns)) {
                                val value = table.rows[row].getOrNull(column).orEmpty()
                                val description = stringResource(R.string.file52_cell_position, row + 1, column + 1)
                                OutlinedButton(onClick = { cell = FileToolCell(document.revision, row, column, value) }, enabled = !busy,
                                    modifier = Modifier.weight(1f).heightIn(min = 64.dp).semantics { contentDescription = description }) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.file52_column, column + 1), style = MaterialTheme.typography.labelSmall)
                                        Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onEdit(document.revision, WorkspaceCsvCommand.InsertRow(table.rows.size), {}) }, enabled = !busy) { Text(stringResource(R.string.file52_insert_row)) }
                        TextButton(onClick = { onEdit(document.revision, WorkspaceCsvCommand.InsertColumn(columns), {}) }, enabled = !busy && columns < WorkspaceCsv.MAX_COLUMNS) { Text(stringResource(R.string.file52_insert_column)) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.file52_done)) } },
        dismissButton = { FlowRow {
            TextButton(onClick = onUndo, enabled = !busy && history.canUndo) { Text(stringResource(R.string.file52_undo)) }
            TextButton(onClick = onRedo, enabled = !busy && history.canRedo) { Text(stringResource(R.string.file52_redo)) }
        } })
}
