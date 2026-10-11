package com.halo.app.ui.screens.notebook

import com.halo.app.language.uiText
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
import com.halo.app.data.notebook.CanvasNode
import kotlin.math.roundToInt

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CanvasEditorScreen(viewModel: CanvasEditorViewModel, focusNodeId: String? = null, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val memoReadiness by viewModel.memoReadiness.collectAsStateWithLifecycle()
    BackHandler { viewModel.leave(onBack) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val edgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    var showImageStudio by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    var aiAction by rememberSaveable { mutableStateOf("Review") }
    var aiInstructions by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var picker by remember { mutableStateOf(false) }
    var insertMenu by remember { mutableStateOf(false) }
    var cardMenu by remember { mutableStateOf(false) }
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
    if (showAi) AlertDialog(onDismissRequest = { showAi = false }, title = { Text(uiText("Think with Halo")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row { listOf("Review", "Expand", "Rewrite").forEach { action ->
                TextButton(onClick = { aiAction = action }) { Text(if (action == aiAction) "[$action]" else action) }
            } }
            Text(if (aiAction == "Rewrite") "Rewrite the selected text card." else "Use the text and link labels on this board.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(aiInstructions, { aiInstructions = it.take(1000) }, label = { Text(uiText("Your direction (optional)")) }, minLines = 2)
        } }, confirmButton = { Button(onClick = { viewModel.requestAi(aiAction, aiInstructions); showAi = false }, enabled = !state.aiBusy) { Text(uiText("Create draft")) } },
        dismissButton = { TextButton(onClick = { showAi = false }) { Text(uiText("Cancel")) } })
    state.aiDraft?.let { draft ->
        AlertDialog(onDismissRequest = viewModel::discardAi, title = { Text("${draft.action} draft") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiText("Review or edit before applying."), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(draft.text, viewModel::editAi, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp), enabled = !state.aiBusy)
                TextButton(onClick = viewModel::regenerateAi, enabled = !state.aiBusy) { Text(uiText("Regenerate")) }
            } }, confirmButton = { Button(onClick = { viewModel.applyAi() }, enabled = !state.aiBusy && draft.text.isNotBlank()) { Text(if (draft.nodeId == null) "Add as card" else "Apply to card") } },
            dismissButton = { TextButton(onClick = viewModel::discardAi) { Text(uiText("Discard")) } })
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
                            Column(Modifier.fillMaxWidth()) { Text(item.title.ifBlank { "Untitled" }); Text(item.type.name.lowercase(), style = MaterialTheme.typography.bodySmall) }
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

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { HaloScreenHeader(state.title,
                uiText("%1\$d cards · %2\$d connections · %3\$d%%", state.nodes.size, state.edges.size, (state.viewportScale * 100).roundToInt()), navigation = { viewModel.leave(onBack) }) }
            Box {
                TextButton(onClick = { menu = true }) { Text(uiText("More")) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(uiText("Think with Halo")) }, enabled = !state.aiBusy && !state.missing, onClick = { menu = false; showAi = true })
                    DropdownMenuItem(text = { Text(uiText("Create an image")) }, enabled = viewModel.imageStudio != null && !state.missing,
                        onClick = { menu = false; viewModel.imageStudio?.prepare(); showImageStudio = true })
                    state.images[selected?.refObjectId]?.let { image ->
                        DropdownMenuItem(text = { Text(uiText("Edit image with AI")) }, onClick = { menu = false; viewModel.imageStudio?.prepare(image); showImageStudio = true })
                    }
                    DropdownMenuItem(text = { Text(uiText("Organize whole board")) }, enabled = state.nodes.isNotEmpty(), onClick = { menu = false; viewModel.organize(wholeBoard = true) })
                    DropdownMenuItem(text = { Text(uiText("Ask Halo to organize")) }, enabled = state.nodes.isNotEmpty() && !state.aiBusy, onClick = { menu = false; viewModel.organize(wholeBoard = state.selectedNodeIds.isEmpty(), ai = true) })
                    DropdownMenuItem(text = { Text(uiText("Find on canvas")) }, onClick = {
                        menu = false; searchOpen = true
                    })
                    DropdownMenuItem(text = { Text(uiText("Rename")) }, onClick = { menu = false; title = state.title; rename = true })
                    DropdownMenuItem(text = { Text(uiText("Fit all cards")) }, onClick = { menu = false; viewModel.fitToContent() })
                    DropdownMenuItem(text = { Text(uiText("Reset view")) }, onClick = { menu = false; viewModel.resetViewport() })
                    DropdownMenuItem(text = { Text(uiText("Export to note")) }, enabled = state.nodes.isNotEmpty() && !state.exporting,
                        onClick = { menu = false; viewModel.exportToNote() })
                    DropdownMenuItem(text = { Text(uiText("Study this canvas")) }, enabled = state.nodes.isNotEmpty() && !state.exporting,
                        onClick = { menu = false; viewModel.exportToNote(startStudy = true) })
                }
            }
        }
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                OutlinedButton(onClick = { insertMenu = true }, enabled = !state.loading && !state.missing && !state.importing) { Text(uiText("Quick add")) }
                DropdownMenu(expanded = insertMenu, onDismissRequest = { insertMenu = false }) {
                    DropdownMenuItem(text = { Text(uiText("Add text")) }, onClick = { insertMenu = false; viewModel.addTextNode() })
                    DropdownMenuItem(text = { Text(uiText("Save to Inbox")) }, onClick = { insertMenu = false; inboxCapture = true })
                    DropdownMenuItem(text = { Text(uiText("Import file")) }, onClick = {
                        insertMenu = false
                        filePicker.launch(arrayOf("*/*"))
                    })
                    DropdownMenuItem(text = { Text(uiText("Sketch")) }, onClick = {
                        insertMenu = false
                        sketchTarget = null
                        sketchOriginal = ""
                        sketchOpen = true
                    })
                    DropdownMenuItem(text = { Text(uiText("Link item")) }, onClick = {
                        insertMenu = false
                        picker = true
                    })
                    DropdownMenuItem(text = { Text(uiText("Add group")) }, onClick = {
                        insertMenu = false; groupDialog = true
                    })
                }
            }
            Box {
                OutlinedButton(onClick = { cardMenu = true }, enabled = selected != null) { Text(uiText("Card actions")) }
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
                        enabled = !state.imageToolBusy) { Text(uiText("Extract PDF page")) }
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
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = viewModel::toggleSelectionMode) { Text(uiText(if (state.selectionMode) "Done selecting" else "Select cards")) }
            if (state.selectionMode) TextButton(onClick = viewModel::selectAll) { Text(uiText("Select all")) }
            if (state.selectedNodeIds.size in 2..6) {
                val selectedNodes = state.nodes.filter { it.id in state.selectedNodeIds }
                    .sortedWith(compareBy<com.halo.app.data.notebook.CanvasNode> { it.y }.thenBy { it.x })
                val images = selectedNodes.mapNotNull { node ->
                    val ref = node.refObjectId ?: return@mapNotNull null
                    val input: com.halo.app.data.workspace.CanvasStitchSource =
                        state.files[ref]?.takeIf { it.mimeType.startsWith("image/") }?.let {
                            com.halo.app.data.workspace.CanvasStitchSource.WorkspaceImage(it)
                        } ?: state.images[ref]?.takeIf {
                            it.type == "IMAGE" && it.mimeType.startsWith("image/")
                        }?.let {
                            com.halo.app.data.workspace.CanvasStitchSource.NoteImage(it)
                        } ?: return@mapNotNull null
                    node.id to input
                }
                val valid = images.size == state.selectedNodeIds.size &&
                    images.map { it.second.sourceKey }.distinct().size == images.size &&
                    images.map { it.second.ownerObjectId }.distinct().size == images.size
                TextButton(onClick = { stitchSources = images }, enabled = valid &&
                    !state.imageToolBusy && !state.importing && !state.loading && !state.missing) {
                    Text(uiText("Combine images"))
                }
            }
            if (state.selectedNodeIds.isNotEmpty()) TextButton(onClick = { viewModel.organize() }) { Text(uiText("Organize selected")) }
            if (state.undoAvailable) TextButton(onClick = { viewModel.undo() }) { Text(uiText("Undo")) }
            state.drawingDraft?.let { draft -> TextButton(onClick = {
                if (draft.kind == "sketch") { sketchTarget = draft.nodeId; sketchOriginal = draft.original; sketchOpen = true }
                else { annotateNodeId = draft.nodeId; annotationOriginal = draft.original; annotationVersion = draft.fileVersion }
            }) { Text(uiText("Resume drawing")) } }
        }
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
        if (state.aiBusy) Row(Modifier.padding(horizontal = 16.dp)) { Text(uiText("Halo is thinking..."), Modifier.weight(1f)); TextButton(onClick = viewModel::discardAi) { Text(uiText("Cancel")) } }
        state.notice?.let { notice ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(uiText(notice), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::clearNotice) { Text(uiText("Dismiss")) }
            }
        }
        if (state.exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged {
            boardWidth = it.width; boardHeight = it.height
            viewModel.measureViewport(it.width.toFloat(), it.height.toFloat(), density.density)
        }.pointerInput(viewModel) {
            detectTransformGestures { center, pan, zoom, _ -> viewModel.transform(pan.x, pan.y, zoom, center.x, center.y) }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val spacing = 32.dp.toPx() * state.viewportScale
                var x = state.viewportX % spacing
                while (x < size.width) { drawLine(gridColor.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), 1f); x += spacing }
                var y = state.viewportY % spacing
                while (y < size.height) { drawLine(gridColor.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 1f); y += spacing }
                val nodes = state.nodes.associateBy { it.id }
                for (edge in state.edges) {
                    val from = nodes[edge.fromNodeId] ?: continue
                    val to = nodes[edge.toNodeId] ?: continue
                    fun anchor(node: CanvasNode) = Offset((node.x + node.width / 2).dp.toPx() * state.viewportScale + state.viewportX,
                        (node.y + node.height / 2).dp.toPx() * state.viewportScale + state.viewportY)
                    drawLine(edgeColor, anchor(from), anchor(to), 2.dp.toPx())
                }
            }
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.missing -> Text(uiText("Canvas unavailable"), Modifier.align(Alignment.Center))
                state.nodes.isEmpty() -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiText("Make room for your ideas"), style = MaterialTheme.typography.titleMedium)
                    Text(uiText("Add cards, link your notes and connect what belongs together."), style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.nodes.sortedWith(compareBy<CanvasNode> { it.type != CanvasNodeType.GROUP }
                .thenBy { it.zIndex }).forEach { node ->
                key(node.id) {
                    val x = with(density) { (node.x.dp * state.viewportScale).toPx() } + state.viewportX
                    val y = with(density) { (node.y.dp * state.viewportScale).toPx() } + state.viewportY
                    val width = (node.width * state.viewportScale).coerceAtLeast(100f)
                    val height = (node.height * state.viewportScale).coerceAtLeast(96f)
                    val visible = x + with(density) { width.dp.toPx() } >= 0 && y + with(density) { height.dp.toPx() } >= 0 && x <= boardWidth && y <= boardHeight
                    if (visible && !state.loading && !state.missing) Surface(
                        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.width(width.coerceAtLeast(100f).dp)
                            .height(height.coerceAtLeast(96f).dp).pointerInput(node.id, state.viewportScale) {
                                detectDragGesturesAfterLongPress(onDragStart = { viewModel.beginDrag(node.id) }) { change, drag ->
                                    change.consume()
                                    viewModel.moveNode(node.id, with(density) { drag.x.toDp().value }, with(density) { drag.y.toDp().value })
                                }
                            },
                        onClick = { viewModel.selectNode(node.id) }, shape = MaterialTheme.shapes.medium,
                        color = if (node.type == CanvasNodeType.GROUP) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                            else if (node.type == CanvasNodeType.TAG) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(if (node.id in state.selectedNodeIds) 2.dp else 1.dp,
                            if (node.id in state.selectedNodeIds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
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
                        else if (node.refObjectId == null) BasicTextField(node.text, { viewModel.updateNodeText(node.id, it) },
                            modifier = Modifier.padding(16.dp).onFocusChanged { if (it.isFocused && !state.selectionMode) viewModel.selectNode(node.id) }, textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner -> if (node.text.isBlank()) Text(uiText("Write an idea..."), color = MaterialTheme.colorScheme.onSurfaceVariant); inner() })
                        else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val reference = state.references.firstOrNull { it.id.value == node.refObjectId }
                            val localFile = state.files[node.refObjectId]
                            state.images[node.refObjectId]?.let { image ->
                                com.halo.app.ui.workspace.WorkspaceAttachmentPreview(image,
                                    Modifier.fillMaxWidth().height((110f * state.viewportScale).coerceAtLeast(48f).dp))
                            }
                            if (localFile?.mimeType?.startsWith("image/") == true ||
                                localFile?.mimeType == "application/pdf") {
                                CanvasAnnotatedFilePreview(localFile, node.text,
                                    Modifier.fillMaxWidth().height((110f * state.viewportScale).coerceAtLeast(48f).dp))
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
        if (selected != null && state.edges.any { it.fromNodeId == selected.id || it.toNodeId == selected.id }) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                state.edges.filter { it.fromNodeId == selected.id || it.toNodeId == selected.id }.forEach { edge ->
                    val other = state.nodes.firstOrNull { it.id == if (edge.fromNodeId == selected.id) edge.toNodeId else edge.fromNodeId }
                    TextButton(onClick = { viewModel.removeConnection(edge.id) }) {
                        Text(uiText("Remove") + " " + (edge.label ?: other?.readableText()?.take(24) ?: uiText("connection")))
                    }
                }
            }
        }
    }
}
