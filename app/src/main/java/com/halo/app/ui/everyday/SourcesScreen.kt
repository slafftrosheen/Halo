package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.halo.app.ai.PrivacyMode
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.*
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloAlertDialog
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.ui.zone5.*
import com.halo.app.ui.screens.notebook.ToolZoneHeader
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourcesScreen(model: SourcesViewModel, sourceId: String?, itemId: String?, onBack: () -> Unit,
    onSource: (String, String?) -> Unit, onOrganizer: (String?) -> Unit, onLocal: (LocalFact) -> Unit, onLibraries: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val familyReady by Graph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
    val familyEnabled by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val privacy by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val kids = !familyReady || familyEnabled
    val power = privacy == PrivacyMode.CLOUD
    val canShow = familyReady && familyEnabled == state.kids
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val now by produceState(Instant.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (isActive) { value = Instant.now(); delay(60_000) } }
    }
    fun time(value: Instant) = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(ZoneId.systemDefault()).format(value)
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var language by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedOnly by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var weather by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<CollectorSource?>(null) }
    var clearing by remember { mutableStateOf<CollectorSource?>(null) }
    var article by remember { mutableStateOf<ArticleReminderReview?>(null) }
    var externalError by remember { mutableStateOf<String?>(null) }
    val current = state.sources.find { it.source.id == sourceId }?.takeIf { canShow && (!kids || SourcesViewModel.childSource(it.source)) }?.let { row ->
        state.manualReceipt?.takeIf { it.source.id == row.source.id && it.source.version == row.source.version } ?: row
    }
    LaunchedEffect(kids) { if (kids) { adding = false; weather = false; removing = null; clearing = null; article = null } }
    val owner = ShellPageOwner(if (sourceId == null) "everyday_sources" else "everyday_source", objectId = sourceId,
        sourceIdentity = "${current?.source?.version}:${current?.snapshot?.contentRevision}:$itemId:${state.busy}:$kids:${state.draft != null}",
        statusLabel = uiText(if (power) "Power" else "Local"), guidanceBlocked = state.busy || state.loading || state.draft != null,
        helpDescription = uiText("Choose sources on this phone. Only selected, enabled sources refresh in Power. Saved snapshots remain readable in Local."),
        defaultDisabledActionReason = uiText(if (!power) "Refresh external sources in Power. Saved information stays available in Local." else "Select and enable the source, or wait for the current action to finish."))
    BindShellPageActions(owner, listOf(
        ShellPageAction("page.sources.add", uiText("Add feed"), HaloGlyph.INTEGRATIONS, !kids && !state.busy) { adding = true },
        ShellPageAction("page.sources.weather", uiText("Add weather city"), HaloGlyph.WATCH, !kids && !state.busy) { weather = true },
        ShellPageAction("page.sources.settings", uiText("Source settings"), HaloGlyph.SETTINGS, current != null && !kids && !state.busy) { current?.source?.let { model.edit(it) } },
        ShellPageAction("page.sources.refresh", uiText("Refresh source"), HaloGlyph.ACTIVITY,
            current?.source?.let { it.selected && it.enabled && (!it.external || power) } == true && !kids && !state.busy) { current?.source?.let(model::refresh) },
        ShellPageAction("page.sources.stop", uiText("Cancel refresh"), HaloGlyph.STOP, current?.attempt?.state == CollectorAttemptState.FETCHING && !kids) { sourceId?.let(model::cancelRefresh) },
    ))
    if (LocalShellHaloOrb.current) BindShellUtilityBand(owner) { ToolZoneHeader(uiText(if (sourceId == null) "Sources" else "Saved source"), onBack) }
    fun external(url: String) {
        if (kids || Graph.kidsPreferences.enabled.value) return
        if (Graph.privacyModes.mode.value != PrivacyMode.CLOUD) { externalError = "Opening a publisher website needs Power. The saved article remains available here."; return }
        externalError = runCatching {
            val checked = CollectorUrlPolicy.validate(url)
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(checked.toString())))
        }.fold({ null }, { "This website could not be opened. The saved information is kept." })
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Zone5PageHeading(current?.source?.title ?: uiText("Sources"), uiText("Saved information with clear sources and timestamps."))
            Text(uiText(if (power) "Power · Only selected and enabled sources may refresh." else "Local · No internet refresh. Saved external information may be out of date."), style = MaterialTheme.typography.bodySmall)
            if (!LocalShellHaloOrb.current) TextButton(onBack) { Text(uiText("Back")) }
        }
        if (sourceId == null && !kids) item { TextButton(onLibraries, Modifier.fillMaxWidth()) { Text(uiText("Reference libraries")) } }
        if (state.loading || state.busy || !canShow) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.takeIf { !kids }?.let { item { EverydayMessage(it, true); TextButton(model::retry, enabled = !state.busy) { Text(uiText("Retry")) } } }
        state.notice?.takeIf { !kids }?.let { item { EverydayMessage(it) } }
        externalError?.let { item { EverydayMessage(it, true) } }
        if (sourceId != null && current == null && !state.loading && canShow) item { Text(uiText("This source is unavailable or is no longer shared with this experience.")) }
        if (current != null) {
            val source = current.source
            item {
                Text(source.publisher, style = MaterialTheme.typography.labelLarge)
                Text(uiText(sourceCategoryLabel(source.category)) + " · " + source.language)
                if (!source.selected) Text(uiText("Not selected · this source does not refresh."))
                else if (!source.enabled) Text(uiText("Refresh disabled · the saved snapshot is kept."))
                if (source.muted) Text(uiText("Muted in briefs. Selected sources can still refresh."))
                current.snapshot?.let { snapshot ->
                    Text(uiText(if (source.kind == CollectorKind.LOCAL_LIBRARY) "Local import metadata · source freshness is not verified" else if (snapshot.stale(now, source.external, power)) "Cached · may be out of date" else "Saved snapshot is current"), style = MaterialTheme.typography.titleMedium)
                    Text(uiText("Retrieved %1\$s", time(snapshot.retrievedAt)), style = MaterialTheme.typography.bodySmall)
                    Text(uiText(if (source.kind == CollectorKind.LOCAL_LIBRARY) "Import metadata checked %1\$s" else "Last verified %1\$s", time(snapshot.verifiedAt)), style = MaterialTheme.typography.bodySmall)
                }
                Text(uiText(attemptLabel(current.attempt.state)), style = MaterialTheme.typography.bodySmall)
                current.attempt.at?.let { Text(uiText("Latest attempt %1\$s", time(it)), style = MaterialTheme.typography.bodySmall) }
                current.attempt.nextAllowedAt?.takeIf { it > now }?.let { Text(uiText("Next eligible refresh %1\$s", time(it)), style = MaterialTheme.typography.bodySmall) }
                source.attributionUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (!kids) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ model.select(source, !source.selected) }, enabled = !state.busy) { Text(uiText(if (source.selected) "Deselect source" else "Select source")) }
                    TextButton({ clearing = source }, enabled = !state.busy && current.snapshot != null) { Text(uiText("Clear cached information")) }
                    TextButton({ removing = source }, enabled = !state.busy) { Text(uiText("Remove source")) }
                }
                if (itemId != null) TextButton({ onSource(source.id, null) }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Back to saved source")) }
            }
            if (!kids && itemId != null && current.source.research?.providerId == "wikipedia") item {
                TextButton({ current.snapshot?.let { snapshot -> model.retrieveWikipedia(source.id, source.version, snapshot.contentRevision, itemId) { onSource(it, null) } } },
                    enabled = power && !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Retrieve full Wikipedia article")) }
            }
            collectorContent(current, itemId, now, locale, kids, { onSource(source.id, it) }, ::external, { entry ->
                current.snapshot?.let { article = ArticleReminderReview(source.id, source.version, it.contentRevision, entry) }
            }, onLocal)
        } else if (sourceId == null && canShow) {
            item {
                OutlinedTextField(query, { query = it.take(200) }, label = { Text(uiText("Find sources")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                FilterChip(selectedOnly, { selectedOnly = !selectedOnly }, modifier = Modifier.heightIn(min = 56.dp), label = { Text(uiText("Selected sources")) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(category == null, { category = null }, modifier = Modifier.heightIn(min = 56.dp), label = { Text(uiText("All categories")) })
                    SourceCategory.entries.forEach { value -> FilterChip(category == value.name, { category = value.name }, modifier = Modifier.heightIn(min = 56.dp), label = { Text(uiText(sourceCategoryLabel(value))) }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(language == null, { language = null }, modifier = Modifier.heightIn(min = 56.dp), label = { Text(uiText("All languages")) })
                    state.sources.map { it.source.language }.distinct().sorted().forEach { value ->
                        FilterChip(language == value, { language = value }, modifier = Modifier.heightIn(min = 56.dp), label = { Text(value) })
                    }
                }
            }
            val sources = state.sources.filter { row ->
                (!selectedOnly || row.source.selected) && (category == null || row.source.category.name == category) &&
                    (language == null || row.source.language == language) && "${row.source.title} ${row.source.publisher}".contains(query, true)
            }
            if (sources.isEmpty() && !state.loading) item { Text(uiText("No matching sources. Change the filters or add a feed.")) }
            items(sources, key = { it.source.id }) { row ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(row.source.title, style = MaterialTheme.typography.titleMedium)
                        Text("${row.source.publisher} · ${row.source.language}", style = MaterialTheme.typography.bodySmall)
                        Text(uiText(sourceCategoryLabel(row.source.category)), style = MaterialTheme.typography.labelLarge)
                        if (!kids) EverydayToggle(uiText("Include in selected sources"), row.source.selected, !state.busy) { model.select(row.source, it) }
                        Text(uiText(if (row.snapshot == null) "No saved snapshot" else if (row.snapshot.stale(now, row.source.external, power)) "Cached · may be out of date" else "Saved snapshot is current"), style = MaterialTheme.typography.bodySmall)
                        TextButton({ onSource(row.source.id, null) }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Open source")) }
                    }
                }
            }
        }
    }
    if (!kids) state.draft?.let { SourceSettings(model, state, it) }
    if (adding && !kids) AddFeedSheet(model, state, power) { adding = false }
    if (weather && !kids) OfflineCityPicker(state.busy, onDismiss = { weather = false }) { city ->
        val place = city.weatherPlace()
        model.edit(CollectorSource("weather:${java.util.UUID.randomUUID()}", city.name, "Open-Meteo", CollectorKind.WEATHER,
            SourceCategory.WEATHER, "und", OpenMeteoParser.endpoint(place), "https://open-meteo.com/", place = place), creating = true)
        weather = false
    }
    removing?.takeIf { !kids }?.let { source -> HaloAlertDialog({ removing = null }, title = { Text(uiText("Remove this source?")) },
        text = { Text(uiText("This permanently removes its settings and cached content from Halo. Organizer items, notes and other sources are kept. Deselect it instead if you want to keep its settings.")) },
        confirmButton = { TextButton({ model.remove(source); removing = null }) { Text(uiText("Remove source")) } },
        dismissButton = { TextButton({ removing = null }) { Text(uiText("Cancel")) } }) }
    clearing?.takeIf { !kids }?.let { source -> HaloAlertDialog({ clearing = null }, title = { Text(uiText("Clear cached information?")) },
        text = { Text(uiText("This removes the saved snapshot and invalidates its old brief links. A selected source can fetch again in Power. Its settings are kept.")) },
        confirmButton = { TextButton({ model.clear(source); clearing = null }) { Text(uiText("Clear cached information")) } },
        dismissButton = { TextButton({ clearing = null }) { Text(uiText("Cancel")) } }) }
    article?.takeIf { !kids }?.let { review ->
        val entry = review.article
        var date by rememberSaveable(entry.id) { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
        var at by rememberSaveable(entry.id) { mutableStateOf("09:00") }
        var invalid by remember { mutableStateOf(false) }
        HaloAlertDialog({ if (!state.busy) article = null }, title = { Text(uiText("Remind me about this article")) }, text = { Column {
            Text(entry.title); Text(entry.publisher, style = MaterialTheme.typography.bodySmall)
            EverydayDateField(uiText("Reminder date"), date, !state.busy) { date = it }
            OutlinedTextField(at, { at = it.take(5) }, label = { Text(uiText("Time · HH:mm")) }, singleLine = true)
            if (invalid) EverydayMessage("Use a valid date and 24-hour HH:mm time.", true)
            state.error?.let { EverydayMessage(it, true) }
        } }, confirmButton = { TextButton({
            val schedule = runCatching { LocalDate.parse(date) to LocalTime.parse(at) }.getOrNull()
            invalid = schedule == null
            schedule?.let { model.remindArticle(review, it.first, it.second) { id -> article = null; onOrganizer(id) } }
        }, enabled = !state.busy) { Text(uiText("Save reminder")) } },
            dismissButton = { TextButton({ article = null }, enabled = !state.busy) { Text(uiText("Cancel")) } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddFeedSheet(model: SourcesViewModel, state: SourcesPageState, power: Boolean, dismiss: () -> Unit) {
    val draftId = rememberSaveable { "custom:${java.util.UUID.randomUUID()}" }
    var url by rememberSaveable { mutableStateOf("") }; var title by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("en") }; var type by rememberSaveable { mutableStateOf(CollectorKind.FEED.name) }
    var selected by rememberSaveable { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    fun close() {
        if (!state.busy) {
            if (url.isNotBlank() || title.isNotBlank() || language != "en" || type != CollectorKind.FEED.name || selected) discard = true
            else dismiss()
        }
    }
    LaunchedEffect(state.addedSourceId, draftId) { if (state.addedSourceId == draftId) dismiss() }
    EverydaySheet(::close) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Add feed"), style = MaterialTheme.typography.headlineSmall)
            Text(uiText("A source can be saved in Local without fetching it. Preview checks real feed contents in Power."))
            state.error?.let { EverydayMessage(it, true) }
            OutlinedTextField(title, { title = it.take(200) }, label = { Text(uiText("Source name")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it.take(4096) }, label = { Text(uiText("Public HTTPS feed address")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(language, { language = it.take(20) }, label = { Text(uiText("Source language · en, ru, lv or another code")) }, modifier = Modifier.fillMaxWidth())
            FlowRow {
                listOf(CollectorKind.FEED to "RSS or Atom feed", CollectorKind.ALERTS to "CAP alerts", CollectorKind.PUBLIC_CALENDAR to "Public iCalendar").forEach { (kind, label) ->
                    FilterChip(type == kind.name, { type = kind.name }, label = { Text(uiText(label)) })
                }
            }
            EverydayToggle(uiText("Include in selected sources"), selected, !state.busy) { selected = it }
            Button({ model.preview(url, CollectorKind.valueOf(type), language) }, enabled = power && !state.busy && url.isNotBlank()) { Text(uiText("Preview feed")) }
            if (!power) Text(uiText("Preview is unavailable in Local. You can save the address now and check it later in Power."), style = MaterialTheme.typography.bodySmall)
            state.preview?.takeIf { it.candidate.endpoint == url && it.candidate.kind.name == type && it.candidate.language == language }?.let { preview ->
                when (val result = preview.result) {
                    is CollectorFetchResult.Updated -> {
                        Text(uiText("Preview received. It is not saved as a source snapshot."))
                        when (val payload = result.payload) {
                            is CollectorPayload.Articles -> payload.items.take(5).forEach { Text(it.title) }
                            is CollectorPayload.Calendar -> payload.preview.records.take(5).forEach { Text(it.title) }
                            is CollectorPayload.Warnings -> payload.items.take(5).forEach { Text(it.title) }
                            else -> Unit
                        }
                    }
                    else -> EverydayMessage("No usable preview was received. Check the address or try again after the refresh delay.", true)
                }
            }
            Button({ model.addUrl(draftId, url, title, CollectorKind.valueOf(type), language, selected) }, enabled = !state.busy && title.isNotBlank() && url.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(uiText("Save source")) }
            TextButton(::close, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(uiText("Cancel")) }
        }
    }
    if (discard) HaloAlertDialog({ discard = false }, title = { Text(uiText("Discard this edit?")) },
        text = { Text(uiText("The source has not been added. The details in this form will be discarded.")) },
        confirmButton = { TextButton({ discard = false; dismiss() }) { Text(uiText("Discard")) } },
        dismissButton = { TextButton({ discard = false }) { Text(uiText("Keep editing")) } })
}

internal fun attemptLabel(state: CollectorAttemptState) = when (state) {
    CollectorAttemptState.NEVER -> "Not checked yet"; CollectorAttemptState.FETCHING -> "Refreshing"
    CollectorAttemptState.CURRENT -> "Last refresh succeeded"; CollectorAttemptState.NOT_MODIFIED -> "Source confirmed unchanged"
    CollectorAttemptState.LOCAL_ONLY -> "Refresh needs Power and permitted connected services"
    CollectorAttemptState.OFFLINE -> "No network connection · saved information is kept"; CollectorAttemptState.DISABLED -> "Refresh disabled"
    CollectorAttemptState.BACKOFF -> "Waiting before the next refresh"; CollectorAttemptState.QUOTA -> "Refresh budget reached"
    CollectorAttemptState.FAILED -> "Refresh failed · saved information is kept"; CollectorAttemptState.CANCELLED -> "Refresh cancelled"
}
