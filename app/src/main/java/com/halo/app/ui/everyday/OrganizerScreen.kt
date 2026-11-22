package com.halo.app.ui.everyday

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.organizer.*
import com.halo.app.everyday.organizer.android.*
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloAlertDialog
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.instruments.HaloInstrumentAction
import com.halo.app.ui.instruments.HaloInstrumentBar
import com.halo.app.ui.instruments.HaloInstrumentMotion
import com.halo.app.ui.presence.*
import com.halo.app.ui.zone5.*
import com.halo.app.ui.screens.notebook.ToolZoneHeader
import com.halo.app.ui.zone6.Choose
import com.halo.app.ui.zone6.rememberZone6PageSetting
import com.halo.app.ui.theme.HaloViewMode
import com.halo.app.ui.theme.rememberVisualPreferences
import com.halo.app.ui.toolkit.HaloPlannerHandoff
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrganizerScreen(model: OrganizerViewModel, onBack: () -> Unit, onClocks: () -> Unit, onBrief: () -> Unit,
    onOpenRelated: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val familyReady by Graph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
    val familyEnabled by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val kids = !familyReady || familyEnabled || state.kids
    val query by model.query.collectAsStateWithLifecycle()
    val day by model.day.collectAsStateWithLifecycle()
    val kind by model.kind.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val selectedDay = LocalDate.parse(day)
    val motion = HaloInstrumentMotion.level()
    val dateForward = HaloInstrumentMotion.pageEnter(forward = true, level = motion)
    val dateBackward = HaloInstrumentMotion.pageEnter(forward = false, level = motion)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val boot = remember(context) { OrganizerAndroidClock.bootId(context) }
    val now by produceState(Instant.now(), lifecycle, kind, state.selected?.timer?.startedAt) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { value = Instant.now(); delay(if (kind == "TIMER" || state.selected?.timer != null) 1000 else 60_000) }
        }
    }
    val access = remember(context) { OrganizerPermissionAccess(context) }
    var permission by remember { mutableStateOf(access.current()) }
    var permissionError by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle, kids) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            permission = access.current(); if (!kids) model.reconcile()
        } }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    val requestNotification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = access.current() }
    fun permissions(intent: android.content.Intent) {
        permissionError = runCatching { context.startActivity(intent) }.isFailure
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::previewImport) }
    var exportReview by rememberSaveable { mutableStateOf(false) }
    var exportVersions by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var dayInput by rememberSaveable(day) { mutableStateOf(day) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var speaking by rememberSaveable { mutableStateOf(false) }
    var instrumentCalendarFiles by rememberSaveable { mutableStateOf(false) }
    var instrumentDate by rememberSaveable { mutableStateOf(false) }
    var instrumentSearch by rememberSaveable { mutableStateOf(false) }
    var instrumentCalendars by rememberSaveable { mutableStateOf(false) }
    var instrumentDrag by rememberSaveable { mutableStateOf(false) }
    val layout = rememberZone6PageSetting("planner.view")
    val weekends = rememberZone6PageSetting("planner.weekends")
    val showWeekends = weekends.value != "off"
    val viewScope = rememberCoroutineScope()
    val defaultLayout = if (rememberVisualPreferences().viewMode == HaloViewMode.EASY) "easy" else "week"
    var layoutDraft by rememberSaveable { mutableStateOf<String?>(null) }
    val effectiveLayout = PlannerView.from(layout.value ?: defaultLayout).key
    var reviewingDateFromEasy by rememberSaveable { mutableStateOf(false) }
    var lastAppliedLayout by remember { mutableStateOf(effectiveLayout) }
    val calendarView = when {
        kids && effectiveLayout != "easy" -> PlannerView.WEEK
        !kids && layoutDraft == null && reviewingDateFromEasy && effectiveLayout == "easy" -> PlannerView.DAY
        else -> PlannerView.from(layoutDraft ?: effectiveLayout)
    }
    val easy = calendarView == PlannerView.EASY
    val today = now.atZone(zone).toLocalDate()
    val agendaDay = if (easy) today else selectedDay
    val samePeriod = calendarView in setOf(PlannerView.WEEK, PlannerView.MONTH) && agendaDay in state.calendarDays
    val currentRange = state.calendarView == calendarView.key && (state.agendaDate == agendaDay || samePeriod)
    val activeAgenda = when {
        !currentRange -> emptyList()
        state.agendaDate == agendaDay -> state.agenda
        else -> state.calendarDays[agendaDay].orEmpty()
    }
    val calendarCounts = state.weekCounts.takeIf { currentRange }.orEmpty()
    val calendarTints = remember(state.calendarDays, state.plannerCalendars, currentRange, kids) {
        if (!currentRange || kids) emptyMap()
        else state.calendarDays.mapValues { (_, rows) ->
            rows.asSequence().filter { !it.completed && it.record.kind in setOf(OrganizerKind.EVENT, OrganizerKind.TASK) }
                .map { it.record.calendar }.distinct().map {
                    androidx.compose.ui.graphics.Color(state.plannerCalendars.appearance(it).color.argb)
                }.toList()
        }
    }
    fun selectView(value: PlannerView) {
        if (kids || state.busy || state.draft != null) return
        model.open(null)
        model.kind("AGENDA")
        viewScope.launch { layout.apply(layout.value, value.key, defaultLayout) }
    }
    fun selectDate(value: LocalDate) {
        model.open(null)
        if (kind !in setOf("AGENDA", "EVENT")) model.kind("AGENDA")
        if (effectiveLayout == "easy" && !kids && layoutDraft == null) reviewingDateFromEasy = value != today
        model.day(value)
    }
    var deleting by remember { mutableStateOf<OrganizerRecord?>(null) }
    var purging by remember { mutableStateOf<OrganizerRecord?>(null) }
    var occurrence by remember { mutableStateOf<OrganizerOccurrence?>(null) }
    val canShow = familyReady && familyEnabled == state.kids
    val current = state.selected.takeIf { canShow }
    val selectedSlot = state.selectedOccurrence?.takeIf { it.record.id == current?.id }
    val leaveByEvent = current?.takeIf { event ->
        val start = if (selectedSlot != null) selectedSlot.start.takeUnless { selectedSlot.allDay }
        else event.time?.takeIf { !it.allDay && event.repeat == null && !state.occurrenceMissing }?.instant(zone)
        !kids && event.kind == OrganizerKind.EVENT && event.status == OrganizerStatus.ACTIVE && start != null && start > now
    }
    val canReviewLeaveBy = leaveByEvent != null && !state.busy && state.draft == null
    fun openLeaveBy() {
        if (canReviewLeaveBy) leaveByEvent?.let { model.reviewLeaveBy(it, selectedSlot?.originalStart) }
    }
    LaunchedEffect(calendarView) { model.calendarView(calendarView) }
    LaunchedEffect(kids) {
        if (kids) {
            adding = false; speaking = false; layoutDraft = null; reviewingDateFromEasy = false
            deleting = null; purging = null; occurrence = null
            instrumentCalendars = false; instrumentDrag = false
        }
    }
    LaunchedEffect(effectiveLayout, today, reviewingDateFromEasy) {
        if (lastAppliedLayout != effectiveLayout) {
            lastAppliedLayout = effectiveLayout
            reviewingDateFromEasy = false
            model.kind("AGENDA")
        }
        if (effectiveLayout == "easy" && !reviewingDateFromEasy) {
            model.day(today)
            model.kind("AGENDA")
            model.query("")
        }
    }
    val pendingHandoff by HaloPlannerHandoff.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingHandoff, kids, state.loading, state.busy, state.draft) {
        val handoff = pendingHandoff ?: return@LaunchedEffect
        if (!kids && !state.loading && !state.busy && state.draft == null) {
            val draft = HaloPlannerHandoff.take(handoff) ?: return@LaunchedEffect
            model.new(OrganizerKind.REMINDER, sourceTitle = draft.title, sourceLabel = draft.source, sourceTime = draft.time)
        }
    }
    val owner = ShellPageOwner("everyday_organizer", objectId = current?.id,
        sourceIdentity = "${current?.id}:${current?.version}:$kind:$day:${state.busy}:${state.draft != null}:$kids:${calendarView.key}",
        helpDescription = uiText("Choose a day to see its plan. Add by talking, then check the time and repeats before you save."),
        guidanceBlocked = state.loading || state.busy || state.draft != null || state.leaveBy != null || speaking ||
            layoutDraft != null || instrumentCalendars,
        disabledActionReasons = mapOf("page.organizer.leave" to uiText(if (leaveByEvent == null) "Open an upcoming timed event." else "Wait for the current action to finish.")),
        defaultDisabledActionReason = uiText(if (kids) "A parent manages the saved plan." else "Wait for the current action to finish."))
    BindShellPageActions(owner, listOf(
        ShellPageAction("page.organizer.add", uiText("Add item"), HaloGlyph.CALENDAR, !kids && !state.busy) { adding = true },
        ShellPageAction("page.organizer.speak", uiText("Add by talking"), HaloGlyph.LISTEN, !kids && !state.busy) { speaking = true },
        ShellPageAction("page.organizer.appearance", uiText("Planner view"), HaloGlyph.APPEARANCE, !kids && !state.busy) {
            layoutDraft = effectiveLayout
        },
        ShellPageAction("page.organizer.edit", uiText(if (current?.repeat != null) "Edit entire series" else "Edit item"), HaloGlyph.NOTE,
            current != null && !kids && !state.busy) { current?.let { model.edit(it) } },
        ShellPageAction("page.organizer.leave", uiText("Leave-by time"), HaloGlyph.WATCH, canReviewLeaveBy, onInvoke = ::openLeaveBy),
        ShellPageAction("page.organizer.undo", uiText("Undo removal"), HaloGlyph.PREVIOUS, state.undoId != null && !kids && !state.busy) { model.undo() },
        ShellPageAction("page.organizer.clocks", uiText("World clocks"), HaloGlyph.WATCH) { onClocks() },
        ShellPageAction("page.organizer.brief", uiText("Your day in Halo"), HaloGlyph.ACTIVITY) { onBrief() },
        ShellPageAction("page.organizer.import", uiText("Import calendar"), HaloGlyph.DOWNLOAD, !kids && !state.busy) {
            importer.launch(arrayOf("text/calendar", "application/ics", "text/plain", "application/octet-stream"))
        },
        ShellPageAction("page.organizer.export", uiText("Export calendar"), HaloGlyph.FILE, !kids && !state.busy) {
            exportVersions = arrayListOf(); exportReview = true
        },
    ))
    if (LocalShellHaloOrb.current) BindShellUtilityBand(owner) {
        ToolZoneHeader(uiText("Planner"), { if (current != null || state.missing) model.open(null) else onBack() })
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Zone5PageHeading(uiText(if (easy && current == null) "Today" else "Planner"),
                uiText(if (kids) "The plan shared with you." else "Your plans, on this phone."))
            if (!LocalShellHaloOrb.current) TextButton({ if (current != null || state.missing) model.open(null) else onBack() }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Back")) }
        }
        if (!kids) item {
            val enabled = !state.busy && state.draft == null
            HaloInstrumentBar("planner", mapOf(
                "planner.views" to HaloInstrumentAction({ layoutDraft = effectiveLayout }, enabled,
                    keyboardAction = { key -> PlannerView.shortcut(key.keyCode)?.let(::selectView) }),
                "planner.today_back_next" to HaloInstrumentAction({ selectDate(today) }, enabled, agendaDay == today,
                    keyboardAction = { key ->
                        selectDate(when (key.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> calendarView.shift(agendaDay, -1)
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> calendarView.shift(agendaDay, 1)
                            else -> today
                        })
                    }),
                "planner.new_event" to HaloInstrumentAction({ model.new(OrganizerKind.EVENT, selectedDate = agendaDay) }, enabled),
                "planner.add_by_talking" to HaloInstrumentAction({ speaking = true }, enabled),
                "planner.drag_to_move_resize" to HaloInstrumentAction({
                    instrumentDrag = !instrumentDrag
                    if (instrumentDrag) {
                        if (easy) reviewingDateFromEasy = true
                        model.open(null)
                        if (kind !in setOf("AGENDA", "EVENT")) model.kind("AGENDA")
                    }
                }, enabled, instrumentDrag),
                "planner.leave_by_time" to HaloInstrumentAction(::openLeaveBy, canReviewLeaveBy),
                "planner.calendars" to HaloInstrumentAction({ instrumentCalendars = true }, enabled,
                    state.plannerCalendars.appearances.values.any { !it.visible }),
                "planner.tasks" to HaloInstrumentAction({ model.open(null); model.kind("TASK") }, enabled, kind == "TASK"),
                "planner.mini_month" to HaloInstrumentAction({ instrumentDate = true }, enabled),
                "planner.search" to HaloInstrumentAction({ instrumentSearch = true }, enabled),
                "planner.import_export" to HaloInstrumentAction({ instrumentCalendarFiles = true }, enabled),
            ), primary = "planner.new_event")
            if (instrumentDrag) {
                Text(uiText("Drag an event to move it, or pull its lower edge to change its length."),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.loading || state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { item { EverydayMessage(it, true); TextButton(model::retry, enabled = !state.busy, modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Retry")) } } }
        state.notice?.takeIf { !kids }?.let { item { EverydayMessage(it) } }
        state.imported?.takeIf { !kids }?.let { result -> item {
            Text(uiText("%1\$d events added; %2\$d existing event IDs kept.", result.added.size, result.alreadyPresent.size))
        } }
        if (state.undoId != null && !kids) item { TextButton(model::undo, enabled = !state.busy) { Text(uiText("Undo removal")) } }
        if (state.missing) item { Text(uiText("This item is no longer available here. It may have been removed or its sharing changed.")); TextButton({ model.open(null) }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Open Planner")) } }
        if (current != null && canShow) {
            if(state.occurrenceMissing) item { EverydayMessage("This occurrence changed or was cancelled. The series details are shown below.",true) }
            state.selectedOccurrence?.let { selected -> item {
                Text(uiText("Selected occurrence"),style=MaterialTheme.typography.labelLarge)
                Text(selected.record.title,style=MaterialTheme.typography.headlineMedium)
                Text(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone).format(selected.start)+" · "+zone.id)
                selected.end?.let { Text(uiText("Ends %1\$s",DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone).format(it))) }
                if(selected.record.details.isNotBlank()) Text(selected.record.details)
                if (!kids) selected.examLessons.forEach { lesson ->
                    TextButton(onClick = { onOpenRelated("notebook/study/${android.net.Uri.encode(lesson.sessionId)}") },
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(lesson.title) }
                }
                if(selected.record.location.isNotBlank()) Text(selected.record.location)
                if(!kids) TextButton({ model.edit(current,selected) },enabled=!state.busy) { Text(uiText("Edit this occurrence")) }
                HorizontalDivider()
                Text(uiText("Series details"),style=MaterialTheme.typography.labelLarge)
            } }
            item {
                Text(current.title, style = MaterialTheme.typography.headlineMedium)
                Text(uiText(organizerKindLabel(current.kind)), style = MaterialTheme.typography.labelLarge)
                Text(uiText(statusLabel(current.status)), style = MaterialTheme.typography.labelLarge)
                if (current.details.isNotBlank()) Text(current.details)
                if (current.location.isNotBlank()) Text(current.location)
                if (!kids && current.kind in setOf(OrganizerKind.EVENT, OrganizerKind.TASK))
                    Text(uiText("Calendar: %s", uiText(current.calendar.label)), style = MaterialTheme.typography.labelMedium)
                current.time?.let { time ->
                    Text(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).format(time.start))
                    time.end?.let { Text(uiText("Ends %1\$s", DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).format(it))) }
                    Text(if (time.followDeviceZone) uiText("Follows phone time zone · %1\$s", zone.id) else time.zoneId)
                    if (time.allDay) Text(uiText("All day"))
                }
                current.repeat?.let { rule -> Text(uiText("Repeating series · %1\$s", uiText(repeatLabel(rule.frequency)))) }
                current.timer?.let { timer ->
                    Text(timerText(timer.remaining(now, OrganizerAndroidClock.elapsedMs(), boot)), style = MaterialTheme.typography.displaySmall)
                    val remaining = timer.remaining(now, OrganizerAndroidClock.elapsedMs(), boot)
                    val receipt = state.receipts.filter { it.recordVersion == current.version }.maxByOrNull { it.updatedAt }
                    Text(uiText(when {
                        current.status != OrganizerStatus.ACTIVE -> "Finished"
                        remaining > 0 && timer.startedAt != null -> "Running"
                        remaining > 0 -> "Paused"
                        receipt != null -> deliveryLabel(receipt.state)
                        else -> "Timer due · waiting for Android delivery"
                    }))
                }
                current.sourceLabel?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                current.sourceUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (!kids) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (leaveByEvent != null) TextButton(::openLeaveBy, enabled = canReviewLeaveBy) { Text(uiText("Leave-by time")) }
                    if (current.kind == OrganizerKind.TASK && current.repeat == null) TextButton({ model.complete(current) }, enabled = !state.busy) {
                        Text(uiText(if (current.status == OrganizerStatus.COMPLETED) "Reopen task" else "Complete task"))
                    }
                    if (current.kind == OrganizerKind.TASK && current.status == OrganizerStatus.ACTIVE) TextButton({ model.new(OrganizerKind.TASK, current.id) }, enabled = !state.busy) { Text(uiText("Add subtask")) }
                    if (current.timer != null && current.status == OrganizerStatus.ACTIVE) {
                        if (current.timer.startedAt != null) TextButton({ model.timer(current, "pause") }, enabled = !state.busy) { Text(uiText("Pause")) }
                        else if (current.timer.remainingMs > 0) TextButton({ model.timer(current, "resume") }, enabled = !state.busy) { Text(uiText("Resume")) }
                        TextButton({ model.timer(current, "stop") }, enabled = !state.busy) { Text(uiText("Stop timer")) }
                    }
                    if (current.status == OrganizerStatus.ACTIVE && current.kind != OrganizerKind.TASK) TextButton({ model.cancel(current) }, enabled = !state.busy) { Text(uiText("Cancel schedule")) }
                    TextButton({ deleting = current }, enabled = !state.busy) { Text(uiText(if (current.repeat != null) "Delete entire series" else "Move to trash")) }
                }
                if (current.repeat != null) Text(uiText("Choose a date in Agenda to edit, skip or complete one occurrence."), style = MaterialTheme.typography.bodySmall)
                TextButton({ model.open(null); model.kind("AGENDA") }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Open agenda")) }
            }
            items(state.subtasks, key = { "subtask:${it.id}" }) { child ->
                TextButton({ model.open(child.id) }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(child.title) }
            }
            if (!kids && current.links.isNotEmpty()) item {
                Text(uiText("Related notes, plans and activity"), style = MaterialTheme.typography.titleMedium)
                current.links.forEach { link -> TextButton({ model.openLink(link.objectId, onOpenRelated) }, Modifier.heightIn(min = 48.dp)) {
                    Text(state.linkChoices.find { it.id == link.objectId }?.title ?: uiText("Open related item"))
                } }
            }
            if(!kids) item {TextButton({onOpenRelated("activity?objectId=${android.net.Uri.encode("organizer:${current.id}")}")},Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(uiText("View item activity"))}}
            if (!kids && state.overrides.isNotEmpty()) item {
                Text(uiText("Individual occurrence changes"), style = MaterialTheme.typography.titleMedium)
                state.overrides.forEach { exception ->
                    Text(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(exception.originalStart))
                    Text(uiText(when (exception.disposition) { OccurrenceDisposition.CANCELLED -> "Skipped"; OccurrenceDisposition.COMPLETED -> "Completed"; OccurrenceDisposition.REPLACED -> "Edited occurrence" }))
                    TextButton({ model.resetOccurrence(current, exception) }, enabled = !state.busy) { Text(uiText("Restore original occurrence")) }
                }
            }
            if (!kids && (current.kind in setOf(OrganizerKind.ALARM, OrganizerKind.REMINDER, OrganizerKind.TIMER) || current.notifyBeforeMinutes != null)) {
                item {
                    Text(uiText("Delivery status"), style = MaterialTheme.typography.titleLarge)
                    if (state.receipts.isEmpty()) Text(uiText("Saved. Android scheduling is still being prepared, or no future alert is due."))
                    if (!permission.notifications) {
                        EverydayMessage("Notifications are off. The item is saved, but Android cannot alert you", true)
                        TextButton({ if (android.os.Build.VERSION.SDK_INT >= 33) requestNotification.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            else permissions(access.notificationIntent()) }) { Text(uiText("Allow notifications")) }
                        TextButton({ permissions(access.notificationIntent()) }) { Text(uiText("Open notification settings")) }
                    }
                    if (!permission.exact) {
                        Text(uiText("Exact alarm access is off. Android may deliver this later; allow exact alarms for precise timing"))
                        TextButton({ permissions(access.exactIntent()) }) { Text(uiText("Allow exact alarms")) }
                    }
                    if (current.kind in setOf(OrganizerKind.ALARM, OrganizerKind.TIMER) && !permission.fullScreen) {
                        Text(uiText("Full-screen alarms are unavailable. Android can still show an allowed notification."))
                        TextButton({ permissions(access.fullScreenIntent()) }) { Text(uiText("Full-screen alarm settings")) }
                    }
                    if (permissionError) EverydayMessage("Android could not open that settings page. Open Halo permissions in system settings.", true)
                    TextButton(model::reconcile, enabled = !state.busy) { Text(uiText("Check scheduling again")) }
                }
                items(state.receipts.sortedByDescending { it.triggerAt }.take(20), key = { "receipt:${it.occurrenceKey}" }) { receipt ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(uiText(deliveryLabel(receipt.state)), style = MaterialTheme.typography.titleMedium)
                        if (receipt.recordVersion != current.version) Text(uiText("Earlier item version · %1\$d", receipt.recordVersion), style = MaterialTheme.typography.bodySmall)
                        Text(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(receipt.triggerAt))
                        receipt.snoozedUntil?.let { Text(uiText("Snoozed")) }
                        if (receipt.recordVersion == current.version && receipt.state in setOf(DeliveryState.FIRED, DeliveryState.DELIVERING)) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5L, 10L, 30L).forEach { minutes -> TextButton({ model.snooze(receipt, minutes) }, enabled = !state.busy) { Text(uiText("Snooze %1\$d minutes", minutes)) } }
                            TextButton({ model.dismiss(receipt) }, enabled = !state.busy) { Text(uiText("Dismiss alert")) }
                        }
                    }
                }
            }
        } else if (!state.missing && canShow) {
            val dateMode = easy || kind in setOf("AGENDA", "EVENT") || layoutDraft != null
            item {
                if (!easy && !kids) {
                    PlannerDateBar(calendarView, agendaDay, today, locale, !state.busy, ::selectView, ::selectDate) { instrumentDate = true }
                    if (calendarView in setOf(PlannerView.WEEK, PlannerView.MONTH, PlannerView.AGENDA)) {
                        EverydayToggle(uiText("Show weekends"), showWeekends, !state.busy) { enabled ->
                            viewScope.launch { weekends.apply(weekends.value, if (enabled) "on" else "off", "on") }
                        }
                    }
                }
                if (!easy && (calendarView == PlannerView.WEEK || calendarView == PlannerView.MONTH)) AnimatedContent(selectedDay, transitionSpec = {
                    (if (targetState >= initialState) dateForward else dateBackward) togetherWith
                        fadeOut(HaloInstrumentMotion.spec(HaloInstrumentMotion.QUICK, motion))
                }, label = "Planner dates") { displayedDay ->
                    if (calendarView == PlannerView.MONTH) {
                        PlannerMonthGrid(YearMonth.from(displayedDay), displayedDay, today, calendarCounts,
                            locale, showWeekends, ::selectDate, calendarColors = calendarTints)
                    } else PlannerWeek(displayedDay, today, calendarCounts, locale,
                        showWeekends = kids || showWeekends, showNavigation = kids,
                        onDay = { if (kids) model.day(it) else selectDate(it) }, calendarColors = calendarTints)
                }
                if (dateMode) {
                    val readRows = activeAgenda.filter { easy || kind == "AGENDA" || it.record.kind == OrganizerKind.EVENT }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!kids) FilledTonalButton({ speaking = true }, enabled = !state.busy,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Add by talking")) }
                        PlannerReadDay(agendaDay, readRows, locale, zone, !state.loading && currentRange)
                    }
                }
                if (!kids) TextButton({ layoutDraft = effectiveLayout }, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText(if (easy) "Easy view settings" else "Planner view")) }
            }
            if (!easy) item {
                OutlinedTextField(query, model::query, label = { Text(uiText("Search your plan")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("AGENDA" to "Agenda") + OrganizerKind.entries.map { it.name to organizerKindLabel(it) } + if (kids) emptyList() else listOf("TRASH" to "Trash")).forEach { (key, label) ->
                        FilterChip(kind == key, { model.kind(key) }, modifier = Modifier.heightIn(min = if (kids) 56.dp else 48.dp), label = { Text(uiText(label)) })
                    }
                }
                if (kids && kind in setOf("AGENDA", "EVENT")) {
                    EverydayDateField(uiText("Agenda date"), dayInput, child = kids) { dayInput = it; runCatching { LocalDate.parse(it) }.getOrNull()?.let(model::day) }
                    FlowRow {
                        TextButton({ model.day(LocalDate.parse(day).minusDays(1)) }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Previous day")) }
                        TextButton({ model.day(LocalDate.now()) }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Today")) }
                        TextButton({ model.day(LocalDate.parse(day).plusDays(1)) }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Next day")) }
                    }
                }
            }
            if (dateMode) {
                val onlyEvents = !easy && kind == "EVENT"
                val rows = filterPlannerRows(activeAgenda, onlyEvents)
                plannerCalendarRows(
                    state, calendarView, agendaDay, today, now, zone, locale, kids, easy,
                    currentRange, showWeekends, onlyEvents, rows,
                    onOpen = model::open, onToggle = model::complete, onOccurrence = { occurrence = it },
                    onReschedule = if (instrumentDrag && !kids && !easy && !state.busy && state.draft == null) model::reschedule else null,
                )
                val nudging = state.plannerBriefSettings?.let { it.proactive && !it.quiet(now.atZone(zone).toLocalTime()) } == true
                if (!easy && kind == "AGENDA" && query.isBlank() && !state.loading && nudging &&
                    agendaDay == today && currentRange && rows.isNotEmpty()) {
                    val end = today.atTime(20, 0).atZone(zone).toInstant()
                    val start = maxOf(now, today.atTime(9, 0).atZone(zone).toInstant())
                    plannerFreeTime(rows, start, end)?.let { gap ->
                        item {
                            val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(uiText("Free time"), style = MaterialTheme.typography.titleSmall)
                                    Text(uiText("%1\$s to %2\$s is open.", time.format(gap.first), time.format(gap.second)))
                                }
                            }
                        }
                    }
                }
                val undated = state.records.filter { it.kind == OrganizerKind.TASK && it.time == null && it.status == OrganizerStatus.ACTIVE }
                if (!easy && kind == "AGENDA" && undated.isNotEmpty()) item { Text(uiText("Tasks without a due date"), style = MaterialTheme.typography.titleMedium) }
                if (!easy && kind == "AGENDA") items(undated, key = { "undated:${it.id}" }) { row -> OrganizerListRow(row, now, boot, { model.open(row.id) }) }
            } else {
                val rows = if (kind == "TRASH" && !kids) state.trash else state.records.filter { it.kind.name == kind }
                if (!state.loading && rows.isEmpty()) item { Text(uiText(if (kind == "TRASH") "Trash is empty." else "No matching items. Add your first item or change the search.")) }
                items(rows, key = { it.id }) { row ->
                    if (kind == "TRASH") Column {
                        Text(row.title, style = MaterialTheme.typography.titleMedium)
                        FlowRow {
                            TextButton({ model.restore(row) }, enabled = !state.busy) { Text(uiText("Restore")) }
                            TextButton({ purging = row }, enabled = !state.busy) { Text(uiText("Delete permanently")) }
                        }
                    } else OrganizerListRow(row, now, boot, { model.open(row.id) })
                }
            }
        }
    }
    if (instrumentCalendars && !kids) PlannerCalendarsSheet(state.plannerCalendars, state.busy,
        onDismiss = { instrumentCalendars = false }, onUpdate = model::updateCalendar)
    if (instrumentCalendarFiles && !kids) AlertDialog(onDismissRequest = { instrumentCalendarFiles = false },
        title = { Text(uiText("Calendar files")) }, text = { Column {
            TextButton(onClick = {
                instrumentCalendarFiles = false
                importer.launch(arrayOf("text/calendar", "application/ics", "text/plain", "application/octet-stream"))
            }) { Text(uiText("Import calendar")) }
            TextButton(onClick = { instrumentCalendarFiles = false; exportVersions = arrayListOf(); exportReview = true }) {
                Text(uiText("Export calendar"))
            }
        } }, confirmButton = { TextButton(onClick = { instrumentCalendarFiles = false }) { Text(uiText("Close")) } })
    if (instrumentDate && !kids) PlannerMonthPicker(agendaDay, today, calendarCounts, locale, onDismiss = { instrumentDate = false }) {
        selectDate(it)
        instrumentDate = false
    }
    if (instrumentSearch && !kids) AlertDialog(onDismissRequest = { instrumentSearch = false },
        title = { Text(uiText("Search your plan")) },
        text = { OutlinedTextField(query, model::query, label = { Text(uiText("Search")) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { model.open(null); instrumentSearch = false }) { Text(uiText("Done")) } })
    layoutDraft?.takeIf { !kids }?.let { preview ->
        layout.Choose(defaultLayout, uiText("Planner view"), PlannerView.entries.map { it.key to uiText(it.label) },
            preview, { layoutDraft = it }, { layoutDraft = null })
    }
    if (speaking && !kids) PlannerSpeakSheet(agendaDay, onDismiss = { speaking = false }) {
        speaking = false
        model.previewSpokenReminder(it)
    }
    state.leaveBy?.takeIf { !kids && it.eventId == current?.id }?.let { review ->
        PlannerLeaveBySheet(review, now, zone, locale, state.busy, state.error, onDismiss = model::dismissLeaveBy,
            onKeep = { model.keepLeaveBy(review, it) }, onOpenExisting = { reminder ->
                if (reminder.deletedAt == null) model.open(reminder.id)
                else { model.open(null); model.kind("TRASH") }
            })
    }
    if (adding && !kids) EverydaySheet({ adding = false }) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(uiText("Add item"), style = MaterialTheme.typography.headlineSmall)
            OrganizerKind.entries.forEach { type -> TextButton({ adding = false; model.new(type, selectedDate = agendaDay) }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText(organizerKindLabel(type))) } }
        }
    }
    if (!kids) state.draft?.let { OrganizerEditor(model, state, it) }
    if (exportReview && !kids) EverydaySheet({ if (!state.busy) exportReview = false }) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(uiText("Choose calendar events to export"), style = MaterialTheme.typography.headlineSmall)
                Text(uiText("A fixed calendar copy and its event list will be saved in Workspace. Then you can copy that file to a destination you choose."))
                state.error?.let { EverydayMessage(it, true) }
            }
            items(state.calendarEvents, key = { it.id }) { event ->
                val key = "${event.id}@${event.version}"
                EverydayToggle(event.title, key in exportVersions, !state.busy && (key in exportVersions || exportVersions.size < 200)) {
                    exportVersions = ArrayList(if (it) exportVersions + key else exportVersions - key)
                }
            }
            item {
                Text(uiText("Choose up to 200 events per file. If duplicate calendar UIDs exist, export one of those events at a time."), style = MaterialTheme.typography.bodySmall)
                val currentKeys = state.calendarEvents.map { "${it.id}@${it.version}" }.toSet()
                val stale = exportVersions.any { it !in currentKeys }
                if (stale) {
                    EverydayMessage("Some selected events changed or were removed. Clear the selection and review the current list.", true)
                    TextButton({ exportVersions = arrayListOf() }, enabled = !state.busy) { Text(uiText("Clear selection")) }
                }
                Button({ model.prepareCalendarExport(exportVersions.associate { it.substringBeforeLast('@') to it.substringAfterLast('@').toLong() }) },
                    enabled = !state.busy && !stale && exportVersions.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(uiText("Save calendar copy")) }
                TextButton({ exportReview = false }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(uiText("Cancel")) }
            }
        }
    }
    LaunchedEffect(state.exportFile?.objectId) { if (state.exportFile != null) exportReview = false }
    state.exportFile?.takeIf { !kids }?.let { file -> HaloAlertDialog(onDismissRequest = model::dismissExport,
        title = { Text(uiText("Calendar copy saved")) }, text = { Column {
            Text(file.displayName); Text(uiText("This saved copy has its own version. Later organizer edits will not change the exported bytes."))
            com.halo.app.ui.workspace.WorkspaceExportButton(file)
        } }, confirmButton = { TextButton(model::dismissExport) { Text(uiText("Done")) } }) }
    occurrence?.takeIf { !kids }?.let { row -> HaloAlertDialog(onDismissRequest = { occurrence = null }, title = { Text(uiText("This occurrence")) },
        text = { Column { Text(row.record.title); Text(uiText("Changes affect only the selected occurrence. The remaining series is kept.")) } },
        confirmButton = { TextButton({ model.edit(row.record, row); occurrence = null }) { Text(uiText("Edit this occurrence")) } },
        dismissButton = { Column {
            TextButton({ model.skip(row); occurrence = null }) { Text(uiText("Skip this occurrence")) }
            TextButton({ occurrence = null }) { Text(uiText("Cancel")) }
        } }) }
    deleting?.takeIf { !kids }?.let { row -> HaloAlertDialog(onDismissRequest = { deleting = null }, title = { Text(uiText("Move to trash?")) },
        text = { Text(uiText(if (row.repeat != null) "This removes the entire series and cancels its scheduled alerts. You can restore it from Trash." else "This removes the item and cancels its scheduled alert. You can restore it from Trash.")) },
        confirmButton = { TextButton({ model.remove(row); deleting = null }) { Text(uiText("Move to trash")) } },
        dismissButton = { TextButton({ deleting = null }) { Text(uiText("Cancel")) } }) }
    purging?.takeIf { !kids }?.let { row -> HaloAlertDialog(onDismissRequest = { purging = null }, title = { Text(uiText("Delete permanently?")) },
        text = { Text(uiText("This erases this item and its occurrence history. It cannot be undone. Linked notes are kept.")) },
        confirmButton = { TextButton({ model.purge(row); purging = null }) { Text(uiText("Delete permanently")) } },
        dismissButton = { TextButton({ purging = null }) { Text(uiText("Cancel")) } }) }
    state.import?.takeIf { !kids }?.let { preview -> EverydaySheet(model::cancelImport) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(uiText("Review calendar import"), style = MaterialTheme.typography.headlineSmall)
                Text(uiText("Only new event IDs are added. Existing items and items in Trash are kept. Embedded alarms and attachments are not imported."))
                Text(uiText("%1\$d supported events are ready to import.", preview.records.size))
                state.error?.let { EverydayMessage(it, true) }
            }
            items(preview.records, key = { it.id }) { Text(it.title) }
            items(preview.issues) { issue ->
                Text(uiText(if (issue.warning) "Import warning · item %1\$d" else "Item %1\$d was not imported", issue.item), color = if (issue.warning) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                Text(issue.reason, style = MaterialTheme.typography.bodySmall)
            }
            item {
                Button(model::importCalendar, enabled = !state.busy && preview.records.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(uiText("Import supported events")) }
                TextButton(model::cancelImport, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(uiText("Cancel")) }
            }
        }
    } }
}

private fun filterPlannerRows(entries: List<OrganizerOccurrence>, onlyEvents: Boolean): List<OrganizerOccurrence> =
    if (onlyEvents) entries.filter { it.record.kind == OrganizerKind.EVENT } else entries

private fun LazyListScope.plannerCalendarRows(
    state: OrganizerPageState, view: PlannerView, date: LocalDate, today: LocalDate, now: Instant, zone: ZoneId,
    locale: java.util.Locale, kids: Boolean, easy: Boolean, currentRange: Boolean, showWeekends: Boolean,
    onlyEvents: Boolean, rows: List<OrganizerOccurrence>, onOpen: (String, Instant?) -> Unit,
    onToggle: (OrganizerRecord, OrganizerOccurrence?) -> Unit, onOccurrence: (OrganizerOccurrence) -> Unit,
    onReschedule: ((OrganizerOccurrence, OrganizerScheduleEdit, Int) -> Unit)?,
) {
    val multiDay = !kids && (view == PlannerView.WEEK || view == PlannerView.AGENDA)
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    val canChange = !kids && !state.busy
    fun addRows(day: LocalDate, entries: List<OrganizerOccurrence>) {
        plannerOccurrenceRows(day, entries, state.conflicts, locale, zone, easy, canChange, onOpen, onToggle, onOccurrence,
            state.plannerCalendars.takeUnless { kids }, onReschedule)
    }
    if (!currentRange) {
        if (!state.loading && state.error == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        return
    }
    if (multiDay) {
        val days = view.dates(date).filter { showWeekends || !it.isPlannerWeekend() || it == date }
        val planned = days.associateWith { filterPlannerRows(state.calendarDays[it].orEmpty(), onlyEvents) }
        val hasItems = planned.values.any { it.isNotEmpty() }
        if (!hasItems) item { Text(uiText("Nothing planned in this period.")) }
        for ((day, entries) in planned) {
            if (entries.isNotEmpty() || view == PlannerView.WEEK && hasItems) {
                item(key = "heading:$day") {
                    Text(dateFormat.format(day), style = MaterialTheme.typography.titleMedium,
                        color = if (day == date) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (entries.isEmpty()) Text(uiText("Nothing planned for this day."), style = MaterialTheme.typography.bodySmall)
                }
                addRows(day, entries)
            }
        }
        return
    }
    if (!easy && view != PlannerView.DAY) item {
        Text(dateFormat.format(date), style = MaterialTheme.typography.titleMedium)
    }
    if (rows.isEmpty()) item {
        Text(uiText(if (easy || kids) "Nothing planned for this day." else "Nothing planned for this day. Add an item or choose another date."))
    }
    if (!kids && view == PlannerView.DAY) plannerDayHours(rows, date, date == today, now, zone, locale) { entries ->
        addRows(date, entries)
    } else addRows(date, rows)
}

private fun LazyListScope.plannerOccurrenceRows(
    date: LocalDate, rows: List<OrganizerOccurrence>, conflicts: Set<String>, locale: java.util.Locale, zone: ZoneId,
    easy: Boolean, canChange: Boolean, onOpen: (String, Instant?) -> Unit,
    onToggle: (OrganizerRecord, OrganizerOccurrence?) -> Unit, onOccurrence: (OrganizerOccurrence) -> Unit,
    calendars: PlannerCalendarSettings?, onReschedule: ((OrganizerOccurrence, OrganizerScheduleEdit, Int) -> Unit)?,
) {
    items(rows, key = { "$date:${it.key}" }) { row ->
        val startsHere = row.start.atZone(zone).toLocalDate() == date
        val content: @Composable () -> Unit = {
            val recurring = row.takeIf { it.record.repeat != null }
            val departure = if (startsHere) plannerDeparture(row, locale, zone) else null
            val calendar = if (row.record.kind in setOf(OrganizerKind.EVENT, OrganizerKind.TASK))
                calendars?.appearance(row.record.calendar) else null
            PlannerAgendaRow(row, plannerTime(row, locale, zone, date), departure, row.key in conflicts, easy, canChange,
                onOpen = { onOpen(row.record.id, recurring?.originalStart) },
                onToggle = { onToggle(row.record, recurring) }, onOccurrence = { onOccurrence(row) },
                calendarColor = calendar?.let { androidx.compose.ui.graphics.Color(it.color.argb) })
        }
        if (onReschedule != null && row.record.kind == OrganizerKind.EVENT && !row.completed && row.end != null && startsHere) {
            PlannerEventDrag(row, zone, locale, onChange = { operation, steps -> onReschedule(row, operation, steps) }, content = content)
        } else content()
    }
}

private fun LazyListScope.plannerDayHours(
    rows: List<OrganizerOccurrence>, date: LocalDate, showNow: Boolean, now: Instant, zone: ZoneId,
    locale: java.util.Locale, addRows: (List<OrganizerOccurrence>) -> Unit,
) {
    val dayStart = date.atStartOfDay(zone).toInstant()
    val hourFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    val hours = rows.groupBy { row ->
        if (row.allDay) null else maxOf(dayStart, row.start.atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant())
    }.toList().sortedBy { it.first }
    var placedNow = false
    fun nowMarker() {
        item(key = "time:now") {
            Text(uiText("Now · %s", hourFormat.format(now)), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        placedNow = true
    }
    for ((hour, entries) in hours) {
        if (!placedNow && showNow && hour != null && hour > now) nowMarker()
        item(key = "time:$hour") {
            Text(if (hour == null) uiText("All day") else hourFormat.format(hour), style = MaterialTheme.typography.titleSmall)
        }
        val split = if (!placedNow && showNow && hour != null) entries.indexOfFirst { it.start > now } else -1
        if (split < 0) addRows(entries)
        else {
            addRows(entries.take(split))
            nowMarker()
            addRows(entries.drop(split))
        }
    }
    if (!placedNow && showNow && rows.isNotEmpty()) nowMarker()
}

@Composable
private fun OrganizerListRow(record: OrganizerRecord, now: Instant, boot: String, open: () -> Unit) {
    Surface(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(record.title, style = MaterialTheme.typography.titleMedium)
            Text(uiText(statusLabel(record.status)), style = MaterialTheme.typography.labelLarge)
            if (record.kind == OrganizerKind.TASK && record.priority in setOf(TaskPriority.HIGH, TaskPriority.URGENT)) Text(uiText(if (record.priority == TaskPriority.URGENT) "Urgent" else "High"))
            record.time?.let { Text(it.start.toString().replace('T', ' ') + " · " + if (it.followDeviceZone) ZoneId.systemDefault().id else it.zoneId) }
            record.timer?.let { Text(timerText(it.remaining(now, OrganizerAndroidClock.elapsedMs(), boot)), style = MaterialTheme.typography.headlineSmall) }
            Text(uiText("Open item"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
private fun timerText(ms: Long): String { val seconds = (ms + 999) / 1000; return "%02d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60) }
private fun statusLabel(status: OrganizerStatus) = when (status) { OrganizerStatus.ACTIVE -> "Active"; OrganizerStatus.COMPLETED -> "Completed"; OrganizerStatus.CANCELLED -> "Cancelled" }
private fun repeatLabel(frequency: RepeatFrequency) = when (frequency) { RepeatFrequency.DAILY -> "Daily"; RepeatFrequency.WEEKLY -> "Weekly"; RepeatFrequency.MONTHLY -> "Monthly"; RepeatFrequency.YEARLY -> "Yearly" }
private fun deliveryLabel(state: DeliveryState) = when (state) {
    DeliveryState.PENDING -> "Preparing delivery"; DeliveryState.EXACT -> "Scheduled precisely"; DeliveryState.APPROXIMATE -> "Scheduled approximately"
    DeliveryState.NOTIFICATIONS_BLOCKED -> "Notification permission needed"; DeliveryState.DELIVERING -> "Delivering alert"
    DeliveryState.FIRED -> "Alert dispatched"; DeliveryState.DISMISSED -> "Alert dismissed"; DeliveryState.MISSED -> "Delivery time missed"
    DeliveryState.CANCELLED -> "Alert cancelled"; DeliveryState.FAILED -> "Delivery failed"
}
