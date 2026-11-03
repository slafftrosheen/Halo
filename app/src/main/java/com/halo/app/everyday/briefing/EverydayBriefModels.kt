package com.halo.app.everyday.briefing

import com.halo.app.daily.DailyAssistantSnapshot
import com.halo.app.everyday.collectors.CollectorAttemptState
import com.halo.app.everyday.collectors.CollectorState
import com.halo.app.everyday.organizer.OrganizerOccurrence
import com.halo.app.everyday.organizer.OrganizerRecord
import java.time.*
import kotlinx.coroutines.flow.Flow

enum class BriefPeriod { MORNING, ON_DEMAND, EVENING, CHANGES, MIDDAY, WEEKLY }
enum class BriefSection { WEATHER, EVENTS, TASKS, HALO_WORK, UNFINISHED, NEWS, RESEARCH, REMINDERS, ALARMS_ROUTINES, WORKSPACE, DEVICE }
enum class BriefAudience { EVERYDAY, KIDS, STUDY }
enum class BriefFactKind { EVENT, TASK, REMINDER, ALARM, TIMER, WEATHER, WARNING, HEADLINE, ACTIVITY, NOTE, NOTIFICATION, DEVICE, ATTENTION }
enum class BriefChangeKind { ADDED, UPDATED, REMOVED }

data class BriefSourceStamp(val id: String, val version: Long, val title: String, val publisher: String, val attributionUrl: String?,
    val external: Boolean, val retrievedAt: Instant?, val verifiedAt: Instant?, val expiresAt: Instant?,
    val attempt: CollectorAttemptState, val childApproved: Boolean) {
    fun stale(now: Instant, power: Boolean) = retrievedAt != null && (expiresAt?.let { now >= it } != false || external && !power)
}

/** Labels come from the presentation layer; this record contains source facts and safe local actions. */
data class BriefFact(val id: String, val kind: BriefFactKind, val title: String, val detail: String,
    val sourceId: String, val revision: String, val route: String, val priority: Int = 0,
    val at: Instant? = null, val externalUrl: String? = null, val values: Map<String, String> = emptyMap())
data class BriefFingerprint(val id: String, val sourceId: String, val revision: String, val kind: BriefFactKind, val section: BriefSection? = null)
data class BriefBaseline(val date: LocalDate, val zoneId: String, val audience: BriefAudience,
    val capturedAt: Instant, val facts: List<BriefFingerprint>, val observedSources: Set<String>)
data class BriefChange(val kind: BriefChangeKind, val factId: String, val sourceId: String, val fact: BriefFact?, val section: BriefSection? = null)

data class EverydayBrief(val id: String, val period: BriefPeriod, val audience: BriefAudience,
    val date: LocalDate, val zoneId: String, val generatedAt: Instant, val facts: List<BriefFact>,
    val sources: List<BriefSourceStamp>, val changes: List<BriefChange>, val baselineAt: Instant?,
    val baselineMissing: Boolean, val comparisonUnavailableSources: Set<String>, val omittedFacts: Int, val omittedChanges: Int, val inputFingerprint: String)

data class BriefInput(val now: Instant, val zone: ZoneId, val audience: BriefAudience,
    val period: BriefPeriod, val sources: List<CollectorState>, val organizer: List<OrganizerRecord>,
    val occurrences: List<OrganizerOccurrence>, val attention: DailyAssistantSnapshot = DailyAssistantSnapshot(),
    val baseline: BriefBaseline? = null, val childRecordIds: Set<String> = emptySet(), val childLocalFactIds: Set<String> = emptySet(),
    val attentionObservedAt: Instant? = null, val outstandingReminders: List<OutstandingReminder> = emptyList())

data class BriefProjection(val document: EverydayBrief, val baseline: BriefBaseline)
data class BriefDeliveryRecord(val key: String, val documentId: String, val at: Instant, val state: String)

data class BriefSettings(val version: Long = 0, val proactive: Boolean = false,
    val morning: LocalTime = LocalTime.of(7, 0), val evening: LocalTime = LocalTime.of(19, 0),
    val quietEnabled: Boolean = true, val quietStart: LocalTime = LocalTime.of(22, 0), val quietEnd: LocalTime = LocalTime.of(7, 0),
    val headlineLimit: Int = 3, val taskLimit: Int = 5, val pinnedRecordIds: Set<String> = emptySet(),
    val childRecordIds: Set<String> = emptySet(), val sections: Set<BriefSection> = BriefSection.entries.toSet()) {
    init { require(version >= 0 && headlineLimit in 0..10 && taskLimit in 1..20 && pinnedRecordIds.size <= 100 && childRecordIds.size <= 100) }
    fun quiet(at: LocalTime): Boolean = quietEnabled && when {
        quietStart == quietEnd -> true
        quietStart < quietEnd -> at >= quietStart && at < quietEnd
        else -> at >= quietStart || at < quietEnd
    }
}

interface BriefRepository {
    fun observe(): Flow<List<EverydayBrief>>
    fun observeSettings(): Flow<BriefSettings>
    suspend fun recent(limit: Int = 30): List<EverydayBrief>
    suspend fun get(id: String): EverydayBrief?
    suspend fun baseline(date: LocalDate, zone: ZoneId, audience: BriefAudience): BriefBaseline?
    /** Atomic first morning baseline and document publication; later briefs never rewrite that baseline. */
    suspend fun publish(projection: BriefProjection): EverydayBrief
    suspend fun remove(id: String)
    suspend fun clear()
    suspend fun settings(): BriefSettings
    suspend fun saveSettings(value: BriefSettings, expectedVersion: Long): BriefSettings
    suspend fun deliveryKeys(since: Instant): Set<String>
    suspend fun deliveries(since: Instant): List<BriefDeliveryRecord>
    suspend fun claimDelivery(key: String, documentId: String, at: Instant): Boolean
    suspend fun reclaimDelivery(previous: BriefDeliveryRecord, at: Instant): Boolean
    suspend fun finishDelivery(key: String, documentId: String, state: String)
}
