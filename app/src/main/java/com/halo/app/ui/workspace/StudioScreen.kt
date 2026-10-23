package com.halo.app.ui.workspace

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.data.studio.*
import com.halo.app.data.workspace.CanvasImageFormat
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.ui.screens.notebook.CanvasAnnotatedFilePreview
import com.halo.sdk.runtime.ink.InkCodec
import com.halo.sdk.runtime.ink.InkDocument

private data class StudioLayerEdit(val revision: Long, val layer: StudioLayer, val adding: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(viewModel: StudioViewModel, onBack: () -> Unit, onOpenOutput: (WorkspaceFile) -> Unit,
    onInsertOutput: (WorkspaceFile) -> Unit, onCamera: (StudioProject) -> Unit, onAiDraft: (StudioProject) -> Unit,
    onRecoverCamera: ((StudioProject) -> Unit)? = null, externalBusy: Boolean = false) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val images by viewModel.images.collectAsStateWithLifecycle()
    val busy = state.busy || externalBusy
    val project = state.project
    DisposableEffect(viewModel, project?.id) {
        val token = project?.id?.let(viewModel.service::attachProject)
        onDispose { token?.let(viewModel.service::detachProject) }
    }
    val defaultName = stringResource(R.string.studio_new_project)
    var naming by remember(project?.id) { mutableStateOf<String?>(null) }
    var editing by remember(project?.id) { mutableStateOf<StudioLayerEdit?>(null) }
    var background by remember(project?.id) { mutableStateOf<StudioProject?>(null) }
    var transforms by remember(project?.id) { mutableStateOf<StudioProject?>(null) }
    var layers by remember(project?.id) { mutableStateOf(false) }
    var importing by remember(project?.id) { mutableStateOf(false) }
    var pendingInk by remember(project?.id) { mutableStateOf<String?>(null) }
    var activeInk by remember(project?.id) { mutableStateOf<StudioInkSession?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importUri) }
    BackHandler(project != null) { if (!busy) viewModel.closeProject() }
    LaunchedEffect(project?.revision, pendingInk) {
        val id = pendingInk
        if (project != null && id != null && project.session.current.layers.any { it.id == id }) {
            activeInk = viewModel.startInk(project.revision, id); pendingInk = null
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(project?.title ?: stringResource(R.string.studio_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = { if (project != null) viewModel.closeProject() else onBack() }, enabled = !busy) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.studio_close))
            } }, actions = {
                if (project != null) IconButton(onClick = viewModel::save, enabled = !busy && project.hasUnsavedChanges) {
                    Icon(Icons.Default.Save, stringResource(R.string.studio_save), tint = MaterialTheme.colorScheme.primary)
                }
            })
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val previewHeight = (maxWidth * .9f).coerceIn(200.dp, 480.dp)
            LazyColumn(Modifier.fillMaxSize().testTag("studio-content"), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.loading) item { CircularProgressIndicator(); Text(stringResource(R.string.studio_loading)) }
                if (state.error) item {
                    Text(stringResource(R.string.studio_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::retry, enabled = !busy) { Text(stringResource(R.string.studio_retry)) }
                }
                if (project == null && !state.loading) {
                    item {
                        Text(stringResource(R.string.studio_local), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { naming = "new" }, enabled = !busy) { Text(stringResource(R.string.studio_new_project)) }
                    }
                    items(projects, key = { it.id }) { item ->
                        OutlinedCard(onClick = { viewModel.open(item.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(item.title, Modifier.padding(16.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else if (project != null) {
                    item { StudioDocumentPreview(viewModel.service, project.session.current, Modifier.fillMaxWidth().height(previewHeight)) }
                    item {
                        Text(stringResource(if (project.hasUnsavedChanges) R.string.studio_local_draft else R.string.studio_saved),
                            style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.edit(project.revision, "undo") }, enabled = !busy && project.session.undo.isNotEmpty()) {
                                Text(stringResource(R.string.studio_undo))
                            }
                            TextButton(onClick = { viewModel.edit(project.revision, "redo") }, enabled = !busy && project.session.redo.isNotEmpty()) {
                                Text(stringResource(R.string.studio_redo))
                            }
                            TextButton(onClick = { layers = true }, enabled = !busy) { Text(stringResource(R.string.studio_layers)) }
                            TextButton(onClick = { transforms = project }, enabled = !busy) { Text(stringResource(R.string.studio_transforms)) }
                        }
                    }
                    item {
                        val textName = stringResource(R.string.studio_text)
                        val rectangleName = stringResource(R.string.studio_rectangle)
                        val ellipseName = stringResource(R.string.studio_ellipse)
                        val inkName = stringResource(R.string.studio_draw)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { importing = true }, enabled = !busy) { Text(stringResource(R.string.studio_import_image)) }
                            OutlinedButton(onClick = { onCamera(project) }, enabled = !busy && onRecoverCamera == null) { Text(stringResource(R.string.studio_camera)) }
                            onRecoverCamera?.let { recover -> OutlinedButton(onClick = { recover(project) }, enabled = !busy) {
                                Text(stringResource(R.string.studio_recover_photo))
                            } }
                            OutlinedButton(onClick = {
                                val selected = project.session.current.layers.find { it.id == project.session.current.selectedLayerId && it.kind == StudioLayerKind.INK && !it.locked }
                                if (selected != null) activeInk = viewModel.startInk(project.revision, selected.id)
                                else {
                                    val ratio = project.session.current.width * 1000 / project.session.current.height
                                    val layer = StudioLayer(name = inkName, kind = StudioLayerKind.INK,
                                        ink = InkCodec.encode(InkDocument(aspectMilli = ratio.takeIf { it in 125..8000 } ?: 0)))
                                    viewModel.command(project.revision, StudioCommand.Add(layer)); pendingInk = layer.id
                                }
                            }, enabled = !busy) { Text(inkName) }
                            listOf(StudioLayerKind.TEXT to textName, StudioLayerKind.RECTANGLE to rectangleName, StudioLayerKind.ELLIPSE to ellipseName).forEach { (kind, name) ->
                                OutlinedButton(onClick = { editing = StudioLayerEdit(project.revision,
                                    StudioLayer(name = name, kind = kind, bounds = StudioRect(100, 100, 800, 300)), true) }, enabled = !busy) { Text(name) }
                            }
                        }
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { background = project }, enabled = !busy) { Text(stringResource(R.string.studio_background)) }
                            TextButton(onClick = { naming = "new" }, enabled = !busy) { Text(stringResource(R.string.studio_presets)) }
                            TextButton(onClick = { naming = "copy" }, enabled = !busy) { Text(stringResource(R.string.studio_save_as)) }
                            TextButton(onClick = viewModel::versions, enabled = !busy) { Text(stringResource(R.string.studio_versions)) }
                            TextButton(onClick = viewModel::discardDraft, enabled = !busy && project.hasUnsavedChanges) { Text(stringResource(R.string.studio_discard)) }
                            TextButton(onClick = { viewModel.export(CanvasImageFormat.PNG) }, enabled = !busy) { Text(stringResource(R.string.studio_export_png)) }
                            TextButton(onClick = { viewModel.export(CanvasImageFormat.JPEG) }, enabled = !busy) { Text(stringResource(R.string.studio_export_jpeg)) }
                            TextButton(onClick = { onAiDraft(project) }, enabled = !busy) { Text(stringResource(R.string.studio_ai)) }
                        }
                    }
                }
                if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item { Text(stringResource(R.string.studio_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    naming?.let { mode ->
        if (mode == "new") StudioCreateDialog(viewModel.service, defaultName, { title, preset ->
            viewModel.create(title, preset); naming = null
        }, { naming = null })
        else StudioNameDialog(project?.title ?: defaultName, { viewModel.saveAs(it); naming = null }, { naming = null })
    }
    background?.let { snapshot -> StudioBackgroundDialog(snapshot.session.current.background,
        { viewModel.command(snapshot.revision, StudioCommand.Background(it)) }, { background = null }) }
    editing?.let { draft -> StudioLayerDialog(draft.layer, { updated ->
        val command = if (draft.adding) StudioCommand.Add(updated) else if (draft.layer.locked && !updated.locked)
            StudioCommand.Batch(listOf(StudioCommand.Replace(draft.layer.copy(locked = false)), StudioCommand.Replace(updated)))
            else StudioCommand.Replace(updated)
        viewModel.command(draft.revision, command)
    }, { editing = null }) }
    transforms?.let { draft -> StudioTransformDialog(viewModel.service, draft.session.current,
        { viewModel.command(draft.revision, it) }, { transforms = null }) }
    if (layers && project != null) ModalBottomSheet(onDismissRequest = { layers = false }) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(bottom = 24.dp)) {
            items(project.session.current.layers.asReversed(), key = { it.id }) { layer ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    TextButton(onClick = { viewModel.command(project.revision, StudioCommand.Select(layer.id)) }, enabled = !busy) {
                        Text(layer.name + if (project.session.current.selectedLayerId == layer.id) " · " + stringResource(R.string.studio_selected) else "", maxLines = 2)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editing = StudioLayerEdit(project.revision, layer); layers = false }, enabled = !busy) { Text(stringResource(R.string.studio_edit_layer)) }
                        val index = project.session.current.layers.indexOf(layer)
                        IconButton(onClick = { viewModel.command(project.revision, StudioCommand.Move(layer.id, index + 1)) }, enabled = !busy && !layer.locked && index < project.session.current.layers.lastIndex) {
                            Icon(Icons.Default.ArrowUpward, stringResource(R.string.studio_move_up))
                        }
                        IconButton(onClick = { viewModel.command(project.revision, StudioCommand.Move(layer.id, index - 1)) }, enabled = !busy && !layer.locked && index > 0) {
                            Icon(Icons.Default.ArrowDownward, stringResource(R.string.studio_move_down))
                        }
                        IconButton(onClick = { viewModel.command(project.revision, StudioCommand.Remove(layer.id)) }, enabled = !busy && !layer.locked) {
                            Icon(Icons.Default.DeleteOutline, stringResource(R.string.studio_remove))
                        }
                    }
                }
            }
        }
    }
    if (importing) ModalBottomSheet(onDismissRequest = { importing = false }) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Button(onClick = { importing = false; picker.launch(arrayOf("image/*")) }, enabled = !busy) { Text(stringResource(R.string.studio_import_image)) }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(images, key = { it.objectId.value }) { image ->
                    ListItem(headlineContent = { Text(image.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { CanvasAnnotatedFilePreview(image, "", Modifier.size(48.dp), maxDimension = 128) },
                        modifier = Modifier.clickable(enabled = !busy) { viewModel.importExisting(image.objectId.value); importing = false })
                }
            }
        }
    }
    if (state.versions.isNotEmpty()) StudioAlertDialog(onDismissRequest = viewModel::dismissVersions,
        title = { Text(stringResource(R.string.studio_versions)) }, text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(state.versions, key = { it.revision }) { version -> TextButton(onClick = { viewModel.restore(version.revision) }, enabled = !busy) {
                    Text(stringResource(R.string.studio_restore) + " · " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(version.savedAt)))
                } }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = viewModel::dismissVersions) { Text(stringResource(R.string.studio_close)) } })
    state.output?.let { output -> StudioAlertDialog(onDismissRequest = viewModel::dismissOutput,
        title = { Text(stringResource(R.string.studio_output_saved)) }, text = { Text(output.displayName) },
        confirmButton = { TextButton(onClick = { viewModel.dismissOutput(); onOpenOutput(output) }) { Text(stringResource(R.string.studio_open_output)) } },
        dismissButton = { TextButton(onClick = { viewModel.dismissOutput(); onInsertOutput(output) }) { Text(stringResource(R.string.studio_insert_canvas)) } }) }
    activeInk?.let { session -> StudioInkDialog(viewModel.service, session, {
        viewModel.saveInkCopy(session, defaultName); activeInk = null; viewModel.finishInk()
    }, { activeInk = null; viewModel.finishInk() }) }
}

@Composable
private fun StudioNameDialog(initial: String, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember(initial) { mutableStateOf(initial) }
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.studio_project_name)) },
        text = { OutlinedTextField(title, { title = it.take(120) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onApply(title) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.studio_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.studio_cancel)) } })
}
