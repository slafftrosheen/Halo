package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.data.notebook.NotebookNote
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.components.HaloGlassLazyPage
import com.halo.app.ui.glass.components.HaloGlassTextField
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import java.text.DateFormat
import java.util.Date

private val NotebookBlue = Color(0xFFB8DFFF)
private val NotebookMint = Color(0xFF9BEBCF)
private val NotebookPink = Color(0xFFF6B8D7)
private val NotebookYellow = Color(0xFFFFD88A)

@Composable
fun NotebookScreen(viewModel: NotebookViewModel) {
    val state by viewModel.uiState.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    HaloGlassLazyPage(title = "") {
        item { NotebookHeader(onCreateNote = viewModel::createNote) }
        item {
            NotebookFilterRow(
                selected = state.filter,
                onSelected = viewModel::setFilter
            )
        }
        item {
            HaloGlassTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = "Search your workspace…",
                singleLine = true,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = HaloGlassColors.TextMuted,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            QuickCaptureRow(
                onCreateNote = viewModel::createNote,
                onOpenInbox = { viewModel.setFilter(NotebookFilter.INBOX) }
            )
        }
        item {
            Text(
                text = when (state.filter) {
                    NotebookFilter.ALL -> "Recent"
                    NotebookFilter.INBOX -> "Inbox"
                    NotebookFilter.ARCHIVED -> "Archived"
                    NotebookFilter.TRASH -> "Trash"
                },
                style = HaloTypography.titleMedium,
                color = HaloGlassColors.TextPrimary,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }

        if (state.notes.isEmpty()) {
            item {
                NotebookEmptyState(
                    hasQuery = state.query.isNotBlank(),
                    filter = state.filter,
                    onCreateNote = viewModel::createNote
                )
            }
        } else {
            items(state.notes, key = NotebookNote::id) { note ->
                NoteCard(
                    note = note,
                    filter = state.filter,
                    onOpen = { viewModel.openNote(note.id) },
                    onArchive = { viewModel.archive(note.id) },
                    onTrash = { viewModel.trash(note.id) },
                    onRestore = { viewModel.restore(note.id) },
                    onOrganize = { viewModel.removeFromInbox(note.id) },
                    onDelete = { pendingDeleteId = note.id }
                )
            }
        }
    }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete this note permanently?") },
            text = { Text("This removes the note and its recovered draft. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteId?.let(viewModel::deletePermanently)
                        pendingDeleteId = null
                    }
                ) { Text("Delete", color = HaloGlassColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NotebookHeader(onCreateNote: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Notebook",
                style = HaloTypography.displayMedium,
                color = HaloGlassColors.TextPrimary
            )
            Text(
                text = "Ideas. Notes. Your workspace.",
                style = HaloTypography.bodySmall,
                color = HaloGlassColors.TextMuted
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(NotebookBlue.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onCreateNote) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New note",
                    tint = NotebookBlue
                )
            }
        }
    }
}

@Composable
private fun NotebookFilterRow(
    selected: NotebookFilter,
    onSelected: (NotebookFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NotebookFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(filter.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NotebookBlue.copy(alpha = 0.24f),
                    selectedLabelColor = NotebookBlue,
                    containerColor = HaloGlassColors.Glass,
                    labelColor = HaloGlassColors.TextSecondary
                )
            )
        }
    }
}

@Composable
private fun QuickCaptureRow(onCreateNote: () -> Unit, onOpenInbox: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickCaptureCard(
            title = "New note",
            subtitle = "Write",
            color = NotebookYellow,
            icon = Icons.Rounded.Description,
            onClick = onCreateNote,
            modifier = Modifier.weight(1f)
        )
        QuickCaptureCard(
            title = "Inbox",
            subtitle = "Captured ideas",
            color = NotebookMint,
            icon = Icons.Rounded.Inbox,
            onClick = onOpenInbox,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickCaptureCard(
    title: String,
    subtitle: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HaloGlassCard(onClick = onClick, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Text(title, style = HaloTypography.labelLarge, color = HaloGlassColors.TextPrimary)
            Text(subtitle, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
        }
    }
}

@Composable
private fun NotebookEmptyState(
    hasQuery: Boolean,
    filter: NotebookFilter,
    onCreateNote: () -> Unit
) {
    HaloGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(NotebookPink.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Description, contentDescription = null, tint = NotebookPink)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    hasQuery -> "No notes match your search"
                    filter == NotebookFilter.ALL -> "Your workspace is ready"
                    else -> "Nothing in ${filter.label.lowercase()}"
                },
                style = HaloTypography.titleMedium,
                color = HaloGlassColors.TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (hasQuery) "Try another phrase." else "Capture an idea and continue it later.",
                style = HaloTypography.bodyMedium,
                color = HaloGlassColors.TextMuted
            )
            if (!hasQuery && filter == NotebookFilter.ALL) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onCreateNote) { Text("Create a note", color = NotebookBlue) }
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: NotebookNote,
    filter: NotebookFilter,
    onOpen: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onOrganize: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    HaloGlassCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NotebookYellow.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Description, contentDescription = null, tint = NotebookYellow)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title,
                    style = HaloTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = HaloGlassColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (note.body.isNotBlank()) {
                    Text(
                        text = note.body.lineSequence().drop(1).joinToString(" ").ifBlank { "Note" },
                        style = HaloTypography.bodySmall,
                        color = HaloGlassColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(note.updatedAt)),
                        style = HaloTypography.labelSmall,
                        color = HaloGlassColors.TextMuted
                    )
                    if (note.inInbox) {
                        Text("• Inbox", style = HaloTypography.labelSmall, color = NotebookMint)
                    }
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Note actions",
                        tint = HaloGlassColors.TextMuted
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (note.inInbox && filter != NotebookFilter.TRASH) {
                        DropdownMenuItem(
                            text = { Text("Remove from Inbox") },
                            leadingIcon = {
                                Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null)
                            },
                            onClick = { menuExpanded = false; onOrganize() }
                        )
                    }
                    if (filter == NotebookFilter.ALL || filter == NotebookFilter.INBOX) {
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                            onClick = { menuExpanded = false; onArchive() }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Restore") },
                            leadingIcon = { Icon(Icons.Rounded.Restore, contentDescription = null) },
                            onClick = { menuExpanded = false; onRestore() }
                        )
                    }
                    if (filter != NotebookFilter.TRASH) {
                        DropdownMenuItem(
                            text = { Text("Move to trash") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                            onClick = { menuExpanded = false; onTrash() }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Delete permanently", color = HaloGlassColors.Danger) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = HaloGlassColors.Danger
                                )
                            },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}
