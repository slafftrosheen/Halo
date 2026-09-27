package com.halo.app.ui.screens.notebook

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.ui.design.HaloHeaderAction
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing
import com.halo.app.ui.presence.HaloSignalGlyph
import com.halo.app.ui.presence.HaloSignalKind

@Composable
fun NotebookScreen(viewModel: NotebookViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showWorkingSet by remember { mutableStateOf(false) }
    var showNewCollection by remember { mutableStateOf(false) }
    var showDeleteCollection by remember { mutableStateOf(false) }
    var collectionName by remember { mutableStateOf("") }
    if (showWorkingSet) {
        WorkingSetSheet(
            items = state.workingSetItems,
            onRemove = { viewModel.selectContext(it, false) },
            onClearAll = viewModel::clearContext,
            onDismiss = { showWorkingSet = false }
        )
    }
    if (showNewCollection) {
        AlertDialog(
            onDismissRequest = { showNewCollection = false },
            title = { Text("New collection") },
            text = {
                OutlinedTextField(
                    value = collectionName,
                    onValueChange = { collectionName = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = collectionName.isNotBlank(),
                    onClick = {
                        viewModel.createCollection(collectionName)
                        collectionName = ""
                        showNewCollection = false
                    },
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewCollection = false }) { Text("Cancel") } },
        )
    }
    if (showDeleteCollection) {
        val selectedName = state.collections.firstOrNull { it.id == state.selectedCollectionId }?.name
        AlertDialog(
            onDismissRequest = { showDeleteCollection = false },
            title = { Text("Delete collection?") },
            text = {
                Text("${selectedName ?: "This collection"} will be removed. Its notes and files will stay in Notebook.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteCollection = false
                    viewModel.deleteSelectedCollection()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCollection = false }) { Text("Cancel") }
            },
        )
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = HaloSpacing.screen)
    ) {
        Spacer(Modifier.height(16.dp))
        HaloScreenHeader("Notebook", "Your workspace",
            primaryAction = HaloHeaderAction("New", viewModel::createNote))
        TextButton(onClick = { showWorkingSet = true }) {
            Text("In Halo's context · ${state.workingSet.size}")
        }
        TextButton(onClick = viewModel::createCanvas) {
            Text("New canvas")
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search Notebook") }
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NotebookFilter.entries.forEach { filter ->
                FilterChip(selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(filter.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.selectedCollectionId == null,
                onClick = { viewModel.selectCollection(null) },
                label = { Text("All collections") },
            )
            state.collections.forEach { collection ->
                FilterChip(
                    selected = state.selectedCollectionId == collection.id,
                    onClick = { viewModel.selectCollection(collection.id) },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text(collection.name) },
                )
            }
            IconButton(onClick = { showNewCollection = true }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "Create collection")
            }
            if (state.selectedCollectionId != null) {
                IconButton(onClick = { showDeleteCollection = true }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete selected collection")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.items.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (state.query.isBlank()) "Nothing here yet" else "No matching items",
                    style = MaterialTheme.typography.titleMedium)
                if (state.query.isBlank() && state.filter == NotebookFilter.ALL) {
                    TextButton(onClick = viewModel::createNote) { Text("Create note") }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(state.items, key = { it.objectRef.id.value }) { item ->
                    WorkspaceObjectRow(
                        item = item,
                        selected = item.objectRef.id.value in state.workingSet,
                        favorite = item.objectRef.id.value in state.favorites,
                        collections = state.collections.map { it.id to it.name },
                        inSelectedCollection = item.objectRef.id.value in state.selectedCollectionMembers,
                        onOpen = { viewModel.openObject(item.objectRef) },
                        onToggleFavorite = { viewModel.toggleFavorite(item.objectRef.id.value) },
                        onAddToCollection = { viewModel.addToCollection(item.objectRef.id.value, it) },
                        onRemoveFromSelectedCollection = { viewModel.removeFromSelectedCollection(item.objectRef.id.value) },
                        onToggleContext = { viewModel.selectContext(item.objectRef.id.value,
                            item.objectRef.id.value !in state.workingSet) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceObjectRow(
    item: NotebookWorkspaceItem,
    selected: Boolean,
    favorite: Boolean,
    collections: List<Pair<String, String>>,
    inSelectedCollection: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToCollection: (String) -> Unit,
    onRemoveFromSelectedCollection: () -> Unit,
    onToggleContext: () -> Unit
) {
    val row = item.objectRef
    var menuExpanded by remember(row.id.value) { mutableStateOf(false) }
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            HaloSignalGlyph(
                kind = signalKind(row.type),
                size = 38.dp,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(row.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = item.snippet?.takeIf { it.isNotBlank() }
                    ?: row.type.name.lowercase().replaceFirstChar { it.uppercase() }
                Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (row.inInbox) Text("Inbox", style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(DateUtils.getRelativeTimeSpanString(row.updatedAt,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (favorite) "Remove favorite" else "Add favorite",
                            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Organize")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (selected) "Remove from Halo context" else "Add to Halo context") },
                                onClick = { menuExpanded = false; onToggleContext() },
                            )
                            if (inSelectedCollection) {
                                DropdownMenuItem(
                                    text = { Text("Remove from this collection") },
                                    onClick = { menuExpanded = false; onRemoveFromSelectedCollection() },
                                )
                            }
                            collections.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text("Add to $name") },
                                    onClick = { menuExpanded = false; onAddToCollection(id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun signalKind(type: WorkspaceObjectType): HaloSignalKind = when (type) {
    WorkspaceObjectType.NOTE -> HaloSignalKind.NOTE
    WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA -> HaloSignalKind.FILE
    WorkspaceObjectType.CONVERSATION, WorkspaceObjectType.ARTIFACT -> HaloSignalKind.ACTION
    WorkspaceObjectType.MEMORY -> HaloSignalKind.MEMORY
}
