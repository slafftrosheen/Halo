package com.halo.app.everyday.organizer

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Explicit IANA transition definitions; arbitrary custom zone rules are never silently substituted. */
internal object IcsTimeZones {
    private val format = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss").withResolverStyle(ResolverStyle.STRICT)
    data class Coverage(val id: String, val from: LocalDateTime, val through: LocalDateTime)
    private data class Change(val at: LocalDateTime, val from: ZoneOffset, val to: ZoneOffset)
    private fun offset(value: ZoneOffset): String = if (value.totalSeconds == 0) "+0000" else value.id.replace(":", "")
    private fun changes(zone: ZoneId, from: LocalDateTime, through: LocalDateTime): List<Change> {
        val values = mutableListOf<Change>()
        var cursor = from.atZone(zone).toInstant().minusSeconds(1)
        while (true) {
            val transition = zone.rules.nextTransition(cursor) ?: break
            if (transition.dateTimeBefore > through) break
            if (transition.dateTimeBefore >= from) values += Change(transition.dateTimeBefore, transition.offsetBefore, transition.offsetAfter)
            cursor = transition.instant.plusSeconds(1)
            require(values.size <= 1000) { "Timezone definition exceeds supported limits" }
        }
        return values
    }
    fun export(zone: ZoneId, fromYear: Int, throughYear: Int): List<String> {
        val from = LocalDate.of(fromYear, 1, 1).atStartOfDay()
        val through = LocalDate.of(throughYear + 1, 1, 1).atStartOfDay()
        val beginning = zone.rules.getOffset(from.atZone(zone).toInstant())
        val ending = zone.rules.getOffset(through.atZone(zone).toInstant())
        val records = listOf(Change(from, beginning, beginning)) + changes(zone, from, through) + Change(through, ending, ending)
        return listOf("BEGIN:VTIMEZONE", "TZID:${zone.id}") + records.flatMap { change ->
            val kind = if (change.to.totalSeconds > change.from.totalSeconds) "DAYLIGHT" else "STANDARD"
            listOf("BEGIN:$kind", "DTSTART:${change.at.format(format)}", "TZOFFSETFROM:${offset(change.from)}", "TZOFFSETTO:${offset(change.to)}", "END:$kind")
        } + "END:VTIMEZONE"
    }
    fun validate(lines: List<String>): Coverage {
        val id = lines.filter { it.startsWith("TZID:") }.single().substringAfter(':')
        val zone = ZoneId.of(id)
        require(lines.none { it.startsWith("RRULE:") || it.startsWith("RDATE:") }) { "Custom/repeating embedded timezone definitions need manual review" }
        val observed = mutableListOf<Change>()
        var fields: MutableMap<String, String>? = null
        lines.forEach { line -> when {
            line in setOf("BEGIN:STANDARD", "BEGIN:DAYLIGHT") -> { require(fields == null); fields = mutableMapOf() }
            line in setOf("END:STANDARD", "END:DAYLIGHT") -> {
                val current = requireNotNull(fields)
                observed += Change(LocalDateTime.parse(requireNotNull(current["DTSTART"]), format),
                    ZoneOffset.of(requireNotNull(current["TZOFFSETFROM"])), ZoneOffset.of(requireNotNull(current["TZOFFSETTO"])))
                fields = null
            }
            fields != null -> { val name = line.substringBefore(':'); require(fields!!.put(name, line.substringAfter(':')) == null) }
        } }
        require(fields == null && observed.size in 2..1002) { "Incomplete embedded timezone definition" }
        val from = observed.minOf { it.at }; val through = observed.maxOf { it.at }
        require(through > from)
        val actual = changes(zone, from, through)
        require(observed.filter { it.from != it.to }.toSet() == actual.toSet()) { "Embedded timezone conflicts with installed IANA rules" }
        observed.filter { it.from == it.to }.forEach {
            require(zone.rules.getOffset(it.at.atZone(zone).toInstant()) == it.to) { "Embedded timezone offset conflicts with installed IANA rules" }
        }
        return Coverage(id, from, through)
    }
}
