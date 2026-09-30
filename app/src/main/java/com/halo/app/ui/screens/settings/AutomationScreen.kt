package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.halo.app.ui.workspace.*
import com.halo.app.di.ManualDependencyGraph
import com.halo.runtime.cognition.routine.*
import com.halo.runtime.cognition.tools.ToolDescriptor
import com.halo.host.android.resolver.AndroidApplicationResolver
import com.halo.host.android.resolver.AppCandidate
import org.json.JSONObject
import java.time.*
import java.util.UUID

@Composable
fun AutomationScreen(viewModel: RoutinesViewModel, onBack: () -> Unit, onNotifications: () -> Unit, reviewRoutineId: String? = null, onReviewedSave: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<Routine?>(null) }
    var editor by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    val result by viewModel.result.collectAsState()
    LaunchedEffect(reviewRoutineId,state.loading) {
        if(reviewRoutineId != null && !state.loading) {
            editing=state.routines.firstOrNull { it.id.value == reviewRoutineId }
            if(editing != null) editor=true
        }
    }
    if(editor) {
        RoutineEditor(editing, state.error, viewModel.actionDescriptors, { editor = false },
            { viewModel.save(it) { editor = false; onReviewedSave() } }, { editing?.let { viewModel.delete(it.id) }; editor = false },
            { editing?.let { viewModel.runNow(it.id) }; editor = false })
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row { TextButton(onClick = onBack) { Text("Back") }; TextButton(onClick = onNotifications) { Text("Notifications & Digests") } }
        Text(state.schedulingStatus, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall)
        state.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        state.blockedRoutines.forEach { (id, reason) ->
            Text(reason, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { viewModel.delete(RoutineId(id)) }) { Text("Delete unsupported automation") }
        }
        Row { TextButton(onClick = { history = true }) { Text("Run history") }; TextButton(onClick = { viewModel.reconcile() }) { Text("Reconcile schedules") } }
        Box(Modifier.weight(1f)) { RoutinesScreen(state.routines, viewModel::enable,
            onCreateRoutine = { editing = null; editor = true }, onOpenRoutine = { id -> editing = state.routines.firstOrNull { it.id == id }; editor = true }) }
    }
    if(history) AlertDialog(onDismissRequest = { history = false }, title = { Text("Automation history") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if(state.recentRuns.isEmpty()) Text("No runs yet")
            state.recentRuns.forEach { run -> Text("${state.routines.firstOrNull { it.id == run.routineId }?.name ?: "Automation"}\n${run.triggerAt} · ${run.state.name.lowercase()}\n${run.coalescedCount} event(s) · ${run.actionsCompleted} / ${run.actionsTotal} actions · ${run.deliveryOutcome?.lowercase() ?: "Delivery pending"}\n${run.reason.orEmpty()}", Modifier.padding(vertical = 8.dp)) }
        }
    }, confirmButton = { TextButton(onClick = { history = false }) { Text("Close") } })
    result?.let { AlertDialog(onDismissRequest = { viewModel.result.value = null }, title = { Text("Automation result") },
        text = { Text(it, Modifier.verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { viewModel.result.value = null }) { Text("Close") } }) }
}

@Composable
private fun RoutineEditor(original: Routine?, error: String?, descriptors: List<ToolDescriptor>, onDismiss: () -> Unit,
    onSave: (Routine) -> Unit, onDelete: () -> Unit, onRun: () -> Unit) {
    var name by remember { mutableStateOf(original?.name ?: "Evening update") }
    val choices = listOf("At time", "Once", "Device connects", "Device disconnects", "App opens", "App notification", "Battery rises", "Battery falls", "Charging starts", "Charging stops", "Network available", "Network lost")
    var type by remember { mutableStateOf(when(original?.trigger) {
        is RoutineTrigger.Once -> "Once"; is RoutineTrigger.DeviceConnected -> "Device connects"; is RoutineTrigger.DeviceDisconnected -> "Device disconnects"
        is RoutineTrigger.AppStarted -> "App opens"; is RoutineTrigger.NotificationFromApp -> "App notification"
        is RoutineTrigger.BatteryCrossedAbove -> "Battery rises"; is RoutineTrigger.BatteryCrossedBelow -> "Battery falls"
        RoutineTrigger.ChargingStarted -> "Charging starts"; RoutineTrigger.ChargingStopped -> "Charging stops"
        RoutineTrigger.NetworkAvailable -> "Network available"; RoutineTrigger.NetworkLost -> "Network lost"; else -> "At time"
    }) }
    var time by remember { mutableStateOf((original?.trigger as? RoutineTrigger.AtTime)?.localTime?.toString() ?: "18:00") }
    var zone by remember { mutableStateOf((original?.trigger as? RoutineTrigger.AtTime)?.zoneId?.id ?: ZoneId.systemDefault().id) }
    var once by remember { mutableStateOf((original?.trigger as? RoutineTrigger.Once)?.at?.atZone(ZoneId.systemDefault())?.toLocalDateTime()?.toString() ?: LocalDateTime.now().plusHours(1).withSecond(0).withNano(0).toString()) }
    var days by remember { mutableStateOf((original?.trigger as? RoutineTrigger.AtTime)?.days ?: emptySet()) }
    var device by remember { mutableStateOf(when(val t = original?.trigger) { is RoutineTrigger.DeviceConnected -> t.deviceId; is RoutineTrigger.DeviceDisconnected -> t.deviceId; else -> "" }) }
    var appPackage by remember { mutableStateOf(when(val t = original?.trigger) { is RoutineTrigger.AppStarted -> t.appPackage; is RoutineTrigger.NotificationFromApp -> t.packageName; else -> "" }) }
    var threshold by remember { mutableStateOf(when(val t = original?.trigger) { is RoutineTrigger.BatteryCrossedAbove -> t.percent; is RoutineTrigger.BatteryCrossedBelow -> t.percent; else -> 50 }) }
    var network by remember { mutableStateOf(original?.conditions?.any { it is RoutineCondition.NetworkAvailable } == true) }
    var networkRequired by remember { mutableStateOf(original?.conditions?.filterIsInstance<RoutineCondition.NetworkAvailable>()?.firstOrNull()?.required ?: true) }
    var battery by remember { mutableStateOf(original?.conditions?.filterIsInstance<RoutineCondition.BatteryAbove>()?.firstOrNull()?.percent?.toString() ?: "") }
    var conditionDevices by remember { mutableStateOf(original?.conditions?.filterIsInstance<RoutineCondition.DeviceAvailable>()?.map { it.deviceId }?.toSet() ?: emptySet()) }
    var providers by remember { mutableStateOf(original?.conditions?.filterIsInstance<RoutineCondition.ProviderAvailable>()?.map { it.providerId }?.toSet() ?: emptySet()) }
    var additionalConditions by remember { mutableStateOf(
        original?.conditions?.filterIsInstance<RoutineCondition.BatteryAbove>().orEmpty().drop(1) +
            original?.conditions?.filterIsInstance<RoutineCondition.NetworkAvailable>().orEmpty().drop(1)) }
    var delivery by remember { mutableStateOf(original?.delivery ?: RoutineDeliveryPolicy.SilentActivity) }
    var cooldown by remember { mutableStateOf((original?.minimumIntervalSeconds ?: 0).toString()) }
    var coalesce by remember { mutableStateOf(((original?.trigger as? RoutineTrigger.NotificationFromApp)?.coalesceSeconds ?: 300).toString()) }
    var actions by remember { mutableStateOf(original?.actions ?: descriptors.firstOrNull()?.let { listOf(RoutineAction(it.name)) }.orEmpty()) }
    var localError by remember { mutableStateOf<String?>(null) }
    var proposal by remember { mutableStateOf<Routine?>(null) }
    val context = LocalContext.current
    val devices by ManualDependencyGraph.devicePresenceRepository.presenceMap.collectAsState()
    fun trigger(): RoutineTrigger = when(type) {
        "Once" -> RoutineTrigger.Once(LocalDateTime.parse(once).atZone(ZoneId.of(zone)).toInstant())
        "Device connects" -> RoutineTrigger.DeviceConnected(device)
        "Device disconnects" -> RoutineTrigger.DeviceDisconnected(device)
        "App opens" -> RoutineTrigger.AppStarted(appPackage)
        "App notification" -> RoutineTrigger.NotificationFromApp(appPackage, coalesce.toLong())
        "Battery rises" -> RoutineTrigger.BatteryCrossedAbove(threshold)
        "Battery falls" -> RoutineTrigger.BatteryCrossedBelow(threshold)
        "Charging starts" -> RoutineTrigger.ChargingStarted
        "Charging stops" -> RoutineTrigger.ChargingStopped
        "Network available" -> RoutineTrigger.NetworkAvailable
        "Network lost" -> RoutineTrigger.NetworkLost
        else -> RoutineTrigger.AtTime(LocalTime.parse(time), days, ZoneId.of(zone))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HaloWorkspaceHeader(if(original == null) "New automation" else "Edit automation", onBack = onDismiss)
        HaloWorkspaceSection("NAME") { OutlinedTextField(name, { name = it.take(120) }, modifier = Modifier.fillMaxWidth(), label = { Text("Name") }) }
        HaloWorkspaceSection("WHEN") { HaloWorkspaceCard {
            choices.forEach { choice -> TextButton(onClick = { type = choice }) { Text(if(type == choice) "✓ $choice" else choice) } }
            if(type == "Once") OutlinedTextField(once, { once = it }, label = { Text("Date and time: YYYY-MM-DDTHH:MM") })
            if(type == "At time") {
                OutlinedTextField(time, { time = it }, label = { Text("Time: HH:MM") })
                DayOfWeek.entries.forEach { day -> Row { Checkbox(day in days, { checked -> days = if(checked) days + day else days - day }); Text(day.name.lowercase()) } }
                Text("No selected days means every day")
            }
            if(type in listOf("Once", "At time")) OutlinedTextField(zone, { zone = it }, label = { Text("Time zone") })
            if(type in listOf("Device connects", "Device disconnects")) {
                devices.keys.forEach { id -> TextButton(onClick = { device = id }) { Text(if(device == id) "✓ $id" else id) } }
                if(devices.isEmpty()) Text("Connect a device to select it")
            }
            if(type in listOf("App opens", "App notification")) AppSelection(appPackage) { appPackage = it }
            if(type in listOf("Battery rises", "Battery falls")) { Text("$threshold%"); Slider(threshold.toFloat(), { threshold = it.toInt() }, valueRange = 1f..100f, steps = 98) }
            if(type == "App notification") OutlinedTextField(coalesce, { coalesce = it.filter(Char::isDigit).take(4) }, label = { Text("Group repeated notifications for seconds (0–3600)") })
        } }
        HaloWorkspaceSection("IF · optional") { HaloWorkspaceCard {
            Row { Checkbox(network, { network = it }); Text("Require network state") }
            if(network) Row {
                TextButton(onClick = { networkRequired = true }) { Text(if(networkRequired) "✓ Available" else "Available") }
                TextButton(onClick = { networkRequired = false }) { Text(if(!networkRequired) "✓ Unavailable" else "Unavailable") }
            }
            OutlinedTextField(battery, { battery = it.filter(Char::isDigit).take(3) }, label = { Text("Battery above percent (1–100)") })
            (devices.keys + conditionDevices).forEach { id -> Row { Checkbox(id in conditionDevices, { checked -> conditionDevices = if(checked) conditionDevices + id else conditionDevices - id }); Text("Device available: $id") } }
            val knownProviders = ManualDependencyGraph.aiProviderRouter.getAllProviders()
            (knownProviders.map { it.id }.toSet() + providers).forEach { id -> Row {
                Checkbox(id in providers, { checked -> providers = if(checked) providers + id else providers - id })
                Text("Provider available: ${knownProviders.firstOrNull { it.id == id }?.displayName ?: id}")
            } }
            additionalConditions.forEachIndexed { index, condition -> Row {
                Text(when(condition) {
                    is RoutineCondition.BatteryAbove -> "Battery above ${condition.percent}%"
                    is RoutineCondition.NetworkAvailable -> if(condition.required) "Network available" else "Network unavailable"
                    else -> "Additional condition"
                })
                TextButton(onClick = { additionalConditions = additionalConditions.filterIndexed { i, _ -> i != index } }) { Text("Remove condition") }
            } }
        } }
        HaloWorkspaceSection("THEN") {
            actions.forEachIndexed { index, action -> HaloWorkspaceCard {
                val descriptor = descriptors.firstOrNull { it.name == action.toolId }
                Text(descriptor?.presentation?.title ?: descriptor?.definition?.description ?: "Unavailable action")
                descriptor?.let { d -> SchemaArguments(d, action.argumentsJson) { json -> actions = actions.toMutableList().also { it[index] = action.copy(argumentsJson = json) } } }
                TextButton(onClick = { actions = actions.filterIndexed { i, _ -> i != index } }) { Text("Remove action") }
            } }
            if(actions.size < 10) descriptors.forEach { d ->
                TextButton(enabled = routineSchemaEditable(d), onClick = { actions = actions + RoutineAction(d.name) }) { Text(if(routineSchemaEditable(d)) "Add ${d.presentation?.title ?: d.definition.description}" else "Not configurable as an automation yet") }
            }
        }
        HaloWorkspaceSection("DELIVERY") { HaloWorkspaceCard {
            listOf(RoutineDeliveryPolicy.SilentActivity, RoutineDeliveryPolicy.AndroidNotification, RoutineDeliveryPolicy.InAppWhenForeground).forEach { d ->
                TextButton(onClick = { delivery = d }) { Text(if(delivery == d) "✓ ${d.label()}" else d.label()) }
            }
            devices.keys.forEach { id -> TextButton(onClick = { delivery = RoutineDeliveryPolicy.WearableSpeech(id) }) { Text("Spoken update on $id") } }
            OutlinedTextField(cooldown, { cooldown = it.filter(Char::isDigit).take(5) }, label = { Text("Minimum seconds between event runs") })
        } }
        HaloWorkspaceSection("AVAILABILITY") { Text(runCatching { trigger().availabilityLabel() }.getOrDefault("Complete the trigger"))
            val sourceName = when(type) { "App opens" -> "foreground"; "App notification" -> "notifications"; "Device connects", "Device disconnects" -> "device"; "Network available", "Network lost" -> "network"; else -> "battery_charging" }
            ManualDependencyGraph.automationRuntime.registry.sources[sourceName]?.let { source ->
                val status by source.availability.collectAsState()
                Text(when(status) { AutomationSourceStatus.REQUIRES_SERVICE -> "Phone screen access needs setup"; AutomationSourceStatus.REQUIRES_PERMISSION -> "Notification access needs setup"; AutomationSourceStatus.UNAVAILABLE -> "Source unavailable"; else -> "Source ready" })
            }
            if(type == "App opens") TextButton(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Set up Phone screen access") }
            if(type == "App notification") TextButton(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Set up Notification access") }
        }
        Text("Runs only actions you approve for this automation. Saving creates permission for this definition; editing requires another save.")
        (localError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row {
            TextButton(onClick = {
                try {
                    val now = Instant.now()
                    val conditions = buildList { if(network) add(RoutineCondition.NetworkAvailable(networkRequired)); if(battery.isNotBlank()) add(RoutineCondition.BatteryAbove(battery.toInt())); conditionDevices.forEach { add(RoutineCondition.DeviceAvailable(it)) }; providers.forEach { add(RoutineCondition.ProviderAvailable(it)) }; addAll(additionalConditions) }
                    proposal = Routine(original?.id ?: RoutineId(UUID.randomUUID().toString()), name.trim(), original?.enabled ?: true, trigger(), conditions, actions,
                        original?.createdAt ?: now, now, delivery = delivery, minimumIntervalSeconds = cooldown.toLong())
                    ManualDependencyGraph.routineExecutor.validate(requireNotNull(proposal))
                } catch(e: Exception) { proposal = null; localError = e.message ?: "Check the fields" }
            }) { Text("Review & save") }
            if(original != null) { TextButton(onClick = onRun) { Text("Run saved automation") }; TextButton(onClick = onDelete) { Text("Delete") } }
        }
    }
    proposal?.let { r -> AlertDialog(onDismissRequest = { proposal = null }, title = { Text("Save automation?") },
        text = { Text(ManualDependencyGraph.routineExecutor.grantSummary(r)) },
        confirmButton = { TextButton(onClick = { onSave(r); proposal = null }) { Text("Save") } }, dismissButton = { TextButton(onClick = { proposal = null }) { Text("Cancel") } }) }
}

@Composable
private fun AppSelection(selected: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val resolver = remember { AndroidApplicationResolver(context) }
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppCandidate>>(emptyList()) }
    LaunchedEffect(query) { apps = resolver.search(query, includeSystem = true) }
    OutlinedTextField(query, { query = it.take(80) }, label = { Text("Find installed app") })
    if(selected.isNotBlank()) Text(runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(selected, 0)).toString() }.getOrDefault("Selected app is unavailable"))
    apps.forEach { candidate -> TextButton(onClick = { onSelect(candidate.app.packageName) }) { Text(if(selected == candidate.app.packageName) "✓ ${candidate.app.label}" else candidate.app.label) } }
}

/** Only simple provider schema fields. Unsupported nesting stays unavailable in the editor. */
@Composable
private fun SchemaArguments(d: ToolDescriptor, raw: String, onChange: (String) -> Unit) {
    val properties = d.definition.parameters["properties"] as? Map<*, *> ?: emptyMap<Any, Any>()
    val complex = properties.values.any { v -> (v as? Map<*, *>)?.get("type") !in setOf("string", "integer", "number", "boolean") }
    if(complex) { Text("Not configurable as an automation yet"); return }
    properties.forEach { (key, schemaValue) ->
        val field = key.toString(); val schema = schemaValue as? Map<*, *> ?: return@forEach
        val json = remember(raw) { JSONObject(raw) }
        val type = schema["type"]?.toString()
        val choices = schema["enum"] as? List<*>
        var input by remember(raw, field) { mutableStateOf(if(json.has(field)) json.get(field).toString() else "") }
        val label = field.replace('_', ' ').replaceFirstChar(Char::uppercase)
        when {
            choices != null -> choices.forEach { choice -> TextButton(onClick = { onChange(JSONObject(raw).put(field, choice).toString()) }) { Text(choice.toString()) } }
            type == "boolean" -> Row { Checkbox(json.optBoolean(field, false), { onChange(JSONObject(raw).put(field, it).toString()) }); Text(label) }
            else -> OutlinedTextField(input, { value ->
                input = value.take(2048)
                val converted: Any? = when(type) { "integer" -> value.toLongOrNull(); "number" -> value.toDoubleOrNull()?.takeIf { it.isFinite() }; else -> value.take((schema["maxLength"] as? Number)?.toInt()?.coerceIn(1,2048) ?: 2048) }
                val updated = JSONObject(raw)
                if(value.isBlank()) updated.remove(field) else updated.put(field, converted ?: value.take(2048))
                onChange(updated.toString())
            }, label = { Text(label) })
        }
    }
}

private fun routineSchemaEditable(d: ToolDescriptor): Boolean {
    val properties = d.definition.parameters["properties"] as? Map<*, *> ?: return true
    return properties.values.all { (it as? Map<*, *>)?.get("type") in setOf("string", "integer", "number", "boolean") }
}
