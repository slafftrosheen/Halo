package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.data.memory.MemoryPageMetadata
import com.halo.app.data.memory.MemoryPageNeighborhood
import com.halo.app.data.memory.MemoryRecord
import com.halo.app.data.memory.memoryPageNeighborhood
import com.halo.app.data.memory.memoryPageTag
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.runtime.cognition.memory.MemoryLimits
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal enum class MemoryPageTool(val title: String) {
    LINK("Link pages"),
    TAG("Tags"),
    BACKLINKS("Linked here from"),
    MAP("Link map"),
}

@Composable
internal fun MemoryPageToolsDialog(
    tool: MemoryPageTool,
    pages: List<MemoryRecord>,
    metadata: MemoryPageMetadata,
    busy: Boolean,
    error: String?,
    viewModel: MemoryHubViewModel,
    close: () -> Unit,
    openPage: (String) -> Unit,
) {
    var sourceId by remember(tool) { mutableStateOf(if (pages.size == 1) pages.first().id else null) }
    val source = pages.firstOrNull { it.id == sourceId }
    val byId = remember(pages) { pages.associateBy(MemoryRecord::id) }
    fun open(id: String) {
        close()
        openPage(id)
    }
    AlertDialog(
        onDismissRequest = { if (!busy) close() },
        title = { Text(uiText(tool.title)) },
        text = {
            Column(Modifier.heightIn(max = 490.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (source == null) {
                    Text(uiText("Choose a memory page"))
                    MemoryPagePicker(pages, busy) { sourceId = it.id }
                } else {
                    Text(memoryPageTitle(source), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { sourceId = null }, enabled = !busy) { Text(uiText("Choose another page")) }
                    when (tool) {
                        MemoryPageTool.LINK -> MemoryLinkEditor(source, pages, metadata, busy,
                            onExisting = { page, linked -> viewModel.linkPage(source, page, linked) },
                            onCreate = { viewModel.linkNewPage(source, it) })
                        MemoryPageTool.TAG -> MemoryTagEditor(source.id, metadata, busy) { label, tagged ->
                            viewModel.tagPage(source, label, tagged)
                        }
                        MemoryPageTool.BACKLINKS -> MemoryBacklinks(
                            metadata.links.filter { it.targetId == source.id }.mapNotNull { byId[it.sourceId] }, ::open)
                        MemoryPageTool.MAP -> MemoryMap(source.id, byId, metadata, ::open)
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = close, enabled = !busy) { Text(uiText("Done")) } },
    )
}

@Composable
private fun MemoryPagePicker(pages: List<MemoryRecord>, busy: Boolean, onPage: (MemoryRecord) -> Unit) {
    var search by remember { mutableStateOf("") }
    HaloTextField(search, { search = it.take(120) }, kind = VoiceFieldKind.SEARCH,
        label = { Text(uiText("Find a memory page")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    val matching = pages.filter { search.isBlank() || it.content.contains(search.trim(), ignoreCase = true) }
    if (matching.isEmpty()) Text(uiText("No matching memory pages"))
    matching.take(25).forEach { page ->
        TextButton(onClick = { onPage(page) }, enabled = !busy) {
            Text(memoryPageTitle(page), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    if (matching.size > 25) Text(uiText("Search to narrow the page list."), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun MemoryLinkEditor(
    source: MemoryRecord,
    pages: List<MemoryRecord>,
    metadata: MemoryPageMetadata,
    busy: Boolean,
    onExisting: (MemoryRecord, Boolean) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember(source.id) { mutableStateOf(false) }
    var value by remember(source.id) { mutableStateOf("") }
    var submitted by remember(source.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(metadata.links, pages) {
        val text = submitted
        val target = text?.let { content -> pages.firstOrNull { it.content == content } }
        if (target != null && metadata.links.any { it.sourceId == source.id && it.targetId == target.id }) {
            creating = false
            value = ""
            submitted = null
        }
    }
    val links = metadata.links.asSequence().filter { it.sourceId == source.id }.map { it.targetId }.toSet()
    val linked = pages.filter { it.id in links }
    if (linked.isNotEmpty()) Text(uiText("Links from this page"), style = MaterialTheme.typography.titleSmall)
    linked.forEach { page ->
        TextButton(onClick = { onExisting(page, false) }, enabled = !busy) {
            Text(uiText("Remove link to %1\$s", memoryPageTitle(page)), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    TextButton(onClick = { creating = !creating }, enabled = !busy) {
        Text(uiText(if (creating) "Link an existing page" else "Create a linked page"))
    }
    if (creating) {
        HaloTextField(value, { value = it.take(MemoryLimits.CONTENT) }, enabled = !busy,
            label = { Text(uiText("New memory page")) }, maxLines = 5, kind = VoiceFieldKind.TEXT)
        Button(onClick = { submitted = value.trim(); onCreate(value.trim()) }, enabled = !busy && value.isNotBlank()) {
            Text(uiText("Create and link"))
        }
    } else MemoryPagePicker(pages.filter { it.id != source.id && it.id !in links }, busy) { onExisting(it, true) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryTagEditor(id: String, metadata: MemoryPageMetadata, busy: Boolean, onTag: (String, Boolean) -> Unit) {
    var value by remember(id) { mutableStateOf("") }
    val tag = runCatching { memoryPageTag(value) }.getOrNull()
    val assigned = metadata.tagsOf(id)
    LaunchedEffect(assigned, tag) {
        if (tag != null && assigned.any { it.key == tag.first }) value = ""
    }
    Text(uiText("Tags group pages across folders and scopes."), style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        assigned.forEach { existing ->
            AssistChip(onClick = { onTag(existing.label, false) }, enabled = !busy,
                label = { Text(uiText("Remove tag #%1\$s", existing.label)) })
        }
    }
    HaloTextField(value, { value = it.take(34) }, enabled = !busy, singleLine = true,
        label = { Text(uiText("New tag")) }, kind = VoiceFieldKind.TEXT)
    Button(onClick = { tag?.let { onTag(it.second, true) } }, enabled = tag != null && !busy) {
        Text(uiText("Add tag"))
    }
}

@Composable
internal fun MemoryBacklinks(pages: List<MemoryRecord>, open: (String) -> Unit) {
    Text(uiText("Linked here from"), style = MaterialTheme.typography.titleSmall)
    if (pages.isEmpty()) Text(uiText("No pages link here yet."), style = MaterialTheme.typography.bodySmall)
    pages.forEach { page ->
        TextButton(onClick = { open(page.id) }) { Text(memoryPageTitle(page), maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun MemoryMap(sourceId: String, pages: Map<String, MemoryRecord>, metadata: MemoryPageMetadata, open: (String) -> Unit) {
    var depth by remember(sourceId) { mutableIntStateOf(1) }
    val graph = remember(sourceId, metadata.links, pages.keys, depth) {
        memoryPageNeighborhood(sourceId, metadata.links, pages.keys, depth)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..3).forEach { step ->
            FilterChip(step == depth, { depth = step }, label = { Text(uiText("Depth %1\$d", step)) })
        }
    }
    if (graph.truncated) Text(uiText("Showing up to 36 linked pages."), style = MaterialTheme.typography.bodySmall)
    val description = uiText("Map of %1\$d pages and %2\$d links", graph.distance.size, graph.links.size)
    val accent = MaterialTheme.colorScheme.primary
    val edge = MaterialTheme.colorScheme.outline
    val selected = MaterialTheme.colorScheme.tertiary
    val latestOpen by rememberUpdatedState(open)
    Canvas(Modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = description }
        .pointerInput(graph) {
            detectTapGestures { position ->
                val dots = memoryDotPositions(graph, size.width.toFloat(), size.height.toFloat())
                val nearest = dots.minByOrNull { (_, dot) -> (dot - position).getDistance() }
                if (nearest != null && (nearest.value - position).getDistance() <= 24.dp.toPx()) latestOpen(nearest.key)
            }
        }) {
        val dots = memoryDotPositions(graph, size.width, size.height)
        graph.links.forEach { link ->
            val from = dots.getValue(link.sourceId)
            val to = dots.getValue(link.targetId)
            drawLine(edge, from, to, strokeWidth = 1.2.dp.toPx())
        }
        dots.forEach { (id, point) ->
            drawCircle(if (id == sourceId) selected else accent, if (id == sourceId) 8.dp.toPx() else 5.dp.toPx(), point)
            if (id == sourceId) drawCircle(selected.copy(alpha = .5f), 13.dp.toPx(), point, style = Stroke(1.dp.toPx()))
        }
    }
    if (graph.links.isEmpty()) Text(uiText("This page has no links yet."), style = MaterialTheme.typography.bodySmall)
    graph.distance.keys.forEach { id ->
        val page = pages[id]
        if (page != null) TextButton(onClick = { open(id) }) { Text(memoryPageTitle(page), maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

private fun memoryDotPositions(graph: MemoryPageNeighborhood, width: Float, height: Float): Map<String, Offset> {
    val center = Offset(width / 2, height / 2)
    val rings = graph.distance.entries.groupBy { it.value }
    val maxRing = rings.keys.maxOrNull()?.coerceAtLeast(1) ?: 1
    return buildMap {
        rings.forEach { (ring, entries) ->
            val fraction = .42f * ring / maxRing
            entries.forEachIndexed { index, entry ->
                val angle = (2 * PI * index / entries.size) - PI / 2 + (ring - 1) * .20
                put(entry.key, center + Offset(cos(angle).toFloat() * width * fraction, sin(angle).toFloat() * height * fraction))
            }
        }
    }
}

internal fun memoryPageTitle(page: MemoryRecord) = page.content.lineSequence().firstOrNull { it.isNotBlank() }
    ?.trim()?.take(100).orEmpty()
