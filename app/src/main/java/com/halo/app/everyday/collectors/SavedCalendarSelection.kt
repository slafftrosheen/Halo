package com.halo.app.everyday.collectors

import com.halo.app.everyday.organizer.*
import org.json.JSONObject
import java.time.*
import java.util.Base64

/** Carries the exact saved occurrence reviewed in a brief, including its comparison snapshot. */
data class SavedCalendarSelection(val recordId: String, val originalStart: Instant, val revision: String, val zoneId: String) {
    fun encode(): String = "calendar:" + Base64.getUrlEncoder().withoutPadding().encodeToString(JSONObject()
        .put("record", recordId).put("start", originalStart.toString()).put("revision", revision).put("zone", zoneId)
        .toString().toByteArray(Charsets.UTF_8))

    fun resolve(state: CollectorState): OrganizerOccurrence? {
        if (state.snapshot?.contentRevision != revision) return null
        val preview = (state.snapshot.payload as? CollectorPayload.Calendar)?.preview ?: return null
        val record = preview.records.find { it.id == recordId } ?: return null
        val zone = ZoneId.of(zoneId)
        if (!OrganizerRecurrence.contains(record, originalStart, zone)) return null
        val time = record.time ?: return null
        val exceptions = preview.exceptions.filter { it.recordId == recordId }
        val override = exceptions.find { it.originalStart == originalStart }
        val start = override?.replacementTime?.instant(zone) ?: if (time.followDeviceZone)
            originalStart.atZone(ZoneId.of(time.zoneId)).toLocalDate().atTime(time.start.toLocalTime()).atZone(zone).toInstant()
        else originalStart
        return OrganizerRecurrence.between(record, exceptions, start, start.plusSeconds(1), zone).find { it.originalStart == originalStart }
    }

    companion object {
        fun decode(value: String): SavedCalendarSelection? = runCatching {
            require(value.startsWith("calendar:") && value.length <= 4096)
            val json = JSONObject(String(Base64.getUrlDecoder().decode(value.removePrefix("calendar:")), Charsets.UTF_8))
            val record = json.getString("record"); val revision = json.getString("revision")
            require(record.length in 1..500 && revision.length in 1..200)
            SavedCalendarSelection(record, Instant.parse(json.getString("start")), revision, ZoneId.of(json.getString("zone")).id)
        }.getOrNull()
    }
}
