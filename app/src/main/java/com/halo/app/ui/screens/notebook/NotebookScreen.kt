package com.halo.app.ui.screens.notebook

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing

private val primaryFilters = listOf(
    NotebookFilter.ALL,
    NotebookFilter.FILES,
    NotebookFilter.NOTES,
    NotebookFilter.CHATS,
    NotebookFilter.CANVASES,
)
private val secondaryFilters = NotebookFilter.entries.filterNot(primaryFilters::contains)

@Composable
fun NotebookScreen(viewModel: NotebookViewModel, onOpenStudy: () -> Unit) {
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshContextPolicy()
        onPauseOrDispose {}
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cloudAllowed by viewModel.cloudSharing.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val mutating by viewModel.isMutating.collectAsStateWithLifecycle()
    var selecting by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var batchMenu by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    val visibleItems = remember(state.items, state.filter) {
        state.items.filterNot { it.objectRef.id.value.startsWith("learning:") || state.filter == NotebookFilter.MEMORY }
    }
    val collectionChoices = remember(state.collections) { state.collections.map { it.id to it.name } }
    var showWorkingSet by remember { mutableStateOf(false) }
    var showNewCollection by remember { mutableStateOf(false) }
    var showDeleteCollection by remember { mutableStateOf(false) }
    var collectionName by remember { mutableStateOf("") }
    var showInboxCapture by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (showInboxCapture) InboxCaptureDialog { showInboxCapture = false }

    if (confirmTrash) AlertDialog(onDismissRequest = { confirmTrash = false },
        title = { Text("Move ${selectedIds.size} items to trash?") }, text = { Text(uiText("You can restore them from the Trash filter.")) },
        confirmButton = { TextButton(onClick = { confirmTrash = false; viewModel.applyToSelection(NotebookBatchAction.TRASH) }) { Text(uiText("Move to trash")) } },
        dismissButton = { TextButton(onClick = { confirmTrash = false }) { Text(uiText("Cancel")) } })

    if (showWorkingSet) {
        WorkingSetSheet(
            items = state.workingSetItems,
            onRemove = { viewModel.selectContext(it, false) },
            onClearAll = viewModel::clearContext,
            onDismiss = { showWorkingSet = false },
            estimatedTokens = state.workingSetTokens,
            cloudAllowed = cloudAllowed,
            remoteAllowed = viewModel::remoteAllowed,
            onOpen = { showWorkingSet = false; viewModel.openObject(it) },
        )
    }
    if (showNewCollection) {
        AlertDialog(
            onDismissRequest = { showNewCollection = false },
            title = { Text(uiText("New collection")) },
            text = {
                OutlinedTextField(
                    value = collectionName,
                    onValueChange = { collectionName = it.take(60) },
                    singleLine = true,
                    label = { Text(uiText("Collection name")) },
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
                ) { Text(uiText("Create")) }
            },
            dismissButton = { TextButton(onClick = { showNewCollection = false }) { Text(uiText("Cancel")) } },
        )
    }
    if (showDeleteCollection) {
        val selectedName = state.collections.firstOrNull { it.id == state.selectedCollectionId }?.name
        AlertDialog(
            onDismissRequest = { showDeleteCollection = false },
            title = { Text(uiText("Delete collection?")) },
            text = { Text("${selectedName ?: "This collection"} will be removed. Its items stay in Workspace.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteCollection = false
                    viewModel.deleteSelectedCollection()
                }) { Text(uiText("Delete")) }
            },
            dismissButton = { TextButton(onClick = { showDeleteCollection = false }) { Text(uiText("Cancel")) } },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = HaloSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            HaloScreenHeader(
                title = uiText("Workspace"),
                subtitle = uiText("Tools for your ideas, with everything in one place"),
            )
        }

        item(key = "toolbox") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.halo.app.ui.components.HaloActionButton(onClick = { showInboxCapture = true }, modifier = Modifier.fillMaxWidth()) { Text(uiText("Save to Inbox")) }
                WorkspaceTool(
                    "Notebook", "Write, organize and improve your notes", Icons.Rounded.NoteAlt,
                    onOpen = { viewModel.setFilter(NotebookFilter.NOTES) },
                    actionLabel = "New note", onAction = viewModel::createNote,
                )
                WorkspaceTool(
                    "Canvas", "Connect ideas and create with AI", Icons.Rounded.DashboardCustomize,
                    onOpen = { viewModel.setFilter(NotebookFilter.CANVASES) },
                    actionLabel = "New canvas", onAction = viewModel::createCanvas,
                )
                WorkspaceTool(
                    "Memory", "What Halo remembers and learns", Icons.Rounded.Psychology,
                    onOpen = { viewModel.openMemoryHub() },
                )
                WorkspaceTool(
                    "Study", "Explore a topic, continue a lesson or review", Icons.Rounded.AutoStories,
                    onOpen = onOpenStudy,
                )
            }
        }

        item(key = "library-heading") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Your library"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showWorkingSet = true }) { Text("Context (${state.workingSet.size})") }
            }
        }

        item(key = "organize") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { sortMenu = true }) { Text(state.sort.label) }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        NotebookSort.entries.forEach { sort -> DropdownMenuItem(text = { Text(sort.label) }, onClick = { viewModel.setSort(sort); sortMenu = false }) }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { selecting = !selecting; viewModel.clearSelection() }) { Text(if (selecting) "Done" else "Select") }
            }
            if (selecting) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${selectedIds.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::selectVisible, enabled = !mutating) { Text(uiText("Select visible")) }
                Box {
                    TextButton(onClick = { batchMenu = true }, enabled = selectedIds.isNotEmpty() && !mutating) { Text(if (mutating) "Updating..." else "Actions") }
                    DropdownMenu(batchMenu, { batchMenu = false }) {
                        if (state.filter !in listOf(NotebookFilter.TRASH, NotebookFilter.ARCHIVED)) {
                            DropdownMenuItem(text = { Text(uiText("Add to Halo context")) }, onClick = { batchMenu = false; viewModel.applyToSelection(NotebookBatchAction.CONTEXT) })
                            DropdownMenuItem(text = { Text(uiText("Archive")) }, onClick = { batchMenu = false; viewModel.applyToSelection(NotebookBatchAction.ARCHIVE) })
                        } else DropdownMenuItem(text = { Text(uiText("Restore")) }, onClick = { batchMenu = false; viewModel.applyToSelection(NotebookBatchAction.RESTORE) })
                        if (state.filter != NotebookFilter.TRASH) DropdownMenuItem(text = { Text(uiText("Move to trash")) }, onClick = { batchMenu = false; confirmTrash = true })
                        state.collections.forEach { collection -> DropdownMenuItem(text = { Text("Add to ${collection.name}") }, onClick = {
                            batchMenu = false; viewModel.applyToSelection(NotebookBatchAction.COLLECTION, collection.id)
                        }) }
                    }
                }
            }
            notice?.let { message -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::clearNotice) { Text(uiText("Dismiss")) }
            } }
        }

        if (state.duePractice.isNotEmpty()) {
            item(key = "due-review") {
                val due = state.duePractice.first()
                Surface(
                    onClick = { viewModel.openStudy(due.sessionId, review = true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoStories, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Review ${state.duePractice.size} card${if (state.duePractice.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                due.question,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(uiText("Start"), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        item(key = "library-controls") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        placeholder = { Text(uiText("Search your workspace")) },
                        shape = RoundedCornerShape(14.dp),
                    )
                    FilterBar(state.filter, viewModel::setFilter)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CollectionBar(
                        state = state,
                        onSelect = viewModel::selectCollection,
                        onCreate = { showNewCollection = true },
                        onDelete = { showDeleteCollection = true },
                    )
                }
            }
        }
        if(state.filter in listOf(NotebookFilter.MEMORY,NotebookFilter.INBOX)) item(key="personal-memory") {
            PersonalMemorySection(viewModel,inboxOnly=state.filter == NotebookFilter.INBOX)
        }
        item(key = "library-title") {
            val location = state.collections.firstOrNull { it.id == state.selectedCollectionId }?.name ?: state.filter.label
            SectionTitle(location, "${state.items.size} item${if (state.items.size == 1) "" else "s"}")
        }

        if (!state.isLoaded) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            }
        } else if (state.items.isEmpty()) {
            item(key = "empty") {
                WorkspaceEmptyState(state.query.isNotBlank(), viewModel::createNote)
            }
        } else {
            items(visibleItems, key = { it.objectRef.id.value }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) androidx.compose.material3.Checkbox(checked = item.objectRef.id.value in selectedIds,
                    enabled = !mutating, onCheckedChange = { viewModel.toggleSelection(item.objectRef.id.value) })
                WorkspaceObjectRow(
                    item = item,
                    selected = item.objectRef.id.value in state.workingSet,
                    favorite = item.objectRef.id.value in state.favorites,
                    collections = collectionChoices,
                    inSelectedCollection = item.objectRef.id.value in state.selectedCollectionMembers,
                    onOpen = { if (selecting) viewModel.toggleSelection(item.objectRef.id.value) else viewModel.openSearchItem(item) },
                    onStudy = { viewModel.startStudy(item.objectRef.id.value) },
                    onToggleFavorite = { viewModel.toggleFavorite(item.objectRef.id.value) },
                    onAddToCollection = { viewModel.addToCollection(item.objectRef.id.value, it) },
                    onRemoveFromSelectedCollection = { viewModel.removeFromSelectedCollection(item.objectRef.id.value) },
                    onToggleContext = {
                        viewModel.selectContext(item.objectRef.id.value, item.objectRef.id.value !in state.workingSet)
                    },
                )
                }
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WorkspaceToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 112.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (emphasized) 2.dp else 0.dp,
        border = BorderStroke(
            1.dp,
            if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FilterBar(selected: NotebookFilter, onSelect: (NotebookFilter) -> Unit) {
    var moreExpanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        primaryFilters.forEach { filter ->
            FilterChip(selected == filter, { onSelect(filter) }, label = { Text(filter.label) })
        }
        Box {
            FilterChip(
                selected = selected in secondaryFilters,
                onClick = { moreExpanded = true },
                label = { Text(if (selected in secondaryFilters) selected.label else "More") },
            )
            DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                secondaryFilters.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(filter.label) },
                        onClick = { moreExpanded = false; onSelect(filter) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionBar(
    state: NotebookUiState,
    onSelect: (String?) -> Unit,
    onCreate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.selectedCollectionId == null,
            onClick = { onSelect(null) },
            leadingIcon = { Icon(Icons.Rounded.Inventory2, null, Modifier.size(18.dp)) },
            label = { Text(uiText("Library")) },
        )
        state.collections.forEach { collection ->
            FilterChip(
                selected = state.selectedCollectionId == collection.id,
                onClick = { onSelect(collection.id) },
                leadingIcon = { Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp)) },
                label = { Text(collection.name) },
            )
        }
        IconButton(onClick = onCreate) { Icon(Icons.Rounded.CreateNewFolder, "Create collection") }
        if (state.selectedCollectionId != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Delete selected collection") }
        }
    }
}

@Composable
private fun WorkspaceEmptyState(searching: Boolean, onCreateNote: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (searching) Icons.Rounded.Search else Icons.Rounded.Inventory2,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(uiText(if (searching) "No matches" else "Your workspace is empty"), style = MaterialTheme.typography.titleMedium)
        Text(
            uiText(if (searching) "Try another name or content phrase." else "Create a note or import a file to begin."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!searching) TextButton(onClick = onCreateNote) { Text(uiText("Create note")) }
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
    onStudy: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToCollection: (String) -> Unit,
    onRemoveFromSelectedCollection: () -> Unit,
    onToggleContext: () -> Unit,
) {
    val row = item.objectRef
    var menuExpanded by remember(row.id.value) { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(workspaceIcon(row), null, Modifier.size(23.dp), MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = item.snippet?.takeIf { it.isNotBlank() } ?: workspaceTypeLabel(row)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DateUtils.getRelativeTimeSpanString(row.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (row.inInbox) MetadataLabel("Inbox")
                    if (selected) MetadataLabel("In context")
                    if (favorite) Icon(Icons.Rounded.Star, "Favorite", Modifier.size(14.dp), MaterialTheme.colorScheme.primary)
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, "Organize ${row.title.ifBlank { "item" }}")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (row.type == com.halo.app.data.notebook.WorkspaceObjectType.NOTE && row.lifecycle == com.halo.app.data.notebook.WorkspaceObjectLifecycle.ACTIVE) {
                        DropdownMenuItem(text = { Text(uiText("Study this note")) }, onClick = { menuExpanded = false; onStudy() })
                    }

                    DropdownMenuItem(
                        leadingIcon = { Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, null) },
                        text = { Text(if (favorite) "Remove favorite" else "Add favorite") },
                        onClick = { menuExpanded = false; onToggleFavorite() },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.Psychology, null) },
                        text = { Text(if (selected) "Remove from Halo context" else "Add to Halo context") },
                        onClick = { menuExpanded = false; onToggleContext() },
                    )
                    if (inSelectedCollection) {
                        DropdownMenuItem(
                            text = { Text(uiText("Remove from this collection")) },
                            onClick = { menuExpanded = false; onRemoveFromSelectedCollection() },
                        )
                    }
                    collections.forEach { (id, name) ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Rounded.Folder, null) },
                            text = { Text("Add to $name") },
                            onClick = { menuExpanded = false; onAddToCollection(id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
}

private fun workspaceIcon(row: WorkspaceObject): ImageVector = when (row.type) {
    WorkspaceObjectType.NOTE -> Icons.Rounded.NoteAlt
    WorkspaceObjectType.CANVAS -> Icons.Rounded.DashboardCustomize
    WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA -> Icons.Rounded.Description
    WorkspaceObjectType.CONVERSATION -> Icons.Rounded.Forum
    WorkspaceObjectType.MEMORY -> Icons.Rounded.Psychology
    WorkspaceObjectType.ARTIFACT -> if (row.contentRef.startsWith("canvas:")) Icons.Rounded.DashboardCustomize else Icons.Rounded.Description
}

private fun workspaceTypeLabel(row: WorkspaceObject): String = when (row.type) {
    WorkspaceObjectType.NOTE -> "Note"
    WorkspaceObjectType.CANVAS -> "Canvas"
    WorkspaceObjectType.FILE -> "File"
    WorkspaceObjectType.MEDIA -> "Media"
    WorkspaceObjectType.CONVERSATION -> "Saved chat"
    WorkspaceObjectType.MEMORY -> "Memory"
    WorkspaceObjectType.ARTIFACT -> if (row.contentRef.startsWith("canvas:")) "Canvas" else "Artifact"
}

@Composable
private fun WorkspaceTool(
    title: String,
    description: String,
    icon: ImageVector,
    onOpen: () -> Unit,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
