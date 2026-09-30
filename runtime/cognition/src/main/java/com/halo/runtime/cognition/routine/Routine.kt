package com.halo.runtime.cognition.routine

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

@JvmInline
value class RoutineId(val value: String)

sealed interface RoutineTrigger {
    data class BatteryCrossedAbove(val percent: Int) : RoutineTrigger
    data class BatteryCrossedBelow(val percent: Int) : RoutineTrigger
    data object ChargingStarted : RoutineTrigger
    data object ChargingStopped : RoutineTrigger
    data object NetworkAvailable : RoutineTrigger
    data object NetworkLost : RoutineTrigger
    data class NotificationFromApp(val packageName: String, val coalesceSeconds: Long = 300) : RoutineTrigger

    data class AtTime(
        val localTime: LocalTime,
        val days: Set<DayOfWeek> = emptySet(),
        val zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()
    ) : RoutineTrigger

    data class Once(
        val at: Instant
    ) : RoutineTrigger

    data class DeviceConnected(
        val deviceId: String
    ) : RoutineTrigger

    data class DeviceDisconnected(
        val deviceId: String
    ) : RoutineTrigger

    data class AppStarted(
        val appPackage: String
    ) : RoutineTrigger
}

sealed interface RoutineCondition {
    data class DeviceAvailable(val deviceId: String) : RoutineCondition
    data class BatteryAbove(val percent: Int) : RoutineCondition
    data class NetworkAvailable(val required: Boolean) : RoutineCondition
    data class ProviderAvailable(val providerId: String) : RoutineCondition
}

data class RoutineAction(
    val toolId: String,
    val argumentsJson: String = "{}"
)

data class Routine(
    val id: RoutineId,
    val name: String,
    val enabled: Boolean = true,
    val trigger: RoutineTrigger,
    val conditions: List<RoutineCondition> = emptyList(),
    val actions: List<RoutineAction> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastRunAt: Instant? = null,
    val nextRunAt: Instant? = null,
    val delivery: RoutineDeliveryPolicy = RoutineDeliveryPolicy.SilentActivity,
    val grantDigest: String? = null,
    val minimumIntervalSeconds: Long = 0
)
