package com.halo.app.ui.workspace

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.*
import com.halo.app.ui.screens.notebook.*

private data class FileToolImageSelection(val minimum: Int, val sources: List<WorkspaceFile>)
private data class FileToolImageEdit(val sources: List<WorkspaceFile>, val batch: Boolean)
private data class FileToolInkEdit(val note: WorkspacePdfAnnotations, val file: WorkspaceFile, val recovery: CanvasDrawingDraft?)

/** Progressive manual controls onto the same use cases and expected revisions as the file action contract. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WorkspaceFileToolsScreen(model: WorkspaceFileToolsViewModel, onBack: () -> Unit,
    onOpenNote: (String) -> Unit, onOpenFile: (String) -> Unit, onOpenStudio: (String?) -> Unit,
    onStudy: (String) -> Unit, onAttach: (WorkspaceFileToolsObject) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val objects by model.objects.collectAsStateWithLifecycle()
    val files by model.files.collectAsStateWithLifecycle()
    val active = objects.mapTo(HashSet()) { it.id.value }
    val availableImages = files.filter { it.objectId.value in active && it.mimeType.startsWith("image/") && it.version != null &&
        it.availability in setOf(WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL) }.take(100)
    val selected = state.selected
    val busy = state.busy
    var creating by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf<FileToolImageSelection?>(null) }
    var imageEdit by remember { mutableStateOf<FileToolImageEdit?>(null) }
    var collage by remember { mutableStateOf<List<WorkspaceFile>?>(null) }
    var extracting by remember { mutableStateOf<WorkspaceFile?>(null) }
    var table by remember(selected?.id) { mutableStateOf(false) }
    var ink by remember { mutableStateOf<FileToolInkEdit?>(null) }
    var moreFormats by remember { mutableStateOf(false) }
    val batchTitle = stringResource(R.string.file52_batch_receipt)
    val annotationTitle = stringResource(R.string.file52_annotation_name)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::importFile) }
    BackHandler(enabled = busy) { }
    // A source change dismisses its old preview; it never grants a fresh version to an in-progress edit.
    LaunchedEffect(state.file?.objectId, state.file?.version, state.annotations?.source, state.error) {
        val current = ink
        if (current != null && (state.file?.objectId != current.file.objectId || state.file?.version != current.file.version)) ink = null
        val edit = imageEdit
        if (edit != null && !edit.batch && (edit.sources.single().objectId != state.file?.objectId ||
            edit.sources.single().version != state.file?.version)) imageEdit = null
    }
    LaunchedEffect(files, objects) {
        fun stale(sources: List<WorkspaceFile>) = sources.any { source ->
            source.objectId.value !in active || files.none { it.objectId == source.objectId && it.version == source.version &&
                it.availability in setOf(WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL) }
        }
        if (imageEdit?.let { it.batch && stale(it.sources) } == true) imageEdit = null
        if (collage?.let(::stale) == true) collage = null
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.file52_title)) }, navigationIcon = {
            IconButton(onClick = { if (selected != null) model.closeSelection() else onBack() }, enabled = !busy) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.file52_back))
            }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("file-tools-content"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.file52_local_scope), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { creating = true }, enabled = !busy) { Text(stringResource(R.string.file52_new_document)) }
                    OutlinedButton(onClick = { picker.launch(arrayOf("text/*", "image/*", "application/pdf", "application/octet-stream")) }, enabled = !busy) { Text(stringResource(R.string.file52_open_file)) }
                    TextButton(onClick = { onOpenStudio(null) }, enabled = !busy) { Text(stringResource(R.string.file52_create_image)) }
                }
            }
            if (busy) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.file52_busy))
                TextButton(onClick = model::cancelOperation) { Text(stringResource(R.string.file52_cancel_operation)) }
            }
            if (state.loading) item { CircularProgressIndicator() }
            if (state.error || state.cancelled) item {
                Text(stringResource(if (state.cancelled) R.string.file52_cancelled else R.string.file52_error), color = MaterialTheme.colorScheme.error)
                model.selectedObjectId?.takeIf { id -> objects.any { it.id.value == id && it.type == WorkspaceObjectType.NOTE } }?.let { id ->
                    TextButton(onClick = { onOpenNote(id) }, enabled = !busy) { Text(stringResource(R.string.file52_open_editor)) }
                }
                model.selectedObjectId?.takeIf { id -> objects.any { it.id.value == id && it.type == WorkspaceObjectType.FILE } }?.let { id ->
                    TextButton(onClick = { onOpenFile(id) }, enabled = !busy) { Text(stringResource(R.string.file52_open_original)) }
                }
                TextButton(onClick = model::dismissError, enabled = !busy) { Text(stringResource(R.string.file52_close)) }
            }
            selected?.let { source ->
                item {
                    Text(source.title, style = MaterialTheme.typography.headlineSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onAttach(source) }, enabled = !busy) { Text(stringResource(R.string.file52_attach_canvas)) }
                        if (source.kind != WorkspaceFileToolsKind.BATCH_RECEIPT) TextButton(onClick = {
                            onStudy(if (source.kind == WorkspaceFileToolsKind.PDF_ANNOTATIONS) requireNotNull(state.annotations).source.objectId else source.id)
                        }, enabled = !busy) { Text(stringResource(R.string.file52_attach_study)) }
                    }
                }
                when (source.kind) {
                    WorkspaceFileToolsKind.TEXT_DOCUMENT -> state.text?.let { document -> item {
                        Text(fileToolFormatName(document.format), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (document.format == WorkspaceTextFormat.CSV) R.string.file52_csv_help else R.string.file52_text_help), style = MaterialTheme.typography.bodySmall)
                        Text(document.text.take(1400), maxLines = 12, overflow = TextOverflow.Ellipsis)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onOpenNote(document.id) }, enabled = !busy) { Text(stringResource(R.string.file52_edit_text)) }
                            if (document.format == WorkspaceTextFormat.CSV) OutlinedButton(onClick = { table = true }, enabled = !busy) { Text(stringResource(R.string.file52_edit_table)) }
                            TextButton(onClick = { model.exportText(source, document.format) }, enabled = !busy) { Text(stringResource(when (document.format) {
                                WorkspaceTextFormat.TXT -> R.string.file52_export_txt; WorkspaceTextFormat.MARKDOWN -> R.string.file52_export_md; WorkspaceTextFormat.CSV -> R.string.file52_export_csv
                            })) }
                            TextButton(onClick = { model.undoText(document) }, enabled = !busy && state.textHistory.canUndo) { Text(stringResource(R.string.file52_undo)) }
                            TextButton(onClick = { model.redoText(document) }, enabled = !busy && state.textHistory.canRedo) { Text(stringResource(R.string.file52_redo)) }
                        }
                    } }
                    WorkspaceFileToolsKind.NOTE -> item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onOpenNote(source.id) }, enabled = !busy) { Text(stringResource(R.string.file52_edit_text)) }
                            TextButton(onClick = { model.exportText(source, WorkspaceTextFormat.TXT) }, enabled = !busy) { Text(stringResource(R.string.file52_export_txt)) }
                            TextButton(onClick = { model.exportText(source, WorkspaceTextFormat.MARKDOWN) }, enabled = !busy) { Text(stringResource(R.string.file52_export_md)) }
                        }
                    }
                    WorkspaceFileToolsKind.FILE -> state.file?.let { file -> item {
                        val available = file.availability in setOf(WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL)
                        if (!available) Text(stringResource(R.string.file52_unavailable), color = MaterialTheme.colorScheme.error)
                        if (available && (file.mimeType.startsWith("image/") || file.mimeType == "application/pdf"))
                            CanvasAnnotatedFilePreview(file, file.displayName, Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp), maxDimension = 640)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onOpenFile(file.objectId.value) }, enabled = !busy) { Text(stringResource(R.string.file52_open_original)) }
                            if (WorkspaceTextFormat.fromFile(file.displayName, file.mimeType) != null) Button(onClick = { model.importText(file) }, enabled = !busy && available) { Text(stringResource(R.string.file52_edit_text)) }
                            else if (file.mimeType.startsWith("image/")) {
                                Button(onClick = { imageEdit = FileToolImageEdit(listOf(file), false) }, enabled = !busy && available) { Text(stringResource(R.string.file52_convert_image)) }
                                TextButton(onClick = { onOpenStudio(file.objectId.value) }, enabled = !busy && available) { Text(stringResource(R.string.file52_create_image)) }
                            } else if (file.mimeType == "application/pdf") {
                                Button(onClick = { extracting = file }, enabled = !busy && available) { Text(stringResource(R.string.file52_pdf_extract)) }
                                TextButton(onClick = { model.createAnnotations(file, (file.displayName.take(80) + " · " + annotationTitle).take(120)) }, enabled = !busy && available) { Text(stringResource(R.string.file52_annotate_pdf)) }
                            } else Text(stringResource(R.string.file52_unsupported), style = MaterialTheme.typography.bodySmall)
                        }
                    } }
                    WorkspaceFileToolsKind.PDF_ANNOTATIONS -> state.annotations?.let { note -> item {
                        Text(stringResource(R.string.file52_annotation_scope), style = MaterialTheme.typography.bodyMedium)
                        val file = state.file
                        if (file != null) CanvasAnnotatedFilePreview(file, note.block.text, Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp),
                            page = note.markup.pages.keys.minOrNull() ?: 1, maxDimension = 640)
                        else Text(stringResource(R.string.file52_unavailable), color = MaterialTheme.colorScheme.error)
                        val recovered = state.recovery
                        val stale = recovered != null && recovered.baseRevision != note.revision
                        if (recovered != null) Text(stringResource(if (stale) R.string.file52_stale_drawing else R.string.file52_recovery_kept), style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                runCatching {
                                    val draft = recovered?.takeIf { it.baseRevision == note.revision }?.blocks?.singleOrNull()?.let { block ->
                                    require(block.id == note.block.id && block.language == WorkspaceFileAnnotations.LANGUAGE)
                                    val marks = CanvasFileMarkupCodec.decode(block.text)
                                    require(CanvasFileMarkupCodec.merge(marks.title, marks.pages) == block.text)
                                    CanvasDrawingDraft(null, "annotation", note.block.text,
                                        pages = (note.markup.pages.keys + marks.pages.keys).associateWith { marks.pages[it].orEmpty() }, fileVersion = note.source.version)
                                }
                                    FileToolInkEdit(note, requireNotNull(file), draft)
                                }.onSuccess { ink = it }.onFailure { model.reportError() }
                            }, enabled = !busy && file != null && !stale) { Text(stringResource(R.string.file52_annotate)) }
                            TextButton(onClick = { onOpenFile(note.source.objectId) }, enabled = !busy) { Text(stringResource(R.string.file52_open_original)) }
                            if (stale) TextButton(onClick = { onOpenNote(note.id) }, enabled = !busy) { Text(stringResource(R.string.file52_open_editor)) }
                            for (page in note.markup.pages.keys.sorted().ifEmpty { listOf(1) }) TextButton(onClick = { model.exportAnnotation(note, page) }, enabled = !busy && file != null) {
                                Text(stringResource(R.string.file52_export_page, page))
                            }
                        }
                    } }
                    WorkspaceFileToolsKind.BATCH_RECEIPT -> state.receipt?.let { receipt -> item {
                        Text(stringResource(when (receipt.phase) {
                            WorkspaceBatchPhase.RUNNING -> R.string.file52_batch_running
                            WorkspaceBatchPhase.COMPLETED -> R.string.file52_batch_completed
                            WorkspaceBatchPhase.PARTIAL -> R.string.file52_batch_partial
                            WorkspaceBatchPhase.CANCELLED -> R.string.file52_cancelled
                            WorkspaceBatchPhase.INTERRUPTED -> R.string.file52_batch_interrupted
                        }), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.file52_receipt_help), style = MaterialTheme.typography.bodySmall)
                        receipt.items.forEachIndexed { index, entry ->
                            Text("${index + 1}. " + stringResource(when (entry.phase) {
                                WorkspaceBatchItemPhase.PENDING -> R.string.file52_item_pending
                                WorkspaceBatchItemPhase.SAVED -> R.string.file52_item_saved
                                WorkspaceBatchItemPhase.FAILED -> R.string.file52_item_failed
                            }))
                            entry.output?.let { output -> TextButton(onClick = { model.openOutput(output) }, enabled = !busy) { Text(stringResource(R.string.file52_open_saved)) } }
                        }
                    } }
                }
            }
            item {
                Text(stringResource(R.string.file52_choose_images), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { choosing = FileToolImageSelection(1, availableImages.toList()) }, enabled = !busy && availableImages.isNotEmpty()) { Text(stringResource(R.string.file52_image_batch)) }
                    OutlinedButton(onClick = { choosing = FileToolImageSelection(2, availableImages.toList()) }, enabled = !busy && availableImages.size >= 2) { Text(stringResource(R.string.file52_collage)) }
                }
            }
            item { Text(stringResource(R.string.file52_recent), style = MaterialTheme.typography.titleMedium) }
            if (objects.isEmpty()) item { Text(stringResource(R.string.file52_empty)) }
            items(objects.take(100), key = { it.id.value }) { item ->
                TextButton(onClick = { model.open(item.id.value) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(item.title, Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                TextButton(onClick = { moreFormats = !moreFormats }) { Text(stringResource(R.string.file52_supported_tools)) }
                if (moreFormats) {
                    WorkspaceFileToolCatalog.capabilities.forEach { capability -> WorkspaceFileToolCapabilityRow(capability) }
                }
            }
        }
    }
    if (creating) WorkspaceNewFileDialog({ title, format -> creating = false; model.create(title, format) }, { creating = false })
    choosing?.let { selection -> WorkspaceChooseImagesDialog(selection.sources, selection.minimum, { picked ->
        choosing = null
        model.prepareImages(picked) { pinned ->
            if (selection.minimum == 1) imageEdit = FileToolImageEdit(pinned, true) else collage = pinned
        }
    }, { choosing = null }) }
    imageEdit?.let { edit -> CanvasLocalImageToolsDialog(edit.sources.first(), busy, { recipe ->
        imageEdit = null
        if (edit.batch) model.batch(batchTitle, edit.sources, recipe) else model.transform(edit.sources.single(), recipe)
    }, { imageEdit = null }, scopeLabel = if (edit.batch) stringResource(R.string.file52_batch_help) else null,
        saveLabel = if (edit.batch) stringResource(R.string.file52_image_batch) else null) }
    collage?.let { images -> CanvasImageStitchDialog(images.map { it.objectId.value to CanvasStitchSource.WorkspaceImage(it) }, busy, { recipe, ordered ->
        collage = null; model.collage(ordered.map { (it.second as CanvasStitchSource.WorkspaceImage).file }, recipe)
    }, { collage = null }) }
    extracting?.let { file -> CanvasPdfPageDialog(file, busy, { pages -> extracting = null; model.extractPdfPage(file, pages.single()) }, { extracting = null }, singlePage = true) }
    if (table) state.text?.let { document -> WorkspaceCsvDialog(document, busy, state.error, state.textHistory,
        { revision, command, saved -> model.editCsv(document.id, revision, command, saved) }, { model.undoText(document) }, { model.redoText(document) }, { table = false }) }
    ink?.let { edit -> CanvasSourceInkDialog(edit.file, edit.note.block.text, recovery = edit.recovery,
        onDraft = { model.keepDrawingRecovery(edit.note, it.copy(fileVersion = edit.note.source.version)) },
        onSave = { pages -> ink = null; model.saveAnnotations(edit.note, pages) },
        onDismiss = { ink = null }, scopeLabel = stringResource(R.string.file52_annotation_scope)) }
}
