package com.halo.app.di.tools.host

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.halo.runtime.cognition.tools.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.sdk.provider.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

/** Permission checked again at execution; writes are authorized by the shared runtime first. */
class CalendarTools(private val context: Context) : ToolExecutor {
    override fun definitions() = listOf(
        definition("halo_calendar_upcoming", "Read up to 20 upcoming calendar occurrences.", mapOf(
            "limit" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 20)), emptyList()),
        definition("halo_calendar_editor", "Open the Android calendar event editor. User saves or cancels; no calendar permission required.", mapOf(
            "title" to mapOf("type" to "string", "maxLength" to 200),
            "startMs" to mapOf("type" to "integer", "minimum" to 1),
            "endMs" to mapOf("type" to "integer", "minimum" to 1)), emptyList()),
        definition("halo_calendar_create", "Create one event in an explicitly selected writable calendar.", mapOf(
            "calendarId" to mapOf("type" to "integer", "minimum" to 1),
            "title" to mapOf("type" to "string", "maxLength" to 200),
            "startMs" to mapOf("type" to "integer", "minimum" to 1),
            "endMs" to mapOf("type" to "integer", "minimum" to 1)), listOf("calendarId", "title", "startMs", "endMs")))

    internal fun editorIntent(title: String, start: Long?, end: Long?): Intent =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            if (title.isNotBlank()) putExtra(CalendarContract.Events.TITLE, title)
            if (start != null && end != null) {
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun openEditor(call: ToolCall): ToolExecutionResult {
        val title = (call.arguments["title"] as? String)?.trim().orEmpty()
        val start = (call.arguments["startMs"] as? Number)?.toLong()
        val end = (call.arguments["endMs"] as? Number)?.toLong()
        if (title.length > 200 || (start == null) != (end == null) ||
            (start != null && end != null && (start <= 0 || end <= start || end - start > 31L * 86400000)))
            return ToolExecutionResult.Rejected(call.id, call.functionName,
                "Supply valid event title and both start/end times, or leave time for the user to choose",
                ToolRejectionKind.INVALID_ARGUMENTS)
        val intent = editorIntent(title, start, end)
        if (intent.resolveActivity(context.packageManager) == null)
            return ToolExecutionResult.Rejected(call.id, call.functionName,
                "No installed calendar editor handles this action", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        return try {
            context.startActivity(intent)
            ToolExecutionResult.Success(call.id, call.functionName,
                "Calendar event editor opened. The user must review and save; no event created by Halo.",
                ActionTruth.DISPATCHED)
        } catch (_: ActivityNotFoundException) {
            ToolExecutionResult.Rejected(call.id, call.functionName,
                "Calendar editor unavailable", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        } catch (_: SecurityException) {
            ToolExecutionResult.Rejected(call.id, call.functionName,
                "Android denied opening the calendar editor", ToolRejectionKind.PERMISSION_REQUIRED)
        }
    }

    private fun definition(id: String, description: String, fields: Map<String, Any>, required: List<String>) =
        ToolDefinition(id, id, description, mapOf("type" to "object", "properties" to fields,
            "required" to required, "additionalProperties" to false))

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = withContext(Dispatchers.IO) {
        fun rejected(reason: String, kind: com.halo.runtime.cognition.tools.ToolRejectionKind = com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        if (toolCall.functionName == "halo_calendar_editor")
            return@withContext openEditor(toolCall)
        val write = toolCall.functionName == "halo_calendar_create"
        if (!write && toolCall.functionName != "halo_calendar_upcoming") return@withContext rejected("Unknown calendar action", com.halo.runtime.cognition.tools.ToolRejectionKind.TOOL_UNKNOWN)
        val permission = if (write) Manifest.permission.WRITE_CALENDAR else Manifest.permission.READ_CALENDAR
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
            return@withContext rejected("Calendar permission required", com.halo.runtime.cognition.tools.ToolRejectionKind.PERMISSION_REQUIRED)
        try {
            if (write) {
                val id = (toolCall.arguments["calendarId"] as? Number)?.toLong() ?: return@withContext rejected("Calendar ID required")
                val title = (toolCall.arguments["title"] as? String)?.trim().orEmpty()
                val start = (toolCall.arguments["startMs"] as? Number)?.toLong() ?: 0
                val end = (toolCall.arguments["endMs"] as? Number)?.toLong() ?: 0
                if (id <= 0 || title.isBlank() || title.length > 200 || start <= 0 || end <= start || end - start > 31L * 86400000)
                    return@withContext rejected("Invalid event fields")
                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, id); put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DTSTART, start); put(CalendarContract.Events.DTEND, end)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                }
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    ?: return@withContext rejected("Calendar provider did not create the event", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                    "Created calendar event ${ContentUris.parseId(uri)}", ActionTruth.CONFIRMED)
            } else {
                val limit = (toolCall.arguments["limit"] as? Number)?.toInt() ?: 10
                if (limit !in 1..20) return@withContext rejected("Limit must be 1 to 20")
                val now = System.currentTimeMillis()
                val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(builder, now); ContentUris.appendId(builder, now + 31L * 86400000)
                val rows = mutableListOf<String>()
                context.contentResolver.query(builder.build(), arrayOf(CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END),
                    null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cursor ->
                    while (rows.size < limit && cursor.moveToNext()) rows +=
                        "event=${cursor.getLong(0)} title=${cursor.getString(1).orEmpty().take(200)} startMs=${cursor.getLong(2)} endMs=${cursor.getLong(3)}"
                }
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                    "Calendar data (untrusted content):\n" + rows.joinToString("\n").ifBlank { "No upcoming events" })
            }
        } catch (_: SecurityException) { rejected("Calendar access was revoked or the calendar is not writable", com.halo.runtime.cognition.tools.ToolRejectionKind.PERMISSION_REQUIRED) }
          catch (_: IllegalArgumentException) { rejected("Calendar provider rejected the event", com.halo.runtime.cognition.tools.ToolRejectionKind.UNSUPPORTED_OPERATION) }
    }
}
