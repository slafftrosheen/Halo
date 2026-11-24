package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.everyday.organizer.OrganizerCalendar
import com.halo.app.everyday.organizer.OrganizerKind
import com.halo.app.everyday.organizer.OrganizerOccurrence
import com.halo.app.everyday.organizer.OrganizerStatus
import com.halo.app.everyday.organizer.TaskPriority
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloAlertDialog
import com.halo.app.ui.planner.v13.DayLoad
import com.halo.app.ui.planner.v13.Movable
import com.halo.app.ui.planner.v13.TooMuchToday
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun plannerMoveSuggestions(rows: List<OrganizerOccurrence>, date: LocalDate, now: Instant,
    zone: ZoneId): List<OrganizerOccurrence> {
    val from = date.atStartOfDay(zone).toInstant()
    val until = date.plusDays(1).atStartOfDay(zone).toInstant()
    val timed = rows.filter { !it.allDay && !it.completed && it.record.status == OrganizerStatus.ACTIVE }
        .mapNotNull { row ->
            val start = maxOf(row.start, from)
            val end = minOf(row.end ?: return@mapNotNull null, until)
            if (end > start) Triple(row, start, end) else null
        }.sortedBy { it.second }
    if (timed.size < 2) return emptyList()
    val start = timed.first().second
    var end = start
    var occupied = 0L
    for ((_, left, right) in timed) {
        if (right > end) {
            occupied += Duration.between(maxOf(left, end), right).seconds
            end = right
        }
    }
    val span = Duration.between(start, end).toMinutes().toInt()
    if (span < 1) return emptyList()
    val candidates = timed.filter { (row, _, _) ->
        val record = row.record
        record.kind == OrganizerKind.TASK && record.repeat == null && record.calendar == OrganizerCalendar.PERSONAL &&
            record.calendarUid == null && row.end?.isAfter(now) == true && row.start.atZone(zone).toLocalDate() == date
    }
    val proposed = TooMuchToday.suggest(DayLoad((occupied / 60).toInt(), span), candidates.map { (row, left, right) ->
        Movable(row.key, Duration.between(left, right).toMinutes().toInt().coerceAtLeast(1),
            row.record.priority == TaskPriority.URGENT, row.record.priority.ordinal)
    })
    return proposed.mapNotNull { next -> candidates.firstOrNull { it.first.key == next.id }?.first }
}

@Composable
internal fun PlannerLoadReview(rows: List<OrganizerOccurrence>, onDismiss: () -> Unit,
    onMove: (List<OrganizerOccurrence>) -> Unit) {
    var selected by remember(rows.map { it.key to it.record.version }) { mutableStateOf(emptySet<String>()) }
    HaloAlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sp13_too_much_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sp13_too_much_body, rows.size), style = MaterialTheme.typography.bodyMedium)
                rows.forEach { row -> EverydayToggle(row.record.title, row.key in selected, true) { checked ->
                    selected = if (checked) selected + row.key else selected - row.key
                } }
            }
        },
        confirmButton = { TextButton(onClick = { onMove(rows.filter { it.key in selected }) }, enabled = selected.isNotEmpty()) {
            Text(uiText("Move to tomorrow"))
        } },
        dismissButton = { TextButton(onDismiss) { Text(uiText("Cancel")) } })
}
