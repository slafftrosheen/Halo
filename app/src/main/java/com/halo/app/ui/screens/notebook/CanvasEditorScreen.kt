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
import androidx.compose.foundation.gestures.detectDragGestures
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

@Composable
fun CanvasEditorScreen(viewModel: CanvasEditorViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    var query by rememberSaveable { mutableStateOf("") }
    var connect by remember { mutableStateOf(false) }
    var connectionLabel by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var boardWidth by remember { mutableStateOf(0) }
    var boardHeight by remember { mutableStateOf(0) }
    val selected = state.nodes.firstOrNull { it.id == state.selectedNodeId }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val voiceRecorder = remember(context) { CanvasVoiceMemoRecorder(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var recordingError by remember { mutableStateOf<String?>(null) }
    var sketchOpen by remember { mutableStateOf(false) }
    var sketchTarget by remember { mutableStateOf<String?>(null) }
    var annotateNodeId by remember { mutableStateOf<String?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var boardSearch by rememberSaveable { mutableStateOf("") }
    var recognizing by remember { mutableStateOf(false) }
    var groupDialog by remember { mutableStateOf(false) }
    var groupTitle by rememberSaveable { mutableStateOf("") }
    var tagDialog by remember { mutableStateOf(false) }
    var tagTitle by rememberSaveable { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importSource(uri)
    }
    val beginVoiceMemo = {
        recordingError = null
        runCatching { voiceRecorder.start() }
            .onSuccess { recording = true }
            .onFailure { recordingError = "Could not start recording. The microphone may be busy." }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginVoiceMemo()
        else recordingError = "Microphone permission denied."
    }
    val finishVoiceMemo: () -> Unit = {
        val file = voiceRecorder.stop()
        recording = false
        if (file == null) recordingError = "Recording was too short. Try again."
        else {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.captures", file)
            val title = "Voice memo " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()) + ".m4a"
            viewModel.importSource(uri, displayName = title, mimeType = "audio/mp4", stagingFile = file)
        }
    }
    val latestFinishVoiceMemo by rememberUpdatedState(finishVoiceMemo)
    DisposableEffect(lifecycleOwner, voiceRecorder) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && voiceRecorder.isRecording) latestFinishVoiceMemo()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            voiceRecorder.cancel()
        }
    }
    if (sketchOpen) {
        val target = state.nodes.firstOrNull { it.id == sketchTarget && it.type == CanvasNodeType.SKETCH }
        CanvasSketchPadDialog(
            initial = target?.text.orEmpty(),
            onSave = { encoded ->
                viewModel.addSketch(encoded, target?.id)
                sketchOpen = false
                sketchTarget = null
            },
            onDismiss = { sketchOpen = false; sketchTarget = null },
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
            CanvasSourceInkDialog(file, target.text,
                onSave = { updates -> viewModel.saveFileMarkup(nodeId, updates); annotateNodeId = null },
                onDismiss = { annotateNodeId = null })
        }
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
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(uiText("Delete this card?")) },
        text = { Text(uiText("Its connections will also be removed. Linked source items stay in Workspace.")) },
        confirmButton = { TextButton(onClick = { viewModel.deleteSelectedNode(); confirmDelete = false }) { Text(uiText("Delete")) } },
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
                            Text(node.text.ifBlank { "Untitled card" }.take(100))
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { connect = false }) { Text(uiText("Cancel")) } })

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { HaloScreenHeader(state.title,
                "${state.nodes.size} cards \u00b7 ${state.edges.size} connections \u00b7 ${(state.viewportScale * 100).roundToInt()}%", navigation = { viewModel.leave(onBack) }) }
            Box {
                TextButton(onClick = { menu = true }) { Text(uiText("More")) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(uiText("Think with Halo")) }, enabled = !state.aiBusy && !state.missing, onClick = { menu = false; showAi = true })
                    DropdownMenuItem(text = { Text(uiText("Create an image")) }, enabled = viewModel.imageStudio != null && !state.missing,
                        onClick = { menu = false; viewModel.imageStudio?.prepare(); showImageStudio = true })
                    state.images[selected?.refObjectId]?.let { image ->
                        DropdownMenuItem(text = { Text(uiText("Edit image with AI")) }, onClick = { menu = false; viewModel.imageStudio?.prepare(image); showImageStudio = true })
                    }
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
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { viewModel.addTextNode() }, enabled = !state.loading && !state.missing && !state.addingNode) { Text(uiText("Add text")) }
            if (recording) {
                Button(onClick = finishVoiceMemo) { Text(uiText("Stop & save")) }
            } else {
                OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginVoiceMemo()
                    else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }, enabled = !state.loading && !state.missing && !state.importing) { Text(uiText("Record memo")) }
            }
            Box {
                OutlinedButton(onClick = { insertMenu = true }, enabled = !state.loading && !state.missing && !state.importing) { Text(uiText("Insert")) }
                DropdownMenu(expanded = insertMenu, onDismissRequest = { insertMenu = false }) {
                    DropdownMenuItem(text = { Text(uiText("Import file")) }, onClick = {
                        insertMenu = false
                        filePicker.launch(arrayOf("*/*"))
                    })
                    DropdownMenuItem(text = { Text(uiText("Sketch")) }, onClick = {
                        insertMenu = false
                        sketchTarget = null
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
            if (selected != null) {
                if (selected.type == CanvasNodeType.SKETCH) {
                    TextButton(onClick = { sketchTarget = selected.id; sketchOpen = true }) { Text(uiText("Edit sketch")) }
                    TextButton(onClick = {
                        if (!recognizing) {
                            recognizing = true
                            val snapshot = selected.text
                            val id = selected.id
                            scope.launch {
                                try {
                                    val found = CanvasSketchOcr.recognize(snapshot)
                                    viewModel.recognizedDraft(id, "Handwriting OCR", found)
                                } catch (_: Exception) {
                                    viewModel.recognizedDraft(id, "Handwriting OCR", "")
                                } finally { recognizing = false }
                            }
                        }
                    }, enabled = !recognizing) { Text(uiText("Recognize handwriting")) }
                }
                val selectedFile = selected.refObjectId?.let { state.files[it] }
                if (selectedFile?.mimeType == "application/pdf" || selectedFile?.mimeType?.startsWith("image/") == true) {
                    TextButton(onClick = { annotateNodeId = selected.id }) { Text(uiText("Annotate")) }
                }
                if (selectedFile?.mimeType?.startsWith("audio/") == true) {
                    TextButton(onClick = { viewModel.transcribeFile(selected.id) },
                        enabled = !state.transcribing) { Text(uiText("Transcribe locally")) }
                }
                TextButton(onClick = { tagDialog = true }) { Text(uiText("Tag selected")) }
                TextButton(onClick = { viewModel.duplicateSelected() }) { Text(uiText("Duplicate")) }
                TextButton(onClick = { connect = true }, enabled = state.nodes.size > 1) { Text(uiText("Connect")) }
                TextButton(onClick = { confirmDelete = true }) { Text(uiText("Delete")) }
            }
        }
        if (searchOpen) {
            val matches = remember(state.nodes, state.references, boardSearch) {
                val term = boardSearch.trim()
                if (term.isBlank()) emptyList() else state.nodes.filter { node ->
                    val label = node.refObjectId?.let { id -> state.references.firstOrNull { it.id.value == id }?.title }
                        ?: if (node.type == CanvasNodeType.SKETCH) "" else CanvasFileMarkupCodec.decode(node.text).title
                    val tagLabels = state.edges.filter { it.fromNodeId == node.id && it.label == "tag" }
                        .mapNotNull { edge -> state.nodes.firstOrNull { it.id == edge.toNodeId }?.text }
                    label.contains(term, ignoreCase = true) || tagLabels.any { it.contains(term, ignoreCase = true) }
                }.take(50)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(boardSearch, { boardSearch = it.take(160) },
                        label = { Text(uiText("Search this canvas")) },
                        singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { searchOpen = false; boardSearch = "" }) { Text(uiText("Close")) }
                }
                if (boardSearch.isNotBlank()) {
                    Text("${matches.size} " + uiText("matching cards"),
                        style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        matches.forEach { node ->
                            val label = node.refObjectId?.let { id -> state.references.firstOrNull { it.id.value == id }?.title }
                                ?: if (node.type == CanvasNodeType.SKETCH) "Sketch" else CanvasFileMarkupCodec.decode(node.text).title
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
        }
        if (recognizing) Row(Modifier.padding(horizontal = 16.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp))
            Text(uiText("Recognizing handwriting…"), Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall)
        }
        if (state.importing) Row(Modifier.padding(horizontal = 16.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp))
            Text(uiText("Importing into Workspace…"), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (recording) Text(uiText("Recording…"), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
        recordingError?.let { error ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(uiText(error), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { recordingError = null }) { Text(uiText("Dismiss")) }
            }
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
                                detectDragGestures(onDragStart = { viewModel.selectNode(node.id) }) { change, drag ->
                                    change.consume()
                                    viewModel.moveNode(node.id, with(density) { drag.x.toDp().value }, with(density) { drag.y.toDp().value })
                                }
                            },
                        onClick = { viewModel.selectNode(node.id) }, shape = MaterialTheme.shapes.medium,
                        color = if (node.type == CanvasNodeType.GROUP) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                            else if (node.type == CanvasNodeType.TAG) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(if (selected?.id == node.id) 2.dp else 1.dp,
                            if (selected?.id == node.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
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
                            TextButton(onClick = { viewModel.selectNode(node.id); sketchTarget = node.id; sketchOpen = true }) {
                                Text(uiText("Edit sketch"))
                            }
                        }
                        else if (node.refObjectId == null) BasicTextField(node.text, { viewModel.updateNodeText(node.id, it) },
                            modifier = Modifier.padding(16.dp).onFocusChanged { if (it.isFocused) viewModel.selectNode(node.id) }, textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
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
                            Text(reference?.title ?: CanvasFileMarkupCodec.decode(node.text).title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
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
                    TextButton(onClick = { viewModel.removeConnection(edge.id) }) { Text("Remove ${edge.label ?: other?.text?.take(24) ?: "connection"}") }
                }
            }
        }
    }
}
