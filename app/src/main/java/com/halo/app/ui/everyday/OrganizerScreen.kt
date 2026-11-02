package com.halo.app.ui.everyday

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.halo.app.ui.presence.*
import com.halo.app.ui.zone5.*
import com.halo.app.ui.screens.notebook.ToolZoneHeader
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
    var deleting by remember { mutableStateOf<OrganizerRecord?>(null) }
    var purging by remember { mutableStateOf<OrganizerRecord?>(null) }
    var occurrence by remember { mutableStateOf<OrganizerOccurrence?>(null) }
    val canShow = familyReady && familyEnabled == state.kids
    val current = state.selected.takeIf { canShow }
    LaunchedEffect(kids) { if (kids) { adding = false; deleting = null; purging = null; occurrence = null } }
    val owner = ShellPageOwner("everyday_organizer", objectId = current?.id,
        sourceIdentity = "${current?.id}:${current?.version}:$kind:$day:${state.busy}:${state.draft != null}:$kids",
        helpDescription = uiText("Keep your plan on this phone. Saved items, delivery permissions and actual notification outcomes are shown separately."),
        guidanceBlocked = state.loading || state.busy || state.draft != null,
        defaultDisabledActionReason = uiText(if (kids) "A parent manages the saved plan." else "Wait for the current action to finish."))
    BindShellPageActions(owner, listOf(
        ShellPageAction("page.organizer.add", uiText("Add item"), HaloGlyph.CALENDAR, !kids && !state.busy) { adding = true },
        ShellPageAction("page.organizer.edit", uiText(if (current?.repeat != null) "Edit entire series" else "Edit item"), HaloGlyph.NOTE,
            current != null && !kids && !state.busy) { current?.let { model.edit(it) } },
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
        ToolZoneHeader(uiText("Organizer"), { if (current != null || state.missing) model.open(null) else onBack() })
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Zone5PageHeading(uiText("Organizer"), uiText(if (kids) "The plan shared with you." else "Your calendar, tasks and time, saved on this phone."))
            if (!LocalShellHaloOrb.current) TextButton({ if (current != null || state.missing) model.open(null) else onBack() }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Back")) }
        }
        if (state.loading || state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { item { EverydayMessage(it, true); TextButton(model::retry, enabled = !state.busy, modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Retry")) } } }
        state.notice?.takeIf { !kids }?.let { item { EverydayMessage(it) } }
        state.imported?.takeIf { !kids }?.let { result -> item {
            Text(uiText("%1\$d events added; %2\$d existing event IDs kept.", result.added.size, result.alreadyPresent.size))
        } }
        if (state.undoId != null && !kids) item { TextButton(model::undo, enabled = !state.busy) { Text(uiText("Undo removal")) } }
        if (state.missing) item { Text(uiText("This item is no longer available here. It may have been removed or its sharing changed.")); TextButton({ model.open(null) }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Open organizer")) } }
        if (current != null && canShow) {
            if(state.occurrenceMissing) item { EverydayMessage("This occurrence changed or was cancelled. The series details are shown below.",true) }
            state.selectedOccurrence?.let { selected -> item {
                Text(uiText("Selected occurrence"),style=MaterialTheme.typography.labelLarge)
                Text(selected.record.title,style=MaterialTheme.typography.headlineMedium)
                Text(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone).format(selected.start)+" · "+zone.id)
                selected.end?.let { Text(uiText("Ends %1\$s",DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone).format(it))) }
                if(selected.record.details.isNotBlank()) Text(selected.record.details)
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
            item {
                OutlinedTextField(query, model::query, label = { Text(uiText("Search your plan")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("AGENDA" to "Agenda") + OrganizerKind.entries.map { it.name to organizerKindLabel(it) } + if (kids) emptyList() else listOf("TRASH" to "Trash")).forEach { (key, label) ->
                        FilterChip(kind == key, { model.kind(key) }, modifier = Modifier.heightIn(min = if (kids) 56.dp else 48.dp), label = { Text(uiText(label)) })
                    }
                }
                if (kind in setOf("AGENDA", "EVENT")) {
                    EverydayDateField(uiText("Agenda date"), dayInput, child = kids) { dayInput = it; runCatching { LocalDate.parse(it) }.getOrNull()?.let(model::day) }
                    FlowRow {
                        TextButton({ model.day(LocalDate.parse(day).minusDays(1)) }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Previous day")) }
                        TextButton({ model.day(LocalDate.now()) }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Today")) }
                        TextButton({ model.day(LocalDate.parse(day).plusDays(1)) }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Next day")) }
                    }
                }
            }
            if (kind in setOf("AGENDA", "EVENT")) {
                val rows = state.agenda.filter { kind == "AGENDA" || it.record.kind == OrganizerKind.EVENT }
                if (!state.loading && rows.isEmpty()) item { Text(uiText("Nothing planned for this day. Add an item or choose another date.")) }
                items(rows, key = { it.key }) { row ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(row.record.title, style = MaterialTheme.typography.titleMedium)
                            Text(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(row.start))
                            if (row.key in state.conflicts) Text(uiText("Overlaps another event"), color = MaterialTheme.colorScheme.error)
                            if (row.completed) Text(uiText("Completed"))
                            FlowRow {
                                TextButton({ model.open(row.record.id) }, Modifier.heightIn(min = if (kids) 56.dp else 48.dp)) { Text(uiText("Open item")) }
                                if (!kids && row.record.repeat != null) TextButton({ occurrence = row }, enabled = !state.busy) { Text(uiText("This occurrence")) }
                                if (!kids && row.record.kind == OrganizerKind.TASK) TextButton({ model.complete(row.record, row.takeIf { it.record.repeat != null }) }, enabled = !state.busy) { Text(uiText(if (row.completed) "Reopen task" else "Complete task")) }
                            }
                        }
                    }
                }
                val undated = state.records.filter { it.kind == OrganizerKind.TASK && it.time == null && it.status == OrganizerStatus.ACTIVE }
                if (kind == "AGENDA" && undated.isNotEmpty()) item { Text(uiText("Tasks without a due date"), style = MaterialTheme.typography.titleMedium) }
                if (kind == "AGENDA") items(undated, key = { "undated:${it.id}" }) { row -> OrganizerListRow(row, now, boot, { model.open(row.id) }) }
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
    if (adding && !kids) EverydaySheet({ adding = false }) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(uiText("Add item"), style = MaterialTheme.typography.headlineSmall)
            OrganizerKind.entries.forEach { type -> TextButton({ adding = false; model.new(type) }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText(organizerKindLabel(type))) } }
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
