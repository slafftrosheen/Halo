package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.workspace.*
import com.halo.runtime.cognition.routine.*
import com.halo.runtime.cognition.watch.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AutomationTab(val title: String) { ROUTINES("Routines"), FOLLOW_UPS("Follow-ups"), HISTORY("History") }

data class AutomationReadiness(
    val labels: AutomationLabels = AutomationLabels(),
    val speakerDevices: Set<String> = emptySet(),
    val connectedDevices: Set<String> = emptySet(),
    val sourceStatus: Map<String, AutomationSourceStatus> = emptyMap(),
    val notificationsEnabled: Boolean = false,
) {
    fun triggerIssue(trigger: RoutineTrigger): String? {
        val missingTarget = when (trigger) {
            is RoutineTrigger.DeviceConnected -> trigger.deviceId !in labels.devices
            is RoutineTrigger.DeviceDisconnected -> trigger.deviceId !in labels.devices
            is RoutineTrigger.AppStarted -> trigger.appPackage !in labels.apps
            is RoutineTrigger.NotificationFromApp -> trigger.packageName !in labels.apps
            else -> false
        }
        if (missingTarget) return when (trigger) {
            is RoutineTrigger.DeviceConnected, is RoutineTrigger.DeviceDisconnected -> "Choose an available saved device"
            else -> "Choose an installed app"
        }
        val key = when (trigger) {
            is RoutineTrigger.AppStarted -> "foreground"
            is RoutineTrigger.NotificationFromApp -> "notifications"
            is RoutineTrigger.DeviceConnected, is RoutineTrigger.DeviceDisconnected -> "device"
            RoutineTrigger.NetworkAvailable, RoutineTrigger.NetworkLost -> "network"
            is RoutineTrigger.AtTime, is RoutineTrigger.Once -> return null
            else -> "battery_charging"
        }
        return when (sourceStatus[key]) {
            AutomationSourceStatus.REQUIRES_SERVICE -> "Phone screen access required"
            AutomationSourceStatus.REQUIRES_PERMISSION -> "Notification access required"
            AutomationSourceStatus.UNAVAILABLE -> "Trigger source unavailable"
            null -> "Checking source status"
            else -> null
        }
    }
    fun deliveryIssue(delivery: RoutineDeliveryPolicy): String? = when (delivery) {
        RoutineDeliveryPolicy.AndroidNotification -> if (notificationsEnabled) null else "Allow Halo notifications"
        is RoutineDeliveryPolicy.WearableSpeech -> when {
            delivery.deviceId !in labels.devices -> "Wearable unavailable"
            delivery.deviceId !in speakerDevices -> "Wearable has no speech route capability"
            delivery.deviceId !in connectedDevices -> "Wearable currently unavailable"
            else -> null
        }
        else -> null
    }
    fun issue(routine: Routine) = triggerIssue(routine.trigger)
        ?: if (routine.actions.any { it.toolId == "halo_notification_digest" } && sourceStatus["notifications"] != AutomationSourceStatus.AVAILABLE) "Notification access required" else deliveryIssue(routine.delivery)
}

@Composable
private fun rememberAutomationReadiness(routines: List<Routine>, watches: List<ContinuationWatch>): AutomationReadiness {
    val context = LocalContext.current
    val graph = ManualDependencyGraph
    var labels by remember { mutableStateOf(AutomationLabels()) }
    var speakers by remember { mutableStateOf(emptySet<String>()) }
    var notificationReady by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME)
                notificationReady = context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val presence by graph.devicePresenceRepository.presenceMap.collectAsStateWithLifecycle()
    val sources = remember { graph.automationRuntime.registry.sources }
    val statusFlow = remember { combine(sources.values.map { it.availability }) { values -> sources.keys.zip(values.toList()).toMap() } }
    val statuses by statusFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    LaunchedEffect(routines, watches) {
        val snapshot = withContext(Dispatchers.IO) {
            val records = graph.deviceRegistry.getSavedDevices()
            val packages = buildSet {
                routines.forEach { when (val t = it.trigger) {
                    is RoutineTrigger.AppStarted -> add(t.appPackage)
                    is RoutineTrigger.NotificationFromApp -> add(t.packageName)
                    else -> Unit
                } }
                watches.forEach { when (val c = it.condition) {
                    is WatchCondition.AppForegrounded -> add(c.packageName)
                    is WatchCondition.NotificationFromApp -> add(c.packageName)
                    else -> Unit
                } }
            }
            val appLabels = packages.mapNotNull { pkg -> runCatching {
                pkg to context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrNull() }.toMap()
            AutomationLabels(records.associate { it.identity.deviceId to it.identity.displayName }, appLabels,
                graph.aiProviderRouter.getAllProviders().associate { it.id to it.displayName }) to
                records.filter { it.descriptor.capabilities.hasSpeaker }.map { it.identity.deviceId }.toSet()
        }
        labels = snapshot.first
        speakers = snapshot.second
        notificationReady = context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()
    }
    return AutomationReadiness(labels, speakers,
        presence.filterValues { it.state in setOf(com.halo.runtime.cognition.device.DevicePresenceState.CONNECTED, com.halo.runtime.cognition.device.DevicePresenceState.READY) }.keys,
        statuses, notificationReady)
}

@Composable
fun AutomationScreen(viewModel: RoutinesViewModel, onBack: () -> Unit, onNotifications: () -> Unit,
    focusRoutineId: String? = null, reviewRoutineId: String? = null, onReviewedSave: () -> Unit = {},
    onActivity: () -> Unit = {}, onInbox: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val watches by ManualDependencyGraph.followUpRepository.observeRecentWatches().collectAsStateWithLifecycle(initialValue = emptyList())
    val readiness = rememberAutomationReadiness(state.routines, watches)
    var tab by remember { mutableStateOf(AutomationTab.ROUTINES) }
    var draft by remember { mutableStateOf<RoutineDraft?>(null) }
    var detailId by remember { mutableStateOf<RoutineId?>(null) }
    LaunchedEffect(focusRoutineId) { detailId = focusRoutineId?.let(::RoutineId) }
    var selectedRun by remember { mutableStateOf<String?>(null) }
    var selectedWatch by remember { mutableStateOf<ContinuationWatchId?>(null) }
    var creating by remember { mutableStateOf(false) }
    var reviewOpened by remember(reviewRoutineId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reviewRoutineId, state.loading) {
        if (!reviewOpened && reviewRoutineId != null && !state.loading) {
            state.routines.firstOrNull { it.id.value == reviewRoutineId }?.let { draft = RoutineDraft(it, it.id); reviewOpened = true }
        }
    }
    draft?.let { current ->
        EverydayRoutineEditor(current, state.error, viewModel.actionDescriptors, readiness,
            onDismiss = { draft = null }, review = viewModel::review,
            onSave = { routine -> viewModel.save(routine) { draft = null; detailId = routine.id; onReviewedSave() } },
            onNotifications = onNotifications, onInbox = onInbox)
        return
    }
    val detail = state.routines.firstOrNull { it.id == detailId }
    if (detail != null) {
        RoutineDetail(detail, state.recentRuns.firstOrNull { it.routineId == detail.id }, detail.id in running,
            readiness, state.error, detail.id.value in state.blockedRoutines, onBack = { detailId = null }, onEdit = { draft = RoutineDraft(detail, detail.id) },
            onDuplicate = { draft = RoutineDraft.duplicate(detail) }, onDelete = { viewModel.delete(detail.id); detailId = null },
            onRun = { viewModel.runNow(detail.id) }, onEnable = { viewModel.enable(detail.id, it) }, onActivity = onActivity,
            onNotifications = onNotifications, onInbox = onInbox)
        return
    }
    state.recentRuns.firstOrNull { it.id == selectedRun }?.let { run ->
        AutomationPage("Automation run", { selectedRun = null }) {
            Text(state.routines.firstOrNull { it.id == run.routineId }?.name ?: "Removed automation", style = MaterialTheme.typography.titleLarge)
            RunEvidence(run, onActivity)
        }
        return
    }
    watches.firstOrNull { it.id == selectedWatch }?.let { watch ->
        var cancel by remember { mutableStateOf(false) }
        AutomationPage("Follow-up", { selectedWatch = null }) {
            Text(watch.presentationText ?: watch.condition.humanLabel(readiness.labels), style = MaterialTheme.typography.titleLarge)
            DetailField("Condition", watch.condition.humanLabel(readiness.labels))
            DetailField("Status", watch.status.humanLabel())
            DetailField("Created", humanTime(watch.createdAt))
            DetailField("Expires", watch.expiresAt?.let { humanTime(it) } ?: "No expiry")
            DetailField("Delivery behavior", watch.behavior.humanLabel())
            if (watch.linkedRunId() != null) TextButton(onClick = onActivity) { Text("Open in Activity") }
            if (watch.status == WatchStatus.ACTIVE) OutlinedButton(onClick = { cancel = true }) { Text("Cancel follow-up") }
        }
        if (cancel) AlertDialog(onDismissRequest = { cancel = false }, title = { Text("Stop waiting for this condition?") },
            confirmButton = { TextButton(onClick = { cancel = false; scope.launch { ManualDependencyGraph.followUpRepository.cancelWatch(watch.id) } }) { Text("Cancel follow-up") } },
            dismissButton = { TextButton(onClick = { cancel = false }) { Text("Keep waiting") } })
        return
    }
    AutomationPage("Automations", onBack, "Run useful things when something changes") {
        val statuses = state.routines.map { routineUiStatus(it, readiness.issue(it) != null, it.id.value in state.blockedRoutines) }
        Text("${statuses.count { it == RoutineUiStatus.ACTIVE }} active · ${statuses.count { it == RoutineUiStatus.PAUSED }} paused")
        val attentionCount = statuses.count { it in setOf(RoutineUiStatus.NEEDS_REVIEW, RoutineUiStatus.NEEDS_SETUP, RoutineUiStatus.BLOCKED) } + state.blockedRoutines.keys.count { id -> state.routines.none { it.id.value == id } }
        Text(if (attentionCount == 0) "All sources ready" else "$attentionCount need attention", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { creating = true }) { Text("New automation") }
        TabRow(selectedTabIndex = tab.ordinal) {
            AutomationTab.entries.forEach { item -> Tab(selected = tab == item, onClick = { tab = item }, text = { Text(item.title) }) }
        }
        state.error?.let { Text(it.take(240), color = MaterialTheme.colorScheme.error) }
        when (tab) {
            AutomationTab.ROUTINES -> {
                if (state.loading) CircularProgressIndicator()
                else if (state.routines.isEmpty()) Text("Start with a battery alert, a connection update or an evening digest.")
                state.routines.forEach { routine ->
                    val status = routineUiStatus(routine, readiness.issue(routine) != null, routine.id.value in state.blockedRoutines)
                    HaloWorkspaceCard(modifier = Modifier.clickable { detailId = routine.id }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(routine.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text(status.label, style = MaterialTheme.typography.labelLarge)
                            }
                            Text(routine.trigger.humanLabel(readiness.labels))
                            Text(routine.actions.joinToString("\n") { it.humanLabel() })
                            Text(routine.delivery.humanLabel(readiness.labels), style = MaterialTheme.typography.bodySmall)
                            Text(routine.nextRunLabel(), style = MaterialTheme.typography.bodySmall)
                            readiness.issue(routine)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            if (status == RoutineUiStatus.NEEDS_REVIEW) TextButton(onClick = { draft = RoutineDraft(routine, routine.id) }) { Text("Review") }
                        }
                    }
                }
                state.blockedRoutines.filterKeys { id -> state.routines.none { it.id.value == id } }.forEach { (id, _) ->
                    var delete by remember(id) { mutableStateOf(false) }
                    HaloWorkspaceCard { Column(Modifier.padding(16.dp)) {
                        Text("Automation needs review", style = MaterialTheme.typography.titleMedium)
                        Text("This saved definition is no longer supported. It cannot run.")
                        TextButton(onClick = { delete = true }) { Text("Delete unsupported automation") }
                    } }
                    if (delete) DeleteRoutineConfirmation("unsupported automation", { delete = false }) { viewModel.delete(RoutineId(id)); delete = false }
                }
            }
            AutomationTab.FOLLOW_UPS -> {
                if (watches.isEmpty()) Text("Ask Halo to tell you when internet returns or a device connects.")
                watches.forEach { watch -> HaloWorkspaceCard(modifier = Modifier.clickable { selectedWatch = watch.id }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(watch.presentationText ?: watch.condition.humanLabel(readiness.labels), style = MaterialTheme.typography.titleMedium)
                        Text(watch.condition.humanLabel(readiness.labels))
                        Text(watch.status.humanLabel())
                    }
                } }
            }
            AutomationTab.HISTORY -> {
                if (state.recentRuns.isEmpty()) Text("No automation runs yet.")
                state.recentRuns.forEach { run -> HaloWorkspaceCard(modifier = Modifier.clickable { selectedRun = run.id }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(state.routines.firstOrNull { it.id == run.routineId }?.name ?: "Removed automation", style = MaterialTheme.typography.titleMedium)
                        Text(humanTime(run.triggerAt))
                        Text(run.state.humanLabel())
                        Text("${run.actionsCompleted} / ${run.actionsTotal} actions · ${deliveryResultLabel(run.deliveryOutcome)}")
                        if (run.coalescedCount > 1) Text("${run.coalescedCount} notifications grouped")
                    }
                } }
            }
        }
        TextButton(onClick = onNotifications) { Text("Notification settings") }
    }
    if (creating) AutomationPicker("New automation", { creating = false }) {
        RoutineTemplate.entries.forEach { template ->
            ListItem(headlineContent = { Text(template.title) }, supportingContent = { Text(template.description) },
                modifier = Modifier.clickable { draft = template.buildDraft(); creating = false })
        }
        TextButton(onClick = { draft = RoutineTemplate.NETWORK.buildDraft().let { it.copy(routine = it.routine.copy(name = "", actions = emptyList())) }; creating = false }) { Text("Start from scratch") }
    }
}

@Composable
internal fun AutomationPage(title: String, onBack: () -> Unit, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HaloWorkspaceHeader(title, subtitle, onBack = onBack)
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AutomationPicker(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
internal fun DetailField(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value)
    }
}

@Composable
internal fun RoutineReviewFields(routine: Routine, labels: AutomationLabels) {
    DetailField("WHEN", routine.trigger.humanLabel(labels))
    DetailField("IF", routine.conditions.takeIf { it.isNotEmpty() }?.joinToString("\n") { it.humanLabel(labels) } ?: "No additional conditions")
    DetailField("THEN", routine.actions.joinToString("\n") { it.humanLabel() })
    DetailField("DELIVER", routine.delivery.humanLabel(labels))
    DetailField("REPEAT", humanInterval(routine.minimumIntervalSeconds))
    (routine.trigger as? RoutineTrigger.NotificationFromApp)?.let {
        DetailField("GROUP NOTIFICATIONS", if (it.coalesceSeconds == 0L) "Off" else "${it.coalesceSeconds} seconds")
    }
}

@Composable
private fun RoutineDetail(routine: Routine, latestRun: RoutineRunSummary?, running: Boolean, readiness: AutomationReadiness,
    error: String?, blocked: Boolean, onBack: () -> Unit, onEdit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit,
    onRun: () -> Unit, onEnable: (Boolean) -> Unit, onActivity: () -> Unit, onNotifications: () -> Unit, onInbox: () -> Unit) {
    var confirmRun by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val status = routineUiStatus(routine, readiness.issue(routine) != null, blocked)
    AutomationPage(routine.name, onBack) {
        RoutineReviewFields(routine, readiness.labels)
        DetailField("STATUS", status.label)
        DetailField("NEXT", routine.nextRunLabel())
        AutomationSetup(routine, readiness, onNotifications, onInbox)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { confirmRun = true }, enabled = !running && status != RoutineUiStatus.NEEDS_REVIEW) { Text(if (running) "Running…" else "Run now") }
            OutlinedButton(onClick = onEdit) { Text(if (status == RoutineUiStatus.NEEDS_REVIEW) "Review" else "Edit") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(routine.enabled, onCheckedChange = onEnable, enabled = !running && (routine.enabled || status == RoutineUiStatus.ACTIVE || status == RoutineUiStatus.PAUSED))
            Text(if (routine.enabled) "On" else "Off", Modifier.padding(top = 12.dp))
        }
        latestRun?.let { HaloWorkspaceCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { RunEvidence(it, onActivity) } } }
        error?.let { Text(it.take(240), color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onDuplicate) { Text("Duplicate") }
        TextButton(onClick = { confirmDelete = true }) { Text(uiText("Delete")) }
    }
    if (confirmRun) AlertDialog(onDismissRequest = { confirmRun = false }, title = { Text("Run “${routine.name}” now?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(routine.actions.joinToString("\n") { it.humanLabel() })
            Text(routine.delivery.humanLabel(readiness.labels))
            Text("This uses the saved approved automation. Its conditions still apply.")
        } }, confirmButton = { TextButton(onClick = { confirmRun = false; onRun() }) { Text("Run now") } },
        dismissButton = { TextButton(onClick = { confirmRun = false }) { Text(uiText("Cancel")) } })
    if (confirmDelete) DeleteRoutineConfirmation(routine.name, { confirmDelete = false }) { confirmDelete = false; onDelete() }
}

@Composable
private fun DeleteRoutineConfirmation(name: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete “${name}”?") },
        text = { Text("Its history remains part of Activity where applicable. The automation itself will no longer run.") },
        confirmButton = { TextButton(onClick = onDelete) { Text(uiText("Delete")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Cancel")) } })
}

@Composable
private fun RunEvidence(run: RoutineRunSummary, onActivity: () -> Unit) {
    DetailField("Status", run.state.humanLabel())
    DetailField("Actions", "${run.actionsCompleted} / ${run.actionsTotal} completed")
    DetailField("Trigger time", humanTime(run.triggerAt))
    DetailField("Trigger source", when (run.source) { "MANUAL" -> "Run now"; "SCHEDULED" -> "Schedule"; else -> "Event trigger" })
    DetailField("Delivery", deliveryResultLabel(run.deliveryOutcome))
    if (run.coalescedCount > 1) Text("${run.coalescedCount} notifications grouped")
    run.reason?.take(240)?.takeIf { it.isNotBlank() }?.let { DetailField("Details", it) }
    if (run.agentRunId.isNotBlank()) TextButton(onClick = onActivity) { Text("Open in Activity") }
}

@Composable
internal fun AutomationSetup(routine: Routine, readiness: AutomationReadiness, onNotifications: () -> Unit, onInbox: () -> Unit) {
    val context = LocalContext.current
    val issue = readiness.issue(routine)
    DetailField("Source status", readiness.triggerIssue(routine.trigger) ?: if (routine.trigger.reachability() == TriggerReachability.RUNTIME_ACTIVE_ONLY) "Ready while Halo is running" else "Ready")
    readiness.deliveryIssue(routine.delivery)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (routine.trigger is RoutineTrigger.AppStarted && issue != null)
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Set up phone screen access") }
    if (routine.trigger is RoutineTrigger.NotificationFromApp || routine.actions.any { it.toolId == "halo_notification_digest" }) {
        TextButton(onClick = onNotifications) { Text("Manage notification access") }
        TextButton(onClick = onInbox) { Text("Open Inbox") }
    }
    if (routine.delivery == RoutineDeliveryPolicy.AndroidNotification && !readiness.notificationsEnabled)
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Allow Halo notifications") }
}
