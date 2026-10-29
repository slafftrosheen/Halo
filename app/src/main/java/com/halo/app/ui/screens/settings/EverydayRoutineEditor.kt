package com.halo.app.ui.screens.settings

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import com.halo.app.ui.design.HaloFocusAction
import com.halo.app.ui.icons.HaloGlyph
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.halo.app.ui.workspace.*
import com.halo.host.android.resolver.AndroidApplicationResolver
import com.halo.host.android.resolver.AppCandidate
import com.halo.runtime.cognition.routine.*
import com.halo.runtime.cognition.tools.ToolDescriptor
import org.json.JSONObject
import java.time.*

private enum class TriggerCategory(val title: String) { SCHEDULE("Schedule"), DEVICE("Device"), APPS("Apps"), BATTERY("Battery & power"), NETWORK("Network") }

@Composable
internal fun EverydayRoutineEditor(initial: RoutineDraft, error: String?, descriptors: List<ToolDescriptor>,
    readiness: AutomationReadiness, onDismiss: () -> Unit, review: (Routine) -> String,
    onSave: (Routine) -> Unit, onNotifications: () -> Unit, onInbox: () -> Unit, saving: Boolean = false,
    trial: RoutineTrialState = RoutineTrialState(), onTryOnce: ((Routine) -> Unit)? = null, onStopTrial: () -> Unit = {},
    startWithReview: Boolean = false) {
    var routine by rememberSaveable(initial.routine.id.value, stateSaver = routineEditStateSaver) { mutableStateOf(initial.routine) }
    var reviewing by rememberSaveable { mutableStateOf(startWithReview) }
    val proposal = routine.takeIf { reviewing }
    var confirmTrial by remember { mutableStateOf(false) }
    val busy = saving || trial.running
    var localError by remember { mutableStateOf<String?>(null) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<TriggerCategory?>(null) }
    var deviceTarget by rememberSaveable { mutableStateOf<Int?>(null) }
    var zoneQuery by rememberSaveable { mutableStateOf("") }
    var leaving by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (!busy) { if (proposal != null) reviewing = false else leaving = true } }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false },
        title = { Text(uiText("Discard this automation draft?")) },
        text = { Text(uiText("These edits have not been saved. Existing automation settings are kept until you save.")) },
        confirmButton = { TextButton(enabled = !busy, onClick = { leaving = false; onDismiss() }) { Text(uiText("Discard draft")) } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text(uiText("Keep editing")) } })
    var invalidInputs by remember { mutableStateOf(emptySet<String>()) }
    fun inputValidity(key: String, valid: Boolean) { invalidInputs = if (valid) invalidInputs - key else invalidInputs + key }
    var chosenApps by remember { mutableStateOf(emptyMap<String, String>()) }
    val labels = readiness.labels.copy(apps = readiness.labels.apps + chosenApps)
    val editorReadiness = readiness.copy(labels = labels)
    fun replaceAction(index: Int, action: RoutineAction) { routine = routine.copy(actions = routine.actions.mapIndexed { i, old -> if (i == index) action else old }) }
    fun replaceCondition(index: Int, condition: RoutineCondition) { routine = routine.copy(conditions = routine.conditions.mapIndexed { i, old -> if (i == index) condition else old }) }
    proposal?.let { proposed ->
        AutomationPage("Review automation", { if (!busy) reviewing = false }) {
            Text(proposed.name, style = MaterialTheme.typography.titleLarge)
            RoutineReviewFields(proposed, labels)
            if (onTryOnce != null) {
                Text(uiText("Try these actions now. Conditions still apply. The result stays here and in History; no notification, speech or future run is scheduled."))
                OutlinedButton(enabled = !busy, onClick = { confirmTrial = true }) { Text(uiText("Try it once")) }
                if (trial.running) {
                    Text(uiText("Trying it once…"))
                    TextButton(onClick = onStopTrial) { Text(uiText("Stop trial")) }
                }
                if (trial.draftKey == routineTrialKey(proposed)) {
                    trial.result?.let { TrialEvidence(it) }
                    trial.error?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
                }
            }
            DetailField("Approval", uiText("This automation may run these exact actions without asking each time. Changing the automation requires another review."))
            if (initial.originalId != null) Text(uiText("Saving replaces the approved definition and reschedules this automation."))
            AutomationSetup(proposed, editorReadiness, onNotifications, onInbox)
            (localError ?: error)?.let { Text(uiText(routineErrorLabel(it)), color = MaterialTheme.colorScheme.error) }
            Text(uiText("Saving approves this exact definition. Turning it on schedules future runs; it does not run it now."))
            HaloFocusAction(uiText(if (saving) "Saving…" else if (initial.originalId != null && initial.routine.enabled) "Save & keep on" else "Save & turn on"), HaloGlyph.AUTOMATION, !busy, { onSave(proposed.copy(enabled = true)) })
            OutlinedButton(enabled = !busy, onClick = { onSave(proposed.copy(enabled = false)) }) { Text(uiText("Save off")) }
        }
        if (confirmTrial) AlertDialog(onDismissRequest = { confirmTrial = false },
            title = { Text(uiText("Try this automation once?")) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(proposed.actions.map { it.uiLabel() }.joinToString("\n"))
                proposed.conditions.forEach { Text(it.uiLabel(labels)) }
                Text(uiText("This runs the reviewed actions once and records the result in History. It does not save this draft or test notification and wearable delivery."))
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = { confirmTrial = false; onTryOnce?.invoke(proposed) }) { Text(uiText("Run trial")) } },
            dismissButton = { TextButton(onClick = { confirmTrial = false }) { Text(uiText("Cancel")) } })
        return
    }
    AutomationPage(if (initial.originalId == null) "New automation" else "Edit automation", { leaving = true }) {
        Text(uiText("Changes stay in this draft until you review and save. An existing enabled automation keeps its saved definition."))
        HaloTextField(routine.name, { routine = routine.copy(name = it.take(120)) }, label = { Text(uiText("Name")) }, modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.SHORT, maxChars = 120)
        HaloSettingsSection(uiText("When")) {
            Text(routine.trigger.uiLabel(labels))
            OutlinedButton(onClick = { category = null; picker = "trigger" }) { Text(uiText("Choose trigger")) }
            when (val trigger = routine.trigger) {
                is RoutineTrigger.AtTime -> {
                    TimeChoice(trigger.localTime) { routine = routine.copy(trigger = trigger.copy(localTime = it)) }
                    DayChoices(trigger.days) { routine = routine.copy(trigger = trigger.copy(days = it)) }
                    TextButton(onClick = { zoneQuery = ""; picker = "zone" }) {
                        Text("Time zone · ${if (trigger.zoneId == ZoneId.systemDefault()) "System · " else ""}${trigger.zoneId.id}")
                    }
                }
                is RoutineTrigger.Once -> {
                    val local = trigger.at.atZone(ZoneId.systemDefault())
                    DateChoice(local.toLocalDate()) { date -> routine = routine.copy(trigger = RoutineTrigger.Once(date.atTime(local.toLocalTime()).atZone(local.zone).toInstant())) }
                    TimeChoice(local.toLocalTime()) { time -> routine = routine.copy(trigger = RoutineTrigger.Once(local.toLocalDate().atTime(time).atZone(local.zone).toInstant())) }
                    Text("Uses system time zone · ${local.zone.id}", style = MaterialTheme.typography.bodySmall)
                }
                is RoutineTrigger.BatteryCrossedAbove -> BatteryChoice(trigger.percent) { routine = routine.copy(trigger = trigger.copy(percent = it)) }
                is RoutineTrigger.BatteryCrossedBelow -> BatteryChoice(trigger.percent) { routine = routine.copy(trigger = trigger.copy(percent = it)) }
                is RoutineTrigger.DeviceConnected, is RoutineTrigger.DeviceDisconnected -> OutlinedButton(onClick = { deviceTarget = null; picker = "device" }) { Text(uiText("Choose device")) }
                is RoutineTrigger.AppStarted, is RoutineTrigger.NotificationFromApp -> OutlinedButton(onClick = { picker = "app" }) { Text(uiText("Choose installed app")) }
                else -> Unit
            }
            if (routine.trigger is RoutineTrigger.BatteryCrossedAbove || routine.trigger is RoutineTrigger.BatteryCrossedBelow)
                Text(uiText("Runs only when the battery crosses this level. It does not repeat while battery stays on the same side."), style = MaterialTheme.typography.bodySmall)
            if (routine.trigger == RoutineTrigger.NetworkAvailable || routine.trigger == RoutineTrigger.NetworkLost)
                Text(uiText("Uses Android’s usable-network state. No Wi-Fi name is stored."), style = MaterialTheme.typography.bodySmall)
            (routine.trigger as? RoutineTrigger.NotificationFromApp)?.let { trigger ->
                PresetSeconds("Group repeated notifications", trigger.coalesceSeconds, listOf(0L, 60L, 300L, 900L), 3600, onValidity = { inputValidity("group", it) }) { routine = routine.copy(trigger = trigger.copy(coalesceSeconds = it)) }
                Text(uiText("Repeated notifications during this window count as one automation occurrence."), style = MaterialTheme.typography.bodySmall)
            }
        }
        HaloSettingsSection(uiText("Only if")) {
            if (routine.conditions.isEmpty()) Text(uiText("No additional conditions"))
            routine.conditions.forEachIndexed { index, condition ->
                HaloWorkspaceCard { Column(Modifier.padding(12.dp)) {
                    Text(condition.uiLabel(labels))
                    when (condition) {
                        is RoutineCondition.BatteryAbove -> BatteryChoice(condition.percent) { replaceCondition(index, condition.copy(percent = it)) }
                        is RoutineCondition.NetworkAvailable -> Row { Switch(condition.required, { replaceCondition(index, condition.copy(required = it)) }); Text(if (condition.required) "Available" else "Unavailable", Modifier.padding(top = 12.dp)) }
                        is RoutineCondition.DeviceAvailable -> TextButton(onClick = { deviceTarget = index; picker = "device" }) { Text(uiText("Choose device")) }
                        is RoutineCondition.ProviderAvailable -> TextButton(onClick = { deviceTarget = index; picker = "provider" }) { Text(uiText("Choose provider")) }
                    }
                    TextButton(onClick = { routine = routine.copy(conditions = routine.conditions.filterIndexed { i, _ -> i != index }) }) { Text(uiText("Remove condition")) }
                } }
            }
            OutlinedButton(enabled = routine.conditions.size < 10, onClick = { picker = "condition" }) { Text(uiText("Add condition")) }
        }
        HaloSettingsSection(uiText("Do")) {
            if (routine.actions.isEmpty()) Text(uiText("Choose what this automation should do"))
            routine.actions.forEachIndexed { index, action ->
                HaloWorkspaceCard { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (action.toolId) {
                        "halo_routine_message" -> {
                            Text(uiText("Show message"), style = MaterialTheme.typography.titleMedium)
                            val text = actionArguments(action).optString("text", "")
                            HaloTextField(text, { replaceAction(index, messageAction(it.take(240))) }, label = { Text(uiText("Message")) }, supportingText = { Text(uiText("%1\$d / 240 characters", text.length)) }, modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.TEXT, maxChars = 240)
                        }
                        "halo_notification_digest" -> {
                            Text(uiText("Notification digest"), style = MaterialTheme.typography.titleMedium)
                            Text(uiText("Made on this phone · no cloud needed"), style = MaterialTheme.typography.bodySmall)
                            val minutes = actionArguments(action).optInt("window_minutes", 60)
                            DigestWindow(minutes, onValidity = { inputValidity("window-$index", it) }) { value -> replaceAction(index, action.copy(argumentsJson = JSONObject().put("window_minutes", value).toString())) }
                        }
                        else -> {
                            val descriptor = descriptors.firstOrNull { it.name == action.toolId }
                            Text(descriptor?.presentation?.title ?: "Additional approved action")
                            if (descriptor == null) Text(uiText("Preserved from this automation. This action must be available before saving."))
                            else SchemaArguments(descriptor, action.argumentsJson) { replaceAction(index, action.copy(argumentsJson = it)) }
                        }
                    }
                    TextButton(onClick = { routine = routine.copy(actions = routine.actions.filterIndexed { i, _ -> i != index }); invalidInputs = invalidInputs.filterNot { it.startsWith("window-") }.toSet() }) { Text(uiText("Remove action")) }
                } }
            }
            if (routine.actions.size >= 8) Text("${routine.actions.size} of 10 actions")
            OutlinedButton(enabled = routine.actions.size < 10, onClick = { picker = "action" }) { Text(uiText("Add action")) }
        }
        HaloSettingsSection(uiText("Tell me")) {
            Text(routine.delivery.uiLabel(labels))
            OutlinedButton(onClick = { picker = "delivery" }) { Text(uiText("Choose delivery")) }
        }
        HaloSettingsSection(uiText("Time between runs")) {
            PresetSeconds("Minimum time between runs", routine.minimumIntervalSeconds, listOf(0L, 60L, 300L, 900L, 3600L), 86400, onValidity = { inputValidity("cooldown", it) }) {
                routine = routine.copy(minimumIntervalSeconds = it)
            }
        }
        AutomationSetup(routine, editorReadiness, onNotifications, onInbox)
        (localError ?: error)?.let { Text(uiText(routineErrorLabel(it)), color = MaterialTheme.colorScheme.error) }
        com.halo.app.ui.components.HaloActionButton(modifier = Modifier.fillMaxWidth(), enabled = invalidInputs.isEmpty(), onClick = {
            try {
                val candidate = routine.copy(name = routine.name.trim(), grantDigest = null, updatedAt = Instant.now())
                candidate.actions.filter { it.toolId == "halo_routine_message" }.forEach {
                    require(actionArguments(it).optString("text", "").trim().length in 1..240) { "Enter a message with 1 to 240 characters" }
                }
                review(candidate) // Existing executor validates schema, safety, source and exact grant preview.
                localError = null
                routine = candidate
                reviewing = true
            } catch (e: IllegalArgumentException) { localError = e.message ?: "Check the automation fields" }
        }) { Text(uiText("Review & save")) }
        Text(uiText("Try it once is available on the review page before saving."), style = MaterialTheme.typography.bodySmall)
    }
    picker?.let { selected -> AutomationPicker(when (selected) {
        "trigger" -> category?.title ?: "Choose a trigger"
        "condition" -> "Add condition"
        "action" -> "Add action"
        "delivery" -> "Choose delivery"
        "device" -> "Choose device"
        "provider" -> "Choose provider"
        "zone" -> "Choose time zone"
        else -> "Choose installed app"
    }, { picker = null }) {
        fun chooseTrigger(trigger: RoutineTrigger) { routine = routine.copy(trigger = trigger); invalidInputs = invalidInputs - "group"; picker = null }
        when (selected) {
            "trigger" -> if (category == null) TriggerCategory.entries.forEach { item ->
                PickerItem(item.title) { category = item }
            } else {
                TextButton(onClick = { category = null }) { Text(uiText("All categories")) }
                when (requireNotNull(category)) {
                    TriggerCategory.SCHEDULE -> {
                        PickerItem("At time") { chooseTrigger(RoutineTrigger.AtTime(LocalTime.of(18, 0))) }
                        PickerItem("Once") { chooseTrigger(RoutineTrigger.Once(Instant.now().plusSeconds(3600))) }
                    }
                    TriggerCategory.DEVICE -> {
                        PickerItem("Device connects") { chooseTrigger(RoutineTrigger.DeviceConnected("")) }
                        PickerItem("Device disconnects") { chooseTrigger(RoutineTrigger.DeviceDisconnected("")) }
                    }
                    TriggerCategory.APPS -> {
                        PickerItem("App opens") { chooseTrigger(RoutineTrigger.AppStarted("")) }
                        PickerItem("Notification from app") { chooseTrigger(RoutineTrigger.NotificationFromApp("")) }
                    }
                    TriggerCategory.BATTERY -> {
                        PickerItem("Battery falls below") { chooseTrigger(RoutineTrigger.BatteryCrossedBelow(20)) }
                        PickerItem("Battery rises above") { chooseTrigger(RoutineTrigger.BatteryCrossedAbove(80)) }
                        PickerItem("Charging starts") { chooseTrigger(RoutineTrigger.ChargingStarted) }
                        PickerItem("Charging stops") { chooseTrigger(RoutineTrigger.ChargingStopped) }
                    }
                    TriggerCategory.NETWORK -> {
                        PickerItem("Network becomes available") { chooseTrigger(RoutineTrigger.NetworkAvailable) }
                        PickerItem("Network is lost") { chooseTrigger(RoutineTrigger.NetworkLost) }
                    }
                }
            }
            "condition" -> {
                listOf("Battery level", "Network state", "Device available", "AI provider available").forEachIndexed { index, title -> PickerItem(title) {
                    routine = routine.copy(conditions = routine.conditions + when (index) {
                        0 -> RoutineCondition.BatteryAbove(40)
                        1 -> RoutineCondition.NetworkAvailable(true)
                        2 -> RoutineCondition.DeviceAvailable("")
                        else -> RoutineCondition.ProviderAvailable("")
                    }); picker = null
                } }
            }
            "action" -> descriptors.forEach { descriptor -> PickerItem(when (descriptor.name) {
                "halo_routine_message" -> "Show message"
                "halo_notification_digest" -> "Notification digest"
                else -> descriptor.presentation?.title ?: "Additional action"
            }, enabled = routineSchemaEditable(descriptor)) {
                routine = routine.copy(actions = routine.actions + when (descriptor.name) {
                    "halo_routine_message" -> messageAction("")
                    "halo_notification_digest" -> RoutineAction(descriptor.name, "{\"window_minutes\":480}")
                    else -> RoutineAction(descriptor.name)
                }); picker = null
            } }
            "delivery" -> {
                PickerItem("Silent", "Keep result in Activity") { routine = routine.copy(delivery = RoutineDeliveryPolicy.SilentActivity); picker = null }
                PickerItem("Notification", "Show an Android notification") { routine = routine.copy(delivery = RoutineDeliveryPolicy.AndroidNotification); picker = null }
                PickerItem("In Halo", "Show when Halo is open") { routine = routine.copy(delivery = RoutineDeliveryPolicy.InAppWhenForeground); picker = null }
                PickerItem("Wearable", "Speak on one selected wearable · no phone-speaker fallback") { picker = "wearable" }
            }
            "device", "wearable" -> {
                if (labels.devices.isEmpty()) Text(uiText("No saved devices. Add a device in Devices first."))
                labels.devices.forEach { (id, name) -> PickerItem(name,
                    if (id in readiness.connectedDevices) "Connected" else "Currently unavailable",
                    enabled = selected != "wearable" || id in readiness.speakerDevices) {
                    if (selected == "wearable") routine = routine.copy(delivery = RoutineDeliveryPolicy.WearableSpeech(id))
                    else if (deviceTarget != null) replaceCondition(requireNotNull(deviceTarget), RoutineCondition.DeviceAvailable(id))
                    else routine = routine.copy(trigger = when (routine.trigger) {
                        is RoutineTrigger.DeviceDisconnected -> RoutineTrigger.DeviceDisconnected(id)
                        else -> RoutineTrigger.DeviceConnected(id)
                    })
                    picker = null
                } }
            }
            "provider" -> labels.providers.forEach { (id, title) -> PickerItem(title) { replaceCondition(requireNotNull(deviceTarget), RoutineCondition.ProviderAvailable(id)); picker = null } }
            "app" -> AppPicker { pkg, title -> chosenApps = chosenApps + (pkg to title); routine = routine.copy(trigger = when (val trigger = routine.trigger) {
                is RoutineTrigger.NotificationFromApp -> trigger.copy(packageName = pkg)
                else -> RoutineTrigger.AppStarted(pkg)
            }); picker = null }
            "zone" -> {
                PickerItem("System · ${ZoneId.systemDefault().id}") { routine = routine.copy(trigger = (routine.trigger as RoutineTrigger.AtTime).copy(zoneId = ZoneId.systemDefault())); picker = null }
                HaloTextField(zoneQuery, { zoneQuery = it }, label = { Text(uiText("Find a time zone")) }, kind = VoiceFieldKind.SEARCH)
                ZoneId.getAvailableZoneIds().sorted().filter { it.contains(zoneQuery, ignoreCase = true) }.forEach { id ->
                    PickerItem(id) { routine = routine.copy(trigger = (routine.trigger as RoutineTrigger.AtTime).copy(zoneId = ZoneId.of(id))); picker = null }
                }
            }
        }
    } }
}

@Composable
private fun PickerItem(title: String, subtitle: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(uiText(title)) }, supportingContent = subtitle?.let { { Text(uiText(it)) } }, modifier = Modifier.clickable(enabled = enabled, onClick = onClick))
}

@Composable
private fun AppPicker(onSelect: (String, String) -> Unit) {
    val context = LocalContext.current
    val resolver = remember { AndroidApplicationResolver(context) }
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppCandidate>>(emptyList()) }
    LaunchedEffect(query) { apps = resolver.search(query, includeSystem = true) }
    HaloTextField(query, { query = it.take(80) }, label = { Text(uiText("Find installed app")) }, kind = VoiceFieldKind.SEARCH, maxChars = 80)
    apps.forEach { candidate -> PickerItem(candidate.app.label) { onSelect(candidate.app.packageName, candidate.app.label) } }
}

@Composable
private fun BatteryChoice(percent: Int, onChange: (Int) -> Unit) {
    Text("$percent%", style = MaterialTheme.typography.titleMedium)
    Slider(percent.toFloat(), { onChange(it.toInt()) }, valueRange = 1f..100f, steps = 98)
}

@Composable
private fun TimeChoice(time: LocalTime, onChange: (LocalTime) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = { TimePickerDialog(context, { _, hour, minute -> onChange(LocalTime.of(hour, minute)) }, time.hour, time.minute, android.text.format.DateFormat.is24HourFormat(context)).show() }) { Text("Time · ${time.toString().take(5)}") }
}

@Composable
private fun DateChoice(date: LocalDate, onChange: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = { DatePickerDialog(context, { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day)) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }) { Text("Date · $date") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChoices(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: java.util.Locale.ENGLISH
    val everyDay = days.isEmpty() || days.size == 7
    var custom by rememberSaveable { mutableStateOf(!everyDay) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(everyDay && !custom, { custom = false; onChange(emptySet()) }, label = { Text(uiText("Every day")) })
        FilterChip(days == DayOfWeek.entries.take(5).toSet(), { custom = true; onChange(DayOfWeek.entries.take(5).toSet()) }, label = { Text(uiText("Weekdays")) })
        FilterChip(days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), { custom = true; onChange(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) }, label = { Text(uiText("Weekends")) })
        FilterChip(custom, { custom = true; if (days.isEmpty()) onChange(DayOfWeek.entries.toSet()) }, label = { Text(uiText("Custom")) })
    }
    if (custom) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { day -> FilterChip(day in days, {
            val changed = if (day in days) days - day else days + day
            if (changed.isNotEmpty()) onChange(changed)
        }, label = { Text(day.getDisplayName(java.time.format.TextStyle.NARROW, locale)) }) }
    }
}

@Composable
private fun PresetSeconds(title: String, seconds: Long, presets: List<Long>, max: Long, onValidity: (Boolean) -> Unit = {}, onChange: (Long) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(seconds !in presets) }
    var customInput by rememberSaveable(custom, seconds) { mutableStateOf(seconds.toString()) }
    val valid = !custom || customInput.toLongOrNull()?.let { it in 0..max } == true
    SideEffect { onValidity(valid) }
    TextButton(onClick = { picking = true }) { Text(uiText("%1\$s · %2\$s", uiText(title), if (seconds == 0L && title.startsWith("Group")) uiText("Off") else uiInterval(seconds))) }
    if (custom) HaloTextField(customInput, { value ->
        customInput = value.filter(Char::isDigit).take(6)
        customInput.toLongOrNull()?.takeIf { it in 0..max }?.let(onChange)
    }, label = { Text(uiText("Custom seconds")) }, isError = !valid, supportingText = { Text("0 to $max seconds") }, kind = VoiceFieldKind.NUMBER)
    if (picking) AutomationPicker(title, { picking = false }) {
        presets.forEach { value -> PickerItem(if (value == 0L && title.startsWith("Group")) "Off" else humanInterval(value)) { onChange(value); custom = false; picking = false } }
        PickerItem("Custom") { custom = true; picking = false }
    }
}

@Composable
private fun DigestWindow(minutes: Int, onValidity: (Boolean) -> Unit = {}, onChange: (Int) -> Unit) {
    var picker by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(minutes !in listOf(60, 180, 480, 1440)) }
    var customInput by rememberSaveable(custom, minutes) { mutableStateOf(minutes.toString()) }
    val valid = !custom || customInput.toIntOrNull()?.let { it in 15..1440 } == true
    SideEffect { onValidity(valid) }
    TextButton(onClick = { picker = true }) { Text("Window · ${if (minutes % 60 == 0) "${minutes / 60} hours" else "$minutes minutes"}") }
    if (custom) HaloTextField(customInput, { value ->
        customInput = value.filter(Char::isDigit).take(4)
        customInput.toIntOrNull()?.takeIf { it in 15..1440 }?.let(onChange)
    }, label = { Text(uiText("Custom minutes · 15 to 1440")) }, isError = !valid, kind = VoiceFieldKind.NUMBER)
    if (picker) AutomationPicker("Digest window", { picker = false }) {
        listOf(60, 180, 480, 1440).forEach { value -> PickerItem("${value / 60} hour${if (value == 60) "" else "s"}") { onChange(value); custom = false; picker = false } }
        PickerItem("Custom window") { custom = true; picker = false }
    }
}
