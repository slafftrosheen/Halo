package com.halo.runtime.cognition.routine

import com.halo.runtime.cognition.attention.HaloUserMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
import java.time.Instant
import java.util.UUID

enum class AutomationSignalKind { DEVICE_CONNECTED, DEVICE_DISCONNECTED, BATTERY_CHANGED, CHARGING_STARTED, CHARGING_STOPPED, NETWORK_AVAILABLE, NETWORK_LOST, APP_FOREGROUNDED, NOTIFICATION_POSTED, SYSTEM_TIME_CHANGED, PROVIDER_STATE_CHANGED, CAPABILITY_STATE_CHANGED }
enum class AutomationTrust { TRUSTED_RUNTIME, PLATFORM_EVENT, UNTRUSTED_EXTERNAL, INTERNAL_TEST }
sealed interface AutomationValue {
    data class Text(val value: String) : AutomationValue { init { require(value.length <= 256) } }
    data class Integer(val value: Long) : AutomationValue
    data class Flag(val value: Boolean) : AutomationValue
}
data class AutomationSignal(val id: String, val kind: AutomationSignalKind, val occurredAt: Instant,
    val source: String, val trust: AutomationTrust, val attributes: Map<String, AutomationValue> = emptyMap()) {
    init { require(id.length in 1..160 && source.length in 1..80 && attributes.size <= 8) }
    fun text(key: String) = (attributes[key] as? AutomationValue.Text)?.value
    fun number(key: String) = (attributes[key] as? AutomationValue.Integer)?.value
}
enum class AutomationSourceStatus { AVAILABLE, REQUIRES_PERMISSION, REQUIRES_SERVICE, RUNTIME_ONLY, UNAVAILABLE }
interface AutomationSignalSource { val availability: StateFlow<AutomationSourceStatus>; val signals: Flow<AutomationSignal> }
/** Composition only. Matching and durable claims remain separate from event ingestion. */
class AutomationSignalRegistry(val sources: Map<String, AutomationSignalSource>) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val signals: Flow<AutomationSignal> = merge(*sources.values.map { it.signals }.toTypedArray())
    fun availability() = sources.mapValues { it.value.availability.value }
}
enum class RoutineOccurrenceSource { SCHEDULED, EVENT, MANUAL }
data class RoutineOccurrence(val id: String, val routineId: RoutineId, val occurredAt: Instant,
    val source: RoutineOccurrenceSource, val signalId: String? = null,
    val rootOccurrenceId: String = id, val causedByOccurrenceId: String? = null,
    val causalRoutineIds: Set<String> = emptySet(), val routineRevision: Instant? = null) {
    companion object {
        fun scheduled(r: Routine, at: Instant) = RoutineOccurrence("routine:${r.id.value}:scheduled:$at:${r.updatedAt}", r.id, at, RoutineOccurrenceSource.SCHEDULED, routineRevision = r.updatedAt)
        fun event(r: Routine, s: AutomationSignal) = RoutineOccurrence("routine:${r.id.value}:event:${s.id}:${r.updatedAt}", r.id, s.occurredAt, RoutineOccurrenceSource.EVENT, s.id, routineRevision = r.updatedAt)
        fun manual(r: Routine, at: Instant) = RoutineOccurrence("routine:${r.id.value}:manual:${UUID.randomUUID()}", r.id, at, RoutineOccurrenceSource.MANUAL, routineRevision = r.updatedAt)
    }
}
object RoutineTriggerMatcher {
    fun matches(t: RoutineTrigger, s: AutomationSignal): Boolean = when (t) {
        is RoutineTrigger.DeviceConnected -> s.kind == AutomationSignalKind.DEVICE_CONNECTED && s.text("device") == t.deviceId
        is RoutineTrigger.DeviceDisconnected -> s.kind == AutomationSignalKind.DEVICE_DISCONNECTED && s.text("device") == t.deviceId
        is RoutineTrigger.AppStarted -> s.kind == AutomationSignalKind.APP_FOREGROUNDED && s.text("package") == t.appPackage
        is RoutineTrigger.NotificationFromApp -> s.kind == AutomationSignalKind.NOTIFICATION_POSTED && s.text("package") == t.packageName
        is RoutineTrigger.BatteryCrossedAbove -> s.kind == AutomationSignalKind.BATTERY_CHANGED && s.number("previous")?.let { it <= t.percent } == true && s.number("percent")?.let { it > t.percent } == true
        is RoutineTrigger.BatteryCrossedBelow -> s.kind == AutomationSignalKind.BATTERY_CHANGED && s.number("previous")?.let { it >= t.percent } == true && s.number("percent")?.let { it < t.percent } == true
        RoutineTrigger.ChargingStarted -> s.kind == AutomationSignalKind.CHARGING_STARTED
        RoutineTrigger.ChargingStopped -> s.kind == AutomationSignalKind.CHARGING_STOPPED
        RoutineTrigger.NetworkAvailable -> s.kind == AutomationSignalKind.NETWORK_AVAILABLE
        RoutineTrigger.NetworkLost -> s.kind == AutomationSignalKind.NETWORK_LOST
        else -> false
    }
}
enum class TriggerReachability { PLATFORM_SCHEDULED, RUNTIME_ACTIVE_ONLY, REQUIRES_ACCESSIBILITY, REQUIRES_NOTIFICATION_ACCESS }
fun RoutineTrigger.reachability() = when (this) {
    is RoutineTrigger.AtTime, is RoutineTrigger.Once -> TriggerReachability.PLATFORM_SCHEDULED
    is RoutineTrigger.AppStarted -> TriggerReachability.REQUIRES_ACCESSIBILITY
    is RoutineTrigger.NotificationFromApp -> TriggerReachability.REQUIRES_NOTIFICATION_ACCESS
    else -> TriggerReachability.RUNTIME_ACTIVE_ONLY
}
fun RoutineTrigger.availabilityLabel() = when (reachability()) {
    TriggerReachability.PLATFORM_SCHEDULED -> "Works after restart; Android may delay execution"
    TriggerReachability.REQUIRES_ACCESSIBILITY -> "Requires Phone screen access"
    TriggerReachability.REQUIRES_NOTIFICATION_ACCESS -> "Requires Notification access"
    TriggerReachability.RUNTIME_ACTIVE_ONLY -> "Works while Halo is running"
}
data class AutomationEnvironmentSnapshot(val capturedAt: Instant, val batteryPercent: Int? = null,
    val charging: Boolean? = null, val networkAvailable: Boolean? = null, val foregroundPackage: String? = null,
    val availableDevices: Set<String> = emptySet(), val availableProviders: Set<String> = emptySet(),
    val userMode: HaloUserMode = HaloUserMode.NORMAL, val screenInteractive: Boolean? = null,
    val thermalLimited: Boolean = false)
enum class ConditionOutcome { PASSED, NOT_MET, UNAVAILABLE }
object RoutineConditionEvaluator {
    fun evaluate(c: RoutineCondition, e: AutomationEnvironmentSnapshot): ConditionOutcome {
        val result: Boolean? = when(c) {
            is RoutineCondition.BatteryAbove -> e.batteryPercent?.let { it > c.percent }
            is RoutineCondition.NetworkAvailable -> e.networkAvailable?.let { it == c.required }
            is RoutineCondition.DeviceAvailable -> c.deviceId in e.availableDevices
            is RoutineCondition.ProviderAvailable -> c.providerId in e.availableProviders
        }
        return when (result) { true -> ConditionOutcome.PASSED; false -> ConditionOutcome.NOT_MET; null -> ConditionOutcome.UNAVAILABLE }
    }
}
sealed interface RoutineDeliveryPolicy {
    data object SilentActivity : RoutineDeliveryPolicy
    data object AndroidNotification : RoutineDeliveryPolicy
    data object InAppWhenForeground : RoutineDeliveryPolicy
    data class WearableSpeech(val deviceId: String) : RoutineDeliveryPolicy
}
data class RoutineResultSummary(val actionsTotal: Int, val actionsCompleted: Int, val status: RoutineRunState, val summaries: List<String>) {
    fun text() = "$actionsCompleted / $actionsTotal actions · ${status.name.lowercase()}\n" + summaries.take(10).joinToString("\n") { it.take(240) }.take(2400)
}
object BackgroundWorkPolicy {
    fun blockReason(e: AutomationEnvironmentSnapshot, requiresModel: Boolean): String? = when {
        !requiresModel -> null
        e.thermalLimited -> "THERMAL_LIMIT"
        e.batteryPercent?.let { it <= 5 } == true && e.charging != true -> "CRITICAL_BATTERY"
        else -> null
    }
}

/** Cached by consumers when routine configuration changes. */
fun RoutineTrigger.signalKind(): AutomationSignalKind? = when (this) {
    is RoutineTrigger.DeviceConnected -> AutomationSignalKind.DEVICE_CONNECTED
    is RoutineTrigger.DeviceDisconnected -> AutomationSignalKind.DEVICE_DISCONNECTED
    is RoutineTrigger.AppStarted -> AutomationSignalKind.APP_FOREGROUNDED
    is RoutineTrigger.NotificationFromApp -> AutomationSignalKind.NOTIFICATION_POSTED
    is RoutineTrigger.BatteryCrossedAbove, is RoutineTrigger.BatteryCrossedBelow -> AutomationSignalKind.BATTERY_CHANGED
    RoutineTrigger.ChargingStarted -> AutomationSignalKind.CHARGING_STARTED
    RoutineTrigger.ChargingStopped -> AutomationSignalKind.CHARGING_STOPPED
    RoutineTrigger.NetworkAvailable -> AutomationSignalKind.NETWORK_AVAILABLE
    RoutineTrigger.NetworkLost -> AutomationSignalKind.NETWORK_LOST
    else -> null
}
