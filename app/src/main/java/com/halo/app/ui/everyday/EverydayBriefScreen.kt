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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.briefing.*
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.ui.zone5.*
import com.halo.app.ui.screens.notebook.ToolZoneHeader
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EverydayBriefScreen(model: EverydayBriefViewModel, onBack: () -> Unit, onSources: () -> Unit,
    onOrganizer: () -> Unit, onOpenFact: (BriefFact) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val kids by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val familyReady by Graph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
    val privacy by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val reading by Graph.readAloud.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val now by produceState(Instant.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (isActive) { value = Instant.now(); delay(60_000) } }
    }
    var history by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var study by rememberSaveable { mutableStateOf(false) }
    var attribution by rememberSaveable { mutableStateOf(false) }
    val power = privacy == com.halo.app.ai.PrivacyMode.CLOUD
    // Hide a previous adult frame synchronously with the live family state, before the IO projection catches up.
    val document = state.document?.takeIf { familyReady && (!kids || it.audience == BriefAudience.KIDS) }
    val loadingLabel = uiText("Loading")
    LaunchedEffect(kids) { if (kids) { settings = false; history = false } }
    val owner = ShellPageOwner("everyday_brief", sourceIdentity = "${document?.id}:$history:$settings:${state.busy}:$kids",
        helpDescription = uiText("Build a brief from your saved plan and selected sources. Local mode uses saved information; opening this page never refreshes the internet."),
        defaultDisabledActionReason = uiText("Wait for the current action to finish."), guidanceBlocked = state.loading || state.busy,
        statusLabel = uiText(if (power) "Power" else "Local"))
    BindShellPageActions(owner, listOf(
        ShellPageAction("page.brief.build", uiText("Update brief"), HaloGlyph.ACTIVITY, !state.busy) { model.build(BriefPeriod.ON_DEMAND, study) },
        ShellPageAction("page.brief.read", uiText("Read aloud"), HaloGlyph.SPEAK, document != null && !state.busy) { model.read() },
        ShellPageAction("page.brief.stop", uiText("Stop reading"), HaloGlyph.STOP, reading?.id?.startsWith("brief:") == true) { model.stopReading() },
        ShellPageAction("page.brief.history", uiText("Brief history"), HaloGlyph.ACTIVITY, !state.busy) { history = true },
        ShellPageAction("page.brief.settings", uiText("Brief settings"), HaloGlyph.SETTINGS, !kids && !state.busy) { settings = true },
        ShellPageAction("page.brief.sources", uiText("Sources"), HaloGlyph.INTEGRATIONS, !kids) { onSources() },
        ShellPageAction("page.brief.organizer", uiText("Organizer"), HaloGlyph.CALENDAR) { onOrganizer() },
    ))
    if (LocalShellHaloOrb.current) BindShellUtilityBand(owner) {
        ToolZoneHeader(uiText("Your day in Halo"), onBack)
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Zone5PageHeading(BriefPresentation.title(document?.period ?: BriefPeriod.ON_DEMAND, locale),
                uiText("Your plan, selected information and what needs your attention."))
            if (!LocalShellHaloOrb.current) TextButton(onBack, Modifier.heightIn(min = 56.dp)) { Text(uiText("Back")) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BriefPeriod.entries.toList().forEach { period ->
                    FilterChip(selected = document?.period == period, onClick = { model.build(period, study) }, enabled = !state.busy, modifier = Modifier.heightIn(min = 56.dp),
                        label = { Text(BriefPresentation.title(period, locale)) })
                }
            }
            if (!kids) FilterChip(study, { study = !study }, label = { Text(uiText("Study presentation")) })
        }
        if (state.loading || state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = loadingLabel }) }
        state.error?.let { item {
            Text(uiText(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            TextButton(model::retry, enabled = !state.busy, modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Retry")) }
        } }
        state.notice?.let { item { Text(uiText(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
        if (state.missing) item { Text(uiText("This saved brief is no longer available. Build a new brief from your current information.")) }
        document?.let { brief ->
            val zone = ZoneId.of(brief.zoneId)
            val stamps = brief.sources.associateBy { it.id }
            item {
                Text(uiText("Saved at %1\$s", DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(brief.generatedAt)),
                    style = MaterialTheme.typography.labelLarge)
                Text(uiText(if (power) "Selected sources may refresh in Power. This brief keeps its saved facts." else
                    "Local mode · External information is cached and may be out of date."), style = MaterialTheme.typography.bodySmall)
                BriefPresentation.comparisonNotice(brief, locale)?.let { Text(it, Modifier.padding(top = 8.dp)) }
            }
            val comparing = brief.period == BriefPeriod.CHANGES && !brief.baselineMissing
            val facts = if (comparing) brief.changes.mapNotNull { it.fact }.distinctBy { it.id } else brief.facts
            if (comparing && brief.changes.isEmpty()) item {
                Text(uiText(if (brief.comparisonUnavailableSources.isEmpty()) "Nothing changed since the saved morning comparison." else
                    "No changes found in the sources that could be compared. Some sources are unavailable."))
            } else if (!comparing && facts.isEmpty()) item {
                Text(uiText("No selected information is available for this brief."))
                TextButton(onOrganizer, Modifier.heightIn(min = 56.dp)) { Text(uiText("Open organizer")) }
                if (!kids) TextButton(onSources) { Text(uiText("Choose sources")) }
            }
            items(facts, key = { it.id }) { fact ->
                Surface(onClick = { onOpenFact(fact) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(uiText(briefSectionLabel(EverydayBriefEngine.section(fact))), style = MaterialTheme.typography.labelLarge)
                        if (comparing) brief.changes.find { it.factId == fact.id }?.let {
                            Text(uiText(if (it.kind == BriefChangeKind.ADDED) "Added" else "Updated"), style = MaterialTheme.typography.labelLarge)
                        }
                        Text(BriefPresentation.factText(fact, locale, zone), style = MaterialTheme.typography.titleMedium)
                        if (fact.detail.isNotBlank()) Text(fact.detail, style = MaterialTheme.typography.bodyMedium)
                        stamps[fact.sourceId]?.let { stamp ->
                            Text(BriefPresentation.sourceStatus(stamp, now, power, locale, zone), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(uiText("Open item"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            val removed = brief.changes.count { it.kind == BriefChangeKind.REMOVED }
            if (comparing && removed > 0) item { Text(uiText("%1\$d items are no longer in the current selected information.", removed)) }
            if (brief.omittedFacts > 0 || brief.omittedChanges > 0) item {
                Text(uiText("This brief shows a selection. Open the organizer or a source for the complete available list."), style = MaterialTheme.typography.bodySmall)
            }
            item {
                TextButton({ attribution = !attribution }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Freshness and sources")) }
                if (attribution) brief.sources.forEach { source ->
                    Text(source.title, style = MaterialTheme.typography.titleSmall)
                    Text(BriefPresentation.sourceStatus(source, now, power, locale, zone), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (history) EverydaySheet(onDismiss = { history = false }) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(uiText("Brief history"), style = MaterialTheme.typography.headlineSmall) }
            if (state.history.isEmpty()) item { Text(uiText("No saved briefs yet.")) }
            items(state.history.filter { !kids || it.audience == BriefAudience.KIDS }, key = { it.id }) { brief ->
                TextButton({ history = false; model.open(brief.id) }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("${BriefPresentation.title(brief.period, locale)} · ${brief.date}")
                }
            }
            item { Text(uiText("History is trimmed to 60 briefs and 30 days when a new brief is saved. The first morning comparison remains fixed for its day."), style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (settings && !kids) state.settings?.let { saved -> BriefSettingsSheet(saved, state.records, state.busy, state.error, state.notice,
        dismiss = { settings = false }, save = model::saveSettings, reload = model::reloadSettings) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BriefSettingsSheet(saved: BriefSettings, records: List<com.halo.app.everyday.organizer.OrganizerRecord>, busy: Boolean,
    error: String?, notice: String?,
    dismiss: () -> Unit, save: (BriefSettings) -> Unit, reload: () -> Unit) {
    var proactive by rememberSaveable(saved.version) { mutableStateOf(saved.proactive) }
    var morning by rememberSaveable(saved.version) { mutableStateOf(saved.morning.toString()) }
    var evening by rememberSaveable(saved.version) { mutableStateOf(saved.evening.toString()) }
    var quiet by rememberSaveable(saved.version) { mutableStateOf(saved.quietEnabled) }
    var start by rememberSaveable(saved.version) { mutableStateOf(saved.quietStart.toString()) }
    var end by rememberSaveable(saved.version) { mutableStateOf(saved.quietEnd.toString()) }
    var headlines by rememberSaveable(saved.version) { mutableStateOf(saved.headlineLimit.toString()) }
    var tasks by rememberSaveable(saved.version) { mutableStateOf(saved.taskLimit.toString()) }
    var pinned by rememberSaveable(saved.version) { mutableStateOf(ArrayList(saved.pinnedRecordIds)) }
    var sections by rememberSaveable(saved.version) { mutableStateOf(ArrayList(saved.sections.map { it.name })) }
    var shared by rememberSaveable(saved.version) { mutableStateOf(ArrayList(saved.childRecordIds)) }
    var query by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    EverydaySheet(onDismiss = { if (!busy) discard = true }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Brief settings"), style = MaterialTheme.typography.headlineSmall)
            Text(uiText("Brief sections"), style = MaterialTheme.typography.titleMedium)
            BriefSection.entries.forEach { section ->
                EverydayToggle(uiText(briefSectionLabel(section)), section.name in sections, !busy) { selected ->
                    sections = ArrayList(if (selected) sections + section.name else sections - section.name)
                }
            }
            error?.let {
                Text(uiText(it), color = MaterialTheme.colorScheme.error)
                TextButton(reload, enabled = !busy) { Text(uiText("Discard edits and reload saved settings")) }
            }
            notice?.let { Text(uiText(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            EverydayToggle(uiText("Morning and evening notices"), proactive, !busy) { proactive = it }
            Text(uiText("Android may deliver notices late. Quiet hours and system Do Not Disturb still apply. Notices never read your brief aloud."), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(morning, { morning = it.take(5) }, label = { Text(uiText("Morning · HH:mm")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(evening, { evening = it.take(5) }, label = { Text(uiText("Evening · HH:mm")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            EverydayToggle(uiText("Quiet hours"), quiet, !busy) { quiet = it }
            OutlinedTextField(start, { start = it.take(5) }, label = { Text(uiText("Quiet from · HH:mm")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(end, { end = it.take(5) }, label = { Text(uiText("Quiet until · HH:mm")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(uiText("Matching quiet-hour times mean quiet all day."), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(headlines, { headlines = it.take(2) }, label = { Text(uiText("Headlines · 0–10")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tasks, { tasks = it.take(2) }, label = { Text(uiText("Tasks · 1–20")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(uiText("Priorities and Kids sharing"), style = MaterialTheme.typography.titleMedium)
            Text(uiText("Only the items you share here may appear in Kids briefs. Sharing a source alone does not share your private plan."), style = MaterialTheme.typography.bodySmall)
            val chosenIds = (pinned + shared).toSet()
            val availableIds = records.map { it.id }.toSet()
            (chosenIds - availableIds).forEach { id ->
                Text(uiText("Unavailable saved item · %1\$s", id), style = MaterialTheme.typography.titleSmall)
                if (id in pinned) EverydayToggle(uiText("Prioritize in brief"), true, !busy, description = "${uiText("Prioritize in brief")} · $id") { pinned = ArrayList(pinned - id) }
                if (id in shared) EverydayToggle(uiText("Share with Kids"), true, !busy, description = "${uiText("Share with Kids")} · $id") { shared = ArrayList(shared - id) }
            }
            TextButton({ shared = arrayListOf() }, enabled = !busy && shared.isNotEmpty()) { Text(uiText("Stop sharing all organizer items")) }
            OutlinedTextField(query, { query = it.take(200) }, label = { Text(uiText("Find an organizer item")) }, modifier = Modifier.fillMaxWidth())
            // Existing grants remain reachable even when the search or creation order changes.
            val chosen = records.filter { it.id in chosenIds }
            val candidates = records.filter { it.id !in chosenIds && it.title.contains(query, true) }
            (chosen + candidates.take(100)).forEach { record ->
                Text(record.title, style = MaterialTheme.typography.titleSmall)
                EverydayToggle(uiText("Prioritize in brief"), record.id in pinned, !busy && (pinned.size < 100 || record.id in pinned), description = "${uiText("Prioritize in brief")} · ${record.title}") { pinned = ArrayList(if (it) pinned + record.id else pinned - record.id) }
                EverydayToggle(uiText("Share with Kids"), record.id in shared, !busy && (shared.size < 100 || record.id in shared), description = "${uiText("Share with Kids")} · ${record.title}") { shared = ArrayList(if (it) shared + record.id else shared - record.id) }
            }
            if (candidates.size > 100) Text(uiText("Showing the first 100 matching items. Refine your search to find more."), style = MaterialTheme.typography.bodySmall)
            if (invalid) Text(uiText("Use 24-hour HH:mm times and numbers within the displayed limits."), color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                val next = runCatching { saved.copy(proactive = proactive, morning = LocalTime.parse(morning), evening = LocalTime.parse(evening),
                    quietEnabled = quiet, quietStart = LocalTime.parse(start), quietEnd = LocalTime.parse(end),
                    headlineLimit = headlines.toInt(), taskLimit = tasks.toInt(), pinnedRecordIds = pinned.toSet(), childRecordIds = shared.toSet(), sections = sections.map(BriefSection::valueOf).toSet()) }.getOrNull()
                invalid = next == null
                if (next != null) save(next)
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(uiText("Save")) }
            TextButton({ discard = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(uiText("Cancel")) }
        }
    }
    if (discard) com.halo.app.ui.components.HaloAlertDialog({ discard = false }, title = { Text(uiText("Discard this edit?")) },
        text = { Text(uiText("Your saved item is kept. The changes in this form will be discarded.")) },
        confirmButton = { TextButton({ discard = false; dismiss() }) { Text(uiText("Discard")) } },
        dismissButton = { TextButton({ discard = false }) { Text(uiText("Keep editing")) } })
}

internal fun briefSectionLabel(section: BriefSection): String = when (section) {
    BriefSection.WEATHER -> "Weather"; BriefSection.EVENTS -> "Events"; BriefSection.TASKS -> "Tasks"
    BriefSection.HALO_WORK -> "Halo and shared work"; BriefSection.UNFINISHED -> "Unfinished work"
    BriefSection.NEWS -> "News"; BriefSection.RESEARCH -> "Research / Web"; BriefSection.REMINDERS -> "Reminders"
    BriefSection.ALARMS_ROUTINES -> "Alarms and routines"; BriefSection.WORKSPACE -> "Workspace context"; BriefSection.DEVICE -> "Device status"
}
