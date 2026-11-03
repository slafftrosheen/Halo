package com.halo.app.ui.everyday

import com.halo.app.data.notebook.*
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.briefing.*
import com.halo.app.everyday.collectors.*

/** Preserve legacy Screen paths for saved entries; these instruments now belong under Workspace's Organizer.
 * Imported routes are never passed to NavController. */
object EverydayRoutes {
    const val ORGANIZER = "screen/organizer"
    const val CLOCKS = "screen/clocks"
    const val BRIEF = "screen/brief"
    const val SOURCES = "screen/sources"
    const val SOURCE = "screen/source"
    private fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    fun organizer(id: String? = null, originalStart: java.time.Instant? = null) = id?.let { "$ORGANIZER?recordId=${encode(it)}"+
        (originalStart?.let { at -> "&occurrenceStart=${encode(at.toString())}" } ?: "") } ?: ORGANIZER
    fun clocks(zoneId: String, label: String) = "$CLOCKS?zoneId=${encode(zoneId)}&cityLabel=${encode(label)}"
    fun brief(id: String? = null) = id?.let { "$BRIEF?briefId=${encode(it)}" } ?: BRIEF
    fun source(id: String, item: String? = null) = "$SOURCE?sourceId=${encode(id)}" + (item?.let { "&itemId=${encode(it)}" } ?: "")

    /** Strict shape for internal typed entry delivery; generic screen Intent strings still use the exact catalogue. */
    fun acceptsEntry(route: String): Boolean = runCatching {
        val uri = android.net.Uri.parse(route)
        val allowed = when (route.substringBefore("?")) {
            ORGANIZER -> {
                val id = uri.getQueryParameter("recordId"); val create = uri.getQueryParameter("createKind")
                val original=uri.getQueryParameter("occurrenceStart")?.let(java.time.Instant::parse)
                if (id != null) id.matches(Regex("[A-Za-z0-9_.:-]{1,200}")) && route == organizer(id,original)
                else create != null && create in com.halo.app.everyday.organizer.OrganizerKind.entries.map { it.name } && route == "$ORGANIZER?createKind=$create"
            }
            SOURCE -> {
                val id = uri.getQueryParameter("sourceId"); val item = uri.getQueryParameter("itemId")
                id != null && id.matches(Regex("[A-Za-z0-9_.:-]{1,160}")) && (item == null || item.length <= 4096) && route == source(id,item)
            }
            CLOCKS -> {
                val zone=uri.getQueryParameter("zoneId")?.let(java.time.ZoneId::of)?.id
                val label=uri.getQueryParameter("cityLabel")
                zone != null && label != null && label.length<=120 && route==clocks(zone,label)
            }
            else -> false
        }
        allowed
    }.getOrDefault(false)

    suspend fun briefFact(documentId: String, factId: String): String {
        val brief = requireNotNull(Graph.briefService.visible(documentId))
        val fact = requireNotNull((brief.facts + brief.changes.mapNotNull { it.fact }).find { it.id == factId })
        fact.values["recordId"]?.let { id ->
            check(Graph.organizerRepository.get(id)?.deletedAt == null && Graph.organizerRepository.get(id) != null)
            return organizer(id,fact.values["occurrence"]?.let(java.time.Instant::parse))
        }
        if (fact.kind == BriefFactKind.ATTENTION) {
            val action = requireNotNull(Graph.dailyAssistantController.recentBase().items.find { it.id == fact.values["localId"] }?.action)
            return com.halo.app.ui.shell.dailyOwnerRoute(action)
        }
        val source = requireNotNull(Graph.collectorRepository.state(fact.sourceId))
        require(source.source.selected && source.source.enabled && !source.source.muted)
        if (source.source.external) {
            if (Graph.kidsPreferences.getEnabled()) require(SourcesViewModel.childSource(source.source))
            val item = if (fact.kind == BriefFactKind.HEADLINE) {
                val rows = (source.snapshot?.payload as? CollectorPayload.Articles)?.items.orEmpty()
                requireNotNull(rows.find { "halo://sources/${source.source.id}/article/${it.id}" == fact.route }).id
            } else if (source.source.kind == CollectorKind.PUBLIC_CALENDAR && fact.kind == BriefFactKind.EVENT) {
                val selection = requireNotNull(fact.values["calendarSelection"]?.let(SavedCalendarSelection::decode))
                requireNotNull(selection.resolve(source))
                selection.encode()
            } else null
            return EverydayRoutes.source(source.source.id, item)
        }
        val id = requireNotNull(fact.values["localId"])
        return local(LocalFact(id, when (fact.kind) {
            BriefFactKind.NOTE -> "NOTE"; BriefFactKind.NOTIFICATION -> "HALO_NOTIFICATION"
            BriefFactKind.ACTIVITY -> if (id.startsWith("run:")) "ACTIVITY" else "TIMELINE"
            else -> "DEVICE"
        }, fact.title, fact.detail, fact.route, null, fact.at), allowSharedChildNote = brief.audience == BriefAudience.KIDS && fact.kind == BriefFactKind.NOTE)
    }
    suspend fun local(fact: LocalFact, allowSharedChildNote: Boolean = false): String {
        val kids = Graph.kidsPreferences.getEnabled()
        check(!kids || allowSharedChildNote && fact.kind == "NOTE")
        return when (fact.kind) {
            "EVENT", "TASK", "REMINDER", "ALARM", "TIMER" -> {
                requireNotNull(Graph.organizerRepository.get(fact.id)).also { require(it.deletedAt == null) }
                organizer(fact.id)
            }
            "NOTE" -> {
                val note = requireNotNull(Graph.workspaceRepository.get(WorkspaceObjectId(fact.id)))
                require(note.type == WorkspaceObjectType.NOTE && note.lifecycle == WorkspaceObjectLifecycle.ACTIVE)
                "notebook/note/${encode(fact.id)}"
            }
            "ACTIVITY" -> {
                val id = fact.id.removePrefix("run:"); requireNotNull(Graph.appDatabase.agentRunDao().get(id))
                "activity?runId=${encode(id)}"
            }
            "TIMELINE" -> { require(Graph.currentTimelineEvents().any { "timeline:${it.presentationId}" == fact.id }); "activity" }
            "HALO_NOTIFICATION" -> "notifications"
            "LOCAL_MODEL" -> "model_browser"
            "BATTERY", "RUNTIME", "DEVICE" -> "devices"
            else -> error("Unsupported local item")
        }
    }
}

internal class EverydayViewModelFactory<T : androidx.lifecycle.ViewModel>(private val create: () -> T) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : androidx.lifecycle.ViewModel> create(modelClass: Class<VM>): VM = create() as VM
}
