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
                .put("zone", t.zoneId.id).put("days", JSONArray(t.days.map { it.name }))
            is RoutineTrigger.DeviceConnected -> put("type", "connected").put("device", t.deviceId)
            is RoutineTrigger.DeviceDisconnected -> put("type", "disconnected").put("device", t.deviceId)
            else -> error("Unsupported trigger")
        }
    }.toString()
    fun decodeTrigger(json: String, version: Int): RoutineTrigger {
        checkVersion(version)
        val j = JSONObject(json)
        return when (j.getString("type")) {
            "once" -> RoutineTrigger.Once(Instant.parse(j.getString("at")))
            "time" -> RoutineTrigger.AtTime(LocalTime.parse(j.getString("time")),
                j.getJSONArray("days").strings().map { DayOfWeek.valueOf(it) }.toSet(), ZoneId.of(j.getString("zone")))
            "connected" -> RoutineTrigger.DeviceConnected(j.getString("device"))
            "disconnected" -> RoutineTrigger.DeviceDisconnected(j.getString("device"))
            else -> error("Unsupported trigger")
        }
    }
    fun encodeConditions(items: List<RoutineCondition>) = JSONArray(items.map { c -> JSONObject().apply {
        when (c) {
            is RoutineCondition.DeviceAvailable -> put("type", "device").put("id", c.deviceId)
            is RoutineCondition.NetworkAvailable -> put("type", "network").put("required", c.required)
            is RoutineCondition.ProviderAvailable -> put("type", "provider").put("id", c.providerId)
            else -> error("Battery condition has no supported target")
        }
    } }).toString()
    fun decodeConditions(json: String, version: Int): List<RoutineCondition> {
        checkVersion(version)
        val a = JSONArray(json)
        require(a.length() <= 10)
        return (0 until a.length()).map { val j = a.getJSONObject(it); when (j.getString("type")) {
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
        val a = JSONArray(json)
        require(a.length() in 1..10) { "A routine requires 1–10 actions" }
        return (0 until a.length()).map { val j = a.getJSONObject(it)
            RoutineAction(j.getString("tool"), j.getJSONObject("arguments").toString()) }
    }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
