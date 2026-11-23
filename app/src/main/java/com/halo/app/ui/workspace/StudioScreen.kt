package com.halo.app.ui.workspace

import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.instruments.HaloInstrumentAction
import com.halo.app.ui.instruments.HaloInstrumentBar

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
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
import com.halo.app.ui.screens.notebook.InkMode
import com.halo.sdk.runtime.ink.InkCodec
import com.halo.sdk.runtime.ink.InkDocument
import com.halo.app.language.uiText
import com.halo.app.ui.zone5.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class StudioLayerEdit(val revision: Long, val layer: StudioLayer, val adding: Boolean = false,
    val geometryFirst: Boolean = false)
private data class StudioVersionPreview(val baseRevision: Long, val version: StudioProjectVersion)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(viewModel: StudioViewModel, onBack: () -> Unit, onOpenOutput: (WorkspaceFile) -> Unit,
    onInsertOutput: (WorkspaceFile) -> Unit, onCamera: (StudioProject) -> Unit, onAiDraft: (StudioProject) -> Unit,
    onRecoverCamera: ((StudioProject) -> Unit)? = null, externalBusy: Boolean = false,
    onOpenCanvas: ((String) -> Unit)? = null, canvasLibrary: Boolean = false) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val canvases by viewModel.canvases.collectAsStateWithLifecycle()
    val library by viewModel.libraryStatus.collectAsStateWithLifecycle()
    val images by viewModel.images.collectAsStateWithLifecycle()
    val latestPixelSelection by viewModel.service.pixelSelection.collectAsStateWithLifecycle()
    val busy = state.busy || externalBusy
    val project = state.project
    val projectId = project?.id
    val projectRevision = project?.revision
    val projectTitle = project?.title
    val document = project?.session?.current
    val selectedLayerId = document?.selectedLayerId
    val hasLayers = document != null && document.layers.isNotEmpty()
    val pixelSelection = latestPixelSelection?.takeIf { it.projectId == projectId && it.projectRevision == projectRevision }
    val context = LocalContext.current
    val lookOwner = remember(context.applicationContext) { studioAppearanceOwner(context) }
    var studioLook by rememberToolAppearance(lookOwner)
    var lookDraft by remember { mutableStateOf<StudioAppearance?>(null) }
    val appearance = lookDraft ?: studioLook?.current ?: StudioAppearance()
    val scope = rememberCoroutineScope()
    fun customize() {
        if (!busy && !canvasLibrary) lookDraft = studioLook?.current
    }
    val editPageLabel = stringResource(R.string.alive_tool_edit_page)
    val studioSettingsLabel = stringResource(R.string.alive_studio_look)
    val lookSnapshot = studioLook
    if (lookSnapshot != null && lookDraft != null) {
        ToolLookDialog(R.string.alive_studio_look, lookSnapshot, appearance, { lookDraft = it },
            onApply = {
                studioLook = lookOwner.apply(lookSnapshot, appearance)
                lookDraft = null
            }, onDismiss = { lookDraft = null }, preview = { StudioAppearancePreview(appearance.paper) }) {
            StudioAppearanceControls(appearance) { lookDraft = it }
        }
    }
    DisposableEffect(viewModel, projectId) {
        val token = projectId?.let(viewModel.service::attachProject)
        onDispose { token?.let(viewModel.service::detachProject) }
    }
    val defaultName = stringResource(R.string.studio_new_project)
    var discardTarget by remember(projectId) { mutableStateOf<StudioProject?>(null) }
    val requestDiscard: () -> Unit = { if (!busy) discardTarget = viewModel.state.value.project }
    var naming by remember(projectId) { mutableStateOf<String?>(null) }
    var namingTitle by remember(projectId) { mutableStateOf("") }
    var namingRevision by remember(projectId) { mutableStateOf<Long?>(null) }
    fun nameProject(mode: String) {
        viewModel.state.value.project?.let { current ->
            namingTitle = current.title; namingRevision = current.revision; naming = mode
        }
    }
    var editing by remember(projectId) { mutableStateOf<StudioLayerEdit?>(null) }
    var background by remember(projectId) { mutableStateOf<StudioProject?>(null) }
    var transforms by remember(projectId) { mutableStateOf<StudioProject?>(null) }
    var cropOnly by remember(projectId) { mutableStateOf(false) }
    var choosingShape by remember(projectId) { mutableStateOf(false) }
    var vectorExport by remember(projectId) { mutableStateOf(false) }
    var vectorEditing by remember(projectId) { mutableStateOf<Triple<Long, StudioLayer?, StudioShape>?>(null) }
    var moveTarget by remember(projectId) { mutableStateOf<Pair<StudioProject, StudioLayer>?>(null) }
    var eyedropper by remember(projectId) { mutableStateOf<StudioProject?>(null) }
    var fillTarget by remember(projectId) { mutableStateOf<StudioProject?>(null) }
    var pixelTarget by remember(projectId) { mutableStateOf<Pair<StudioProject, StudioPixelTool>?>(null) }
    var eraseTarget by remember(projectId) { mutableStateOf<StudioPixelSelection?>(null) }
    var wheelOpen by remember(projectId) { mutableStateOf(false) }
    var symmetryOpen by remember(projectId) { mutableStateOf(false) }
    var paletteOpen by remember(projectId) { mutableStateOf(false) }
    var referenceOpen by remember(projectId) { mutableStateOf(false) }
    var referenceId by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var referenceVersion by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var referencePickOwner by rememberSaveable { mutableStateOf<String?>(null) }
    var referenceOpacity by rememberSaveable(projectId) { mutableFloatStateOf(.8f) }
    var referenceOnTop by rememberSaveable(projectId) { mutableStateOf(true) }
    val pinnedReference = images.firstOrNull { it.objectId.value == referenceId && it.version == referenceVersion }
    fun unpinReference() { referenceId = null; referenceVersion = null }
    fun pinReference(file: WorkspaceFile) {
        referenceId = file.objectId.value; referenceVersion = file.version; referenceOpen = false
    }
    var swatches by rememberSaveable { mutableStateOf("") }
    var pendingPigment by remember(projectId) { mutableStateOf<Int?>(null) }
    var lightTarget by remember(projectId) { mutableStateOf<Pair<StudioProject, StudioLayer>?>(null) }
    var layers by remember(projectId) { mutableStateOf(false) }
    var addingLayer by remember(projectId) { mutableStateOf(false) }
    var removingLayer by remember(projectId) { mutableStateOf<StudioLayerEdit?>(null) }
    var librarySection by rememberSaveable(canvasLibrary) { mutableStateOf(if (canvasLibrary) "canvases" else "projects") }
    var historyOpen by remember(projectId) { mutableStateOf(false) }
    var versionPreview by remember(projectId) { mutableStateOf<StudioVersionPreview?>(null) }
    var restoring by remember(projectId) { mutableStateOf(false) }
    var importing by remember(projectId) { mutableStateOf(false) }
    var pendingInk by remember(projectId) { mutableStateOf<String?>(null) }
    var activeInk by remember(projectId) { mutableStateOf<StudioInkSession?>(null) }
    var inkMode by remember(projectId) { mutableStateOf(InkMode.DRAW) }
    var pickerProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerProjectRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    var importBaseRevision by remember(projectId) { mutableStateOf<Long?>(null) }
    var handoff by remember(projectId) { mutableStateOf<Pair<String, String>?>(null) }
    val showVersions: () -> Unit = { historyOpen = true; viewModel.versions() }
    val shellOwnsOrb = com.halo.app.ui.presence.LocalShellHaloOrb.current
    val drawName = stringResource(R.string.studio_draw)
    val pencilName = uiText("Pencil")
    val textName = stringResource(R.string.studio_text)
    val pictureName = stringResource(R.string.alive_studio_picture)
    val rectangleName = stringResource(R.string.studio_rectangle)
    fun speakTool(name: String) {
        if (studioLook?.current?.speakTools != true) return
        scope.launch {
            val graph = com.halo.app.di.ManualDependencyGraph
            val fields = com.halo.app.ui.components.VoiceFieldCancellation
            val ticket = fields.epoch.value
            val provenance = withContext(Dispatchers.IO) {
                com.halo.app.audio.readingProvenance(com.halo.sdk.provider.RemoteDataCategory.TEXT)
            }
            if (ticket != fields.epoch.value || fields.activeFields.value.isNotEmpty() ||
                graph.haloSpeechOutputCoordinator.playbackActive.value ||
                graph.readAloud.state.value?.preparing == true) return@launch
            graph.readAloud.start("studio-tool", name, graph.speechSynthesisRegistry.selector.ttsSelection.value.id,
                cloudApproved = provenance != null, language = null, remoteProvenance = provenance)
        }
    }
    fun openInk(mode: InkMode, pencil: Boolean = false, preset: StudioPigment? = null, symmetry: StudioSymmetry? = null) {
        val current = viewModel.state.value.project ?: return
        if (busy) return
        val selected = current.session.current.layers.find {
            it.id == current.session.current.selectedLayerId && it.kind == StudioLayerKind.INK && !it.locked &&
                (mode != InkMode.DRAW || it.pencil == pencil) && preset == null && (symmetry == null || symmetry == it.symmetry)
        }
        if (selected != null) {
            inkMode = mode
            activeInk = viewModel.startInk(current.revision, selected.id)
        } else if (mode == InkMode.DRAW && current.session.current.layers.size < StudioDocument.MAX_LAYERS) {
            inkMode = mode
            val ratio = current.session.current.width * 1000 / current.session.current.height
            val layer = StudioLayer(name = if (pencil) pencilName else drawName, kind = StudioLayerKind.INK,
                color = preset?.color ?: 0xff18203a.toInt(), strokeWidth = preset?.width ?: if (pencil) 1 else 5,
                ink = InkCodec.encode(InkDocument(aspectMilli = ratio.takeIf { it in 125..8000 } ?: 0)), pencil = pencil, symmetry = symmetry)
            viewModel.command(current.revision, StudioCommand.Add(layer)); pendingInk = layer.id
        }
    }
    fun draw() { speakTool(drawName); openInk(InkMode.DRAW, preset = pendingPigment?.let { StudioPigment(it, 5) }); pendingPigment = null }
    fun openCrop() {
        cropOnly = true
        transforms = viewModel.state.value.project
    }
    fun openImageTransforms() {
        cropOnly = false
        transforms = viewModel.state.value.project
    }
    fun add(kind: StudioLayerKind, name: String) {
        val current = viewModel.state.value.project ?: return
        if (!busy) {
            speakTool(name)
            editing = StudioLayerEdit(current.revision, StudioLayer(name = name, kind = kind,
                bounds = StudioRect(100, 100, 800, 300)), true)
        }
    }
    fun pickPicture() {
        if (!busy) viewModel.state.value.project?.let {
            speakTool(pictureName)
            importBaseRevision = it.revision
            importing = true
        }
    }
    val pageOwner = ShellPageOwner(if (canvasLibrary) "canvases" else "studio", projectId, "${projectRevision}:${selectedLayerId}:$librarySection",
        canonicalSourceVersion = projectRevision?.toString(),
        statusLabel = project?.let { stringResource(when {
            state.operation == StudioOperation.SAVE -> R.string.work_saving
            state.operation == StudioOperation.EXPORT -> R.string.work_exporting
            state.busy -> R.string.work_keeping
            it.hasUnsavedChanges -> R.string.work_draft_kept
            else -> R.string.zone5_saved_state
        }) },
        selectionIds = listOfNotNull(selectedLayerId),
        helpDescription = stringResource(R.string.zone5_help_studio),
        guidanceBlocked = lookDraft != null || discardTarget != null || busy || state.error || state.loading || naming != null || addingLayer || editing != null || importing ||
            background != null || transforms != null || choosingShape || vectorExport || vectorEditing != null || moveTarget != null || eyedropper != null ||
            fillTarget != null || pixelTarget != null || eraseTarget != null || wheelOpen || symmetryOpen || paletteOpen ||
            referenceOpen || lightTarget != null || layers || removingLayer != null || historyOpen || activeInk != null || state.aiReview || state.output != null,
        completedGuideIds = buildSet {
            if (project != null) add("studio-new.v1")
            if (hasLayers) add("studio-layer.v1")
            if (canvases.isNotEmpty()) add("canvas-new.v1")
        },
        helpSteps = if (canvasLibrary) emptyList() else listOf(
            com.halo.app.ui.presence.ShellGuideStep("studio-new.v1", "page.studio.new", stringResource(R.string.zone5_help_create), project != null),
            com.halo.app.ui.presence.ShellGuideStep("studio-layer.v1", "page.studio.add-layer", stringResource(R.string.zone5_guide_studio_layer), hasLayers),
            com.halo.app.ui.presence.ShellGuideStep("studio-save.v1", "page.studio.save", stringResource(R.string.zone5_help_save_version), project != null && !project.hasUnsavedChanges),
            com.halo.app.ui.presence.ShellGuideStep("studio-export.v1", "page.studio.export", stringResource(R.string.zone5_help_make_copy), project != null && state.exportedSource == "${project.id}:${project.revision}")),
        nextStep = if (!busy && !state.loading && !state.error && naming == null && !addingLayer && editing == null &&
            !importing && background == null && transforms == null && !layers && !historyOpen && activeInk == null &&
            (project != null && !hasLayers || project == null &&
                (if (canvasLibrary) !library.canvasesLoading && !library.canvasesError && canvases.isEmpty()
                 else !library.projectsLoading && !library.projectsError && projects.isEmpty())))
            com.halo.app.ui.presence.ShellNextStep(if (project != null) "studio-layer.v1" else if (canvasLibrary) "canvas-new.v1" else "studio-new.v1",
                if (project != null) "page.studio.add-layer" else if (canvasLibrary) "page.studio.new-canvas" else "page.studio.new",
                stringResource(if (project != null) R.string.zone5_guide_studio_layer else if (canvasLibrary) R.string.zone5_guide_canvas_new else R.string.zone5_guide_studio)) else null,
        canonicalToolIds = if (project == null && librarySection == "canvases") listOf("halo_canvas_create", "halo_canvas_get")
            else listOf("halo_studio_current", "halo_studio_get", "halo_studio_list", "halo_studio_create", "halo_studio_edit", "halo_studio_save", "halo_studio_export"),
        activeToolId = if (project == null) librarySection else "project",
        defaultDisabledActionReason = stringResource(if (state.loading) R.string.action_loading else R.string.action_wait),
        disabledActionReasons = mapOf(
            "page.studio.new-canvas" to stringResource(if (busy) R.string.action_wait else if (state.loading) R.string.action_loading else R.string.zone5_host_unavailable),
            "page.studio.save" to stringResource(if (busy) R.string.action_wait else R.string.action_no_changes),
            "page.studio.discard" to stringResource(if (busy) R.string.action_wait else R.string.action_no_changes),
            "page.studio.undo" to stringResource(if (busy) R.string.action_wait else R.string.action_no_undo),
            "page.studio.redo" to stringResource(if (busy) R.string.action_wait else R.string.action_no_redo)))
    val editPage = ShellPageAction("page.studio.appearance", editPageLabel, HaloGlyph.APPEARANCE,
        !busy && studioLook != null, ::customize)
    val actions = if (project == null) listOf(
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
            ShellPageAction("page.studio.undo", uiText("Undo"), HaloGlyph.PREVIOUS, !busy && project.session.undo.isNotEmpty()) { viewModel.edit(project.revision, "undo") },
            ShellPageAction("page.studio.redo", uiText("Redo"), HaloGlyph.NEXT, !busy && project.session.redo.isNotEmpty()) { viewModel.edit(project.revision, "redo") },
            ShellPageAction("page.studio.image", stringResource(R.string.studio_import_image), HaloGlyph.FILE, !busy, ::pickPicture),
            ShellPageAction("page.studio.camera", stringResource(if (onRecoverCamera == null) R.string.studio_camera else R.string.studio_recover_photo), HaloGlyph.SEE, !busy) {
                if (onRecoverCamera == null) onCamera(project) else onRecoverCamera(project)
            },
            ShellPageAction("page.studio.draw", drawName, HaloGlyph.NOTE, !busy, ::draw),
            ShellPageAction("page.studio.text", textName, HaloGlyph.NOTE, !busy) { add(StudioLayerKind.TEXT, textName) },
            ShellPageAction("page.studio.shape", rectangleName, HaloGlyph.TOOL, !busy) { addingLayer = true },
            ShellPageAction("page.studio.transform", stringResource(R.string.studio_transforms), HaloGlyph.TOOL, !busy, ::openImageTransforms),
            ShellPageAction("page.studio.background", stringResource(R.string.studio_background), HaloGlyph.APPEARANCE, !busy) { background = project },
            ShellPageAction("page.studio.ai", stringResource(R.string.studio_ai), HaloGlyph.THINK, !busy) { onAiDraft(project) },
            ShellPageAction("page.studio.continue", stringResource(R.string.link53_continue), HaloGlyph.FILE, !busy) {
                handoff = project.id to project.revision.toString()
            },
            ShellPageAction("page.studio.new", stringResource(R.string.studio_presets), HaloGlyph.NOTE, !busy) { naming = "new" },
            ShellPageAction("page.studio.copy", stringResource(R.string.studio_save_as), HaloGlyph.FILE, !busy) { nameProject("copy") },
            ShellPageAction("page.studio.export-jpeg", stringResource(R.string.studio_export_jpeg), HaloGlyph.FILE, !busy) { viewModel.export(CanvasImageFormat.JPEG) },
            ShellPageAction("page.studio.discard", stringResource(R.string.studio_discard), HaloGlyph.TRASH,
                !busy && project.hasUnsavedChanges, requestDiscard),
        )
    val preferredIds = appearance.favorites.map { "page.studio." + it.action }
    val sortedActions = if (project == null) actions else actions.sortedBy { action ->
        preferredIds.indexOf(action.id).takeIf { it >= 0 } ?: Int.MAX_VALUE
    }
    BindShellPageActions(pageOwner, if (canvasLibrary) sortedActions else sortedActions + editPage)
    discardTarget?.let { target ->
        StudioAlertDialog(onDismissRequest = { discardTarget = null },
            title = { Text(stringResource(R.string.studio_discard_confirm)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(target.title)
                Text(stringResource(R.string.studio_discard_effect))
                if (projectRevision != target.revision) Text(stringResource(R.string.studio_recovery_changed))
            } },
            confirmButton = { TextButton(onClick = {
                viewModel.discardDraft(target.id, target.revision); discardTarget = null
            }, enabled = !busy && projectId == target.id && projectRevision == target.revision) { Text(stringResource(R.string.studio_discard)) } },
            dismissButton = { TextButton(onClick = { discardTarget = null }) { Text(uiText("Cancel")) } })
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetId = pickerProjectId; val targetRevision = pickerProjectRevision
        pickerProjectId = null; pickerProjectRevision = null
        if (uri != null) {
            val current = viewModel.state.value.project
            if (current != null && targetId != null && targetRevision != null && current.id == targetId && current.revision == targetRevision) viewModel.importUri(uri)
            else viewModel.reportError(StudioOperation.IMPORT, StudioFailureReason.CHANGED)
        }
    }
    var restoreLease by rememberSaveable { mutableStateOf<Long?>(null) }
    val restoredTitle = uiText("Restored Studio project")
    val packagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val lease = restoreLease
        restoreLease = null
        if (uri != null && lease != null && viewModel.state.value.project == null && !viewModel.state.value.busy) {
            viewModel.restorePackage(uri, restoredTitle) {
                val graph = com.halo.app.di.ManualDependencyGraph
                graph.kidsPreferences.requireAdultAction(lease)
                graph.haloPause.requireActive()
            }
        }
    }
    val referencePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val owner = referencePickOwner
        referencePickOwner = null
        if (uri != null && owner != null) viewModel.importReference(uri, owner, ::pinReference)
    }
    BackHandler(project != null && lookDraft == null) { if (!busy) viewModel.closeProject() }
    LaunchedEffect(projectRevision, pendingInk, state.error) {
        val id = pendingInk
        if (project != null && id != null && project.session.current.layers.any { it.id == id }) {
            activeInk = viewModel.startInk(project.revision, id); pendingInk = null
        } else if (state.error) pendingInk = null
    }
    LaunchedEffect(restoring, state.busy, state.error) {
        if (restoring && !state.busy) {
            restoring = false
            if (!state.error) { versionPreview = null; historyOpen = false }
        }
    }
    if (shellOwnsOrb) com.halo.app.ui.presence.BindShellUtilityBand(pageOwner, includesStatus = project != null) {
        com.halo.app.ui.screens.notebook.ToolZoneHeader(projectTitle ?: stringResource(if (canvasLibrary) R.string.zone5_tool_canvas else R.string.zone5_tool_studio),
            onBack = { if (project != null) viewModel.closeProject() else onBack() }, enabled = !busy,
            onTitleClick = if (project != null) ({ nameProject("rename") }) else null,
            subtitle = project?.let { pageOwner.statusLabel + " · " + stringResource(R.string.zone5_graphics_dimensions,
                it.session.current.width, it.session.current.height, it.session.current.layers.size) },
            onTitleLongClick = (::customize).takeIf { !canvasLibrary && studioLook != null }) {
                if (project != null) IconButton(onClick = viewModel::save, enabled = !busy && project.hasUnsavedChanges) {
                    val saveLabel = stringResource(R.string.studio_save)
                    Box(Modifier.semantics { contentDescription = saveLabel }) { Zone5Icon("action_save", HaloGlyph.NOTE, Modifier.size(24.dp)) }
                }
                if (project != null) StudioOverflowMenu(busy, project.hasUnsavedChanges,
                    onBackground = { background = project }, onPresets = { naming = "new" },
                    onSaveAs = { nameProject("copy") }, onVersions = showVersions,
                    onDiscard = requestDiscard,
                    onExportPng = { viewModel.export(CanvasImageFormat.PNG) },
                    onExportJpeg = { viewModel.export(CanvasImageFormat.JPEG) },
                    onVectorExport = { vectorExport = true }, onCustomize = { if (studioLook != null) customize() })
        }
    }
    Scaffold(containerColor = com.halo.app.ui.theme.haloWorkspaceBackground(), topBar = {
        if (!shellOwnsOrb) TopAppBar(title = { Text(projectTitle ?: stringResource(if (canvasLibrary) R.string.zone5_tool_canvas else R.string.zone5_tool_studio), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = { if (project != null) viewModel.closeProject() else onBack() }, enabled = !busy) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.studio_close))
            } }, actions = {
                if (project != null) IconButton(onClick = viewModel::save, enabled = !busy && project.hasUnsavedChanges) {
                    Icon(Icons.Default.Save, stringResource(R.string.studio_save), tint = MaterialTheme.colorScheme.primary)
                }
                if (project != null) StudioOverflowMenu(busy, project.hasUnsavedChanges,
                    onBackground = { background = project }, onPresets = { naming = "new" },
                    onSaveAs = { nameProject("copy") }, onVersions = showVersions,
                    onDiscard = requestDiscard,
                    onExportPng = { viewModel.export(CanvasImageFormat.PNG) },
                    onExportJpeg = { viewModel.export(CanvasImageFormat.JPEG) },
                    onVectorExport = { vectorExport = true }, onCustomize = { if (studioLook != null) customize() })
                if (project == null && !canvasLibrary) TextButton(onClick = ::customize,
                    enabled = !busy && studioLook != null) { Text(studioSettingsLabel) }
            })
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (project != null) Column(Modifier.fillMaxSize().padding(16.dp).testTag("studio-content")) {
                if (shellOwnsOrb) Zone5InstrumentHeading(stringResource(R.string.zone5_tool_studio), "tool_studio", project.title,
                    state = stringResource(if (project.hasUnsavedChanges) R.string.studio_local_draft else R.string.studio_saved))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                StudioRecoveryNotice(state, viewModel::retry, viewModel::dismissError)
                val document = project.session.current
                val selectedLayer = document.layers.firstOrNull { it.id == document.selectedLayerId && !it.locked }
                val selectedInk = selectedLayer?.kind == StudioLayerKind.INK
                val selectedBrush = selectedInk && selectedLayer?.pencil == false
                val canAdd = document.layers.size < StudioDocument.MAX_LAYERS
                val ready = !busy && pendingInk == null
                val imageLayer = document.layers.firstOrNull { it.id == document.selectedLayerId && it.kind == StudioLayerKind.IMAGE && it.visible }
                val hasArtwork = document.background ushr 24 != 0 || document.layers.any { it.visible && it.opacity > 0 }
                val editablePixels = selectedLayer != null && selectedLayer.visible && selectedLayer.opacity > 0
                HaloInstrumentBar("studio", mapOf(
                    "studio.move" to HaloInstrumentAction({ selectedLayer?.let { moveTarget = project to it } }, ready && selectedLayer != null),
                    "studio.rectangle_ellipse_select" to HaloInstrumentAction({ pixelTarget = project to StudioPixelTool.MARQUEE }, ready),
                    "studio.lasso" to HaloInstrumentAction({ pixelTarget = project to StudioPixelTool.LASSO }, ready),
                    "studio.magic_wand" to HaloInstrumentAction({ pixelTarget = project to StudioPixelTool.WAND }, ready && hasArtwork),
                    "studio.select_subject" to HaloInstrumentAction({ pixelTarget = project to StudioPixelTool.SUBJECT }, ready && hasArtwork),
                    "studio.brush" to HaloInstrumentAction(::draw, ready && (canAdd || selectedBrush && pendingPigment == null), working = pendingInk != null),
                    "studio.pencil" to HaloInstrumentAction({
                        openInk(InkMode.DRAW, pencil = true, preset = pendingPigment?.let { StudioPigment(it, 1) }); pendingPigment = null
                    }, ready && (canAdd || selectedInk && selectedLayer?.pencil == true && pendingPigment == null)),
                    "studio.eraser" to HaloInstrumentAction({ openInk(InkMode.ERASE) }, ready && selectedInk),
                    "studio.smudge_blend" to HaloInstrumentAction({ pixelTarget = project to StudioPixelTool.SMUDGE }, ready && editablePixels),
                    "studio.fill" to HaloInstrumentAction({ fillTarget = project }, ready && canAdd),
                    "studio.eyedropper" to HaloInstrumentAction({ eyedropper = project }, ready && document.layers.isNotEmpty() && canAdd),
                    "studio.colour_wheel_triangle" to HaloInstrumentAction({ wheelOpen = true }, ready && canAdd),
                    "studio.shapes" to HaloInstrumentAction({ choosingShape = true }, ready && canAdd),
                    "studio.text" to HaloInstrumentAction({ add(StudioLayerKind.TEXT, textName) }, ready && canAdd),
                    "studio.transform" to HaloInstrumentAction({ selectedLayer?.let { editing = StudioLayerEdit(project.revision, it, geometryFirst = true) } }, ready && selectedLayer != null),
                    "studio.crop" to HaloInstrumentAction(::openCrop, ready),
                    "studio.layers_panel" to HaloInstrumentAction({ layers = true }, ready),
                    "studio.symmetry" to HaloInstrumentAction({ symmetryOpen = true }, ready && canAdd),
                    "studio.reference_image" to HaloInstrumentAction({ referenceOpen = true }, ready, selected = pinnedReference != null),
                    "studio.history" to HaloInstrumentAction(showVersions, ready),
                    "studio.quick_palette" to HaloInstrumentAction({ paletteOpen = true }, ready && canAdd),
                    "studio.fix_the_light_halo" to HaloInstrumentAction({ imageLayer?.let { lightTarget = project to it } }, ready && canAdd && imageLayer != null),
                ), primary = "studio.brush")
                if (pixelSelection != null) {
                    val selectionName = uiText("Pixel selection")
                    StudioSelectedPixelActions(
                        ready, canAdd, editablePixels,
                        onCopy = { viewModel.applyPixels(project.revision, StudioPixelEdit.Apply(StudioPixelOperation.COPY, selectionName), pixelSelection) },
                        onErase = { eraseTarget = pixelSelection },
                        onClear = { viewModel.edit(project.revision, "clear_pixel_selection") },
                    )
                }
                StudioWorkbench(viewModel.service, project, busy, ::draw, { add(StudioLayerKind.TEXT, textName) },
                    { addingLayer = true }, ::pickPicture,
                    { layers = true }, ::openImageTransforms,
                    { id -> viewModel.command(project.revision, StudioCommand.Select(id)) },
                    { layer -> editing = StudioLayerEdit(project.revision, layer) }, Modifier.weight(1f),
                    appearance = appearance, onUndo = { viewModel.edit(project.revision, "undo") }, pixelSelection = pixelSelection?.mask, reference = {
                        pinnedReference?.let { file ->
                            StudioReferenceOverlay(file, referenceOpacity, ::unpinReference, Modifier.align(Alignment.TopEnd).padding(6.dp))
                        }
                    })
            } else LazyColumn(Modifier.fillMaxSize().testTag("studio-content"), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (shellOwnsOrb) item("studio-heading") { Zone5InstrumentHeading(stringResource(R.string.studio_title), if (canvasLibrary) "tool_canvas" else "tool_studio", projectTitle,
                    state = if (project == null) stringResource(if (canvasLibrary) R.string.zone5_canvas_purpose else R.string.zone5_graphics_intro) else stringResource(
                        if (project.hasUnsavedChanges) R.string.studio_local_draft else R.string.studio_saved)) }
                if (state.loading) item { CircularProgressIndicator(); Text(stringResource(R.string.studio_loading)) }
                if (state.error) item { StudioRecoveryNotice(state, viewModel::retry, viewModel::dismissError) }
                if (project == null && !state.loading && librarySection == "canvases") {
                    item {
                        if (library.canvasesLoading && canvases.isEmpty()) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(uiText("Loading…")) }
                        if (library.canvasesError) {
                            Text(stringResource(R.string.studio_error), color = MaterialTheme.colorScheme.error)
                            TextButton(viewModel::retryLibrary) { Text(uiText("Try again")) }
                        }
                        if (!library.canvasesLoading && !library.canvasesError && canvases.isEmpty()) Text(stringResource(R.string.zone5_no_canvases))
                        if (!shellOwnsOrb || !library.canvasesLoading && !library.canvasesError && canvases.isEmpty())
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
                        if (!shellOwnsOrb) Text(stringResource(R.string.studio_local), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = {
                            val graph = com.halo.app.di.ManualDependencyGraph
                            restoreLease = runCatching { graph.kidsPreferences.adultActionRevision() }.getOrNull()
                            if (restoreLease != null && !graph.isHaloPaused())
                                packagePicker.launch(arrayOf("application/zip", "application/octet-stream"))
                        }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Open Halo project file")) }
                        if (library.projectsLoading && projects.isEmpty()) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(uiText("Loading…")) }
                        if (library.projectsError) {
                            Text(stringResource(R.string.studio_error), color = MaterialTheme.colorScheme.error)
                            TextButton(viewModel::retryLibrary) { Text(uiText("Try again")) }
                        }
                        if (!shellOwnsOrb || !library.projectsLoading && !library.projectsError && projects.isEmpty())
                            Button(onClick = { naming = "new" }, enabled = !busy,
                                modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.studio_new_project)) }
                    }
                    items(projects, key = { it.id }) { item ->
                        OutlinedCard(onClick = { viewModel.open(item.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Zone5Icon("type_image", HaloGlyph.FILE, Modifier.size(32.dp))
                                Text(item.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Zone5Icon("action_forward", HaloGlyph.NEXT, Modifier.size(20.dp))
                            }
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
            if (live?.id == projectId && live?.revision == namingRevision) {
                if (mode == "rename") viewModel.rename(title) else viewModel.saveAs(title)
                naming = null
            } else viewModel.reportError()
        }, { naming = null }, enabled = !busy && project != null && project.revision == namingRevision,
            title = if (mode == "rename") uiText("Rename") else stringResource(R.string.studio_project_name))
    }
    handoff?.let { (id, revision) -> WorkspaceContinueSheet(id, revision) { handoff = null } }
    if (vectorExport && project != null) com.halo.app.ui.zone6.Zone6ButtonSheet(uiText("Export project"), { vectorExport = false }) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            StudioProjectFormat.entries.forEach { format ->
                TextButton(onClick = { vectorExport = false; viewModel.exportProject(format) },
                    enabled = !busy && StudioProjectExport.allows(project.session.current, format)) {
                    Text(format.name)
                }
            }
            Text(uiText("SVG and DXF use vector shapes. PDF uses the document scale. A Halo package keeps editable layers."),
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if (choosingShape && project != null) com.halo.app.ui.zone6.Zone6ButtonSheet(uiText("Shapes"), { choosingShape = false }) {
        val ellipseName = stringResource(R.string.studio_ellipse)
        TextButton({ choosingShape = false; add(StudioLayerKind.RECTANGLE, rectangleName) }, enabled = !busy) { Text(rectangleName) }
        TextButton({ choosingShape = false; add(StudioLayerKind.ELLIPSE, ellipseName) }, enabled = !busy) { Text(ellipseName) }
        TextButton(onClick = {
            choosingShape = false
            vectorEditing = Triple(project.revision, null, StudioShape.PATH)
        }, enabled = !busy) { Text(uiText("Precision drawing")) }
        val selectedShape = project.session.current.layers.firstOrNull {
            it.id == project.session.current.selectedLayerId && !it.locked &&
                it.kind in setOf(StudioLayerKind.RECTANGLE, StudioLayerKind.ELLIPSE)
        }
        if (selectedShape != null) TextButton(onClick = {
            choosingShape = false
            vectorEditing = Triple(project.revision, selectedShape,
                selectedShape.vector?.shape ?: if (selectedShape.kind == StudioLayerKind.ELLIPSE) StudioShape.ARC else StudioShape.ROUNDED_RECT)
        }, enabled = !busy) { Text(uiText("Edit selected shape")) }
    }
    vectorEditing?.let { (revision, layer, shape) ->
        val current = project?.takeIf { it.revision == revision }
        if (current != null) StudioVectorDialog(current.session.current, layer, shape, onApply = { updated ->
            viewModel.command(revision, if (layer == null) StudioCommand.Add(updated) else StudioCommand.Replace(updated))
            vectorEditing = null
        }, onDismiss = { vectorEditing = null })
        else LaunchedEffect(revision) { vectorEditing = null }
    }
    moveTarget?.let { (snapshot, layer) ->
        StudioMoveDialog(snapshot.session.current, layer, projectRevision == snapshot.revision && !busy,
            onApply = { viewModel.command(snapshot.revision, it); moveTarget = null }, onDismiss = { moveTarget = null })
    }
    eyedropper?.let { snapshot ->
        StudioEyedropperDialog(viewModel.service, snapshot.session.current, projectRevision == snapshot.revision && !busy, { color ->
            pendingPigment = color
            swatches = studioRecentPigments(swatches, color)
            eyedropper = null
        }, { eyedropper = null })
    }
    fillTarget?.let { snapshot ->
        val name = uiText("Fill")
        StudioFillDialog(viewModel.service, snapshot.session.current, pendingPigment ?: 0xff18203a.toInt(), projectRevision == snapshot.revision && !busy,
            onApply = { request -> viewModel.fill(snapshot.revision, request, name); fillTarget = null }, onDismiss = { fillTarget = null })
    }
    pixelTarget?.let { (snapshot, tool) ->
        StudioPixelInstrumentDialog(viewModel, snapshot, tool, pixelSelection,
            projectRevision == snapshot.revision && projectId == snapshot.id && !busy, onDismiss = { pixelTarget = null })
    }
    eraseTarget?.let { target ->
        StudioErasePixelConfirmation(enabled = !busy && target === pixelSelection,
            onErase = {
                viewModel.applyPixels(target.projectRevision, StudioPixelEdit.Apply(StudioPixelOperation.ERASE), target)
                eraseTarget = null
            }, onDismiss = { eraseTarget = null })
    }
    if (wheelOpen) StudioColourWheel(pendingPigment ?: 0xff18203a.toInt(), swatches, { color ->
        pendingPigment = color; swatches = studioRecentPigments(swatches, color); wheelOpen = false
    }, { wheelOpen = false })
    if (symmetryOpen) StudioSymmetryDialog({ symmetry ->
        openInk(InkMode.DRAW, preset = pendingPigment?.let { StudioPigment(it, 5) }, symmetry = symmetry)
        pendingPigment = null; symmetryOpen = false
    }, { symmetryOpen = false })
    if (referenceOpen && project != null) StudioReferenceDialog(images, pinnedReference, referenceOpacity, referenceOnTop,
        loading = busy || library.imagesLoading,
        onOpacity = { referenceOpacity = it }, onAlwaysOnTop = { referenceOnTop = it }, onChoose = ::pinReference,
        onBrowse = { referencePickOwner = project.id; referencePicker.launch(arrayOf("image/*")) },
        onRemove = ::unpinReference, onDismiss = { referenceOpen = false })
    if (paletteOpen) StudioQuickPaletteDialog(swatches, pendingPigment, project != null && !busy, { pigment ->
        swatches = studioRecentPigments(swatches, pigment.color)
        pendingPigment = null
        paletteOpen = false
        openInk(InkMode.DRAW, preset = pigment)
    }, { paletteOpen = false })
    lightTarget?.let { (snapshot, layer) ->
        StudioLightDialog(viewModel.service, snapshot.session.current, layer, projectRevision == snapshot.revision && !busy,
            onApply = { viewModel.command(snapshot.revision, it); lightTarget = null }, onDismiss = { lightTarget = null })
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
                addingLayer = false; draw()
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
    }, { editing = null }, geometryFirst = draft.geometryFirst) }
    transforms?.let { draft -> StudioTransformDialog(viewModel.service, draft.session.current,
        { viewModel.command(draft.revision, it) }, { transforms = null }, cropOnly = cropOnly) }
    if (layers && project != null) com.halo.app.ui.zone6.Zone6ButtonSheet(
        stringResource(R.string.z6_sheet_layers), { layers = false }) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(bottom = 24.dp)) {
            item {
                TextButton(onClick = { layers = false; addingLayer = true }, enabled = !busy,
                    modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.z6_sheet_layers_add))
                }
            }
            if (project.session.current.layers.isEmpty()) item { Text(stringResource(R.string.zone5_no_layers), Modifier.padding(16.dp)) }
            items(project.session.current.layers.asReversed(), key = { it.id }) { layer ->
                val selected = project.session.current.selectedLayerId == layer.id
                var layerMenuOpen by remember(layer.id) { mutableStateOf(false) }
                val pinnedImage = layer.source?.let { source -> images.firstOrNull {
                    it.objectId.value == source.objectId && it.version == source.version
                } }
                Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.medium,
                    border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.command(project.revision, StudioCommand.Select(layer.id)) },
                            enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                if (pinnedImage != null) CanvasAnnotatedFilePreview(pinnedImage, "", Modifier.size(40.dp), maxDimension = 96)
                                else Surface(Modifier.size(40.dp), shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                    Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                        val glyph = when (layer.kind) {
                                            StudioLayerKind.TEXT -> HaloGlyph.NOTE
                                            StudioLayerKind.IMAGE -> HaloGlyph.FILE
                                            else -> HaloGlyph.TOOL
                                        }
                                        com.halo.app.ui.icons.HaloIcon(glyph, null, Modifier.size(20.dp))
                                    }
                                }
                                Column {
                                    Text(layer.name, maxLines = 2)
                                    Text(uiText(layer.kind.name.lowercase().replaceFirstChar(Char::uppercaseChar)),
                                        style = MaterialTheme.typography.bodySmall)
                                    if (layer.locked) Text(uiText("Locked"), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Switch(layer.visible, { visible ->
                            viewModel.command(project.revision, StudioCommand.Replace(layer.copy(visible = visible)))
                        }, enabled = !busy && !layer.locked, modifier = Modifier.semantics {
                            contentDescription = layer.name
                        })
                        Box {
                            IconButton(onClick = { layerMenuOpen = true }, enabled = !busy) {
                                Icon(Icons.Default.MoreVert, stringResource(R.string.z6_sheet_more))
                            }
                            val index = project.session.current.layers.indexOf(layer)
                            DropdownMenu(expanded = layerMenuOpen, onDismissRequest = { layerMenuOpen = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.studio_edit_layer)) }, onClick = {
                                    layerMenuOpen = false
                                    editing = StudioLayerEdit(project.revision, layer)
                                    layers = false
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.studio_move_up)) }, enabled = !layer.locked && index < project.session.current.layers.lastIndex,
                                    onClick = { layerMenuOpen = false; viewModel.command(project.revision, StudioCommand.Move(layer.id, index + 1)) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.studio_move_down)) }, enabled = !layer.locked && index > 0,
                                    onClick = { layerMenuOpen = false; viewModel.command(project.revision, StudioCommand.Move(layer.id, index - 1)) })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.z6_sheet_delete), color = MaterialTheme.colorScheme.error) },
                                    enabled = !layer.locked,
                                    onClick = {
                                        layerMenuOpen = false
                                        removingLayer = StudioLayerEdit(project.revision, layer)
                                        layers = false
                                    },
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
    removingLayer?.let { target ->
        val valid = !busy && projectRevision == target.revision &&
            project.session.current.layers.any { it == target.layer && !it.locked }
        StudioAlertDialog(onDismissRequest = { removingLayer = null },
            title = { Text(stringResource(R.string.studio_remove)) },
            text = { Text(if (valid) target.layer.name else stringResource(R.string.zone5_document_changed)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.command(target.revision, StudioCommand.Remove(target.layer.id))
                    removingLayer = null
                }, enabled = valid) { Text(uiText("Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removingLayer = null }) { Text(uiText("Cancel")) } })
    }
    if (importing) ModalBottomSheet(onDismissRequest = { importing = false }) {
        val valid = project != null && project.revision == importBaseRevision
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (!valid) Text(stringResource(R.string.zone5_document_changed))
            Button(onClick = {
                pickerProjectId = projectId; pickerProjectRevision = importBaseRevision
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
                if (state.error) item { StudioRecoveryNotice(state, viewModel::retry, viewModel::dismissError) }
                if (state.versionsLoaded && state.versions.isEmpty()) item { Text(stringResource(R.string.zone5_no_versions)) }
                items(state.versions, key = { it.revision }) { version -> TextButton(onClick = { project?.let { versionPreview = StudioVersionPreview(it.revision, version) } }, enabled = !busy) {
                    Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(version.savedAt)))
                } }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { historyOpen = false; viewModel.dismissVersions() }, enabled = !busy) { Text(stringResource(R.string.studio_close)) } })
    versionPreview?.let { target ->
        val document = remember(target.version) { runCatching { StudioDocumentCodec.decode(target.version.document) }.getOrNull() }
        val valid = !busy && projectId == target.version.objectId && projectRevision == target.baseRevision && document != null
        StudioAlertDialog(onDismissRequest = { if (!busy) versionPreview = null },
            title = { Text(stringResource(R.string.studio_versions)) }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(target.version.savedAt)))
                    document?.let { StudioDocumentPreview(viewModel.service, it, Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 300.dp)) }
                    if (!valid && !busy) Text(stringResource(R.string.zone5_document_changed))
                    StudioRecoveryNotice(state, viewModel::retry, viewModel::dismissError)
                }
            }, confirmButton = { TextButton(onClick = {
                restoring = viewModel.restore(target.version.revision, target.baseRevision, target.version.objectId)
            }, enabled = valid) { Text(stringResource(R.string.studio_restore)) } },
            dismissButton = { TextButton(onClick = { versionPreview = null }, enabled = !busy) { Text(stringResource(R.string.studio_cancel)) } })
    }
    state.output?.let { output -> StudioAlertDialog(onDismissRequest = viewModel::dismissOutput,
        title = { Text(stringResource(R.string.studio_output_saved)) }, text = { Column { Text(output.displayName); WorkspaceExportButton(output); WorkspaceContinueButton(output.objectId.value, output.version) } },
        confirmButton = { TextButton(onClick = { viewModel.dismissOutput(); onOpenOutput(output) }) { Text(stringResource(R.string.studio_open_output)) } },
        dismissButton = { if (output.mimeType in setOf("image/png", "image/jpeg")) TextButton(onClick = {
            viewModel.dismissOutput(); onInsertOutput(output)
        }) { Text(stringResource(R.string.studio_insert_canvas)) } }) }
    activeInk?.let { session -> StudioInkDialog(viewModel.service, session, {
        viewModel.saveInkCopy(session, defaultName); activeInk = null; viewModel.finishInk()
    }, { activeInk = null; viewModel.finishInk() }, initialMode = inkMode, reference = {
        if (referenceOnTop) pinnedReference?.let { file ->
            StudioReferenceOverlay(file, referenceOpacity, ::unpinReference, Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
    }) }
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
