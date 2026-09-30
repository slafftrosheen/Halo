package com.halo.app.data.routine

import com.halo.runtime.cognition.routine.*
import org.json.JSONObject
import org.json.JSONArray
import java.time.*

/** Explicit tagged schema. Unknown data fails closed; no reflection or guessed actions. */
class RoutineCodec {
    private fun checkVersion(version: Int) { require(version == 1) { "Unsupported routine schema $version; edit before enabling" } }
    fun encodeTrigger(t: RoutineTrigger): String = JSONObject().apply {
        when (t) {
            is RoutineTrigger.Once -> put("type", "once").put("at", t.at.toString())
            is RoutineTrigger.AtTime -> put("type", "time").put("time", t.localTime.toString())
                .put("zone", t.zoneId.id).put("days", JSONArray(t.days.map { it.name }.sorted()))
            is RoutineTrigger.DeviceConnected -> put("type", "connected").put("device", t.deviceId)
            is RoutineTrigger.DeviceDisconnected -> put("type", "disconnected").put("device", t.deviceId)
            is RoutineTrigger.AppStarted -> put("type", "app_started").put("package", t.appPackage)
            is RoutineTrigger.NotificationFromApp -> put("type", "notification").put("package", t.packageName).put("coalesce", t.coalesceSeconds)
            is RoutineTrigger.BatteryCrossedAbove -> put("type", "battery_above").put("percent", t.percent)
            is RoutineTrigger.BatteryCrossedBelow -> put("type", "battery_below").put("percent", t.percent)
            RoutineTrigger.ChargingStarted -> put("type", "charging_started")
            RoutineTrigger.ChargingStopped -> put("type", "charging_stopped")
            RoutineTrigger.NetworkAvailable -> put("type", "network_available")
            RoutineTrigger.NetworkLost -> put("type", "network_lost")
        }
    }.toString()
    fun decodeTrigger(json: String, version: Int): RoutineTrigger {
        checkVersion(version)
        com.halo.app.automation.RoutineArguments.decode(json)
        val j = JSONObject(json)
        return when (j.getString("type")) {
            "once" -> RoutineTrigger.Once(Instant.parse(j.getString("at")))
            "time" -> RoutineTrigger.AtTime(LocalTime.parse(j.getString("time")),
                j.getJSONArray("days").strings().map { DayOfWeek.valueOf(it) }.toSet(), ZoneId.of(j.getString("zone")))
            "connected" -> RoutineTrigger.DeviceConnected(j.getString("device"))
            "disconnected" -> RoutineTrigger.DeviceDisconnected(j.getString("device"))
            "app_started" -> RoutineTrigger.AppStarted(j.getString("package"))
            "notification" -> RoutineTrigger.NotificationFromApp(j.getString("package"), j.optLong("coalesce", 300))
            "battery_above" -> RoutineTrigger.BatteryCrossedAbove(j.getInt("percent"))
            "battery_below" -> RoutineTrigger.BatteryCrossedBelow(j.getInt("percent"))
            "charging_started" -> RoutineTrigger.ChargingStarted
            "charging_stopped" -> RoutineTrigger.ChargingStopped
            "network_available" -> RoutineTrigger.NetworkAvailable
            "network_lost" -> RoutineTrigger.NetworkLost
            else -> error("Unsupported trigger")
        }
    }
    fun encodeConditions(items: List<RoutineCondition>) = JSONArray(items.map { c -> JSONObject().apply {
        when (c) {
            is RoutineCondition.DeviceAvailable -> put("type", "device").put("id", c.deviceId)
            is RoutineCondition.NetworkAvailable -> put("type", "network").put("required", c.required)
            is RoutineCondition.ProviderAvailable -> put("type", "provider").put("id", c.providerId)
            is RoutineCondition.BatteryAbove -> put("type", "battery").put("percent", c.percent)
        }
    } }).toString()
    fun decodeConditions(json: String, version: Int): List<RoutineCondition> {
        checkVersion(version)
        com.halo.app.automation.RoutineArguments.decode("{\"items\":$json}")
        val a = JSONArray(json)
        require(a.length() <= 10)
        return (0 until a.length()).map { val j = a.getJSONObject(it); when (j.getString("type")) {
            "battery" -> RoutineCondition.BatteryAbove(j.getInt("percent"))
            "device" -> RoutineCondition.DeviceAvailable(j.getString("id"))
            "network" -> RoutineCondition.NetworkAvailable(j.getBoolean("required"))
            "provider" -> RoutineCondition.ProviderAvailable(j.getString("id"))
            else -> error("Unsupported condition")
        } }
    }
    fun encodeActions(items: List<RoutineAction>) = JSONArray(items.map {
        JSONObject().put("tool", it.toolId).put("arguments", JSONObject(it.argumentsJson))
    }).toString()
    fun decodeActions(json: String, version: Int): List<RoutineAction> {
        checkVersion(version)
        com.halo.app.automation.RoutineArguments.checkBounds(json, 90000, 12)
        val a = JSONArray(json)
        require(a.length() in 1..10) { "A routine requires 1–10 actions" }
        return (0 until a.length()).map { val j = a.getJSONObject(it)
            RoutineAction(j.getString("tool"), j.getJSONObject("arguments").toString()) }
    }
    fun encodeDelivery(d: RoutineDeliveryPolicy): String = JSONObject().apply {
        when(d) {
            RoutineDeliveryPolicy.SilentActivity -> put("type", "silent")
            RoutineDeliveryPolicy.AndroidNotification -> put("type", "notification")
            RoutineDeliveryPolicy.InAppWhenForeground -> put("type", "in_app")
            is RoutineDeliveryPolicy.WearableSpeech -> put("type", "wearable").put("device", d.deviceId)
        }
    }.toString()
    fun decodeDelivery(raw: String): RoutineDeliveryPolicy { com.halo.app.automation.RoutineArguments.decode(raw); val j = JSONObject(raw); return when(j.getString("type")) {
        "silent" -> RoutineDeliveryPolicy.SilentActivity
        "notification" -> RoutineDeliveryPolicy.AndroidNotification
        "in_app" -> RoutineDeliveryPolicy.InAppWhenForeground
        "wearable" -> RoutineDeliveryPolicy.WearableSpeech(j.getString("device"))
        else -> error("Unsupported delivery")
    } }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
