package com.halo.app.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.workspace.*
import com.halo.app.ui.screens.notebook.CanvasAnnotatedFilePreview

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkspaceNewFileDialog(onCreate: (String, WorkspaceTextFormat) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(WorkspaceTextFormat.TXT) }
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.file52_new_document)) }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it.take(120) }, label = { Text(stringResource(R.string.file52_document_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkspaceTextFormat.entries.forEach { candidate -> FilterChip(candidate == format, { format = candidate }, label = { Text(fileToolFormatName(candidate)) }) }
            }
            Text(stringResource(if (format == WorkspaceTextFormat.CSV) R.string.file52_csv_help else R.string.file52_text_help), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { onCreate(title, format) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.file52_new_document)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.file52_cancel)) } })
}

@Composable
internal fun fileToolFormatName(format: WorkspaceTextFormat): String = stringResource(when (format) {
    WorkspaceTextFormat.TXT -> R.string.file52_text
    WorkspaceTextFormat.MARKDOWN -> R.string.file52_markdown
    WorkspaceTextFormat.CSV -> R.string.file52_csv
})

@Composable
internal fun WorkspaceChooseImagesDialog(files: List<WorkspaceFile>, minimum: Int, onConfirm: (List<WorkspaceFile>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.file52_choose_images)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.file52_select_limit), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.file52_selected_count, selected.size))
            LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(files, key = { it.objectId.value }) { file ->
                    val active = file.objectId.value in selected
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(enabled = active || selected.size < 6) {
                        selected = if (active) selected - file.objectId.value else selected + file.objectId.value
                    }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(active, onCheckedChange = null, enabled = active || selected.size < 6)
                        CanvasAnnotatedFilePreview(file, file.displayName, Modifier.size(56.dp), maxDimension = 128)
                        Text(file.displayName, Modifier.weight(1f).padding(8.dp), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(files.filter { it.objectId.value in selected }) }, enabled = selected.size in minimum..6) {
        Text(stringResource(R.string.file52_apply))
    } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.file52_cancel)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkspaceFileToolCapabilityRow(capability: WorkspaceFileToolCapability) {
    val title = when (capability.id) {
        "text" -> R.string.file52_text
        "csv" -> R.string.file52_csv
        "image" -> R.string.file52_convert_image
        "image_batch" -> R.string.file52_image_batch
        "collage" -> R.string.file52_collage
        "pdf_page_image" -> R.string.file52_pdf_extract
        "pdf_annotations" -> R.string.file52_pdf_annotations
        "native_pdf_edit" -> R.string.file52_native_pdf
        "office_roundtrip" -> R.string.file52_office
        else -> error("Unmapped file capability")
    }
    val status = when (capability.state) {
        WorkspaceFileToolState.READY -> R.string.file52_ready
        WorkspaceFileToolState.LIMITED -> R.string.file52_limited
        WorkspaceFileToolState.IN_DEVELOPMENT -> R.string.file52_development
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                Text(stringResource(status), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        val help = when (capability.id) {
            "text" -> R.string.file52_text_help
            "csv" -> R.string.file52_csv_help
            "image" -> R.string.file52_image_help
            "image_batch" -> R.string.file52_batch_help
            "collage" -> R.string.file52_collage_help
            "pdf_page_image" -> R.string.file52_pdf_extract_help
            "pdf_annotations" -> R.string.file52_annotation_scope
            "native_pdf_edit" -> R.string.file52_native_pdf_future
            else -> R.string.file52_office_future
        }
        Text(stringResource(help), style = MaterialTheme.typography.bodySmall)
    }
}
