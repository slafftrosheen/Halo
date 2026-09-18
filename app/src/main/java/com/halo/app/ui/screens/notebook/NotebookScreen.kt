package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.data.notebook.NotebookNote
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.components.HaloGlassLazyPage
import com.halo.app.ui.glass.components.HaloGlassTextField
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import java.text.DateFormat
import java.util.Date

@Composable
fun NotebookScreen(viewModel: NotebookViewModel) {
    val state by viewModel.uiState.collectAsState()

    HaloGlassLazyPage(title = "Notebook") {
        item {
            Text(
                text = "Capture ideas and continue working offline.",
                style = HaloTypography.bodyLarge,
                color = HaloGlassColors.TextSecondary
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HaloGlassTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = "Search notes",
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                HaloGlassButton.Primary(text = "New", onClick = viewModel::createNote)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NotebookFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }
        }

        if (state.notes.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp)
                ) {
                    Text(
                        text = if (state.query.isBlank()) "Your workspace is empty." else "No notes match your search.",
                        style = HaloTypography.titleMedium,
                        color = HaloGlassColors.TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Create a note now; files, memory, and conversations arrive in later Notebook slices.",
                        style = HaloTypography.bodyMedium,
                        color = HaloGlassColors.TextMuted
                    )
                }
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
                    onDelete = { viewModel.deletePermanently(note.id) }
                )
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
    onDelete: () -> Unit
) {
    HaloGlassCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = note.title,
                    style = HaloTypography.titleMedium,
                    color = HaloGlassColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (note.inInbox) {
                    Text(
                        text = "Inbox",
                        style = HaloTypography.labelMedium,
                        color = HaloGlassColors.Accent
                    )
                }
            }
            if (note.body.isNotBlank()) {
                Text(
                    text = note.body.replace('\n', ' '),
                    style = HaloTypography.bodyMedium,
                    color = HaloGlassColors.TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "Edited ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note.updatedAt))}",
                style = HaloTypography.labelSmall,
                color = HaloGlassColors.TextMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (filter) {
                    NotebookFilter.ALL, NotebookFilter.INBOX -> {
                        HaloGlassButton.Ghost(text = "Archive", onClick = onArchive)
                        HaloGlassButton.Ghost(text = "Trash", onClick = onTrash)
                    }
                    NotebookFilter.ARCHIVED -> {
                        HaloGlassButton.Ghost(text = "Restore", onClick = onRestore)
                        HaloGlassButton.Ghost(text = "Trash", onClick = onTrash)
                    }
                    NotebookFilter.TRASH -> {
                        HaloGlassButton.Ghost(text = "Restore", onClick = onRestore)
                        HaloGlassButton.Danger(text = "Delete", onClick = onDelete)
                    }
                }
            }
        }
    }
}
