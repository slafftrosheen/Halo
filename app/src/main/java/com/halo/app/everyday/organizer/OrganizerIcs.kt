package com.halo.app.everyday.organizer

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.*
import java.time.format.DateTimeFormatter

data class CalendarImportIssue(val item: Int, val uid: String?, val reason: String, val warning: Boolean = false)
data class CalendarImportPreview(val records: List<OrganizerRecord>, val exceptions: List<OccurrenceOverride>,
    val issues: List<CalendarImportIssue>)

/** Bounded calendar interchange; no remote resource loading, attachment opening or alarm execution. */
object OrganizerIcs {
    private val date = DateTimeFormatter.BASIC_ISO_DATE
    private val local = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss", java.util.Locale.ROOT).withResolverStyle(java.time.format.ResolverStyle.STRICT)
    private val utc = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'", java.util.Locale.ROOT).withZone(ZoneOffset.UTC)
    private data class Property(val name: String, val parameters: Map<String, String>, val value: String)
    private fun property(line: String): Property {
        var quoted = false
        val colon = line.indexOfFirst { char -> if (char == '"') { quoted = !quoted; false } else char == ':' && !quoted }
        require(colon > 0) { "Invalid calendar property" }
        val fields = line.substring(0, colon).split(';')
        val parameterPairs = fields.drop(1).map { field ->
            val equal = field.indexOf('='); require(equal > 0) { "Invalid property parameter" }
            field.substring(0, equal).uppercase(java.util.Locale.ROOT) to field.substring(equal + 1).removeSurrounding("\"")
        }
        require(parameterPairs.map { it.first }.distinct().size == parameterPairs.size) { "Duplicate property parameters" }
        val params = parameterPairs.toMap()
        return Property(fields.first().uppercase(java.util.Locale.ROOT), params, line.substring(colon + 1))
    }
    private data class ParsedTime(val local: LocalDateTime, val zone: ZoneId, val allDay: Boolean, val floating: Boolean) {
        fun instant(): Instant = local.atZone(zone).toInstant()
    }
    private fun parseTime(property: Property, fallback: ZoneId, zones: Map<String, Result<IcsTimeZones.Coverage>> = emptyMap()): ParsedTime {
        val allDay = property.parameters["VALUE"] == "DATE"
        val value = property.value
        require(property.parameters.keys.all { it in setOf("VALUE", "TZID") }) { "Unsupported time parameters" }
        require(property.parameters["VALUE"] == null || property.parameters["VALUE"] in setOf("DATE", "DATE-TIME"))
        val isUtc = value.endsWith('Z')
        require(!isUtc || "TZID" !in property.parameters) { "UTC time cannot also specify TZID" }
        require(!allDay || "TZID" !in property.parameters) { "All-day dates do not take a TZID parameter" }
        val zone = if (isUtc) ZoneOffset.UTC else property.parameters["TZID"]?.let(ZoneId::of) ?: fallback
        val time = if (allDay) LocalDate.parse(value, date).atStartOfDay() else LocalDateTime.parse(value.removeSuffix("Z"), local)
        property.parameters["TZID"]?.let { referenced -> zones[referenced]?.getOrThrow()?.let { coverage ->
            require(time >= coverage.from && time <= coverage.through) { "Referenced timezone does not cover this date" }
        } }
        return ParsedTime(time, zone, allDay, !allDay && !isUtc && "TZID" !in property.parameters)
    }
    private fun unescape(value: String): String {
        val result = StringBuilder(); var escaped = false
        value.forEach { char ->
            if (escaped) { result.append(if (char == 'n' || char == 'N') '\n' else char); escaped = false }
            else if (char == '\\') escaped = true else result.append(char)
        }
        require(!escaped) { "Truncated text escape" }
        return result.toString()
    }
    private fun rule(value: String, start: ParsedTime): RepeatRule {
        val pairs = value.split(';').map { it.split('=', limit = 2).also { pair -> require(pair.size == 2) } }
        require(pairs.map { it[0] }.distinct().size == pairs.size) { "Duplicate recurrence fields" }
        val fields = pairs.associate { it[0] to it[1] }
        require(fields.keys.all { it in setOf("FREQ", "INTERVAL", "BYDAY", "COUNT", "UNTIL", "WKST") }) { "This recurrence rule is not supported; it was not simplified" }
        require(fields["WKST"] == null || fields["WKST"] == "MO") { "Only Monday week starts are supported" }
        val days = mapOf("MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY,
            "TH" to DayOfWeek.THURSDAY, "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY)
        return RepeatRule(RepeatFrequency.valueOf(requireNotNull(fields["FREQ"])), fields["INTERVAL"]?.toInt() ?: 1,
            fields["BYDAY"]?.split(',')?.map { requireNotNull(days[it]) { "Ordinal weekdays are not supported" } }?.toSet().orEmpty(),
            fields["COUNT"]?.toInt(), fields["UNTIL"]?.let { end ->
                if (end.length == 8) LocalDate.parse(end, date).plusDays(1).atStartOfDay(start.zone).toInstant().minusNanos(1)
                else parseTime(Property("UNTIL", emptyMap(), end), start.zone).instant()
            })
    }
    private fun identity(calendarId: String, uid: String): String = "ics:" + MessageDigest.getInstance("SHA-256")
        .digest("$calendarId\u0000$uid".toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun preview(bytes: ByteArray, calendarId: String, defaultZone: ZoneId, now: Instant): CalendarImportPreview {
        require(bytes.size <= 2 * 1024 * 1024 && calendarId.isNotBlank() && calendarId.length <= 2048) { "Calendar exceeds import limits" }
        val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        val input = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        require('\u0000' !in input) { "Invalid calendar text" }
        val unfolded = mutableListOf<String>()
        input.replace("\r\n", "\n").split('\n').forEach { line ->
            if ((line.startsWith(' ') || line.startsWith('\t')) && unfolded.isNotEmpty()) unfolded[unfolded.lastIndex] += line.drop(1)
            else if (line.isNotEmpty()) unfolded += line
            require(unfolded.lastOrNull().orEmpty().length <= 32768 && unfolded.size <= 50000) { "Calendar property limit exceeded" }
        }
        require(unfolded.firstOrNull() == "BEGIN:VCALENDAR" && unfolded.lastOrNull() == "END:VCALENDAR") { "Not an iCalendar document" }
        val events = mutableListOf<List<Property>>()
        val zoneDefinitions = mutableListOf<List<String>>()
        var zoneDefinition: MutableList<String>? = null
        var current: MutableList<Property>? = null
        var nested = 0
        unfolded.drop(1).dropLast(1).forEach { line ->
            when {
                line == "BEGIN:VTIMEZONE" -> { require(current == null && zoneDefinition == null); zoneDefinition = mutableListOf() }
                line == "END:VTIMEZONE" -> { zoneDefinitions += requireNotNull(zoneDefinition).toList(); zoneDefinition = null }
                zoneDefinition != null -> zoneDefinition!!.add(line)
                line == "BEGIN:VEVENT" -> { require(current == null); current = mutableListOf(); nested = 0 }
                line == "END:VEVENT" -> { require(current != null && nested == 0); events += current!!.toList(); current = null; require(events.size <= 2000) }
                current != null && line.startsWith("BEGIN:") -> { current!!.add(Property("X-HALO-OMITTED", emptyMap(), line.substringAfter(':'))); nested++ }
                current != null && line.startsWith("END:") -> { require(nested > 0); nested-- }
                current != null && nested == 0 -> current!!.add(property(line))
            }
        }
        require(current == null && zoneDefinition == null) { "Unclosed calendar component" }
        val zones = zoneDefinitions.associate { block ->
            val id = block.firstOrNull { it.startsWith("TZID:") }?.substringAfter(':') ?: error("Embedded timezone has no identity")
            id to runCatching { IcsTimeZones.validate(block) }
        }
        require(zones.size == zoneDefinitions.size) { "Duplicate timezone definition" }
        val issues = mutableListOf<CalendarImportIssue>()
        val records = mutableListOf<OrganizerRecord>()
        val exceptions = mutableListOf<OccurrenceOverride>()
        val blockedSeries = mutableSetOf<String>()
        val occurrenceIdentities = mutableSetOf<Pair<String, Instant>>()
        val masters = events.filter { event -> event.none { it.name == "RECURRENCE-ID" } }
        fun List<Property>.one(name: String): Property? {
            val matching = filter { it.name == name }; require(matching.size <= 1) { "Duplicate $name property" }; return matching.singleOrNull()
        }
        fun parseEvent(event: List<Property>): Pair<OrganizerTime, ParsedTime> {
            val start = parseTime(requireNotNull(event.one("DTSTART")) { "Missing event start" }, defaultZone, zones)
            val explicitEnd = event.one("DTEND")?.let { parseTime(it, start.zone, zones) }
            val duration = event.one("DURATION")?.value?.let(Duration::parse)
            require(explicitEnd == null || duration == null) { "Both end and duration were supplied" }
            require(explicitEnd == null || explicitEnd.allDay == start.allDay)
            val end = explicitEnd?.instant()?.atZone(start.zone)?.toLocalDateTime()
                ?: duration?.let { start.local.atZone(start.zone).plus(it).toLocalDateTime() }
                ?: if (start.allDay) start.local.plusDays(1) else error("Timed event has no end or duration; choose one before importing")
            return OrganizerTime(start.local, end, start.zone.id, start.allDay, start.floating).also { it.validate() } to start
        }
        masters.forEach { event ->
            val uid = event.firstOrNull { it.name == "UID" }?.value?.let { runCatching { unescape(it) }.getOrNull() }
            try {
                event.one("UID")
                require(!uid.isNullOrBlank() && uid.length <= 2000) { "Missing or oversized event UID" }
                require(event.none { it.name in setOf("RDATE", "EXRULE") }) { "Additional recurrence dates/rules need manual review" }
                val (time, start) = parseEvent(event)
                val record = OrganizerRecord(identity(calendarId, uid), OrganizerKind.EVENT,
                    unescape(event.one("SUMMARY")?.value ?: "Imported event"), unescape(event.one("DESCRIPTION")?.value.orEmpty()),
                    time, event.one("RRULE")?.let { rule(it.value, start) },
                    status = if (event.one("STATUS")?.value == "CANCELLED") OrganizerStatus.CANCELLED else OrganizerStatus.ACTIVE,
                    sourceUrl = event.one("URL")?.value?.takeIf { it.isNotEmpty() }, sourceLabel = "iCalendar", createdAt = now,
                    location = event.one("LOCATION")?.value?.let(::unescape).orEmpty(), calendarUid = uid)
                record.validate()
                if (!time.allDay && !time.followDeviceZone && !isUtcZone(ZoneId.of(time.zoneId))) {
                    val embedded = zones[time.zoneId]
                    if (embedded != null) {
                        val coverage = embedded.getOrThrow()
                        val last = when {
                            record.repeat == null -> time.end!!
                            record.repeat.count != null -> OrganizerRecurrence.slots(time, record.repeat, defaultZone).last().identity.atZone(ZoneId.of(time.zoneId)).toLocalDateTime()
                            record.repeat.until != null -> record.repeat.until.atZone(ZoneId.of(time.zoneId)).toLocalDateTime()
                            else -> LocalDateTime.of(2199, 12, 31, 23, 59)
                        }
                        require(time.start >= coverage.from && last <= coverage.through) { "Embedded timezone does not cover the event series" }
                    } else issues += CalendarImportIssue(events.indexOf(event) + 1, uid.take(200), "No embedded timezone; uses installed IANA rules for ${time.zoneId}", warning = true)
                }
                require(records.none { it.id == record.id }) { "Duplicate event UID" }
                val excluded = event.filter { it.name == "EXDATE" }.flatMap { value -> value.value.split(',').map { raw ->
                    val occurrence = parseTime(value.copy(value = raw), start.zone, zones).instant()
                    require(record.repeat != null && OrganizerRecurrence.contains(record, occurrence, defaultZone)) { "Excluded date is not part of this series" }
                    OccurrenceOverride(record.id, occurrence, OccurrenceDisposition.CANCELLED)
                } }
                records += record; exceptions += excluded.distinctBy { it.originalStart }
                if (event.any { it.name == "ATTACH" || it.name == "X-HALO-OMITTED" }) issues += CalendarImportIssue(events.indexOf(event) + 1,
                    uid.take(200), "Embedded alarms and attachments are not imported, opened or scheduled", warning = true)
            } catch (failure: Exception) {
                uid?.let { id -> identity(calendarId, id).takeIf { key -> records.any { it.id == key } }?.let(blockedSeries::add) }
                issues += CalendarImportIssue(events.indexOf(event) + 1, uid?.take(200), failure.message ?: "Unsupported calendar event")
            }
        }
        events.filter { event -> event.any { it.name == "RECURRENCE-ID" } }.forEach { event ->
            val uid = event.firstOrNull { it.name == "UID" }?.value?.let { runCatching { unescape(it) }.getOrNull() }
            try {
                event.one("UID")
                require(!uid.isNullOrBlank())
                val parent = requireNotNull(records.find { it.id == identity(calendarId, uid) }) { "Occurrence has no imported series" }
                val recurrenceId = requireNotNull(event.one("RECURRENCE-ID"))
                require("RANGE" !in recurrenceId.parameters) { "This-and-future recurrence overrides need manual review" }
                val original = parseTime(recurrenceId, ZoneId.of(parent.time!!.zoneId), zones).instant()
                require(occurrenceIdentities.add(parent.id to original)) { "Duplicate occurrence identity" }
                require(parent.repeat != null && OrganizerRecurrence.contains(parent, original, defaultZone))
                val cancelled = event.one("STATUS")?.value == "CANCELLED"
                val value = if (cancelled) OccurrenceOverride(parent.id, original, OccurrenceDisposition.CANCELLED)
                    else OccurrenceOverride(parent.id, original, OccurrenceDisposition.REPLACED, parseEvent(event).first, event.one("SUMMARY")?.value?.let(::unescape),
                        replacementDetails = event.one("DESCRIPTION")?.value?.let(::unescape), replacementLocation = event.one("LOCATION")?.value?.let(::unescape), sourceUrlOverride = event.one("URL")?.value)
                exceptions.removeAll { it.recordId == parent.id && it.originalStart == original }; exceptions += value
                if (event.any { it.name == "ATTACH" || it.name == "X-HALO-OMITTED" }) issues += CalendarImportIssue(events.indexOf(event) + 1, uid.take(200), "Embedded alarms and attachments are omitted", warning = true)
            } catch (failure: Exception) {
                uid?.let { blockedSeries += identity(calendarId, it) }
                issues += CalendarImportIssue(events.indexOf(event) + 1, uid?.take(200), "Series withheld: " + (failure.message ?: "Unsupported occurrence change"))
            }
        }
        return CalendarImportPreview(records.filterNot { it.id in blockedSeries }, exceptions.filterNot { it.recordId in blockedSeries }, issues)
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\r\n", "\n").replace('\r', '\n')
        .replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,")
    private fun isUtcZone(zone: ZoneId) = zone.rules.isFixedOffset && zone.rules.getOffset(Instant.EPOCH).totalSeconds == 0
    private fun timeLine(name: String, time: OrganizerTime, end: Boolean = false): String {
        val value = if (end) requireNotNull(time.end) else time.start
        return if (time.allDay) "$name;VALUE=DATE:${value.toLocalDate().format(date)}"
            else if (time.followDeviceZone) "$name:${value.format(local)}"
            else if (isUtcZone(ZoneId.of(time.zoneId))) "$name:${utc.format(value.atZone(ZoneId.of(time.zoneId)).toInstant())}"
            else "$name;TZID=\"${time.zoneId}\":${value.format(local)}"
    }
    fun export(records: List<OrganizerRecord>, exceptions: List<OccurrenceOverride>, exportedAt: Instant): ByteArray {
        require(records.size <= 2000)
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Halo//Everyday Organizer//EN", "CALSCALE:GREGORIAN")
        val active = records.filter { it.kind == OrganizerKind.EVENT && it.deletedAt == null }
        require(active.map { it.calendarUid ?: "${it.id}@halo.local" }.distinct().size == active.size) {
            "Selected calendars contain duplicate event UIDs. Export these calendars separately or select one version of each event"
        }
        val times = active.mapNotNull { it.time } + exceptions.filter { ex -> active.any { it.id == ex.recordId } }.mapNotNull { it.replacementTime }
        times.filter { !it.allDay && !it.followDeviceZone && !isUtcZone(ZoneId.of(it.zoneId)) }.groupBy { it.zoneId }.forEach { (id, values) ->
            lines += IcsTimeZones.export(ZoneId.of(id), values.minOf { it.start.year } - 1, 2199)
        }
        active.forEach { record ->
            record.validate(); val time = requireNotNull(record.time)
            val uid = escape(record.calendarUid ?: "${record.id}@halo.local")
            lines += listOf("BEGIN:VEVENT", "UID:$uid", "DTSTAMP:${utc.format(exportedAt)}", timeLine("DTSTART", time), timeLine("DTEND", time, end = true),
                "SUMMARY:${escape(record.title)}", "DESCRIPTION:${escape(record.details)}", "LOCATION:${escape(record.location)}", "SEQUENCE:${record.version}")
            if (record.status == OrganizerStatus.CANCELLED) lines += "STATUS:CANCELLED"
            record.sourceUrl?.let { lines += "URL:$it" }
            record.repeat?.let { rule ->
                val fields = mutableListOf("FREQ=${rule.frequency.name}", "INTERVAL=${rule.interval}")
                if (rule.weekdays.isNotEmpty()) fields += "BYDAY=" + rule.weekdays.sortedBy { it.value }.joinToString(",") { it.name.take(2) }
                rule.count?.let { fields += "COUNT=$it" }; rule.until?.let { bound -> fields += "UNTIL=" + when {
                    time.allDay -> bound.atZone(ZoneId.of(time.zoneId)).toLocalDate().format(date)
                    time.followDeviceZone -> bound.atZone(ZoneId.of(time.zoneId)).toLocalDateTime().format(local)
                    else -> utc.format(bound)
                } }
                lines += "RRULE:" + fields.joinToString(";")
            }
            lines += "END:VEVENT"
            exceptions.filter { it.recordId == record.id && it.disposition != OccurrenceDisposition.COMPLETED }.forEach { exception ->
                val identityTime = time.copy(start = exception.originalStart.atZone(ZoneId.of(time.zoneId)).toLocalDate().atTime(time.start.toLocalTime()), end = null)
                lines += listOf("BEGIN:VEVENT", "UID:$uid", "DTSTAMP:${utc.format(exportedAt)}", timeLine("RECURRENCE-ID", identityTime))
                if (exception.disposition == OccurrenceDisposition.CANCELLED) lines += "STATUS:CANCELLED"
                else {
                    val replacement = requireNotNull(exception.replacementTime)
                    lines += listOf(timeLine("DTSTART", replacement), timeLine("DTEND", replacement, end = true), "SUMMARY:${escape(exception.replacementTitle ?: record.title)}",
                        "DESCRIPTION:${escape(exception.replacementDetails ?: record.details)}", "LOCATION:${escape(exception.replacementLocation ?: record.location)}")
                    (exception.sourceUrlOverride ?: record.sourceUrl)?.let { lines += "URL:$it" }
                }
                lines += "END:VEVENT"
            }
        }
        lines += "END:VCALENDAR"
        require(lines.size <= 50000) { "Calendar has too many properties; choose fewer records or time zones" }
        // RFC line folding counts UTF-8 octets and never splits a multibyte code point.
        return lines.joinToString("\r\n", postfix = "\r\n") { line ->
            val folded = StringBuilder(); var width = 0
            line.codePoints().toArray().forEach { point ->
                val text = String(Character.toChars(point)); val bytes = text.toByteArray(StandardCharsets.UTF_8).size
                if (width + bytes > 75) { folded.append("\r\n "); width = 1 }
                folded.append(text); width += bytes
            }
            folded.toString()
        }.toByteArray(StandardCharsets.UTF_8).also { require(it.size <= 2 * 1024 * 1024) { "Calendar export exceeds size limit; choose fewer records" } }
    }
}
