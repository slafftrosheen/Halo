package com.halo.app.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.halo.app.language.uiText
import com.halo.runtime.cognition.routine.*
import com.halo.runtime.cognition.watch.*
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Translate only new template seeds; existing names and messages remain user content. */
@Composable internal fun RoutineTemplate.localizedDraft(): RoutineDraft {
    val seed = androidx.compose.runtime.remember(this) { buildDraft() }
    val name = uiText(seed.routine.name)
    val actions = if (this == RoutineTemplate.DIGEST) seed.routine.actions else {
        val message = uiText(actionArguments(seed.routine.actions.single()).optString("text", ""))
        listOf(messageAction(message))
    }
    return seed.copy(routine = seed.routine.copy(name = name, actions = actions))
}

@Composable internal fun uiRoutineTime(at: java.time.Instant): String {
    val locale = LocalResources.current.configuration.locales[0]
    return java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, locale
    ).format(java.util.Date.from(at))
}

@Composable internal fun Routine.uiNextRunLabel(): String = when {
    !enabled -> uiText("No runs while off")
    nextRunAt != null -> uiText("Next · %1\$s", uiRoutineTime(requireNotNull(nextRunAt)))
    else -> uiText(nextRunLabel())
}

/** Translate Halo's wording; keep user messages, app names and device names as source data. */
@Composable internal fun RoutineTrigger.uiLabel(labels: AutomationLabels): String = when (this) {
    is RoutineTrigger.AtTime -> {
        val locale = LocalResources.current.configuration.locales[0]
        val repeat = when {
            days.isEmpty() || days.size == 7 -> uiText("Every day")
            days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> uiText("Weekends")
            days == DayOfWeek.entries.take(5).toSet() -> uiText("Weekdays")
            else -> days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
        }
        "$repeat · ${localTime.format(DateTimeFormatter.ofPattern("HH:mm"))} · ${zoneId.id}"
    }
    is RoutineTrigger.Once -> uiText("Once · %1\$s", java.text.DateFormat.getDateTimeInstance().format(java.util.Date.from(at)))
    is RoutineTrigger.BatteryCrossedAbove -> uiText("Battery rises above %1\$d%%", percent)
    is RoutineTrigger.BatteryCrossedBelow -> uiText("Battery falls below %1\$d%%", percent)
    is RoutineTrigger.AppStarted -> uiText("%1\$s opens", labels.apps[appPackage] ?: uiText("Choose an app"))
    is RoutineTrigger.NotificationFromApp -> uiText("Notification from %1\$s", labels.apps[packageName] ?: uiText("Choose an app"))
    is RoutineTrigger.DeviceConnected -> uiText("%1\$s connects", labels.devices[deviceId] ?: uiText("Choose a device"))
    is RoutineTrigger.DeviceDisconnected -> uiText("%1\$s disconnects", labels.devices[deviceId] ?: uiText("Choose a device"))
    else -> uiText(humanLabel(labels))
}

@Composable internal fun RoutineCondition.uiLabel(labels: AutomationLabels): String = when (this) {
    is RoutineCondition.BatteryAbove -> uiText("Battery above %1\$d%%", percent)
    is RoutineCondition.NetworkAvailable -> uiText(if (required) "Network available" else "Network unavailable")
    is RoutineCondition.DeviceAvailable -> uiText("%1\$s available", labels.devices[deviceId] ?: uiText("Choose a device"))
    is RoutineCondition.ProviderAvailable -> uiText("%1\$s available", labels.providers[providerId] ?: uiText("Provider unavailable"))
}

@Composable internal fun RoutineDeliveryPolicy.uiLabel(labels: AutomationLabels): String = when (this) {
    is RoutineDeliveryPolicy.WearableSpeech -> uiText("%1\$s · no phone-speaker fallback", labels.devices[deviceId] ?: uiText("Choose a device"))
    else -> uiText(humanLabel(labels))
}

@Composable internal fun RoutineAction.uiLabel(): String = when (toolId) {
    "halo_routine_message" -> uiText("Show “%1\$s”", actionArguments(this).optString("text", ""))
    "halo_notification_digest" -> uiText("Notification digest · last %1\$d minutes", actionArguments(this).optInt("window_minutes", 60))
    else -> uiText("Additional approved action")
}

@Composable internal fun uiInterval(seconds: Long): String = when {
    seconds == 0L -> uiText("No extra limit")
    seconds % 60 == 0L -> uiText("%1\$d minutes", seconds / 60)
    else -> uiText("%1\$d seconds", seconds)
}

@Composable internal fun WatchCondition.uiLabel(labels: AutomationLabels): String = when(this) {
    is WatchCondition.BatteryBelow -> uiText("Battery below %1\$d%%", percent)
    is WatchCondition.BatteryAbove -> uiText("Battery above %1\$d%%", percent)
    is WatchCondition.DeviceConnected -> uiText("%1\$s connects", labels.devices[deviceId] ?: uiText("Choose a device"))
    is WatchCondition.DeviceDisconnected -> uiText("%1\$s disconnects", labels.devices[deviceId] ?: uiText("Choose a device"))
    is WatchCondition.AppForegrounded -> uiText("%1\$s opens", labels.apps[packageName] ?: uiText("Choose an app"))
    is WatchCondition.NotificationFromApp -> uiText("Notification from %1\$s", labels.apps[packageName] ?: uiText("Choose an app"))
    is WatchCondition.ProviderAvailable -> uiText("%1\$s becomes available", labels.providers[providerId] ?: uiText("Provider unavailable"))
    else -> uiText(humanLabel(labels))
}
