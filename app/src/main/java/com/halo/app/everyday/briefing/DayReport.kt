package com.halo.app.everyday.briefing

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.halo.app.data.DayCaptureRow
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.EverydayReadAuthority
import com.halo.app.everyday.collectors.CollectorKind
import com.halo.app.everyday.collectors.CollectorPayload
import com.halo.app.everyday.organizer.*
import com.halo.app.ui.everyday.EverydayRoutes
import com.halo.app.ui.screens.notifications.ReplyCapability
import com.halo.app.ui.toolkit.HaloFocusOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

internal enum class DayReportPeriod(val label: String) { TODAY("Today"), YESTERDAY("Yesterday"), WEEK("This week") }
internal enum class DayReportCard(val label: String, val explanation: String) {
    TIMELINE("Hour by hour", "These times come from saved Planner entries, replyable messages, and photos you took in Halo."),
    PLACES("Where you went", "These cities come from location tags on photos you took in Halo. A photo tag doesn't show how you travelled."),
    PHOTOS("Photos you took", "These are saved captures from Halo's phone camera."),
    FOCUS("Focus & screen", "Focus time comes from Halo Focus. Screen time comes from Android after you grant Usage access."),
    NEEDS("Needs you", "These are due Planner tasks and active messages where Android offers a reply."),
    TOMORROW("Tomorrow", "These plans come from Planner for the next day."),
    NEWS("News from your sources", "These saved headlines come from your selected sources. You turned on news for this report."),
}

internal data class DayReportChoices(val plans: Boolean = true, val messages: Boolean = true, val photos: Boolean = true,
    val places: Boolean = false, val focus: Boolean = false, val news: Boolean = false, val hidden: Set<DayReportCard> = emptySet())

internal data class FocusNote(val sessionId: String, val text: String, val completedAt: Long)

internal class DayReportSettings private constructor(private val context: Context) {
    private val disk = context.getSharedPreferences("halo_day_report", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(DayReportChoices(
        disk.getBoolean("plans", true), disk.getBoolean("messages", true), disk.getBoolean("photos", true),
        disk.getBoolean("places", false), disk.getBoolean("focus", false), disk.getBoolean("news", false),
        disk.getStringSet("hidden", emptySet()).orEmpty().mapNotNull { value ->
            DayReportCard.entries.firstOrNull { it.name == value }
        }.toSet()))
    val choices = current.asStateFlow()
    private val recentFocusNote = MutableStateFlow(runCatching {
        val json = JSONObject(disk.getString("focusNote", null) ?: return@runCatching null)
        FocusNote(json.getString("sessionId"), json.getString("text"), json.getLong("completedAt"))
    }.getOrNull())
    val focusNote = recentFocusNote.asStateFlow()

    @Synchronized fun keepFocusNote(sessionId: String, text: String) {
        val family = Graph.kidsPreferences.adultActionRevision()
        val statement = text.trim().also { require(it.isNotEmpty() && it.length <= 240 && it.none(Char::isISOControl)) }
        val note = FocusNote(sessionId, statement, System.currentTimeMillis())
        Graph.kidsPreferences.requireAdultAction(family)
        check(disk.edit().putString("focusNote", JSONObject().put("sessionId", note.sessionId)
            .put("text", note.text).put("completedAt", note.completedAt).toString()).commit())
        recentFocusNote.value = note
    }

    @Synchronized fun removeFocusNote(id: String) {
        if (recentFocusNote.value?.sessionId != id) return
        val family = Graph.kidsPreferences.adultActionRevision()
        Graph.kidsPreferences.requireAdultAction(family)
        check(disk.edit().remove("focusNote").commit())
        recentFocusNote.value = null
    }

    @Synchronized fun save(value: DayReportChoices) {
        val newsChanged = current.value.news != value.news
        val edit = disk.edit().putBoolean("plans", value.plans).putBoolean("messages", value.messages)
            .putBoolean("photos", value.photos).putBoolean("places", value.places).putBoolean("focus", value.focus)
            .putBoolean("news", value.news).putStringSet("hidden", value.hidden.map { it.name }.toSet())
        if (!value.focus) edit.remove("focusHistory")
        check(edit.commit())
        EverydayReadAuthority.invalidate()
        current.value = value
        if (newsChanged) runCatching { com.halo.app.nativeactions.EverydayWidgetUpdates.enqueue(context) }
    }

    private fun history(): List<FocusSpan> {
        val rows = runCatching { JSONArray(disk.getString("focusHistory", "[]")) }.getOrElse { JSONArray() }
        return (0 until rows.length()).mapNotNull { index ->
            rows.optJSONObject(index)?.let { FocusSpan(it.optString("id"), it.optLong("start"), it.optLong("end")) }
                ?.takeIf { it.start > 0 && it.end > it.start }
        }
    }

    @Synchronized fun recordFocus(id: String, from: Long, until: Long) {
        if (!current.value.focus || !Graph.kidsPreferences.loadedState.value || Graph.kidsPreferences.enabled.value) return
        val cutoff = System.currentTimeMillis() - Duration.ofDays(8).toMillis()
        val history = history().filter { it.end >= cutoff && it.id != id } + FocusSpan(id, from, until)
        disk.edit().putString("focusHistory", JSONArray(history.takeLast(1440).map {
            JSONObject().put("id", it.id).put("start", it.start).put("end", it.end)
        }).toString()).apply()
    }

    @Synchronized fun focusHistory(): List<Pair<Long, Long>> = if (!current.value.focus) emptyList()
        else history().map { it.start to it.end }

    private data class FocusSpan(val id: String, val start: Long, val end: Long)
    companion object {
        @Volatile private var instance: DayReportSettings? = null
        fun get(context: Context): DayReportSettings = instance ?: synchronized(this) {
            instance ?: DayReportSettings(context.applicationContext).also { instance = it }
        }
    }
}

internal enum class DayMomentTone { ORDINARY, DONE, NEEDS }
internal data class DayMoment(val id: String, val title: String, val at: Instant?, val route: String?,
    val tone: DayMomentTone = DayMomentTone.ORDINARY, val allDay: Boolean = false, val detail: String? = null)
internal data class DayPhoto(val id: String, val title: String, val at: Instant, val uri: Uri)
internal data class DayReport(val date: LocalDate, val zone: ZoneId, val period: DayReportPeriod,
    val timeline: List<DayMoment>, val completed: Int, val scheduled: Int, val needs: List<DayMoment>,
    val tomorrow: List<DayMoment>, val photos: List<DayPhoto>, val places: List<String>, val focusMinutes: Long,
    val screenMinutes: Long?, val news: List<DayMoment>, val choices: DayReportChoices, val choicesRevision: Long,
    val notificationRevision: Long?)

internal class DayReportService(private val context: Context) {
    val settings = DayReportSettings.get(context)
    private val preparedReport = MutableStateFlow<DayReport?>(null)
    private var preparedDate: LocalDate? = null
    private val preparationLock = kotlinx.coroutines.sync.Mutex()

    suspend fun prepareEvening() = preparationLock.withLock {
        val now = java.time.ZonedDateTime.now()
        if (now.toLocalTime() < LocalTime.of(21, 0) || preparedDate == now.toLocalDate() ||
            Graph.kidsPreferences.getEnabled()) return@withLock
        preparedReport.value = load(DayReportPeriod.TODAY)
        preparedDate = now.toLocalDate()
    }

    fun prepared(): DayReport? = preparedReport.value?.takeIf {
        it.date == LocalDate.now() && it.zone == ZoneId.systemDefault() && it.choices == settings.choices.value &&
            it.choicesRevision == EverydayReadAuthority.capture() && Graph.kidsPreferences.loadedState.value &&
            !Graph.kidsPreferences.enabled.value && it.needs.none { row -> row.route == "notifications" } &&
            it.photos.isEmpty() && it.places.isEmpty() && it.focusMinutes == 0L && it.screenMinutes == null
    }

    suspend fun load(period: DayReportPeriod): DayReport = withContext(Dispatchers.IO) {
        val family = Graph.kidsPreferences.adultActionRevision()
        val choices = settings.choices.value
        val revision = EverydayReadAuthority.capture()
        val notificationPolicy = if (choices.messages) Graph.notificationPolicies.changes.value else null
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate()
        val date = if (period == DayReportPeriod.YESTERDAY) today.minusDays(1) else today
        val start = if (period == DayReportPeriod.WEEK) date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else date
        val from = start.atStartOfDay(zone).toInstant()
        val endOfDate = date.plusDays(1).atStartOfDay(zone).toInstant()
        val until = minOf(now, endOfDate)
        val selected = Graph.collectorRepository.states().filter { it.source.selected && it.source.enabled && !it.source.muted }
        fun enabled(kind: CollectorKind) = selected.any { it.source.kind == kind }
        val plansEnabled = choices.plans && enabled(CollectorKind.LOCAL_ORGANIZER)
        val workspaceEnabled = (choices.photos || choices.places) && enabled(CollectorKind.LOCAL_WORKSPACE)
        val messagesEnabled = choices.messages && enabled(CollectorKind.LOCAL_NOTIFICATIONS) && Graph.notificationBridge.isAccessGranted()
        val planKinds = setOf(OrganizerKind.EVENT, OrganizerKind.TASK, OrganizerKind.REMINDER)
        val agenda = if (plansEnabled) Graph.organizerUseCases.agenda(from, endOfDate, planKinds) else emptyList()
        val tasks = if (plansEnabled) Graph.organizerRepository.records().filter { it.kind == OrganizerKind.TASK } else emptyList()
        val checked = tasks.filter { it.status == OrganizerStatus.COMPLETED && it.updatedAt >= from && it.updatedAt < until }
        val recurringChecked = agenda.filter { it.completed && it.record.repeat != null && it.start < until }
        val checkedIds = checked.map { it.id }.toSet()
        val planMoments = agenda.filter { it.start < until && it.record.id !in checkedIds }.map(::moment) +
            checked.map { DayMoment(it.id, it.title, it.updatedAt, EverydayRoutes.organizer(it.id), DayMomentTone.DONE) }
        val dueFrom = if (period == DayReportPeriod.TODAY) start.minusDays(30).atStartOfDay(zone).toInstant() else from
        val dueAgenda = if (plansEnabled && dueFrom < from) Graph.organizerUseCases.agenda(dueFrom, until, setOf(OrganizerKind.TASK)) else agenda
        val due = dueAgenda.filter { it.record.kind == OrganizerKind.TASK && !it.completed &&
            it.record.status == OrganizerStatus.ACTIVE && it.start < until }
            .map { moment(it).copy(tone = DayMomentTone.NEEDS) }
        val openTasks = tasks.filter { it.status == OrganizerStatus.ACTIVE && it.time == null && it.createdAt >= dueFrom && it.createdAt < until }
            .map { DayMoment(it.id, it.title, null, EverydayRoutes.organizer(it.id), DayMomentTone.NEEDS) }
        val messages = if (messagesEnabled) {
            val inbox = Graph.notificationInboxSource
            val refresh = inbox.refresh()
            withTimeoutOrNull(2_000) {
                combine(inbox.state, inbox.appliedRefresh) { state, applied -> state to applied }.first { it.second >= refresh }.first
            }?.items.orEmpty().filter { it.active && it.category == "Messages" && it.reply is ReplyCapability.Available &&
                    it.postedAt >= from && it.postedAt < until }.map {
                    DayMoment(it.key, it.title.orEmpty().ifBlank { it.appLabel }, it.postedAt,
                        "notifications", DayMomentTone.NEEDS, detail = it.appLabel)
                }
        } else emptyList()
        val captures = if (workspaceEnabled) Graph.appDatabase.workspaceContextDao().dayCaptures(from.toEpochMilli(), until.toEpochMilli())
            .mapNotNull { capture(it, from, until) } else emptyList()
        val photos = captures.takeIf { choices.photos }.orEmpty()
        val places = if (choices.places) captures.take(30).mapNotNull { photo ->
            val coordinates = runCatching {
                context.contentResolver.openInputStream(photo.uri)?.use { ExifInterface(it).latLong }
            }.getOrNull()
            coordinates?.let { Graph.offlineCities.nearest(it[0], it[1], limitKm = 15.0)?.name }
        }.distinct() else emptyList()
        val focus = if (choices.focus) {
            val history = settings.focusHistory() + listOfNotNull(HaloFocusOwner.get(context).dayReportSpan())
            history.distinctBy { it.first }.sumOf { (startMs, endMs) ->
                (minOf(endMs, until.toEpochMilli()) - maxOf(startMs, from.toEpochMilli())).coerceAtLeast(0)
            } / 60_000L
        } else 0L
        val screen = if (choices.focus) androidScreenMinutes(context, from, until) else null
        val nextDate = date.plusDays(1)
        val tomorrow = if (plansEnabled) Graph.organizerUseCases.agenda(nextDate.atStartOfDay(zone).toInstant(),
            nextDate.plusDays(1).atStartOfDay(zone).toInstant(), planKinds).filter { !it.completed }.map(::moment) else emptyList()
        val news = if (choices.news) selected.flatMap { state ->
            (state.snapshot?.payload as? CollectorPayload.Articles)?.items.orEmpty().mapNotNull { article ->
                val posted = article.publishedAt?.takeIf { it >= from && it < until } ?: return@mapNotNull null
                DayMoment(article.id, article.title, posted, EverydayRoutes.source(state.source.id, article.id), detail = article.publisher)
            }
        }.sortedByDescending { it.at }.take(10) else emptyList()
        Graph.kidsPreferences.requireAdultAction(family)
        EverydayReadAuthority.requireCurrent(revision)
        check(settings.choices.value == choices)
        if (messagesEnabled) check(Graph.notificationBridge.isAccessGranted() && notificationPolicy == Graph.notificationPolicies.changes.value)
        DayReport(date, zone, period, (planMoments + messages + photos.map {
            DayMoment("capture:${it.id}", it.title, it.at, "notebook/note/" + Uri.encode(it.id))
        }).sortedWith(compareBy({ it.at }, { it.id })), checked.size + recurringChecked.size,
            agenda.count { it.record.kind == OrganizerKind.EVENT }, (due + openTasks + messages).sortedBy { it.at }, tomorrow, photos,
            places, focus, screen, news, choices, revision, notificationPolicy.takeIf { messagesEnabled })
    }

    private fun moment(entry: OrganizerOccurrence) = DayMoment(entry.key, entry.record.title, entry.start,
        if (entry.record.repeat == null) EverydayRoutes.organizer(entry.record.id) else EverydayRoutes.organizer(entry.record.id, entry.originalStart),
        if (entry.completed) DayMomentTone.DONE else DayMomentTone.ORDINARY, entry.allDay)

    private fun capture(row: DayCaptureRow, from: Instant, until: Instant): DayPhoto? {
        val metadata = runCatching { JSONObject(row.metadata) }.getOrNull() ?: return null
        if (metadata.optString("sourceKind") !in setOf("PHONE_CAMERA", "DEVICE_CAPTURE")) return null
        val at = Instant.ofEpochMilli(metadata.optLong("captureTimestampMs").takeIf { it > 0 } ?: row.captured)
        val uri = Uri.parse(row.uri).takeIf { it.scheme == "content" && it.authority == context.packageName + ".captures" } ?: return null
        return DayPhoto(row.objectId, row.title, at, uri).takeIf { at >= from && at < until }
    }
}

internal fun screenTimeAvailable(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 28) return false
    val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
    @Suppress("DEPRECATION")
    return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}

/** Only screen state events are used. The report never records app names or activity history. */
private fun androidScreenMinutes(context: Context, from: Instant, until: Instant): Long? {
    if (!screenTimeAvailable(context)) return null
    val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
    val start = from.toEpochMilli()
    val end = until.toEpochMilli()
    val events = manager.queryEvents(start - Duration.ofDays(1).toMillis(), end) ?: return null
    val row = UsageEvents.Event()
    var active: Long? = null
    var total = 0L
    var any = false
    while (events.hasNextEvent() && events.getNextEvent(row)) {
        when (row.eventType) {
            UsageEvents.Event.SCREEN_INTERACTIVE -> { any = true; if (active == null) active = row.timeStamp }
            UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP -> {
                any = true
                active?.let { total += (row.timeStamp - maxOf(it, start)).coerceAtLeast(0) }
                active = null
            }
        }
    }
    active?.let { total += (end - maxOf(it, start)).coerceAtLeast(0) }
    return (total / 60_000L).takeIf { any }
}
