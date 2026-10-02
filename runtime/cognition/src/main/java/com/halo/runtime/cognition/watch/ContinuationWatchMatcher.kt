package com.halo.runtime.cognition.watch

import com.halo.runtime.cognition.routine.AutomationSignal
import com.halo.runtime.cognition.routine.AutomationSignalKind
import com.halo.runtime.cognition.routine.AutomationValue

object ContinuationWatchMatcher {
    fun matches(condition: WatchCondition, signal: AutomationSignal): Boolean = when (condition) {
        WatchCondition.NetworkAvailable -> signal.kind == AutomationSignalKind.NETWORK_AVAILABLE
        WatchCondition.NetworkLost -> signal.kind == AutomationSignalKind.NETWORK_LOST

        is WatchCondition.BatteryBelow -> {
            signal.kind == AutomationSignalKind.BATTERY_CHANGED &&
                signal.number("previous")?.let { it >= condition.percent } == true &&
                signal.number("percent")?.let { it < condition.percent } == true
        }

        is WatchCondition.BatteryAbove -> {
            signal.kind == AutomationSignalKind.BATTERY_CHANGED &&
                signal.number("previous")?.let { it <= condition.percent } == true &&
                signal.number("percent")?.let { it > condition.percent } == true
        }

        is WatchCondition.DeviceConnected -> {
            signal.kind == AutomationSignalKind.DEVICE_CONNECTED &&
                signal.text("device")?.equals(condition.deviceId, ignoreCase = true) == true
        }

        is WatchCondition.DeviceDisconnected -> {
            signal.kind == AutomationSignalKind.DEVICE_DISCONNECTED &&
                signal.text("device")?.equals(condition.deviceId, ignoreCase = true) == true
        }

        is WatchCondition.AppForegrounded -> {
            signal.kind == AutomationSignalKind.APP_FOREGROUNDED &&
                signal.text("package")?.equals(condition.packageName, ignoreCase = true) == true
        }

        is WatchCondition.NotificationFromApp -> {
            signal.kind == AutomationSignalKind.NOTIFICATION_POSTED &&
                signal.text("package")?.equals(condition.packageName, ignoreCase = true) == true
        }

        is WatchCondition.ProviderAvailable -> {
            signal.kind == AutomationSignalKind.PROVIDER_STATE_CHANGED &&
                signal.text("provider")?.equals(condition.providerId, ignoreCase = true) == true &&
                signal.attributes["available"]?.let { (it as? AutomationValue.Flag)?.value } == true
        }

        is WatchCondition.CapabilityAvailable -> {
            signal.kind == AutomationSignalKind.CAPABILITY_STATE_CHANGED &&
                signal.text("capability")?.equals(condition.capabilityId, ignoreCase = true) == true &&
                signal.attributes["available"]?.let { (it as? AutomationValue.Flag)?.value } == true
        }
    }
}
