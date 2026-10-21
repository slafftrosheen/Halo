package com.halo.app.ui.screens.settings

import com.halo.runtime.cognition.routine.*
import com.halo.app.automation.RoutineArguments
import com.halo.runtime.cognition.watch.*
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class RoutineUiStatus(val label: String) {
    ACTIVE("On"), PAUSED("Off"), NEEDS_REVIEW("Review required"), NEEDS_SETUP("Setup required"), BLOCKED("Can’t run")
}

data class AutomationLabels(
    val devices: Map<String, String> = emptyMap(),
    val apps: Map<String, String> = emptyMap(),
    val providers: Map<String, String> = emptyMap(),
) {
    fun device(id: String) = devices[id] ?: if (id.isBlank()) "Choose a device" else "Configured device · currently unavailable"
    fun app(id: String) = apps[id] ?: if (id.isBlank()) "Choose an app" else "Configured app · currently unavailable"
    fun provider(id: String) = providers[id] ?: "Configured provider · currently unavailable"
}

fun routineUiStatus(routine: Routine, needsSetup: Boolean = false, blocked: Boolean = false): RoutineUiStatus = when {
    routine.grantDigest == null || !runCatching { routine.grantDigest == RoutineArguments.grant(routine) }.getOrDefault(false) -> RoutineUiStatus.NEEDS_REVIEW
    needsSetup -> RoutineUiStatus.NEEDS_SETUP
    blocked -> RoutineUiStatus.BLOCKED
    !routine.enabled -> RoutineUiStatus.PAUSED
    else -> RoutineUiStatus.ACTIVE
}

internal fun routineCanRun(status: RoutineUiStatus, running: Boolean): Boolean =
    !running && status in setOf(RoutineUiStatus.ACTIVE, RoutineUiStatus.PAUSED)

// An enabled routine must remain pausable even when its setup or grant becomes invalid.
internal fun routineCanToggle(enabled: Boolean, status: RoutineUiStatus, running: Boolean): Boolean =
    enabled || routineCanRun(status, running)

/** Runtime details remain in their owner; never render arbitrary exception text in everyday UI. */
internal fun routineErrorLabel(reason: String): String = when (reason) {
    "Choose a future time", "Enter a message with 1 to 240 characters",
    "Enter a name of at most 120 characters", "A routine requires 1–10 actions",
    "Choose a device", "Set up Phone screen access before saving",
    "Set up Notification access before saving" -> reason
    "Open and save this automation to approve its actions", "Review this automation before running it",
    "Save and review this automation first" -> "Review this routine before turning it on or running it."
    else -> "Could not complete this change. Review the routine and try again."
}

internal fun routineRunExplanation(state: RoutineRunState): String? = when (state) {
    RoutineRunState.FAILED -> "This run failed. Review the routine before trying again."
    RoutineRunState.BLOCKED -> "This run could not start. Check the routine’s setup."
    RoutineRunState.PARTIAL -> "Some actions completed. Review Activity before running this again."
    RoutineRunState.INTERRUPTED -> "Completion is not confirmed. Check Activity before trying again."
    RoutineRunState.SKIPPED -> "The conditions were not met. No actions ran."
    else -> null
}

/** Drafts have no authority: only the reviewed save operation can persist and grant them. */
data class RoutineDraft(val routine: Routine, val originalId: RoutineId? = null) {
    companion object {
        fun duplicate(original: Routine, now: Instant = Instant.now()) = RoutineDraft(original.copy(
            id = RoutineId(UUID.randomUUID().toString()), name = (original.name.take(115) + " copy"),
            enabled = false, grantDigest = null, lastRunAt = null, nextRunAt = null,
            createdAt = now, updatedAt = now))
    }
}

enum class RoutineTemplate(val title: String, val description: String) {
    BATTERY("Battery low alert", "Tell me when battery falls below 20%"),
    NETWORK("Network is back", "Tell me when a usable connection returns"),
    DEVICE("Device connected", "Choose a device and receive an update"),
    DIGEST("Daily notification digest", "An evening summary of the last 8 hours");

    fun buildDraft(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): RoutineDraft {
        val trigger = when (this) {
            BATTERY -> RoutineTrigger.BatteryCrossedBelow(20)
            NETWORK -> RoutineTrigger.NetworkAvailable
            DEVICE -> RoutineTrigger.DeviceConnected("")
            DIGEST -> RoutineTrigger.AtTime(LocalTime.of(18, 0), zoneId = zone)
        }
        val name = when (this) { BATTERY -> "Battery low"; DIGEST -> "Evening digest"; else -> title }
        val action = when (this) {
            DIGEST -> RoutineAction("halo_notification_digest", "{\"window_minutes\":480}")
            else -> messageAction(when (this) {
                BATTERY -> "Battery is below 20%"
                NETWORK -> "Network connection is available again"
                else -> "Your device is connected"
            })
        }
        return RoutineDraft(Routine(RoutineId(UUID.randomUUID().toString()), name, false, trigger,
            actions = listOf(action), createdAt = now, updatedAt = now, delivery = RoutineDeliveryPolicy.AndroidNotification))
    }
}

fun messageAction(text: String) = RoutineAction("halo_routine_message", JSONObject().put("text", text).toString())
fun actionArguments(action: RoutineAction): JSONObject = runCatching { JSONObject(action.argumentsJson) }.getOrElse { JSONObject() }
fun RoutineAction.humanLabel(): String = when (toolId) {
    "halo_routine_message" -> "Show \"${actionArguments(this).optString("text", "")}\""
    "halo_notification_digest" -> {
        val minutes = actionArguments(this).optInt("window_minutes", 60)
        "Notification digest · last ${if (minutes % 60 == 0) "${minutes / 60} hour${if (minutes == 60) "" else "s"}" else "$minutes minutes"}"
    }
    else -> "Additional approved action"
}

fun RoutineTrigger.humanLabel(labels: AutomationLabels = AutomationLabels()): String = when (this) {
    is RoutineTrigger.AtTime -> {
        val repeat = when {
            days.isEmpty() || days.size == 7 -> "Every day"
            days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Weekends"
            days == DayOfWeek.entries.take(5).toSet() -> "Weekdays"
            else -> days.sorted().joinToString(", ") { it.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }
        }
        "$repeat · ${localTime.format(DateTimeFormatter.ofPattern("HH:mm"))} · ${zoneId.id}"
    }
    is RoutineTrigger.Once -> "Once · ${humanTime(at)}"
    is RoutineTrigger.BatteryCrossedAbove -> "Battery rises above $percent%"
    is RoutineTrigger.BatteryCrossedBelow -> "Battery falls below $percent%"
    RoutineTrigger.ChargingStarted -> "Charging starts"
    RoutineTrigger.ChargingStopped -> "Charging stops"
    RoutineTrigger.NetworkAvailable -> "Network becomes available"
    RoutineTrigger.NetworkLost -> "Network is lost"
    is RoutineTrigger.AppStarted -> "${labels.app(appPackage)} opens"
    is RoutineTrigger.NotificationFromApp -> "Notification from ${labels.app(packageName)}"
    is RoutineTrigger.DeviceConnected -> "${labels.device(deviceId)} connects"
    is RoutineTrigger.DeviceDisconnected -> "${labels.device(deviceId)} disconnects"
}

fun RoutineCondition.humanLabel(labels: AutomationLabels = AutomationLabels()): String = when (this) {
    is RoutineCondition.BatteryAbove -> "Battery above $percent%"
    is RoutineCondition.NetworkAvailable -> if (required) "Network available" else "Network unavailable"
    is RoutineCondition.DeviceAvailable -> "${labels.device(deviceId)} available"
    is RoutineCondition.ProviderAvailable -> "${labels.provider(providerId)} available"
}

fun RoutineDeliveryPolicy.humanLabel(labels: AutomationLabels = AutomationLabels()): String = when (this) {
    RoutineDeliveryPolicy.SilentActivity -> "Silent · keep result in Activity"
    RoutineDeliveryPolicy.AndroidNotification -> "Android notification"
    RoutineDeliveryPolicy.InAppWhenForeground -> "In Halo · show when Halo is open"
    is RoutineDeliveryPolicy.WearableSpeech -> "${labels.device(deviceId)} · no phone-speaker fallback"
}

fun RoutineRunState.humanLabel(): String = when (this) {
    RoutineRunState.SCHEDULED -> "Scheduled"
    RoutineRunState.RUNNING -> "Running"
    RoutineRunState.COMPLETED -> "Completed"
    RoutineRunState.SKIPPED -> "Conditions not met"
    RoutineRunState.BLOCKED -> "Blocked"
    RoutineRunState.PARTIAL -> "Partially completed"
    RoutineRunState.FAILED -> "Failed"
    RoutineRunState.INTERRUPTED -> "Completion uncertain"
    RoutineRunState.CANCELLED -> "Cancelled"
}

fun deliveryResultLabel(outcome: String?): String = when (outcome) {
    "TRIAL_RECORDED" -> "Trial recorded in History"
    "DISPATCHED" -> "Notification dispatched"
    "PLAYBACK_COMPLETED" -> "Speech playback completed"
    "DELIVERED" -> "Delivered in Halo"
    "SURFACED_SILENTLY" -> "Kept in Activity"
    "QUEUED" -> "Delivery queued"
    "SUPPRESSED" -> "Delivery suppressed"
    "EXPIRED" -> "Delivery expired"
    "FAILED" -> "Delivery failed"
    null -> "Delivery pending"
    else -> "Delivery not confirmed"
}

fun humanTime(at: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val local = at.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val day = when (local.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> local.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
    }
    return "$day at ${local.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

fun Routine.nextRunLabel(): String = if (!enabled) "No runs while off" else nextRunAt?.let { "Next · ${humanTime(it)}" } ?: when (trigger) {
    is RoutineTrigger.AtTime, is RoutineTrigger.Once -> if (enabled) "Schedule pending" else "No scheduled run while off"
    is RoutineTrigger.BatteryCrossedAbove, is RoutineTrigger.BatteryCrossedBelow -> "Waiting for battery threshold"
    RoutineTrigger.NetworkAvailable, RoutineTrigger.NetworkLost -> "Waiting for network change"
    is RoutineTrigger.DeviceConnected, is RoutineTrigger.DeviceDisconnected -> "Waiting for device"
    is RoutineTrigger.AppStarted, is RoutineTrigger.NotificationFromApp -> "Waiting for app"
    else -> "Event driven"
}

fun WatchStatus.humanLabel(): String = when (this) {
    WatchStatus.ACTIVE -> "Waiting"
    WatchStatus.MATCH_CLAIMED -> "Finishing"
    WatchStatus.SATISFIED -> "Completed"
    WatchStatus.EXPIRED -> "Expired"
    WatchStatus.CANCELLED -> "Cancelled"
    WatchStatus.FAILED -> "Failed"
}

fun WatchCondition.humanLabel(labels: AutomationLabels = AutomationLabels()): String = when (this) {
    WatchCondition.NetworkAvailable -> "Internet is available"
    WatchCondition.NetworkLost -> "Internet connection is lost"
    is WatchCondition.BatteryBelow -> "Battery below $percent%"
    is WatchCondition.BatteryAbove -> "Battery above $percent%"
    is WatchCondition.DeviceConnected -> "${labels.device(deviceId)} connects"
    is WatchCondition.DeviceDisconnected -> "${labels.device(deviceId)} disconnects"
    is WatchCondition.AppForegrounded -> "${labels.app(packageName)} opens"
    is WatchCondition.NotificationFromApp -> "Notification from ${labels.app(packageName)}"
    is WatchCondition.ProviderAvailable -> "${labels.provider(providerId)} becomes available"
    is WatchCondition.CapabilityAvailable -> "Required capability becomes available"
}

fun WatchBehavior.humanLabel(): String = when (this) {
    is WatchBehavior.Notify -> "Notify me"
    is WatchBehavior.MakeRunResumable -> if (wakePolicy == ContinuationWakePolicy.SURFACE_TO_USER) "Bring the task back to my attention" else "Make the task available to continue"
    is WatchBehavior.ReconcileRun -> "Check the linked task"
}

fun humanInterval(seconds: Long): String = when {
    seconds == 0L -> "No extra limit"
    seconds == 3600L -> "1 hour"
    seconds % 60 == 0L -> "${seconds / 60} minute${if (seconds == 60L) "" else "s"}"
    else -> "$seconds seconds"
}

fun ContinuationWatch.linkedRunId() = runId ?: when (val action = behavior) {
    is WatchBehavior.MakeRunResumable -> action.runId
    is WatchBehavior.ReconcileRun -> action.runId
    is WatchBehavior.Notify -> null
}
