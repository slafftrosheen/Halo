package com.halo.app.ui.screens.study

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.EverydayWriteAuthority
import com.halo.app.everyday.organizer.OrganizerKind
import com.halo.app.everyday.organizer.OrganizerLink
import com.halo.app.everyday.organizer.OrganizerTime
import com.halo.app.everyday.organizer.RepeatFrequency
import com.halo.app.everyday.organizer.RepeatRule
import com.halo.app.everyday.organizer.StudyExamLesson
import com.halo.app.everyday.organizer.StudyExamSchedule
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.zone6.Zone6ButtonSheet
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun studyExamDates(exam: LocalDate, time: LocalTime, days: Int, now: ZonedDateTime): List<LocalDate> {
    require(days in 1..14)
    return (days downTo 1).map { exam.minusDays(it.toLong()) }.filter {
        it.atTime(time).atZone(now.zone).isAfter(now)
    }
}

@Composable
internal fun StudyExamPlanSheet(courses: List<HaloStudyCollection>, authority: () -> Unit, onDismiss: () -> Unit,
    onSaved: (String, java.time.Instant) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    var title by rememberSaveable { mutableStateOf("") }
    var examDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var minuteOfDay by rememberSaveable { mutableIntStateOf(18 * 60) }
    var days by rememberSaveable { mutableIntStateOf(5) }
    var busy by remember { mutableStateOf(false) }
    var issue by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf<android.app.Dialog?>(null) }
    var courseId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCourses by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { picker?.dismiss() } }

    val exam = examDay?.let(LocalDate::ofEpochDay)?.takeIf { it.year in 1970..2199 }
    val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    val dates = exam?.let { studyExamDates(it, time, days, now) }.orEmpty()
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val eventTitle = stringResource(R.string.study_exam_event, title.trim())
    val details = exam?.let { stringResource(R.string.study_exam_details, dateFormat.format(it)) }.orEmpty()
    val course = courses.firstOrNull { it.id == courseId }
    val lessonFallback = stringResource(R.string.study_exam_open_lesson)
    val plan = exam?.let { examDate -> StudyExamSchedule(examDate, course?.lessons.orEmpty().take(50).map {
        StudyExamLesson(it.id, it.goal?.trim()?.take(120)?.takeIf(String::isNotBlank) ?: lessonFallback, it.updatedAtEpochMs)
    }) }

    Zone6ButtonSheet(stringResource(R.string.study_exam_plan), onDismiss = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloTextField(title, { title = it.take(140); issue = 0 }, VoiceFieldKind.TEXT,
                modifier = Modifier.fillMaxWidth(), maxChars = 140, singleLine = true, enabled = !busy,
                label = { Text(stringResource(R.string.study_exam_name)) })
            if (courses.isNotEmpty() || courseId != null) Box {
                TextButton(onClick = { showCourses = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(course?.title ?: stringResource(R.string.study_exam_choose_course))
                }
                DropdownMenu(showCourses, onDismissRequest = { showCourses = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.study_exam_without_course)) }, onClick = {
                        courseId = null; showCourses = false; issue = 0
                    })
                    courses.forEach { choice -> DropdownMenuItem(text = { Text(choice.title) }, onClick = {
                        if (title.isBlank()) title = choice.title.take(140)
                        courseId = choice.id; showCourses = false; issue = 0
                    }) }
                }
            }
            TextButton(onClick = {
                val initial = exam ?: now.toLocalDate().plusWeeks(1)
                picker = DatePickerDialog(context, { _, year, month, day ->
                    examDay = LocalDate.of(year, month + 1, day).toEpochDay()
                    issue = 0
                }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
                    datePicker.minDate = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
                    datePicker.maxDate = LocalDate.of(2199, 12, 31).atStartOfDay(now.zone).toInstant().toEpochMilli()
                    show()
                }
            }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (exam == null) stringResource(R.string.study_exam_date)
                    else stringResource(R.string.study_exam_on_date, dateFormat.format(exam)))
            }
            TextButton(onClick = {
                picker = TimePickerDialog(context, { _, hour, minute -> minuteOfDay = hour * 60 + minute; issue = 0 },
                    time.hour, time.minute, DateFormat.is24HourFormat(context)).apply { show() }
            }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.study_exam_time, timeFormat.format(time)))
            }
            Text(stringResource(R.string.study_exam_days), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (count in listOf(3, 5, 7)) FilterChip(days == count, onClick = { days = count; issue = 0 },
                    enabled = !busy, label = { Text(count.toString()) })
            }
            if (dates.isNotEmpty()) Text(stringResource(R.string.study_exam_preview,
                dateFormat.format(dates.first()), dateFormat.format(dates.last())), style = MaterialTheme.typography.bodyMedium)
            else if (exam != null) Text(stringResource(R.string.study_exam_no_days), style = MaterialTheme.typography.bodyMedium)
            if (plan != null && course != null) dates.forEachIndexed { index, date ->
                val assignments = plan.lessonsOn(index, dates.size)
                if (assignments.isNotEmpty()) Text("${dateFormat.format(date)} · ${assignments.joinToString { it.title }}",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (courseId != null && (course == null || course.lessons.size > 50))
                Text(stringResource(R.string.study_exam_course_unavailable), style = MaterialTheme.typography.bodySmall)
            if (issue != 0) Text(stringResource(issue), color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                val reviewedPlan = plan ?: return@Button
                if (busy || dates.isEmpty() || title.isBlank()) return@Button
                val reviewedNow = ZonedDateTime.now()
                if (reviewedNow.zone != now.zone || studyExamDates(reviewedPlan.examDate, time, days, reviewedNow) != dates) {
                    now = reviewedNow
                    issue = R.string.study_exam_changed
                    return@Button
                }
                busy = true
                issue = 0
                scope.launch {
                    try {
                        val start = dates.first().atTime(time)
                        val end = start.atZone(reviewedNow.zone).plusMinutes(25)
                        val saved = EverydayWriteAuthority.run(authority) {
                            val references = course?.lessons.orEmpty().flatMap { it.sourceObjectIds }.distinct()
                            require(references.size <= 50)
                            Graph.organizerUseCases.create(OrganizerKind.EVENT, eventTitle, details = details,
                                time = OrganizerTime(start, end.toLocalDateTime(), reviewedNow.zone.id, endOffset = end.offset),
                                repeat = if (dates.size > 1) RepeatRule(RepeatFrequency.DAILY, count = dates.size) else null,
                                links = references.map { OrganizerLink(it) }, studyExam = reviewedPlan)
                        }
                        authority()
                        onSaved(saved.id, requireNotNull(saved.time).instant(reviewedNow.zone))
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { issue = R.string.study_exam_save_failed }
                    finally { busy = false }
                }
            }, enabled = !busy && title.isNotBlank() && dates.isNotEmpty() &&
                (courseId == null || course != null && course.lessons.size <= 50), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.study_exam_save))
            }
        }
    }
}
