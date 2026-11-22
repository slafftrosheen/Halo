package com.halo.app.everyday.organizer.android

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

internal data class PhoneCalendarEvent(val id: Long, val title: String, val start: Instant,
    val end: Instant, val allDay: Boolean)

internal object PhoneCalendarAgenda {
    fun permitted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun upcoming(context: Context, now: Instant, zone: ZoneId): List<PhoneCalendarEvent> = withContext(Dispatchers.IO) {
        if (!permitted(context)) return@withContext emptyList()
        val today = now.atZone(zone).toLocalDate()
        val through = today.plusDays(7).atStartOfDay(zone).toInstant()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, today.atStartOfDay(zone).toInstant().toEpochMilli())
            ContentUris.appendId(this, through.toEpochMilli())
        }.build()
        // CalendarContract.Instances materializes recurring events and joins visible calendars for this window.
        try {
            context.contentResolver.query(uri, arrayOf("event_id", "title", "begin", "end", "allDay"),
                "visible = 1 AND (eventStatus IS NULL OR eventStatus != ?)",
                arrayOf(CalendarContract.Events.STATUS_CANCELED.toString()), "begin ASC")?.use { rows ->
                buildList {
                    while (size < 4 && rows.moveToNext()) {
                        val start = Instant.ofEpochMilli(rows.getLong(2))
                        val end = Instant.ofEpochMilli(rows.getLong(3))
                        val allDay = rows.getInt(4) == 1
                        if (end <= now || start >= through || (!allDay && start < now)) continue
                        val title = rows.getString(1)?.trim()?.take(200).orEmpty()
                        if (title.isNotBlank()) add(PhoneCalendarEvent(rows.getLong(0), title, start, end, allDay))
                    }
                }
            }.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun intent(event: PhoneCalendarEvent): Intent = Intent(Intent.ACTION_VIEW,
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)).apply {
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start.toEpochMilli())
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end.toEpochMilli())
    }
}
