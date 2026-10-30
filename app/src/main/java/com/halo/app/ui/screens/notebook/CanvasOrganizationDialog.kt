package com.halo.app.ui.screens.notebook

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloActionButton

@Composable
internal fun CanvasOrganizationDialog(viewModel: CanvasEditorViewModel, state: CanvasEditorUiState) {
    val proposal = state.organization ?: return
    val newGroupLabel = uiText("New group")
    var showTechnical by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = viewModel::cancelOrganization,
        title = { Text(uiText("Preview organization")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.notice?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
            Text(uiText("%1\$d source cards · %2\$d groups", proposal.sourceFingerprints.size, proposal.groups.size))
            val arranged = proposal.groups.sumOf { it.memberIds.size }
            Text(uiText("%1\$d cards will move; %2\$d will stay where they are.", arranged, proposal.sourceFingerprints.size - arranged))
            Text(uiText(if (proposal.suggestedByAi) "Group headings and next steps are AI suggestions. Review them before approving." else "This is your manual organization. Edit the groups before approving."))
            Text(uiText("Approval arranges these cards and adds group links. Original notes and files stay unchanged. Nothing is sent to another app."))
            TextButton(onClick = { showTechnical = !showTechnical }) { Text(uiText("Technical details")) }
            proposal.groups.forEachIndexed { index, group ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HaloTextField(group.title, { viewModel.editOrganizationGroup(index, it) },
                    label = { Text(uiText("Group title")) }, modifier = Modifier.fillMaxWidth(), enabled = !state.aiBusy, kind = VoiceFieldKind.SHORT)
                group.memberIds.forEach { id ->
                    val node = state.nodes.firstOrNull { it.id == id }
                    Text(node?.readableText()?.ifBlank { uiText("Untitled card") }?.take(120)
                        ?: uiText("Untitled card"), style = MaterialTheme.typography.bodyMedium)
                    if (showTechnical) Text(uiText("Source: %1\$s", node?.refObjectId ?: id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!state.aiBusy) {
                        if (proposal.groups.size > 1) TextButton(onClick = { viewModel.moveOrganizationMember(id, (index + 1) % proposal.groups.size) }) { Text(uiText("Move to next group")) }
                        TextButton(onClick = { viewModel.newOrganizationGroup(id, newGroupLabel) }, enabled = proposal.groups.size < 12 && group.memberIds.size > 1) { Text(uiText("Move to new group")) }
                    }
                }
                TextButton(onClick = { viewModel.removeOrganizationGroup(index) }, enabled = !state.aiBusy) { Text(uiText("Remove suggested group")) }
                }
                }
            }
            Text(uiText("Next steps are optional suggestions, not completed tasks."), style = MaterialTheme.typography.bodyMedium)
            HaloTextField(proposal.nextSteps.joinToString("\n"), viewModel::editNextSteps,
                label = { Text(uiText("Next steps (one per line)")) }, modifier = Modifier.fillMaxWidth(), enabled = !state.aiBusy, kind = VoiceFieldKind.TEXT)
            Row(Modifier.fillMaxWidth().toggleable(
                value = proposal.createLinkedNote, enabled = !state.aiBusy,
                role = Role.Checkbox, onValueChange = viewModel::setOrganizationNote,
            ).padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(proposal.createLinkedNote, onCheckedChange = null, enabled = !state.aiBusy)
                Text(uiText("Create a linked checklist note"), Modifier.padding(start = 8.dp))
            }
            if (state.aiBusy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(uiText("Halo is thinking...")) }
        } },
        confirmButton = { HaloActionButton(onClick = viewModel::approveOrganization,
            enabled = !state.aiBusy && runCatching { proposal.validate() }.isSuccess) { Text(uiText("Approve organization")) } },
        dismissButton = { TextButton(onClick = viewModel::cancelOrganization) { Text(uiText("Cancel")) } })
}
