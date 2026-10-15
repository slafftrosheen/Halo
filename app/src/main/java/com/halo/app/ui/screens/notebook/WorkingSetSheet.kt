package com.halo.app.ui.screens.notebook

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.notebook.WorkspaceObjectType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkingSetSheet(
    items: List<WorkspaceObject>,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    estimatedTokens: Map<String, Int> = emptyMap(),
    cloudAllowed: Boolean = false,
    remoteAllowed: (WorkspaceObject) -> Boolean = { cloudAllowed && it.type != WorkspaceObjectType.MEMORY },
    onOpen: (WorkspaceObject) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = com.halo.app.ui.design.HaloShapes.hero) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = uiText("halo context"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = uiText("Items available to halo: %1\$d", items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (items.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text(uiText("Clear all"))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(uiText("Selected items take priority over older chat. Removing context keeps the original items."),
                style = MaterialTheme.typography.bodySmall)

            if (items.isEmpty()) {
                Text(
                    text = uiText("Nothing added yet. Add a note, chat, file, or memory when you want halo to use it in a conversation."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id.value }) { item ->
                        Surface(
                            shape = com.halo.app.ui.design.HaloShapes.card,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (item.type) {
                                        WorkspaceObjectType.NOTE -> Icons.Rounded.NoteAlt
                                        WorkspaceObjectType.CONVERSATION -> Icons.Rounded.Forum
                                        WorkspaceObjectType.MEMORY -> Icons.Rounded.Psychology
                                        WorkspaceObjectType.CANVAS -> Icons.Rounded.DashboardCustomize
                                        WorkspaceObjectType.FILE,
                                        WorkspaceObjectType.MEDIA,
                                        WorkspaceObjectType.ARTIFACT -> Icons.Rounded.Description
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.size(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = uiText(when (item.type) {
                                            WorkspaceObjectType.CONVERSATION -> "Saved chat"
                                            WorkspaceObjectType.CANVAS -> "Canvas"
                                            WorkspaceObjectType.NOTE -> "Note"
                                            WorkspaceObjectType.FILE -> "File"
                                            WorkspaceObjectType.MEMORY -> "Memory"
                                            WorkspaceObjectType.MEDIA -> "Media"
                                            WorkspaceObjectType.ARTIFACT -> "Artifact"
                                        }),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(uiText(if (remoteAllowed(item)) "Cloud allowed" else "Local only"),
                                    Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall)
                                TextButton(onClick = { onOpen(item) }) { Text(uiText("Open")) }
                                TextButton(onClick = { onRemove(item.id.value) }) { Text(uiText("Remove from context")) }

                            }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextTransparencySheet(
    activeConversationTitle: String?,
    workingSetCount: Int,
    memoryEnabled: Boolean,
    cloudSharingAllowed: Boolean,
    onDismiss: () -> Unit
) {
    val composition by com.halo.runtime.cognition.context.ContextDiagnostics.composition.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = com.halo.app.ui.design.HaloShapes.hero) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = uiText("Why this context?"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = uiText("halo assembles context only from selected local sources."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            ContextSourceRow(
                title = "Conversation",
                description = activeConversationTitle?.let { "Active thread: $it" } ?: "New thread"
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            ContextSourceRow(
                title = "halo context",
                description = "$workingSetCount user-selected workspace ${if (workingSetCount == 1) "item" else "items"}"
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            ContextSourceRow(
                title = "Memory",
                description = if (memoryEnabled) "Enabled · Bounded retrieval" else "Disabled"
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            ContextSourceRow(
                title = "Network Privacy",
                description = if (cloudSharingAllowed) "Network models may receive selected context" else "Local only · Context restricted for cloud models"
            )
            composition?.let { value ->
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ContextSourceRow("Context Inspector", "Estimated tokens · window ${value.budget.modelContextTokens}; output ${value.budget.reservedOutputTokens}; system ${value.budget.reservedSystemTokens}; remaining ${value.remainingTokens}")
                value.segmentTokens.forEach { (kind, tokens) ->
                    Text("${kind.name.lowercase().replace('_', ' ')}: $tokens", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ContextSourceRow(title: String, description: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
