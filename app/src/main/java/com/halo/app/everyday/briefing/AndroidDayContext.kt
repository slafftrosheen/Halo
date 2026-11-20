package com.halo.app.everyday.briefing

import com.halo.app.data.AppDatabase
import com.halo.app.data.notification.NotificationRepository
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.organizer.*
import java.time.Instant
import java.time.ZoneId

/** Fresh owner reads respect source selection and notification policies. No notification prose is copied into feed caches. */
class AndroidDayContext(private val database: AppDatabase, private val sources: CollectorRepository,
    private val organizer: OrganizerUseCases, private val notifications: NotificationRepository,
    private val notificationAccess: () -> Boolean, private val notificationRevision: () -> Long) {
    val reader = DayContextReader(::load, ::body)
    private suspend fun load(from: Instant, until: Instant): DayContextSnapshot {
        val all = sources.states()
        val selected = all.filter { it.source.selected && it.source.enabled && !it.source.muted }
        fun enabled(kind: CollectorKind) = selected.any { it.source.kind == kind }
        fun within(at: Instant) = at >= from && at < until
        val coverage = linkedMapOf("sources" to "${selected.size} selected sources; ${all.size - selected.size} excluded by source settings. Saved snapshots only; unknown publication dates included explicitly.")
        val rows = linkedMapOf<String, List<DayContextItem>>()
        rows["SOURCES"] = selected.filter { it.source.external }.flatMap { state ->
            val snapshot = state.snapshot?.takeIf { it.sourceVersion == state.source.version }
            val reference = mapOf<String, Any?>("sourceId" to state.source.id, "sourceVersion" to state.source.version,
                "contentRevision" to snapshot?.contentRevision, "retrievedAt" to snapshot?.retrievedAt?.toString(),
                "expiresAt" to snapshot?.expiresAt?.toString(), "attempt" to state.attempt.state.name)
            when (val payload = snapshot?.payload) {
                is CollectorPayload.Articles -> payload.items.filter { it.publishedAt == null || within(it.publishedAt) }.map {
                    DayContextItem("source:${it.id}", it.title, it.publishedAt, snapshot.contentRevision,
                        reference + mapOf("articleId" to it.id, "contentStatus" to it.contentStatus, "imageCount" to it.images.size, "url" to it.url))
                }
                else -> listOf(DayContextItem("source:${state.source.id}", state.source.title, snapshot?.retrievedAt,
                    "${state.source.version}:${snapshot?.contentRevision}", reference + mapOf("available" to (snapshot != null))))
            }
        }
        rows["ORGANIZER"] = if (enabled(CollectorKind.LOCAL_ORGANIZER)) {
            val agenda = organizer.agenda(from, until).map { it.record.id }.toSet()
            organizer.repository.records().filter { within(it.updatedAt) || it.id in agenda }.take(1001).map {
                DayContextItem(it.id, it.title, it.updatedAt, it.version.toString(), mapOf("kind" to it.kind.name, "status" to it.status.name,
                    "scheduledInWindow" to (it.id in agenda), "readTool" to "halo_organizer_get"))
            }
        } else emptyList()
        rows["WORKSPACE"] = if (enabled(CollectorKind.LOCAL_WORKSPACE)) database.notebookDao().dayObjects(from.toEpochMilli(), until.toEpochMilli(), 1001).map {
            DayContextItem(it.id, it.title, Instant.ofEpochMilli(it.updatedAt), "${it.updatedAt}:${it.noteRevision}",
                mapOf("type" to it.type, "readTool" to "halo_get_workspace_object"))
        } else emptyList()
        val notices = if (enabled(CollectorKind.LOCAL_NOTIFICATIONS) && notificationAccess())
            notifications.recentForDigest(from, 500).filter { it.postedAt < until } else emptyList()
        rows["NOTIFICATIONS"] = notices.map {
            val text = "${it.content.title.orEmpty()}\n${it.content.body.orEmpty()}"
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
            DayContextItem(it.key, it.content.title ?: it.appLabel ?: it.packageName, it.postedAt,
                "${notificationRevision()}:$hash", mapOf("app" to (it.appLabel ?: it.packageName), "active" to it.active, "redacted" to (text.isBlank())))
        }
        coverage["notifications"] = "Access=${notificationAccess()}; policyRevision=${notificationRevision()}; retained at most 24 hours, maximum 500 recent permitted notifications; hidden/blocked content excluded. ${if (notices.size == 500) "LIMIT_REACHED: additional notifications may be omitted." else ""}"
        rows["ACTIVITY"] = if (enabled(CollectorKind.LOCAL_ACTIVITY)) database.agentRunDao().dayCompletedRoots(from.toEpochMilli(), until.toEpochMilli(), 1001).map {
            DayContextItem(it.id, it.request.take(1000), Instant.ofEpochMilli(it.updatedAt), "${it.updatedAt}:${it.state}", mapOf("state" to it.state))
        } else emptyList()
        listOf("ORGANIZER" to CollectorKind.LOCAL_ORGANIZER, "WORKSPACE" to CollectorKind.LOCAL_WORKSPACE, "ACTIVITY" to CollectorKind.LOCAL_ACTIVITY).forEach { (section, kind) ->
            coverage[section.lowercase()] = when { !enabled(kind) -> "Excluded by source settings";
                rows[section].orEmpty().size > 1000 -> "LIMIT_REACHED: first 1000 items only";
                else -> "Current saved items in the time window; historical deletions and uncaptured activity are unavailable" }
            rows[section] = rows[section].orEmpty().take(1000)
        }
        return DayContextSnapshot(rows.mapValues { (_, items) -> items.sortedWith(compareBy<DayContextItem> { it.at ?: Instant.MIN }.thenBy { it.id }) }, coverage)
    }
    private suspend fun body(section: String, item: DayContextItem): String? = when (section) {
        "ORGANIZER" -> organizer.repository.get(item.id)?.takeIf { it.version.toString() == item.version }?.let {
            "${it.title}\n${it.details}\nStatus: ${it.status}\nTime: ${it.time}\nLocation: ${it.location}"
        }
        "WORKSPACE" -> database.notebookDocumentDao().getDocument(item.id)?.takeIf {
            it.lifecycle == "ACTIVE" && "${it.updatedAt}:${it.revision}" == item.version
        }?.body
        "NOTIFICATIONS" -> {
            check(notificationAccess())
            notifications.recentForDigest(requireNotNull(item.at), 500).find { it.key == item.id }?.let { "${it.content.title.orEmpty()}\n${it.content.body.orEmpty()}" }
        }
        "ACTIVITY" -> database.agentRunDao().get(item.id)?.run?.let { "${it.request}\nOutcome: ${it.state}" }
        else -> null // Sources retain their own exact-version paged body and pixel tools.
    }
}
