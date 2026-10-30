package com.halo.app.ui.workspace

import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner
import com.halo.app.ui.icons.HaloGlyph

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.data.studio.*
import com.halo.app.data.workspace.CanvasImageFormat
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.ui.screens.notebook.CanvasAnnotatedFilePreview
import com.halo.sdk.runtime.ink.InkCodec
import com.halo.sdk.runtime.ink.InkDocument
import com.halo.app.language.uiText
import com.halo.app.ui.zone5.*

private data class StudioLayerEdit(val revision: Long, val layer: StudioLayer, val adding: Boolean = false)
private data class StudioVersionPreview(val baseRevision: Long, val version: StudioProjectVersion)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(viewModel: StudioViewModel, onBack: () -> Unit, onOpenOutput: (WorkspaceFile) -> Unit,
    onInsertOutput: (WorkspaceFile) -> Unit, onCamera: (StudioProject) -> Unit, onAiDraft: (StudioProject) -> Unit,
    onRecoverCamera: ((StudioProject) -> Unit)? = null, externalBusy: Boolean = false,
    onOpenCanvas: ((String) -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val canvases by viewModel.canvases.collectAsStateWithLifecycle()
    val library by viewModel.libraryStatus.collectAsStateWithLifecycle()
    val images by viewModel.images.collectAsStateWithLifecycle()
    val busy = state.busy || externalBusy
    val project = state.project
    DisposableEffect(viewModel, project?.id) {
        val token = project?.id?.let(viewModel.service::attachProject)
        onDispose { token?.let(viewModel.service::detachProject) }
    }
    val defaultName = stringResource(R.string.studio_new_project)
    var naming by remember(project?.id) { mutableStateOf<String?>(null) }
    var namingTitle by remember(project?.id) { mutableStateOf("") }
    var namingRevision by remember(project?.id) { mutableStateOf<Long?>(null) }
    fun nameProject(mode: String) {
        viewModel.state.value.project?.let { current ->
            namingTitle = current.title; namingRevision = current.revision; naming = mode
        }
    }
    var editing by remember(project?.id) { mutableStateOf<StudioLayerEdit?>(null) }
    var background by remember(project?.id) { mutableStateOf<StudioProject?>(null) }
    var transforms by remember(project?.id) { mutableStateOf<StudioProject?>(null) }
    var layers by remember(project?.id) { mutableStateOf(false) }
    var addingLayer by remember(project?.id) { mutableStateOf(false) }
    var librarySection by rememberSaveable { mutableStateOf("projects") }
    var historyOpen by remember(project?.id) { mutableStateOf(false) }
    var versionPreview by remember(project?.id) { mutableStateOf<StudioVersionPreview?>(null) }
    var restoring by remember(project?.id) { mutableStateOf(false) }
    var importing by remember(project?.id) { mutableStateOf(false) }
    var pendingInk by remember(project?.id) { mutableStateOf<String?>(null) }
    var activeInk by remember(project?.id) { mutableStateOf<StudioInkSession?>(null) }
    var pickerProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerProjectRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    var importBaseRevision by remember(project?.id) { mutableStateOf<Long?>(null) }
    val showVersions: () -> Unit = { historyOpen = true; viewModel.versions() }
    val shellOwnsOrb = com.halo.app.ui.presence.LocalShellHaloOrb.current
    val pageOwner = ShellPageOwner("studio", project?.id, "${project?.revision}:${project?.session?.current?.selectedLayerId}:$librarySection",
        canonicalSourceVersion = project?.revision?.toString(),
        selectionIds = listOfNotNull(project?.session?.current?.selectedLayerId),
        canonicalToolIds = if (project == null && librarySection == "canvases") listOf("halo_canvas_create", "halo_canvas_get")
            else listOf("halo_studio_current", "halo_studio_get", "halo_studio_list", "halo_studio_create", "halo_studio_edit", "halo_studio_save", "halo_studio_export"),
        activeToolId = if (project == null) librarySection else "project",
        disabledActionReasons = mapOf("page.studio.new-canvas" to stringResource(R.string.zone5_host_unavailable),
            "page.studio.add-layer" to uiText("Updating..."), "page.studio.export" to uiText("Updating...")))
    BindShellPageActions(pageOwner,
        if (project == null) listOf(
            if (librarySection == "canvases") ShellPageAction("page.studio.new-canvas", uiText("New canvas"), HaloGlyph.NOTE,
                !busy && !state.loading && onOpenCanvas != null) { naming = "canvas" }
            else ShellPageAction("page.studio.new", stringResource(R.string.studio_new_project), HaloGlyph.NOTE, !busy && !state.loading) { naming = "new" },
        ) else listOf(
            ShellPageAction("page.studio.add-layer", stringResource(R.string.zone5_add_layer), HaloGlyph.NOTE, !busy) { addingLayer = true },
            ShellPageAction("page.studio.layers", stringResource(R.string.studio_layers), HaloGlyph.NOTE, !busy) { layers = true },
            ShellPageAction("page.studio.export", stringResource(R.string.studio_export_png), HaloGlyph.FILE, !busy) { viewModel.export(CanvasImageFormat.PNG) },
            ShellPageAction("page.studio.versions", stringResource(R.string.studio_versions), HaloGlyph.ACTIVITY, !busy, showVersions),
            ShellPageAction("page.studio.rename", uiText("Rename"), HaloGlyph.NOTE, !busy) { nameProject("rename") },
            ShellPageAction("page.studio.save", stringResource(R.string.studio_save), HaloGlyph.NOTE, !busy && project.hasUnsavedChanges) { viewModel.save() },
        ))
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetId = pickerProjectId; val targetRevision = pickerProjectRevision
        pickerProjectId = null; pickerProjectRevision = null
        if (uri != null) {
            val current = viewModel.state.value.project
            if (current != null && targetId != null && targetRevision != null && current.id == targetId && current.revision == targetRevision) viewModel.importUri(uri)
            else viewModel.reportError()
        }
    }
    BackHandler(project != null) { if (!busy) viewModel.closeProject() }
    LaunchedEffect(project?.revision, pendingInk) {
        val id = pendingInk
        if (project != null && id != null && project.session.current.layers.any { it.id == id }) {
            activeInk = viewModel.startInk(project.revision, id); pendingInk = null
        }
    }
    LaunchedEffect(restoring, state.busy, state.error) {
        if (restoring && !state.busy) {
            restoring = false
            if (!state.error) { versionPreview = null; historyOpen = false }
        }
    }
    if (shellOwnsOrb) com.halo.app.ui.presence.BindShellUtilityBand(pageOwner) {
        com.halo.app.ui.screens.notebook.ToolZoneHeader(project?.title ?: stringResource(R.string.studio_title),
            onBack = { if (project != null) viewModel.closeProject() else onBack() }, enabled = !busy,
            onTitleClick = if (project != null) ({ nameProject("rename") }) else null) {
                if (project != null) IconButton(onClick = viewModel::save, enabled = !busy && project.hasUnsavedChanges) {
                    val saveLabel = stringResource(R.string.studio_save)
                    Box(Modifier.semantics { contentDescription = saveLabel }) { Zone5Icon("action_save", HaloGlyph.NOTE, Modifier.size(24.dp)) }
                }
                if (project != null) StudioOverflowMenu(busy, project.hasUnsavedChanges,
                    onBackground = { background = project }, onPresets = { naming = "new" },
                    onSaveAs = { nameProject("copy") }, onVersions = showVersions,
                    onDiscard = viewModel::discardDraft,
                    onExportPng = { viewModel.export(CanvasImageFormat.PNG) },
                    onExportJpeg = { viewModel.export(CanvasImageFormat.JPEG) })
        }
    }
    Scaffold(containerColor = com.halo.app.ui.theme.haloWorkspaceBackground(), topBar = {
        if (!shellOwnsOrb) TopAppBar(title = { Text(project?.title ?: stringResource(R.string.studio_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = { if (project != null) viewModel.closeProject() else onBack() }, enabled = !busy) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.studio_close))
            } }, actions = {
                if (project != null) IconButton(onClick = viewModel::save, enabled = !busy && project.hasUnsavedChanges) {
                    Icon(Icons.Default.Save, stringResource(R.string.studio_save), tint = MaterialTheme.colorScheme.primary)
                }
                if (project != null) StudioOverflowMenu(busy, project.hasUnsavedChanges,
                    onBackground = { background = project }, onPresets = { naming = "new" },
                    onSaveAs = { nameProject("copy") }, onVersions = showVersions,
                    onDiscard = viewModel::discardDraft,
                    onExportPng = { viewModel.export(CanvasImageFormat.PNG) },
                    onExportJpeg = { viewModel.export(CanvasImageFormat.JPEG) })
            })
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val previewHeight = (maxWidth * .9f).coerceIn(200.dp, 480.dp)
            LazyColumn(Modifier.fillMaxSize().testTag("studio-content"), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (shellOwnsOrb) item("studio-heading") { Zone5InstrumentHeading(stringResource(R.string.studio_title), "tool_studio", project?.title,
                    state = if (project == null) stringResource(R.string.zone5_projects_canvases) else stringResource(
                        if (project.hasUnsavedChanges) R.string.studio_local_draft else R.string.studio_saved)) }
                if (project == null) item("studio-tabs") {
                    Zone5LocalTabs(listOf(Zone5LocalTab("projects", uiText("Projects"), "tool_studio"),
                        Zone5LocalTab("canvases", uiText("Canvases"), "type_canvas")), librarySection, { librarySection = it })
                }
                if (state.loading) item { CircularProgressIndicator(); Text(stringResource(R.string.studio_loading)) }
                if (state.error) item {
                    Text(stringResource(R.string.studio_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::retry, enabled = !busy) { Text(stringResource(R.string.studio_retry)) }
                }
                if (project == null && !state.loading && librarySection == "canvases") {
                    item {
                        if (library.canvasesLoading && canvases.isEmpty()) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(uiText("Loading…")) }
                        if (library.canvasesError) {
                            Text(stringResource(R.string.studio_error), color = MaterialTheme.colorScheme.error)
                            TextButton(viewModel::retryLibrary) { Text(uiText("Try again")) }
                        }
                        if (!library.canvasesLoading && !library.canvasesError && canvases.isEmpty()) Text(stringResource(R.string.zone5_no_canvases))
                        Zone5ActionButton(uiText("New canvas"), "action_add", !busy && onOpenCanvas != null) { naming = "canvas" }
                        if (onOpenCanvas == null) Text(stringResource(R.string.zone5_host_unavailable))
                    }
                    items(canvases, key = { it.id.value }) { canvas ->
                        TextButton(onClick = { onOpenCanvas?.invoke(canvas.id.value) }, enabled = !busy && onOpenCanvas != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Zone5Icon("type_canvas", HaloGlyph.NOTE, Modifier.size(24.dp)); Spacer(Modifier.width(12.dp))
                            Text(canvas.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                } else if (project == null && !state.loading) {
                    item {
                        Text(stringResource(R.string.studio_local), style = MaterialTheme.typography.bodyMedium)
                        if (library.projectsLoading && projects.isEmpty()) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(uiText("Loading…")) }
                        if (library.projectsError) {
                            Text(stringResource(R.string.studio_error), color = MaterialTheme.colorScheme.error)
                            TextButton(viewModel::retryLibrary) { Text(uiText("Try again")) }
                        }
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
                            WorkspaceContinueButton(project.id, project.revision.toString(), enabled = !busy)
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
                            OutlinedButton(onClick = { importBaseRevision = project.revision; importing = true }, enabled = !busy) { Text(stringResource(R.string.studio_import_image)) }
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
        else if (mode == "canvas") StudioCanvasNameDialog({ title -> viewModel.createCanvas(title); naming = null }, { naming = null })
        else StudioNameDialog(namingTitle, { title ->
            val live = viewModel.state.value.project
            if (live?.id == project?.id && live?.revision == namingRevision) {
                if (mode == "rename") viewModel.rename(title) else viewModel.saveAs(title)
                naming = null
            } else viewModel.reportError()
        }, { naming = null }, enabled = !busy && project != null && project.revision == namingRevision,
            title = if (mode == "rename") uiText("Rename") else stringResource(R.string.studio_project_name))
    }
    if (addingLayer && project != null) ModalBottomSheet(onDismissRequest = { addingLayer = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.zone5_choose_layer), style = MaterialTheme.typography.titleLarge)
            Zone5ActionButton(stringResource(R.string.studio_import_image), "action_import", !busy, Modifier.fillMaxWidth()) {
                addingLayer = false; importBaseRevision = project.revision; importing = true
            }
            val textName = stringResource(R.string.studio_text)
            val rectangleName = stringResource(R.string.studio_rectangle)
            val ellipseName = stringResource(R.string.studio_ellipse)
            val drawName = stringResource(R.string.studio_draw)
            listOf(Triple(StudioLayerKind.TEXT, textName, "studio_text"), Triple(StudioLayerKind.RECTANGLE, rectangleName, "studio_shapes"),
                Triple(StudioLayerKind.ELLIPSE, ellipseName, "studio_shapes")).forEach { (kind, name, icon) ->
                Zone5ActionButton(name, icon, !busy, Modifier.fillMaxWidth()) {
                    addingLayer = false; editing = StudioLayerEdit(project.revision,
                        StudioLayer(name = name, kind = kind, bounds = StudioRect(100, 100, 800, 300)), true)
                }
            }
            Zone5ActionButton(drawName, "studio_draw", !busy, Modifier.fillMaxWidth()) {
                addingLayer = false
                val selected = project.session.current.layers.find { it.id == project.session.current.selectedLayerId && it.kind == StudioLayerKind.INK && !it.locked }
                if (selected != null) activeInk = viewModel.startInk(project.revision, selected.id)
                else {
                    val ratio = project.session.current.width * 1000 / project.session.current.height
                    val layer = StudioLayer(name = drawName, kind = StudioLayerKind.INK,
                        ink = InkCodec.encode(InkDocument(aspectMilli = ratio.takeIf { it in 125..8000 } ?: 0)))
                    viewModel.command(project.revision, StudioCommand.Add(layer)); pendingInk = layer.id
                }
            }
        }
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
            item { Text(stringResource(R.string.studio_layers), Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge) }
            if (project.session.current.layers.isEmpty()) item { Text(stringResource(R.string.zone5_no_layers), Modifier.padding(16.dp)) }
            items(project.session.current.layers.asReversed(), key = { it.id }) { layer ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    TextButton(onClick = { viewModel.command(project.revision, StudioCommand.Select(layer.id)) }, enabled = !busy) {
                        Text(layer.name + if (project.session.current.selectedLayerId == layer.id) " · " + stringResource(R.string.studio_selected) else "", maxLines = 2)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(stringResource(R.string.studio_visible))
                            Switch(layer.visible, { visible -> viewModel.command(project.revision, StudioCommand.Replace(layer.copy(visible = visible))) },
                                enabled = !busy && !layer.locked)
                        }
                        if (layer.locked) Text(uiText("Locked"), style = MaterialTheme.typography.bodySmall)
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
        val valid = project != null && project.revision == importBaseRevision
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (!valid) Text(stringResource(R.string.zone5_document_changed))
            Button(onClick = {
                pickerProjectId = project?.id; pickerProjectRevision = importBaseRevision
                importing = false; picker.launch(arrayOf("image/*"))
            }, enabled = !busy && valid) { Text(stringResource(R.string.studio_import_image)) }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                if (library.imagesLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                else if (library.imagesError) item {
                    Text(stringResource(R.string.zone5_unavailable), color = MaterialTheme.colorScheme.error)
                    Zone5ActionButton(uiText("Try again"), "action_refresh", onClick = viewModel::retryLibrary)
                } else if (images.isEmpty()) item { Text(stringResource(R.string.zone5_no_images)) }
                items(images, key = { it.objectId.value }) { image ->
                    ListItem(headlineContent = { Text(image.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { CanvasAnnotatedFilePreview(image, "", Modifier.size(48.dp), maxDimension = 128) },
                        modifier = Modifier.clickable(enabled = !busy && valid) { viewModel.importExisting(image.objectId.value, requireNotNull(image.version)); importing = false })
                }
            }
        }
    }
    if (historyOpen) StudioAlertDialog(onDismissRequest = { if (!busy) { historyOpen = false; viewModel.dismissVersions() } },
        title = { Text(stringResource(R.string.studio_versions)) }, text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                if (!state.versionsLoaded && !state.error) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(uiText("Loading…")) }
                if (state.error) item { Text(stringResource(R.string.studio_error)); TextButton(showVersions, enabled = !busy) { Text(uiText("Try again")) } }
                if (state.versionsLoaded && state.versions.isEmpty()) item { Text(stringResource(R.string.zone5_no_versions)) }
                items(state.versions, key = { it.revision }) { version -> TextButton(onClick = { project?.let { versionPreview = StudioVersionPreview(it.revision, version) } }, enabled = !busy) {
                    Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(version.savedAt)))
                } }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { historyOpen = false; viewModel.dismissVersions() }, enabled = !busy) { Text(stringResource(R.string.studio_close)) } })
    versionPreview?.let { target ->
        val document = remember(target.version) { runCatching { StudioDocumentCodec.decode(target.version.document) }.getOrNull() }
        val valid = !busy && project?.id == target.version.objectId && project?.revision == target.baseRevision && document != null
        StudioAlertDialog(onDismissRequest = { if (!busy) versionPreview = null },
            title = { Text(stringResource(R.string.studio_versions)) }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(target.version.savedAt)))
                    document?.let { StudioDocumentPreview(viewModel.service, it, Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 300.dp)) }
                    if (!valid && !busy) Text(stringResource(R.string.zone5_document_changed))
                    if (state.error) Text(stringResource(R.string.studio_error))
                }
            }, confirmButton = { TextButton(onClick = {
                restoring = viewModel.restore(target.version.revision, target.baseRevision, target.version.objectId)
            }, enabled = valid) { Text(stringResource(R.string.studio_restore)) } },
            dismissButton = { TextButton(onClick = { versionPreview = null }, enabled = !busy) { Text(stringResource(R.string.studio_cancel)) } })
    }
    state.output?.let { output -> StudioAlertDialog(onDismissRequest = viewModel::dismissOutput,
        title = { Text(stringResource(R.string.studio_output_saved)) }, text = { Column { Text(output.displayName); WorkspaceContinueButton(output.objectId.value, output.version) } },
        confirmButton = { TextButton(onClick = { viewModel.dismissOutput(); onOpenOutput(output) }) { Text(stringResource(R.string.studio_open_output)) } },
        dismissButton = { TextButton(onClick = { viewModel.dismissOutput(); onInsertOutput(output) }) { Text(stringResource(R.string.studio_insert_canvas)) } }) }
    activeInk?.let { session -> StudioInkDialog(viewModel.service, session, {
        viewModel.saveInkCopy(session, defaultName); activeInk = null; viewModel.finishInk()
    }, { activeInk = null; viewModel.finishInk() }) }
}

@Composable
private fun StudioCanvasNameDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(uiText("New canvas")) }, text = {
        OutlinedTextField(title, { title = it.take(160) }, label = { Text(uiText("Title")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }, confirmButton = { TextButton(onClick = { onCreate(title.trim()) }, enabled = title.trim().isNotEmpty()) { Text(uiText("Create")) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.studio_cancel)) } })
}

@Composable
private fun StudioNameDialog(initial: String, onApply: (String) -> Unit, onDismiss: () -> Unit,
    enabled: Boolean = true, title: String = "") {
    var value by rememberSaveable(initial) { mutableStateOf(initial) }
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(title.ifBlank { stringResource(R.string.studio_project_name) }) },
        text = { Column {
            OutlinedTextField(value, { value = it.take(120) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (!enabled) Text(stringResource(R.string.zone5_document_changed))
        } },
        confirmButton = { TextButton(onClick = { onApply(value) }, enabled = enabled && value.isNotBlank()) { Text(stringResource(R.string.studio_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.studio_cancel)) } })
}
