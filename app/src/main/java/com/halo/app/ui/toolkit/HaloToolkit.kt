package com.halo.app.ui.toolkit

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.zone6.Zone6ButtonSheet
import com.halo.app.ui.zone6.Zone6PageApplyRow
import com.halo.app.ui.zone6.editZone6PageOnHold
import com.halo.app.ui.zone6.rememberZone6PageSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal enum class ToolkitGroup(@StringRes val label: Int) {
    MAKE(R.string.toolkit_make),
    CAPTURE(R.string.toolkit_capture),
    PLAN(R.string.toolkit_plan),
}

internal enum class ToolkitShelfRequest { FIND, EDIT }

internal data class ToolkitTool(
    val route: String,
    @StringRes val title: Int,
    val glyph: HaloGlyph,
    val group: ToolkitGroup,
    val tint: Color = Color.Unspecified,
    val introduced: Boolean = false,
)

internal object HaloToolkit {
    const val SHELF = "workspace.shelf.v8"
    const val SEEN = "workspace.new.v8"
    const val DEFAULT_SHELF = "studio|file_tools|study|canvases"

    val tools = listOf(
        ToolkitTool("studio", R.string.zone5_tool_studio, HaloGlyph.TOOL, ToolkitGroup.MAKE),
        ToolkitTool("canvases", R.string.zone5_tool_canvas, HaloGlyph.NOTE, ToolkitGroup.MAKE),
        ToolkitTool("sheets", R.string.toolkit_sheets, HaloGlyph.FILE, ToolkitGroup.MAKE, Color(0xFFD8BC88), true),
        ToolkitTool("file_tools", R.string.zone5_tool_notes, HaloGlyph.NOTE, ToolkitGroup.CAPTURE),
        ToolkitTool("scan", R.string.toolkit_scan, HaloGlyph.CAMERA, ToolkitGroup.CAPTURE, Color(0xFFE5C564), true),
        ToolkitTool("record", R.string.toolkit_record, HaloGlyph.RECORD, ToolkitGroup.CAPTURE, Color(0xFF8DC5EB), true),
        ToolkitTool("album", R.string.toolkit_album, HaloGlyph.SEE, ToolkitGroup.CAPTURE, Color(0xFFF0C2BF), true),
        ToolkitTool("planner", R.string.toolkit_planner, HaloGlyph.CALENDAR, ToolkitGroup.PLAN, Color(0xFFACBC8C), true),
        ToolkitTool("study", R.string.zone5_tool_study, HaloGlyph.QUESTION, ToolkitGroup.PLAN),
        ToolkitTool("notebook/memory", R.string.zone5_tool_memory, HaloGlyph.MEMORY, ToolkitGroup.PLAN),
        ToolkitTool("maps", R.string.halo11_tool_maps, HaloGlyph.CALENDAR, ToolkitGroup.PLAN, Color(0xFF76C5B3), true),
        ToolkitTool("study?photoCoach=true", R.string.halo11_tool_coach, HaloGlyph.QUESTION, ToolkitGroup.PLAN, Color(0xFFBDA9F6), true),
        ToolkitTool("calc", R.string.halo11_tool_calc, HaloGlyph.TOOL, ToolkitGroup.MAKE, Color(0xFFEACC94), true),
        ToolkitTool("measure", R.string.halo11_tool_measure, HaloGlyph.CAMERA, ToolkitGroup.CAPTURE, Color(0xFF8CB6EF), true),
        ToolkitTool("translate", R.string.halo11_translate_title, HaloGlyph.LANGUAGE, ToolkitGroup.CAPTURE, Color(0xFF8CB6EF), true),
        ToolkitTool("focus", R.string.halo11_tool_focus, HaloGlyph.PLAY, ToolkitGroup.PLAN, Color(0xFF76C5B3), true),
    )

    fun tool(route: String?): ToolkitTool? = tools.firstOrNull { it.route == route }
    fun pinned(saved: String?): List<ToolkitTool> =
        (saved ?: DEFAULT_SHELF).split('|').distinct().mapNotNull(::tool).take(4)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HaloToolkitShelf(
    easy: Boolean, onEdit: (() -> Unit)?, availableRoutes: Set<String> = HaloToolkit.tools.mapTo(hashSetOf()) { it.route },
    request: ToolkitShelfRequest? = null, onRequestHandled: () -> Unit = {}, onOpen: (String) -> Unit,
) {
    val shelf = rememberZone6PageSetting(HaloToolkit.SHELF)
    val seen = rememberZone6PageSetting(HaloToolkit.SEEN)
    val scope = rememberCoroutineScope()
    val current = HaloToolkit.pinned(shelf.value)
    val available = HaloToolkit.tools.filter { it.route in availableRoutes }
    val font = LocalDensity.current.fontScale
    val columns = if (font >= 1.4f) 1 else if (easy) 2 else 3
    var expected by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<List<ToolkitTool>?>(null) }
    var search by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val names = available.associateWith { stringResource(it.title) }
    val witnessed = seen.value.orEmpty().split('|').toSet()

    fun open(tool: ToolkitTool) {
        if (opening) return
        if (tool.introduced && tool.route !in witnessed) {
            opening = true
            scope.launch {
                try {
                    val former = seen.value
                    val visited = former.orEmpty().split('|').filter(String::isNotBlank).toSet()
                    if (tool.route !in visited) {
                        try { seen.apply(former, (visited + tool.route).joinToString("|"), "") }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* A seen marker does not block opening its tool. */ }
                    }
                    onOpen(tool.route)
                } finally { opening = false }
            }
        } else {
            onOpen(tool.route)
        }
    }
    fun begin(tool: ToolkitTool? = null) {
        expected = shelf.value
        failure = false
        draft = if (tool != null && tool !in current && current.size < 4) current + tool else current
    }
    LaunchedEffect(request) {
        when (request) {
            ToolkitShelfRequest.FIND -> { search = true; query = "" }
            ToolkitShelfRequest.EDIT -> if (draft == null) begin()
            null -> return@LaunchedEffect
        }
        onRequestHandled()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.zone5_choose_tool), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }.then(onEdit?.let { Modifier.editZone6PageOnHold(it) } ?: Modifier))
        Row(Modifier.fillMaxWidth()) {
            TextButton({ search = true; query = "" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.toolkit_find_tool))
            }
            TextButton({ begin() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.toolkit_edit_shelf))
            }
        }
        val groups = if (easy) listOf(null to current.filter { it in available })
        else ToolkitGroup.entries.map { group -> group to available.filter { it.group == group } }
        groups.forEach { (group, tools) ->
            if (group != null && tools.isNotEmpty()) Text(stringResource(group.label), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
            if (easy && tools.isEmpty()) Text(stringResource(R.string.toolkit_shelf_empty))
            tools.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { tool ->
                        val pinLabel = stringResource(R.string.toolkit_edit_shelf)
                        val shape = MaterialTheme.shapes.large
                        Surface(Modifier.weight(1f).heightIn(min = if (easy) 132.dp else 96.dp)
                            .clip(shape).combinedClickable(enabled = !opening, role = Role.Button, onClick = { open(tool) },
                                onLongClickLabel = pinLabel, onLongClick = { begin(tool) }),
                            shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Box(Modifier.padding(vertical = 12.dp, horizontal = 6.dp)) {
                                Column(Modifier.fillMaxWidth().align(Alignment.Center).padding(top = if (tool.introduced && tool.route !in witnessed) 10.dp else 0.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ToolkitIdentity(tool)
                                    Text(names.getValue(tool), style = MaterialTheme.typography.titleSmall,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                                if (tool.introduced && tool.route !in witnessed) {
                                    Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.align(Alignment.TopEnd)) {
                                        Text(stringResource(R.string.toolkit_new), Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    if (search) Zone6ButtonSheet(stringResource(R.string.toolkit_find_tool), { search = false }) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloTextField(query, { query = it.take(80) }, singleLine = true, kind = VoiceFieldKind.SEARCH,
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.toolkit_find_tool)) }, readText = null)
            val matches = available.filter { tool ->
                val term = query.trim()
                term.isEmpty() || names.getValue(tool).contains(term, ignoreCase = true) || tool.route.contains(term, ignoreCase = true)
            }
            if (matches.isEmpty()) Text(stringResource(R.string.toolkit_empty_search))
            matches.forEach { tool ->
                TextButton(onClick = { search = false; open(tool) }, enabled = !opening,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    ToolkitIdentity(tool)
                    Spacer(Modifier.width(8.dp))
                    Text(names.getValue(tool), modifier = Modifier.weight(1f))
                }
            }
        }
    }
    draft?.let { configuration ->
        val initial = HaloToolkit.pinned(expected)
        Zone6ButtonSheet(stringResource(R.string.toolkit_edit_shelf), { if (!busy) draft = null }) {
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.toolkit_shelf_hint))
                (configuration + available.filterNot { it in configuration }).forEach { tool ->
                    key(tool.route) {
                        val position = configuration.indexOf(tool)
                        val pinned = position >= 0
                        val title = stringResource(tool.title)
                        ToolkitArrangementRow(tool, title, pinned, enabled = !busy,
                            canMoveUp = position > 0, canMoveDown = pinned && position < configuration.lastIndex,
                            canPin = pinned || configuration.size < 4,
                            onPin = {
                                val latest = draft.orEmpty()
                                if (tool in latest) draft = latest - tool
                                else if (latest.size < 4) draft = latest + tool
                            }, onShift = { movement ->
                                val latest = draft.orEmpty()
                                val from = latest.indexOf(tool)
                                val destination = from + movement
                                if (from in latest.indices && destination in latest.indices)
                                    draft = latest.toMutableList().apply { add(destination, removeAt(from)) }
                            })
                    }
                }
                if (configuration.size == 4) Text(stringResource(R.string.toolkit_shelf_limit))
                Zone6PageApplyRow(configuration != initial, busy, failure, shelf.previous != null,
                    onUndo = { draft = initial; failure = false },
                    onRestore = { draft = HaloToolkit.pinned(shelf.previous); failure = false },
                    onApply = {
                        busy = true
                        scope.launch {
                            try {
                                failure = !shelf.apply(expected, configuration.joinToString("|") { it.route }, HaloToolkit.DEFAULT_SHELF)
                                if (!failure) draft = null
                            } finally { busy = false }
                        }
                    })
            }
        }
    }
}

@Composable
private fun ToolkitIdentity(tool: ToolkitTool) {
    val colors = MaterialTheme.colorScheme
    val identity = tool.tint.takeIf { it != Color.Unspecified } ?: colors.secondary
    Surface(shape = MaterialTheme.shapes.medium, color = lerp(colors.surfaceContainerHigh, identity, .20f),
        contentColor = lerp(colors.onSurface, identity, .38f), modifier = Modifier.size(36.dp)) {
        Box(contentAlignment = Alignment.Center) { HaloIcon(tool.glyph, null, Modifier.size(22.dp)) }
    }
}

@Composable
private fun ToolkitArrangementRow(
    tool: ToolkitTool, title: String, pinned: Boolean, enabled: Boolean, canMoveUp: Boolean, canMoveDown: Boolean,
    canPin: Boolean, onPin: () -> Unit, onShift: (Int) -> Unit,
) {
    val shift by rememberUpdatedState(onShift)
    val rowDistance = with(LocalDensity.current) { 56.dp.toPx() }
    val drag = if (pinned && enabled) Modifier.pointerInput(rowDistance) {
        var offset = 0f
        detectDragGesturesAfterLongPress(onDragStart = { offset = 0f }) { change, delta ->
            change.consume()
            offset += delta.y
            if (offset >= rowDistance) {
                shift(1)
                offset -= rowDistance
            } else if (offset <= -rowDistance) {
                shift(-1)
                offset += rowDistance
            }
        }
    } else Modifier
    val moveUp = uiText("Move up")
    val moveDown = uiText("Move down")
    val actionLabel = stringResource(if (pinned) R.string.toolkit_unpin else R.string.toolkit_pin, title)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ToolkitIdentity(tool)
        Column(Modifier.weight(1f).then(drag)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(if (pinned) R.string.toolkit_in_dock else R.string.toolkit_on_shelf), style = MaterialTheme.typography.labelMedium,
                color = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (pinned) {
            IconButton(onClick = { onShift(-1) }, enabled = enabled && canMoveUp,
                modifier = Modifier.semantics { contentDescription = moveUp }) { Text("↑") }
            IconButton(onClick = { onShift(1) }, enabled = enabled && canMoveDown,
                modifier = Modifier.semantics { contentDescription = moveDown }) { Text("↓") }
        }
        IconButton(onClick = onPin, enabled = enabled && canPin,
            modifier = Modifier.semantics { contentDescription = actionLabel }) {
            HaloIcon(if (pinned) HaloGlyph.SUCCESS else HaloGlyph.ACTION, null)
        }
    }
}
