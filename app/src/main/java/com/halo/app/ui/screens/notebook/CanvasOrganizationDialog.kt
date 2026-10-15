package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import com.halo.app.ui.design.HaloShapes
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloActionButton

@Composable
internal fun CanvasOrganizationDialog(viewModel: CanvasEditorViewModel, state: CanvasEditorUiState) {
    val proposal = state.organization ?: return
    val newGroupLabel = uiText("New group")
    AlertDialog(shape = HaloShapes.hero, onDismissRequest = viewModel::cancelOrganization,
        title = { Text(uiText("Preview organization")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("%1\$d source cards · %2\$d groups", proposal.sourceFingerprints.size, proposal.groups.size))
            val arranged = proposal.groups.sumOf { it.memberIds.size }
            Text(uiText("%1\$d cards will move; %2\$d will stay where they are.", arranged, proposal.sourceFingerprints.size - arranged))
            Text(uiText(if (proposal.suggestedByAi) "Group headings and next steps are AI suggestions. Review them before approving." else "This is your manual organization. Edit the groups before approving."))
            Text(uiText("Approval arranges these cards and adds group links. Original notes and files stay unchanged. Nothing is sent to another app."))
            proposal.groups.forEachIndexed { index, group ->
                Surface(Modifier.fillMaxWidth(), shape = HaloShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var sourceDetails by rememberSaveable(proposal.id, index) { mutableStateOf(false) }
                OutlinedTextField(group.title, { viewModel.editOrganizationGroup(index, it) },
                    shape = HaloShapes.control, label = { Text(uiText("Group title")) }, modifier = Modifier.fillMaxWidth(), enabled = !state.aiBusy)
                group.memberIds.forEach { id ->
                    val node = state.nodes.firstOrNull { it.id == id }
                    Text(node?.readableText()?.take(100)?.takeIf { it.isNotBlank() } ?: uiText("Untitled card"),
                        style = MaterialTheme.typography.bodyMedium)
                    if (sourceDetails) Text(uiText("Source: %1\$s", node?.refObjectId ?: id),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!state.aiBusy) {
                        if (proposal.groups.size > 1) TextButton(onClick = { viewModel.moveOrganizationMember(id, (index + 1) % proposal.groups.size) }) { Text(uiText("Move to next group")) }
                        TextButton(onClick = { viewModel.newOrganizationGroup(id, newGroupLabel) }, enabled = proposal.groups.size < 12 && group.memberIds.size > 1) { Text(uiText("Move to new group")) }
                    }
                }
                TextButton(onClick = { sourceDetails = !sourceDetails }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText(if (sourceDetails) "Hide technical details" else "Technical details"))
                }
                TextButton(onClick = { viewModel.removeOrganizationGroup(index) }, enabled = !state.aiBusy) { Text(uiText("Remove suggested group")) }
                } }
            }
            Text(uiText("Next steps are optional suggestions, not completed tasks."), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(proposal.nextSteps.joinToString("\n"), viewModel::editNextSteps,
                shape = HaloShapes.control, label = { Text(uiText("Next steps (one per line)")) }, modifier = Modifier.fillMaxWidth(), enabled = !state.aiBusy)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(
                value = proposal.createLinkedNote, enabled = !state.aiBusy, role = Role.Checkbox,
                onValueChange = viewModel::setOrganizationNote), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(proposal.createLinkedNote, onCheckedChange = null, enabled = !state.aiBusy)
                Text(uiText("Create a linked checklist note"), Modifier.weight(1f))
            }
            if (state.aiBusy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(uiText("halo is thinking...")) }
        } },
        confirmButton = { HaloActionButton(onClick = viewModel::approveOrganization,
            enabled = !state.aiBusy && runCatching { proposal.validate() }.isSuccess) { Text(uiText("Tidy up the board")) } },
        dismissButton = { TextButton(onClick = viewModel::cancelOrganization) { Text(uiText("Cancel")) } })
}
