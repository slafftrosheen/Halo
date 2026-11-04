package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.halo.app.everyday.organizer.*
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloAlertDialog
import java.time.DayOfWeek
import java.time.format.TextStyle

internal fun organizerKindLabel(kind: OrganizerKind) = when (kind) {
    OrganizerKind.EVENT -> "Calendar"; OrganizerKind.TASK -> "Tasks"; OrganizerKind.REMINDER -> "Reminders"
    OrganizerKind.ALARM -> "Alarms"; OrganizerKind.TIMER -> "Timers"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrganizerEditor(model: OrganizerViewModel, state: OrganizerPageState, draft: OrganizerDraft) {
    OrganizerEditorForm(state, draft, model::draft, model::save)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrganizerEditorForm(state: OrganizerPageState, draft: OrganizerDraft, onDraft: (OrganizerDraft?) -> Unit, onSave: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var parentQuery by rememberSaveable { mutableStateOf("") }
    var noteQuery by rememberSaveable { mutableStateOf("") }
    var discard by rememberSaveable { mutableStateOf(false) }
    val change: (OrganizerDraft) -> Unit = { if (!state.busy) onDraft(it) }
    EverydaySheet(onDismiss = { if (!state.busy) discard = true }) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText(if (draft.creating) "Add item" else if (draft.occurrence != null) "Edit this occurrence" else if (draft.base.repeat != null) "Edit entire series" else "Edit item"), style = MaterialTheme.typography.headlineSmall)
            Text(uiText(organizerKindLabel(draft.base.kind)), style = MaterialTheme.typography.labelLarge)
            state.error?.let { EverydayMessage(it, true) }
            OutlinedTextField(draft.title, { change(draft.copy(title = it.take(200))) }, enabled = !state.busy,
                label = { Text(uiText("Title")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = draft.title.isBlank())
            OutlinedTextField(draft.details, { change(draft.copy(details = it.take(16000))) }, enabled = !state.busy,
                label = { Text(uiText("Details")) }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6)
            if (draft.base.kind == OrganizerKind.EVENT) OutlinedTextField(draft.location, { change(draft.copy(location = it.take(1000))) },
                enabled = !state.busy, label = { Text(uiText("Location")) }, modifier = Modifier.fillMaxWidth())
            if (draft.base.kind == OrganizerKind.TASK) EverydayToggle(uiText("Due date"), draft.dated, !state.busy) { change(draft.copy(dated = it)) }
            if (draft.base.kind != OrganizerKind.TIMER && (draft.dated || draft.base.kind != OrganizerKind.TASK)) {
                if (draft.base.kind != OrganizerKind.ALARM) EverydayToggle(uiText("All day"), draft.allDay, !state.busy) { allDay ->
                    val first = runCatching { java.time.LocalDate.parse(draft.date) }.getOrNull()
                    val end = runCatching { java.time.LocalDate.parse(draft.endDate) }.getOrNull()
                    change(draft.copy(allDay = allDay, endDate = if (allDay && draft.hasEnd && first != null &&
                        (end == null || end <= first)) first.plusDays(1).toString() else draft.endDate))
                }
                EverydayDateField(uiText("Start date"), draft.date, !state.busy) { change(draft.copy(date = it)) }
                if (!draft.allDay) OutlinedTextField(draft.time, { change(draft.copy(time = it.take(8))) }, enabled = !state.busy,
                    label = { Text(uiText("Time · HH:mm")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (draft.hasEnd) {
                    EverydayDateField(uiText(if (draft.allDay) "End date · first day after the event" else "End date"), draft.endDate, !state.busy) { change(draft.copy(endDate = it)) }
                    if (!draft.allDay) OutlinedTextField(draft.endTime, { change(draft.copy(endTime = it.take(8))) }, enabled = !state.busy,
                        label = { Text(uiText("End time · HH:mm")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                OutlinedTextField(draft.zone, { change(draft.copy(zone = it.take(80))) }, enabled = !state.busy,
                    label = { Text(uiText("Time zone · for example Europe/Riga")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TextButton({ change(draft.copy(zone = java.time.ZoneId.systemDefault().id)) }, enabled = !state.busy) { Text(uiText("Use phone time zone")) }
                EverydayToggle(uiText("Follow the phone time zone when travelling"), draft.followZone, !state.busy) { change(draft.copy(followZone = it)) }
                Text(uiText("A missing daylight-saving time moves forward by the gap. A repeated time uses the earlier offset."), style = MaterialTheme.typography.bodySmall)
                if (draft.occurrence == null) {
                    Text(uiText("Repeat"), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf("" to "Does not repeat") + listOf("DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly", "YEARLY" to "Yearly")).forEach { (key, label) ->
                            FilterChip(draft.frequency == key, { change(draft.copy(frequency = key)) }, enabled = !state.busy, label = { Text(uiText(label)) })
                        }
                    }
                    if (draft.frequency.isNotBlank()) {
                        OutlinedTextField(draft.interval, { change(draft.copy(interval = it.take(3))) }, enabled = !state.busy,
                            label = { Text(uiText("Repeat interval · 1–366")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        if (draft.frequency == "WEEKLY") FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DayOfWeek.entries.forEach { day -> FilterChip(day in draft.weekdays, {
                                change(draft.copy(weekdays = if (day in draft.weekdays) draft.weekdays - day else draft.weekdays + day))
                            }, enabled = !state.busy, label = { Text(day.getDisplayName(TextStyle.FULL, locale)) }) }
                        }
                        OutlinedTextField(draft.count, { change(draft.copy(count = it.take(5), until = "")) }, enabled = !state.busy,
                            label = { Text(uiText("Number of occurrences · optional, 1–10000")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(draft.until, { change(draft.copy(until = it.take(10), count = "")) }, enabled = !state.busy,
                            label = { Text(uiText("Or repeat until · YYYY-MM-DD")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Text(uiText("Leave both end limits empty to keep repeating. Monthly dates absent from a month are skipped."), style = MaterialTheme.typography.bodySmall)
                    }
                    if (!draft.creating) EverydayToggle(uiText("Reset individual occurrence changes if the series schedule changes"), draft.resetOccurrences, !state.busy) { change(draft.copy(resetOccurrences = it)) }
                }
                if (draft.base.kind !in setOf(OrganizerKind.ALARM, OrganizerKind.REMINDER) && draft.occurrence == null) {
                    EverydayToggle(uiText("Notify me"), draft.notify, !state.busy) { change(draft.copy(notify = it)) }
                    if (draft.notify) OutlinedTextField(draft.lead, { change(draft.copy(lead = it.take(5))) }, enabled = !state.busy,
                        label = { Text(uiText("Minutes before · 0–10080")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            }
            if (draft.base.kind == OrganizerKind.TIMER && draft.creating) {
                OutlinedTextField(draft.duration, { change(draft.copy(duration = it.take(8))) }, enabled = !state.busy,
                    label = { Text(uiText("Duration in seconds · 1–2678400")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text(uiText("The timer starts when you save it."), style = MaterialTheme.typography.bodySmall)
            }
            if (draft.base.kind == OrganizerKind.TASK && draft.occurrence == null) {
                Text(uiText("Priority"), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { priority -> FilterChip(draft.priority == priority, { change(draft.copy(priority = priority)) }, enabled = !state.busy,
                        label = { Text(uiText(when (priority) { TaskPriority.LOW -> "Low"; TaskPriority.NORMAL -> "Normal"; TaskPriority.HIGH -> "High"; TaskPriority.URGENT -> "Urgent" })) }) }
                }
                Text(uiText("Parent task"), style = MaterialTheme.typography.titleMedium)
                FilterChip(draft.parentId == null, { change(draft.copy(parentId = null)) }, enabled = !state.busy, label = { Text(uiText("No parent task")) })
                OutlinedTextField(parentQuery, { parentQuery = it.take(200) }, label = { Text(uiText("Find a parent task")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                state.parentTasks.filter { it.id != draft.base.id && (it.title.contains(parentQuery, true) || it.id == draft.parentId) }.sortedBy { it.id != draft.parentId }.take(50).forEach { task ->
                    FilterChip(draft.parentId == task.id, { change(draft.copy(parentId = task.id)) }, enabled = !state.busy, label = { Text(task.title) })
                }
            }
            if (draft.occurrence == null) {
                Text(uiText("Related notes, plans and activity"), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(noteQuery, { noteQuery = it.take(200) }, label = { Text(uiText("Find a related item")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                organizerLinkSearch(state.linkChoices,noteQuery,draft.links).forEach { note ->
                    EverydayToggle(note.title, note.id in draft.links, !state.busy && (draft.links.size < 50 || note.id in draft.links)) {
                        change(draft.copy(links = if (it) draft.links + note.id else draft.links - note.id))
                    }
                }
                Text(uiText("Showing up to 90 matching items. Search to find older items. Activity and Timeline use retained history."),style=MaterialTheme.typography.bodySmall)
                draft.links.filter { id -> state.linkChoices.none { it.id == id } }.forEach { id ->
                    TextButton({ change(draft.copy(links = draft.links - id)) }, enabled = !state.busy) { Text(uiText("Remove link")) }
                }
                Text(uiText("Related items stay in their original instrument. Removing a link does not delete them."), style = MaterialTheme.typography.bodySmall)
            }
        }
        // Related-item results can fill many screens. The decision controls stay
        // outside that scroll area, including above the keyboard on short screens.
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton({ discard = true }, enabled = !state.busy, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(uiText("Cancel")) }
            Button(onSave, enabled = !state.busy && draft.title.isNotBlank(), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(uiText("Save")) }
        }
    }
    if (discard) HaloAlertDialog(onDismissRequest = { discard = false }, title = { Text(uiText("Discard this edit?")) },
        text = { Text(uiText("Your saved item is kept. The changes in this form will be discarded.")) },
        confirmButton = { TextButton({ discard = false; onDraft(null) }) { Text(uiText("Discard")) } },
        dismissButton = { TextButton({ discard = false }) { Text(uiText("Keep editing")) } })
}
