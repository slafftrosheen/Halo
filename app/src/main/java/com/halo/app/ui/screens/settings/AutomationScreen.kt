package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.runtime.cognition.routine.*
import java.time.*
import java.util.UUID

@Composable
fun AutomationScreen(viewModel: RoutinesViewModel, onBack: () -> Unit, onNotifications: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<Routine?>(null) }
    var editor by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    val result by viewModel.result.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row { TextButton(onClick = onBack) { Text("Back") }; TextButton(onClick = onNotifications) { Text("Notifications & Digests") } }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(state.schedulingStatus, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall)
        state.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        state.blockedRoutines.forEach { (id, reason) ->
            Text(reason, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { viewModel.delete(RoutineId(id)) }) { Text("Delete unsupported routine") }
        }
        TextButton(onClick = { history = true }) { Text("Run history") }
        Box(Modifier.weight(1f)) {
            RoutinesScreen(state.routines, viewModel::enable,
                onCreateRoutine = { editing = null; editor = true },
                onOpenRoutine = { id -> editing = state.routines.firstOrNull { it.id == id }; editor = true })
        }
    }
    if (editor) RoutineEditor(editing, state.error, { editor = false },
        { viewModel.save(it) { editor = false } },
        { editing?.let { viewModel.delete(it.id) }; editor = false },
        { editing?.let { viewModel.runNow(it.id) }; editor = false })
    if (history) AlertDialog(onDismissRequest = { history = false }, title = { Text("Run history") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            if (state.recentRuns.isEmpty()) Text("No runs yet")
            state.recentRuns.forEach { run -> Text("${state.routines.firstOrNull { it.id == run.routineId }?.name ?: "Notification digest"}\n${run.triggerAt} - ${run.state}", Modifier.padding(vertical = 8.dp)) }
        } }, confirmButton = { TextButton(onClick = { history = false }) { Text("Close") } })
    result?.let { AlertDialog(onDismissRequest = { viewModel.result.value = null }, title = { Text("Routine result") },
        text = { Text(it, Modifier.verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { viewModel.result.value = null }) { Text("Close") } }) }
}

@Composable
private fun RoutineEditor(original: Routine?, error: String?, onDismiss: () -> Unit, onSave: (Routine) -> Unit,
    onDelete: () -> Unit, onRun: () -> Unit) {
    var name by remember { mutableStateOf(original?.name ?: "Evening digest") }
    var type by remember { mutableStateOf(when (original?.trigger) { is RoutineTrigger.Once -> "Once"; is RoutineTrigger.DeviceConnected -> "Device connects"; is RoutineTrigger.DeviceDisconnected -> "Device disconnects"; else -> "At time" }) }
    var time by remember { mutableStateOf((original?.trigger as? RoutineTrigger.AtTime)?.localTime?.toString() ?: "18:00") }
    var zone by remember { mutableStateOf((original?.trigger as? RoutineTrigger.AtTime)?.zoneId?.id ?: ZoneId.systemDefault().id) }
    var once by remember { mutableStateOf((original?.trigger as? RoutineTrigger.Once)?.at?.atZone(ZoneId.systemDefault())?.toLocalDateTime()?.toString() ?: LocalDateTime.now().plusHours(1).withSecond(0).withNano(0).toString()) }
    var days by remember { mutableStateOf((original?.trigger as? RoutineTrigger.AtTime)?.days ?: setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) }
    var device by remember { mutableStateOf(when (val t = original?.trigger) { is RoutineTrigger.DeviceConnected -> t.deviceId; is RoutineTrigger.DeviceDisconnected -> t.deviceId; else -> "" }) }
    var minutes by remember { mutableStateOf(original?.actions?.joinToString(", ") { org.json.JSONObject(it.argumentsJson).optInt("window_minutes", 480).toString() } ?: "480") }
    var network by remember { mutableStateOf(original?.conditions?.any { it is RoutineCondition.NetworkAvailable } == true) }
    var conditionDevice by remember { mutableStateOf(original?.conditions?.filterIsInstance<RoutineCondition.DeviceAvailable>()?.firstOrNull()?.deviceId ?: "") }
    var provider by remember { mutableStateOf(original?.conditions?.filterIsInstance<RoutineCondition.ProviderAvailable>()?.firstOrNull()?.providerId ?: "") }
    var localError by remember { mutableStateOf<String?>(null) }
    fun trigger(): RoutineTrigger = when (type) {
        "Once" -> RoutineTrigger.Once(LocalDateTime.parse(once).atZone(ZoneId.of(zone)).toInstant())
        "Device connects" -> RoutineTrigger.DeviceConnected(device.trim())
        "Device disconnects" -> RoutineTrigger.DeviceDisconnected(device.trim())
        else -> RoutineTrigger.AtTime(LocalTime.parse(time), days, ZoneId.of(zone))
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (original == null) "New routine" else "Edit routine") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") })
            Text("WHEN")
            listOf("Once", "At time", "Device connects", "Device disconnects").forEach { choice ->
                TextButton(onClick = { type = choice }) { Text(if (type == choice) "Selected: $choice" else choice) }
            }
            if (type == "Once") OutlinedTextField(once, { once = it }, label = { Text("Date and time: YYYY-MM-DDTHH:MM") })
            if (type == "At time") {
                OutlinedTextField(time, { time = it }, label = { Text("Time: HH:MM") })
                DayOfWeek.values().forEach { day -> Row {
                    Checkbox(day in days, { checked -> days = if (checked) days + day else days - day }); Text(day.name)
                } }
                Text("No selected days means every day")
            }
            if (type == "Once" || type == "At time") OutlinedTextField(zone, { zone = it }, label = { Text("Time zone") })
            else OutlinedTextField(device, { device = it }, label = { Text("Associated device ID") })
            Text("Next: " + runCatching { trigger().nextOccurrence(Instant.now())?.toString() ?: "Device event / expired" }.getOrDefault("Enter a valid schedule"))
            Text("IF: optional")
            Row { Checkbox(network, { network = it }); Text("Network available") }
            OutlinedTextField(conditionDevice, { conditionDevice = it }, label = { Text("Required device ID") })
            OutlinedTextField(provider, { provider = it }, label = { Text("Required provider ID") })
            Text("THEN: Offline notification digest")
            OutlinedTextField(minutes, { minutes = it }, label = { Text("Windows in minutes: 15-1440, comma-separated") })
            Text("Saving grants this digest action permission to run. Content stays local. Delivery is quiet.")
            (localError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (original != null) { TextButton(onClick = onRun) { Text("Run saved routine now") }; TextButton(onClick = onDelete) { Text("Delete routine") } }
        } },
        confirmButton = { TextButton(onClick = {
            try {
                val now = Instant.now()
                val windows = minutes.split(',').map { it.trim().toInt().coerceIn(15, 1440) }
                require(windows.size in 1..10) { "Use at most 10 digest actions" }
                val conditions = buildList {
                    if (network) add(RoutineCondition.NetworkAvailable(true))
                    if (conditionDevice.isNotBlank()) add(RoutineCondition.DeviceAvailable(conditionDevice.trim()))
                    if (provider.isNotBlank()) add(RoutineCondition.ProviderAvailable(provider.trim()))
                }
                onSave(Routine(original?.id ?: RoutineId(UUID.randomUUID().toString()), name.trim(), original?.enabled ?: true,
                    trigger(), conditions, windows.map { RoutineAction("halo_notification_digest", "{\"window_minutes\":$it}") }, original?.createdAt ?: now, now))
            } catch (e: Exception) { localError = e.message ?: "Check the fields" }
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
