package com.halo.runtime.cognition.routine

fun RoutineTrigger.summary(): String = when(this) {
    is RoutineTrigger.AtTime -> "${if(days.isEmpty()) "Every day" else days.sorted().joinToString { it.name.lowercase().replaceFirstChar(Char::uppercase) }} · $localTime · ${zoneId.id}"
    is RoutineTrigger.Once -> "Once at $at"
    is RoutineTrigger.DeviceConnected -> "When my device connects"
    is RoutineTrigger.DeviceDisconnected -> "When my device disconnects"
    is RoutineTrigger.AppStarted -> "When app opens"
    is RoutineTrigger.NotificationFromApp -> "When an app notification arrives"
    is RoutineTrigger.BatteryCrossedAbove -> "Battery rises above $percent%"
    is RoutineTrigger.BatteryCrossedBelow -> "Battery falls below $percent%"
    RoutineTrigger.ChargingStarted -> "Charging starts"
    RoutineTrigger.ChargingStopped -> "Charging stops"
    RoutineTrigger.NetworkAvailable -> "Network becomes available"
    RoutineTrigger.NetworkLost -> "Network is lost"
}
fun RoutineDeliveryPolicy.label(): String = when(this) {
    RoutineDeliveryPolicy.SilentActivity -> "Silent"
    RoutineDeliveryPolicy.AndroidNotification -> "Notification"
    RoutineDeliveryPolicy.InAppWhenForeground -> "When Halo is open"
    is RoutineDeliveryPolicy.WearableSpeech -> "Spoken on configured wearable"
}

fun routineGrantSummary(r: Routine, actionTitles: List<String> = r.actions.map { "Approved registered action" }): String =
    "When: ${r.trigger.summary()}${when(val t = r.trigger) {
        is RoutineTrigger.AppStarted -> " · ${t.appPackage}"
        is RoutineTrigger.NotificationFromApp -> " · ${t.packageName} · group for ${t.coalesceSeconds}s"
        is RoutineTrigger.DeviceConnected -> " · ${t.deviceId}"
        is RoutineTrigger.DeviceDisconnected -> " · ${t.deviceId}"
        else -> ""
    }}\nIf: ${if(r.conditions.isEmpty()) "No additional conditions" else r.conditions.joinToString { c -> when(c) {
        is RoutineCondition.BatteryAbove -> "Battery above ${c.percent}%"
        is RoutineCondition.NetworkAvailable -> if(c.required) "Network available" else "Network unavailable"
        is RoutineCondition.DeviceAvailable -> "Device ${c.deviceId} available"
        is RoutineCondition.ProviderAvailable -> "Provider ${c.providerId} available"
    } }}\nThen: " + actionTitles.take(10).joinToString("; ") { it.take(120) } +
    "\nDelivery: ${r.delivery.label()}${(r.delivery as? RoutineDeliveryPolicy.WearableSpeech)?.let { " · ${it.deviceId}" }.orEmpty()}\nMinimum event interval: ${r.minimumIntervalSeconds}s\n${r.trigger.availabilityLabel()}\nThese actions may run without asking each time. No other actions are granted."
