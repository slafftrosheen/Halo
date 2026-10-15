package com.halo.app.ui.screens.notebook

import com.halo.app.language.uiText
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.launch
import com.halo.app.data.notebook.CanvasFileMarkupCodec
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.data.notebook.CanvasNodeType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloFocusAction
import com.halo.app.ui.design.HaloEmptyState
import com.halo.app.ui.design.HaloProgressPresentation
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.fx.haloDraftBorder
import com.halo.app.data.notebook.CanvasNode
import kotlin.math.roundToInt

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CanvasEditorScreen(viewModel: CanvasEditorViewModel, focusNodeId: String? = null, onBack: () -> Unit) {
    var toolsExpanded by rememberSaveable(viewModel.canvasId) { mutableStateOf(false) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val boardEditingEnabled = state.canEditBoard()
    // Dismiss stale action menus if a background operation takes ownership of the board.
    // Saveable drafts remain intact and can be resumed when the board is ready.
    val memoReadiness by viewModel.memoReadiness.collectAsStateWithLifecycle()
    BackHandler { viewModel.leave(onBack) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val edgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    var showImageStudio by remember { mutableStateOf(false) }
    var showAi by rememberSaveable { mutableStateOf(false) }
    var draftReviewOpen by rememberSaveable(viewModel.canvasId) { mutableStateOf(true) }
    LaunchedEffect(state.aiDraft != null) { if (state.aiDraft != null) draftReviewOpen = true }
    var aiAction by rememberSaveable { mutableStateOf("Review") }
    var aiInstructions by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var rename by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var picker by remember { mutableStateOf(false) }
    var insertMenu by remember { mutableStateOf(false) }
    var cardMenu by remember { mutableStateOf(false) }
    LaunchedEffect(boardEditingEnabled) { if (!boardEditingEnabled) cardMenu = false }
    var inboxCapture by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var connect by remember { mutableStateOf(false) }
    var connectionLabel by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var boardWidth by remember { mutableStateOf(0) }
    var boardHeight by remember { mutableStateOf(0) }
    var focused by rememberSaveable(focusNodeId) { mutableStateOf(false) }
    LaunchedEffect(focusNodeId, state.loading, boardWidth, boardHeight) {
        if (!focused && !state.loading && boardWidth > 0 && boardHeight > 0 && focusNodeId != null) {
            viewModel.focusNode(focusNodeId); focused = true
        }
    }
    val selected = state.nodes.firstOrNull { it.id == state.selectedNodeId }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var sketchOpen by rememberSaveable { mutableStateOf(false) }
    var sketchTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var sketchOriginal by rememberSaveable { mutableStateOf("") }
    var annotateNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var imageToolsNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var pdfPageNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var stitchSources by remember { mutableStateOf<List<Pair<String, com.halo.app.data.workspace.CanvasStitchSource>>>(emptyList()) }
    var stitchPickerIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var annotationVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var annotationOriginal by rememberSaveable { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var boardSearch by rememberSaveable { mutableStateOf("") }
    var searchType by rememberSaveable { mutableStateOf("All") }
    LaunchedEffect(boardSearch, searchType, state.nodes.map { it.text }, state.edges, state.references) {
        val types = when (searchType) {
            "Notes" -> setOf(com.halo.app.data.notebook.WorkspaceObjectType.NOTE)
            "Files" -> setOf(com.halo.app.data.notebook.WorkspaceObjectType.FILE, com.halo.app.data.notebook.WorkspaceObjectType.MEDIA)
            else -> emptySet()
        }
        viewModel.searchCanvas(boardSearch, types)
    }
    var recognizing by remember { mutableStateOf(false) }
    var groupDialog by remember { mutableStateOf(false) }
    var groupTitle by rememberSaveable { mutableStateOf("") }
    var tagDialog by remember { mutableStateOf(false) }
    var tagTitle by rememberSaveable { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importSource(uri)
    }
    DisposableEffect(viewModel, lifecycleOwner) {
        com.halo.app.di.ManualDependencyGraph.activeCanvasForCapture.value = viewModel.canvasId
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.persistPending() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.persistPending()
            if (com.halo.app.di.ManualDependencyGraph.activeCanvasForCapture.value == viewModel.canvasId)
                com.halo.app.di.ManualDependencyGraph.activeCanvasForCapture.value = null
        }
    }
    if (inboxCapture) InboxCaptureDialog(viewModel.canvasId) { inboxCapture = false }
    if (insertMenu) AlertDialog(
        onDismissRequest = { insertMenu = false },
        title = { Text(stringResource(com.halo.app.R.string.brand_canvas_import_choice)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { insertMenu = false; picker = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(uiText("Link a workspace item")) }
                OutlinedButton(
                    onClick = { insertMenu = false; filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(uiText("Add a file")) }
            }
        },
        confirmButton = {
            TextButton(onClick = { insertMenu = false }) { Text(uiText("Cancel")) }
        }
    )
    CanvasOrganizationDialog(viewModel, state)
    if (sketchOpen && !state.loading) {
        val target = state.nodes.firstOrNull { it.id == sketchTarget && it.type == CanvasNodeType.SKETCH }
        CanvasSketchPadDialog(
            initial = sketchOriginal,
            recovery = state.drawingDraft?.takeIf { it.kind == "sketch" && it.nodeId == sketchTarget }?.sketch,
            onDraft = { encoded -> viewModel.keepDrawingDraft(com.halo.app.data.notebook.CanvasDrawingDraft(sketchTarget, "sketch", sketchOriginal, sketch = encoded)) },
            onSave = { encoded ->
                viewModel.addSketch(encoded, sketchTarget, sketchOriginal.takeIf { sketchTarget != null })
                sketchOpen = false
                sketchTarget = null
            },
            onDismiss = { viewModel.discardDrawingDraft(); sketchOpen = false; sketchTarget = null },
        )
    }

    if (groupDialog) AlertDialog(
        onDismissRequest = { groupDialog = false },
        title = { Text(uiText("Create group")) },
        text = { OutlinedTextField(groupTitle, { groupTitle = it.take(80) },
            label = { Text(uiText("Group title")) }, singleLine = true) },
        confirmButton = { Button(onClick = { viewModel.addGroup(groupTitle); groupDialog = false; groupTitle = "" },
            enabled = groupTitle.isNotBlank()) { Text(uiText("Create group")) } },
        dismissButton = { TextButton(onClick = { groupDialog = false }) { Text(uiText("Cancel")) } },
    )
    if (tagDialog) AlertDialog(
        onDismissRequest = { tagDialog = false },
        title = { Text(uiText("Tag selected")) },
        text = { OutlinedTextField(tagTitle, { tagTitle = it.take(50) },
            label = { Text(uiText("Tag name")) }, singleLine = true) },
        confirmButton = { Button(onClick = { viewModel.tagSelected(tagTitle); tagDialog = false; tagTitle = "" },
            enabled = tagTitle.trim().trimStart('#').isNotBlank()) { Text(uiText("Add tag")) } },
        dismissButton = { TextButton(onClick = { tagDialog = false }) { Text(uiText("Cancel")) } },
    )
    annotateNodeId?.let { nodeId ->
        val target = state.nodes.firstOrNull { it.id == nodeId }
        val file = target?.refObjectId?.let { state.files[it] }
        if (file != null && target != null) {
            CanvasSourceInkDialog(file, annotationOriginal,
                recovery = state.drawingDraft?.takeIf { it.kind == "annotation" && it.nodeId == nodeId },
                onDraft = { draft -> viewModel.keepDrawingDraft(draft.copy(nodeId = nodeId, fileVersion = annotationVersion)) },
                onSave = { updates -> viewModel.saveFileMarkup(nodeId, updates, annotationOriginal, annotationVersion); annotateNodeId = null },
                onDismiss = { viewModel.discardDrawingDraft(); annotateNodeId = null })
        }
    }
    imageToolsNodeId?.let { nodeId ->
        val target = state.nodes.firstOrNull { it.id == nodeId }
        val file = target?.refObjectId?.let(state.files::get)
        if (file != null && file.mimeType.startsWith("image/")) {
            CanvasLocalImageToolsDialog(
                file = file,
                busy = state.imageToolBusy,
                onSave = { recipe ->
                    viewModel.saveEditedImage(nodeId, file.version, recipe) { imageToolsNodeId = null }
                },
                onDismiss = { if (!state.importing) imageToolsNodeId = null },
            )
        }
    }
    pdfPageNodeId?.let { nodeId ->
        val target = state.nodes.firstOrNull { it.id == nodeId }
        val pdf = target?.refObjectId?.let(state.files::get)
        if (pdf != null && pdf.mimeType == "application/pdf") {
            CanvasPdfPageDialog(pdf, state.imageToolBusy,
                onSave = { pages ->
                    viewModel.saveExtractedPdfPages(nodeId, pdf, pages) { pdfPageNodeId = null }
                },
                onDismiss = { if (!state.imageToolBusy) pdfPageNodeId = null })
        }
    }
    if (stitchPickerIds.isNotEmpty()) {
        CanvasStitchSourcePicker(
            viewModel = viewModel,
            nodeIds = stitchPickerIds,
            onChoose = { chosen -> stitchSources = chosen; stitchPickerIds = emptySet() },
            onDismiss = { stitchPickerIds = emptySet() },
        )
    }
    if (stitchSources.isNotEmpty()) {
        CanvasImageStitchDialog(
            sources = stitchSources,
            busy = state.imageToolBusy,
            onSave = { recipe, ordered ->
                viewModel.saveCombinedImages(ordered, recipe) { stitchSources = emptyList() }
            },
            onDismiss = { if (!state.imageToolBusy) stitchSources = emptyList() },
        )
    }
    state.textDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = viewModel::discardRecognizedDraft,
            title = { Text(uiText(if (draft.sourceKind == "Transcript") "Review transcript" else "Review recognized text")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiText("Edit before adding this searchable text card."),
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(draft.text, viewModel::editRecognizedDraft,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 340.dp))
                }
            },
            confirmButton = {
                Button(onClick = viewModel::saveRecognizedDraft, enabled = draft.text.isNotBlank()) {
                    Text(uiText("Add text card"))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::discardRecognizedDraft) { Text(uiText("Discard")) }
            },
        )
    }
    if (showImageStudio) viewModel.imageStudio?.let { studio ->
        com.halo.app.ui.workspace.WorkspaceImageDialog(studio) { showImageStudio = false }
    }
    if (showAi) AlertDialog(onDismissRequest = { showAi = false }, title = { Text(uiText("Think with halo")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row { listOf("Review", "Expand", "Rewrite").forEach { action ->
                TextButton(onClick = { aiAction = action }) { Text(uiText(action) + if (action == aiAction) " ✓" else "") }
            } }
            Text(uiText(if (aiAction == "Rewrite") "Rewrite the selected text card." else "Use the text and link labels on this board."), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(aiInstructions, { aiInstructions = it.take(1000) }, label = { Text(uiText("Your direction (optional)")) }, minLines = 2)
        } }, confirmButton = { Button(onClick = { viewModel.requestAi(aiAction, aiInstructions); showAi = false }, enabled = boardEditingEnabled) { Text(uiText("Create draft")) } },
        dismissButton = { TextButton(onClick = { showAi = false }) { Text(uiText("Cancel")) } })
    state.aiDraft?.let { draft ->
        if (draftReviewOpen) {
            ModalBottomSheet(
                onDismissRequest = { draftReviewOpen = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                CanvasAiDraftReview(
                    draft = draft,
                    busy = !boardEditingEnabled,
                    onEdit = viewModel::editAi,
                    onKeep = viewModel::applyAi,
                    onDiscard = viewModel::discardAiDraft,
                    onRegenerate = viewModel::regenerateAi,
                    modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp),
                )
            }
        }
    }

    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text(uiText("Rename canvas")) },
        text = { OutlinedTextField(title, { title = it.take(160) }, singleLine = true, label = { Text(uiText("Title")) }) },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { viewModel.rename(title); rename = false }) { Text(uiText("Save")) } },
        dismissButton = { TextButton(onClick = { rename = false }) { Text(uiText("Cancel")) } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(uiText("Remove selected cards?")) },
        text = { Text(uiText("Its connections will also be removed. Linked source items stay in Workspace.")) },
        confirmButton = { TextButton(onClick = { viewModel.manualSelectionAction("Clear"); confirmDelete = false }) { Text(uiText("Delete")) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(uiText("Cancel")) } })
    if (picker) AlertDialog(onDismissRequest = { picker = false }, title = { Text(uiText("Link a workspace item")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text(uiText("Search items")) }, singleLine = true)
                val matches = remember(state.references, query) { state.references.filter { it.title.contains(query, true) } }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    if (matches.isEmpty()) item { Text(uiText("No matching items. Create a note or import a file in Workspace first.")) }
                    items(matches, key = { it.id.value }) { item ->
                        TextButton(onClick = { viewModel.addReference(item.id.value); picker = false; query = "" }) {
                            Column(Modifier.fillMaxWidth()) { Text(item.title.ifBlank { uiText("Untitled") }); Text(uiText(when (item.type) { com.halo.app.data.notebook.WorkspaceObjectType.CONVERSATION -> "Saved chat"; else -> item.type.name.lowercase().replaceFirstChar { it.uppercase() } }), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { picker = false }) { Text(uiText("Done")) } })
    if (connect && selected != null) AlertDialog(onDismissRequest = { connect = false }, title = { Text(uiText("Connect this card")) },
        text = {
            Column {
                OutlinedTextField(connectionLabel, { connectionLabel = it.take(100) }, label = { Text(uiText("Connection label (optional)")) }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(state.nodes.filter { it.id != selected.id }, key = { it.id }) { node ->
                        TextButton(onClick = { viewModel.connectSelected(node.id, connectionLabel); connect = false; connectionLabel = "" }) {
                            Text(node.readableText().ifBlank { "Untitled card" }.take(100))
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { connect = false }) { Text(uiText("Cancel")) } })

    if (menu) {
        ModalBottomSheet(onDismissRequest = { menu = false },
            containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(uiText("More tools"), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp))
                CanvasSheetAction("Think with halo", com.halo.app.R.drawable.halo_ic_think_with_halo,
                    enabled = boardEditingEnabled, accent = true) {
                    menu = false; showAi = true
                }
                CanvasSheetAction("Organize whole board", com.halo.app.R.drawable.halo_ic_ai_organize,
                    enabled = boardEditingEnabled && state.nodes.isNotEmpty()) {
                    menu = false; viewModel.organize(wholeBoard = true)
                }
                CanvasSheetAction("Study this canvas", com.halo.app.R.drawable.halo_ic_study_this,
                    enabled = boardEditingEnabled && state.nodes.isNotEmpty()) {
                    menu = false; viewModel.exportToNote(startStudy = true)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 4.dp))
                CanvasSheetAction("Find on canvas", com.halo.app.R.drawable.halo_ic_search) {
                    menu = false; searchOpen = true
                }
                CanvasSheetAction("Fit all cards", com.halo.app.R.drawable.halo_ic_fit_all,
                    enabled = state.nodes.isNotEmpty() && !state.loading && !state.missing) {
                    menu = false; viewModel.fitToContent()
                }
                CanvasSheetAction("Export to note", com.halo.app.R.drawable.halo_ic_export,
                    enabled = boardEditingEnabled && state.nodes.isNotEmpty()) {
                    menu = false; viewModel.exportToNote()
                }
                CanvasSheetAction("Rename", com.halo.app.R.drawable.halo_ic_new_note,
                    enabled = boardEditingEnabled) {
                    menu = false; title = state.title; rename = true
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 4.dp))
                CanvasSheetAction("Ask halo to organize", com.halo.app.R.drawable.halo_ic_ai_organize,
                    enabled = boardEditingEnabled && state.nodes.isNotEmpty()) {
                    menu = false; viewModel.organize(wholeBoard = state.selectedNodeIds.isEmpty(), ai = true)
                }
                CanvasSheetAction("Create an image", com.halo.app.R.drawable.halo_ic_create_image,
                    enabled = boardEditingEnabled && viewModel.imageStudio != null) {
                    menu = false; viewModel.imageStudio?.prepare(); showImageStudio = true
                }
                state.images[selected?.refObjectId]?.let { image ->
                    CanvasSheetAction("Edit image with AI", com.halo.app.R.drawable.halo_ic_image,
                        enabled = boardEditingEnabled && viewModel.imageStudio != null) {
                        menu = false; viewModel.imageStudio?.prepare(image); showImageStudio = true
                    }
                }
                CanvasSheetAction("Reset view", com.halo.app.R.drawable.halo_ic_fit_all) {
                    menu = false; viewModel.resetViewport()
                }
                CanvasSheetAction("More tools", com.halo.app.R.drawable.halo_ic_more) {
                    menu = false; toolsExpanded = !toolsExpanded
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        EditorWorkspaceHeader(state.title, menu,
            onToggle = { menu = true }, onBack = { viewModel.leave(onBack) },
            onHalo = { showAi = true }, haloEnabled = boardEditingEnabled)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText("%1\$d cards · %2\$d connections · %3\$d%%",
                    state.nodes.size, state.edges.size, (state.viewportScale * 100).roundToInt()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.nodes.isNotEmpty()) {
                TextButton(
                    onClick = viewModel::fitToContent,
                    enabled = !state.loading && !state.missing,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(uiText("Fit all cards")) }
            }
        }
        if (toolsExpanded) EditorToolsPanel {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(uiText("More tools"), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { toolsExpanded = false }) { Text(uiText("Done")) }
        }
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                HaloFocusAction(uiText("Card actions"), HaloGlyph.ACTION, enabled = selected != null && boardEditingEnabled,
                    onClick = { cardMenu = true }, active = selected != null,
                    style = if (selected != null) com.halo.app.ui.components.HaloActionStyle.PRIMARY else com.halo.app.ui.components.HaloActionStyle.SECONDARY)
                DropdownMenu(cardMenu, { cardMenu = false }) {
            if (selected != null) {
                if (selected.type == CanvasNodeType.SKETCH) {
                    TextButton(onClick = { sketchTarget = selected.id; sketchOriginal = selected.text; sketchOpen = true }) { Text(uiText("Edit sketch")) }
                    TextButton(onClick = {
                        if (!recognizing) {
                            recognizing = true
                            val snapshot = selected.text
                            val id = selected.id
                            scope.launch {
                                try {
                                    val found = CanvasSketchOcr.recognize(snapshot)
                                    viewModel.recognizedDraft(id, "Handwriting OCR", found, snapshot)
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (_: Exception) {
                                    viewModel.recognizedDraft(id, "Handwriting OCR", "")
                                } finally { recognizing = false }
                            }
                        }
                    }, enabled = !recognizing) { Text(uiText("Recognize handwriting")) }
                }
                val selectedFile = selected.refObjectId?.let { state.files[it] }
                if (selectedFile?.mimeType?.startsWith("image/") == true) {
                    TextButton(onClick = { cardMenu = false; imageToolsNodeId = selected.id }) {
                        Text(uiText("Image tools"))
                    }
                    TextButton(onClick = {
                        cardMenu = false
                        viewModel.saveImagePalette(selected.id, selectedFile.version)
                    }, enabled = !state.importing && !state.imageToolBusy) { Text(uiText("Extract color palette")) }
                }
                if (selectedFile?.mimeType == "application/pdf") {
                    TextButton(onClick = { cardMenu = false; pdfPageNodeId = selected.id },
                        enabled = !state.imageToolBusy) { Text(uiText("Extract PDF pages")) }
                }
                if (selectedFile?.mimeType == "application/pdf" || selectedFile?.mimeType?.startsWith("image/") == true) {
                    TextButton(onClick = { annotateNodeId = selected.id; annotationOriginal = selected.text; annotationVersion = selectedFile.version }) { Text(uiText("Annotate")) }
                    TextButton(onClick = {
                        val id = selected.id
                        val original = selected.text
                        recognizing = true
                        scope.launch {
                            try {
                                val text = CanvasSketchOcr.recognizeSource(context, selectedFile)
                                viewModel.recognizedDraft(id, "OCR page 1", text, original, selectedFile.version)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { viewModel.recognizedDraft(id, "OCR page 1", "") }
                            finally { recognizing = false }
                        }
                    }, enabled = !recognizing) { Text(uiText("Recognize source text (page 1)")) }
                    Text(uiText("Local OCR reads Latin script, including Latvian. Cyrillic and handwriting may be unreadable. Review against the source."), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
                if (selectedFile?.mimeType?.startsWith("audio/") == true) {
                    Text(uiText("Local speech: %1\$s · %2\$s · %3\$s", uiText(memoReadiness.modelName), if (memoReadiness.language == "auto") uiText("Automatic") else memoReadiness.language, uiText(memoReadiness.phase)), Modifier.padding(12.dp))
                    Text(uiText("Use an M4A or WAV recording up to two minutes and 12 MB. If recognition is unavailable, write a transcript."), Modifier.padding(12.dp))
                    TextButton(onClick = { viewModel.transcribeFile(selected.id) },
                        enabled = !state.transcribing && memoReadiness.phase in setOf("Ready", "Installed")) { Text(uiText("Transcribe locally")) }
                    TextButton(onClick = { viewModel.writeTranscript(selected.id) }) { Text(uiText("Write transcript")) }
                }
                TextButton(onClick = { tagDialog = true }) { Text(uiText("Tag selected")) }
                TextButton(onClick = { viewModel.duplicateSelected() }) { Text(uiText("Duplicate")) }
                TextButton(onClick = { connect = true }, enabled = state.nodes.size > 1) { Text(uiText("Connect")) }
                TextButton(onClick = { viewModel.manualSelectionAction("Arrange") }) { Text(uiText("Arrange selected")) }
                TextButton(onClick = { viewModel.manualSelectionAction("Bring forward") }) { Text(uiText("Bring forward")) }
                TextButton(onClick = { viewModel.manualSelectionAction("Ungroup") }, enabled = selected.type == CanvasNodeType.GROUP) { Text(uiText("Ungroup")) }
                TextButton(onClick = { confirmDelete = true }) { Text(uiText("Remove selected cards")) }
            }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = viewModel::toggleSelectionMode, enabled = boardEditingEnabled) { Text(uiText(if (state.selectionMode) "Done selecting" else "Select cards")) }
            if (state.selectionMode) TextButton(onClick = viewModel::selectAll, enabled = boardEditingEnabled) { Text(uiText("Select all")) }
            if (state.selectedNodeIds.size in 1..6) {
                val selection = state.nodes.filter { it.id in state.selectedNodeIds }
                val mayContainImages = selection.any { node ->
                    node.refObjectId?.let { ref ->
                        state.files[ref]?.mimeType?.startsWith("image/") == true ||
                            state.images[ref]?.type == "IMAGE" ||
                            state.references.any { it.id.value == ref &&
                                it.type == com.halo.app.data.notebook.WorkspaceObjectType.NOTE }
                    } == true
                }
                if (mayContainImages) {
                    TextButton(onClick = { stitchPickerIds = state.selectedNodeIds.toSet() },
                        enabled = !state.imageToolBusy && !state.importing && !state.loading && !state.missing) {
                        Text(uiText("Combine images"))
                    }
                }
            }
            if (state.selectedNodeIds.isNotEmpty()) TextButton(onClick = { viewModel.organize() }, enabled = boardEditingEnabled) { Text(uiText("Organize selected")) }
            TextButton(onClick = { viewModel.undo() }, enabled = boardEditingEnabled && state.undoAvailable) { Text(uiText("Undo")) }
            state.drawingDraft?.let { draft -> TextButton(onClick = {
                if (draft.kind == "sketch") { sketchTarget = draft.nodeId; sketchOriginal = draft.original; sketchOpen = true }
                else { annotateNodeId = draft.nodeId; annotationOriginal = draft.original; annotationVersion = draft.fileVersion }
            }) { Text(uiText("Resume drawing")) } }
        }
        Text(uiText(if (state.selectedNodeIds.isEmpty()) "Tap a card to select. Hold and drag to move. Pinch to zoom; drag the background to pan." else "Selected cards: %1\$d", state.selectedNodeIds.size),
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        if (searchOpen) {
            val hits = state.searchHits
            val matches = state.nodes.filter { node -> hits.any { it.nodeId == node.id } }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(boardSearch, { boardSearch = it.take(160) },
                        label = { Text(uiText("Search this canvas")) },
                        singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { searchOpen = false; boardSearch = "" }) { Text(uiText("Close")) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Notes", "Files").forEach { type -> FilterChip(searchType == type, { searchType = type }, label = { Text(uiText(type)) }) }
                }
                if (boardSearch.isNotBlank()) {
                    Text(uiText("%1\$d matching cards", matches.size),
                        style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        matches.forEach { node ->
                            val label = node.refObjectId?.let { id -> state.references.firstOrNull { it.id.value == id }?.title }
                                ?: if (node.type == CanvasNodeType.SKETCH) "Sketch" else CanvasFileMarkupCodec.displayTitle(node.text)
                            TextButton(onClick = { viewModel.focusNode(node.id) }) {
                                Text(label.take(28).ifBlank { uiText("Untitled card") })
                            }
                        }
                    }
                }
            }
        }
        }
        if (state.transcribing) Row(Modifier.padding(horizontal = 16.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp))
            Text(uiText("Transcribing locally…"), Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = viewModel::cancelTranscription) { Text(uiText("Cancel")) }
        }
        if (recognizing) Row(Modifier.padding(horizontal = 16.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp))
            Text(uiText("Recognizing handwriting…"), Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall)
        }
        if (state.imageToolBusy) Row(Modifier.padding(horizontal = 16.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp))
            Text(uiText("Processing image locally…"), Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall)
        }
        if (state.importing) Row(Modifier.padding(horizontal = 16.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp))
            Text(uiText("Importing into Workspace…"), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (state.aiBusy) Row(Modifier.padding(horizontal = 16.dp)) { Text(uiText("halo is thinking..."), Modifier.weight(1f)); TextButton(onClick = viewModel::discardAi) { Text(uiText("Cancel")) } }
        state.notice?.let { notice ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(uiText(notice), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::clearNotice) { Text(uiText("Dismiss")) }
            }
        }
        if (state.exporting) HaloProgressPresentation(uiText("Saving to Notebook…"), Modifier.padding(horizontal = 16.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged {
            boardWidth = it.width; boardHeight = it.height
            viewModel.measureViewport(it.width.toFloat(), it.height.toFloat(), density.density)
        }.pointerInput(viewModel) {
            detectTransformGestures { center, pan, zoom, _ -> viewModel.transform(pan.x, pan.y, zoom, center.x, center.y) }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                // Round 2: dots anchored in board coordinates, not graph-paper lines.
                val spacing = (32.dp.toPx() * state.viewportScale).coerceAtLeast(8.dp.toPx())
                val dotRadius = 1.dp.toPx()
                var x = ((state.viewportX % spacing) + spacing) % spacing
                while (x < size.width) {
                    var y = ((state.viewportY % spacing) + spacing) % spacing
                    while (y < size.height) {
                        drawCircle(gridColor.copy(alpha = 0.42f), dotRadius, Offset(x, y))
                        y += spacing
                    }
                    x += spacing
                }
                val nodes = state.nodes.associateBy { it.id }
                for (edge in state.edges) {
                    val from = nodes[edge.fromNodeId] ?: continue
                    val to = nodes[edge.toNodeId] ?: continue
                    fun anchor(node: CanvasNode) = Offset((node.x + node.width / 2).dp.toPx() * state.viewportScale + state.viewportX,
                        (node.y + node.height / 2).dp.toPx() * state.viewportScale + state.viewportY)
                    val start = anchor(from); val end = anchor(to)
                    val bend = (end.x - start.x) * .5f
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(start.x, start.y); cubicTo(start.x + bend, start.y, end.x - bend, end.y, end.x, end.y)
                    }
                    drawPath(path, edgeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    drawCircle(edgeColor, 3.5.dp.toPx(), start); drawCircle(edgeColor, 3.5.dp.toPx(), end)
                }
            }
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.missing -> HaloEmptyState(uiText("Canvas unavailable"), uiText("Return to Notebook to choose another canvas. Your other items are unchanged."), HaloGlyph.NOTE,
                    modifier = Modifier.align(Alignment.Center))
                state.nodes.isEmpty() -> CanvasEmptyStarter(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    enabled = boardEditingEnabled,
                    onText = { viewModel.addTextNode() },
                    onImport = { insertMenu = true },
                    onHalo = { showAi = true }
                )
            }
            state.nodes.sortedWith(compareBy<CanvasNode> { it.type != CanvasNodeType.GROUP }
                .thenBy { it.zIndex }).forEach { node ->
                key(node.id) {
                    val x = with(density) { (node.x.dp * state.viewportScale).toPx() } + state.viewportX
                    val y = with(density) { (node.y.dp * state.viewportScale).toPx() } + state.viewportY
                    val width = node.width * state.viewportScale
                    val height = node.height * state.viewportScale
                    val visible = x + with(density) { width.dp.toPx() } >= 0 && y + with(density) { height.dp.toPx() } >= 0 && x <= boardWidth && y <= boardHeight
                    if (visible && !state.loading && !state.missing) CanvasCardFrame(
                        width = node.width, height = node.height, scale = state.viewportScale,
                        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.pointerInput(node.id, state.viewportScale) {
                                detectDragGesturesAfterLongPress(onDragStart = { viewModel.beginDrag(node.id) }) { change, drag ->
                                    change.consume()
                                    viewModel.moveNode(node.id, with(density) { drag.x.toDp().value }, with(density) { drag.y.toDp().value })
                                }
                            },
                    ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        onClick = { viewModel.selectNode(node.id) },
                        shape = com.halo.app.ui.design.HaloShapes.card,
                        color = when (node.type) {
                            CanvasNodeType.GROUP -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .30f)
                            CanvasNodeType.TAG -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .50f)
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            if (node.id in state.selectedNodeIds || node.id == state.selectedNodeId) 2.dp else 1.dp,
                            if (node.id in state.selectedNodeIds || node.id == state.selectedNodeId)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        if (node.type == CanvasNodeType.GROUP || node.type == CanvasNodeType.TAG) Column(
                            Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(uiText(if (node.type == CanvasNodeType.GROUP) "Group title" else "Tag card"),
                                style = MaterialTheme.typography.labelSmall)
                            BasicTextField(node.text, { viewModel.updateNodeText(node.id, it.take(if (node.type == CanvasNodeType.GROUP) 80 else 50)) },
                                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary))
                            if (node.type == CanvasNodeType.GROUP) Text(
                                uiText("Drag this zone to move enclosed cards."),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        else if (node.type == CanvasNodeType.SKETCH) Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(uiText("Sketch"), style = MaterialTheme.typography.labelSmall)
                            CanvasSketchPreview(node.text, Modifier.fillMaxWidth().weight(1f))
                            TextButton(onClick = { viewModel.selectNode(node.id); sketchTarget = node.id; sketchOriginal = node.text; sketchOpen = true }) {
                                Text(uiText("Edit sketch"))
                            }
                        }
                        else if (node.refObjectId == null) BasicTextField(
                            value = node.text,
                            onValueChange = { viewModel.updateNodeText(node.id, it) },
                            modifier = Modifier.fillMaxSize().padding(14.dp)
                                .onFocusChanged { if (it.isFocused && !state.selectionMode) viewModel.selectNode(node.id) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(painterResource(com.halo.app.R.drawable.halo_ic_card),
                                            contentDescription = null, modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary)
                                        Text(uiText("Text"), style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Box {
                                        if (node.text.isBlank()) Text(uiText("Write an idea..."),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium)
                                        inner()
                                    }
                                }
                            },
                        )
                        else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val reference = state.references.firstOrNull { it.id.value == node.refObjectId }
                            val localFile = state.files[node.refObjectId]
                            state.images[node.refObjectId]?.let { image ->
                                com.halo.app.ui.workspace.WorkspaceAttachmentPreview(image,
                                    Modifier.fillMaxWidth().height(110.dp))
                            }
                            if (localFile?.mimeType?.startsWith("image/") == true ||
                                localFile?.mimeType == "application/pdf") {
                                CanvasAnnotatedFilePreview(localFile, node.text,
                                    Modifier.fillMaxWidth().height(110.dp))
                            }
                            Text(uiText(when {
                                state.images[node.refObjectId] != null || localFile?.mimeType?.startsWith("image/") == true -> "Image"
                                localFile?.mimeType?.startsWith("audio/") == true -> "Audio file"
                                localFile?.mimeType == "application/pdf" -> "PDF document"
                                else -> reference?.type?.name?.lowercase() ?: "Source unavailable"
                            }), style = MaterialTheme.typography.labelSmall)
                            Text(reference?.title ?: CanvasFileMarkupCodec.displayTitle(node.text), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            if (localFile?.mimeType?.startsWith("audio/") == true) {
                                CanvasLocalAudioPlayer(localFile)
                            }
                            TextButton(onClick = { viewModel.openReference(node.id) }, enabled = reference != null) { Text(uiText("Open source")) }
                        }
                    }
                    }
                }
            }
        // An unapproved draft is a board overlay, never a persisted CanvasNode.
        // It stays visible when the editor sheet is dismissed and disappears
        // immediately after an explicit Keep or Discard.
        if (state.aiDraft != null && !draftReviewOpen) {
            CanvasInlineDraftCard(
                draft = state.aiDraft!!,
                busy = !boardEditingEnabled,
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
                onReview = { draftReviewOpen = true },
                onKeep = viewModel::applyAi,
                onDiscard = viewModel::discardAiDraft,
            )
        }
        }
        if (selected != null && state.edges.any { it.fromNodeId == selected.id || it.toNodeId == selected.id }) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                state.edges.filter { it.fromNodeId == selected.id || it.toNodeId == selected.id }.forEach { edge ->
                    val other = state.nodes.firstOrNull { it.id == if (edge.fromNodeId == selected.id) edge.toNodeId else edge.fromNodeId }
                    TextButton(onClick = { viewModel.removeConnection(edge.id) }, enabled = boardEditingEnabled) {
                        Text(uiText("Remove") + " " + (edge.label ?: other?.readableText()?.take(24) ?: uiText("connection")))
                    }
                }
            }
        }
        // Editing commands are not actionable while a file, model draft or export
        // is mutating the board. Selection/viewport are preserved on suspension.
        CanvasBrandDock(enabled = boardEditingEnabled, haloThinking = state.aiBusy,
            selecting = state.selectionMode, onSelect = viewModel::toggleSelectionMode,
            onText = { viewModel.addTextNode() },
            onSketch = { sketchTarget = null; sketchOriginal = ""; sketchOpen = true },
            onLink = { picker = true }, onGroup = { groupDialog = true },
            onImport = { filePicker.launch(arrayOf("*/*")) }, onHalo = { showAi = true })
    }
}

/**
 * DevKit v1.2 Round 2: an empty board should invite a real first action, not
 * leave users to discover the toolbar before they can start.
 */
@Composable
internal fun CanvasEmptyStarter(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onText: () -> Unit,
    onImport: () -> Unit,
    onHalo: () -> Unit,
) {
    Column(
        modifier = modifier.widthIn(max = 360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(uiText("Make room for your ideas"), style = MaterialTheme.typography.titleMedium)
        Text(
            uiText("Add cards, link your notes and connect what belongs together."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onText, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(uiText("Add text card"))
        }
        OutlinedButton(onClick = onImport, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(com.halo.app.R.string.brand_canvas_import_choice))
        }
        OutlinedButton(onClick = onHalo, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(uiText("Think with halo"))
        }
    }
}

@Composable
internal fun CanvasBrandDock(enabled: Boolean, selecting: Boolean, onSelect: () -> Unit,
    onText: () -> Unit, onSketch: () -> Unit, onLink: () -> Unit, onGroup: () -> Unit,
    onImport: () -> Unit, onHalo: () -> Unit, haloThinking: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
        shape = com.halo.app.ui.design.HaloShapes.hero,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val actions = listOf(
                    Triple(if (selecting) "Done selecting" else "Select", com.halo.app.R.drawable.halo_ic_select, onSelect),
                    Triple("Text", com.halo.app.R.drawable.halo_ic_new_note, onText),
                    Triple("Sketch", com.halo.app.R.drawable.halo_ic_sketch, onSketch),
                    Triple("Link", com.halo.app.R.drawable.halo_ic_link, onLink),
                    Triple("Group", com.halo.app.R.drawable.halo_ic_group, onGroup),
                    Triple("Import", com.halo.app.R.drawable.halo_ic_import, onImport))
                actions.forEachIndexed { index, (label, icon, action) ->
                    val active = selecting && index == 0
                    Surface(onClick = action, enabled = enabled,
                        modifier = Modifier.heightIn(min = 56.dp),
                        shape = com.halo.app.ui.design.HaloShapes.pill,
                        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.widthIn(min = 60.dp).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
                            Text(uiText(label), style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            com.halo.app.ui.presence.HaloSignalFace(com.halo.app.ui.presence.HaloSignalPresentation(
                    state = if (haloThinking) com.halo.app.ui.presence.HaloSignalState.THINKING else com.halo.app.ui.presence.HaloSignalState.IDLE,
                    label = if (haloThinking) "Thinking" else "Ready"),
                size = 58.dp, contentDescription = uiText("Think with halo"), onClick = if (enabled) onHalo else null)
        }
    }
}

/** Round 2 page 39: all canvas-level actions live in one scannable bottom sheet. */
@Composable
internal fun CanvasSheetAction(
    label: String,
    icon: Int,
    enabled: Boolean = true,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = if (accent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (accent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(painterResource(icon), contentDescription = null,
                modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text(uiText(label), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// Round 2: Keep is the ONLY persistence action. A drag-dismiss merely collapses
// the sheet to a reopenable board banner; Discard is never inferred from dismissal.
@Composable
internal fun CanvasAiDraftReview(
    draft: CanvasAiDraft,
    busy: Boolean,
    onEdit: (String) -> Unit,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.navigationBarsPadding().padding(bottom = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(com.halo.app.R.drawable.halo_ic_ai_draft),
                contentDescription = null, modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(uiText("Review AI draft"), style = MaterialTheme.typography.titleLarge)
                Text(uiText(if (draft.nodeId == null) "Add as card" else "Apply to card"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().haloDraftBorder(
                visible = true, color = MaterialTheme.colorScheme.primary),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = com.halo.app.ui.design.HaloShapes.card,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiText("Review or edit before applying."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = draft.text,
                    onValueChange = onEdit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 152.dp, max = 300.dp),
                    enabled = !busy,
                    minLines = 4,
                )
            }
        }
        OutlinedButton(
            onClick = onRegenerate, enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(uiText("Regenerate")) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDiscard, enabled = !busy,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Text(uiText("Discard"))
            }
            Button(onClick = onKeep, enabled = !busy && draft.text.isNotBlank(),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Text(uiText("Keep"))
            }
        }
    }
}

// The preview belongs to the view only; calling Keep is the sole path to a
// persisted node. A pending rewrite also remains separate from its source card.
@Composable
internal fun CanvasInlineDraftCard(
    draft: CanvasAiDraft,
    busy: Boolean,
    onReview: () -> Unit,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(.86f).widthIn(max = 300.dp)
            .haloDraftBorder(visible = true, color = MaterialTheme.colorScheme.primary),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(com.halo.app.R.drawable.halo_ic_ai_draft),
                    contentDescription = null, modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text(uiText("Review AI draft"), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Text(draft.text, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            TextButton(onClick = onReview, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(uiText(if (draft.nodeId == null) "Add as card" else "Apply to card"))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDiscard, enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(uiText("Discard")) }
                Button(onClick = onKeep, enabled = !busy && draft.text.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(uiText("Keep")) }
            }
        }
    }
}
