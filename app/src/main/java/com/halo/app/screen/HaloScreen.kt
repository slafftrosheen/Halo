package com.halo.app.screen

import android.Manifest
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.halo.app.data.workspace.WorkspaceQuery
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.uiText
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

private data class ScreenDisplay(
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
    onOpen: (String) -> Unit, onTalk: () -> Unit, onChat: () -> Unit = {}) {
    val layout by store.layout.collectAsStateWithLifecycle()
    val routineItems by store.items.collectAsStateWithLifecycle()
    val hidePrivate by store.hidePrivate.collectAsStateWithLifecycle()
    val keepCharging by store.keepCharging.collectAsStateWithLifecycle()
    val dimAtNight by store.dimAtNight.collectAsStateWithLifecycle()
    val topics by store.topics.collectAsStateWithLifecycle()
    var topicsOpen by rememberSaveable { mutableStateOf(false) }
    var topicsDraft by rememberSaveable { mutableStateOf("") }
    val daily by ManualDependencyGraph.dailyAssistantController.snapshot.collectAsStateWithLifecycle()
    val workspace by remember { ManualDependencyGraph.workspaceRepository.observe(WorkspaceQuery()) }
        .collectAsStateWithLifecycle(emptyList())
    val activeStudySessions by remember { ManualDependencyGraph.studyUseCases.observeActiveSessions() }
        .collectAsStateWithLifecycle(emptyList())
    val activity by ManualDependencyGraph.activityViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val reduced = rememberHaloReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableStateOf(Instant.now()) }
    var customizing by rememberSaveable { mutableStateOf(false) }
    var showingArchivedCards by rememberSaveable { mutableStateOf(false) }
    fun setCustomizing(enabled: Boolean) {
        customizing = enabled
        if (!enabled) showingArchivedCards = false
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
    var requestError by remember { mutableStateOf(false) }
    var researchRefreshQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var savingResearch by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val widgetNames = ScreenWidgets.types.associateWith { uiText(widgetTitle(it)) }
    var detailCard by remember { mutableStateOf<ScreenCard?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var canUndo by remember { mutableStateOf(false) }
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
    fun change(cards: List<ScreenCard>, message: String) {
        latest.value?.let { store.saveLayout(it.copy(cards = cards)); notice = message; canUndo = true }
    }
    fun add(type: String, itemId: String? = null) {
        val current = latest.value ?: return
        if (current.cards.size >= 24) { notice = "Your Screen is full. Hide a card to make room."; return }
        if (current.cards.any { it.widgetType == type && it.itemId == itemId }) {
            notice = "This card is already on your Screen."; picker = false; return
        }
        change(current.cards + ScreenCard(type, itemId,
            if (type == "halo_activity") CardSize.COMPACT else CardSize.STANDARD, false), "Card added")
        picker = false
        if (type == "calendar" && !calendarAllowed) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
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
                if (power.isInteractive && !power.isPowerSaveMode) now = Instant.now()
                delay(60_000)
            }
        }
    }
    LaunchedEffect(now, calendarAllowed, layout?.cards?.any { it.widgetType == "calendar" }) {
        if (calendarAllowed && layout?.cards?.any { it.widgetType == "calendar" } == true) {
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
    BackHandler(fullScreen || customizing) { if (fullScreen) onFullScreen(false) else customizing = false }
    ScreenWindowPolicy(fullScreen, keepCharging, dimAtNight, now)
    val shift = if (fullScreen) ((now.epochSecond / 300L) % 5L).toInt() - 2 else 0
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    fun display(card: ScreenCard): ScreenDisplay = when (card.widgetType) {
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
        "interests" -> ScreenDisplay("For you", topics.ifBlank { "Choose a reading source. Your notes and chats are never used to guess your interests." }, "Needs setup", null, topics.isNotBlank(), sourceSummary = topics.isNotBlank())
        "research" -> routineItems.firstOrNull { it.id == card.itemId && it.sourceType == ScreenSourceType.RESEARCH }?.let { item ->
            val summary = item.summary.orEmpty()
            ScreenDisplay(item.title, summary.take(280) + if (summary.length > 280) "…" else "", if (item.expiresAt?.isBefore(now) == true) uiText("Saved result may be out of date")
                else "${uiText("Sourced research")} · ${item.sourceLinks.size} ${uiText("links")}",
                null, true, item.updatedAt, sourceTitle = true, sourceSummary = true, detailSummary = summary,
                showFullTimestamp = true)
        } ?: ScreenDisplay("Research", "Save a sourced web search to keep it here.", "No saved research yet", null, true)
        "tasks" -> ScreenDisplay("Needs attention", daily.primary?.title ?: "No tasks to catch up on",
            if (daily.items.isEmpty()) "No pending items" else "%1$d updates", daily.primary?.action?.let {
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
    }.haloStateAura(HaloLight.IDLE, MaterialTheme.colorScheme.primaryContainer, Color(0xFFFF9F6B), MaterialTheme.colorScheme.error).offset(x = shift.dp, y = shift.dp)
        .padding(horizontal = if (fullScreen) 24.dp else 16.dp)) {
        if (!fullScreen) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HomeSurfaceSwitch(true, { if (!it) onChat() }, Modifier.weight(1f))
            TextButton(onClick = { settings = true }) { Text(uiText(layout?.name ?: "Your Screen")) }
            IconButton(onClick = onTalk) { HaloIcon(HaloGlyph.LISTEN, uiText("Talk to halo")) }
            IconButton(onClick = { setCustomizing(!customizing) }) { HaloIcon(HaloGlyph.NOTE, uiText("Customize")) }
        }
        if (layout == null) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                HaloSignalFace(HaloSignalPresentation(label = "Your Screen"), size = 112.dp)
                Text(uiText("A little clarity, at a glance."), style = MaterialTheme.typography.headlineSmall)
                Text(uiText("Choose a starting layout. You can change every card."), modifier = Modifier.padding(vertical = 16.dp))
                listOf("Morning", "Focus", "Quiet").forEach { name ->
                    OutlinedButton(onClick = { store.saveLayout(ScreenWidgets.starter(name)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText(name)) }
                }
            }
        } else {
            if (!fullScreen || chromeVisible) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!fullScreen) {
                    TextButton(onClick = { setCustomizing(!customizing) }) { Text(uiText(if (customizing) "Done" else "Customize")) }
                    TextButton(onClick = { picker = true }) { Text(uiText("Add card")) }
                    TextButton(onClick = { askLayout = true }) { Text(uiText("Ask halo to change Screen")) }
                }
                TextButton(onClick = { onFullScreen(!fullScreen) }) { Text(uiText(if (fullScreen) "Exit full screen" else "Full screen")) }
                if (fullScreen) TextButton(onClick = onTalk) { Text(uiText("Talk to halo")) }
            }
            notice?.let { message ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText(message), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    if (canUndo) TextButton(onClick = { store.undo(); notice = "Change undone"; canUndo = false }) { Text(uiText("Undo")) }
                    TextButton(onClick = { notice = null }) { Text(uiText("Dismiss")) }
                }
            }
            val cardsForDisplay = layout!!.cards.withIndex().filter { it.value.archived == showingArchivedCards }
            if (customizing) TextButton(onClick = { showingArchivedCards = !showingArchivedCards }) {
                Text(uiText(if (showingArchivedCards) "Show current cards" else "Show archived cards"))
            }
            if (cardsForDisplay.isEmpty()) Text(uiText(if (showingArchivedCards) "No archived cards" else "No cards on Screen"),
                Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val columns = when { maxWidth >= 840.dp -> 4; maxWidth >= 600.dp -> 3; else -> 2 }
                val enlarged = LocalDensity.current.fontScale >= 1.5f
                LazyVerticalGrid(GridCells.Fixed(columns), contentPadding = PaddingValues(bottom = 20.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    cardsForDisplay.forEachIndexed { index, indexedCard ->
                        val sourceIndex = indexedCard.index
                        val card = indexedCard.value
                        val section = screenSection(card.widgetType)
                        if (index == 0 || screenSection(cardsForDisplay[index - 1].value.widgetType) != section)
                            item(key = "section:$index", span = { GridItemSpan(maxLineSpan) }) {
                                Text(uiText(section), Modifier.padding(top = 12.dp, bottom = 4.dp),
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        item(key = "card:$index:${card.widgetType}:${card.itemId}", span = { GridItemSpan(when {
                            enlarged || card.size == CardSize.EXPANDED -> columns
                            card.widgetType == "clock" && columns == 3 -> 3
                            card.widgetType == "clock" && fullScreen -> minOf(2, columns)
                            card.widgetType in setOf("clock", "weather") && columns == 2 -> 1
                            card.size == CardSize.STANDARD -> minOf(2, columns)
                            else -> 1
                        }) }) {
                        val data = display(card)
                        var appeared by remember { mutableStateOf(reduced) }
                        LaunchedEffect(Unit) { if (!reduced) delay(index.coerceAtMost(8) * 80L); appeared = true }
                        androidx.compose.animation.AnimatedVisibility(visible = appeared, enter = if (reduced) fadeIn(androidx.compose.animation.core.snap())
                            else fadeIn() + slideInVertically { it / 8 }) {
                            ScreenWidgetCard(data, card, fullScreen && hidePrivate && data.sensitive,
                                fullScreen, customizing, index, cardsForDisplay.size,
                                onOpen = { detailCard = card },
                                onMove = { delta ->
                                    val target = cardsForDisplay.getOrNull(index + delta)?.index
                                    if (target != null) {
                                        val cards = layout!!.cards.toMutableList()
                                        val moved = cards[sourceIndex]
                                        cards[sourceIndex] = cards[target]
                                        cards[target] = moved
                                        change(cards, "Card moved")
                                    }
                                },
                                onArchive = {
                                    change(layout!!.cards.mapIndexed { i, c -> if (i == sourceIndex) c.copy(archived = !c.archived) else c },
                                        if (showingArchivedCards) "Card restored" else "Card archived")
                                },
                                onHide = { change(layout!!.cards.filterIndexed { i, _ -> i != sourceIndex }, "Hidden from Screen. Its source is kept.") },
                                onPin = { change(layout!!.cards.mapIndexed { i, c -> if (i == sourceIndex) c.copy(pinned = !c.pinned) else c }, "Card updated") },
                                onResize = { change(layout!!.cards.mapIndexed { i, c -> if (i == sourceIndex) c.copy(size = when (c.size) {
                                    CardSize.COMPACT -> CardSize.STANDARD; CardSize.STANDARD -> CardSize.EXPANDED; CardSize.EXPANDED -> CardSize.COMPACT
                                }) else c }, "Card resized") })
                        }
                    }
                    }
                }
            }
        }
    }
    if (picker) ModalBottomSheet(onDismissRequest = { picker = false }) {
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
                    Text(uiText(widgetTitle(type)))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (settings) ModalBottomSheet(onDismissRequest = { settings = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        Text(uiText("Screen settings"), Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall)
        ScreenToggle("Hide private details in full screen", hidePrivate, store::setPrivacy)
        ScreenToggle("Stay on while charging", keepCharging, store::setKeepCharging)
        ScreenToggle("Dim at night", dimAtNight, store::setDimAtNight)
        TextButton(onClick = { topicsDraft = topics; topicsOpen = true; settings = false }) { Text(uiText("Choose topics")) }
        Text(uiText("Night hours: 22:00–07:00. Your sources stay unchanged."), Modifier.padding(20.dp))
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
            store.saveLayout(ScreenWidgets.starter(name)); canUndo = true; notice = "Layout changed"; resetLayout = null
        }) { Text(uiText("Apply")) } },
        dismissButton = { TextButton(onClick = { resetLayout = null }) { Text(uiText("Cancel")) } }) }
    if (topicsOpen) ModalBottomSheet(onDismissRequest = { topicsOpen = false }) {
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
        ModalBottomSheet(onDismissRequest = { previewType = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(uiText("Card preview"), style = MaterialTheme.typography.headlineSmall)
                ScreenWidgetCard(data, card, fullScreen && hidePrivate && data.sensitive, false, false, 0, 1,
                    {}, {}, {}, {}, {}, {})
                com.halo.app.ui.components.HaloActionButton(onClick = { add(type, previewItem); previewType = null },
                    modifier = Modifier.fillMaxWidth()) { Text(uiText("Add card")) }
                TextButton(onClick = { previewType = null; picker = true }) { Text(uiText("Back")) }
            }
        }
    }
    if (askLayout) ModalBottomSheet(onDismissRequest = { askLayout = false; proposedLayout = null }) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(uiText("Ask halo to change Screen"), style = MaterialTheme.typography.headlineSmall)
            Text(uiText("Add, hide, enlarge, or move a card to the top. Say the card name shown in Add card."))
            com.halo.app.ui.components.HaloTextField(layoutRequest, {
                layoutRequest = it; proposedLayout = null; requestError = false
            }, label = { Text(uiText("What would you like to change?")) },
                modifier = Modifier.fillMaxWidth(), kind = com.halo.app.ui.components.VoiceFieldKind.TEXT)
            if (requestError) Text(uiText("That change is unavailable. Use Customize to choose the card and action."),
                color = MaterialTheme.colorScheme.error)
            val proposal = proposedLayout
            if (proposal == null) com.halo.app.ui.components.HaloActionButton(onClick = {
                val base = latest.value
                val change = ScreenLayoutRequests.propose(layoutRequest, widgetNames)
                proposalBase = base
                proposedLayout = if (base != null && change != null) ScreenLayoutRequests.apply(base, change) else null
                requestError = proposedLayout == null
            }, enabled = layoutRequest.isNotBlank() && layout != null) { Text(uiText("Preview")) }
            else {
                Text(uiText("Review your new layout"), style = MaterialTheme.typography.titleMedium)
                proposal.cards.forEach { card -> Text(uiText(widgetTitle(card.widgetType)) + " · " + uiText(when (card.size) {
                    CardSize.COMPACT -> "Compact"; CardSize.STANDARD -> "Standard"; CardSize.EXPANDED -> "Large"
                })) }
                com.halo.app.ui.components.HaloActionButton(onClick = {
                    if (latest.value != proposalBase) { proposedLayout = null; requestError = true }
                    else { store.saveLayout(proposal); canUndo = true; notice = "Layout changed"; proposedLayout = null; askLayout = false }
                }) { Text(uiText("Apply")) }
            }
            TextButton(onClick = { proposedLayout = null; askLayout = false; customizing = true }) { Text(uiText("Customize")) }
        }
    }
    detailCard?.let { selected -> layout?.cards?.firstOrNull { it.widgetType == selected.widgetType && it.itemId == selected.itemId }?.let { card ->
        val data = display(card)
        val researchItem = if (card.widgetType == "research")
            routineItems.firstOrNull { it.id == card.itemId && it.sourceType == ScreenSourceType.RESEARCH } else null
        ModalBottomSheet(onDismissRequest = { detailCard = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (fullScreen && hidePrivate && data.sensitive) uiText("Private card") else if (data.sourceTitle) data.title else uiText(data.title), style = MaterialTheme.typography.headlineSmall)
                if (fullScreen && hidePrivate && data.sensitive) Text(uiText("Private details are hidden. Exit full screen to see them."))
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
                                            notice = uiText("Could not save to Notebook")
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
    "automation_result" -> "Routine results"; "halo_activity" -> "What halo did"; "research" -> "Research"; else -> "Reading & interests"
}

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
private fun ScreenWidgetCard(data: ScreenDisplay, card: ScreenCard, masked: Boolean, fullScreen: Boolean,
    editing: Boolean, index: Int, count: Int, onOpen: () -> Unit, onMove: (Int) -> Unit,
    onArchive: () -> Unit, onHide: () -> Unit, onPin: () -> Unit, onResize: () -> Unit) {
    var previous by remember { mutableStateOf(data.updatedAt) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    var update by remember { mutableStateOf(false) }
    LaunchedEffect(data.updatedAt) {
        if (previous != null && previous != data.updatedAt) { update = true; delay(1200); update = false }
        previous = data.updatedAt
    }
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (editing) 2.dp else 1.dp,
            if (editing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(if (fullScreen) 24.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (masked) {
                HaloIcon(HaloGlyph.SECURITY, null)
                Text(uiText("Private card"), style = MaterialTheme.typography.titleMedium)
                Text(uiText("Details are hidden in full screen."), style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (data.sourceTitle) data.title else uiText(data.title), Modifier.weight(1f), style = when {
                        card.widgetType == "clock" -> MaterialTheme.typography.displaySmall
                        else -> MaterialTheme.typography.titleMedium
                    })
                    if (update) HaloPackageFx(HaloPackageMoment.CARD_UPDATE, Modifier.size(40.dp))
                    else if (card.pinned) HaloIcon(HaloGlyph.NOTE, uiText("Pinned"), Modifier.size(16.dp))
                }
                Text(if (data.sourceSummary) data.summary else uiText(data.summary), style = MaterialTheme.typography.bodyMedium)
                Text(uiText(data.state, *data.stateArguments.toTypedArray()) + (data.updatedAt?.let { instant ->
                    " · " + formatScreenTimestamp(instant, data.showFullTimestamp, locale)
                } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (editing) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onMove(-1) }, enabled = index > 0) { Text(uiText("Move up")) }
                TextButton(onClick = { onMove(1) }, enabled = index < count - 1) { Text(uiText("Move down")) }
                TextButton(onClick = onResize) { Text(uiText("Resize")) }
                TextButton(onClick = onPin) { Text(uiText(if (card.pinned) "Unpin" else "Pin")) }
                TextButton(onClick = onArchive) { Text(uiText(if (card.archived) "Restore" else "Archive")) }
                TextButton(onClick = onHide) { Text(uiText("Hide")) }
            }
        }
    }
}


private fun screenSection(type: String): String = when (type) {
    "clock", "weather", "calendar" -> "Now"
    "tasks" -> "Needs attention"
    "notebook" -> "Continue"
    else -> "For you"
}
