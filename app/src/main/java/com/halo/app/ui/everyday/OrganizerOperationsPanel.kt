package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.organizer.*
import com.halo.app.everyday.organizer.android.OrganizerAndroidClock
import com.halo.app.language.uiText
import java.time.Instant
import java.time.ZoneId

internal fun responsibilityLabel(owner: CommitmentOwner) = when (owner) {
    CommitmentOwner.ME -> "You"; CommitmentOwner.HALO -> "Halo"; CommitmentOwner.SHARED -> "Shared"
}
internal fun workStateLabel(state: HaloWorkState) = when (state) {
    HaloWorkState.DRAFT -> "Draft"; HaloWorkState.READY -> "Ready"; HaloWorkState.RUNNING -> "Running"
    HaloWorkState.WAITING -> "Waiting"; HaloWorkState.NEEDS_USER -> "Needs your decision"
    HaloWorkState.COMPLETED -> "Completed"; HaloWorkState.FAILED -> "Failed"
}
internal fun commitmentPurposeLabel(purpose: CommitmentPurpose) = when (purpose) {
    CommitmentPurpose.STANDARD -> "Task"; CommitmentPurpose.INBOX -> "Inbox"; CommitmentPurpose.ROUTINE -> "Routine"
    CommitmentPurpose.WATCH -> "Watch / monitor"; CommitmentPurpose.STOPWATCH -> "Stopwatch"
}
internal fun organizerSectionLabel(section: OrganizerSection) = when (section) {
    OrganizerSection.TODAY -> "Today"; OrganizerSection.PLANNER -> "Planner"; OrganizerSection.TIME -> "Time"
    OrganizerSection.ROUTINES -> "Routines"; OrganizerSection.INBOX -> "Inbox"; OrganizerSection.RESEARCH -> "Search / Research"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrganizerSectionContent(section: OrganizerSection, state: OrganizerPageState, model: OrganizerViewModel,
    now: Instant, kids: Boolean, onCreate: (CommitmentPurpose) -> Unit, onClocks: () -> Unit, onOpenRelated: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!kids) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (section) {
                OrganizerSection.INBOX -> TextButton({ onCreate(CommitmentPurpose.INBOX) }, enabled = !state.busy) { Text(uiText("Capture to Inbox")) }
                OrganizerSection.TIME -> {
                    TextButton({ model.new(OrganizerKind.ALARM) }, enabled = !state.busy) { Text(uiText("Add alarm")) }
                    TextButton({ model.new(OrganizerKind.TIMER) }, enabled = !state.busy) { Text(uiText("Add timer")) }
                    TextButton({ onCreate(CommitmentPurpose.STOPWATCH) }, enabled = !state.busy) { Text(uiText("Add stopwatch")) }
                    TextButton(onClocks) { Text(uiText("World clocks")) }
                }
                OrganizerSection.ROUTINES -> {
                    TextButton({ onCreate(CommitmentPurpose.ROUTINE) }, enabled = !state.busy) { Text(uiText("Add routine")) }
                    TextButton({ onCreate(CommitmentPurpose.WATCH) }, enabled = !state.busy) { Text(uiText("Watch / monitor")) }
                    TextButton({ onOpenRelated("automation") }) { Text(uiText("Open saved automations")) }
                }
                OrganizerSection.TODAY, OrganizerSection.PLANNER -> TextButton({ onCreate(CommitmentPurpose.STANDARD) }, enabled = !state.busy) { Text(uiText("Add commitment")) }
                OrganizerSection.RESEARCH -> Unit
            }
        }
        if (section == OrganizerSection.RESEARCH) {
            if (!kids) OrganizerResearchPanel(model, state, onOpenRelated) else Text(uiText("A parent manages the saved plan."))
        } else {
            if (section == OrganizerSection.TODAY) OrganizerTodayInformation(now, kids, onOpenRelated)
            val records = OrganizerSections.records(section, state.records, now, ZoneId.systemDefault(), state.todayOccurrenceIds, state.outstandingIds)
            val grouped = OrganizerSections.responsibility(records)
            CommitmentOwner.entries.forEach { owner ->
                val rows = grouped[owner].orEmpty()
                if (rows.isNotEmpty() || section == OrganizerSection.TODAY) {
                    Text(uiText(responsibilityLabel(owner)), style = MaterialTheme.typography.titleLarge)
                    if (rows.isEmpty()) Text(uiText("Nothing requiring attention here."))
                    rows.take(60).forEach { record ->
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(record.title, style = MaterialTheme.typography.titleMedium)
                                Text(uiText(responsibilityLabel(record.commitment.owner)) + " · " + uiText(workStateLabel(OrganizerResponsibility.state(record))))
                                record.time?.let { Text(it.start.toString() + " · " + it.zoneId) }
                                if (!kids && record.commitment.state == HaloWorkState.NEEDS_USER) Text(uiText("Open this item to review the result and your next step."))
                                TextButton({ model.open(record.id) }, Modifier.heightIn(min = 48.dp)) { Text(uiText("Open item")) }
                            }
                        }
                    }
                    if (rows.size > 60) Text(uiText("Showing the first 60 matching items. Refine your search to find more."))
                }
            }
            if (records.isEmpty() && section != OrganizerSection.TODAY) Text(uiText("No commitments in this section yet."))
        }
    }
}

@Composable
private fun OrganizerTodayInformation(now: Instant, kids: Boolean, onOpen: (String) -> Unit) {
    val sources by remember { Graph.collectorRepository.observe() }.collectAsStateWithLifecycle(emptyList())
    val brief by remember { Graph.briefService.observeLatestVisible() }.collectAsStateWithLifecycle(null)
    val mode by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val allowed = sources.filter { it.source.selected && it.source.enabled && !it.source.muted && (!kids || SourcesViewModel.childSource(it.source)) }
    allowed.filter { it.source.kind == com.halo.app.everyday.collectors.CollectorKind.WEATHER }.take(2).forEach { state ->
        val snapshot = state.snapshot?.takeIf { it.sourceVersion == state.source.version }
        val weather = (snapshot?.payload as? com.halo.app.everyday.collectors.CollectorPayload.Weather)?.fact
        if (snapshot != null && weather != null) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(weather.place.name, style = MaterialTheme.typography.titleMedium)
                    weather.temperatureC?.let { Text(java.text.NumberFormat.getNumberInstance(locale).format(it) + " °C") }
                    Text(uiText(weatherLabel(weather.weatherCode)))
                    Text(state.source.publisher, style = MaterialTheme.typography.bodySmall)
                    Text(uiText("Retrieved %1\$s", snapshot.retrievedAt.toString()), style = MaterialTheme.typography.bodySmall)
                    if (snapshot.stale(now, state.source.external, mode == com.halo.app.ai.PrivacyMode.CLOUD)) Text(uiText("Cached / stale"))
                    TextButton({ onOpen(EverydayRoutes.source(state.source.id)) }) { Text(uiText("Open source")) }
                }
            }
        }
    }
    val visible = brief?.takeIf { !kids || it.audience == com.halo.app.everyday.briefing.BriefAudience.KIDS }
    Text(uiText("Your day in Halo"), style = MaterialTheme.typography.titleMedium)
    visible?.let { document ->
        document.facts.filter { fact -> fact.sourceId == "halo:attention" || allowed.any { source -> source.source.id == fact.sourceId && document.sources.any { stamp -> stamp.id == source.source.id && stamp.version == source.source.version } } }
            .take(3).forEach { Text(com.halo.app.everyday.briefing.BriefPresentation.factText(it, locale, ZoneId.of(document.zoneId))) }
    }
    TextButton({ onOpen(EverydayRoutes.brief(visible?.id)) }) { Text(uiText("Open daily brief")) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrganizerWorkDetails(record: OrganizerRecord, model: OrganizerViewModel, busy: Boolean,
    now: Instant, resultAllowed: Boolean, onEdit: () -> Unit, onPromote: () -> Unit, onOpenRelated: (String) -> Unit) {
    val work = record.commitment
    val sourceStates by remember { Graph.collectorRepository.observe() }.collectAsStateWithLifecycle(emptyList())
    val reviewable = runCatching {
        Graph.organizerWorkCapabilities.validate(work.actions)
        work.actions.forEach { action ->
            val args = Graph.organizerWorkCapabilities.arguments(action)
            (args["sourceId"] as? String)?.let { id -> require(sourceStates.any { it.source.id == id && it.source.version == (args["sourceVersion"] as Number).toLong() && it.source.selected && it.source.enabled && !it.source.muted }) }
        }
    }.isSuccess
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText(responsibilityLabel(work.owner)) + " · " + uiText(workStateLabel(OrganizerResponsibility.state(record))), style = MaterialTheme.typography.titleMedium)
        if (work.intent.isNotBlank()) Text(work.intent)
        if (work.stopwatch != null) {
            val elapsed = work.stopwatch.elapsed(now, OrganizerAndroidClock.elapsedMs(), OrganizerAndroidClock.bootId(androidx.compose.ui.platform.LocalContext.current))
            Text(stopwatchText(elapsed), style = MaterialTheme.typography.displaySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val running = work.stopwatch.startedAt != null
                TextButton({ model.commitmentAction(record, if (running) "PAUSE" else "START") }, enabled = !busy) { Text(uiText(if (running) "Pause" else "Start")) }
                TextButton({ model.commitmentAction(record, "LAP") }, enabled = !busy && running && work.stopwatch.laps.size < 50) { Text(uiText("Lap")) }
                TextButton({ model.commitmentAction(record, "RESET") }, enabled = !busy && !running) { Text(uiText("Reset")) }
            }
            work.stopwatch.laps.forEachIndexed { index, lap -> Text(uiText("Lap %1\$d · %2\$s", index + 1, stopwatchText(lap))) }
        } else if (record.kind == OrganizerKind.TASK || work.owner != CommitmentOwner.ME) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (work.owner != CommitmentOwner.HALO) TextButton({ model.commitmentAction(record, "DELEGATE") }, enabled = !busy) { Text(uiText("Delegate to Halo")) }
                if (work.owner != CommitmentOwner.ME) TextButton({ model.commitmentAction(record, "TAKE_OVER") }, enabled = !busy) { Text(uiText("Take over")) }
                if (work.owner != CommitmentOwner.SHARED) TextButton({ model.commitmentAction(record, "MAKE_SHARED") }, enabled = !busy) { Text(uiText("Make shared")) }
                if (record.kind == OrganizerKind.TASK && work.purpose !in setOf(CommitmentPurpose.WATCH, CommitmentPurpose.INBOX)) TextButton({ model.commitmentAction(record, "WATCH") }, enabled = !busy) { Text(uiText("Watch / monitor")) }
                if (work.purpose == CommitmentPurpose.INBOX) TextButton(onPromote, enabled = !busy && work.execution == null) { Text(uiText("Move to Planner")) }
                TextButton(onEdit, enabled = !busy && work.execution == null) { Text(uiText("Edit responsibility and plan")) }
            }
            if (work.owner != CommitmentOwner.ME) {
                Text(uiText("Reviewed work schedules support one date or daily/weekly recurrence with an explicit time zone. Other schedules need revision before enabling."))
                Text(uiText("A saved plan is not running work. Execution and delivery are shown from their own records."))
                work.actions.forEach { action ->
                    Text(uiText(capabilityLabel(action.capability)), style = MaterialTheme.typography.titleSmall)
                    val args = runCatching { com.halo.app.automation.RoutineArguments.decode(action.argumentsJson) }.getOrDefault(emptyMap())
                    (args["query"] as? String)?.let { Text(it) }
                    (args["period"] as? String)?.let { period -> runCatching { com.halo.app.everyday.briefing.BriefPeriod.valueOf(period) }.getOrNull()?.let {
                        Text(com.halo.app.everyday.briefing.BriefPresentation.title(it, androidx.compose.ui.platform.LocalConfiguration.current.locales[0]))
                    } }
                    (args["sourceId"] as? String)?.let { id ->
                        val selected = sourceStates.find { it.source.id == id }
                        Text(selected?.source?.title ?: uiText("Source unavailable"))
                        selected?.snapshot?.retrievedAt?.let { Text(uiText("Retrieved %1\$s", it.toString())) }
                        Text(uiText(if (args["refresh"] == true) "A permitted Power refresh is part of this action." else "This action uses retained source observations."))
                    }
                    if (args["predicate"] == "CHANGED") Text(uiText("Compare against the selected saved source observation."))
                    if (args["predicate"] == "CONTAINS") { Text(uiText("Text to watch for")); Text(args["value"] as? String ?: "") }
                    (args["condition"] as? String)?.let { condition ->
                        Text(uiText(when (condition) { "BATTERY_BELOW" -> "Battery below threshold"; "NETWORK_AVAILABLE" -> "Network available"; else -> "Charging" }))
                        (args["percent"] as? Number)?.let { Text("${it.toInt()}%") }
                    }
                }
                if (!reviewable && work.actions.isNotEmpty()) Text(uiText("Some required capabilities or sources changed. Edit the plan before reviewing it."))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (work.execution == null) TextButton({ model.commitmentAction(record, "PREPARE") }, enabled = !busy && reviewable && !work.recoveryNeedsReview) { Text(uiText("Review work plan")) }
                    if (work.execution != null && work.state in setOf(HaloWorkState.READY, HaloWorkState.WAITING)) {
                        TextButton({ model.commitmentAction(record, "RUN_NOW") }, enabled = !busy) { Text(uiText("Run reviewed plan now")) }
                        TextButton({ model.commitmentAction(record, "ARM") }, enabled = !busy && record.time != null && work.state in setOf(HaloWorkState.READY, HaloWorkState.WAITING) && work.execution.scheduling !in setOf("SCHEDULED", "EVENT_DRIVEN")) { Text(uiText("Enable reviewed schedule")) }
                    }
                    if (work.execution != null) TextButton({ model.commitmentAction(record, "STOP") }, enabled = !busy) { Text(uiText("Stop Halo work")) }
                    if (work.owner == CommitmentOwner.SHARED && work.state == HaloWorkState.NEEDS_USER && work.result?.conditionMatched != false && work.result?.newMatch != false && work.result?.waiting == false && resultAllowed)
                        TextButton({ model.commitmentAction(record, "ACKNOWLEDGE") }, enabled = !busy) { Text(uiText("I completed my part")) }
                }
                work.execution?.let { binding ->
                    Text(uiText(when (binding.scheduling) { "SCHEDULED", "EVENT_DRIVEN" -> "A background schedule request is recorded. Android may delay execution."
                        "BLOCKED" -> "The schedule is blocked. Review its permissions and definition."; else -> "No confirmed active schedule is recorded." }))
                    binding.runId?.let { id -> TextButton({ onOpenRelated("activity?runId=${android.net.Uri.encode(id)}") }) { Text(uiText("Open execution record")) } }
                }
            }
        }
        work.recovery?.let { recovery ->
            Text(uiText("Previous execution"), style = MaterialTheme.typography.titleMedium)
            Text(recovery.recordedAt.toString())
            if (work.recoveryNeedsReview) Text(uiText("Inspect the previous execution and acknowledge its uncertain effects before continuing."))
            recovery.runId?.let { id -> TextButton({ onOpenRelated("activity?runId=${android.net.Uri.encode(id)}") }) { Text(uiText("Open previous execution record")) } }
            if (work.recoveryNeedsReview && work.execution == null) TextButton({ model.commitmentAction(record, "ACKNOWLEDGE_RECOVERY") }, enabled = !busy) { Text(uiText("I reviewed the previous execution")) }
        }
        work.result?.let { result ->
            val sourceAccess = (work.sources + result.sourceRefs).all { ref -> sourceStates.any { it.source.id == ref.id && it.source.version == ref.version && it.source.selected && it.source.enabled && !it.source.muted } }
            if (resultAllowed && sourceAccess) {
                Text(uiText("Recorded result"), style = MaterialTheme.typography.titleMedium)
                result.messages.forEach { Text(uiText(it)) }; if (result.summary.isNotBlank()) Text(result.summary); Text(result.confirmedAt.toString())
            } else Text(uiText("This saved result is withheld because its source access changed. Review sources before reading it."))
        }
        work.nextHumanAction?.let { Text(uiText("Next step"), style = MaterialTheme.typography.titleMedium); Text(uiText(it)) }
    }
}

internal fun capabilityLabel(id: String) = when (id) {
    "summarize" -> "Build a selected brief"; "web.search" -> "Research selected evidence"
    "monitor" -> "Monitor a selected source"; "system.monitor" -> "Monitor a device condition"; else -> "Unavailable capability"
}
private fun stopwatchText(ms: Long) = java.lang.String.format(java.util.Locale.ROOT, "%02d:%02d:%02d.%02d", ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, ms / 10 % 100)
