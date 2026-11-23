package com.halo.app.ui.screens.home

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.halo.app.language.uiText
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.halo.app.R
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.workspace.AdaptiveSlot
import com.halo.app.data.workspace.SavedAdaptivePage
import com.halo.app.data.workspace.WorkspaceQuery
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.briefing.DayReportSettings
import com.halo.app.everyday.briefing.TodayNewsReport
import com.halo.app.everyday.briefing.todayWeather
import com.halo.app.everyday.briefing.upcomingPlannerEvents
import com.halo.app.everyday.collectors.ArticleImage
import com.halo.app.everyday.collectors.CollectorKind
import com.halo.app.everyday.collectors.CollectorPayload
import com.halo.app.everyday.organizer.OrganizerOccurrence
import com.halo.app.everyday.organizer.PlannerCalendarPreferences
import com.halo.app.everyday.organizer.android.PhoneCalendarAgenda
import com.halo.app.everyday.organizer.android.PhoneCalendarEvent
import com.halo.app.ui.adaptive.AdaptiveTodayPage
import com.halo.app.ui.adaptive.adaptiveTodayFeed
import com.halo.app.ui.everyday.EverydayRoutes
import com.halo.app.ui.everyday.ArticleHeadlinePicture
import com.halo.app.ui.everyday.ArticleHeadlineStrip
import com.halo.app.ui.everyday.OfflineCityPicker
import com.halo.app.ui.everyday.SourcesViewModel
import com.halo.app.ui.everyday.gallery
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.shell.HaloDeepLinks
import com.halo.app.ui.zone6.Zone6ButtonSheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class HaloHomeBlock(
    val id: String,
    val heading: Int,
    val title: String,
    val source: String,
    val why: Int,
    val route: String,
    val supporting: String? = null,
    val departure: HaloBlockDeparture? = null,
    val explanation: String? = null,
    val pictures: List<ArticleImage> = emptyList(),
)

internal data class HaloBlockDeparture(val label: String, val route: String)

internal class HaloBlockChoices(private val storage: SharedPreferences) {
    var pinned by mutableStateOf(storage.getStringSet("pinned", emptySet()).orEmpty().toSet())
        private set
    var hidden by mutableStateOf(storage.getStringSet("hidden", emptySet()).orEmpty().toSet())
        private set
    var autoAdvance by mutableStateOf(storage.getBoolean("autoAdvance", true))
        private set

    fun toggleAutoAdvance() {
        autoAdvance = !autoAdvance
        storage.edit().putBoolean("autoAdvance", autoAdvance).apply()
    }

    fun pin(id: String) {
        pinned = if (id in pinned) pinned - id else pinned + id
        storage.edit().putStringSet("pinned", pinned).apply()
    }

    fun hide(id: String) {
        hidden = hidden + id
        pinned = pinned - id
        storage.edit().putStringSet("hidden", hidden).putStringSet("pinned", pinned).apply()
    }

    fun restore() {
        hidden = emptySet()
        storage.edit().remove("hidden").apply()
    }

    fun retarget(oldId: String, currentId: String) {
        if (oldId !in pinned && oldId !in hidden) return
        if (oldId in pinned) pinned = pinned - oldId + currentId
        if (oldId in hidden) hidden = hidden - oldId + currentId
        storage.edit().putStringSet("pinned", pinned).putStringSet("hidden", hidden).apply()
    }
}

@Composable
internal fun rememberHaloBlockChoices(page: String): HaloBlockChoices {
    val context = LocalContext.current
    return remember(context, page) {
        HaloBlockChoices(context.getSharedPreferences("halo_${page}_blocks_v1", Context.MODE_PRIVATE))
    }
}

@Composable
internal fun HaloTodayScreen(open: (String) -> Unit, savedPages: Boolean = false, onBack: () -> Unit = {},
    initialPage: SavedAdaptivePage? = null) {
    val context = LocalContext.current
    val choices = rememberHaloBlockChoices("today")
    val pinned = choices.pinned
    val feedsFlow = remember { Graph.collectorRepository.observe() }
    val feeds by feedsFlow.collectAsStateWithLifecycle(emptyList())
    val cachedNews by Graph.todayNews.history.collectAsStateWithLifecycle()
    val picturesVisible by Graph.articlePictureSettings.visible.collectAsStateWithLifecycle()
    val language by Graph.appLanguagePreferences.language.collectAsStateWithLifecycle()
    val weatherSettings: SourcesViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.halo.app.ui.everyday.EverydayViewModelFactory {
            SourcesViewModel(context.applicationContext as com.halo.app.HaloApplication, androidx.lifecycle.SavedStateHandle())
        },
    )
    val weatherSettingsState by weatherSettings.state.collectAsStateWithLifecycle()
    var chooseCity by rememberSaveable { mutableStateOf(false) }
    var addWidget by rememberSaveable { mutableStateOf(false) }
    var adaptivePage by rememberSaveable { mutableStateOf(savedPages) }
    val plannerFlow = remember { Graph.organizerRepository.observe() }
    val records by plannerFlow.collectAsStateWithLifecycle(emptyList())
    val calendarChanges by PlannerCalendarPreferences.observeChanges.collectAsStateWithLifecycle()
    val calendarSettings = remember(calendarChanges) { runCatching { PlannerCalendarPreferences.read(Graph.storage) }.getOrNull() }
    val zone = ZoneId.systemDefault()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(Instant.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = Instant.now()
                delay(60_000)
            }
        }
    }
    var calendarGranted by remember { mutableStateOf(PhoneCalendarAgenda.permitted(context)) }
    val requestCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calendarGranted = it }
    LaunchedEffect(now) { calendarGranted = PhoneCalendarAgenda.permitted(context) }
    val phoneEvents by produceState<List<PhoneCalendarEvent>>(emptyList(), calendarGranted, now) {
        value = if (!calendarGranted) emptyList() else try { PhoneCalendarAgenda.upcoming(context, now, zone) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { emptyList() }
    }
    LaunchedEffect(now, feeds.any { it.source.kind == CollectorKind.FEED && it.source.selected }, language) {
        try { Graph.todayNews.refreshIfDue() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Keep the last visible report and try again on the normal schedule. */ }
    }
    val today = now.atZone(zone).toLocalDate()
    val hour = now.atZone(zone).hour
    val evening = hour >= 18
    val day = today.plusDays(if (evening) 1 else 0)
    val next by produceState<List<OrganizerOccurrence>>(emptyList(), day, records, calendarSettings, now) {
        val visibility = calendarSettings ?: return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                upcomingPlannerEvents(now, zone, visibility)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
    val activeSources = feeds.filter { it.source.enabled && it.source.selected && !it.source.muted }
    val locale = LocalConfiguration.current.locales[0]
    val clock = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
    val currentRecord = records.associateBy { it.id }
    val upcoming = next.filter { it.start >= now && it.start < today.plusDays(7).atStartOfDay(zone).toInstant() &&
        calendarSettings?.appearance(it.record.calendar)?.visible == true &&
        currentRecord[it.record.id]?.version == it.record.version }
    val nextEvent = upcoming.firstOrNull()
    LaunchedEffect(nextEvent?.key) {
        nextEvent?.let { choices.retarget("day", "event:${it.key}") }
    }
    val quietFlow = remember { Graph.briefRepository.observeSettings() }
    val quiet by quietFlow.collectAsStateWithLifecycle(com.halo.app.everyday.briefing.BriefSettings(quietEnabled = false))
    val dueFlow = remember { Graph.studyUseCases.observeDuePracticeItems(limit = 100) }
    val due by dueFlow.collectAsStateWithLifecycle(emptyList())
    val cardsFlow = remember { Graph.appDatabase.studyDao().observeLibraryPracticeCards() }
    val cards by cardsFlow.collectAsStateWithLifecycle(emptyList())
    val eligibleCards = remember(cards) { cards.mapTo(hashSetOf()) { it.id } }
    val dueCount = due.count { it.id in eligibleCards }
    val focusSettings = remember(context) { DayReportSettings.get(context) }
    val focusNote by focusSettings.focusNote.collectAsStateWithLifecycle()
    val planner = stringResource(R.string.today_block_planner)
    val recent = recentLibraryBlock("library")
    val weatherSource = todayWeather(activeSources)
    val study = if (dueCount > 0) HaloHomeBlock("study", R.string.today_block_study,
        stringResource(R.string.today_block_due, dueCount), stringResource(R.string.halo11_home_library),
        R.string.today_block_why_study, "study") else null
    val windDown = if (quiet.quietEnabled) HaloHomeBlock("winddown", R.string.today_block_wind_down,
        stringResource(R.string.today_block_quiet_at, clock.format(quiet.quietStart)), planner,
        R.string.today_block_why_wind_down, "winddown") else null
    val focus = focusNote?.takeIf { Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() == today }?.let {
        HaloHomeBlock("focus", R.string.halo11_focus_done, it.text, stringResource(R.string.halo_focus_title),
            R.string.halo11_focus_keep_hint, "focus")
    }
    val permittedPhoneEvents = phoneEvents.takeIf {
        calendarGranted && PhoneCalendarAgenda.permitted(context)
    }.orEmpty()
    if (adaptivePage) {
        var refresh by remember { mutableIntStateOf(0) }
        val local = listOfNotNull(
            study?.let { AdaptiveSlot.STUDY to it },
            recent?.let { AdaptiveSlot.LIBRARY to it },
            focus?.let { AdaptiveSlot.FOCUS to it },
            windDown?.let { AdaptiveSlot.WIND_DOWN to it },
        ).toMap()
        val blocks = adaptiveTodayFeed(refresh, now, upcoming, permittedPhoneEvents, local,
            weatherSource?.source?.id)
        AdaptiveTodayPage(blocks, open, savedPages,
            onBack = { if (savedPages) onBack() else adaptivePage = false },
            onRefresh = { refresh++ }, initialPage = initialPage)
        return
    }
    val article = activeSources.asSequence().filter { it.source.kind == CollectorKind.FEED }.flatMap { state ->
        val snapshot = state.snapshot?.takeIf { it.sourceVersion == state.source.version }
        (snapshot?.payload as? CollectorPayload.Articles)?.items.orEmpty().asSequence().map { state.source to it }
    }.maxByOrNull { it.second.publishedAt ?: Instant.MIN }
    val weather = weatherSource?.let { state ->
        state.snapshot?.takeIf { it.sourceVersion == state.source.version && it.expiresAt.isAfter(now) }
            ?.payload as? CollectorPayload.Weather
    }
    val date = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val available = buildList {
        upcoming.forEachIndexed { index, event ->
            val eventRoute = EverydayRoutes.organizer(event.record.id, event.originalStart)
            val time = if (event.allDay) stringResource(R.string.today_block_all_day)
                else clock.format(event.start.atZone(zone))
            val departure = event.record.leaveBeforeMinutes?.takeUnless { event.allDay }?.let { minutes ->
                event.start.minusSeconds(minutes * 60L).takeIf { it.isAfter(now) }
            }
            val leave = departure?.let {
                HaloBlockDeparture(stringResource(R.string.today_departure_at, clock.format(it.atZone(zone))),
                    "maps?eventId=${android.net.Uri.encode(event.record.id)}" +
                        "&occurrenceStart=${android.net.Uri.encode(event.originalStart.toString())}")
            }
            // Keep an independently pinned departure from an older installation.
            val standaloneDeparture = index == 0 && "route" in pinned
            val eventDate = event.start.atZone(zone).toLocalDate()
            val heading = when (eventDate) {
                today -> R.string.today_block_day
                today.plusDays(1) -> R.string.today_block_tomorrow
                else -> R.string.today_block_upcoming
            }
            val supporting = listOfNotNull(
                date.format(eventDate).takeIf { eventDate > today.plusDays(1) },
                event.examLessons.take(2).joinToString { it.title }.takeIf(String::isNotBlank),
            ).joinToString(" · ").takeIf(String::isNotBlank)
            add(HaloHomeBlock("event:${event.key}", heading,
                stringResource(R.string.today_block_at, event.record.title, time), planner, R.string.today_block_why_day,
                eventRoute, supporting = supporting,
                departure = leave.takeUnless { standaloneDeparture || "route" in choices.hidden },
                explanation = stringResource(R.string.today_why_event_named, event.record.title, time)))
            if (standaloneDeparture && leave != null) {
                add(HaloHomeBlock("route", R.string.today_block_leave, event.record.title, planner,
                    R.string.today_block_why_leave, leave.route, supporting = leave.label))
            }
        }
        weatherSource?.let { state ->
            val rain = weather?.fact?.days?.firstOrNull { it.date == day }?.precipitationProbability
            val temp = weather?.fact?.temperatureC
            val supporting = when {
                rain != null -> stringResource(if (rain >= 60) R.string.today_block_umbrella else R.string.today_block_rain, rain)
                temp != null -> stringResource(R.string.today_weather_temperature, kotlin.math.round(temp).toInt())
                else -> stringResource(R.string.today_weather_waiting)
            }
            add(HaloHomeBlock("weather", R.string.today_block_weather, state.source.place?.name ?: state.source.title,
                state.source.publisher, R.string.today_block_why_weather, EverydayRoutes.source(state.source.id), supporting = supporting))
        }
        if (!evening || "study" in pinned) study?.let(::add)
        article?.let { (source, item) ->
            if (!evening || "reads" in pinned) add(HaloHomeBlock("reads", R.string.today_block_reads, item.title, item.publisher.ifBlank { source.publisher },
                R.string.today_block_why_reads, EverydayRoutes.source(source.id, item.id),
                pictures = item.gallery(picturesVisible, kids = false)))
        }
        if (evening || "winddown" in pinned) windDown?.let(::add)
        if (!evening || "library" in pinned) recent?.let(::add)
        focus?.let(::add)
    }
    HaloTodayContent(hour, available, choices, open, chooseWeatherCity = { chooseCity = true },
        news = cachedNews.filter { it.isVisible(feeds, language.tag, now) },
        newsPictures = picturesVisible,
        liveArticles = activeSources.flatMap { row ->
            (row.snapshot?.payload as? CollectorPayload.Articles)?.items.orEmpty().map { row.source.id to it.id }
        }.toSet(),
        newsEnabled = activeSources.any { it.source.kind == CollectorKind.FEED },
        phoneEvents = permittedPhoneEvents,
        connectCalendar = if (!calendarGranted) ({ requestCalendar.launch(android.Manifest.permission.READ_CALENDAR) }) else null,
        onAddWidget = { addWidget = true }, onMakePage = { adaptivePage = true })
    if (addWidget) com.halo.app.nativeactions.EverydayWidgetPicker(
        initialKind = com.halo.app.everyday.presentation.EverydayCardKind.TODAY) { addWidget = false }
    if (chooseCity && !weatherSettingsState.kids) {
        OfflineCityPicker(weatherSettingsState.busy, error = weatherSettingsState.error, onDismiss = { chooseCity = false }, weather = true) { city ->
            weatherSettings.setTodayWeatherCity(city) { chooseCity = false }
        }
    }
}

@Composable
internal fun HaloTodayContent(hour: Int, available: List<HaloHomeBlock>, choices: HaloBlockChoices, open: (String) -> Unit,
    chooseWeatherCity: (() -> Unit)? = null, news: List<TodayNewsReport> = emptyList(), newsEnabled: Boolean = false,
    newsPictures: Boolean = true, liveArticles: Set<Pair<String, String>> = emptySet(),
    phoneEvents: List<PhoneCalendarEvent> = emptyList(), connectCalendar: (() -> Unit)? = null,
    onAddWidget: (() -> Unit)? = null, onMakePage: (() -> Unit)? = null) {
    val evening = hour >= 18
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val calendarLabel = stringResource(R.string.today_phone_calendar)
    val calendarDate = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val calendarTime = remember(locale) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale) }
    val phoneRoutes = phoneEvents.associateBy { "phone-calendar/${it.id}/${it.start.toEpochMilli()}" }
    val phoneBlocks = phoneRoutes.map { (route, event) ->
        val at = if (event.allDay) calendarDate.format(event.start.atZone(java.time.ZoneOffset.UTC))
            else calendarTime.format(event.start.atZone(ZoneId.systemDefault()))
        HaloHomeBlock(route, R.string.today_block_upcoming, event.title, calendarLabel,
            R.string.today_why_phone_calendar, route, supporting = at)
    }
    val allBlocks = available + phoneBlocks
    var cannotOpenCalendar by remember { mutableStateOf(false) }
    var addPlan by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val greeting = when {
                    evening -> R.string.today_greeting_evening
                    hour >= 12 -> R.string.today_greeting_afternoon
                    else -> R.string.today_greeting_morning
                }
                Text(stringResource(greeting), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.today_local_sources), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { open("home") }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.home_chat_title))
            }
        }
        onMakePage?.let { make ->
            TextButton(make, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.adaptive_made_for_you))
            }
        }
        chooseWeatherCity?.let { choose ->
            TextButton(choose, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (available.any { it.id == "weather" }) R.string.today_weather_change_city else R.string.today_weather_choose_city))
            }
        }
        Box {
            TextButton(onClick = { addPlan = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.halo_today_add_plan))
            }
            DropdownMenu(addPlan, onDismissRequest = { addPlan = false }) {
                val actions = listOf(
                    com.halo.app.everyday.organizer.OrganizerKind.EVENT to "Add event",
                    com.halo.app.everyday.organizer.OrganizerKind.TASK to "Add task",
                    com.halo.app.everyday.organizer.OrganizerKind.REMINDER to "Add reminder",
                    com.halo.app.everyday.organizer.OrganizerKind.ALARM to "Set alarm",
                    com.halo.app.everyday.organizer.OrganizerKind.TIMER to "Start timer",
                )
                actions.forEach { (kind, label) -> DropdownMenuItem(text = { Text(uiText(label)) }, onClick = {
                    addPlan = false
                    open("${EverydayRoutes.ORGANIZER}?createKind=${kind.name}")
                }) }
            }
        }
        if (allBlocks.none { it.id !in choices.hidden }) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(if (evening) R.string.today_empty_tomorrow else R.string.today_empty),
                        style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { open(EverydayRoutes.ORGANIZER) }) {
                        Text(stringResource(R.string.today_open_planner))
                    }
                }
            }
        }
        HaloHomeBlockDeck(allBlocks, choices, open = { route ->
            val phoneEvent = phoneRoutes[route]
            if (phoneEvent == null) open(route)
            else cannotOpenCalendar = runCatching {
                Graph.kidsPreferences.adultActionRevision()
                check(PhoneCalendarAgenda.permitted(context))
                context.startActivity(PhoneCalendarAgenda.intent(phoneEvent))
            }.isFailure
        }, showSourceSummary = false, paged = true)
        if (cannotOpenCalendar) Text(stringResource(R.string.today_calendar_unavailable), style = MaterialTheme.typography.bodySmall)
        connectCalendar?.let { connect -> TextButton(connect) { Text(stringResource(R.string.today_connect_calendar)) } }
        if (newsEnabled || news.isNotEmpty()) TodayNewsCard(news, newsPictures, liveArticles, open)
        onAddWidget?.let { add -> TextButton(add, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.halo_today_add_widget))
        } }
    }
}

@Composable
private fun HomeCardsPager(keys: List<String>, autoAdvance: Boolean, card: @Composable (Int) -> Unit) {
    val pager = rememberPagerState { keys.size }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(autoAdvance, keys, pager.settledPage, lifecycle) {
        if (autoAdvance && keys.size > 1) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(10_000)
                if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % keys.size)
            }
        }
    }
    HorizontalPager(state = pager, key = { keys[it] }, modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top) { index -> card(index) }
    if (keys.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { scope.launch { pager.scrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } },
            enabled = pager.currentPage > 0) { Text(uiText("Previous")) }
        Text(uiText("%1\$d / %2\$d", pager.currentPage.coerceIn(0, keys.lastIndex) + 1, keys.size),
            style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = { scope.launch { pager.scrollToPage((pager.currentPage + 1).coerceAtMost(keys.lastIndex)) } },
            enabled = pager.currentPage < keys.lastIndex) { Text(uiText("Next")) }
    }
}

@Composable
private fun TodayNewsCard(reports: List<TodayNewsReport>, pictures: Boolean,
    liveArticles: Set<Pair<String, String>>, open: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.today_news_title), style = MaterialTheme.typography.titleLarge)
            if (reports.isEmpty()) {
                Text(stringResource(R.string.today_news_waiting), style = MaterialTheme.typography.bodyMedium)
            } else HomeCardsPager(reports.map { it.generatedAt.toString() }, autoAdvance = false) { index ->
                val report = reports[index]
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val locale = LocalConfiguration.current.locales[0]
                val timestamp = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
                    .format(report.generatedAt.atZone(ZoneId.systemDefault()))
                Text(stringResource(R.string.today_news_generated, timestamp), style = MaterialTheme.typography.labelMedium)
                if (pictures) report.sources.firstNotNullOfOrNull { it.picture }?.let { ArticleHeadlinePicture(it) }
                Text(report.text, style = MaterialTheme.typography.bodyMedium)
                com.halo.app.ui.components.HaloReadAloud(report.text,
                    category = com.halo.sdk.provider.RemoteDataCategory.TEXT)
                report.sources.forEach { source ->
                    val current = (source.id to source.articleId) in liveArticles
                    TextButton({ open(EverydayRoutes.source(source.id, source.articleId.takeIf { current })) },
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(listOf("${source.number}. ${source.publisher}", source.title).filter(String::isNotBlank).joinToString(" · "))
                    }
                }
                }
            }
        }
    }
}

@Composable
internal fun recentLibraryBlock(id: String): HaloHomeBlock? {
    val query = remember {
        Graph.workspaceRepository.observe(WorkspaceQuery(types = setOf(WorkspaceObjectType.NOTE, WorkspaceObjectType.CANVAS)))
    }
    val rows by query.collectAsStateWithLifecycle(emptyList())
    val recent = rows.asSequence().filterNot { it.inInbox }.maxByOrNull { it.updatedAt } ?: return null
    return HaloHomeBlock(id, if (id == "continue") R.string.today_block_continue else R.string.today_block_from_library,
        recent.title, stringResource(R.string.halo11_home_library), R.string.today_block_why_library,
        HaloDeepLinks.PREFIX + "library/item/" + recent.id.value)
}

@Composable
internal fun HaloHomeBlockDeck(
    available: List<HaloHomeBlock>,
    choices: HaloBlockChoices,
    open: (String) -> Unit,
    showSourceSummary: Boolean = true,
    paged: Boolean = false,
) {
    var order by remember { mutableStateOf(emptyList<String>()) }
    var fresh by remember { mutableStateOf(emptySet<String>()) }
    val eligible = available.filter { it.id !in choices.hidden }
    val eligibleIds = eligible.map(HaloHomeBlock::id)
    LaunchedEffect(eligibleIds) {
        val current = eligibleIds.toSet()
        val additions = eligibleIds - order.toSet()
        if (order.isEmpty()) order = additions.sortedBy { it !in choices.pinned }
        else {
            fresh = fresh.intersect(current) + additions
            order = order.filter(current::contains) + additions
        }
    }
    var requestedWhy by remember { mutableStateOf<String?>(null) }
    val visible = eligible.associateBy(HaloHomeBlock::id)
    val initialOrder = eligible.map(HaloHomeBlock::id).sortedBy { it !in choices.pinned }
    val displayOrder = (order + initialOrder).distinct().filter(visible::containsKey)
    if (displayOrder.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showSourceSummary) Text(stringResource(R.string.today_blocks_on_this_phone), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val card: @Composable (String) -> Unit = { id ->
                val block = visible.getValue(id)
                key(id) {
                    HaloHomeBlockCard(block, id in choices.pinned, id in fresh,
                        onOpen = { fresh = fresh - id; open(block.route) },
                        onDeparture = { route -> fresh = fresh - id; open(route) },
                        onPin = { choices.pin(id) },
                        onHide = { choices.hide(id) },
                        onWhy = { requestedWhy = id },
                    )
                }
            }
            if (paged && displayOrder.size > 1) {
                val motion = com.halo.app.ui.theme.rememberVisualPreferences().motionLevel
                val context = LocalContext.current
                val canAutoAdvance = motion != com.halo.app.ui.theme.HaloLookMotion.STILL &&
                    android.animation.ValueAnimator.areAnimatorsEnabled() &&
                    context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)?.isTouchExplorationEnabled != true
                HomeCardsPager(displayOrder, autoAdvance = choices.autoAdvance && canAutoAdvance) { card(displayOrder[it]) }
                if (canAutoAdvance) TextButton(onClick = choices::toggleAutoAdvance) {
                    Text(uiText(if (choices.autoAdvance) "Pause automatic paging" else "Start automatic paging"))
                }
            } else displayOrder.forEach { card(it) }
        }
    }
    requestedWhy?.let(visible::get)?.let { block ->
        Zone6ButtonSheet(stringResource(R.string.today_block_why), onDismiss = { requestedWhy = null }) {
            Text(block.explanation ?: stringResource(block.why), style = MaterialTheme.typography.bodyMedium)
            if (block.departure != null) {
                Text(stringResource(R.string.today_block_why_leave), style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = { requestedWhy = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(android.R.string.ok))
            }
        }
    }
    if (choices.hidden.isNotEmpty()) TextButton(onClick = choices::restore) { Text(stringResource(R.string.today_restore_blocks)) }
}

@Composable
private fun HaloHomeBlockCard(
    block: HaloHomeBlock,
    pinned: Boolean,
    fresh: Boolean,
    onOpen: () -> Unit,
    onDeparture: (String) -> Unit,
    onPin: () -> Unit,
    onHide: () -> Unit,
    onWhy: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val options = stringResource(R.string.today_block_options)
    val colors = MaterialTheme.colorScheme
    val openLabel = stringResource(R.string.today_block_open)
    val glyph = when {
        block.heading == R.string.today_block_reads -> HaloGlyph.READ_MODE
        block.id == "day" || block.id == "route" || block.id.startsWith("event:") ||
            block.id.startsWith("phone-calendar/") -> HaloGlyph.CALENDAR
        block.id == "weather" -> HaloGlyph.CLOUD
        block.id == "study" -> HaloGlyph.MEMORY
        block.id == "winddown" -> HaloGlyph.WATCH
        else -> HaloGlyph.NOTE
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerLow, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column {
            Box {
                Column(Modifier.fillMaxWidth()
                    .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.heightIn(min = 30.dp).padding(end = 38.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        HaloIcon(glyph, null, Modifier.size(15.dp), tint = colors.onSurfaceVariant)
                        Text(stringResource(block.heading).uppercase(LocalConfiguration.current.locales[0]),
                            Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (fresh || pinned) Surface(shape = RoundedCornerShape(6.dp), color = colors.primary.copy(alpha = .10f)) {
                            Text(stringResource(if (pinned) R.string.today_block_pinned else R.string.today_block_new),
                                Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelSmall,
                                color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    block.pictures.firstOrNull()?.let { ArticleHeadlinePicture(it) }
                    Text(block.title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    block.supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                    Text(block.source, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    ArticleHeadlineStrip(block.pictures.drop(1))
                }
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton({ menu = true }) {
                        Icon(painterResource(R.drawable.halo_ic_more), contentDescription = options, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text(stringResource(if (pinned) R.string.today_block_unpin else R.string.today_block_pin)) },
                            { menu = false; onPin() })
                        DropdownMenuItem({ Text(stringResource(R.string.today_block_less)) }, { menu = false; onHide() })
                        DropdownMenuItem({ Text(stringResource(R.string.today_block_why)) }, { menu = false; onWhy() })
                    }
                }
            }
            block.departure?.let { departure ->
                HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = colors.outlineVariant)
                TextButton(onClick = { onDeparture(departure.route) },
                    modifier = Modifier.padding(horizontal = 6.dp).heightIn(min = 48.dp)) {
                    Text(departure.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
