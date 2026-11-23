package com.halo.app.ui.screens.notebook

import com.halo.app.ui.zone6.Choose
import com.halo.app.ui.zone6.WORKSPACE_DOCK_DEFAULT
import com.halo.app.ui.zone6.WORKSPACE_DOCK_KEY
import com.halo.app.ui.zone6.editZone6PageOnHold
import com.halo.app.ui.zone6.rememberZone6PageSetting

import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner

import androidx.compose.runtime.LaunchedEffect

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloVoiceTextActions
import androidx.activity.compose.BackHandler
import com.halo.app.R
import androidx.compose.ui.res.stringResource

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.notebook.NotebookCollection
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing
import com.halo.app.ui.design.HaloEmptyState
import com.halo.app.ui.design.HaloFocusAction
import com.halo.app.ui.design.HaloHeaderAction
import com.halo.app.ui.design.HaloProgressPresentation
import androidx.compose.runtime.saveable.rememberSaveable
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.nativeactions.PrivateLibraryHandoff
import com.halo.app.nativeactions.PrivateLibraryTarget
import kotlinx.coroutines.flow.first

private val primaryFilters = listOf(
    NotebookFilter.ALL,
    NotebookFilter.NOTES,
    NotebookFilter.PHOTOS,
    NotebookFilter.FILES,
    NotebookFilter.SCANS,
    NotebookFilter.CARDS,
    NotebookFilter.PLACES,
    NotebookFilter.RECORDINGS,
    NotebookFilter.ORGANIZER,
    NotebookFilter.PEOPLE,
)
private val secondaryFilters = NotebookFilter.entries.filterNot {
    it in primaryFilters || it == NotebookFilter.GLASSES_PHOTOS
}

@Composable
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
fun NotebookScreen(viewModel: NotebookViewModel, onOpenStudy: () -> Unit, onSkills: () -> Unit = {},
    onOpenModels: () -> Unit = {}, requestLocalSearch: Boolean = false, onLocalSearchOpened: () -> Unit = {},
    onOpenStudio: (() -> Unit)? = null, onOpenFileTools: (() -> Unit)? = null, onStartVoiceMode: () -> Unit = {}, librarySession: NotebookLibrarySession? = null, rootRequest: Long = 0L,
    onOpenCanvasLibrary: (() -> Unit)? = null, onOpenEveryday: ((String)->Unit)? = null,
    onOpenLibraryPlace: ((String) -> Unit)? = null, homeWallpaperVisible: Boolean = false) {
    var returnItem by remember { mutableStateOf<String?>(null) }
    val viewBinding by viewModel.viewBinding.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel to viewBinding) {
        viewModel.refreshContextPolicy()
        returnItem = viewModel.pendingReturnItem()
        onPauseOrDispose {}
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val queryText by viewModel.queryText.collectAsStateWithLifecycle()
    val pendingMemoryReviews by viewModel.pendingMemoryReviewCount.collectAsStateWithLifecycle()
    val cloudAllowed by viewModel.cloudSharing.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val batchNotice by viewModel.batchNotice.collectAsStateWithLifecycle()
    val mutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val canUseSelection = !mutating && state.isLoaded && !state.loadError
    val trashUndoIds by viewModel.trashUndoIds.collectAsStateWithLifecycle()
    var selecting by rememberSaveable { mutableStateOf(false) }
    val shellHosted = com.halo.app.ui.presence.LocalShellHaloOrb.current
    val workspaceBackdrop = if (homeWallpaperVisible) Modifier
        else Modifier.background(com.halo.app.ui.theme.haloWorkspaceBackground())
    var workspaceView by rememberSaveable { mutableStateOf("library") }
    val showLibrary = workspaceView == "library"
    val easy = com.halo.app.ui.theme.rememberVisualPreferences().viewMode == com.halo.app.ui.theme.HaloViewMode.EASY
    val libraryViewSetting = rememberZone6PageSetting("library.view")
    val libraryDefault = if (easy) "pictures" else "list"
    val workspaceSlot = rememberZone6PageSetting(WORKSPACE_DOCK_KEY)
    var pageEditPreview by remember(workspaceView) { mutableStateOf<String?>(null) }
    val setting = if (showLibrary) libraryViewSetting else workspaceSlot
    val defaultView = if (showLibrary) libraryDefault else WORKSPACE_DOCK_DEFAULT
    val pageView = pageEditPreview ?: setting.value ?: defaultView
    val editPage = { pageEditPreview = pageView }
    val editPageLabel = stringResource(R.string.page_layout_edit)
    val deviceLabel = uiText("Workspace")
    val slotChoices = listOf(WORKSPACE_DOCK_DEFAULT to deviceLabel) +
        com.halo.app.ui.shell.ShellOrbCatalog.workspaceDestinations(false).map { it.route to uiText(it.title) }
    if (pageEditPreview != null) {
        val choices = if (showLibrary) listOf("list" to stringResource(R.string.page_library_list),
            "pictures" to stringResource(R.string.page_library_pictures)) else slotChoices
        setting.Choose(defaultView, stringResource(if (showLibrary) R.string.page_library_layout else R.string.page_workspace_slot),
            choices, pageView, { pageEditPreview = it }, { pageEditPreview = null })
    }
    val libraryList = androidx.compose.foundation.lazy.rememberLazyListState()
    val toolsList = androidx.compose.foundation.lazy.rememberLazyListState()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val selectView: (Boolean) -> Unit = { library ->
        focusManager.clearFocus(); workspaceView = if (library) "library" else "tools"
    }
    var consumedRootRequest by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(rootRequest) {
        if (rootRequest != consumedRootRequest) {
            workspaceView = "library"
            consumedRootRequest = rootRequest
        }
    }
    LaunchedEffect(selectedIds) { if (selectedIds.isNotEmpty()) selecting = true }
    var sortMenu by remember { mutableStateOf(false) }
    var batchMenu by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var namingItem by remember { mutableStateOf<NotebookWorkspaceItem?>(null) }
    val openPrivate: ((PrivateLibraryTarget) -> Unit)? = onOpenEveryday?.let { navigate ->
        { target -> PrivateLibraryHandoff.offer(target); navigate("vault") }
    }
    val libraryOverview = state.filter == NotebookFilter.ALL && state.selectedCollectionId == null && queryText.isBlank() && !selecting
    val groupSearchResults = state.groupsLibrarySearch()
    val visibleItems = remember(state.items, state.filter, groupSearchResults) { state.visibleLibraryObjects() }
    LaunchedEffect(returnItem, state, visibleItems, showLibrary) {
        val target = returnItem ?: return@LaunchedEffect
        if (!showLibrary || !state.isLoaded || state.loadError || !viewModel.isCurrentView(state)) return@LaunchedEffect
        val index = visibleItems.indexOfFirst { it.objectRef.id.value == target }
        if (index >= 0) {
            androidx.compose.runtime.withFrameNanos { }
            // Header rows vary with Inbox/standalone/Undo. Actual item count locates the keyed result block.
            val prefix = (libraryList.layoutInfo.totalItemsCount - visibleItems.size - 1).coerceAtLeast(0)
            libraryList.scrollToItem(prefix + index)
        }
        viewModel.consumedReturnItem(target)
        returnItem = null
    }
    val collectionChoices = remember(state.collections) { state.collections.map { it.id to it.name } }
    var showWorkingSet by rememberSaveable { mutableStateOf(false) }
    var showNewCollection by rememberSaveable { mutableStateOf(false) }
    var showDeleteCollection by remember { mutableStateOf(false) }
    var collectionName by rememberSaveable { mutableStateOf("") }
    var showInboxCapture by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var importFile by rememberSaveable { mutableStateOf(false) }
    var showLocalSearch by rememberSaveable { mutableStateOf(false) }
    var toolkitRequest by remember { mutableStateOf<com.halo.app.ui.toolkit.ToolkitShelfRequest?>(null) }
    BindNotebookLibrary(librarySession, viewModel, libraryVisible = showLibrary && !sortMenu && !batchMenu &&
        !confirmTrash && !showWorkingSet && !showNewCollection && !showDeleteCollection && !showInboxCapture && !showLocalSearch &&
        pageEditPreview == null)
    val libraryActions = when {
        !showLibrary -> listOf(
            ShellPageAction("page.notebook.scan", stringResource(R.string.toolkit_scan), HaloGlyph.CAMERA,
                onOpenEveryday != null) { onOpenEveryday?.invoke("scan") },
            ShellPageAction("page.notebook.find-tool", stringResource(R.string.toolkit_find_tool), HaloGlyph.SEARCH) {
                toolkitRequest = com.halo.app.ui.toolkit.ToolkitShelfRequest.FIND
            },
            ShellPageAction("page.notebook.edit-shelf", stringResource(R.string.toolkit_edit_shelf), HaloGlyph.SETTINGS) {
                toolkitRequest = com.halo.app.ui.toolkit.ToolkitShelfRequest.EDIT
            },
        )
        selecting -> listOf(
            ShellPageAction("page.notebook.select-visible", "Select visible", HaloGlyph.NOTE,
                canUseSelection && visibleItems.isNotEmpty()) { viewModel.selectVisible() },
            ShellPageAction("page.notebook.selection-actions", "Actions", HaloGlyph.TOOL,
                canUseSelection && selectedIds.isNotEmpty()) { batchMenu = true },
            ShellPageAction("page.notebook.selection-done", "Done", HaloGlyph.SUCCESS) { selecting = false; viewModel.clearSelection() },
        )
        else -> listOf(
            ShellPageAction("page.notebook.new-note", "New note", HaloGlyph.NOTE, !mutating) { viewModel.createNote() },
            ShellPageAction("page.notebook.import", "Import", HaloGlyph.TOOL, !mutating) { importFile = true; showInboxCapture = true },
            ShellPageAction("page.notebook.organize", stringResource(R.string.zone5_organize), HaloGlyph.TOOL,
                canUseSelection) { selecting = true },
            ShellPageAction("page.notebook.capture", "Quick capture", HaloGlyph.NOTE, !mutating) { showInboxCapture = true },
            ShellPageAction("page.notebook.related", "Find related notes", HaloGlyph.SEARCH) { showLocalSearch = true },
            ShellPageAction("page.notebook.context", "In chat", HaloGlyph.NOTE) { showWorkingSet = true },
            ShellPageAction("page.notebook.chats", "Saved chats", HaloGlyph.NOTE) { viewModel.setFilter(NotebookFilter.CHATS) },
            ShellPageAction("page.notebook.inbox", "Inbox", HaloGlyph.NOTE) { viewModel.setFilter(NotebookFilter.INBOX) },
        ) + listOfNotNull(
            if (onOpenEveryday != null) ShellPageAction("page.notebook.private-papers",
                stringResource(R.string.halo11_private_library_items), HaloGlyph.SECURITY) { onOpenEveryday("vault") } else null,
            state.duePractice.firstOrNull()?.let { due -> ShellPageAction("page.notebook.review", "Review cards", HaloGlyph.QUESTION) { viewModel.openStudy(due.sessionId, review = true) } },
            state.studySessions.firstOrNull()?.let { session -> ShellPageAction("page.notebook.continue", "Continue learning", HaloGlyph.QUESTION) { viewModel.openStudy(session.id) } },
            if (pendingMemoryReviews > 0) ShellPageAction("page.notebook.memory-review", "Memory suggestions", HaloGlyph.MEMORY) { viewModel.openMemoryHub("Suggestions") } else null,
        )
    }
    BindShellPageActions(ShellPageOwner("notebook", sourceIdentity = "${state.filter}:${state.selectedCollectionId}:$queryText:${state.resolvedQuery}:${state.sort}:$workspaceView:$selecting:${selectedIds.sorted()}",
        helpDescription = stringResource(R.string.zone5_help_library),
        guidanceBlocked = mutating || state.loadError || sortMenu || batchMenu || showInboxCapture || showLocalSearch || selecting,
        completedGuideIds = if (state.isLoaded && !state.loadError && state.items.any { it.objectRef.type == WorkspaceObjectType.NOTE }) setOf("library-new.v1") else emptySet(),
        helpSteps = listOf(com.halo.app.ui.presence.ShellGuideStep("library-new.v1", "page.notebook.new-note", stringResource(R.string.zone5_guide_library),
            state.isLoaded && !state.loadError && state.items.any { it.objectRef.type == WorkspaceObjectType.NOTE })),
        nextStep = if (showLibrary && state.isLoaded && !state.loadError && !mutating && state.items.isEmpty() &&
            queryText.isBlank() && state.filter == NotebookFilter.ALL && !selecting && !showInboxCapture && !showLocalSearch)
            com.halo.app.ui.presence.ShellNextStep("library-new.v1", "page.notebook.new-note", stringResource(R.string.zone5_guide_library)) else null,
        selectionIds = if (showLibrary) selectedIds.sorted() else emptyList(), activeToolId = workspaceView,
        canonicalToolIds = if (!showLibrary) listOf("halo_list_screens", "halo_open_screen") else listOf("halo_library_view", "halo_library_set_view", "halo_library_update_item_or_suggestion", "halo_library_organization", "halo_library_organize",
            "halo_library_batch", "halo_library_apply_batch", "halo_note_get", "halo_note_purge_status",
            "halo_note_purge", "halo_note_purge_retry"),
        defaultDisabledActionReason = stringResource(when {
            mutating -> R.string.action_wait
            !state.isLoaded -> R.string.action_loading
            else -> R.string.zone5_refresh_first
        }),
        disabledActionReasons = buildMap {
            put("page.notebook.view-library", stringResource(R.string.action_current_view))
            put("page.notebook.view-tools", stringResource(R.string.action_current_view))
            if (canUseSelection) {
                put("page.notebook.select-visible", uiText("Nothing in this view"))
                put("page.notebook.selection-actions", stringResource(R.string.zone5_select_first))
            }
        }), libraryActions + listOf(
                ShellPageAction("page.notebook.layout", editPageLabel, HaloGlyph.SETTINGS, !selecting && !mutating, editPage),
                ShellPageAction("page.notebook.view-library", "Library", HaloGlyph.FILE, !showLibrary) { selectView(true) },
                ShellPageAction("page.notebook.view-tools", "Tools", HaloGlyph.TOOL, showLibrary) { selectView(false) }))
    BackHandler(enabled = !showLibrary) { workspaceView = "library" }
    LaunchedEffect(requestLocalSearch) {
        if (requestLocalSearch) { workspaceView = "library"; showLocalSearch = true; onLocalSearchOpened() }
    }
    val zoneTabs = shellHosted && com.halo.app.ui.presence.BindShellUtilityBand(ShellPageOwner("notebook", activeToolId = workspaceView)) {
        WorkspaceSectionSwitcher(showLibrary, selectView)
    }
    if (!showLibrary) {
        LazyColumn(state = toolsList, modifier = Modifier.fillMaxSize()
            .then(workspaceBackdrop).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (!zoneTabs) item("workspace-heading") { WorkspaceHeadingAndTabs(false, selectView, editPage) }
            item("workspace-tools") {
                WorkspaceToolsList(onOpenStudy, { viewModel.openMemoryHub() }, onOpenStudio, onOpenFileTools,
                    onOpenCanvasLibrary, onOpenEveryday, easy = easy, onEdit = editPage,
                    toolkitRequest = toolkitRequest, onToolkitRequestHandled = { toolkitRequest = null })
                TextButton(onClick = editPage, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.page_workspace_slot))
                }
            }
            item("workspace-bottom") { Spacer(Modifier.height(12.dp)) }
        }
        return
    }
    if (showInboxCapture) InboxCaptureDialog(initialImport = importFile) { showInboxCapture = false; importFile = false }
    if (showLocalSearch) NotebookLocalSearchSheet(
        viewModel = viewModel, items = state.items, studySessions = state.studySessions,
        onOpenModels = onOpenModels, onDismiss = { showLocalSearch = false },
    )
    if (batchMenu) com.halo.app.ui.presence.LocalizedMaterialBottomSheet(
        onDismissRequest = { batchMenu = false },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText("%1\$d selected", selectedIds.size), style = MaterialTheme.typography.titleLarge)
            if (!canUseSelection) Text(if (mutating) uiText("Updating...") else stringResource(R.string.zone5_refresh_first),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            @Composable fun batchAction(label: String, invoke: () -> Unit) {
                TextButton(onClick = { batchMenu = false; invoke() }, enabled = canUseSelection && selectedIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText(label)) }
            }
            if (state.filter !in listOf(NotebookFilter.TRASH, NotebookFilter.ARCHIVED)) {
                batchAction("Add to Halo context") { viewModel.applyToSelection(NotebookBatchAction.CONTEXT) }
                batchAction("Archive") { viewModel.applyToSelection(NotebookBatchAction.ARCHIVE) }
            } else batchAction("Restore") { viewModel.applyToSelection(NotebookBatchAction.RESTORE) }
            if (state.filter != NotebookFilter.TRASH) batchAction("Move to trash") { confirmTrash = true }
            state.collections.forEach { collection ->
                TextButton(onClick = { batchMenu = false; viewModel.applyToSelection(NotebookBatchAction.COLLECTION, collection.id) },
                    enabled = canUseSelection && selectedIds.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(uiText("Add to %1\$s", collection.name))
                }
            }
            TextButton(onClick = { batchMenu = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Cancel")) }
        }
    }

    if (confirmTrash) AlertDialog(onDismissRequest = { confirmTrash = false },
        title = { Text(uiText("Move %1\$d items to trash?", selectedIds.size)) }, text = { Text(uiText("You can restore them from the Trash filter.")) },
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
                HaloTextField(
                    value = collectionName,
                    onValueChange = { collectionName = it.take(60) },
                    singleLine = true,
                    label = { Text(uiText("Collection name")) }, kind = VoiceFieldKind.SHORT, maxChars = 60)
            },
            confirmButton = {
                TextButton(
                    enabled = collectionName.isNotBlank() && !mutating,
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
            text = { Text(uiText("%1\$s will be removed. Its items stay in Workspace.", selectedName ?: uiText("This collection"))) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteCollection = false
                    viewModel.deleteSelectedCollection()
                }) { Text(uiText("Delete")) }
            },
            dismissButton = { TextButton(onClick = { showDeleteCollection = false }) { Text(uiText("Cancel")) } },
        )
    }

    namingItem?.let { item ->
        LibraryNameEditor(item,
            onSave = { title -> viewModel.renameLibraryItem(item, title); namingItem = null },
            onDismiss = { namingItem = null })
    }

    LazyColumn(
        state = libraryList,
        modifier = Modifier
            .fillMaxSize()
            .then(workspaceBackdrop)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!zoneTabs) item(key = "workspace-heading") { WorkspaceHeadingAndTabs(true, selectView, editPage) }
        if (!shellHosted) item(key = "standalone-actions") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                libraryActions.forEach { action -> TextButton(onClick = action.onInvoke, enabled = action.enabled) { Text(uiText(action.title)) } }
            }
        }
        if (showLibrary && onOpenEveryday != null) item(key = "pages-made") {
            TextButton(onClick = { onOpenEveryday("home/pages") }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(androidx.compose.ui.res.stringResource(R.string.adaptive_pages_you_made))
            }
        }
        if (showLibrary) item(key = "library-controls") {
            val colors = MaterialTheme.colorScheme
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                HaloTextField(
                    value = queryText,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(painterResource(R.drawable.halo_ic_search), contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.halo11_library_find), maxLines = 1,
                        overflow = TextOverflow.Ellipsis) },
                    inputContentDescription = uiText("Search your workspace"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceContainerLow,
                        unfocusedContainerColor = colors.surfaceContainerLow,
                        unfocusedBorderColor = colors.outlineVariant,
                    ),
                    shape = RoundedCornerShape(18.dp), kind = VoiceFieldKind.SEARCH)
                if (parseLibrarySearchPrompt(queryText).monthSaved != null) {
                    Text(stringResource(R.string.halo11_search_previous_month_saved), style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant)
                }
                FilterBar(state.filter, viewModel::setFilter)
                if (state.selectedCollectionId != null) {
                    CollectionBar(
                        state = state,
                        onSelect = viewModel::selectCollection,
                        onCreate = { showNewCollection = true },
                        onDelete = { showDeleteCollection = true },
                        onPrivate = openPrivate?.takeIf { !selecting && !mutating },
                    )
                }
            }
        }

        if (libraryOverview && state.isLoaded) {
            if (onOpenEveryday != null) item("library-private") {
                OutlinedButton(onClick = { onOpenEveryday("vault") }, enabled = !mutating,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    HaloIcon(HaloGlyph.SECURITY, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.halo11_library_private_papers))
                }
            }
            state.collectionSuggestion?.let { suggestion -> item("library-collection-suggestion") {
                LibraryCollectionSuggestionCard(suggestion, !mutating,
                    onAdd = { viewModel.addLibrarySuggestion(suggestion) },
                    onDismiss = { viewModel.dismissLibrarySuggestion(suggestion) })
            } }
            item("library-collections-header") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.halo11_library_collections), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { showNewCollection = true }, enabled = !mutating,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("New collection")) }
                }
            }
            items(state.collections, key = { "library:collection:${it.id}" }) { collection ->
                LibraryCollectionRow(collection) { viewModel.selectCollection(collection.id) }
            }
        }

        if (trashUndoIds.isNotEmpty()) item(key = "trash_undo") {
            Surface(modifier = Modifier.fillMaxWidth(),
                shape = com.halo.app.ui.design.HaloShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(uiText("%1\$d items moved to trash", trashUndoIds.size),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = viewModel::undoLastTrash, enabled = !mutating,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText("Undo"))
                    }
                    TextButton(onClick = viewModel::dismissTrashUndo, enabled = !mutating,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText("Dismiss"))
                    }
                }
            }
        }

        state.suggestionUndo?.let { receipt -> item("library-suggestion-undo") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.halo11_library_suggestion_saved), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { viewModel.undoLibrarySuggestion(receipt) }, enabled = !mutating) { Text(uiText("Undo")) }
            }
        } }

        if (showLibrary) item(key = "organize") {
            if (libraryOverview) {
                val title = if (state.sort == NotebookSort.UPDATED) R.string.halo11_library_recent else R.string.halo11_home_library
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            // NB01: the sort/select row must wrap when labels grow (200% font).
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    TextButton(onClick = { sortMenu = true },
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (state.sort == NotebookSort.TOOL) stringResource(R.string.page_library_by_tool) else uiText(state.sort.label))
                    }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        NotebookSort.entries.forEach { sort -> DropdownMenuItem(
                            text = { Text(if (sort == NotebookSort.TOOL) stringResource(R.string.page_library_by_tool) else uiText(sort.label)) },
                            onClick = { viewModel.setSort(sort); sortMenu = false }) }
                    }
                }
                TextButton(onClick = editPage, enabled = !selecting && !mutating,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (pageView == "pictures") R.string.page_library_pictures else R.string.page_library_list))
                }
                TextButton(onClick = { selecting = !selecting; viewModel.clearSelection() }, enabled = selecting || canUseSelection,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (selecting) uiText("Done") else stringResource(R.string.zone5_organize))
                }
            }
            if (selecting) FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(uiText("%1\$d selected", selectedIds.size), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::selectVisible, enabled = canUseSelection && visibleItems.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Select visible")) }
                Box {
                    TextButton(onClick = { batchMenu = true }, enabled = selectedIds.isNotEmpty() && canUseSelection) { Text(uiText(if (mutating) "Updating..." else "Actions")) }
                }
            }
            if (batchNotice != null || notice != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(batchNotice?.let { notebookBatchNotice(it) } ?: notebookNoticeText(notice.orEmpty()),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::clearNotice) { Text(uiText("Dismiss")) }
            }
        }

        if(showLibrary && state.filter in listOf(NotebookFilter.MEMORY,NotebookFilter.INBOX)) item(key="personal-memory") {
            PersonalMemorySection(viewModel,inboxOnly=state.filter == NotebookFilter.INBOX)
        }
        if (showLibrary && !libraryOverview) item(key = "library-title") {
            val location = state.collections.firstOrNull { it.id == state.selectedCollectionId }?.name
                ?: libraryFilterLabel(state.filter)
            val itemCount = visibleItems.size + state.everyday.size + state.libraryCards.size + state.libraryPeople.size
            SectionTitle(location, if (state.isLoaded) uiText("Items: %1\$d", itemCount) else "")
        }

        if (showLibrary && !selecting && state.libraryCards.isNotEmpty()) {
            if (state.filter != NotebookFilter.CARDS) item("library-cards-header") {
                SectionTitle(stringResource(R.string.halo11_library_cards), "")
            }
            items(state.libraryCards, key = { "library:study:${it.id}" }) { card ->
                val lockCard = openPrivate?.takeUnless { mutating }?.let { open ->
                    { open(PrivateLibraryTarget.StudyCard(card.id, card.sessionId, card.question,
                        card.createdAtEpochMs, card.lastReviewedAtEpochMs)) }
                }
                LibraryReferenceRow(card.question, HaloLibraryKind.CARD, R.string.halo11_library_open_study, lockCard) {
                    viewModel.openLibraryCard(card)
                }
            }
        }
        if (showLibrary && !selecting && state.libraryPeople.isNotEmpty()) {
            if (state.filter != NotebookFilter.PEOPLE) item("library-people-header") {
                SectionTitle(stringResource(R.string.halo11_library_people), "")
            }
            items(state.libraryPeople, key = { "library:person:${it.id}" }) { person ->
                val lockPerson = openPrivate?.takeUnless { mutating }?.let { open ->
                    { open(PrivateLibraryTarget.Person(person.id, person.name, person.photoId, person.photoVersion)) }
                }
                LibraryReferenceRow(person.name, HaloLibraryKind.PERSON, R.string.halo11_library_open_reference, lockPerson) {
                    viewModel.openLibraryPerson(person)
                }
            }
        }
        if (showLibrary && !selecting) items(state.everyday, key = { it.key }) { entry ->
            val lockPlan = openPrivate?.takeIf { entry.privateEligible && !mutating }?.let { open ->
                { open(PrivateLibraryTarget.Organizer(entry.id, entry.title, entry.version)) }
            }
            EverydayLibraryRow(entry, onPrivate = lockPlan) { viewModel.openEverydayItem(entry) }
        }
        if (showLibrary && state.librarySourcesError) item("library-connected-items-error") {
            Text(stringResource(R.string.halo11_library_sources_error), color = MaterialTheme.colorScheme.error)
            TextButton(viewModel::retryLibraryLoad) { Text(uiText("Try again")) }
        }
        if(showLibrary && state.everydayError) item("everyday-library-error") {
            Text(uiText("Everyday items could not be loaded. Your saved information is kept."),color=MaterialTheme.colorScheme.error)
            TextButton(viewModel::retryLibraryLoad) {Text(uiText("Try again"))}
        }
        if (state.loadError) item(key = "library-error") {
            Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.zone5_library_unavailable), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(if (state.isLoaded) R.string.zone5_library_stale else R.string.zone5_library_error),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = viewModel::retryLibraryLoad, enabled = !mutating,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Try again")) }
                }
            }
        }

        if (!state.isLoaded && !state.loadError) {
            item(key = "loading") {
                com.halo.app.ui.zone6.Zone6DelayedLoading(loading = true)
            }
        } else if (!state.loadError && visibleItems.isEmpty() && state.everyday.isEmpty() && state.libraryCards.isEmpty() &&
            state.libraryPeople.isEmpty() && !state.everydayError && !state.librarySourcesError &&
            state.filter !in listOf(NotebookFilter.MEMORY, NotebookFilter.INBOX)) {
            item(key = "empty") {
                if (state.query.isBlank() && state.filter == NotebookFilter.ALL && state.selectedCollectionId == null) {
                    com.halo.app.ui.zone6.Zone6RingEmptyState(
                        stringResource(R.string.z6_empty_library_t), stringResource(R.string.z6_empty_library_p), HaloGlyph.NOTE,
                        if (mutating) emptyList() else listOf(
                            com.halo.app.ui.zone6.Zone6Start(uiText("Start a note"), HaloGlyph.NOTE, viewModel::createNote),
                            com.halo.app.ui.zone6.Zone6Start(uiText("Import"), HaloGlyph.FILE) { importFile = true; showInboxCapture = true },
                            com.halo.app.ui.zone6.Zone6Start(uiText("Make a canvas"), HaloGlyph.TOOL, viewModel::createCanvas),
                        ),
                    )
                } else {
                NotebookEmptyGuidance(state.query, state.filter, state.selectedCollectionId != null, !mutating,
                    viewModel::createNote, viewModel::createCanvas) {
                    viewModel.setQuery(""); viewModel.setFilter(NotebookFilter.ALL); viewModel.selectCollection(null)
                }
                if (notebookEmptyAction(state.query, state.filter, state.selectedCollectionId != null) == NotebookEmptyAction.CREATE_NOTE) {
                    TextButton(onClick = { importFile = true; showInboxCapture = true }, enabled = !mutating,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        com.halo.app.ui.zone5.Zone5Icon("action_import", HaloGlyph.FILE, Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp)); Text(uiText("Import"))
                    }
                }
                }
            }
        } else if (showLibrary) {
            val groupStarts = if (groupSearchResults) visibleItems.distinctBy { it.libraryKind }.map { it.objectRef.id.value }.toSet() else emptySet()
            items(notebookItemsForSurface(visibleItems, showLibrary),
                key = { it.objectRef.id.value }) { item ->
                val itemKind = item.libraryKind
                if (itemKind != null && item.objectRef.id.value in groupStarts) {
                    SectionTitle(stringResource(itemKind.group), "")
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) androidx.compose.material3.Checkbox(checked = item.objectRef.id.value in selectedIds,
                    enabled = canUseSelection, onCheckedChange = { viewModel.toggleSelection(item.objectRef.id.value) })
                WorkspaceObjectRow(
                    item = item,
                    bigPictures = pageView == "pictures" && !selecting,
                    selected = item.objectRef.id.value in state.workingSet,
                    favorite = item.objectRef.id.value in state.favorites,
                    collections = collectionChoices,
                    inSelectedCollection = item.objectRef.id.value in state.selectedCollectionMembers,
                    onOpen = {
                        if (selecting) { if (canUseSelection) viewModel.toggleSelection(item.objectRef.id.value) }
                        else if (itemKind == HaloLibraryKind.PLACE && onOpenLibraryPlace != null &&
                            item.objectRef.lifecycle == com.halo.app.data.notebook.WorkspaceObjectLifecycle.ACTIVE) {
                            val id = item.objectRef.id.value
                            viewModel.rememberLibraryItemReturn(id)
                            onOpenLibraryPlace(id)
                        } else viewModel.openSearchItem(item)
                    },
                    onStudy = { viewModel.startStudy(item.objectRef.id.value) },
                    onToggleFavorite = { viewModel.toggleFavorite(item.objectRef.id.value) },
                    onAddToCollection = { viewModel.addToCollection(item.objectRef.id.value, it) },
                    onRemoveFromSelectedCollection = { viewModel.removeFromSelectedCollection(item.objectRef.id.value) },
                    onToggleContext = {
                        viewModel.selectContext(item.objectRef.id.value, item.objectRef.id.value !in state.workingSet)
                    },
                    onMultiSelect = if (canUseSelection) ({
                        if (!selecting) { selecting = true; viewModel.clearSelection() }
                        viewModel.toggleSelection(item.objectRef.id.value)
                    }) else null,
                    onRestore = if (item.objectRef.lifecycle ==
                        com.halo.app.data.notebook.WorkspaceObjectLifecycle.TRASHED && !mutating)
                        ({ viewModel.restore(item.objectRef.id.value) }) else null,
                    selectedCollectionName = state.collections.firstOrNull {
                        it.id == state.selectedCollectionId
                    }?.name,
                    onRename = if (!selecting && viewModel.libraryNamingEnabled && canRenameLibraryItem(item)) {
                        { namingItem = item }
                    } else null,
                    onPrivate = openPrivate?.takeIf { !selecting && !mutating && canProtectLibraryItem(item) }?.let { open ->
                        { open(PrivateLibraryTarget.Item(item.objectRef.id.value, item.objectRef.title, item.objectRef.updatedAt)) }
                    },
                )
                }
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(12.dp)) }
    }
}

/** Show one recent workspace item when Study continuation is present.
 * A second recent item is useful only when there is no learning session.
 * The full library remains unchanged.
 */
internal fun notebookRecentContinuationCount(hasStudySession: Boolean): Int =
    if (hasStudySession) 1 else 2

/** Study source admission remains authoritative in StudyUseCases.start().
 * Only active, supported Workspace *classes* offer a shortcut; a corrupt or
 * inaccessible PDF must still produce the backend's actual Unavailable reason.
 */
internal fun notebookStudySourceAction(
    type: WorkspaceObjectType,
    lifecycle: com.halo.app.data.notebook.WorkspaceObjectLifecycle,
): String? {
    if (lifecycle != com.halo.app.data.notebook.WorkspaceObjectLifecycle.ACTIVE) return null
    return when (type) {
        WorkspaceObjectType.NOTE -> "Study this note"
        WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA -> "Study this material"
        else -> null
    }
}

/** The hub previews the latest item; the full Library reuses the exact same source list. */
internal fun <T> notebookItemsForSurface(items: List<T>, library: Boolean): List<T> =
    if (library) items else items.take(1)

@Composable
private fun SectionTitle(title: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        border = BorderStroke(
            1.dp,
            if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = com.halo.app.ui.design.HaloShapes.control,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
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
    val palette = MaterialTheme.colorScheme
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = palette.surfaceContainerLow,
        selectedContainerColor = palette.primary.copy(alpha = .10f),
        selectedLabelColor = palette.primary,
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        primaryFilters.forEach { filter ->
            val photoFamily = filter == NotebookFilter.PHOTOS && selected == NotebookFilter.GLASSES_PHOTOS
            FilterChip(selected == filter || photoFamily, { onSelect(filter) },
                label = { Text(libraryFilterLabel(filter)) }, shape = RoundedCornerShape(12.dp), colors = chipColors)
        }
        Box {
            FilterChip(
                selected = selected in secondaryFilters,
                onClick = { moreExpanded = true },
                label = { Text(if (selected in secondaryFilters) libraryFilterLabel(selected) else uiText("More")) },
                shape = RoundedCornerShape(12.dp), colors = chipColors,
            )
            DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                secondaryFilters.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(libraryFilterLabel(filter)) },
                        onClick = { moreExpanded = false; onSelect(filter) },
                    )
                }
            }
        }
    }
    if (selected == NotebookFilter.PHOTOS || selected == NotebookFilter.GLASSES_PHOTOS) {
        FilterChip(selected == NotebookFilter.GLASSES_PHOTOS, {
            onSelect(if (selected == NotebookFilter.GLASSES_PHOTOS) NotebookFilter.PHOTOS else NotebookFilter.GLASSES_PHOTOS)
        }, label = { Text(stringResource(R.string.halo11_library_from_glasses)) })
    }
}

@Composable
private fun libraryFilterLabel(filter: NotebookFilter): String = when (filter) {
    NotebookFilter.GLASSES_PHOTOS -> stringResource(R.string.halo11_library_from_glasses)
    else -> filter.libraryKind()?.let { stringResource(it.group) } ?: uiText(filter.label)
}

@Composable
private fun LibraryReferenceRow(title: String, kind: HaloLibraryKind, action: Int,
    onPrivate: (() -> Unit)? = null, onOpen: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onOpen, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.weight(1f).heightIn(min = 78.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(kind.label) + " · " + stringResource(action),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onPrivate != null) {
            IconButton(onClick = onPrivate) {
                com.halo.app.ui.zone5.Zone5Icon("action_private", HaloGlyph.SECURITY, Modifier.size(24.dp),
                    contentDescription = stringResource(R.string.halo11_private_lock))
            }
        }
    }
}

@Composable
private fun LibraryCollectionRow(collection: NotebookCollection, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (tile, ink) = when (collection.sortOrder.mod(3)) {
        0 -> colors.primaryContainer to colors.onPrimaryContainer
        1 -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    Surface(onClick = onOpen, shape = com.halo.app.ui.design.HaloShapes.control, color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant), modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.small, color = tile, contentColor = ink, modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.halo_ic_collections), null, Modifier.size(18.dp))
                }
            }
            Text(collection.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall)
            Text(uiText("Items: %1\$d", collection.activeItems), style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun CollectionBar(
    state: NotebookUiState,
    onSelect: (String?) -> Unit,
    onCreate: () -> Unit,
    onDelete: () -> Unit,
    onPrivate: ((PrivateLibraryTarget) -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.selectedCollectionId == null,
            onClick = { onSelect(null) },
            leadingIcon = { Icon(painterResource(R.drawable.halo_ic_inbox), null, Modifier.size(18.dp)) },
            label = { Text(uiText("Library")) },
        )
        state.collections.forEach { collection ->
            FilterChip(
                selected = state.selectedCollectionId == collection.id,
                onClick = { onSelect(collection.id) },
                leadingIcon = { Icon(painterResource(R.drawable.halo_ic_collections), null, Modifier.size(18.dp)) },
                label = { Text(collection.name) },
            )
        }
        TextButton(onClick = onCreate) { Text(uiText("New collection")) }
        if (state.selectedCollectionId != null) {
            val selected = state.collections.firstOrNull { it.id == state.selectedCollectionId }
            if (selected != null && onPrivate != null) {
                val label = stringResource(R.string.halo11_private_lock)
                IconButton(onClick = { onPrivate(PrivateLibraryTarget.Collection(selected.id, selected.name)) }) {
                    com.halo.app.ui.zone5.Zone5Icon("action_private", HaloGlyph.SECURITY, Modifier.size(24.dp), contentDescription = label)
                }
            }
            IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.halo_ic_trash), uiText("Delete selected collection")) }
        }
    }
}

private fun canProtectLibraryItem(item: NotebookWorkspaceItem): Boolean {
    val row = item.objectRef
    if (row.lifecycle != com.halo.app.data.notebook.WorkspaceObjectLifecycle.ACTIVE || runCatching {
            java.util.UUID.fromString(row.id.value).toString() == row.id.value
        }.getOrDefault(false).not()) return false
    return when (row.type) {
        WorkspaceObjectType.NOTE -> row.contentRef == "note:${row.id.value}"
        WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA -> row.contentRef == "file:${row.id.value}"
        WorkspaceObjectType.CANVAS, WorkspaceObjectType.ARTIFACT ->
            row.contentRef in setOf("canvas:${row.id.value}", "studio:${row.id.value}")
        else -> false
    }
}

internal enum class NotebookEmptyAction { CREATE_NOTE, CREATE_CANVAS, SHOW_LIBRARY }

internal fun notebookEmptyAction(query: String, filter: NotebookFilter, inCollection: Boolean): NotebookEmptyAction = when {
    query.isNotBlank() || inCollection -> NotebookEmptyAction.SHOW_LIBRARY
    filter == NotebookFilter.CANVASES -> NotebookEmptyAction.CREATE_CANVAS
    filter in listOf(NotebookFilter.ALL, NotebookFilter.NOTES) -> NotebookEmptyAction.CREATE_NOTE
    else -> NotebookEmptyAction.SHOW_LIBRARY
}

@Composable
internal fun NotebookEmptyGuidance(query: String, filter: NotebookFilter, inCollection: Boolean,
    enabled: Boolean, onCreateNote: () -> Unit, onCreateCanvas: () -> Unit, onShowLibrary: () -> Unit) {
    val action = notebookEmptyAction(query, filter, inCollection)
    val title = when {
        query.isNotBlank() -> "No matches"
        inCollection -> "This collection is empty"
        filter == NotebookFilter.CANVASES -> "Your first canvas"
        filter == NotebookFilter.TRASH -> "Nothing in trash"
        filter == NotebookFilter.ALL || filter == NotebookFilter.NOTES -> "Make room for your ideas"
        else -> "Nothing in this view"
    }
    val message = when (action) {
        NotebookEmptyAction.CREATE_NOTE -> "Create a note, import a file, or capture something for later. Find it here when you return."
        NotebookEmptyAction.CREATE_CANVAS -> "Create a board, then add text, media or links to your existing notes."
        NotebookEmptyAction.SHOW_LIBRARY -> "Choose the whole library to see your items. Filters do not delete anything."
    }
    val label = when (action) {
        NotebookEmptyAction.CREATE_NOTE -> "Create note"
        NotebookEmptyAction.CREATE_CANVAS -> "New canvas"
        NotebookEmptyAction.SHOW_LIBRARY -> "Show whole library"
    }
    val onClick = when (action) {
        NotebookEmptyAction.CREATE_NOTE -> onCreateNote
        NotebookEmptyAction.CREATE_CANVAS -> onCreateCanvas
        NotebookEmptyAction.SHOW_LIBRARY -> onShowLibrary
    }
    HaloEmptyState(uiText(title), uiText(message), HaloGlyph.NOTE,
        action = HaloHeaderAction(uiText(label), onClick), enabled = enabled)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun WorkspaceObjectRow(
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
    onMultiSelect: (() -> Unit)? = null,
    selectedCollectionName: String? = null,
    onRestore: (() -> Unit)? = null,
    bigPictures: Boolean = false,
    onRename: (() -> Unit)? = null,
    onPrivate: (() -> Unit)? = null,
) {
    val row = item.objectRef
    val icon = when (item.libraryKind) {
        HaloLibraryKind.PHOTO -> R.drawable.ic_halo_type_image
        HaloLibraryKind.RECORDING -> R.drawable.ic_halo_capture_record
        else -> workspaceIconResource(row)
    }
    var menuExpanded by remember(row.id.value) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            role = androidx.compose.ui.semantics.Role.Button,
            onClick = onOpen,
            onLongClick = onMultiSelect,
            onLongClickLabel = if (onMultiSelect != null) uiText("Select") else null,
        ),
        color = MaterialTheme.colorScheme.surface,
        shape = com.halo.app.ui.design.HaloShapes.control,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column {
            if (bigPictures) LibraryPicture(row, icon, item.libraryKind == HaloLibraryKind.PHOTO && row.type == WorkspaceObjectType.NOTE)
            Row(
            Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = com.halo.app.ui.design.HaloShapes.control, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), null, Modifier.size(23.dp),
                        com.halo.app.ui.zone5.zone5FileInk(MaterialTheme.colorScheme))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val typeLabel = if (item.libraryKind == HaloLibraryKind.FILE || item.libraryKind == null) uiText(workspaceTypeLabel(row))
                    else stringResource(item.libraryKind.label)
                Text(workspaceDisplayTitle(row.title, item.snippet, typeLabel), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val dateLabel = java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT,
                    androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                ).format(java.util.Date(row.updatedAt))
                val collectionLabel = selectedCollectionName?.takeIf { inSelectedCollection }
                    ?: if (row.inInbox) uiText("Inbox") else null
                val glassesLabel = if (item.fromGlasses) stringResource(R.string.halo11_library_from_glasses) else null
                Text(listOfNotNull(typeLabel, glassesLabel, collectionLabel, dateLabel).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = true)
                item.snippet?.trim()?.takeIf {
                    it.isNotBlank() && it != row.title
                }?.let { excerpt ->
                    Text(excerpt, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                if (selected) MetadataLabel("In chat", emphasized = true)
                item.previousTitle?.let { previous ->
                    Text(stringResource(R.string.halo11_library_was_called, previous), style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                val isDark = MaterialTheme.colorScheme.background.luminance() < .5f
                Icon(
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = uiText(if (favorite) "Remove favorite" else "Add favorite"),
                    tint = if (favorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.halo_ic_more),
                        uiText("Organize %1\$s", row.title.ifBlank { uiText("item") }))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    onRename?.let { rename ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.halo11_library_name_item)) },
                            onClick = { menuExpanded = false; rename() })
                    }
                    onPrivate?.let { protect ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.halo11_private_lock)) },
                            onClick = { menuExpanded = false; protect() })
                    }
                    onRestore?.let { restore ->
                        DropdownMenuItem(
                            text = { Text(uiText("Restore")) },
                            onClick = { menuExpanded = false; restore() }
                        )
                    }
                    notebookStudySourceAction(row.type, row.lifecycle)?.let { label ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(painterResource(R.drawable.halo_ic_study), null) },
                            text = { Text(uiText(label)) },
                            onClick = { menuExpanded = false; onStudy() },
                        )
                    }

                    if (row.lifecycle == com.halo.app.data.notebook.WorkspaceObjectLifecycle.ACTIVE) DropdownMenuItem(
                        leadingIcon = { Icon(painterResource(R.drawable.halo_ic_memory), null) },
                        text = { Text(uiText(if (selected) "Remove from Halo context" else "Add to Halo context")) },
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
                            leadingIcon = { Icon(painterResource(R.drawable.halo_ic_collections), null) },
                            text = { Text(uiText("Add to %1\$s", name)) },
                            onClick = { menuExpanded = false; onAddToCollection(id) },
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun LibraryPicture(row: WorkspaceObject, icon: Int, isCapture: Boolean) {
    val authority = LocalContext.current.packageName + ".captures"
    val captureUri by produceState<android.net.Uri?>(null, row.id, row.updatedAt, isCapture) {
        value = if (isCapture) {
            try {
                com.halo.app.di.ManualDependencyGraph.notebookAssetRepository.observe(row.id.value).first()
                    .asSequence().filter { it.type == "IMAGE" && it.storageType == "APP_OWNED" }
                    .map { android.net.Uri.parse(it.pathOrUri) }
                    .firstOrNull { it.scheme == "content" && it.authority == authority }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        } else null
    }
    val file by produceState<com.halo.app.data.workspace.WorkspaceFile?>(null, row.id, row.updatedAt) {
        if (row.type in setOf(WorkspaceObjectType.MEDIA, WorkspaceObjectType.FILE, WorkspaceObjectType.ARTIFACT)) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { com.halo.app.di.ManualDependencyGraph.workspaceFileRepository.getFile(row.id) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }?.takeIf { it.mimeType.startsWith("image/") || it.mimeType == "application/pdf" }
        }
    }
    val preview = file
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().height(118.dp)) {
        when {
            preview != null -> CanvasLocalFileThumbnail(preview, Modifier.fillMaxSize().padding(6.dp))
            captureUri != null -> coil.compose.AsyncImage(model = captureUri, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null,
                    modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun MetadataLabel(text: String, emphasized: Boolean = false) {
    if (emphasized) {
        Surface(shape = com.halo.app.ui.design.HaloShapes.control,
            color = MaterialTheme.colorScheme.primaryContainer) {
            Text(uiText(text), Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    } else {
        Text(uiText(text), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun workspaceTypeLabel(row: WorkspaceObject): String = when (row.type) {
    WorkspaceObjectType.NOTE -> "Note"
    WorkspaceObjectType.CANVAS -> "Canvas"
    WorkspaceObjectType.FILE -> "File"
    WorkspaceObjectType.MEDIA -> "Media"
    WorkspaceObjectType.CONVERSATION -> "Saved chat"
    WorkspaceObjectType.MEMORY -> "Memory"
    WorkspaceObjectType.ARTIFACT -> if (row.contentRef.startsWith("studio:")) "Studio" else if (row.contentRef.startsWith("canvas:")) "Canvas" else "Artifact"
}

/** Display only. A memory suggestion is never an approved personal fact.
 * The same MemoryHub Suggestions page owns review, editing and acceptance.
 */
@Composable
internal fun NotebookPendingMemoryReview(count: Int, onReview: () -> Unit) {
    if (count <= 0) return
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onReview,
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp)
                    .background(scheme.primaryContainer.copy(alpha = .3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.halo_ic_memory), contentDescription = null,
                    modifier = Modifier.size(24.dp), tint = scheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText("%1\$d things halo could remember", count), style = MaterialTheme.typography.titleSmall)
                Text(uiText("Only if you say yes"), style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant)
            }
            Text(uiText("Review"), style = MaterialTheme.typography.labelLarge,
                color = scheme.primary)
        }
    }
}

/** Brand Book extension: Study remains an actual, always-visible Notebook
 * workflow. The four Round 2 creation actions remain unchanged.
 */
@Composable
internal fun NotebookLearningTools(onStudy: () -> Unit, onMemory: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText("Tools"), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkspaceTool("Study", "Explore a topic, continue a lesson or review",
                R.drawable.halo_ic_study, onStudy, actionLabel = "Open Study",
                onAction = onStudy, modifier = Modifier.weight(1f))
            WorkspaceTool("Memory", "What Halo remembers and learns",
                R.drawable.halo_ic_memory, onMemory, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun WorkspaceTool(
    title: String,
    description: String,
    icon: Int,
    onOpen: () -> Unit,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpen,
        modifier = modifier.heightIn(min = 72.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(icon), contentDescription = null,
                    modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Text(uiText(title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            }
            // Kept as a visible, localizable action label for Study; clicking
            // anywhere on the tile opens the same real learning flow.
            Text(uiText(actionLabel ?: description), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun NotebookCreateAction(
    label: String,
    icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 84.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = if (emphasized) scheme.primaryContainer.copy(alpha = 0.25f)
            else scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (emphasized) scheme.primary
            else scheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null,
                modifier = Modifier.size(24.dp), tint = scheme.primary)
            Text(uiText(label), style = MaterialTheme.typography.labelLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** Display only: never rewrites the stored title or the user's source text. */
internal fun workspaceDisplayTitle(title: String, snippet: String?, fallback: String): String {
    val generated = title.isBlank() || title.startsWith("memo_") || title in setOf("Untitled", "Untitled note", "Untitled canvas")
    if (!generated) return title
    return snippet?.lineSequence()?.map { it.trim().trimStart('#', '*', '-', ' ') }
        ?.firstOrNull { it.isNotBlank() }?.take(80) ?: fallback
}

private fun workspaceIconResource(row: WorkspaceObject): Int = when (row.type) {
    WorkspaceObjectType.NOTE -> R.drawable.ic_halo_type_note
    WorkspaceObjectType.CANVAS -> R.drawable.ic_halo_type_canvas
    WorkspaceObjectType.CONVERSATION -> R.drawable.ic_halo_library_saved_chat
    WorkspaceObjectType.MEMORY -> R.drawable.ic_halo_tool_memory
    WorkspaceObjectType.FILE -> R.drawable.ic_halo_type_file
    WorkspaceObjectType.MEDIA -> R.drawable.ic_halo_type_file
    WorkspaceObjectType.ARTIFACT -> if (row.contentRef.startsWith("studio:")) R.drawable.ic_halo_type_image else if (row.contentRef.startsWith("canvas:")) R.drawable.ic_halo_type_canvas else R.drawable.ic_halo_type_file
}
