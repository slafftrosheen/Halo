package com.halo.app.ui.everyday

import android.os.Bundle
import com.halo.app.data.everyday.OrganizerCodec
import com.halo.app.everyday.organizer.*
import java.time.*

/** A restored editor keeps its reviewed version; it cannot silently overwrite a voice/widget edit. */
data class OrganizerDraft(
    val base: OrganizerRecord, val creating: Boolean, val occurrence: Instant? = null,
    val originalOverride: OccurrenceOverride? = null,
    val title: String = base.title, val details: String = base.details, val location: String = base.location,
    val dated: Boolean = base.time != null,
    val date: String = (base.time?.start?.toLocalDate() ?: LocalDate.now()).toString(),
    val time: String = (base.time?.start?.toLocalTime() ?: LocalTime.of(9, 0)).toString(),
    val endDate: String = (base.time?.end?.toLocalDate() ?: base.time?.start?.toLocalDate() ?: LocalDate.now()).toString(),
    val endTime: String = (base.time?.end?.toLocalTime() ?: LocalTime.of(10, 0)).toString(),
    val zone: String = base.time?.zoneId ?: ZoneId.systemDefault().id,
    val allDay: Boolean = base.time?.allDay ?: false, val followZone: Boolean = base.time?.followDeviceZone ?: false,
    val frequency: String = base.repeat?.frequency?.name.orEmpty(), val interval: String = (base.repeat?.interval ?: 1).toString(),
    val weekdays: Set<DayOfWeek> = base.repeat?.weekdays.orEmpty(),
    val count: String = base.repeat?.count?.toString().orEmpty(),
    val until: String = base.repeat?.until?.atZone(ZoneId.of(zone))?.toLocalDate()?.toString().orEmpty(),
    val priority: TaskPriority = base.priority, val parentId: String? = base.parentId,
    val notify: Boolean = base.notifyBeforeMinutes != null, val lead: String = (base.notifyBeforeMinutes ?: 0).toString(),
    val duration: String = ((base.timer?.durationMs ?: 300_000) / 1000).toString(),
    val links: List<String> = base.links.map { it.objectId }, val resetOccurrences: Boolean = false,
) {
    val hasEnd: Boolean get() = base.kind == OrganizerKind.EVENT || (originalOverride?.replacementTime ?: base.time)?.end != null
    fun record(now: Instant, elapsedMs: Long, bootId: String): OrganizerRecord {
        val selectedZone = ZoneId.of(zone)
        val scheduled = if (base.kind != OrganizerKind.TIMER && (dated || base.kind != OrganizerKind.TASK)) {
            val start = LocalDate.parse(date).atTime(if (allDay) LocalTime.MIDNIGHT else LocalTime.parse(time))
            val end = if (hasEnd) LocalDate.parse(endDate).atTime(if (allDay) LocalTime.MIDNIGHT else LocalTime.parse(endTime)) else null
            OrganizerTime(start, end, selectedZone.id, allDay, followZone)
        } else null
        val repeat = if (frequency.isNotBlank() && scheduled != null && occurrence == null) RepeatRule(
            RepeatFrequency.valueOf(frequency), interval.toInt(), if (frequency == "WEEKLY") weekdays else emptySet(),
            count.takeIf { it.isNotBlank() }?.toInt(), until.takeIf { it.isNotBlank() }?.let { date ->
                base.repeat?.until?.takeIf { zone == base.time?.zoneId && it.atZone(selectedZone).toLocalDate().toString() == date }
                    ?: LocalDate.parse(date).plusDays(1).atStartOfDay(selectedZone).toInstant().minusMillis(1)
            }) else null
        val timer = if (base.kind == OrganizerKind.TIMER && creating) Math.multiplyExact(duration.toLong(), 1000).let {
            TimerState(it, it).start(now, elapsedMs, bootId)
        } else base.timer
        return base.copy(title = title.trim(), details = details, location = location, time = scheduled, repeat = repeat,
            priority = priority, parentId = parentId, timer = timer,
            notifyBeforeMinutes = if (notify && scheduled != null && base.kind != OrganizerKind.ALARM) lead.toInt() else null,
            links = links.distinct().map { id -> base.links.find { it.objectId == id } ?: OrganizerLink(id) }).also { it.validate() }
    }
    fun bundle() = Bundle().apply {
        putString("base", OrganizerCodec.encode(base)); putBoolean("creating", creating); putString("occurrence", occurrence?.toString())
        putString("override", originalOverride?.let(OrganizerCodec::encode))
        mapOf("title" to title, "details" to details, "location" to location, "date" to date, "time" to time,
            "endDate" to endDate, "endTime" to endTime, "zone" to zone, "frequency" to frequency, "interval" to interval,
            "count" to count, "until" to until, "priority" to priority.name, "parentId" to parentId,
            "lead" to lead, "duration" to duration).forEach { (key, value) -> putString(key, value) }
        mapOf("dated" to dated, "allDay" to allDay, "followZone" to followZone, "notify" to notify,
            "resetOccurrences" to resetOccurrences).forEach { (key, value) -> putBoolean(key, value) }
        putStringArrayList("weekdays", ArrayList(weekdays.map { it.name })); putStringArrayList("links", ArrayList(links))
    }
    companion object {
        fun forOccurrence(base: OrganizerRecord, occurrence: OrganizerOccurrence, override: OccurrenceOverride?): OrganizerDraft {
            val time = override?.replacementTime ?: requireNotNull(base.time).let { original ->
                val zone = ZoneId.of(original.zoneId)
                val start = occurrence.originalStart.atZone(zone).toLocalDate().atTime(original.start.toLocalTime())
                val end = original.end?.let {
                    if (original.allDay) start.plusDays(java.time.temporal.ChronoUnit.DAYS.between(original.start.toLocalDate(), it.toLocalDate()))
                    else start.atZone(zone).plus(Duration.between(original.start.atZone(zone), it.atZone(zone))).toLocalDateTime()
                }
                original.copy(start = start, end = end)
            }
            return OrganizerDraft(base, false, occurrence.originalStart, originalOverride = override,
                title = override?.replacementTitle ?: base.title, details = override?.replacementDetails ?: base.details,
                location = override?.replacementLocation ?: base.location,
                date = time.start.toLocalDate().toString(), time = time.start.toLocalTime().toString(),
                endDate = time.end?.toLocalDate()?.toString().orEmpty(), endTime = time.end?.toLocalTime()?.toString().orEmpty(),
                zone = time.zoneId, allDay = time.allDay, followZone = time.followDeviceZone)
        }
        fun restore(bundle: Bundle): OrganizerDraft = bundle.run {
            OrganizerDraft(OrganizerCodec.decode(requireNotNull(getString("base"))), getBoolean("creating"), getString("occurrence")?.let(Instant::parse),
                originalOverride = getString("override")?.let(OrganizerCodec::override),
                title = getString("title").orEmpty(), details = getString("details").orEmpty(), location = getString("location").orEmpty(),
                dated = getBoolean("dated"), date = getString("date").orEmpty(), time = getString("time").orEmpty(),
                endDate = getString("endDate").orEmpty(), endTime = getString("endTime").orEmpty(), zone = getString("zone").orEmpty(),
                allDay = getBoolean("allDay"), followZone = getBoolean("followZone"), frequency = getString("frequency").orEmpty(),
                interval = getString("interval").orEmpty(), weekdays = getStringArrayList("weekdays").orEmpty().map(DayOfWeek::valueOf).toSet(),
                count = getString("count").orEmpty(), until = getString("until").orEmpty(), priority = TaskPriority.valueOf(requireNotNull(getString("priority"))),
                parentId = getString("parentId"), notify = getBoolean("notify"), lead = getString("lead").orEmpty(), duration = getString("duration").orEmpty(),
                links = getStringArrayList("links").orEmpty(), resetOccurrences = getBoolean("resetOccurrences"))
        }
    }
}
