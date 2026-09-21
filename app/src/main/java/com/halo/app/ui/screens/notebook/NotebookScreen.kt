package com.halo.app.ui.screens.notebook

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.data.notebook.NotebookNote
import com.halo.app.ui.presence.HaloSignalGlyph
import com.halo.app.ui.presence.HaloSignalKind
import com.halo.app.ui.theme.HaloPastelYellow

@Composable
fun NotebookScreen(viewModel: NotebookViewModel) {
    val state by viewModel.uiState.collectAsState()
    val selected by (viewModel.workspace?.workingSet ?: kotlinx.coroutines.flow.flowOf(emptyList<String>())).collectAsState(emptyList())
    var panel by remember { mutableStateOf<String?>(null) }
    if (panel != null) WorkspacePanel(viewModel, panel!!, onClose = { panel = null })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Notebook",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Your persistent workspace.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = viewModel::createNote,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New note",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(11.dp)
                )
            }
        }

        Row {
            TextButton(onClick = { panel = "Conversations" }) { Text("Conversations") }
            TextButton(onClick = { panel = "Memory" }) { Text("Memory") }
        }
        Text("Working Set: ${selected.size} selected · local context", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            placeholder = { Text("Search notesâ€¦") },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NotebookFilter.values().forEach { filter ->
                FilterChip(
                    label = filter.label,
                    selected = state.filter == filter,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setFilter(filter) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        if (state.notes.isEmpty()) {
            EmptyNotebookState(
                hasQuery = state.query.isNotBlank(),
                filter = state.filter,
                onCreate = viewModel::createNote,
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                text = when (state.filter) {
                    NotebookFilter.ALL -> "Recent"
                    NotebookFilter.INBOX -> "Inbox"
                    NotebookFilter.ARCHIVED -> "Archived"
                    NotebookFilter.TRASH -> "Trash"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.notes, key = { it.id }) { note ->
                    NotebookNoteRow(
                        note = note,
                        onClick = { viewModel.openNote(note.id) }
                    )
                    if (state.filter != NotebookFilter.TRASH) Row {
                        TextButton(onClick = { viewModel.selectContext(note.id, note.id !in selected) }) {
                            Text(if (note.id in selected) "Remove from Working Set" else "Add to Working Set")
                        }
                        TextButton(onClick = { viewModel.remember(note.id) }) { Text("Remember") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NotebookNoteRow(
    note: NotebookNote,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            HaloSignalGlyph(
                kind = HaloSignalKind.NOTE,
                size = 38.dp
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = note.title.ifBlank { "Untitled note" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = DateUtils.getRelativeTimeSpanString(
                            note.updatedAt,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                        ).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val preview = note.body
                    .lineSequence()
                    .drop(1)
                    .joinToString(" ")
                    .trim()
                    .ifBlank { note.body.trim() }

                if (preview.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (note.inInbox) {
                    Spacer(Modifier.height(7.dp))
                    Surface(
                        color = HaloPastelYellow.copy(alpha = 0.32f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Inbox",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotebookState(
    hasQuery: Boolean,
    filter: NotebookFilter,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HaloSignalGlyph(kind = HaloSignalKind.NOTE, size = 48.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (hasQuery) "No matching notes" else when (filter) {
                    NotebookFilter.ALL -> "Notebook is empty"
                    NotebookFilter.INBOX -> "Inbox is clear"
                    NotebookFilter.ARCHIVED -> "Nothing archived"
                    NotebookFilter.TRASH -> "Trash is empty"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!hasQuery && filter == NotebookFilter.ALL) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = onCreate,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Create note",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspacePanel(viewModel: NotebookViewModel, panel: String, onClose: () -> Unit) {
    val useMemories by (viewModel.workspace?.useMemories ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    val cloudSharing by (viewModel.workspace?.cloudSharing ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    val history by (viewModel.conversations?.history ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.halo.app.data.PersistedTurn>())).collectAsState()
    val memories by (viewModel.workspace?.memories ?: kotlinx.coroutines.flow.flowOf(emptyList<com.halo.app.data.Memory>())).collectAsState(emptyList())
    var opened by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<com.halo.app.data.Memory?>(null) }
    var correction by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text(panel) }, confirmButton = {
        TextButton(onClick = onClose) { Text("Close") }
    }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (panel == "Conversations") {
                if (history.isEmpty()) Text("Completed conversations appear here.")
                history.groupBy { it.conversationId }.forEach { (id, turns) ->
                    TextButton(onClick = { opened = if (opened == id) null else id }) {
                        Text(turns.first().userText.take(90))
                    }
                    if (opened == id) {
                        turns.forEach { Text("You: ${it.userText}\nHalo: ${it.assistantText}\n", style = MaterialTheme.typography.bodyMedium) }
                        TextButton(onClick = { viewModel.openConversation(id); onClose() }) { Text("Use this conversation for next turn") }
                    }
                }
            } else {
                com.halo.app.ui.workspace.HaloWorkspaceToggleRow(title = "Use memories in Halo context", checked = useMemories,
                    onCheckedChange = { viewModel.workspace?.setUseMemories(it) })
                com.halo.app.ui.workspace.HaloWorkspaceToggleRow(title = "Share workspace with network models",
                    subtitle = "Allows selected Working Set and enabled memories to leave this phone.", checked = cloudSharing,
                    onCheckedChange = { viewModel.workspace?.setCloudSharing(it) })
                if (memories.isEmpty()) Text("Remember a note explicitly to add it here.")
                memories.forEach { memory ->
                    Text(memory.content)
                    memory.sourceObjectId?.let { id -> TextButton(onClick = { viewModel.openNote(id); onClose() }) { Text("Source · revision ${memory.sourceRevision}") } }
                    Row {
                        TextButton(onClick = { editing = memory; correction = memory.content }) { Text("Correct") }
                        TextButton(onClick = { viewModel.forget(memory.id) }) { Text("Forget") }
                    }
                }
                if (editing != null) {
                    OutlinedTextField(value = correction, onValueChange = { correction = it }, label = { Text("Corrected memory") })
                    TextButton(onClick = { editing?.let { viewModel.correct(it, correction) }; editing = null }, enabled = correction.isNotBlank()) { Text("Save correction") }
                }
            }
        }
    })
}
