package com.halo.app.ui.screens.notebook

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
    var query by rememberSaveable { mutableStateOf("") }
    var connect by remember { mutableStateOf(false) }
    var connectionLabel by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var boardWidth by remember { mutableStateOf(0) }
    var boardHeight by remember { mutableStateOf(0) }
    val selected = state.nodes.firstOrNull { it.id == state.selectedNodeId }

    if (showImageStudio) viewModel.imageStudio?.let { studio ->
        com.halo.app.ui.workspace.WorkspaceImageDialog(studio) { showImageStudio = false }
    }
    if (showAi) AlertDialog(onDismissRequest = { showAi = false }, title = { Text("Think with Halo") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row { listOf("Review", "Expand", "Rewrite").forEach { action ->
                TextButton(onClick = { aiAction = action }) { Text(if (action == aiAction) "[$action]" else action) }
            } }
            Text(if (aiAction == "Rewrite") "Rewrite the selected text card." else "Use the text and link labels on this board.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(aiInstructions, { aiInstructions = it.take(1000) }, label = { Text("Your direction (optional)") }, minLines = 2)
        } }, confirmButton = { Button(onClick = { viewModel.requestAi(aiAction, aiInstructions); showAi = false }, enabled = !state.aiBusy) { Text("Create draft") } },
        dismissButton = { TextButton(onClick = { showAi = false }) { Text("Cancel") } })
    state.aiDraft?.let { draft ->
        AlertDialog(onDismissRequest = viewModel::discardAi, title = { Text("${draft.action} draft") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Review or edit before applying.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(draft.text, viewModel::editAi, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp), enabled = !state.aiBusy)
                TextButton(onClick = viewModel::regenerateAi, enabled = !state.aiBusy) { Text("Regenerate") }
            } }, confirmButton = { Button(onClick = { viewModel.applyAi() }, enabled = !state.aiBusy && draft.text.isNotBlank()) { Text(if (draft.nodeId == null) "Add as card" else "Apply to card") } },
            dismissButton = { TextButton(onClick = viewModel::discardAi) { Text("Discard") } })
    }

    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text("Rename canvas") },
        text = { OutlinedTextField(title, { title = it.take(160) }, singleLine = true, label = { Text("Title") }) },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { viewModel.rename(title); rename = false }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this card?") },
        text = { Text("Its connections will also be removed. Linked source items stay in Workspace.") },
        confirmButton = { TextButton(onClick = { viewModel.deleteSelectedNode(); confirmDelete = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    if (picker) AlertDialog(onDismissRequest = { picker = false }, title = { Text("Link a workspace item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Search items") }, singleLine = true)
                val matches = remember(state.references, query) { state.references.filter { it.title.contains(query, true) } }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    if (matches.isEmpty()) item { Text("No matching items. Create a note or import a file in Workspace first.") }
                    items(matches, key = { it.id.value }) { item ->
                        TextButton(onClick = { viewModel.addReference(item.id.value); picker = false; query = "" }) {
                            Column(Modifier.fillMaxWidth()) { Text(item.title.ifBlank { "Untitled" }); Text(item.type.name.lowercase(), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { picker = false }) { Text("Done") } })
    if (connect && selected != null) AlertDialog(onDismissRequest = { connect = false }, title = { Text("Connect this card") },
        text = {
            Column {
                OutlinedTextField(connectionLabel, { connectionLabel = it.take(100) }, label = { Text("Connection label (optional)") }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(state.nodes.filter { it.id != selected.id }, key = { it.id }) { node ->
                        TextButton(onClick = { viewModel.connectSelected(node.id, connectionLabel); connect = false; connectionLabel = "" }) {
                            Text(node.text.ifBlank { "Untitled card" }.take(100))
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { connect = false }) { Text("Cancel") } })

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { HaloScreenHeader(state.title,
                "${state.nodes.size} cards \u00b7 ${state.edges.size} connections \u00b7 ${(state.viewportScale * 100).roundToInt()}%", navigation = { viewModel.leave(onBack) }) }
            Box {
                TextButton(onClick = { menu = true }) { Text("More") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Think with Halo") }, enabled = !state.aiBusy && !state.missing, onClick = { menu = false; showAi = true })
                    DropdownMenuItem(text = { Text("Create an image") }, enabled = viewModel.imageStudio != null && !state.missing,
                        onClick = { menu = false; viewModel.imageStudio?.prepare(); showImageStudio = true })
                    state.images[selected?.refObjectId]?.let { image ->
                        DropdownMenuItem(text = { Text("Edit image with AI") }, onClick = { menu = false; viewModel.imageStudio?.prepare(image); showImageStudio = true })
                    }
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; title = state.title; rename = true })
                    DropdownMenuItem(text = { Text("Fit all cards") }, onClick = { menu = false; viewModel.fitToContent() })
                    DropdownMenuItem(text = { Text("Reset view") }, onClick = { menu = false; viewModel.resetViewport() })
                    DropdownMenuItem(text = { Text("Export to note") }, enabled = state.nodes.isNotEmpty() && !state.exporting,
                        onClick = { menu = false; viewModel.exportToNote() })
                    DropdownMenuItem(text = { Text("Study this canvas") }, enabled = state.nodes.isNotEmpty() && !state.exporting,
                        onClick = { menu = false; viewModel.exportToNote(startStudy = true) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { viewModel.addTextNode() }, enabled = !state.loading && !state.missing && !state.addingNode) { Text("Add text") }
            OutlinedButton(onClick = { picker = true }, enabled = !state.loading && !state.missing) { Text("Link item") }
            if (selected != null) {
                TextButton(onClick = { viewModel.duplicateSelected() }) { Text("Duplicate") }
                TextButton(onClick = { connect = true }, enabled = state.nodes.size > 1) { Text("Connect") }
                TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
            }
        }
        if (state.aiBusy) Row(Modifier.padding(horizontal = 16.dp)) { Text("Halo is thinking...", Modifier.weight(1f)); TextButton(onClick = viewModel::discardAi) { Text("Cancel") } }
        state.notice?.let { notice ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(notice, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::clearNotice) { Text("Dismiss") }
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
                state.missing -> Text("Canvas unavailable", Modifier.align(Alignment.Center))
                state.nodes.isEmpty() -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Make room for your ideas", style = MaterialTheme.typography.titleMedium)
                    Text("Add cards, link your notes and connect what belongs together.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.nodes.forEach { node ->
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
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(if (selected?.id == node.id) 2.dp else 1.dp,
                            if (selected?.id == node.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        if (node.refObjectId == null) BasicTextField(node.text, { viewModel.updateNodeText(node.id, it) },
                            modifier = Modifier.padding(16.dp).onFocusChanged { if (it.isFocused) viewModel.selectNode(node.id) }, textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner -> if (node.text.isBlank()) Text("Write an idea...", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() })
                        else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val reference = state.references.firstOrNull { it.id.value == node.refObjectId }
                            state.images[node.refObjectId]?.let { image -> com.halo.app.ui.workspace.WorkspaceAttachmentPreview(image, Modifier.fillMaxWidth().height((110f * state.viewportScale).coerceAtLeast(48f).dp)) }
                            Text(if (state.images[node.refObjectId] != null) "Image" else reference?.type?.name?.lowercase() ?: "Source unavailable", style = MaterialTheme.typography.labelSmall)
                            Text(reference?.title ?: node.text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            TextButton(onClick = { viewModel.openReference(node.id) }, enabled = reference != null) { Text("Open source") }
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
