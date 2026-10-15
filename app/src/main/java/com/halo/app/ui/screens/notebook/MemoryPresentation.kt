package com.halo.app.ui.screens.notebook

import com.halo.app.data.memory.MemoryRecord
import com.halo.runtime.cognition.memory.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class MemoryGroup(val label: String) {
    ALL("All"), PREFERENCES("Preferences"), PEOPLE("People"), PROJECTS("Projects"),
    DEVICES("Devices"), FACTS("Facts"), OTHER("Other");
    fun includes(kind: MemoryKind) = this == ALL || this == when (kind) {
        MemoryKind.PREFERENCE -> PREFERENCES
        MemoryKind.PERSON, MemoryKind.RELATIONSHIP -> PEOPLE
        MemoryKind.PROJECT -> PROJECTS
        MemoryKind.DEVICE -> DEVICES
        MemoryKind.FACT, MemoryKind.PLACE -> FACTS
        else -> OTHER
    }
}
enum class MemorySort(val label: String) { UPDATED("Recently updated"), USED("Most used"), LAST_USED("Recently used") }
data class MemoryUiItem(val record: MemoryRecord, val kindLabel: String, val originLabel: String,
    val confirmationLabel: String, val scopeLabel: String, val confidenceLabel: String, val stale: Boolean,
    val usageSummary: String)

object MemoryPresentation {
    fun kind(kind: MemoryKind): String = when (kind) {
        MemoryKind.PREFERENCE -> "Preference"; MemoryKind.FACT -> "Fact"; MemoryKind.PERSON -> "Person"
        MemoryKind.PLACE -> "Place"; MemoryKind.DEVICE -> "Device"; MemoryKind.PROJECT -> "Project"
        MemoryKind.INSTRUCTION -> "Instruction"; MemoryKind.RELATIONSHIP -> "Relationship"
        MemoryKind.WORKFLOW -> "Workflow"; MemoryKind.PROCEDURE_REFERENCE -> "Procedure reference"
        MemoryKind.EPISODE -> "Past task"
    }
    fun origin(origin: MemoryProposalOrigin): String = when (origin) {
        MemoryProposalOrigin.USER_EXPLICIT -> "You asked halo to remember this"
        MemoryProposalOrigin.USER_CORRECTION -> "You corrected this"
        MemoryProposalOrigin.MODEL_INFERRED -> "Suggested by halo"
        MemoryProposalOrigin.EPISODE_DERIVED -> "Suggested from a past task"
        MemoryProposalOrigin.WORKSPACE_DERIVED -> "Suggested from Workspace"
        MemoryProposalOrigin.IMPORT -> "Imported"
    }
    fun scope(scope: MemoryScope, resolvedName: String?): String = when (scope) {
        MemoryScope.GLOBAL -> "Everywhere"
        MemoryScope.CONVERSATION -> "Temporary conversation context"
        else -> resolvedName?.let { when (scope) {
            MemoryScope.PROJECT -> "Project · $it"; MemoryScope.DEVICE -> "Device · $it"
            else -> "Workspace · $it"
        } } ?: "Original scope unavailable"
    }
    fun evidence(kind: MemoryEvidenceKind): String = when (kind) {
        MemoryEvidenceKind.CONVERSATION_TURN -> "Conversation"
        MemoryEvidenceKind.AGENT_RUN -> "Halo task"
        MemoryEvidenceKind.WORKSPACE_OBJECT -> "Workspace"
        MemoryEvidenceKind.USER_ACTION -> "Approved by you"
        MemoryEvidenceKind.ROUTINE_RUN -> "Automation run"
        MemoryEvidenceKind.IMPORTED -> "Imported source"
        MemoryEvidenceKind.EPISODE -> "Past task"
    }
    fun date(at: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (date) { today -> "Today"; today.minusDays(1) -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }
    }
    fun item(record: MemoryRecord, scopeName: String? = null, now: Long = System.currentTimeMillis()) = MemoryUiItem(
        record, kind(record.kind), origin(record.origin),
        if (record.userConfirmed) "Approved by you" else if (record.origin == MemoryProposalOrigin.IMPORT) "Imported · not verified" else "Not approved",
        scope(record.scope, scopeName), when {
            record.origin in setOf(MemoryProposalOrigin.USER_EXPLICIT, MemoryProposalOrigin.USER_CORRECTION) -> "User stated"
            record.confidence == null -> "Unknown"
            record.confidence >= .8f -> "Strong evidence"
            else -> "Suggested"
        }, record.validUntil?.let { it <= now } == true,
        if (record.usageCount == 0L) "Not used yet" else "Used ${record.usageCount} times" +
            record.lastUsedAt?.let { " · Last used ${date(it, now)}" }.orEmpty())
}
