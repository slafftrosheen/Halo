package com.halo.app.ui.everyday

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.halo.app.everyday.organizer.*
import com.halo.app.language.uiText
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlannerCalendarsSheet(settings: PlannerCalendarSettings, busy: Boolean, onDismiss: () -> Unit,
    onUpdate: (Long, OrganizerCalendar, Boolean?, PlannerCalendarColor?) -> Unit) {
    EverydaySheet(onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(uiText("Calendars"), style = MaterialTheme.typography.headlineSmall)
            Text(uiText("Choose which calendars are visible in Planner. Alerts and sharing stay as set."),
                style = MaterialTheme.typography.bodyMedium)
            for (calendar in OrganizerCalendar.entries) {
                val appearance = settings.appearance(calendar)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(Color(appearance.color.argb), CircleShape))
                    Text(uiText(calendar.label), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    val showCalendar = uiText("Show %s", uiText(calendar.label))
                    Switch(appearance.visible, { onUpdate(settings.version, calendar, it, null) },
                        modifier = Modifier.semantics { contentDescription = showCalendar }, enabled = !busy)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (color in PlannerCalendarColor.entries) {
                        FilterChip(appearance.color == color, { onUpdate(settings.version, calendar, null, color) },
                            enabled = !busy, label = { Text(uiText(color.label)) },
                            leadingIcon = { Box(Modifier.size(10.dp).background(Color(color.argb), CircleShape)) })
                    }
                }
                HorizontalDivider()
            }
            TextButton(onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Close")) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlannerEventDrag(
    row: OrganizerOccurrence,
    zone: ZoneId,
    locale: Locale,
    onChange: (OrganizerScheduleEdit, Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val source = requireNotNull(row.effectiveTime)
    val timed = !row.allDay
    val end = requireNotNull(row.end)
    val canShorten = if (timed) java.time.Duration.between(row.start, end) > java.time.Duration.ofMinutes(15)
        else source.start.toLocalDate().plusDays(1) < requireNotNull(source.end).toLocalDate()
    val stepSize = with(LocalDensity.current) { (if (timed) 24.dp else 56.dp).toPx() }
    val latestOnChange by rememberUpdatedState(onChange)
    var operation by remember(row.key, row.record.version) { mutableStateOf<OrganizerScheduleEdit?>(null) }
    var steps by remember(row.key, row.record.version) { mutableIntStateOf(0) }
    val preview = remember(row, operation, steps, zone) {
        val selected = operation
        if (selected == null || steps == 0) null
        else runCatching { rescheduledEventTime(source, row, selected, steps, zone) }.getOrNull()
    }
    fun gesture(edit: OrganizerScheduleEdit): Modifier = Modifier.pointerInput(row.key, row.record.version, stepSize, edit, zone) {
        var distance = 0f
        detectDragGestures(
            onDragStart = { distance = 0f; steps = 0; operation = edit },
            onDragCancel = { distance = 0f; steps = 0; operation = null },
            onDragEnd = {
                val result = steps
                distance = 0f
                steps = 0
                operation = null
                if (result != 0 && runCatching { rescheduledEventTime(source, row, edit, result, zone) }.isSuccess) {
                    latestOnChange(edit, result)
                }
            },
        ) { change, amount ->
            change.consume()
            distance += amount.y
            steps = (distance / stepSize).roundToInt().coerceIn(-96, 96)
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(gesture(OrganizerScheduleEdit.MOVE).fillMaxWidth()) { content() }
        Surface(gesture(OrganizerScheduleEdit.RESIZE).fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                Text(uiText(if (timed) "Pull the lower edge to resize · 15-minute steps" else "Pull the lower edge to resize · whole days"),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
        if (operation != null && steps != 0) {
            if (preview == null) Text(uiText("This time is outside the calendar range."), color = MaterialTheme.colorScheme.error)
            else {
                val formatter = if (timed) DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                    else DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                val localFormatter = formatter.withLocale(locale)
                val starts = localFormatter.format(preview.start)
                val ends = localFormatter.format(requireNotNull(preview.end))
                Text("$starts – $ends",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton({ latestOnChange(OrganizerScheduleEdit.MOVE, -1) }) { Text(uiText("Move earlier")) }
            TextButton({ latestOnChange(OrganizerScheduleEdit.MOVE, 1) }) { Text(uiText("Move later")) }
            TextButton({ latestOnChange(OrganizerScheduleEdit.RESIZE, -1) }, enabled = canShorten) { Text(uiText("Shorten")) }
            TextButton({ latestOnChange(OrganizerScheduleEdit.RESIZE, 1) }) { Text(uiText("Extend")) }
        }
    }
}
