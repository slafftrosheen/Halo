package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.everyday.organizer.*
import com.halo.app.language.uiText
import java.time.*

@Composable
internal fun OrganizerInboxPromotion(record: OrganizerRecord, state: OrganizerPageState, model: OrganizerViewModel, dismiss: () -> Unit) {
    var target by rememberSaveable(record.id, record.version) { mutableStateOf("TASK") }
    var owner by rememberSaveable(record.id, record.version) { mutableStateOf(record.commitment.owner.name) }
    var dated by rememberSaveable(record.id, record.version) { mutableStateOf(record.time != null) }
    var date by rememberSaveable(record.id, record.version) { mutableStateOf((record.time?.start?.toLocalDate() ?: LocalDate.now()).toString()) }
    var time by rememberSaveable(record.id, record.version) { mutableStateOf((record.time?.start?.toLocalTime() ?: LocalTime.of(9, 0)).toString()) }
    var endDate by rememberSaveable(record.id, record.version) { mutableStateOf(date) }
    var endTime by rememberSaveable(record.id, record.version) { mutableStateOf("10:00") }
    var zone by rememberSaveable(record.id, record.version) { mutableStateOf(record.time?.zoneId ?: ZoneId.systemDefault().id) }
    var invalid by remember { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    EverydaySheet({ if (!state.busy) discard = true }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Promote Inbox item"), style = MaterialTheme.typography.headlineSmall)
            Text(record.title)
            state.error?.let { EverydayMessage(it, true) }
            OrganizerChoice("Plan as", target, listOf("TASK" to "Task", "EVENT" to "Event", "REMINDER" to "Reminder", "WATCH" to "Watch / monitor"), !state.busy) {
                target = it
                if (it == "WATCH" && owner == "ME") owner = "HALO"
            }
            OrganizerChoice("Responsibility", owner, CommitmentOwner.entries.filter { target != "WATCH" || it != CommitmentOwner.ME }
                .map { it.name to responsibilityLabel(it) }, !state.busy) { owner = it }
            if (target in setOf("TASK", "WATCH")) EverydayToggle(uiText("Set a date"), dated, !state.busy) { dated = it }
            if (dated || target in setOf("EVENT", "REMINDER")) {
                EverydayDateField(uiText("Date"), date, !state.busy) { date = it }
                OutlinedTextField(time, { time = it }, label = { Text(uiText("Time · HH:mm")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                if (target == "EVENT") {
                    EverydayDateField(uiText("End date"), endDate, !state.busy) { endDate = it }
                    OutlinedTextField(endTime, { endTime = it }, label = { Text(uiText("End time · HH:mm")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(zone, { zone = it.take(100) }, label = { Text(uiText("Time zone")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            }
            if (invalid) EverydayMessage("Check the date, time zone and event end before promoting this item.", true)
            Button({
                val scheduled = runCatching {
                    if (dated || target in setOf("EVENT", "REMINDER")) OrganizerTime(LocalDate.parse(date).atTime(LocalTime.parse(time)),
                        if (target == "EVENT") LocalDate.parse(endDate).atTime(LocalTime.parse(endTime)) else null, ZoneId.of(zone).id).also { it.validate() } else null
                }
                invalid = scheduled.isFailure
                if (!invalid) { submitted = true; model.promoteCapture(record, if (target == "WATCH") OrganizerKind.TASK else OrganizerKind.valueOf(target), scheduled.getOrNull(),
                    CommitmentOwner.valueOf(owner), if (target == "WATCH") CommitmentPurpose.WATCH else CommitmentPurpose.STANDARD, dismiss) }
            }, enabled = !state.busy && !submitted, modifier = Modifier.fillMaxWidth()) { Text(uiText("Promote item")) }
            if (submitted && !state.busy) Text(uiText("Read the current work and execution record before continuing. Do not repeat an uncertain operation."))
            TextButton({ discard = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(uiText("Cancel")) }
        }
    }
    if (discard) com.halo.app.ui.components.HaloAlertDialog({ discard = false }, title = { Text(uiText("Discard this edit?")) },
        text = { Text(uiText("Your saved item is kept. The changes in this form will be discarded.")) },
        confirmButton = { TextButton({ discard = false; dismiss() }) { Text(uiText("Discard")) } },
        dismissButton = { TextButton({ discard = false }) { Text(uiText("Keep editing")) } })
}
