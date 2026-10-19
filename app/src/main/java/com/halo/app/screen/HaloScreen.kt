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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private data class ScreenDisplay(
    val title: String, val summary: String, val state: String,
    val route: String?, val sensitive: Boolean = false, val updatedAt: Instant? = null,
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
    val daily by ManualDependencyGraph.dailyAssistantController.snapshot.collectAsStateWithLifecycle()
    val workspace by remember { ManualDependencyGraph.workspaceRepository.observe(WorkspaceQuery()) }
        .collectAsStateWithLifecycle(emptyList())
    val activity by ManualDependencyGraph.activityViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reduced = rememberHaloReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableStateOf(Instant.now()) }
    var customizing by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var chromeTouch by remember { mutableLongStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var detailIndex by remember { mutableStateOf<Int?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var canUndo by remember { mutableStateOf(false) }
    var calendarAllowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,
        Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) }
    var calendarEvent by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var calendarFailed by remember { mutableStateOf(false) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        calendarAllowed = it
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
            }))
        }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                        if (cursor.moveToFirst()) (cursor.getString(0) ?: uiTextFallbackCalendar()) to cursor.getLong(1) else null
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
            .withLocale(locale).format(now.atZone(ZoneId.systemDefault())), "On this phone", null)
        "calendar" -> ScreenDisplay("Next on your calendar", when {
            !calendarAllowed -> "Choose whether halo can read your calendar."
            calendarFailed -> "Your calendar could not be opened."
            calendarEvent == null -> "Nothing scheduled in the next 24 hours."
            else -> calendarEvent!!.first + " · " + DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
                .format(Instant.ofEpochMilli(calendarEvent!!.second).atZone(ZoneId.systemDefault()))
        }, if (!calendarAllowed) "Permission needed" else if (calendarFailed) "Needs attention" else "On this phone", null, true)
        "weather" -> ScreenDisplay("Weather", "A weather source has not been connected.", "Needs setup", "integrations")
        "interests" -> ScreenDisplay("For you", "Choose a reading source. Your notes and chats are never used to guess your interests.", "Choose a source", "notebook")
        "tasks" -> ScreenDisplay("Needs attention", daily.primary?.title ?: "You're caught up",
            if (daily.items.isEmpty()) "No pending items" else "${daily.items.size} updates", daily.primary?.action?.let {
                com.halo.app.ui.shell.dailyOwnerRoute(it)
            } ?: "activity", true)
        "notebook" -> workspace.firstOrNull()?.let { note ->
            val kind = when (note.type) {
                com.halo.app.data.notebook.WorkspaceObjectType.CANVAS -> "canvas"
                com.halo.app.data.notebook.WorkspaceObjectType.CONVERSATION -> "conversation"
                com.halo.app.data.notebook.WorkspaceObjectType.MEMORY -> "memory"
                com.halo.app.data.notebook.WorkspaceObjectType.NOTE -> "note"
                else -> "file"
            }
            ScreenDisplay("Continue", note.title, "Saved on this phone", "notebook/$kind/${android.net.Uri.encode(note.id.value)}", true)
        } ?: ScreenDisplay("Notebook", "Your saved work will appear here.", "Nothing saved yet", "notebook", true)
        "halo_activity" -> ScreenDisplay("What halo did", activity.events.firstOrNull()?.title ?: "No activity yet",
            "Activity", "activity", true)
        else -> {
            val item = if (card.itemId != null) routineItems.firstOrNull { it.id == card.itemId }
                else null
            if (item == null) ScreenDisplay("Routine results", "A finished routine will update this card.", "Waiting for a result", "automation", true)
            else ScreenDisplay(item.title, item.summary.orEmpty(),
                when (item.state) { ScreenItemState.STALE -> "Last update failed"; ScreenItemState.ERROR -> "Needs attention"; else -> "Updated" },
                item.sourceId?.let { "automation?routineId=${android.net.Uri.encode(it)}" } ?: "automation", true, item.updatedAt)
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
            IconButton(onClick = { customizing = !customizing }) { HaloIcon(HaloGlyph.NOTE, uiText("Customize")) }
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
                    TextButton(onClick = { customizing = !customizing }) { Text(uiText(if (customizing) "Done" else "Customize")) }
                    TextButton(onClick = { picker = true }) { Text(uiText("Add card")) }
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
            val cardsForDisplay = layout!!.cards
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val columns = when { maxWidth >= 840.dp -> 4; maxWidth >= 600.dp -> 3; else -> 2 }
                val enlarged = LocalDensity.current.fontScale >= 1.5f
                LazyVerticalGrid(GridCells.Fixed(columns), contentPadding = PaddingValues(bottom = 20.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    cardsForDisplay.forEachIndexed { index, card ->
                        val section = screenSection(card.widgetType)
                        if (index == 0 || screenSection(cardsForDisplay[index - 1].widgetType) != section)
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
                                fullScreen, customizing, index, layout!!.cards.size,
                                onOpen = { detailIndex = layout!!.cards.indexOf(card) },
                                onMove = { delta -> val cards = layout!!.cards.toMutableList(); val target = index + delta
                                    if (target in cards.indices) { val c = cards.removeAt(index); cards.add(target, c); change(cards, "Card moved") } },
                                onHide = { change(layout!!.cards.filterIndexed { i, _ -> i != index }, "Hidden from Screen. Its source is kept.") },
                                onPin = { change(layout!!.cards.mapIndexed { i, c -> if (i == index) c.copy(pinned = !c.pinned) else c }, "Card updated") },
                                onResize = { change(layout!!.cards.mapIndexed { i, c -> if (i == index) c.copy(size = when (c.size) {
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
                    TextButton(onClick = { add(type, result.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(uiText("Routine results") + " · " + result.title)
                    }
                } else TextButton(onClick = { add(type) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(uiText(widgetTitle(type)))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (settings) ModalBottomSheet(onDismissRequest = { settings = false }) {
        Text(uiText("Screen settings"), Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall)
        ScreenToggle("Hide private details in full screen", hidePrivate, store::setPrivacy)
        ScreenToggle("Stay on while charging", keepCharging, store::setKeepCharging)
        ScreenToggle("Dim at night", dimAtNight, store::setDimAtNight)
        Text(uiText("Night hours: 22:00–07:00. Your sources stay unchanged."), Modifier.padding(20.dp))
        Text(uiText("Starting layouts"), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
        listOf("Morning", "Focus", "Quiet").forEach { name ->
            TextButton(onClick = { store.saveLayout(ScreenWidgets.starter(name)); canUndo = true; notice = "Layout changed"; settings = false }) { Text(uiText(name)) }
        }
        Spacer(Modifier.height(24.dp))
    }
    detailIndex?.let { index -> layout?.cards?.getOrNull(index)?.let { card ->
        val data = display(card)
        ModalBottomSheet(onDismissRequest = { detailIndex = null }) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(uiText(if (fullScreen && hidePrivate && data.sensitive) "Private card" else data.title), style = MaterialTheme.typography.headlineSmall)
                if (fullScreen && hidePrivate && data.sensitive) Text(uiText("Private details are hidden. Exit full screen to see them."))
                else {
                    Text(uiText(data.summary), style = MaterialTheme.typography.bodyLarge)
                    HaloReadAloud(data.title + ". " + data.summary, compact = false)
                    if (card.widgetType == "calendar" && !calendarAllowed)
                        Button(onClick = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }) { Text(uiText("Allow calendar access")) }
                    data.route?.let { route -> Button(onClick = { detailIndex = null; onFullScreen(false); onOpen(route) }) { Text(uiText("Open source")) } }
                }
                Text(uiText(data.state), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    change(layout!!.cards.filterIndexed { i, _ -> i != index }, "Hidden from Screen. Its source is kept."); detailIndex = null
                }) { Text(uiText("Hide from Screen")) }
            }
        }
    } }
}

private fun uiTextFallbackCalendar() = "Calendar event"

internal fun widgetTitle(type: String): String = when (type) {
    "clock" -> "Clock & date"; "weather" -> "Weather"; "calendar" -> "Calendar"
    "tasks" -> "Needs attention"; "notebook" -> "Continue in Notebook"
    "automation_result" -> "Routine results"; "halo_activity" -> "What halo did"; else -> "Reading & interests"
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
    onHide: () -> Unit, onPin: () -> Unit, onResize: () -> Unit) {
    var previous by remember { mutableStateOf(data.updatedAt) }
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
                    Text(uiText(data.title), Modifier.weight(1f), style = when {
                        card.widgetType == "clock" -> MaterialTheme.typography.displaySmall
                        else -> MaterialTheme.typography.titleMedium
                    })
                    if (update) HaloPackageFx(HaloPackageMoment.CARD_UPDATE, Modifier.size(40.dp))
                    else if (card.pinned) HaloIcon(HaloGlyph.NOTE, uiText("Pinned"), Modifier.size(16.dp))
                }
                Text(uiText(data.summary), style = MaterialTheme.typography.bodyMedium)
                Text(uiText(data.state) + (data.updatedAt?.let { " · " + DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                    .format(it.atZone(ZoneId.systemDefault())) } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (editing) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onMove(-1) }, enabled = index > 0) { Text(uiText("Move up")) }
                TextButton(onClick = { onMove(1) }, enabled = index < count - 1) { Text(uiText("Move down")) }
                TextButton(onClick = onResize) { Text(uiText("Resize")) }
                TextButton(onClick = onPin) { Text(uiText(if (card.pinned) "Unpin" else "Pin")) }
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
