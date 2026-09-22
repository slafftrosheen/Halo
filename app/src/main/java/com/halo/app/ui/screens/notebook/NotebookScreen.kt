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

@Composable
fun NotebookScreen(viewModel: NotebookViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showWorkingSet by remember { mutableStateOf(false) }
    if (showWorkingSet) {
        WorkingSetSheet(
            items = state.workingSetItems,
            onRemove = { viewModel.selectContext(it, false) },
            onClearAll = viewModel::clearContext,
            onDismiss = { showWorkingSet = false }
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
                        onOpen = { viewModel.openObject(item.objectRef) },
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
    onOpen: () -> Unit,
    onToggleContext: () -> Unit
) {
    val row = item.objectRef
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(typeMark(row.type), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 14.dp))
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
                TextButton(onClick = onToggleContext) {
                    Text(if (selected) "Remove" else "Add to context")
                }
            }
        }
    }
}

private fun typeMark(type: WorkspaceObjectType): String = when (type) {
    WorkspaceObjectType.NOTE -> "N"
    WorkspaceObjectType.FILE -> "F"
    WorkspaceObjectType.CONVERSATION -> "C"
    WorkspaceObjectType.MEMORY -> "M"
    WorkspaceObjectType.MEDIA -> "•"
    WorkspaceObjectType.ARTIFACT -> "A"
}
