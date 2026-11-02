package com.halo.app.everyday.presentation

import com.halo.app.everyday.organizer.*
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.briefing.*
import kotlinx.coroutines.flow.*

/** Read-only Library references. Editing and lifecycle remain with each instrument; no duplicate Notes store. */
data class EverydayLibraryEntry(val kind: String,val id: String,val version: Long,val title: String,val detail: String,val updatedAt: Long,
    val target: EverydayTarget,val code: String="") {
    val key get()="everyday:$kind:$id"
    val manageTool get()=when(kind) {"ORGANIZER" -> "halo_organizer_get";"SOURCE" -> "halo_source_get";"CLOCK" -> "halo_clock_query";else -> "halo_brief_query"}
}
class EverydayLibraryProjection(private val organizer: OrganizerRepository,private val briefs: BriefRepository,
    private val clocks: WorldClockRepository,private val sources: CollectorRepository,
    private val family: Flow<Boolean>,private val ready: Flow<Boolean>) {
    private data class Data(val records: List<OrganizerRecord>,val briefs: List<EverydayBrief>,val clocks: List<WorldClockRecord>,val sources: List<CollectorState>,val settings: BriefSettings)
    fun observe(): Flow<List<EverydayLibraryEntry>> = combine(organizer.observe(),briefs.observe(),clocks.observe(),sources.observe(),briefs.observeSettings()) {
        records,documents,cities,feeds,settings ->Data(records,documents,cities,feeds,settings)
    }.combine(combine(family,ready) {kids,loaded ->kids to loaded}) {data,scope ->
        if(!scope.second) emptyList() else project(data.records,data.briefs,data.clocks,data.sources,data.settings,scope.first)
    }
    companion object {
        fun project(records: List<OrganizerRecord>,briefs: List<EverydayBrief>,clocks: List<WorldClockRecord>,sources: List<CollectorState>,settings: BriefSettings,kids: Boolean): List<EverydayLibraryEntry> = buildList {
            records.filter {it.deletedAt==null && (!kids || it.id in settings.childRecordIds)}.forEach {r ->
                add(EverydayLibraryEntry("ORGANIZER",r.id,r.version,r.title,r.time?.let {"${it.start} · ${it.zoneId}"}.orEmpty(),r.updatedAt.toEpochMilli(),EverydayTarget.Record(r.id,r.version),r.kind.name))
            }
            briefs.filter {!kids || it.audience==BriefAudience.KIDS}.forEach {b ->
                // Metadata never contains a fact/title excerpt that can outlive a sharing grant.
                add(EverydayLibraryEntry("BRIEF",b.id,1,b.date.toString(),b.generatedAt.toString(),b.generatedAt.toEpochMilli(),EverydayTarget.Brief(b.id),b.period.name))
            }
            if(!kids) clocks.filter {it.deletedAt==null}.forEach {c ->
                add(EverydayLibraryEntry("CLOCK",c.id,c.version,c.label,c.zoneId,0,EverydayTarget.Clock(c.zoneId,c.label)))
            }
            sources.filter {s ->s.source.external && s.source.selected && s.source.enabled && !s.source.muted && (!kids || s.source.childApproved)}.forEach {s ->
                add(EverydayLibraryEntry("SOURCE",s.source.id,s.source.version,s.source.title,s.source.publisher,s.snapshot?.retrievedAt?.toEpochMilli() ?: 0,
                    EverydayTarget.Source(s.source.id,s.source.version,s.snapshot?.contentRevision),s.source.kind.name))
            }
        }.sortedWith(compareByDescending<EverydayLibraryEntry> {it.updatedAt}.thenBy {it.key})
    }
}
