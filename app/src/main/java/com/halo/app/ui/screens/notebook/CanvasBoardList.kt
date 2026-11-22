package com.halo.app.ui.screens.notebook

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.notebook.CanvasNode
import com.halo.app.data.notebook.CanvasNodeType
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

private data class CanvasListLink(val targetId: String, val outgoing: Boolean, val label: String?)

internal fun CanvasNode.canEditListText(): Boolean =
    refObjectId == null && type in setOf(CanvasNodeType.TEXT, CanvasNodeType.STICKY, CanvasNodeType.GROUP, CanvasNodeType.FRAME, CanvasNodeType.TAG)

@Composable
internal fun canvasTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

@Composable
internal fun CanvasBoardModeSwitch(list: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !list, onClick = { onSelect(false) }, modifier = Modifier.heightIn(min = 48.dp),
            label = { Text(stringResource(R.string.canvas_presentation_board)) })
        FilterChip(selected = list, onClick = { onSelect(true) }, modifier = Modifier.heightIn(min = 48.dp),
            label = { Text(stringResource(R.string.canvas_presentation_list)) })
    }
}

@Composable
internal fun CanvasBoardList(
    state: CanvasEditorUiState,
    connectionNodes: Set<String>?,
    boardEditingEnabled: Boolean,
    onSelect: (String) -> Unit,
    onEdit: (CanvasNode) -> Unit,
    onCardActions: (String) -> Unit,
    onOpenSource: (String) -> Unit,
    appearance: CanvasAppearance = CanvasAppearance(),
    modifier: Modifier = Modifier,
) {
    val cards = remember(state.nodes) { state.nodes.sortedWith(compareBy<CanvasNode> { it.y }.thenBy { it.x }.thenBy { it.id }) }
    val references = remember(state.references) { state.references.associateBy { it.id.value } }
    val nodes = remember(state.nodes) { state.nodes.associateBy { it.id } }
    val links = remember(state.edges, nodes) {
        buildMap<String, MutableList<CanvasListLink>> {
            state.edges.forEach { edge ->
                if (edge.fromNodeId !in nodes || edge.toNodeId !in nodes) return@forEach
                getOrPut(edge.fromNodeId) { mutableListOf() }.add(CanvasListLink(edge.toNodeId, true, edge.label))
                if (edge.fromNodeId != edge.toNodeId) {
                    getOrPut(edge.toNodeId) { mutableListOf() }.add(CanvasListLink(edge.fromNodeId, false, edge.label))
                }
            }
        }
    }
    val listState = rememberLazyListState()
    val selectedId = state.selectedNodeId
    LaunchedEffect(selectedId) {
        val position = cards.indexOfFirst { it.id == selectedId }
        if (position >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.key == selectedId }) {
            listState.scrollToItem(position)
        }
    }
    LazyColumn(modifier.fillMaxWidth(), state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(cards, key = { it.id }) { node ->
            val reference = references[node.refObjectId]
            val title = canvasListTitle(node, reference?.title)
            val glyph = when (node.type) {
                CanvasNodeType.TEXT, CanvasNodeType.STICKY, CanvasNodeType.NOTE -> HaloGlyph.NOTE
                CanvasNodeType.IMAGE, CanvasNodeType.INK_SELECTION -> HaloGlyph.SEE
                CanvasNodeType.FILE -> HaloGlyph.FILE
                CanvasNodeType.SKETCH -> HaloGlyph.TOOL
                CanvasNodeType.COMMENT -> HaloGlyph.NOTE
                CanvasNodeType.GROUP, CanvasNodeType.FRAME, CanvasNodeType.SHAPE, CanvasNodeType.TAG -> HaloGlyph.ACTION
            }
            val selectedCard = if (connectionNodes != null) node.id in connectionNodes
                else if (state.selectionMode) node.id in state.selectedNodeIds else node.id == selectedId
            Surface(Modifier.fillMaxWidth(), shape = HaloShapes.card,
                color = canvasCardColor(appearance, node,
                    node.refObjectId?.let { it in state.images || state.files[it]?.mimeType?.startsWith("image/") == true } == true),
                border = BorderStroke(1.dp, if (selectedCard) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    TextButton(onClick = { onSelect(node.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { selected = selectedCard }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HaloIcon(glyph, null, Modifier.size(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                                Text(uiText(when (node.type) {
                                    CanvasNodeType.INK_SELECTION -> "Ink selection"
                                    CanvasNodeType.COMMENT -> "Comment pin"
                                    else -> node.type.name.lowercase().replaceFirstChar { it.uppercase() }
                                }), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    links[node.id].orEmpty().forEach { link ->
                        val target = nodes[link.targetId] ?: return@forEach
                        val targetTitle = canvasListTitle(target, references[target.refObjectId]?.title).lineSequence().first().take(100)
                        val relationship = stringResource(
                            if (link.outgoing) R.string.canvas_presentation_linked_to else R.string.canvas_presentation_linked_from,
                            targetTitle
                        )
                        Text(link.label?.takeIf(String::isNotBlank)?.let { "$relationship · $it" } ?: relationship,
                            Modifier.padding(start = 12.dp, bottom = 6.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (connectionNodes == null && !state.selectionMode && selectedCard) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            when {
                                node.canEditListText() -> TextButton(onClick = { onEdit(node) }, enabled = boardEditingEnabled,
                                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Edit")) }
                                reference != null -> TextButton(onClick = { onOpenSource(node.id) },
                                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Open source")) }
                            }
                            TextButton(onClick = { onCardActions(node.id) }, enabled = boardEditingEnabled,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Card actions")) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun canvasListTitle(node: CanvasNode, reference: String?): String = (reference ?: when (node.type) {
    CanvasNodeType.SKETCH -> uiText("Sketch")
    CanvasNodeType.INK_SELECTION -> uiText("Ink selection")
    CanvasNodeType.COMMENT -> uiText("Comment pin")
    else -> node.readableText()
}).trim().ifBlank { uiText("Untitled card") }
