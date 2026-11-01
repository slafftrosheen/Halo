package com.halo.app.data.everyday

import com.halo.app.everyday.organizer.*
import java.time.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned on-disk format. Invalid records fail visibly; no partial defaults or silent data loss. */
internal object OrganizerCodec {
    private fun JSONObject.nullable(name: String): String? = if (isNull(name)) null else getString(name)
    private fun JSONObject.number(name: String): Long? = if (isNull(name)) null else getLong(name)
    private fun time(value: OrganizerTime) = JSONObject().put("start", value.start.toString())
        .put("end", value.end?.toString()).put("zone", value.zoneId).put("allDay", value.allDay).put("floating", value.followDeviceZone)
    private fun time(value: JSONObject) = OrganizerTime(LocalDateTime.parse(value.getString("start")), value.nullable("end")?.let(LocalDateTime::parse),
        value.getString("zone"), value.getBoolean("allDay"), value.getBoolean("floating"))
    fun encode(record: OrganizerRecord): String = JSONObject().apply {
        put("schema", 1); put("id", record.id); put("kind", record.kind.name); put("title", record.title); put("details", record.details)
        put("priority", record.priority.name); put("status", record.status.name); put("parentId", record.parentId)
        put("notifyBeforeMinutes", record.notifyBeforeMinutes); put("version", record.version)
        put("createdAt", record.createdAt.toEpochMilli()); put("updatedAt", record.updatedAt.toEpochMilli()); put("deletedAt", record.deletedAt?.toEpochMilli())
        put("sourceUrl", record.sourceUrl); put("sourceLabel", record.sourceLabel)
        put("location", record.location)
        put("calendarUid", record.calendarUid)
        record.time?.let { put("time", time(it)) }
        record.repeat?.let { rule -> put("repeat", JSONObject().put("frequency", rule.frequency.name).put("interval", rule.interval)
            .put("weekdays", JSONArray(rule.weekdays.sortedBy { it.value }.map { it.name })).put("count", rule.count).put("until", rule.until?.toEpochMilli())) }
        record.timer?.let { timer -> put("timer", JSONObject().put("duration", timer.durationMs).put("remaining", timer.remainingMs)
            .put("started", timer.startedAt?.toEpochMilli()).put("elapsed", timer.elapsedStartMs).put("boot", timer.bootId).put("runId", timer.runId)) }
        put("links", JSONArray(record.links.map { JSONObject().put("id", it.objectId).put("relation", it.relation) }))
    }.toString()
    fun decode(body: String): OrganizerRecord {
        val json = JSONObject(body)
        require(json.getInt("schema") == 1) { "Organizer data needs a newer Halo version" }
        val rule = json.optJSONObject("repeat")?.let { r -> RepeatRule(RepeatFrequency.valueOf(r.getString("frequency")), r.getInt("interval"),
            r.getJSONArray("weekdays").let { days -> (0 until days.length()).map { DayOfWeek.valueOf(days.getString(it)) }.toSet() },
            r.number("count")?.toInt(), r.number("until")?.let(Instant::ofEpochMilli)) }
        val timer = json.optJSONObject("timer")?.let { t -> TimerState(t.getLong("duration"), t.getLong("remaining"),
            t.number("started")?.let(Instant::ofEpochMilli), t.number("elapsed"), t.nullable("boot"),
            t.optString("runId").takeIf { it.isNotBlank() } ?: "legacy:${json.getString("id")}:${t.number("started") ?: 0}") }
        return OrganizerRecord(json.getString("id"), OrganizerKind.valueOf(json.getString("kind")), json.getString("title"), json.getString("details"),
            json.optJSONObject("time")?.let(::time), rule, TaskPriority.valueOf(json.getString("priority")), OrganizerStatus.valueOf(json.getString("status")),
            json.nullable("parentId"), json.number("notifyBeforeMinutes")?.toInt(), timer,
            json.getJSONArray("links").let { links -> (0 until links.length()).map { index -> links.getJSONObject(index).let { OrganizerLink(it.getString("id"), it.getString("relation")) } } },
            json.nullable("sourceUrl"), json.nullable("sourceLabel"), json.getLong("version"), Instant.ofEpochMilli(json.getLong("createdAt")),
            Instant.ofEpochMilli(json.getLong("updatedAt")), json.number("deletedAt")?.let(Instant::ofEpochMilli), json.optString("location", ""), json.nullable("calendarUid")).also { it.validate() }
    }
    fun encode(value: OccurrenceOverride) = JSONObject().apply {
        put("schema", 1); put("recordId", value.recordId); put("originalStart", value.originalStart.toEpochMilli())
        put("disposition", value.disposition.name); put("version", value.version); put("title", value.replacementTitle)
        put("details", value.replacementDetails); put("location", value.replacementLocation); put("sourceUrl", value.sourceUrlOverride)
        value.replacementTime?.let { put("time", time(it)) }
    }.toString()
    fun override(body: String): OccurrenceOverride = JSONObject(body).let { json ->
        require(json.getInt("schema") == 1)
        OccurrenceOverride(json.getString("recordId"), Instant.ofEpochMilli(json.getLong("originalStart")),
            OccurrenceDisposition.valueOf(json.getString("disposition")), json.optJSONObject("time")?.let(::time), json.nullable("title"), json.getLong("version"),
            json.nullable("details"), json.nullable("location"), json.nullable("sourceUrl"))
    }
    fun entity(record: OrganizerRecord) = OrganizerEntity(record.id, record.kind.name, record.title, record.parentId, record.version,
        record.updatedAt.toEpochMilli(), record.deletedAt?.toEpochMilli(), encode(record))
    fun receipt(value: OrganizerDeliveryEntity) = DeliveryReceipt(value.occurrenceKey, value.recordId, value.recordVersion,
        Instant.ofEpochMilli(value.triggerAt), DeliveryState.valueOf(value.state), Instant.ofEpochMilli(value.updatedAt), value.detail,
        value.snoozedUntil?.let(Instant::ofEpochMilli))
    fun entity(value: DeliveryReceipt) = OrganizerDeliveryEntity(value.occurrenceKey, value.recordId, value.recordVersion,
        value.triggerAt.toEpochMilli(), value.state.name, value.updatedAt.toEpochMilli(), value.detail, value.snoozedUntil?.toEpochMilli())
}
