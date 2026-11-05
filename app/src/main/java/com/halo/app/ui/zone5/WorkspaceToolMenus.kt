package com.halo.app.ui.zone5

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
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

internal fun workspaceMenuGroup(id: String): WorkspaceMenuGroup = when (WorkspaceActionPresentation.require(id).group) {
    ShellActionGroup.FILE -> WorkspaceMenuGroup.FILE
    ShellActionGroup.EDIT -> WorkspaceMenuGroup.EDIT
    ShellActionGroup.INSERT -> WorkspaceMenuGroup.ADD
    ShellActionGroup.VIEW -> WorkspaceMenuGroup.VIEW
    ShellActionGroup.OPTIONS -> WorkspaceMenuGroup.MORE
}

/** Primary operations only. Secondary options live once in the searchable action sheet. */
@Composable
internal fun WorkspaceToolMenus(guidedActionId: String? = null, onDisclosureChanged: (Boolean) -> Unit = {}) {
    val projection = LocalShellPageActions.current?.value ?: return
    if (projection.owner.routeFamily !in workspaceMenuFamilies || !LocalShellPageOwnerFilter.current(projection.owner)) return
    // The editor supplies Insert/Style/Undo above the IME; retain one editing control row.
    if (projection.owner.routeFamily == "note" && WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0) return
    val primary = projection.actions.filter { WorkspaceActionPresentation.require(it.id).let { presentation -> presentation.role == ShellActionRole.PRIMARY && presentation.placement == ShellPrimaryPlacement.TOOLBAR } }
        .sortedBy { WorkspaceActionPresentation.require(it.id).order }
    if (primary.isEmpty()) return
    val target = if (LocalHaloZone5Navigation.current?.kids == true) 56.dp else 48.dp
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("zone5-primary-actions")) {
        val columns = if (largeText || maxWidth < 300.dp) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            primary.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { action ->
                        val active = projection.owner.activeToolId == action.id
                        FilterChip(selected = active, onClick = { projection.invoke(action.id) }, enabled = action.enabled,
                            label = { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Zone5Icon(zone5ActionIconId(HaloOrbContextAction("page:${action.id}", action.title, action.glyph)), action.glyph, Modifier.size(20.dp))
                                Text(uiText(action.title), Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                            } },
                            modifier = Modifier.weight(1f).heightIn(min = target).semantics {
                                selected = active
                                if (!action.enabled) stateDescription = projection.owner.disabledReasonFor(action.id).orEmpty()
                            }.haloTrace(action.id == guidedActionId, MaterialTheme.colorScheme.primary, 12.dp, lapOnAppear = true))
                    }
                }
            }
            primary.filterNot { it.enabled }.mapNotNull { projection.owner.disabledReasonFor(it.id) }.distinct().forEach {
                Text(uiText(it), style = MaterialTheme.typography.bodySmall)
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
