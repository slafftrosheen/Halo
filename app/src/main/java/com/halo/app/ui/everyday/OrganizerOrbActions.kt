package com.halo.app.ui.everyday

import com.halo.app.everyday.organizer.*

/** Page-owned operations; every invocation still passes the canonical owner's version and policy checks. */
internal enum class OrganizerOrbAction(val label: String) {
    TASK("Add task"), EVENT("Add event"), TIMER("Add timer"), ALARM("Add alarm"), BRIEF("Your day in Halo"), TODAY("Today"),
    CAPTURE("Capture to Inbox"), RESEARCH("Search / Research"), ROUTINE("Add routine"),
    COMPLETE("Complete task"), EDIT("Reschedule / edit"), ATTACH("Attach related item"),
    DELEGATE("Delegate to Halo"), TAKE_OVER("Take over"), SHARED("Make shared"), WATCH("Watch / monitor"),
    STOP("Stop Halo work"), PLAN("Edit responsibility and plan"), RESULT("Open execution record"),
    UNDO("Undo removal"), CLOCKS("World clocks"), SOURCES("Sources"), IMPORT("Import calendar"), EXPORT("Export calendar")
}

internal fun organizerOrbActions(record: OrganizerRecord?, section: String, kids: Boolean): List<OrganizerOrbAction> {
    if (kids) return if (record == null) listOf(OrganizerOrbAction.TODAY, OrganizerOrbAction.BRIEF, OrganizerOrbAction.CLOCKS) else emptyList()
    if (record == null) return buildList {
        if (section == "INBOX") add(OrganizerOrbAction.CAPTURE)
        if (section == "ROUTINES") add(OrganizerOrbAction.ROUTINE)
        if (section == "RESEARCH") add(OrganizerOrbAction.RESEARCH)
        addAll(listOf(OrganizerOrbAction.TASK, OrganizerOrbAction.EVENT, OrganizerOrbAction.TIMER, OrganizerOrbAction.ALARM,
            OrganizerOrbAction.BRIEF, OrganizerOrbAction.TODAY, OrganizerOrbAction.CAPTURE, OrganizerOrbAction.RESEARCH,
            OrganizerOrbAction.UNDO, OrganizerOrbAction.CLOCKS, OrganizerOrbAction.SOURCES, OrganizerOrbAction.IMPORT, OrganizerOrbAction.EXPORT))
    }.distinct()
    val work = record.commitment
    return buildList {
        if (record.kind == OrganizerKind.TASK && work.owner == CommitmentOwner.ME && record.status == OrganizerStatus.ACTIVE && record.repeat == null && work.purpose != CommitmentPurpose.STOPWATCH) add(OrganizerOrbAction.COMPLETE)
        if (work.execution == null) addAll(listOf(OrganizerOrbAction.EDIT, OrganizerOrbAction.ATTACH))
        if ((record.kind == OrganizerKind.TASK || work.owner != CommitmentOwner.ME) && work.purpose != CommitmentPurpose.STOPWATCH && record.status == OrganizerStatus.ACTIVE) {
            if (work.owner != CommitmentOwner.HALO) add(OrganizerOrbAction.DELEGATE)
            if (work.owner != CommitmentOwner.ME) add(OrganizerOrbAction.TAKE_OVER)
            if (work.owner != CommitmentOwner.SHARED) add(OrganizerOrbAction.SHARED)
            if (record.kind == OrganizerKind.TASK && work.purpose !in setOf(CommitmentPurpose.WATCH, CommitmentPurpose.INBOX)) add(OrganizerOrbAction.WATCH)
            if (work.execution == null) add(OrganizerOrbAction.PLAN) else add(OrganizerOrbAction.STOP)
        }
        if (work.execution?.runId != null) add(OrganizerOrbAction.RESULT)
    }
}
