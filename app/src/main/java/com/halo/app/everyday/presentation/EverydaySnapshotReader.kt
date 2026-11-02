package com.halo.app.everyday.presentation

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.room.withTransaction
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.organizer.*
import com.halo.app.ui.everyday.EverydayRoutes
import com.halo.app.ui.everyday.SourcesViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*

/** Local database reads only, bounded to the card's seven-day window. No bootstrap seed or remote refresh. */
suspend fun readEverydaySnapshot(context: Context, now: Instant = Instant.now()): EverydaySnapshot = withContext(Dispatchers.IO) {
    val kids = Graph.kidsPreferences.getEnabled()
    val familyRevision=Graph.kidsPreferences.presentationRevision()
    val modeRevision=Graph.privacyModes.revision
    val epoch = if (!kids) Graph.kidsPreferences.adultActionRevision() else null
    val zone = ZoneId.systemDefault()
    val snapshot = Graph.appDatabase.withTransaction {
        val settings = Graph.briefRepository.settings()
        val records = Graph.organizerRepository.records().filter { !kids || it.id in settings.childRecordIds }
        val occurrences = mutableListOf<OrganizerOccurrence>()
        records.filter { it.time != null && it.status == OrganizerStatus.ACTIVE }.forEach { record ->
            val start=now.atZone(zone).toLocalDate().minusDays(if(record.kind in setOf(OrganizerKind.TASK,OrganizerKind.REMINDER)) 30 else 0).atStartOfDay(zone).toInstant()
            occurrences += OrganizerRecurrence.between(record, Graph.organizerRepository.overrides(record.id), start,
                now.atZone(zone).plusDays(7).toInstant(), zone, limit = 10000)
            require(occurrences.size <= 10000) { "The plan is too large for a card. Open Organizer." }
        }
        val sources = Graph.collectorRepository.states().filter { !kids || SourcesViewModel.childSource(it.source) }
        val briefs = Graph.briefRepository.recent(60).filter { it.date == now.atZone(zone).toLocalDate() && it.zoneId == zone.id }
            .asSequence().filter { !kids || it.audience == com.halo.app.everyday.briefing.BriefAudience.KIDS }.take(5).toList()
        val visible = briefs.mapNotNull { Graph.briefService.visible(it.id) }.take(1)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        EverydaySnapshot(now, zone, records, occurrences, sources, visible,
            if (kids) emptyList() else Graph.worldClocks.all(), records.flatMap { Graph.organizerRepository.receipts(it.id) },
            kids, settings.childRecordIds, Graph.privacyModes.mode.value == com.halo.app.ai.PrivacyMode.CLOUD,
            if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0,100) else null,
            (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,readEverydayAuthorityStamp(),familyRevision,modeRevision)
    }
    if (kids != Graph.kidsPreferences.enabled.value || runCatching { Graph.kidsPreferences.requirePresentation(familyRevision) }.isFailure ||
        epoch != null && runCatching { Graph.kidsPreferences.requireAdultAction(epoch) }.isFailure)
        throw CancellationException("Family context changed during card read")
    snapshot
}

/** Called inside the final Room publication transaction. Includes same-profile sharing/source edits. */
suspend fun readEverydayAuthorityStamp(): String = Graph.appDatabase.withTransaction {
    val value=buildString {
        Graph.organizerRepository.records(true).sortedBy { it.id }.forEach { append(it.id).append(':').append(it.version).append(';') }
        Graph.appDatabase.organizerDao().allReceipts().forEach { append(it.occurrenceKey).append(':').append(it.recordVersion).append(':')
            .append(it.state).append(':').append(it.updatedAt).append(':').append(it.snoozedUntil).append(';') }
        Graph.collectorRepository.states().sortedBy { it.source.id }.forEach { append(it.source.id).append(':').append(it.source.version)
            .append(':').append(it.snapshot?.contentRevision).append(':').append(it.attempt).append(';') }
        Graph.briefRepository.recent(60).forEach { append(it.id).append(';') }
        append(Graph.briefRepository.settings().version)
        Graph.worldClocks.all().forEach { append(it.id).append(':').append(it.version).append(';') }
        append(Graph.kidsPreferences.presentationRevision()).append(':').append(Graph.privacyModes.revision)
    }
    java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** A face is a review, not authority. Recheck the canonical source at the user's tap. */
suspend fun EverydayTarget.route(): String = when (this) {
    EverydayTarget.Organizer -> EverydayRoutes.ORGANIZER
    EverydayTarget.Sources -> EverydayRoutes.SOURCES
    EverydayTarget.Clocks -> EverydayRoutes.CLOCKS
    EverydayTarget.Devices -> "devices"
    EverydayTarget.BriefLatest -> EverydayRoutes.BRIEF
    is EverydayTarget.Clock -> EverydayRoutes.clocks(zoneId,label)
    is EverydayTarget.Record -> {
        val row = requireNotNull(Graph.organizerRepository.get(id))
        require(row.version == version && row.deletedAt == null)
        if (Graph.kidsPreferences.getEnabled()) require(id in Graph.briefRepository.settings().childRecordIds)
        if(originalStart != null) requireNotNull(OrganizerRecurrence.exact(row,Graph.organizerRepository.overrides(id),originalStart,ZoneId.systemDefault()))
        EverydayRoutes.organizer(id,originalStart)
    }
    is EverydayTarget.Source -> {
        val current = requireNotNull(Graph.collectorRepository.state(id))
        require(current.source.version == version && current.source.selected && current.source.enabled && !current.source.muted)
        if (Graph.kidsPreferences.getEnabled()) require(SourcesViewModel.childSource(current.source))
        require(revision == null || current.snapshot?.contentRevision == revision)
        if (articleId != null) require((current.snapshot?.payload as? com.halo.app.everyday.collectors.CollectorPayload.Articles)?.items?.any { it.id == articleId } == true)
        EverydayRoutes.source(id, articleId)
    }
    is EverydayTarget.Brief -> EverydayRoutes.brief(id)
    is EverydayTarget.Create -> {
        check(!Graph.kidsPreferences.getEnabled())
        "${EverydayRoutes.ORGANIZER}?createKind=${kind.name}"
    }
}
