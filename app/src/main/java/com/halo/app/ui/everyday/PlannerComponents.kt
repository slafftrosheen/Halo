package com.halo.app.ui.everyday

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.halo.app.everyday.organizer.OrganizerKind
import com.halo.app.everyday.organizer.OrganizerOccurrence
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloReadAloud
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.LocalPrivateSpeech
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.theme.HaloAccent
import com.halo.app.ui.theme.brandAccent
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
internal fun PlannerWeek(
    selectedDay: LocalDate, today: LocalDate, counts: Map<LocalDate, Int>,
    locale: Locale, onDay: (LocalDate) -> Unit,
) {
    val first = selectedDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val sage = brandAccent(dark = true, accent = HaloAccent.SAGE)
    val fullDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onDay(selectedDay.minusWeeks(1)) }) { HaloIcon(HaloGlyph.PREVIOUS, uiText("Previous week")) }
            Text(first.month.getDisplayName(TextStyle.FULL, locale) + " " + first.year, style = MaterialTheme.typography.titleMedium)
            IconButton({ onDay(selectedDay.plusWeeks(1)) }) { HaloIcon(HaloGlyph.NEXT, uiText("Next week")) }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val minTile = if (LocalDensity.current.fontScale > 1.25f) 62.dp else 48.dp
            val scroll = maxWidth < minTile * 7 + 24.dp
            Row(
                Modifier.fillMaxWidth().then(if (scroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(7) { offset ->
                    val date = first.plusDays(offset.toLong())
                    val count = counts[date]
                    val isToday = date == today
                    val isSelected = date == selectedDay
                    val caption = buildList {
                        add(fullDate.format(date))
                        if (isToday) add(uiText("Today"))
                        count?.let { add(if (it == 1) uiText("1 planned item") else uiText("%1\$d planned items", it)) }
                    }.joinToString(". ")
                    Surface(
                        onClick = { onDay(date) },
                        modifier = (if (scroll) Modifier.width(minTile) else Modifier.weight(1f))
                            .heightIn(min = 68.dp).semantics { contentDescription = caption; selected = isSelected },
                        shape = MaterialTheme.shapes.medium,
                        color = if (isToday) sage.fill else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (isToday) sage.onFill else MaterialTheme.colorScheme.onSurface,
                        border = if (isSelected) BorderStroke(2.dp,
                            if (isToday) sage.onFill else MaterialTheme.colorScheme.primary) else null,
                    ) {
                        Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelSmall)
                            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                            Text("•".repeat((count ?: 0).coerceAtMost(3)).ifEmpty { " " },
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.semantics { contentDescription = "" })
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlannerAgendaRow(
    row: OrganizerOccurrence, label: String, departure: String?, conflict: Boolean, easy: Boolean, canChange: Boolean,
    onOpen: () -> Unit, onToggle: () -> Unit, onOccurrence: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(onClick = onOpen, modifier = Modifier.weight(1f).heightIn(min = if (easy) 64.dp else 56.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, style = if (easy) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge)
                        Text(row.record.title, style = if (easy) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
                        if (row.record.location.isNotBlank()) Text(row.record.location, style = MaterialTheme.typography.bodyMedium)
                        departure?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (conflict) Text(uiText("Overlaps another event"), color = MaterialTheme.colorScheme.error)
                        if (row.completed && !canChange) Text(uiText("Completed"))
                    }
                }
                if (canChange && row.record.kind == OrganizerKind.TASK) {
                    val action = uiText(if (row.completed) "Reopen task" else "Complete task")
                    Surface(
                        onClick = onToggle, shape = CircleShape, modifier = Modifier.size(48.dp),
                        color = if (row.completed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (row.completed) HaloIcon(HaloGlyph.SUCCESS, action)
                            else Box(Modifier.size(20.dp).semantics { contentDescription = action }) {
                                Surface(Modifier.fillMaxSize(), shape = CircleShape,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {}
                            }
                        }
                    }
                }
            }
            if (!easy && canChange && row.record.repeat != null) {
                TextButton(onOccurrence, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("This occurrence")) }
            }
        }
    }
}

@Composable
internal fun plannerTime(row: OrganizerOccurrence, locale: Locale, zone: ZoneId): String =
    if (row.allDay) uiText("All day") else
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(row.start)

@Composable
internal fun plannerDeparture(row: OrganizerOccurrence, locale: Locale, zone: ZoneId): String? {
    val minutes = row.record.leaveBeforeMinutes?.takeUnless { row.allDay } ?: return null
    val departure = row.start.minusSeconds(minutes.toLong() * 60)
    val sameDay = departure.atZone(zone).toLocalDate() == row.start.atZone(zone).toLocalDate()
    val format = if (sameDay) DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) else DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    return uiText("Leave at %1\$s", format.withLocale(locale).withZone(zone).format(departure))
}

@Composable
internal fun PlannerReadDay(day: LocalDate, rows: List<OrganizerOccurrence>, locale: Locale, zone: ZoneId, enabled: Boolean) {
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(day)
    val script = buildList {
        add(date)
        if (rows.isEmpty()) add(uiText("Nothing planned for this day."))
        rows.forEach { row ->
            add(buildList {
                add(plannerTime(row, locale, zone))
                add(row.record.title)
                if (row.record.location.isNotBlank()) add(row.record.location)
                plannerDeparture(row, locale, zone)?.let { add(it) }
                if (row.completed) add(uiText("Completed"))
            }.joinToString(". "))
        }
    }.joinToString("\n\n") { it.trimEnd('.') + "." }
    CompositionLocalProvider(LocalPrivateSpeech provides true) {
        HaloReadAloud(script, enabled = enabled, label = uiText("Read my day"), compact = true)
    }
}

internal fun plannerFreeTime(rows: List<OrganizerOccurrence>, now: Instant, until: Instant): Pair<Instant, Instant>? {
    val minimum = Duration.ofHours(2)
    var cursor = now
    for (row in rows.filterNot { it.completed }.sortedBy { it.start }) {
        if (row.start >= until) break
        if (row.start > cursor && Duration.between(cursor, row.start) >= minimum) return cursor to row.start
        cursor = maxOf(cursor, row.end ?: row.start)
        if (cursor >= until) return null
    }
    return (cursor to until).takeIf { Duration.between(cursor, until) >= minimum }
}

@Composable
internal fun PlannerSpeakSheet(selectedDay: LocalDate, onDismiss: () -> Unit, onReview: (PlannerReminderCandidate) -> Unit) {
    var transcript by rememberSaveable { mutableStateOf("") }
    var request by rememberSaveable { mutableLongStateOf(1L) }
    EverydaySheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Add by talking"), style = MaterialTheme.typography.headlineSmall)
            Text(uiText("Tell Halo what to remind you about and when. You can edit the reminder before you save it."))
            Text(uiText("Every Thursday evening remind me to put the bins out."), style = MaterialTheme.typography.bodySmall)
            CompositionLocalProvider(LocalPrivateSpeech provides true) {
                HaloTextField(transcript, { transcript = it }, VoiceFieldKind.TEXT, Modifier.fillMaxWidth(),
                    label = { Text(uiText("What would you like to remember?")) }, minLines = 2, maxLines = 4,
                    maxChars = 500, dictationRequest = request, onRequestConsumed = { request = 0 })
            }
            Button({ onReview(plannerReminderCandidate(transcript, selectedDay)) }, enabled = transcript.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Review reminder")) }
            TextButton(onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Cancel")) }
        }
    }
}

@Composable
internal fun PlannerReminderReview(draft: OrganizerDraft, locale: Locale, enabled: Boolean, onChange: (OrganizerDraft) -> Unit) {
    val firstDay = runCatching { LocalDate.parse(draft.date) }.getOrNull()
    val localizedDay = firstDay?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(it) }
    val weekday = draft.weekdays.singleOrNull()
    val repeat = when (draft.frequency) {
        "WEEKLY" -> weekday?.let { uiText("Every %1\$s", it.getDisplayName(TextStyle.FULL, locale)) } ?: uiText("Weekly")
        "DAILY" -> uiText("Daily")
        "MONTHLY" -> uiText("Monthly")
        "YEARLY" -> uiText("Yearly")
        else -> uiText("Once")
    }
    var editing by rememberSaveable(draft.base.id) { mutableStateOf<String?>(null) }
    val time = if (draft.allDay) uiText("All day") else draft.time
    val reminder = if (draft.lead.toIntOrNull() == 0) uiText("On this phone at the scheduled time") else
        uiText("On this phone, %1\$s minutes before", draft.lead)
    val lines = listOf(
        "what" to (uiText("What") to draft.title.ifBlank { uiText("Choose a title") }),
        "when" to (uiText("When") to "$repeat · $time"),
        "remind" to (uiText("Remind") to reminder),
        "starts" to (uiText("Starts") to (localizedDay ?: draft.date)),
    )
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(8.dp)) {
            lines.forEach { (id, text) ->
                Surface(onClick = { editing = if (editing == id) null else id }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text.first, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text.second, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                when (id.takeIf { it == editing }) {
                    "what" -> OutlinedTextField(draft.title, { onChange(draft.copy(title = it.take(200))) }, enabled = enabled,
                        label = { Text(uiText("Title")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    "when" -> {
                        OutlinedTextField(draft.time, { onChange(draft.copy(time = it.take(8), allDay = false, spokenTimeGuess = null)) }, enabled = enabled,
                            label = { Text(uiText("Time · HH:mm")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(draft.frequency.isEmpty(), { onChange(draft.copy(frequency = "", weekdays = emptySet())) },
                                enabled = enabled, label = { Text(uiText("Once")) })
                            FilterChip(draft.frequency == "WEEKLY", {
                                onChange(draft.copy(frequency = "WEEKLY", interval = "1", weekdays = setOf((firstDay ?: LocalDate.now()).dayOfWeek)))
                            }, enabled = enabled, label = { Text(uiText("Weekly")) })
                            FilterChip(draft.frequency == "DAILY", { onChange(draft.copy(frequency = "DAILY", interval = "1", weekdays = emptySet())) },
                                enabled = enabled, label = { Text(uiText("Daily")) })
                        }
                    }
                    "remind" -> OutlinedTextField(draft.lead, { onChange(draft.copy(notify = true, lead = it.filter(Char::isDigit).take(5))) },
                        enabled = enabled, label = { Text(uiText("Minutes before · 0–10080")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    "starts" -> EverydayDateField(uiText("Start date"), draft.date, enabled) { value ->
                        val next = runCatching { LocalDate.parse(value) }.getOrNull()
                        onChange(draft.copy(date = value, spokenDateGuess = false,
                            weekdays = if (next != null && draft.frequency == "WEEKLY") setOf(next.dayOfWeek) else draft.weekdays))
                    }
                }
                if (id != "starts") HorizontalDivider()
            }
        }
    }
    draft.spokenTimeGuess?.let { guess ->
        val label = when (guess) {
            "morning" -> uiText("Morning")
            "afternoon" -> uiText("Afternoon")
            "evening" -> uiText("Evening")
            "noon" -> uiText("Noon")
            else -> null
        }
        Text(if (label == null) uiText("Halo suggested %1\$s. Tap When to change the time.", draft.time)
            else uiText("Halo used %1\$s for “%2\$s”. Tap When to change it.", draft.time, label),
            style = MaterialTheme.typography.bodyMedium)
    }
    if (draft.spokenDateGuess) Text(uiText("Halo suggested the start date. Tap Starts to change it."),
        style = MaterialTheme.typography.bodyMedium)
}
