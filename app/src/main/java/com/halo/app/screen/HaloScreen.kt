package com.halo.app.screen

import android.Manifest
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.halo.app.data.workspace.WorkspaceQuery
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.uiText
import com.halo.app.R
import com.halo.app.ui.components.HaloReadAloud
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.fx.*
import com.halo.app.ui.icons.*
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal data class ScreenDisplay(
    val title: String, val summary: String, val state: String,
    val route: String?, val sensitive: Boolean = false, val updatedAt: Instant? = null,
    val sourceTitle: Boolean = false, val sourceSummary: Boolean = false, val detailSummary: String? = null,
    val showFullTimestamp: Boolean = false,
    val stateArguments: List<Any> = emptyList(),
)

/** The same local layout is used on phones, tablets and in full screen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HaloScreen(store: LocalScreenStore, fullScreen: Boolean, onFullScreen: (Boolean) -> Unit,
    onOpen: (String) -> Unit, onTalk: () -> Unit, onChat: () -> Unit = {},
    orbControl: (@Composable (androidx.compose.ui.unit.Dp) -> Unit)? = null, orbActive: Boolean = false,
    onQuickNote: () -> Unit = {}) {
    val layout by store.layout.collectAsStateWithLifecycle()
    val routineItems by store.items.collectAsStateWithLifecycle()
    val hidePrivate by store.hidePrivate.collectAsStateWithLifecycle()
    val keepCharging by store.keepCharging.collectAsStateWithLifecycle()
    val dimAtNight by store.dimAtNight.collectAsStateWithLifecycle()
    val topics by store.topics.collectAsStateWithLifecycle()
    val presets by store.presets.collectAsStateWithLifecycle()
    var topicsOpen by rememberSaveable { mutableStateOf(false) }
    var topicsDraft by rememberSaveable { mutableStateOf("") }
    val daily by ManualDependencyGraph.dailyAssistantController.snapshot.collectAsStateWithLifecycle()
    val workspace by remember { ManualDependencyGraph.workspaceRepository.observe(WorkspaceQuery()) }
        .collectAsStateWithLifecycle(emptyList())
    val activeStudySessions by remember { ManualDependencyGraph.studyUseCases.observeActiveSessions() }
        .collectAsStateWithLifecycle(emptyList())
    val activity by ManualDependencyGraph.activityViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val environment = rememberScreenEnvironment()
    val maskSensitive = screenMasksSensitive(fullScreen, hidePrivate, environment)
    val canInteract = environment.resumed && environment.interactive && !environment.locked && !orbActive
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableStateOf(Instant.now()) }
    var editBaseJson by rememberSaveable { mutableStateOf<String?>(null) }
    var editDraftJson by rememberSaveable { mutableStateOf<String?>(null) }
    var editResearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val editBase = remember(editBaseJson) { ScreenLayoutCodec.decode(editBaseJson) }
    val editDraft = remember(editDraftJson) { ScreenLayoutCodec.decode(editDraftJson) }
    val customizing = editBase != null && editDraft != null
    val workingLayout = editDraft ?: layout
    var showingArchivedCards by rememberSaveable { mutableStateOf(false) }
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    var cardOptionsIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var presetNameOpen by rememberSaveable { mutableStateOf(false) }
    var presetName by rememberSaveable { mutableStateOf("") }
    var presetError by remember { mutableStateOf(false) }
    var gridColumns by remember { mutableIntStateOf(2) }
    fun startEdit(initial: ScreenLayout? = layout, researchQuery: String? = null) {
        val saved = layout ?: return
        val draft = initial ?: return
        editBaseJson = ScreenLayoutCodec.encode(saved)
        editDraftJson = ScreenLayoutCodec.encode(draft)
        editResearchQuery = researchQuery
        showingArchivedCards = false
    }
    fun setCustomizing(enabled: Boolean) {
        if (enabled) startEdit() else {
            editBaseJson = null; editDraftJson = null; editResearchQuery = null; showingArchivedCards = false
            appearanceOpen = false; cardOptionsIndex = null
        }
    }
    var picker by rememberSaveable { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var chromeTouch by remember { mutableLongStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var resetLayout by rememberSaveable { mutableStateOf<String?>(null) }
    var previewType by rememberSaveable { mutableStateOf<String?>(null) }
    var previewItem by rememberSaveable { mutableStateOf<String?>(null) }
    var askLayout by rememberSaveable { mutableStateOf(false) }
    var layoutRequest by rememberSaveable { mutableStateOf("") }
    var proposalBase by remember { mutableStateOf<ScreenLayout?>(null) }
    var proposedLayout by remember { mutableStateOf<ScreenLayout?>(null) }
    var proposedPlan by remember { mutableStateOf<ScreenLayoutRequests.Plan?>(null) }
    var selectedChanges by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var includeResearch by remember { mutableStateOf(true) }
    var proposedResearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    fun selectProposal(changes: Set<Int>, research: Boolean) {
        selectedChanges = changes; includeResearch = research
        val plan = proposedPlan ?: return
        val base = proposalBase ?: return
        val selected = ScreenLayoutRequests.select(plan, changes, research)
        proposedLayout = ScreenLayoutRequests.apply(base, selected) ?: base
        proposedResearchQuery = selected.researchQuery
    }
    var requestError by remember { mutableStateOf(false) }
    var researchRefreshQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var savingResearch by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val widgetNames = ScreenWidgets.types.associateWith { screenWidgetTitle(it) }
    var detailCard by remember { mutableStateOf<ScreenCard?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var canUndo by remember { mutableStateOf(store.hasUndo()) }
    var calendarAllowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,
        Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) }
    var calendarEvent by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var calendarFailed by remember { mutableStateOf(false) }
    var calendarDenied by rememberSaveable { mutableStateOf(false) }
    val fallbackCalendarTitle = uiText("Calendar event")
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        calendarAllowed = it
        calendarDenied = !it
        now = Instant.now()
    }
    val latest = rememberUpdatedState(layout)
    val working = rememberUpdatedState(workingLayout)
    fun change(cards: List<ScreenCard>, message: String) {
        working.value?.let {
            if (customizing) editDraftJson = ScreenLayoutCodec.encode(it.copy(cards = cards))
            else { store.saveLayout(it.copy(cards = cards)); canUndo = true }
            notice = message
        }
    }
    fun add(type: String, itemId: String? = null) {
        val current = working.value ?: return
        if (current.cards.size >= 24) { notice = "Your Screen is full. Hide a card to make room."; return }
        if (current.cards.any { it.widgetType == type && it.itemId == itemId }) {
            notice = "This card is already on your Screen."; picker = false; return
        }
        change(current.cards + ScreenCard(type, itemId,
            if (type == "halo_activity") CardSize.COMPACT else CardSize.STANDARD, false), "Card added")
        picker = false
        if (type == "calendar" && !calendarAllowed && !customizing) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
    }
    LaunchedEffect(fullScreen, chromeTouch) {
        chromeVisible = true
        if (fullScreen) { delay(5000); chromeVisible = false }
    }
    LaunchedEffect(routineItems, layout?.cards) {
        val current = layout
        if (current != null) {
            val unbound = current.cards.indexOfFirst { it.widgetType == "automation_result" && it.itemId == null }
            val result = routineItems.sortedByDescending { it.updatedAt }.firstOrNull { item ->
                current.cards.none { it.itemId == item.id }
            }
            if (unbound >= 0 && result != null) store.saveLayout(current.copy(cards = current.cards.mapIndexed { index, card ->
                if (index == unbound) card.copy(itemId = result.id) else card
            }), recordUndo = false)
        }
    }
    LaunchedEffect(layout?.cards) {
        detailCard?.let { selected ->
            if (layout?.cards?.none { it.widgetType == selected.widgetType && it.itemId == selected.itemId } != false) detailCard = null
        }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            calendarAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            if (!calendarAllowed) calendarEvent = null
            while (true) {
                val power = context.getSystemService(android.os.PowerManager::class.java)
                if (power.isInteractive) now = Instant.now()
                delay(60_000)
            }
        }
    }
    LaunchedEffect(now, calendarAllowed, environment.resumed, environment.locked, layout?.cards?.any { it.widgetType == "calendar" }) {
        if (calendarAllowed && environment.resumed && !environment.locked && layout?.cards?.any { it.widgetType == "calendar" } == true) {
            calendarEvent = withContext(Dispatchers.IO) {
                try {
                    val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                        android.content.ContentUris.appendId(it, now.toEpochMilli())
                        android.content.ContentUris.appendId(it, now.plusSeconds(86400).toEpochMilli())
                    }.build()
                    context.contentResolver.query(uri,
                        arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
                        null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                        if (cursor.moveToFirst()) (cursor.getString(0) ?: fallbackCalendarTitle) to cursor.getLong(1) else null
                    }.also { calendarFailed = false }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { calendarFailed = true; null }
            }
        }
    }
    BackHandler(fullScreen || customizing) { if (fullScreen) onFullScreen(false) else setCustomizing(false) }
    ScreenWindowPolicy(fullScreen, keepCharging, dimAtNight, now, environment)
    val shift = if (fullScreen && environment.resumed && environment.interactive) ((now.epochSecond / 300L) % 5L).toInt() - 2 else 0
    LaunchedEffect(environment.resumed, environment.locked, orbActive) {
        if (!environment.resumed || environment.locked || orbActive) {
            settings = false; picker = false; appearanceOpen = false; cardOptionsIndex = null
            presetNameOpen = false; topicsOpen = false; askLayout = false; previewType = null; detailCard = null
        }
        if (!environment.resumed || environment.locked) researchRefreshQuery = null
    }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    @Composable fun display(card: ScreenCard): ScreenDisplay = when (card.widgetType) {
        "clock" -> ScreenDisplay(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
            .format(now.atZone(ZoneId.systemDefault())), DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withLocale(locale).format(now.atZone(ZoneId.systemDefault())), "On this phone", null, sourceTitle = true, sourceSummary = true)
        "calendar" -> ScreenDisplay("Next on your calendar", when {
            !calendarAllowed -> "Choose whether halo can read your calendar."
            calendarFailed -> "Your calendar could not be opened."
            calendarEvent == null -> "Nothing scheduled in the next 24 hours."
            else -> calendarEvent!!.first + " · " + DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
                .format(Instant.ofEpochMilli(calendarEvent!!.second).atZone(ZoneId.systemDefault()))
        }, if (!calendarAllowed) "Permission needed" else if (calendarFailed) "Needs attention" else "On this phone", null, true, sourceSummary = calendarAllowed && !calendarFailed && calendarEvent != null)
        "weather" -> ScreenDisplay("Weather", "A weather source has not been connected.", "Needs setup", null)
        "device" -> ScreenDisplay(stringResource(R.string.screen_this_phone), environment.batteryPercent?.let {
            stringResource(R.string.screen_battery_percent, it)
        } ?: stringResource(R.string.screen_battery_unavailable),
            if (environment.charging) stringResource(R.string.screen_charging) else stringResource(R.string.screen_on_battery),
            null, sourceTitle = true, sourceSummary = true)
        "interests" -> ScreenDisplay("For you", topics.ifBlank { "Choose a reading source. Your notes and chats are never used to guess your interests." }, "Needs setup", null, topics.isNotBlank(), sourceSummary = topics.isNotBlank())
        "research" -> routineItems.firstOrNull { it.id == card.itemId && it.sourceType == ScreenSourceType.RESEARCH }?.let { item ->
            val summary = item.summary.orEmpty()
            ScreenDisplay(item.title, summary.take(280) + if (summary.length > 280) "…" else "", if (item.expiresAt?.isBefore(now) == true) uiText("Saved result may be out of date")
                else "${uiText("Sourced research")} · ${item.sourceLinks.size} ${uiText("links")}",
                null, true, item.updatedAt, sourceTitle = true, sourceSummary = true, detailSummary = summary,
                showFullTimestamp = true)
        } ?: ScreenDisplay("Research", "Save a sourced web search to keep it here.", "No saved research yet", null, true)
        "tasks" -> ScreenDisplay("Needs attention", daily.primary?.title ?: "No tasks to catch up on",
            if (daily.items.isEmpty()) "No pending items" else "%1\$d updates", daily.primary?.action?.let {
                com.halo.app.ui.shell.dailyOwnerRoute(it)
            } ?: "activity", true,
            sourceSummary = daily.primary != null,
            stateArguments = if (daily.items.isEmpty()) emptyList() else listOf(daily.items.size))
        "notebook" -> workspace.firstOrNull()?.let { note ->
            val kind = when (note.type) {
                com.halo.app.data.notebook.WorkspaceObjectType.CANVAS -> "canvas"
                com.halo.app.data.notebook.WorkspaceObjectType.CONVERSATION -> "conversation"
                com.halo.app.data.notebook.WorkspaceObjectType.MEMORY -> "memory"
                com.halo.app.data.notebook.WorkspaceObjectType.NOTE -> "note"
                else -> "file"
            }
            ScreenDisplay("Continue", note.title, "Saved on this phone", "notebook/$kind/${android.net.Uri.encode(note.id.value)}", true, sourceSummary = true)
        } ?: ScreenDisplay("Notebook", "Your saved work will appear here.", "Nothing saved yet", "notebook", true)
        "study" -> activeStudySessions.firstOrNull()?.let { session ->
            ScreenDisplay(session.goal?.take(160) ?: uiText("Continue studying"), uiText("Resume your saved Study session."),
                uiText("Saved on this phone"), "notebook/study/${android.net.Uri.encode(session.id)}", true,
                sourceTitle = true, sourceSummary = true)
        } ?: ScreenDisplay("Continue studying", uiText("Start a Study session to see it here."), uiText("No active session"), "study", true,
            sourceSummary = true)
        "halo_activity" -> ScreenDisplay("What halo did", activity.events.firstOrNull()?.title ?: "No activity yet",
            "Activity", "activity", true, sourceSummary = activity.events.isNotEmpty())
        else -> {
            val item = if (card.itemId != null) routineItems.firstOrNull { it.id == card.itemId }
                else null
            if (item == null) ScreenDisplay("Routine results", "A finished routine will update this card.", "Waiting for a result", "automation", true)
            else ScreenDisplay(item.title, item.summary.orEmpty(),
                if (item.expiresAt?.isBefore(now) == true) "Saved result may be out of date" else when (item.state) {
                    ScreenItemState.READY -> "Updated"
                    ScreenItemState.LOADING -> "Loading…"
                    ScreenItemState.REFRESHING -> "Updating…"
                    ScreenItemState.EMPTY -> "Nothing to show yet"
                    ScreenItemState.NEEDS_SETUP -> "Needs setup"
                    ScreenItemState.PERMISSION_DENIED -> "Permission needed"
                    ScreenItemState.OFFLINE -> "Offline · showing saved content"
                    ScreenItemState.STALE -> "Last update failed"
                    ScreenItemState.ERROR -> "Needs attention"
                },
                item.sourceId?.let { "automation?routineId=${android.net.Uri.encode(it)}" } ?: "automation",
                item.sensitivity != ScreenSensitivity.PUBLIC, item.lastSuccessAt ?: item.updatedAt, sourceTitle = true, sourceSummary = true)
        }
    }
    Column(Modifier.fillMaxSize().pointerInput(fullScreen) {
        if (fullScreen) awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            chromeTouch += 1L
        }
    }.background(screenBackgroundColor(workingLayout?.appearance ?: ScreenAppearance()))
        .haloStateAura(HaloLight.IDLE, MaterialTheme.colorScheme.primaryContainer, Color(0xFFFF9F6B), MaterialTheme.colorScheme.error).offset(x = shift.dp, y = shift.dp)
        .padding(horizontal = if (fullScreen) 24.dp else 16.dp)) {
        val conflictText = stringResource(R.string.screen_edit_conflict)
        val noteRequestedText = stringResource(R.string.screen_note_requested)
        ScreenWorkspaceChrome(workingLayout != null, customizing, fullScreen, chromeVisible, orbActive,
            canInteract, onChat, { settings = true }, onFullScreen,
            onEdit = { setCustomizing(true) }, onApply = {
                if (editBase != null && editDraft != null && store.applyDraft(editBase, editDraft)) {
                    val research = editResearchQuery
                    setCustomizing(false); notice = "Layout changed"; canUndo = store.hasUndo()
                    if (!research.isNullOrBlank()) researchRefreshQuery = research
                } else notice = conflictText
            }, onCancel = { setCustomizing(false); notice = null }, onAppearance = { appearanceOpen = true },
            onAdd = { picker = true }, onNote = { onQuickNote(); notice = noteRequestedText },
            onTalk = onTalk, orbControl = orbControl)
        if (workingLayout == null) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (orbControl != null) orbControl(112.dp)
                else HaloSignalFace(HaloSignalPresentation(label = "Your Screen"), size = 112.dp)
                Text(uiText("A little clarity, at a glance."), style = MaterialTheme.typography.headlineSmall)
                Text(uiText("Choose a starting layout. You can change every card."), modifier = Modifier.padding(vertical = 16.dp))
                listOf("Morning", "Focus", "Quiet").forEach { name ->
                    OutlinedButton(onClick = { store.saveLayout(ScreenWidgets.starter(name)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText(name)) }
                }
            }
        } else {
            if (fullScreen && screenAmbientDecision(true, keepCharging, dimAtNight, false, environment).resting)
                Text(stringResource(R.string.screen_power_rest), style = MaterialTheme.typography.bodySmall)
            notice?.let { message ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText(message), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    if (canUndo && !customizing) TextButton(onClick = { store.undo(); notice = "Change undone"; canUndo = false }) { Text(uiText("Undo")) }
                    TextButton(onClick = { notice = null }) { Text(uiText("Dismiss")) }
                }
            }
            val cardsForDisplay = workingLayout.cards.withIndex().filter { it.value.archived == showingArchivedCards }
            if (customizing) TextButton(onClick = { showingArchivedCards = !showingArchivedCards }) {
                Text(uiText(if (showingArchivedCards) "Show current cards" else "Show archived cards"))
            }
            if (cardsForDisplay.isEmpty()) Text(uiText(if (showingArchivedCards) "No archived cards" else "No cards on Screen"),
                Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val columns = ScreenGrid.columns(maxWidth.value, LocalDensity.current.fontScale)
                LaunchedEffect(columns) { gridColumns = columns }
                val visibleCards = cardsForDisplay.map { it.value }
                fun updateVisible(values: List<ScreenCard>, message: String) {
                    val replacements = cardsForDisplay.mapIndexed { i, card -> card.index to values[i] }.toMap()
                    change(workingLayout.cards.mapIndexed { i, card -> replacements[i] ?: card }, message)
                }
                ScreenWorkspaceGrid(visibleCards, columns, Modifier.fillMaxSize()) { cell, geometry ->
                    val indexed = cardsForDisplay[cell.index]
                    val card = indexed.value
                    val data = display(card)
                    ScreenWidgetCard(data, card, maskSensitive && data.sensitive,
                        fullScreen, customizing && canInteract,
                        onOpen = { if (canInteract) { if (customizing) cardOptionsIndex = indexed.index else detailCard = card } },
                        appearance = workingLayout.appearance, enabled = canInteract,
                        editControls = if (customizing && canInteract) ({ ScreenGridHandles(cell, geometry, columns, visibleCards.size, screenWidgetTitle(card.widgetType),
                            onMove = { dx, dy -> updateVisible(ScreenGrid.move(visibleCards, cell.index, columns, dx, dy), "Card moved") },
                            onResize = { dw, dh -> updateVisible(ScreenGrid.resize(visibleCards, cell.index, columns, dw, dh), "Card resized") },
                            onOptions = { cardOptionsIndex = indexed.index }) }) else null)
                }
            }
        }
    }
    if (appearanceOpen && customizing && workingLayout != null) ScreenNativeSheet(onDismissRequest = { appearanceOpen = false }) {
        ScreenSheetBody(stringResource(R.string.screen_appearance), { appearanceOpen = false }) {
            ScreenAppearanceControls(workingLayout.appearance) { value ->
                editDraftJson = ScreenLayoutCodec.encode(workingLayout.copy(appearance = value))
            }
        }
    }
    if (customizing && workingLayout != null) cardOptionsIndex?.let { selected ->
        workingLayout.cards.getOrNull(selected)?.let { card ->
            val visible = workingLayout.cards.withIndex().filter { it.value.archived == showingArchivedCards }
            val visibleIndex = visible.indexOfFirst { it.index == selected }
            fun editGrid(resize: Boolean, x: Int, y: Int) {
                if (visibleIndex < 0) return
                val values = if (resize) ScreenGrid.resize(visible.map { it.value }, visibleIndex, gridColumns, x, y)
                    else ScreenGrid.move(visible.map { it.value }, visibleIndex, gridColumns, x, y)
                val replacements = visible.mapIndexed { i, value -> value.index to values[i] }.toMap()
                change(workingLayout.cards.mapIndexed { i, value -> replacements[i] ?: value }, if (resize) "Card resized" else "Card moved")
                if (!resize && gridColumns == 1) cardOptionsIndex = visible.getOrNull((visibleIndex + y).coerceIn(visible.indices))?.index
            }
            ScreenNativeSheet(onDismissRequest = { cardOptionsIndex = null }) {
                ScreenSheetBody(screenWidgetTitle(card.widgetType), { cardOptionsIndex = null }) {
                    Text(stringResource(R.string.screen_grid_help), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Triple(R.string.screen_move_left, -1, 0), Triple(R.string.screen_move_right, 1, 0),
                            Triple(R.string.screen_move_up, 0, -1), Triple(R.string.screen_move_down, 0, 1)).forEach { (label, x, y) ->
                            OutlinedButton(onClick = { editGrid(false, x, y) }, enabled = gridColumns > 1 || x == 0,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(label)) }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Triple(R.string.screen_wider, 1, 0), Triple(R.string.screen_narrower, -1, 0),
                            Triple(R.string.screen_taller, 0, 1), Triple(R.string.screen_shorter, 0, -1)).forEach { (label, x, y) ->
                            OutlinedButton(onClick = { editGrid(true, x, y) }, enabled = gridColumns > 1 || x == 0,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(label)) }
                        }
                    }
                    if (gridColumns == 1) Text(stringResource(R.string.screen_large_text_reflow), style = MaterialTheme.typography.bodySmall)
                    ScreenTypographyControls(card.typography, card.widgetType == "clock") { value ->
                        change(workingLayout.cards.mapIndexed { i, c -> if (i == selected) c.copy(typography = value.bounded()) else c }, "Card updated")
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { change(workingLayout.cards.mapIndexed { i, c -> if (i == selected) c.copy(pinned = !c.pinned) else c }, "Card updated") }) {
                            Text(uiText(if (card.pinned) "Unpin" else "Pin"))
                        }
                        TextButton(onClick = {
                            change(workingLayout.cards.mapIndexed { i, c -> if (i == selected) c.copy(archived = !c.archived) else c }, "Card updated")
                            cardOptionsIndex = null
                        }) { Text(uiText(if (card.archived) "Restore" else "Archive")) }
                        TextButton(onClick = {
                            change(workingLayout.cards.filterIndexed { i, _ -> i != selected }, "Hidden from Screen. Its source is kept.")
                            cardOptionsIndex = null
                        }) { Text(uiText("Hide")) }
                    }
                }
            }
        }
    }
    if (picker) ScreenNativeSheet(onDismissRequest = { picker = false }) {
        Text(uiText("Add a card"), Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall)
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            ScreenWidgets.types.forEach { type ->
                if (type == "automation_result" && routineItems.isNotEmpty()) routineItems.forEach { result ->
                    TextButton(onClick = { previewType = type; previewItem = result.id; picker = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(uiText("Routine results") + " · " + result.title)
                    }
                } else if (type == "research" && routineItems.any { it.sourceType == ScreenSourceType.RESEARCH }) routineItems
                    .filter { it.sourceType == ScreenSourceType.RESEARCH }.forEach { result ->
                        TextButton(onClick = { previewType = type; previewItem = result.id; picker = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(uiText("Research") + " · " + result.title)
                        }
                } else TextButton(onClick = { previewType = type; previewItem = null; picker = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(screenWidgetTitle(type))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (settings) ScreenNativeSheet(onDismissRequest = { settings = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        Text(uiText("Screen settings"), Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = { if (!customizing) startEdit(); appearanceOpen = true; settings = false }) { Text(stringResource(R.string.screen_appearance)) }
        TextButton(onClick = { picker = true; settings = false }) { Text(uiText("Add card")) }
        TextButton(onClick = { askLayout = true; settings = false }, enabled = !customizing) { Text(uiText("Ask halo to change Screen")) }
        if (canUndo && !customizing) TextButton(onClick = { store.undo(); notice = "Change undone"; canUndo = false; settings = false }) { Text(uiText("Undo")) }
        ScreenToggle("Hide private details in full screen", hidePrivate, store::setPrivacy)
        ScreenToggle("Stay on while charging", keepCharging, store::setKeepCharging)
        ScreenToggle("Dim at night", dimAtNight, store::setDimAtNight)
        TextButton(onClick = { topicsDraft = topics; topicsOpen = true; settings = false }) { Text(uiText("Choose topics")) }
        Text(uiText("Night hours: 22:00–07:00. Your sources stay unchanged."), Modifier.padding(20.dp))
        Text(stringResource(R.string.screen_saved_presets), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { presetName = ""; presetError = false; presetNameOpen = true; settings = false }, enabled = layout != null) {
            Text(stringResource(R.string.screen_save_preset))
        }
        presets.forEach { preset ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { startEdit(preset); settings = false }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(preset.name) }
                IconButton(onClick = { store.removePreset(preset.id) }, modifier = Modifier.size(48.dp)) {
                    HaloIcon(HaloGlyph.TRASH, stringResource(R.string.screen_remove_preset, preset.name))
                }
            }
        }
        Text(uiText("Starting layouts"), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
        listOf("Morning", "Focus", "Quiet").forEach { name ->
            TextButton(onClick = { resetLayout = name; settings = false }) { Text(uiText(name)) }
        }
        Spacer(Modifier.height(24.dp))
        }
    }
    resetLayout?.let { name -> AlertDialog(onDismissRequest = { resetLayout = null },
        title = { Text(uiText("Replace your Screen layout?")) },
        text = { Text(uiText("Your cards will change. Notes, routines and other sources are kept. You can undo this change.")) },
        confirmButton = { TextButton(onClick = {
            startEdit(ScreenWidgets.starter(name)); resetLayout = null
        }) { Text(uiText("Preview")) } },
        dismissButton = { TextButton(onClick = { resetLayout = null }) { Text(uiText("Cancel")) } }) }
    if (presetNameOpen) ScreenNativeSheet(onDismissRequest = { presetNameOpen = false }) {
        ScreenSheetBody(stringResource(R.string.screen_save_preset), { presetNameOpen = false }) {
            Text(stringResource(R.string.screen_preset_saved_layout))
            com.halo.app.ui.components.HaloTextField(presetName, { presetName = it.take(64); presetError = false },
                label = { Text(stringResource(R.string.screen_preset_name)) }, modifier = Modifier.fillMaxWidth(),
                kind = com.halo.app.ui.components.VoiceFieldKind.TEXT)
            if (presetError) Text(stringResource(R.string.screen_preset_error), color = MaterialTheme.colorScheme.error)
            com.halo.app.ui.components.HaloActionButton(onClick = {
                val saved = latest.value
                if (saved != null && store.savePreset(presetName, saved)) presetNameOpen = false else presetError = true
            }, enabled = presetName.isNotBlank()) { Text(uiText("Save")) }
        }
    }
    if (topicsOpen) ScreenNativeSheet(onDismissRequest = { topicsOpen = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(uiText("Choose topics"), style = MaterialTheme.typography.headlineSmall)
            Text(uiText("Topics are chosen by you. A reading source still needs to be connected."))
            com.halo.app.ui.components.HaloTextField(topicsDraft, { topicsDraft = it.take(1000) },
                label = { Text(uiText("Topics")) }, modifier = Modifier.fillMaxWidth(),
                kind = com.halo.app.ui.components.VoiceFieldKind.TEXT)
            com.halo.app.ui.components.HaloActionButton(onClick = { store.setTopics(topicsDraft); topicsOpen = false }) { Text(uiText("Save")) }
        }
    }
    previewType?.let { type ->
        val card = ScreenCard(type, previewItem, CardSize.STANDARD, false)
        val data = display(card)
        ScreenNativeSheet(onDismissRequest = { previewType = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(uiText("Card preview"), style = MaterialTheme.typography.headlineSmall)
                ScreenWidgetCard(data, card, maskSensitive && data.sensitive, false, false,
                    {}, appearance = workingLayout?.appearance ?: ScreenAppearance())
                com.halo.app.ui.components.HaloActionButton(onClick = { add(type, previewItem); previewType = null },
                    modifier = Modifier.fillMaxWidth()) { Text(uiText("Add card")) }
                TextButton(onClick = { previewType = null; picker = true }) { Text(uiText("Back")) }
            }
        }
    }
    if (askLayout) ScreenNativeSheet(onDismissRequest = {
        askLayout = false; proposedLayout = null; proposedPlan = null; proposedResearchQuery = null
    }) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(uiText("Ask halo to change Screen"), style = MaterialTheme.typography.headlineSmall)
            Text(uiText("Add, hide, enlarge, or move a card to the top. Say the card name shown in Add card."))
            Text(uiText("You can also describe several cards and a research topic."), style = MaterialTheme.typography.bodySmall)
            com.halo.app.ui.components.HaloTextField(layoutRequest, {
                layoutRequest = it; proposedLayout = null; proposedPlan = null; proposedResearchQuery = null; requestError = false
            }, label = { Text(uiText("What would you like to change?")) },
                modifier = Modifier.fillMaxWidth(), kind = com.halo.app.ui.components.VoiceFieldKind.TEXT)
            if (requestError) Text(uiText("That change is unavailable. Use Customize to choose the card and action."),
                color = MaterialTheme.colorScheme.error)
            val proposal = proposedLayout
            if (proposal == null) com.halo.app.ui.components.HaloActionButton(onClick = {
                val base = latest.value
                val plan = ScreenLayoutRequests.compose(layoutRequest, widgetNames)
                proposalBase = base
                proposedLayout = if (base != null && plan != null) ScreenLayoutRequests.apply(base, plan) else null
                proposedPlan = plan.takeIf { proposedLayout != null }
                selectedChanges = plan?.changes?.indices?.toSet().orEmpty()
                includeResearch = true
                proposedResearchQuery = plan?.researchQuery
                requestError = proposedLayout == null
            }, enabled = layoutRequest.isNotBlank() && layout != null) { Text(uiText("Preview")) }
            else {
                Text(uiText("Review your new layout"), style = MaterialTheme.typography.titleMedium)
                proposedPlan?.changes?.forEachIndexed { index, change ->
                    val (verb, type) = when (change) {
                        is LayoutChange.Add -> "Add card" to change.widgetType
                        is LayoutChange.Remove -> "Hide" to change.widgetType
                        is LayoutChange.Move -> "Move up" to change.widgetType
                        is LayoutChange.Resize -> "Resize" to change.widgetType
                    }
                    Row(Modifier.fillMaxWidth().toggleable(selectedChanges.contains(index), role = Role.Checkbox,
                        onValueChange = { checked -> selectProposal(if (checked) selectedChanges + index else selectedChanges - index, includeResearch) }),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(selectedChanges.contains(index), null, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
                        Text(uiText(verb) + " · " + screenWidgetTitle(type), Modifier.weight(1f))
                    }
                }
                proposedPlan?.researchQuery?.let { query ->
                    Row(Modifier.fillMaxWidth().toggleable(includeResearch, role = Role.Checkbox,
                        onValueChange = { checked -> selectProposal(selectedChanges, checked) }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(includeResearch, null, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
                        Text(stringResource(R.string.screen_proposal_research, query), Modifier.weight(1f))
                    }
                }
                TextButton(onClick = {
                    if (latest.value != proposalBase) { proposedLayout = null; proposedPlan = null; requestError = true }
                    else { startEdit(proposal, proposedResearchQuery); askLayout = false; proposedLayout = null; proposedPlan = null }
                }, enabled = selectedChanges.isNotEmpty() || !proposedResearchQuery.isNullOrBlank()) { Text(stringResource(R.string.screen_preview_on_screen)) }
                com.halo.app.ui.components.HaloActionButton(onClick = {
                    val base = proposalBase
                    if (base == null || !store.applyDraft(base, proposal)) { proposedLayout = null; proposedPlan = null; requestError = true }
                    else {
                        canUndo = store.hasUndo(); notice = "Layout changed"
                        proposedLayout = null; proposedPlan = null; askLayout = false
                        proposedResearchQuery?.let { researchRefreshQuery = it }
                        proposedResearchQuery = null
                    }
                }, enabled = selectedChanges.isNotEmpty() || !proposedResearchQuery.isNullOrBlank()) { Text(uiText("Apply")) }
            }
            TextButton(onClick = { proposedLayout = null; askLayout = false; setCustomizing(true) }) { Text(uiText("Customize")) }
        }
    }
    detailCard?.let { selected -> layout?.cards?.firstOrNull { it.widgetType == selected.widgetType && it.itemId == selected.itemId }?.let { card ->
        val data = display(card)
        val researchItem = if (card.widgetType == "research")
            routineItems.firstOrNull { it.id == card.itemId && it.sourceType == ScreenSourceType.RESEARCH } else null
        ScreenNativeSheet(onDismissRequest = { detailCard = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (maskSensitive && data.sensitive) uiText("Private card") else if (data.sourceTitle) data.title else uiText(data.title), style = MaterialTheme.typography.headlineSmall)
                if (maskSensitive && data.sensitive) Text(stringResource(R.string.screen_private_hidden))
                else {
                    val detailSummary = data.detailSummary ?: data.summary
                    Text(if (data.sourceSummary) detailSummary else uiText(detailSummary), style = MaterialTheme.typography.bodyLarge)
                    if (data.showFullTimestamp) data.updatedAt?.let { instant ->
                        val dateTime = formatScreenTimestamp(instant, true, locale)
                        Text(uiText("Retrieved") + " · " + dateTime, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (card.widgetType == "interests") TextButton(onClick = {
                        topicsDraft = topics; topicsOpen = true; detailCard = null
                    }) { Text(uiText("Choose topics")) }
                    if (card.widgetType in setOf("calendar", "tasks") || researchItem != null) {
                        OutlinedButton(onClick = {
                            when (card.widgetType) {
                                "calendar" -> { now = Instant.now(); calendarFailed = false }
                                "tasks" -> ManualDependencyGraph.dailyAssistantController.refresh()
                                "research" -> {
                                    researchRefreshQuery = researchItem?.title
                                    detailCard = null
                                }
                                else -> Unit
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(uiText("Refresh")) }
                    }
                    if (card.widgetType == "research") researchItem?.sourceLinks.orEmpty().forEach { source ->
                        OutlinedButton(onClick = { runCatching { uriHandler.openUri(source.url) } }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) { Text(source.title); Text(source.url, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    if (researchItem?.availableActions?.contains(ScreenAction.SaveToNotebook) == true) {
                        val saveNotebookError = uiText("Could not save to Notebook")
                        com.halo.app.ui.components.HaloActionButton(
                            onClick = {
                                if (!savingResearch) {
                                    savingResearch = true
                                    coroutineScope.launch {
                                        try {
                                            val note = ManualDependencyGraph.notebookRepository.createNote(
                                                body = researchNoteBody(researchItem),
                                                source = com.halo.app.data.notebook.WorkspaceSource.USER_CREATED,
                                                title = researchItem.title.take(200),
                                            )
                                            detailCard = null
                                            onFullScreen(false)
                                            onOpen("notebook/note/${android.net.Uri.encode(note.id)}")
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            notice = saveNotebookError
                                        } finally {
                                            savingResearch = false
                                        }
                                    }
                                }
                            },
                            enabled = !savingResearch,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(uiText("Save to Notebook")) }
                    }
                    HaloReadAloud(data.title + ". " + detailSummary, compact = false)
                    if (card.widgetType == "calendar" && !calendarAllowed)
                        com.halo.app.ui.components.HaloActionButton(onClick = {
                            if (calendarDenied) context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${context.packageName}")))
                            else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                        }) { Text(uiText(if (calendarDenied) "Open Android settings" else "Allow calendar access")) }
                    data.route?.let { route -> com.halo.app.ui.components.HaloActionButton(onClick = { detailCard = null; onFullScreen(false); onOpen(route) }) { Text(uiText("Open source")) } }
                }
                Text(uiText(data.state, *data.stateArguments.toTypedArray()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    change(layout!!.cards.filterNot { it == card }, "Hidden from Screen. Its source is kept."); detailCard = null
                }) { Text(uiText("Hide from Screen")) }
            }
        }
    } }
    researchRefreshQuery?.let { query ->
        com.halo.app.ui.online.GroundedWebSearchDialog(query) { researchRefreshQuery = null }
    }
}

private fun researchNoteBody(item: ScreenItem): String = buildString {
    append((item.summary ?: "").take(8_000))
    val sources = item.sourceLinks.asSequence()
        .filter { link -> runCatching {
            val uri = java.net.URI(link.url)
            uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank()
        }.getOrDefault(false) }
        .distinctBy { it.url }
        .take(8)
        .toList()
    if (sources.isNotEmpty()) {
        append("\n\nSources\n")
        sources.forEach { source ->
            append("\n")
            append(source.title.take(200).ifBlank { source.url.take(2048) })
            append("\n")
            append(source.url.take(2048))
            append("\n")
        }
    }
    item.updatedAt?.let { append("\nRetrieved · ").append(it.toString()) }
}.take(12_000)

internal fun widgetTitle(type: String): String = when (type) {
    "clock" -> "Clock & date"; "weather" -> "Weather"; "calendar" -> "Calendar"
    "tasks" -> "Needs attention"; "notebook" -> "Continue in Notebook"; "study" -> "Continue studying"
    "automation_result" -> "Routine results"; "halo_activity" -> "What halo did"; "research" -> "Research"; "device" -> "This phone"; else -> "Reading & interests"
}

@Composable
internal fun screenWidgetTitle(type: String): String = if (type == "device") stringResource(R.string.screen_this_phone) else uiText(widgetTitle(type))

internal fun formatScreenTimestamp(instant: Instant, includeDate: Boolean, locale: Locale,
    zone: ZoneId = ZoneId.systemDefault()): String {
    val formatter = if (includeDate) DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        else DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    return formatter.withLocale(locale).format(instant.atZone(zone))
}

@Composable
private fun ScreenToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(uiText(label), Modifier.weight(1f)); Switch(checked, onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScreenWidgetCard(data: ScreenDisplay, card: ScreenCard, masked: Boolean, fullScreen: Boolean,
    editing: Boolean, onOpen: () -> Unit,
    appearance: ScreenAppearance = ScreenAppearance(), enabled: Boolean = true,
    editControls: (@Composable () -> Unit)? = null) {
    var previous by remember { mutableStateOf(data.updatedAt) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    var update by remember { mutableStateOf(false) }
    LaunchedEffect(data.updatedAt) {
        if (previous != null && previous != data.updatedAt) { update = true; delay(1200); update = false }
        previous = data.updatedAt
    }
    Surface(onClick = onOpen, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (appearance.translucent && !masked) .94f else 1f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (editing) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
        Column(Modifier.fillMaxWidth().padding(if (fullScreen) 24.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (masked) {
                HaloIcon(HaloGlyph.SECURITY, null)
                Text(uiText("Private card"), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.screen_private_hidden), style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (data.sourceTitle) data.title else uiText(data.title), Modifier.weight(1f), style =
                        screenTextStyle(if (card.widgetType == "clock") MaterialTheme.typography.displayLarge else MaterialTheme.typography.titleMedium,
                            appearance, if (card.widgetType == "clock") card.typography.clockPercent else card.typography.titlePercent))
                    if (update) HaloPackageFx(HaloPackageMoment.CARD_UPDATE, Modifier.size(40.dp))
                    else if (card.pinned) HaloIcon(HaloGlyph.NOTE, uiText("Pinned"), Modifier.size(16.dp))
                }
                Text(if (data.sourceSummary) data.summary else uiText(data.summary),
                    style = screenTextStyle(MaterialTheme.typography.bodyMedium, appearance, card.typography.valuePercent))
                Text(uiText(data.state, *data.stateArguments.toTypedArray()) + (data.updatedAt?.let { instant ->
                    " · " + formatScreenTimestamp(instant, data.showFullTimestamp, locale)
                } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (editing && editControls != null) editControls()
        }
    }
}
