package com.halo.app.everyday.organizer

/** Stable presentation codes. Raw parser/Java exception text is diagnostic data, never UI copy. */
enum class CalendarImportReason(val message: String) {
    INVALID("This calendar entry is invalid or unsupported. Review it in the original calendar."),
    RECURRENCE("This recurrence is unsupported. Review the series manually; it was not simplified."),
    TIMEZONE("The timezone definition is invalid, unsupported or does not cover this event."),
    DURATION("This calendar duration is invalid or exceeds the supported one-year limit."),
    ALL_DAY_DURATION("All-day durations must use whole calendar days or weeks."),
    OVERLAP("The event ends in the later daylight-saving overlap. Review the end time manually."),
    FLOATING_DURATION("A floating day/week duration changes with the device timezone. Review it manually."),
    RECURRING_DURATION("A recurring day/week duration cannot be preserved across daylight-saving changes. Review it manually."),
    END_REQUIRED("This timed event needs an end time or a duration before import."),
    START_REQUIRED("This event has no supported start time."),
    IDENTITY("The event or occurrence identity is missing, duplicated or ambiguous."),
    OCCURRENCE("This occurrence change cannot be applied to the imported series."),
    EMBEDDED_OMITTED("Embedded alarms and attachments are not imported, opened or scheduled."),
    IANA_RULES("No embedded timezone; uses installed IANA rules for %1\$s");

    companion object {
        /** Also classifies saved pre-code snapshots without changing their diagnostic strings. */
        fun fromDiagnostic(raw: String): CalendarImportReason {
            val text=raw.removePrefix("Series withheld: ").lowercase(java.util.Locale.ROOT)
            return when {
                text.startsWith("no embedded timezone;") -> IANA_RULES
                "embedded alarms and attachments" in text -> EMBEDDED_OMITTED
                "later daylight-saving overlap" in text -> OVERLAP
                "floating timed day/week duration" in text -> FLOATING_DURATION
                "recurring timed day/week duration" in text -> RECURRING_DURATION
                "all-day duration" in text -> ALL_DAY_DURATION
                "duration" in text && "no end" !in text -> DURATION
                "timed event has no end" in text -> END_REQUIRED
                "missing event start" in text -> START_REQUIRED
                "timezone" in text || "tzid" in text || "zoneid" in text -> TIMEZONE
                "duplicate" in text && ("uid" in text || "identity" in text) || "event uid" in text -> IDENTITY
                "recurrence" in text || "week starts" in text || "ordinal weekdays" in text || "excluded date" in text -> RECURRENCE
                "occurrence" in text -> OCCURRENCE
                else -> INVALID
            }
        }
    }
}
