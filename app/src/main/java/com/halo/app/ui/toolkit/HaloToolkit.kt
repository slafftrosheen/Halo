package com.halo.app.ui.toolkit

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.zone6.Zone6ButtonSheet
import com.halo.app.ui.zone6.editZone6PageOnHold
import com.halo.app.ui.zone6.rememberZone6PageSetting
import kotlinx.coroutines.launch

internal enum class ToolkitGroup(@StringRes val label: Int) {
    MAKE(R.string.toolkit_make),
    CAPTURE(R.string.toolkit_capture),
    PLAN(R.string.toolkit_plan),
}

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
        ToolkitTool("vault", R.string.toolkit_vault, HaloGlyph.LOCAL, ToolkitGroup.PLAN, Color(0xFFD8BC88), true),
    )

    fun tool(route: String?): ToolkitTool? = tools.firstOrNull { it.route == route }
    fun pinned(saved: String?): List<ToolkitTool> =
        (saved ?: DEFAULT_SHELF).split('|').distinct().mapNotNull(::tool).take(4)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HaloToolkitShelf(easy: Boolean, onEdit: (() -> Unit)?, onOpen: (String) -> Unit) {
    val shelf = rememberZone6PageSetting(HaloToolkit.SHELF)
    val seen = rememberZone6PageSetting(HaloToolkit.SEEN)
    val scope = rememberCoroutineScope()
    val current = HaloToolkit.pinned(shelf.value)
    val font = LocalDensity.current.fontScale
    var manage by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<ToolkitTool?>(null) }
    var failure by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun open(tool: ToolkitTool) {
        if (tool.introduced) scope.launch {
            val former = seen.value
            val visited = former.orEmpty().split('|').filter { it.isNotBlank() }.toSet()
            if (tool.route !in visited) seen.apply(former, (visited + tool.route).joinToString("|"), "")
        }
        onOpen(tool.route)
    }
    fun toggle(tool: ToolkitTool) {
        val expected = shelf.value
        val latest = HaloToolkit.pinned(expected)
        val change = if (tool in latest) latest - tool else (latest + tool).takeLast(4)
        busy = true
        scope.launch {
            try {
                failure = !shelf.apply(expected, change.joinToString("|") { it.route }, HaloToolkit.DEFAULT_SHELF)
                if (!failure) target = null
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.zone5_choose_tool), style = MaterialTheme.typography.titleMedium,
            modifier = onEdit?.let { Modifier.editZone6PageOnHold(it) } ?: Modifier)
        TextButton({ manage = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.toolkit_edit_shelf))
        }
        val groups = if (easy) listOf(null to current)
        else ToolkitGroup.entries.map { group -> group to HaloToolkit.tools.filter { it.group == group } }
        groups.forEach { (group, tools) ->
            if (group != null) Text(stringResource(group.label), style = MaterialTheme.typography.titleSmall)
            if (easy && tools.isEmpty()) Text(stringResource(R.string.toolkit_shelf_empty))
            tools.chunked(if (font >= 1.4f) 1 else if (easy) 2 else 3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { tool ->
                        val seenTool = tool.route in seen.value.orEmpty().split('|')
                        val pinLabel = stringResource(R.string.toolkit_edit_shelf)
                        val shape = MaterialTheme.shapes.large
                        Surface(Modifier.weight(1f).heightIn(min = if (easy) 132.dp else 96.dp)
                            .clip(shape).combinedClickable(role = Role.Button, onClick = { open(tool) },
                                onLongClickLabel = pinLabel, onLongClick = { failure = false; target = tool }),
                            shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val ink = tool.tint.takeUnless { it == Color.Unspecified }?.let { tint ->
                                    androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.onSurface, tint, .42f)
                                } ?: MaterialTheme.colorScheme.onSurface
                                CompositionLocalProvider(LocalContentColor provides ink) {
                                    HaloIcon(tool.glyph, null, Modifier.size(24.dp))
                                }
                                Text(stringResource(tool.title), style = MaterialTheme.typography.titleSmall)
                                if (tool.introduced && !seenTool) Text(stringResource(R.string.toolkit_new),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    repeat((if (font >= 1.4f) 1 else if (easy) 2 else 3) - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (manage || target != null) Zone6ButtonSheet(stringResource(R.string.toolkit_edit_shelf), {
        if (!busy) { target = null; manage = false; failure = false }
    }) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.toolkit_shelf_hint))
            if (failure) Text(stringResource(R.string.page_layout_error), color = MaterialTheme.colorScheme.error)
            (target?.let(::listOf) ?: HaloToolkit.tools).forEach { tool ->
                val selected = tool in current
                val limit = !selected && current.size >= 4
                val label = if (selected) R.string.toolkit_unpin else R.string.toolkit_pin
                OutlinedButton({ toggle(tool) }, enabled = !busy && !limit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(label, stringResource(tool.title)))
                }
            }
            if (current.size == 4) Text(stringResource(R.string.toolkit_shelf_limit))
            if (target != null) TextButton({ target = null; manage = true }) {
                Text(stringResource(R.string.toolkit_edit_shelf))
            }
        }
    }
}
