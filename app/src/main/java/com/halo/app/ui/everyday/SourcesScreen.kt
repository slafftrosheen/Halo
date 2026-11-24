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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.halo.app.ai.PrivacyMode
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.*
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloAlertDialog
import com.halo.app.ui.components.HaloReadAloud
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.ui.screens.home.HaloHomeBlock
import com.halo.app.ui.screens.home.HaloHomeBlockDeck
import com.halo.app.ui.screens.home.rememberHaloBlockChoices
import com.halo.app.ui.zone5.*
import com.halo.app.ui.screens.notebook.ToolZoneHeader
import com.halo.app.ui.sources.v13.SourceGroup
import com.halo.app.ui.sources.v13.SourceManager
import com.halo.app.ui.sources.v13.SourceStatus
import com.halo.app.ui.sources.v13.label
import com.halo.app.ui.workspace.WorkspaceNoteDestination
import com.halo.app.ui.workspace.prepareWorkspaceNoteHandoff
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourcesScreen(model: SourcesViewModel, sourceId: String?, itemId: String?, onBack: () -> Unit,
    onSource: (String, String?) -> Unit, onOrganizer: (String?) -> Unit, onLocal: (LocalFact) -> Unit,
    onWidgetsDestination: (String) -> Unit = {}, readsOnly: Boolean = false, onOpenReadBlock: (String) -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val familyReady by Graph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
    val familyEnabled by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val privacy by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val picturesVisible by Graph.articlePictureSettings.visible.collectAsStateWithLifecycle()
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
    var addingWidget by rememberSaveable { mutableStateOf(false) }
    var showingKnowledge by rememberSaveable { mutableStateOf(false) }
    var showingMix by rememberSaveable { mutableStateOf(false) }
    val addWidgetLabel = androidx.compose.ui.res.stringResource(com.halo.app.R.string.alive_widget_title)
    val heading = if (readsOnly) androidx.compose.ui.res.stringResource(com.halo.app.R.string.halo11_home_reads)
        else stringResource(R.string.sp13_sources_title)
    var cityKind by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<CollectorSource?>(null) }
    var clearing by remember { mutableStateOf<CollectorSource?>(null) }
    var article by remember { mutableStateOf<ArticleReminderReview?>(null) }
    var externalError by remember { mutableStateOf<String?>(null) }
    val current = state.sources.find { it.source.id == sourceId }?.takeIf { canShow && (!kids || SourcesViewModel.childSource(it.source)) }?.let { row ->
        state.manualReceipt?.takeIf { it.source.id == row.source.id && it.source.version == row.source.version } ?: row
    }
    val readsHome = readsOnly && sourceId == null && canShow && !kids
    val headlines = remember(state.sources, readsHome) {
        if (!readsHome) emptyList() else state.sources.asSequence().filter {
            it.source.kind == CollectorKind.FEED && it.source.enabled && it.source.selected && !it.source.muted
        }.mapNotNull { row ->
            val snapshot = row.snapshot?.takeIf { it.sourceVersion == row.source.version }
            (snapshot?.payload as? CollectorPayload.Articles)?.items?.maxByOrNull {
                it.publishedAt ?: Instant.MIN
            }?.let { row.source to it }
        }.sortedByDescending { it.second.publishedAt ?: Instant.MIN }.take(6).toList()
    }
    LaunchedEffect(kids) { if (kids) { adding = false; addingWidget = false; showingKnowledge = false; showingMix = false; cityKind = null; removing = null; clearing = null; article = null } }
    val savedArticle = (current?.snapshot?.payload as? CollectorPayload.Articles)?.items?.firstOrNull { it.id == itemId }
    val fullArticle by produceState<ArticleContent?>(null, savedArticle?.contentKey) {
        value = savedArticle?.let { withContext(Dispatchers.IO) { Graph.articleContentStore.read(it.contentKey) } }
    }
    val articleDraftPrompt = uiText("Summarize this saved article using Sources.")
    var articleReading by remember(itemId) { mutableStateOf(false) }
    val articleScope = rememberCoroutineScope()
    var workspaceArticle by remember(sourceId, itemId) { mutableStateOf<SavedArticleIdentity?>(null) }
    var sendingArticle by remember(sourceId, itemId) { mutableStateOf(false) }
    var articleHandoffFailed by remember(sourceId, itemId) { mutableStateOf(false) }
    val articleIdentity = current?.let { row ->
        val snapshot = row.snapshot
        if (savedArticle != null && snapshot != null && snapshot.sourceVersion == row.source.version &&
            row.source.selected && row.source.enabled && !row.source.muted
        ) SavedArticleIdentity(row.source.id, row.source.version, snapshot.contentRevision, savedArticle.id) else null
    }
    fun openHandoff() {
        if (!kids && !sendingArticle) {
            articleHandoffFailed = false
            workspaceArticle = articleIdentity
        }
    }
    fun sendArticle(identity: SavedArticleIdentity, destination: WorkspaceNoteDestination) {
        if (sendingArticle) return
        sendingArticle = true
        articleHandoffFailed = false
        articleScope.launch {
            try {
                val family = com.halo.app.ui.toolkit.requireAdultToolkit(context)
                fun verify() {
                    check(!Graph.haloPause.isPaused())
                    com.halo.app.ui.toolkit.requireAdultToolkit(context, family)
                }
                val route = prepareWorkspaceNoteHandoff(destination, ::verify) {
                    Graph.articleWorkspace.keep(identity, ::verify)
                }
                verify()
                workspaceArticle = null
                onWidgetsDestination(route)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { articleHandoffFailed = true }
            finally { sendingArticle = false }
        }
    }
    val owner = ShellPageOwner(if (sourceId == null) "everyday_sources" else "everyday_source", objectId = sourceId,
        sourceIdentity = "${current?.source?.version}:${current?.snapshot?.contentRevision}:$itemId:${state.busy}:$kids:${state.draft != null}",
        canonicalToolIds = if (sourceId == null && !kids) listOf("halo_source_query", "halo_source_save",
            "halo_knowledge_catalogue", "halo_knowledge_search", "halo_knowledge_download")
            else listOf("halo_source_query", "halo_source_get", "halo_source_image") +
                listOfNotNull("halo_source_keep_article".takeIf { articleIdentity != null && !kids }),
        statusLabel = current?.snapshot?.retrievedAt?.let { uiText("Retrieved %1\$s", time(it)) }
            ?: uiText(if (power) "Power" else "Local"), guidanceBlocked = state.busy || state.loading || state.draft != null,
        helpDescription = uiText("Choose sources on this phone. Only selected, enabled sources refresh in Power. Saved snapshots remain readable in Local."),
        defaultDisabledActionReason = uiText(if (!power) "Refresh external sources in Power. Saved information stays available in Local." else "Select and enable the source, or wait for the current action to finish."))
    BindShellPageActions(owner, buildList {
        if (!kids) {
            if (sourceId == null) add(ShellPageAction("page.sources.knowledge", stringResource(com.halo.app.R.string.knowledge_title), HaloGlyph.INTEGRATIONS) {
                showingKnowledge = true
            })
            if (sourceId == null && !readsOnly) add(ShellPageAction("page.sources.widget", addWidgetLabel, HaloGlyph.TOOL) { addingWidget = true })
            add(ShellPageAction("page.sources.pictures", uiText(if (picturesVisible) "Hide article pictures" else "Show article pictures"), HaloGlyph.SETTINGS) {
                if (!Graph.kidsPreferences.enabled.value) Graph.articlePictureSettings.setVisible(!Graph.articlePictureSettings.visible.value)
            })
        }
        if (savedArticle != null) {
            add(ShellPageAction("page.sources.read", uiText("Read aloud"), HaloGlyph.SPEAK) { articleReading = true })
            val snapshot = current?.snapshot
            val source = current?.source
            if (!kids && source != null && snapshot != null && source.selected && source.enabled && !source.muted &&
                snapshot.sourceVersion == source.version
            ) add(ShellPageAction("page.sources.ask", uiText("Ask halo"), HaloGlyph.HALO) {
                val target = org.json.JSONObject().put("id", source.id).put("version", source.version)
                    .put("contentRevision", snapshot.contentRevision).put("articleId", savedArticle.id)
                val mini = com.halo.app.ui.mini.MiniHaloEntry
                mini.pendingArticle.value = articleDraftPrompt to com.halo.app.ui.mini.MiniHaloEntry.SavedArticle(savedArticle.title, target.toString())
                context.startActivity(mini.intent(context, "mini_halo", articleDraftPrompt))
            })
        }
        addAll(listOf(
        ShellPageAction("page.sources.add", uiText("Add feed"), HaloGlyph.INTEGRATIONS, !kids && !state.busy) { adding = true },
        ShellPageAction("page.sources.weather", uiText("Add weather city"), HaloGlyph.WATCH, !kids && !state.busy) { cityKind = CollectorKind.WEATHER.name },
        ShellPageAction("page.sources.air", uiText("Add air & pollen city"), HaloGlyph.LOCAL, !kids && !state.busy) { cityKind = CollectorKind.AIR_QUALITY.name },
        ShellPageAction("page.sources.settings", uiText("Source settings"), HaloGlyph.SETTINGS, current != null && !kids && !state.busy) { current?.source?.let { model.edit(it) } },
        ShellPageAction("page.sources.refresh", uiText("Refresh source"), HaloGlyph.ACTIVITY,
            current?.source?.let { it.selected && it.enabled && (!it.external || power) } == true && !kids && !state.busy) { current?.source?.let(model::refresh) },
        ShellPageAction("page.sources.stop", uiText("Cancel refresh"), HaloGlyph.STOP, current?.attempt?.state == CollectorAttemptState.FETCHING && !kids) { sourceId?.let(model::cancelRefresh) },
    ))
    })
    workspaceArticle?.takeUnless { kids }?.let { identity ->
        AlertDialog(onDismissRequest = { if (!sendingArticle) workspaceArticle = null },
            title = { Text(uiText("Send to workspace")) }, text = {
                Column {
                    if (articleHandoffFailed) Text(uiText("The article could not be saved. Reopen it and try again."),
                        color = MaterialTheme.colorScheme.error)
                    if (sendingArticle) LinearProgressIndicator(Modifier.fillMaxWidth())
                    WorkspaceNoteDestination.entries.forEach { destination ->
                        TextButton(onClick = { sendArticle(identity, destination) }, enabled = !sendingArticle) {
                            Text(uiText(destination.label))
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { workspaceArticle = null }, enabled = !sendingArticle) { Text(uiText("Cancel")) } })
    }
    if (addingWidget && canShow && !kids) com.halo.app.nativeactions.EverydayWidgetPicker { addingWidget = false }
    if (articleReading && savedArticle != null) com.halo.app.ui.zone6.Zone6ButtonSheet(
        uiText("Read aloud"), { articleReading = false }) {
        com.halo.app.ui.components.HaloReadAloud((fullArticle?.text ?: savedArticle.text).ifBlank { savedArticle.title })
    }
    if (LocalShellHaloOrb.current) BindShellUtilityBand(owner) {
        ToolZoneHeader(if (sourceId == null) heading else uiText("Saved source"), onBack)
    }
    fun external(url: String): Boolean {
        if (kids || Graph.kidsPreferences.enabled.value) return false
        if (Graph.privacyModes.mode.value != PrivacyMode.CLOUD) { externalError = "Opening a publisher website needs Power. The saved article remains available here."; return false }
        externalError = runCatching {
            val checked = CollectorUrlPolicy.validate(url)
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(checked.toString())))
        }.fold({ null }, { "This website could not be opened. The saved information is kept." })
        return externalError == null
    }
    if (showingKnowledge && canShow && !kids) OfflineKnowledgeSheet(power, { showingKnowledge = false }, ::external)
    if (showingMix && canShow && !kids) SourceMixSheet(state.sources, dismiss = { showingMix = false })
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (savedArticle == null) item {
            Zone5PageHeading(current?.source?.title ?: heading,
                if (current == null && !readsOnly) stringResource(R.string.sp13_sources_sub)
                else uiText("Saved information with clear sources and timestamps."))
            Text(uiText(if (power) "Power · Only selected and enabled sources may refresh." else "Local · No internet refresh. Saved external information may be out of date."), style = MaterialTheme.typography.bodySmall)
            if (!LocalShellHaloOrb.current) TextButton(onBack) { Text(uiText("Back")) }
        }
        if (sourceId == null && canShow && !kids) item("offline-knowledge") {
            OutlinedButton(onClick = { showingKnowledge = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(com.halo.app.R.string.knowledge_title))
            }
        }
        if (readsHome) item("reads-headlines") {
            val choices = rememberHaloBlockChoices("reads")
            val saved = headlines.map { (source, fact) ->
                HaloHomeBlock(source.id, com.halo.app.R.string.today_block_reads, fact.title,
                    fact.publisher.ifBlank { source.publisher }, com.halo.app.R.string.today_block_why_reads,
                    EverydayRoutes.source(source.id, fact.id), pictures = fact.gallery(picturesVisible, kids = false))
            }
            val spokenHeadlines = headlines.filter { (source, _) -> source.id !in choices.hidden }
                .joinToString("\n\n") { (source, fact) -> "${fact.publisher.ifBlank { source.publisher }}. ${fact.title}" }
            if (spokenHeadlines.isNotBlank()) HaloReadAloud(spokenHeadlines,
                label = stringResource(com.halo.app.R.string.today_read_headlines), category = com.halo.sdk.provider.RemoteDataCategory.TEXT)
            HaloHomeBlockDeck(saved, choices, onOpenReadBlock)
        }
        if (state.loading || state.busy || !canShow) item { com.halo.app.ui.zone6.Zone6DelayedLoading(true) }
        if (current?.attempt?.state == CollectorAttemptState.OFFLINE) item {
            com.halo.app.ui.zone6.Zone6OfflineLine(uiText("No internet · saved information stays available"))
        }
        state.error?.takeIf { !kids }?.let { item { EverydayMessage(it, true); TextButton(model::retry, enabled = !state.busy) { Text(uiText("Retry")) } } }
        state.notice?.takeIf { !kids }?.let { item { EverydayMessage(it) } }
        externalError?.let { item { EverydayMessage(it, true) } }
        if (sourceId != null && current == null && !state.loading && canShow) item { Text(uiText("This source is unavailable or is no longer shared with this experience.")) }
        if (current != null) {
            val source = current.source
            if (savedArticle == null) item {
                Text(source.publisher, style = MaterialTheme.typography.labelLarge)
                Text(uiText(sourceCategoryLabel(source.category)) + " · " + source.language)
                if (!source.selected) Text(uiText("Not selected · this source does not refresh."))
                else if (!source.enabled) Text(uiText("Refresh disabled · the saved snapshot is kept."))
                if (source.muted) Text(uiText("Muted in briefs. Selected sources can still refresh."))
                current.snapshot?.let { snapshot ->
                    Text(uiText(if (snapshot.stale(now, source.external, power)) "Cached · may be out of date" else "Saved snapshot is current"), style = MaterialTheme.typography.titleMedium)
                    Text(uiText("Retrieved %1\$s", time(snapshot.retrievedAt)), style = MaterialTheme.typography.bodySmall)
                    Text(uiText("Last verified %1\$s", time(snapshot.verifiedAt)), style = MaterialTheme.typography.bodySmall)
                }
                SourceStatus(current, stale = current.snapshot?.stale(now, source.external, power) == true)
                current.attempt.at?.let { Text(uiText("Latest attempt %1\$s", time(it)), style = MaterialTheme.typography.bodySmall) }
                current.attempt.nextAllowedAt?.takeIf { it > now }?.let { Text(uiText("Next eligible refresh %1\$s", time(it)), style = MaterialTheme.typography.bodySmall) }
                source.attributionUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (!kids) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ model.select(source, !source.selected) }, enabled = !state.busy) { Text(uiText(if (source.selected) "Deselect source" else "Select source")) }
                    TextButton({ model.setEnabled(source, !source.enabled) }, enabled = !state.busy) {
                        Text(stringResource(if (source.enabled) R.string.sp13_pause else R.string.sp13_resume))
                    }
                    TextButton({ clearing = source }, enabled = !state.busy && current.snapshot != null) { Text(uiText("Clear cached information")) }
                    TextButton({ removing = source }, enabled = !state.busy) { Text(uiText("Remove source")) }
                }
                if (itemId != null) TextButton({ onSource(source.id, null) }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Back to saved source")) }
                if (!kids && articleIdentity != null) TextButton(onClick = ::openHandoff, enabled = !sendingArticle,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Send to workspace")) }
            }
            collectorContent(current, itemId, now, locale, kids, { onSource(source.id, it) }, { external(it) }, { entry ->
                current.snapshot?.let { article = ArticleReminderReview(source.id, source.version, it.contentRevision, entry) }
            }, onLocal, picturesVisible)
        } else if (sourceId == null && canShow) {
            item {
                if (!kids && !readsOnly) Button({ adding = true }, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.sp13_add_source))
                }
                if (!kids && !readsOnly) TextButton({ showingMix = true }, Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.sp13_mix_title))
                }
                if (!readsOnly) Text(uiText("On your home screen"), style = MaterialTheme.typography.titleMedium)
                if (!kids) FilledTonalButton(onClick = { if (readsOnly) adding = true else addingWidget = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (readsOnly) uiText("Add feed") else addWidgetLabel) }
                if (!kids) EverydayToggle(uiText("Show article pictures"), picturesVisible, !state.busy) {
                    if (!Graph.kidsPreferences.enabled.value) Graph.articlePictureSettings.setVisible(it)
                }
                if (!readsOnly) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ onOrganizer(null) }, Modifier.heightIn(min = 48.dp)) { Text(uiText("Organizer")) }
                    TextButton({ onWidgetsDestination(EverydayRoutes.CLOCKS) }, Modifier.heightIn(min = 48.dp)) { Text(uiText("World clocks")) }
                    TextButton({ onWidgetsDestination(EverydayRoutes.BRIEF) }, Modifier.heightIn(min = 48.dp)) { Text(uiText("Your day")) }
                    if (!kids) TextButton({ cityKind = CollectorKind.AIR_QUALITY.name }, Modifier.heightIn(min = 48.dp),
                        enabled = !state.busy) { Text(uiText("Add air & pollen city")) }
                }

                HaloTextField(query, { query = it.take(200) }, VoiceFieldKind.SEARCH,
                    label = { Text(uiText("Find sources")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, maxChars = 200)
                FilterChip(selectedOnly, { selectedOnly = !selectedOnly }, modifier = Modifier.heightIn(min = 56.dp), label = { Text(uiText("Selected sources")) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(category == null, { category = null }, modifier = Modifier.heightIn(min = 56.dp), label = { Text(uiText("All categories")) })
                    SourceCategory.entries.forEach { value -> FilterChip(category == value.name, { category = value.name }, modifier = Modifier.heightIn(min = 56.dp), label = { Text(uiText(sourceCategoryLabel(value))) }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(language == null, { language = null }, modifier = Modifier.heightIn(min = 56.dp), label = { Text(uiText("All languages")) })
                    state.sources.filter { !readsOnly || it.source.kind == CollectorKind.FEED }
                        .map { it.source.language }.distinct().sorted().forEach { value ->
                        FilterChip(language == value, { language = value }, modifier = Modifier.heightIn(min = 56.dp), label = { Text(value) })
                    }
                }
            }
            val sources = state.sources.filter { row ->
                (!readsOnly || row.source.kind == CollectorKind.FEED) && (!selectedOnly || row.source.selected) &&
                    (category == null || row.source.category.name == category) &&
                    (language == null || row.source.language == language) && "${row.source.title} ${row.source.publisher}".contains(query, true)
            }
            if (sources.isEmpty() && !state.loading) item {
                if (state.sources.isEmpty() && state.error == null) com.halo.app.ui.zone6.Zone6RingEmptyState(
                    androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_empty_sources_t),
                    androidx.compose.ui.res.stringResource(com.halo.app.R.string.z6_empty_sources_p),
                    HaloGlyph.INTEGRATIONS,
                    if (kids || state.busy) emptyList() else listOf(
                        com.halo.app.ui.zone6.Zone6Start(uiText("Add feed"), HaloGlyph.INTEGRATIONS) { adding = true },
                        com.halo.app.ui.zone6.Zone6Start(uiText("Add weather city"), HaloGlyph.WATCH) { cityKind = CollectorKind.WEATHER.name },
                    ),
                ) else Text(uiText("No matching sources. Change the filters or add a feed."))
            }
            val grouped = sources.groupBy { if (kids) SourceGroup.KIDS_SAFE else SourceManager.group(it.source) }
            grouped.toSortedMap().forEach { (group, rows) ->
                if (!readsOnly) item("source-group:${group.name}") {
                    Text(stringResource(group.label), style = MaterialTheme.typography.titleMedium)
                }
                items(rows, key = { it.source.id }) { row ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(row.source.title, style = MaterialTheme.typography.titleMedium)
                        Text("${row.source.publisher} · ${row.source.language}", style = MaterialTheme.typography.bodySmall)
                        if (!readsOnly && picturesVisible && row.source.selected && row.source.enabled && !row.source.muted) {
                            val preview = (row.snapshot?.takeIf { it.sourceVersion == row.source.version }
                                ?.payload as? CollectorPayload.Articles)?.items
                                ?.filter { it.gallery(picturesVisible, kids).isNotEmpty() }
                                ?.maxByOrNull { it.publishedAt ?: Instant.MIN }
                            if (preview != null) ArticleSourcePreview(preview, picturesVisible, kids)
                        }
                        Text(uiText(sourceCategoryLabel(row.source.category)), style = MaterialTheme.typography.labelLarge)
                        if (!kids) EverydayToggle(uiText("Include in selected sources"), row.source.selected, !state.busy) { model.select(row.source, it) }
                        Text(uiText(if (row.snapshot == null) "No saved snapshot" else if (row.snapshot.stale(now, row.source.external, power)) "Cached · may be out of date" else "Saved snapshot is current"), style = MaterialTheme.typography.bodySmall)
                        SourceStatus(row, stale = row.snapshot?.stale(now, row.source.external, power) == true)
                        TextButton({ onSource(row.source.id, null) }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Open source")) }
                    }
                }
            }
            }
        }
    }
    if (!kids) state.draft?.let { SourceSettings(model, state, it) }
    if (adding && !kids) AddFeedSheet(model, state, power) { adding = false }
    val chosenCityKind = cityKind?.let(CollectorKind::valueOf)
    if (chosenCityKind != null && !kids) OfflineCityPicker(state.busy, onDismiss = { cityKind = null }, weather = true) { city ->
        val place = city.weatherPlace()
        val air = chosenCityKind == CollectorKind.AIR_QUALITY
        val prefix = if (air) "air" else "weather"
        val title = if (air) com.halo.app.language.resolveUiText(context.resources, "%s air & pollen", city.name) else city.name
        model.edit(CollectorSource("$prefix:${java.util.UUID.randomUUID()}", title, "Open-Meteo", chosenCityKind,
            SourceCategory.WEATHER, "und",
            if (air) OpenMeteoAirQuality.endpoint(place) else OpenMeteoParser.endpoint(place),
            if (air) OpenMeteoAirQuality.ATTRIBUTION_URL else "https://open-meteo.com/", selected = true, place = place), creating = true)
        cityKind = null
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
    var browsing by rememberSaveable { mutableStateOf(false) }
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
            Text(stringResource(R.string.sp13_add_source), style = MaterialTheme.typography.headlineSmall)
            com.halo.app.ui.theme.v13.Halo13Animation(com.halo.app.ui.theme.v13.Halo13Motion.ADD_SOURCE,
                Modifier.fillMaxWidth().height(76.dp))
            TextButton({ browsing = !browsing }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (browsing) R.string.sp13_add_link else R.string.sp13_add_browse))
            }
            if (browsing) {
                state.error?.let { EverydayMessage(it, true) }
                com.halo.app.everyday.collectors.StarterSourceCatalogue.external().forEach { starter ->
                    val registered = state.sources.firstOrNull { it.source.id == starter.id }?.source
                    EverydayToggle(starter.title, registered?.selected == true, !state.busy) { checked ->
                        if (registered != null) model.select(registered, checked)
                        else if (checked) model.addStarter(starter.id)
                    }
                }
                TextButton(::close, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(uiText("Done")) }
                return@Column
            }
            Text(uiText("A source can be saved in Local without fetching it. Preview checks real feed contents in Power."))
            state.error?.let { EverydayMessage(it, true) }
            OutlinedTextField(title, { title = it.take(200) }, label = { Text(uiText("Source name")) }, modifier = Modifier.fillMaxWidth())
            HaloTextField(url, { url = it.take(4096) }, VoiceFieldKind.URL_ID, maxChars = 4096,
                label = { Text(stringResource(com.halo.app.R.string.halo_source_public_url)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(language, { language = it.take(20) }, label = { Text(uiText("Source language · en, ru, lv or another code")) }, modifier = Modifier.fillMaxWidth())
            FlowRow {
                listOf(CollectorKind.FEED to "RSS or Atom feed", CollectorKind.ALERTS to "CAP alerts", CollectorKind.PUBLIC_CALENDAR to "Public iCalendar").forEach { (kind, label) ->
                    FilterChip(type == kind.name, { type = kind.name }, label = { Text(uiText(label)) })
                }
            }
            EverydayToggle(uiText("Include in selected sources"), selected, !state.busy) { selected = it }
            Button({ model.preview(url, CollectorKind.valueOf(type), language) }, enabled = power && !state.busy && url.isNotBlank()) { Text(uiText("Preview feed")) }
            if (!power) Text(uiText("Preview is unavailable in Local. You can save the address now and check it later in Power."), style = MaterialTheme.typography.bodySmall)
            val currentPreview = state.preview?.takeIf { it.matches(url, CollectorKind.valueOf(type), language) }
            currentPreview?.let { preview ->
                when (val result = preview.result) {
                    is CollectorFetchResult.Updated -> {
                        if (preview.discoveredFeed) {
                            Text(stringResource(R.string.sp13_link_found))
                            preview.candidate.endpoint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        Text(uiText("Preview received. It is not saved as a source snapshot."))
                        when (val payload = result.payload) {
                            is CollectorPayload.Articles -> payload.items.take(3).forEach { Text(it.title) }
                            is CollectorPayload.Calendar -> payload.preview.records.take(5).forEach { Text(it.title) }
                            is CollectorPayload.Warnings -> payload.items.take(5).forEach { Text(it.title) }
                            else -> Unit
                        }
                    }
                    is CollectorFetchResult.Unavailable -> EverydayMessage(result.reason, true)
                    CollectorFetchResult.NotModified -> EverydayMessage("No new preview was received.", true)
                }
            }
            Button({ model.addUrl(draftId, url, title, CollectorKind.valueOf(type), language, selected) },
                enabled = !state.busy && title.isNotBlank() && url.isNotBlank() && currentPreview?.websiteWithoutFeed != true,
                modifier = Modifier.fillMaxWidth()) { Text(uiText("Save source")) }
            TextButton(::close, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(uiText("Cancel")) }
        }
    }
    if (discard) HaloAlertDialog({ discard = false }, title = { Text(uiText("Discard this edit?")) },
        text = { Text(uiText("The source has not been added. The details in this form will be discarded.")) },
        confirmButton = { TextButton({ discard = false; dismiss() }) { Text(uiText("Discard")) } },
        dismissButton = { TextButton({ discard = false }) { Text(uiText("Keep editing")) } })
}

@Composable
private fun SourceMixSheet(sources: List<CollectorState>, dismiss: () -> Unit) {
    val selected = sources.map { it.source }.filter { it.selected && it.enabled && it.kind == CollectorKind.FEED }
    EverydaySheet(dismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.sp13_mix_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.sp13_mix_body))
            if (selected.isEmpty()) Text(uiText("No matching sources. Change the filters or add a feed."))
            for ((category, rows) in selected.groupBy { it.category }.toSortedMap()) {
                Text(uiText(sourceCategoryLabel(category)), style = MaterialTheme.typography.titleMedium)
                for ((language, group) in rows.groupBy { it.language }.toSortedMap()) {
                    val label = java.util.Locale.forLanguageTag(language).getDisplayLanguage(LocalConfiguration.current.locales[0])
                    Text("$label · ${group.size}")
                }
            }
            TextButton(dismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Done")) }
        }
    }
}

internal fun attemptLabel(state: CollectorAttemptState) = when (state) {
    CollectorAttemptState.NEVER -> "Not checked yet"; CollectorAttemptState.FETCHING -> "Refreshing"
    CollectorAttemptState.CURRENT -> "Last refresh succeeded"; CollectorAttemptState.NOT_MODIFIED -> "Source confirmed unchanged"
    CollectorAttemptState.LOCAL_ONLY -> "Refresh needs Power and permitted connected services"
    CollectorAttemptState.OFFLINE -> "No network connection · saved information is kept"; CollectorAttemptState.DISABLED -> "Refresh disabled"
    CollectorAttemptState.BACKOFF -> "Waiting before the next refresh"; CollectorAttemptState.QUOTA -> "Refresh budget reached"
    CollectorAttemptState.FAILED -> "Refresh failed · saved information is kept"; CollectorAttemptState.CANCELLED -> "Refresh cancelled"
}
