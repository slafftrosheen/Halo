package com.halo.app.ui.everyday

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

internal enum class PlannerView(val key: String, val label: String) {
    DAY("day", "Day view"), WEEK("week", "Week view"), MONTH("month", "Month view"),
    AGENDA("agenda", "Agenda view"), EASY("easy", "Easy view · today only");

    fun shift(date: LocalDate, amount: Long): LocalDate = when (this) {
        DAY, EASY -> date.plusDays(amount)
        WEEK -> date.plusWeeks(amount)
        MONTH -> date.plusMonths(amount)
        AGENDA -> date.plusWeeks(amount * 2)
    }

    fun dates(date: LocalDate): List<LocalDate> = when (this) {
        DAY, EASY -> listOf(date)
        WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { first ->
            List(7) { first.plusDays(it.toLong()) }
        }
        MONTH -> List(date.lengthOfMonth()) { date.withDayOfMonth(it + 1) }
        AGENDA -> List(14) { date.plusDays(it.toLong()) }
    }

    companion object {
        fun from(value: String?): PlannerView = entries.find { it.key == value } ?: WEEK

        fun shortcut(keyCode: Int): PlannerView? = when (keyCode) {
            android.view.KeyEvent.KEYCODE_1 -> DAY
            android.view.KeyEvent.KEYCODE_2 -> WEEK
            android.view.KeyEvent.KEYCODE_3 -> MONTH
            android.view.KeyEvent.KEYCODE_4 -> AGENDA
            else -> null
        }
    }
}

internal fun LocalDate.isPlannerWeekend() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlannerDateBar(view: PlannerView, date: LocalDate, today: LocalDate, locale: Locale, enabled: Boolean,
    onView: (PlannerView) -> Unit, onDate: (LocalDate) -> Unit, onCalendar: () -> Unit) {
    val dates = view.dates(date)
    val short = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val dateLabel = when (view) {
        PlannerView.DAY, PlannerView.EASY -> DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date)
        PlannerView.MONTH -> DateTimeFormatter.ofPattern("LLLL uuuu", locale).format(date)
        PlannerView.WEEK, PlannerView.AGENDA -> "${short.format(dates.first())} – ${short.format(dates.last())}"
    }
    val back = uiText(when (view) {
        PlannerView.WEEK -> "Previous week"; PlannerView.MONTH -> "Previous month"; PlannerView.AGENDA -> "Previous two weeks"
        else -> "Previous day"
    })
    val next = uiText(when (view) {
        PlannerView.WEEK -> "Next week"; PlannerView.MONTH -> "Next month"; PlannerView.AGENDA -> "Next two weeks"
        else -> "Next day"
    })
    Column(Modifier.fillMaxWidth().onPreviewKeyEvent { key ->
        val native = key.nativeKeyEvent
        if (!enabled || native.action != android.view.KeyEvent.ACTION_DOWN || native.repeatCount != 0 ||
            native.isAltPressed || native.isCtrlPressed || native.isShiftPressed) return@onPreviewKeyEvent false
        val target = PlannerView.shortcut(native.keyCode)
        when {
            target != null -> onView(target)
            native.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT -> onDate(view.shift(date, -1))
            native.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> onDate(view.shift(date, 1))
            native.keyCode == android.view.KeyEvent.KEYCODE_T -> onDate(today)
            else -> return@onPreviewKeyEvent false
        }
        true
    }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onDate(view.shift(date, -1)) }, enabled = enabled) { HaloIcon(HaloGlyph.PREVIOUS, back) }
            Text(dateLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton({ onDate(view.shift(date, 1)) }, enabled = enabled) { HaloIcon(HaloGlyph.NEXT, next) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton({ onDate(today) }, enabled = enabled) { Text(uiText("Today")) }
            TextButton(onCalendar, enabled = enabled) { Text(uiText("Jump to date")) }
        }
    }
}

@Composable
internal fun PlannerWeek(
    selectedDay: LocalDate, today: LocalDate, counts: Map<LocalDate, Int>,
    locale: Locale, showWeekends: Boolean = true, showNavigation: Boolean = true, onDay: (LocalDate) -> Unit,
    calendarColors: Map<LocalDate, List<androidx.compose.ui.graphics.Color>> = emptyMap(),
) {
    val first = selectedDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val sage = brandAccent(dark = true, accent = HaloAccent.SAGE)
    val fullDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showNavigation) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onDay(selectedDay.minusWeeks(1)) }) { HaloIcon(HaloGlyph.PREVIOUS, uiText("Previous week")) }
            Text(first.month.getDisplayName(TextStyle.FULL, locale) + " " + first.year, style = MaterialTheme.typography.titleMedium)
            IconButton({ onDay(selectedDay.plusWeeks(1)) }) { HaloIcon(HaloGlyph.NEXT, uiText("Next week")) }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val minTile = if (LocalDensity.current.fontScale > 1.25f) 62.dp else 48.dp
            val dayCount = if (showWeekends) 7 else 5
            val scroll = maxWidth < minTile * dayCount + 4.dp * (dayCount - 1)
            Row(
                Modifier.fillMaxWidth().then(if (scroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(dayCount) { offset ->
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
                            PlannerCalendarDayMarks(count ?: 0, calendarColors[date].orEmpty())
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlannerMonthGrid(month: YearMonth, selectedDay: LocalDate, today: LocalDate, counts: Map<LocalDate, Int>,
    locale: Locale, showWeekends: Boolean, onDay: (LocalDate) -> Unit,
    calendarColors: Map<LocalDate, List<androidx.compose.ui.graphics.Color>> = emptyMap()) {
    val first = month.atDay(1)
    val origin = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val rows = (first.dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7
    val weekdays = if (showWeekends) 7 else 5
    val fullDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    val sage = brandAccent(dark = true, accent = HaloAccent.SAGE)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (1..weekdays).forEach { offset ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val weekday = DayOfWeek.of(offset)
                    Text(weekday.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                        Modifier.semantics { contentDescription = weekday.getDisplayName(TextStyle.FULL, locale) },
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        repeat(rows) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(weekdays) { offset ->
                    val date = origin.plusDays((week * 7L) + offset)
                    val isToday = date == today
                    val picked = date == selectedDay
                    val daysInMonth = YearMonth.from(date) == month
                    val count = counts[date] ?: 0
                    val caption = if (count == 0) fullDate.format(date) else
                        "${fullDate.format(date)}. ${uiText(if (count == 1) "1 planned item" else "%1\$d planned items", count)}"
                    Surface(onClick = { onDay(date) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                        contentDescription = caption
                        selected = picked
                    }, shape = MaterialTheme.shapes.small,
                        color = if (isToday) sage.fill else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (isToday) sage.onFill else if (daysInMonth) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = if (picked) BorderStroke(2.dp, if (isToday) sage.onFill else MaterialTheme.colorScheme.primary) else null) {
                        Column(Modifier.padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium)
                            if (count > 0) PlannerCalendarDayMarks(count, calendarColors[date].orEmpty())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannerCalendarDayMarks(count: Int, calendarColors: List<androidx.compose.ui.graphics.Color>) {
    val defaultColor = LocalContentColor.current
    Row(Modifier.heightIn(min = 14.dp).semantics { contentDescription = "" },
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (color in calendarColors.ifEmpty { List(count.coerceAtMost(3)) { defaultColor } }) {
            Surface(Modifier.size(4.dp), shape = CircleShape, color = color) {}
        }
    }
}

@Composable
internal fun PlannerMonthPicker(selectedDay: LocalDate, today: LocalDate, counts: Map<LocalDate, Int>, locale: Locale,
    onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    var yearMonth by rememberSaveable(selectedDay.toString()) { mutableStateOf(YearMonth.from(selectedDay).toString()) }
    var draft by rememberSaveable(selectedDay.toString()) { mutableStateOf(selectedDay.toString()) }
    var typing by rememberSaveable { mutableStateOf(false) }
    val month = YearMonth.parse(yearMonth)
    val chosen = runCatching { LocalDate.parse(draft) }.getOrNull()
    val monthFormat = DateTimeFormatter.ofPattern("LLLL uuuu", locale)
    fun jump(date: LocalDate) {
        draft = date.toString()
        yearMonth = YearMonth.from(date).toString()
    }
    EverydaySheet(onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText("Jump to date"), style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ yearMonth = month.minusMonths(1).toString() }) { HaloIcon(HaloGlyph.PREVIOUS, uiText("Previous month")) }
                Text(monthFormat.format(month), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton({ yearMonth = month.plusMonths(1).toString() }) { HaloIcon(HaloGlyph.NEXT, uiText("Next month")) }
            }
            PlannerMonthGrid(month, chosen ?: selectedDay, today, counts, locale, showWeekends = true, onDay = ::jump)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ jump(today) }, modifier = Modifier.weight(1f)) { Text(uiText("Today")) }
                TextButton({ typing = !typing }, modifier = Modifier.weight(1f)) { Text(uiText(if (typing) "Hide date field" else "Type a date")) }
            }
            if (typing) EverydayDateField(uiText("Date"), draft) { entered ->
                val typed = runCatching { LocalDate.parse(entered) }.getOrNull()
                if (typed != null) jump(typed) else draft = entered
            }
            Button({ chosen?.let(onPick) }, Modifier.fillMaxWidth().heightIn(min = 52.dp),
                enabled = chosen != null && YearMonth.from(chosen) == month) { Text(uiText("Go")) }
        }
    }
}

@Composable
internal fun PlannerAgendaRow(
    row: OrganizerOccurrence, label: String, departure: String?, conflict: Boolean, easy: Boolean, canChange: Boolean,
    onOpen: () -> Unit, onToggle: () -> Unit, onOccurrence: () -> Unit,
    calendarColor: androidx.compose.ui.graphics.Color? = null,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(onClick = onOpen, modifier = Modifier.weight(1f).heightIn(min = if (easy) 64.dp else 56.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (calendarColor != null) {
                                Surface(Modifier.size(8.dp), shape = CircleShape, color = calendarColor) {}
                            }
                            Text(label, style = if (easy) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge)
                        }
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
internal fun plannerTime(row: OrganizerOccurrence, locale: Locale, zone: ZoneId, date: LocalDate): String {
    if (row.allDay) return uiText("All day")
    val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    val startsOn = row.start.atZone(zone).toLocalDate()
    val end = row.end
    if (startsOn < date) return if (end != null && end.atZone(zone).toLocalDate() == date)
        uiText("Continues until %s", formatter.format(end)) else uiText("Continues")
    val start = formatter.format(row.start)
    return if (end != null && end.atZone(zone).toLocalDate() == startsOn) "$start – ${formatter.format(end)}" else start
}

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
                add(plannerTime(row, locale, zone, day))
                add(row.record.title)
                if (row.record.location.isNotBlank()) add(row.record.location)
                if (row.start.atZone(zone).toLocalDate() == day) plannerDeparture(row, locale, zone)?.let { add(it) }
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
