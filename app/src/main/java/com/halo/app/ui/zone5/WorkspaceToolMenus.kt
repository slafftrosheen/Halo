package com.halo.app.ui.zone5

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.presence.*
import com.halo.app.ui.fx.haloTrace

internal val workspaceMenuFamilies = setOf("note", "canvas", "studio", "file_tools", "study", "memory", "canvases", "notebook")

internal enum class WorkspaceMenuGroup(val label: Int) {
    FILE(R.string.zone5_group_file), EDIT(R.string.zone5_group_edit), ADD(R.string.zone5_group_add),
    VIEW(R.string.zone5_group_view), MORE(R.string.zone5_group_more)
}

internal fun workspaceMenuGroup(id: String): WorkspaceMenuGroup {
    val action = id.removePrefix("page:").substringAfterLast('.')
    if (action.startsWith("view-")) return WorkspaceMenuGroup.VIEW
    if (action.startsWith("export-")) return WorkspaceMenuGroup.FILE
    if (action.startsWith("edit-")) return WorkspaceMenuGroup.EDIT
    if (action.startsWith("add-")) return WorkspaceMenuGroup.ADD
    return when (action) {
        "new", "new-note", "new-canvas", "open", "save", "export", "versions", "close", "copy", "import", "new-image" -> WorkspaceMenuGroup.FILE
        "undo", "redo", "rename", "find", "arrange", "edit", "forget", "background", "transform", "tools", "table" -> WorkspaceMenuGroup.EDIT
        "insert", "add", "add-layer", "material", "attach", "text", "shape", "draw", "image", "camera" -> WorkspaceMenuGroup.ADD
        "outline", "fit", "layers", "details", "source", "history", "practice", "read", "stop-reading" -> WorkspaceMenuGroup.VIEW
        else -> WorkspaceMenuGroup.MORE
    }
}

/** All menus invoke the same live entry lease as satellites; no second command registry. */
@Composable
internal fun WorkspaceToolMenus(guidedActionId: String? = null, onDisclosureChanged: (Boolean) -> Unit = {}) {
    val projection = LocalShellPageActions.current?.value ?: return
    if (projection.owner.routeFamily !in workspaceMenuFamilies ||
        !LocalShellPageOwnerFilter.current(projection.owner)) return
    val actions = projection.actions.filterNot { it.title == "Back" || it.id.endsWith(".back") }
    var opened by remember(projection.owner.routeFamily, projection.owner.objectId) { mutableStateOf<WorkspaceMenuGroup?>(null) }
    val guideSession = LocalZone5GuideSession.current
    LaunchedEffect(projection) { opened = null }
    val disclosure by rememberUpdatedState(onDisclosureChanged)
    LaunchedEffect(opened) { disclosure(opened != null) }
    DisposableEffect(Unit) { onDispose { disclosure(false) } }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WorkspaceMenuGroup.entries.forEach { group ->
            val entries = actions.filter { workspaceMenuGroup(it.id) == group }
            if (entries.isNotEmpty()) Box {
                TextButton(onClick = { opened = group }, modifier = Modifier.heightIn(min = 48.dp)
                    .haloTrace(entries.any { it.id == guidedActionId }, MaterialTheme.colorScheme.primary, 12.dp, lapOnAppear = true),
                    contentPadding = PaddingValues(horizontal = 12.dp)) { Text(stringResource(group.label)) }
                DropdownMenu(expanded = opened == group, onDismissRequest = { opened = null }) {
                    entries.forEach { action ->
                        DropdownMenuItem(enabled = action.enabled,
                            modifier = Modifier.haloTrace(action.id == guidedActionId, MaterialTheme.colorScheme.primary, 12.dp),
                            text = { Column {
                                Text(uiText(action.title))
                                if (!action.enabled) projection.owner.disabledActionReasons[action.id]?.let {
                                    Text(uiText(it), style = MaterialTheme.typography.bodySmall)
                                }
                            } },
                            leadingIcon = { Zone5Icon(zone5ActionIconId(HaloOrbContextAction("page:${action.id}", action.title, action.glyph)), action.glyph, Modifier.size(24.dp)) },
                            onClick = {
                                opened = null
                                if (projection.invoke(action.id)) projection.owner.nextStep?.takeIf { it.actionId == action.id }
                                    ?.let { guideSession?.dismiss(it.id) }
                            })
                    }
                }
            }
        }
    }
}

@Composable
internal fun workspaceInstrumentName(icon: String, fallback: String): String = when (icon) {
    "tool_studio" -> stringResource(R.string.zone5_tool_studio)
    "tool_study" -> stringResource(R.string.zone5_tool_study)
    "tool_memory" -> stringResource(R.string.zone5_tool_memory)
    "tool_canvas", "type_canvas" -> stringResource(R.string.zone5_tool_canvas)
    "tool_notes", "tool_office", "type_note", "office_document" -> stringResource(R.string.zone5_tool_notes)
    else -> fallback
}
