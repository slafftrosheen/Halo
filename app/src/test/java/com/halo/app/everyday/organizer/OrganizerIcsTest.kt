package com.halo.app.everyday.organizer

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class OrganizerIcsTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val zone = ZoneId.of("Europe/Riga")
    private fun parse(events: String) = OrganizerIcs.preview(("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + events.trimIndent().replace("\n", "\r\n") + "\r\nEND:VCALENDAR\r\n").toByteArray(), "personal", zone, now)
    @Test fun importsWeeklyEventWithExcludedAndMovedOccurrencesWithoutLoadingAttachments() {
        val preview = parse("""
            BEGIN:VEVENT
            UID:event1
            DTSTART;TZID=Europe/Riga:20260105T100000
            DTEND;TZID=Europe/Riga:20260105T110000
            SUMMARY:Weekly\, review
            RRULE:FREQ=WEEKLY;BYDAY=MO;COUNT=3
            EXDATE;TZID=Europe/Riga:20260112T100000
            ATTACH:https://example.org/must-not-fetch
            END:VEVENT
            BEGIN:VEVENT
            UID:event1
            RECURRENCE-ID;TZID=Europe/Riga:20260119T100000
            DTSTART;TZID=Europe/Riga:20260120T120000
            DTEND;TZID=Europe/Riga:20260120T130000
            SUMMARY:Moved review
            END:VEVENT
        """)
        assertTrue(preview.issues.toString(), preview.issues.none { !it.warning })
        assertTrue(preview.issues.any { it.warning && it.reason.contains("attachments") })
        assertEquals(1, preview.records.size); assertEquals(2, preview.exceptions.size)
        val rows = OrganizerRecurrence.between(preview.records.single(), preview.exceptions, now, now.plusSeconds(30 * 86400), zone)
        assertEquals(listOf(5, 20), rows.map { it.start.atZone(zone).dayOfMonth })
        assertEquals("Weekly, review", rows.first().record.title)
    }
    @Test fun unsupportedRecurrenceIsReportedInsteadOfSilentlyImportedAsOneOff() {
        val preview = parse("""
            BEGIN:VEVENT
            UID:unsupported
            DTSTART:20260101T100000Z
            DTEND:20260101T110000Z
            RRULE:FREQ=MONTHLY;BYDAY=1MO
            SUMMARY:Monthly
            END:VEVENT
        """)
        assertTrue(preview.records.isEmpty()); assertEquals(1, preview.issues.size)
        assertTrue(preview.issues.single().reason.contains("Ordinal"))
    }
    @Test fun allDayAndUnicodeRoundTripRetainDatesTextAndRecurrence() {
        val record = OrganizerRecord("id", OrganizerKind.EVENT, "Mācības · Учёба · Żółć".repeat(8), details = "Line one\nLine two; a,b\\c",
            time = OrganizerTime(LocalDateTime.parse("2026-03-29T00:00"), LocalDateTime.parse("2026-03-30T00:00"), zone.id, allDay = true),
            repeat = RepeatRule(RepeatFrequency.DAILY, count = 3), createdAt = now)
        val valid = record.copy(title = record.title.take(180))
        val bytes = OrganizerIcs.export(listOf(valid), emptyList(), now)
        assertTrue(bytes.toString(Charsets.UTF_8).split("\r\n").all { it.toByteArray().size <= 75 })
        val imported = OrganizerIcs.preview(bytes, "export", zone, now)
        assertTrue(imported.issues.toString(), imported.issues.isEmpty())
        assertEquals(valid.title, imported.records.single().title); assertEquals(valid.details, imported.records.single().details)
        assertEquals(valid.time, imported.records.single().time); assertEquals(valid.repeat, imported.records.single().repeat)
    }
    @Test fun allDayDurationsKeepMidnightAcrossBothDstChangesAndRepeat() {
        listOf("20260329" to 23L, "20261025" to 25L).forEach { (date, hours) ->
            val preview = parse("BEGIN:VEVENT\nUID:day\nDTSTART;VALUE=DATE:$date\nDURATION:P1D\nRRULE:FREQ=DAILY;COUNT=2\nSUMMARY:Day\nEND:VEVENT")
            assertTrue(preview.issues.toString(), preview.issues.none { !it.warning })
            val record = preview.records.single()
            val time = requireNotNull(record.time)
            assertEquals(time.start.plusDays(1), time.end)
            val rows = OrganizerRecurrence.between(record, emptyList(), time.instant(zone),
                time.start.plusDays(3).atZone(zone).toInstant(), zone)
            assertEquals(2, rows.size)
            assertEquals(hours, Duration.between(rows.first().start, rows.first().end).toHours())
            assertTrue(rows.all { it.end!!.atZone(zone).toLocalTime() == LocalTime.MIDNIGHT })
        }
    }

    @Test fun nominalDaysAndWeeksDifferFromElapsedHoursAcrossDst() {
        fun timed(duration: String) = parse("BEGIN:VEVENT\nUID:duration\nDTSTART;TZID=Europe/Riga:20260328T120000\nDURATION:$duration\nSUMMARY:Trip\nEND:VEVENT").records.single().time!!
        assertEquals(LocalDateTime.parse("2026-03-29T12:00"), timed("P1D").end)
        assertEquals(LocalDateTime.parse("2026-03-29T13:00"), timed("PT24H").end)
        assertEquals(LocalDateTime.parse("2026-03-29T14:00"), timed("P1DT2H").end)
        assertEquals(LocalDateTime.parse("2026-04-04T12:00"), timed("+P1W").end)
        assertEquals(LocalDateTime.parse("2026-03-28T13:02:03"), timed("PT1H2M3S").end)
    }

    @Test fun invalidAndUnsupportedDurationFormsAreNotNormalizedIntoEvents() {
        listOf("P", "PT", "P1DT", "P0D", "PT0S", "-P1D", "PT-1H", "P1M", "P1W1D", "P999999999999999999999D").forEach { duration ->
            val preview = parse("BEGIN:VEVENT\nUID:invalid\nDTSTART:20260101T100000Z\nDURATION:$duration\nSUMMARY:Invalid\nEND:VEVENT")
            assertTrue(duration, preview.records.isEmpty())
            assertTrue(duration, preview.issues.any { !it.warning })
        }
        val allDayHours = parse("BEGIN:VEVENT\nUID:invalid\nDTSTART;VALUE=DATE:20260101\nDURATION:PT24H\nSUMMARY:Invalid\nEND:VEVENT")
        assertTrue(allDayHours.records.isEmpty())
    }

    @Test fun timedRecurringNominalDurationRequiresReviewWhenModelCannotPreserveDstSemantics() {
        val preview = parse("BEGIN:VEVENT\nUID:repeat\nDTSTART;TZID=Europe/Riga:20260328T120000\nDURATION:P1D\nRRULE:FREQ=DAILY;COUNT=3\nSUMMARY:Trip\nEND:VEVENT")
        assertTrue(preview.records.isEmpty())
        assertTrue(preview.issues.any { !it.warning && it.reason.contains("duration") })
    }

    @Test fun floatingTimedNominalDurationsRequireReviewEvenWhenImportedInAFixedZone() {
        for (deviceZone in listOf(zone, ZoneId.of("UTC"))) {
            for (recurrence in listOf("", "RRULE:FREQ=DAILY;COUNT=3\n")) {
                val body = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:floating\nDTSTART:20260328T120000\nDURATION:P1D\n${recurrence}SUMMARY:Trip\nEND:VEVENT\nEND:VCALENDAR"
                val preview = OrganizerIcs.preview(body.toByteArray(), "personal", deviceZone, now)
                assertTrue("$deviceZone $recurrence", preview.records.isEmpty())
                assertTrue(preview.issues.any { !it.warning && it.reason.contains("Floating") })
            }
        }
        val elapsed = parse("BEGIN:VEVENT\nUID:elapsed\nDTSTART:20260328T120000\nDURATION:PT24H\nSUMMARY:Elapsed\nEND:VEVENT")
        assertEquals(1, elapsed.records.size)
    }

    @Test fun laterOverlapEndpointsAreWithheldRatherThanShortenedByLosingTheirOffset() {
        listOf("DURATION:P1DT1H", "DURATION:PT25H", "DTEND:20261025T013000Z").forEach { end ->
            val preview = parse("BEGIN:VEVENT\nUID:overlap\nDTSTART;TZID=Europe/Riga:20261024T033000\n$end\nSUMMARY:Trip\nEND:VEVENT")
            assertTrue(end, preview.records.isEmpty())
            assertTrue(preview.issues.any { !it.warning && it.reason.contains("overlap") })
        }
        val representable = parse("BEGIN:VEVENT\nUID:overlap\nDTSTART;TZID=Europe/Riga:20261024T033000\nDURATION:PT24H\nSUMMARY:Trip\nEND:VEVENT")
        assertEquals(LocalDateTime.parse("2026-10-25T03:30"), representable.records.single().time!!.end)
    }

    @Test fun unsupportedRecurrenceOnOverrideWithholdsWholeSeries() {
        val master = "BEGIN:VEVENT\nUID:a\nDTSTART:20260101T100000Z\nDTEND:20260101T110000Z\nRRULE:FREQ=DAILY;COUNT=3\nSUMMARY:Master\nEND:VEVENT"
        listOf("RRULE:FREQ=DAILY;COUNT=2", "RDATE:20260104T100000Z", "EXRULE:FREQ=WEEKLY", "EXDATE:20260103T100000Z").forEach { property ->
            val occurrence = "BEGIN:VEVENT\nUID:a\nRECURRENCE-ID:20260102T100000Z\nDTSTART:20260102T120000Z\nDTEND:20260102T130000Z\n$property\nSUMMARY:Moved\nEND:VEVENT"
            val preview = parse("$master\n$occurrence")
            assertTrue(property, preview.records.isEmpty())
            assertTrue(preview.exceptions.isEmpty())
            assertTrue(preview.issues.any { !it.warning })
        }
    }

    @Test fun malformedDuplicateMasterCannotBeAcceptedByChangingInputOrder() {
        val valid = "BEGIN:VEVENT\nUID:a\nDTSTART:20260101T100000Z\nDTEND:20260101T110000Z\nSUMMARY:Master\nEND:VEVENT"
        val invalid = valid.replace("DTEND:20260101T110000Z", "RRULE:FREQ=MONTHLY;BYDAY=1MO")
        listOf("$invalid\n$valid", "$valid\n$invalid").forEach { events ->
            val preview = parse(events)
            assertTrue(preview.records.isEmpty())
            assertTrue(preview.exceptions.isEmpty())
            assertTrue(preview.issues.any { !it.warning })
        }
        val ambiguous = valid.replace("UID:a", "UID:a\nUID:b")
        val other = valid.replace("UID:a", "UID:b")
        assertTrue(parse("$ambiguous\n$other").records.isEmpty())
        assertTrue(parse("$other\n$ambiguous").records.isEmpty())
    }
    @Test fun oversizeAndMalformedDocumentsFailBeforePartialPublication() {
        assertThrows(IllegalArgumentException::class.java) { OrganizerIcs.preview(ByteArray(2 * 1024 * 1024 + 1), "id", zone, now) }
        assertThrows(IllegalArgumentException::class.java) { OrganizerIcs.preview("BEGIN:VEVENT\nEND:VEVENT".toByteArray(), "id", zone, now) }
    }
    @Test fun duplicateUidsDoNotCreateConflictingRecordsAndMissingEndNeedsReview() {
        val event = "BEGIN:VEVENT\nUID:a\nDTSTART:20260101T100000Z\nDTEND:20260101T110000Z\nSUMMARY:First\nEND:VEVENT"
        val preview = parse(event + "\n" + event)
        assertEquals(0, preview.records.size); assertEquals(1, preview.issues.size)
        val missing = parse(event.replace("DTEND:20260101T110000Z\n", ""))
        assertTrue(missing.records.isEmpty()); assertEquals(1, missing.issues.size)
    }
    @Test fun invalidCalendarDatesAndMidnightTwentyFourAreRejectedWithoutNormalization() {
        listOf("20260230T100000", "20260229T100000", "20260101T240000").forEach { invalid ->
            val result = parse("BEGIN:VEVENT\nUID:bad\nDTSTART:${invalid}Z\nDURATION:PT1H\nSUMMARY:Invalid\nEND:VEVENT")
            assertTrue(result.records.isEmpty()); assertTrue(result.issues.any { !it.warning })
        }
        assertEquals(1, parse("BEGIN:VEVENT\nUID:valid\nDTSTART:20260228T100000Z\nDURATION:PT1H\nSUMMARY:Valid\nEND:VEVENT").records.size)
    }
    @Test fun allDayAndFloatingRecurrenceExportsUseMatchingBoundsAndIdentities() {
        for (allDay in listOf(true, false)) {
            val time = OrganizerTime(LocalDateTime.parse(if (allDay) "2026-01-01T00:00" else "2026-01-01T10:00"),
                LocalDateTime.parse(if (allDay) "2026-01-02T00:00" else "2026-01-01T11:00"), zone.id, allDay, followDeviceZone = !allDay)
            val record = OrganizerRecord("event", OrganizerKind.EVENT, "Review", time = time,
                repeat = RepeatRule(RepeatFrequency.DAILY, until = time.start.plusDays(3).atZone(zone).toInstant()), createdAt = now)
            val exception = OccurrenceOverride(record.id, time.start.plusDays(1).atZone(zone).toInstant(), OccurrenceDisposition.CANCELLED)
            val encoded = OrganizerIcs.export(listOf(record), listOf(exception), now).toString(Charsets.UTF_8)
            assertTrue(encoded.contains(if (allDay) "UNTIL=20260104" else "UNTIL=20260104T100000"))
            assertTrue(encoded.contains(if (allDay) "RECURRENCE-ID;VALUE=DATE:20260102" else "RECURRENCE-ID:20260102T100000"))
            val decoded = OrganizerIcs.preview(encoded.toByteArray(), "roundtrip", ZoneId.of("Europe/London"), now)
            assertTrue(decoded.issues.toString(), decoded.issues.none { !it.warning })
            assertEquals(1, decoded.exceptions.size)
            assertEquals(3, OrganizerRecurrence.between(decoded.records.single(), decoded.exceptions, now.minusSeconds(86400), now.plusSeconds(7 * 86400), ZoneId.of("Europe/London")).size)
        }
    }
    @Test fun fixedOffsetAndNamedZoneExportsPreserveWallTimeAndValidatedDefinitions() {
        listOf("+02:00", "UTC", "Europe/Riga").forEach { id ->
            val time = OrganizerTime(LocalDateTime.parse("2026-03-28T10:00"), LocalDateTime.parse("2026-03-28T11:00"), id)
            val record = OrganizerRecord("event", OrganizerKind.EVENT, "Review", time = time, repeat = RepeatRule(RepeatFrequency.DAILY, count = 3), createdAt = now)
            val bytes = OrganizerIcs.export(listOf(record), emptyList(), now)
            if (id != "UTC") assertTrue(bytes.toString(Charsets.UTF_8).contains("BEGIN:VTIMEZONE"))
            else assertFalse(bytes.toString(Charsets.UTF_8).contains("TZID=\"UTC\""))
            val decoded = OrganizerIcs.preview(bytes, "roundtrip", zone, now)
            assertTrue(decoded.issues.toString(), decoded.issues.none { !it.warning })
            assertEquals(OrganizerRecurrence.slots(time, record.repeat, zone).map { it.start }.toList(),
                OrganizerRecurrence.slots(decoded.records.single().time!!, decoded.records.single().repeat, zone).map { it.start }.toList())
        }
    }
    @Test fun occurrenceDetailsLocationAndLinksSurviveRoundTripAndEmbeddedAlarmsAreOnlyWarnings() {
        val preview = parse("""
            BEGIN:VEVENT
            UID:source
            DTSTART:20260101T100000Z
            DTEND:20260101T110000Z
            RRULE:FREQ=DAILY;COUNT=3
            SUMMARY:Master
            DESCRIPTION:Original
            LOCATION:Room A
            URL:https://example.org/master
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT15M
            DESCRIPTION:Alarm
            END:VALARM
            END:VEVENT
            BEGIN:VEVENT
            UID:source
            RECURRENCE-ID:20260102T100000Z
            DTSTART:20260102T120000Z
            DTEND:20260102T130000Z
            SUMMARY:Moved
            DESCRIPTION:New details
            LOCATION:Room B
            URL:https://example.org/instance
            END:VEVENT
        """)
        assertTrue(preview.issues.none { !it.warning }); assertTrue(preview.issues.any { it.warning })
        val bytes = OrganizerIcs.export(preview.records, preview.exceptions, now)
        val roundTrip = OrganizerIcs.preview(bytes, "roundtrip", zone, now)
        val moved = OrganizerRecurrence.between(roundTrip.records.single(), roundTrip.exceptions, now, now.plusSeconds(4 * 86400), zone).single { it.record.title == "Moved" }
        assertEquals("New details", moved.record.details); assertEquals("Room B", moved.record.location)
        assertEquals("https://example.org/instance", moved.record.sourceUrl)
    }
    @Test fun conflictingEmbeddedTimeZoneIsRejectedInsteadOfSilentlyUsingDifferentOffsets() {
        val time = OrganizerTime(LocalDateTime.parse("2026-03-28T10:00"), LocalDateTime.parse("2026-03-28T11:00"), zone.id)
        val record = OrganizerRecord("event", OrganizerKind.EVENT, "Review", time = time, createdAt = now)
        val bytes = OrganizerIcs.export(listOf(record), emptyList(), now).toString(Charsets.UTF_8)
            .replace("TZOFFSETTO:+0300", "TZOFFSETTO:+0400").toByteArray()
        val decoded = OrganizerIcs.preview(bytes, "roundtrip", zone, now)
        assertTrue(decoded.records.isEmpty()); assertTrue(decoded.issues.any { !it.warning && it.reason.contains("conflict") })
    }
    @Test fun fixedOffsetWeeklyAndMonthBoundaryRecurrencesDoNotShiftCalendarAnchor() {
        val time = OrganizerTime(LocalDateTime.parse("2026-06-01T00:30"), LocalDateTime.parse("2026-06-01T01:30"), "+14:00")
        listOf(RepeatRule(RepeatFrequency.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY), count = 4), RepeatRule(RepeatFrequency.MONTHLY, count = 4)).forEach { repeat ->
            val record = OrganizerRecord("event", OrganizerKind.EVENT, "Review", time = time, repeat = repeat, createdAt = now)
            val decoded = OrganizerIcs.preview(OrganizerIcs.export(listOf(record), emptyList(), now), "roundtrip", zone, now)
            assertTrue(decoded.issues.toString(), decoded.issues.none { !it.warning })
            assertEquals(OrganizerRecurrence.slots(time, repeat, zone).map { it.start }.toList(),
                OrganizerRecurrence.slots(decoded.records.single().time!!, decoded.records.single().repeat, zone).map { it.start }.toList())
        }
    }
    @Test fun conflictingZonesOnOccurrenceAndOtherTimeReferencesWithholdSeries() {
        val badZone = IcsTimeZones.export(zone, 2025, 2027).joinToString("\n").replace("TZOFFSETTO:+0300", "TZOFFSETTO:+0400")
        val master = "BEGIN:VEVENT\nUID:a\nDTSTART:20260328T100000Z\nDTEND:20260328T110000Z\nRRULE:FREQ=DAILY;COUNT=3\nSUMMARY:Review\nEND:VEVENT"
        val variant = "BEGIN:VEVENT\nUID:a\nRECURRENCE-ID:20260329T100000Z\nDTSTART;TZID=Europe/Riga:20260329T140000\nDTEND;TZID=Europe/Riga:20260329T150000\nSUMMARY:Moved\nEND:VEVENT"
        val changed = parse("$badZone\n$master\n$variant")
        assertTrue(changed.records.isEmpty()); assertTrue(changed.issues.any { !it.warning })
        listOf(
            master.replace("DTEND:20260328T110000Z", "DTEND;TZID=Europe/Riga:20260328T130000"),
            master.replace("SUMMARY:Review", "SUMMARY:Review\nEXDATE;TZID=Europe/Riga:20260329T130000")
        ).forEach { event ->
            val parsed = parse("$badZone\n$event")
            assertTrue(parsed.records.isEmpty()); assertTrue(parsed.issues.any { !it.warning })
        }
        val recurrenceZone = variant.replace("RECURRENCE-ID:20260329T100000Z", "RECURRENCE-ID;TZID=Europe/Riga:20260329T130000")
            .replace("DTSTART;TZID=Europe/Riga:20260329T140000", "DTSTART:20260329T140000Z").replace("DTEND;TZID=Europe/Riga:20260329T150000", "DTEND:20260329T150000Z")
        assertTrue(parse("$badZone\n$master\n$recurrenceZone").records.isEmpty())
    }
    @Test fun duplicateOccurrenceOrUidPropertiesAreNotResolvedByInputOrder() {
        val master = "BEGIN:VEVENT\nUID:a\nDTSTART:20260101T100000Z\nDTEND:20260101T110000Z\nRRULE:FREQ=DAILY;COUNT=3\nSUMMARY:Review\nEND:VEVENT"
        val occurrence = "BEGIN:VEVENT\nUID:a\nRECURRENCE-ID:20260102T100000Z\nDTSTART:20260102T120000Z\nDTEND:20260102T130000Z\nSUMMARY:Moved\nEND:VEVENT"
        val duplicate = parse("$master\n$occurrence\n${occurrence.replace("SUMMARY:Moved", "SUMMARY:Conflicting")}")
        assertTrue(duplicate.records.isEmpty()); assertTrue(duplicate.issues.any { !it.warning && it.reason.contains("Duplicate") })
        val ambiguousUid = parse(master.replace("UID:a", "UID:a\nUID:b"))
        assertTrue(ambiguousUid.records.isEmpty()); assertTrue(ambiguousUid.issues.any { !it.warning })
    }
    @Test fun importedUidRetainsStableIdentityAcrossRepeatedExports() {
        val source = parse("BEGIN:VEVENT\nUID:original\\,calendar@example.org\nDTSTART:20260101T100000Z\nDTEND:20260101T110000Z\nSUMMARY:Review\nEND:VEVENT")
        val first = OrganizerIcs.preview(OrganizerIcs.export(source.records, emptyList(), now), "personal", zone, now)
        val second = OrganizerIcs.preview(OrganizerIcs.export(first.records, emptyList(), now), "personal", zone, now)
        assertEquals(source.records.single().id, first.records.single().id)
        assertEquals(first.records.single().id, second.records.single().id)
        assertEquals("original,calendar@example.org", second.records.single().calendarUid)
    }
    @Test fun exportingDifferentCalendarsWithSameUidRequiresExplicitSelection() {
        val input = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:shared\nDTSTART:20260101T100000Z\nDTEND:20260101T110000Z\nSUMMARY:Review\nEND:VEVENT\nEND:VCALENDAR".toByteArray()
        val first = OrganizerIcs.preview(input, "calendarA", zone, now).records.single()
        val second = OrganizerIcs.preview(input, "calendarB", zone, now).records.single()
        assertNotEquals(first.id, second.id)
        val error = assertThrows(IllegalArgumentException::class.java) { OrganizerIcs.export(listOf(first, second), emptyList(), now) }
        assertTrue(error.message!!.contains("Export these calendars separately"))
    }
}
