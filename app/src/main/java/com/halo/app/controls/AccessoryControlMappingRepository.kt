package com.halo.app.controls

import com.halo.sdk.host.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Versioned, bounded preferences. Connection-only keys never cross a process lifetime. */
class AccessoryControlMappingRepository(private val storage: Storage) {
    private val durable = mutableMapOf<String, List<ControlBinding>>()
    private val session = mutableMapOf<String, List<ControlBinding>>()
    private val classes = mutableMapOf<String, List<ControlBinding>>()
    private val revision = MutableStateFlow(0)
    val changes = revision.asStateFlow()
    var malformed: Boolean = false
        private set
    init { load() }
    @Synchronized fun device(endpoint: ControlEndpoint): List<ControlBinding> =
        if (endpoint.durable) durable[endpoint.id].orEmpty() else session["${endpoint.id}|${endpoint.epoch}"].orEmpty()
    @Synchronized fun defaults(deviceClass: String) = classes[deviceClass].orEmpty()
    @Synchronized fun count() = (durable.values + session.values + classes.values).sumOf { it.size }
    @Synchronized fun save(endpoint: ControlEndpoint, binding: ControlBinding, classDefault: Boolean = false): Boolean {
        if (!endpoint.connected || (binding.control to binding.gesture) !in endpoint.supported ||
            binding.control in setOf(AccessoryControl.VOLUME_UP, AccessoryControl.VOLUME_DOWN) ||
            binding.selection.action !in endpoint.availableActions || !validSelection(binding.selection)) return false
        val map = if (classDefault) classes else if (endpoint.durable) durable else session
        val key = if (classDefault) endpoint.deviceClass else if (endpoint.durable) endpoint.id else "${endpoint.id}|${endpoint.epoch}"
        if (map.size >= 64 && key !in map) return false
        map[key] = map[key].orEmpty().filterNot { it.control == binding.control && it.gesture == binding.gesture } + binding
        persist(); return true
    }
    @Synchronized fun reset(endpoint: ControlEndpoint) {
        durable.remove(endpoint.id); session.remove("${endpoint.id}|${endpoint.epoch}"); persist()
    }
    @Synchronized fun resetAll() { durable.clear(); session.clear(); classes.clear(); malformed = false; persist() }
    @Synchronized fun disconnect(endpoint: ControlEndpoint) { session.remove("${endpoint.id}|${endpoint.epoch}"); revision.value++ }
    private fun validSelection(s: AccessoryActionSelection) =
        if (s.action == AccessoryAction.ReviewRoutine) s.routineId?.matches(Regex("[A-Za-z0-9_-]{1,160}")) == true else s.routineId == null
    private fun load() {
        val raw = storage.getString(KEY) ?: return
        try {
            require(raw.length <= 64_000)
            val root = JSONObject(raw); require(root.get("version") == 1)
            fun read(name: String, target: MutableMap<String, List<ControlBinding>>) {
                val rows = root.getJSONArray(name); require(rows.length() <= 64)
                repeat(rows.length()) { i ->
                    val row = rows.getJSONObject(i); val key = row.getString("key")
                    require(key.matches(Regex("[A-Za-z0-9:_-]{1,200}")) && key !in target)
                    val bindings = row.getJSONArray("bindings"); require(bindings.length() <= 40)
                    val list = (0 until bindings.length()).map { j ->
                        val b = bindings.getJSONObject(j)
                        val selection = AccessoryActionSelection(AccessoryAction.valueOf(b.getString("action")),
                            if (b.has("routineId")) b.getString("routineId") else null)
                        require(validSelection(selection))
                        ControlBinding(AccessoryControl.valueOf(b.getString("control")), AccessoryControlAction.valueOf(b.getString("gesture")), selection)
                    }
                    require(list.map { it.control to it.gesture }.distinct().size == list.size)
                    target[key] = list
                }
            }
            read("devices", durable); read("classes", classes)
        } catch (_: Exception) { durable.clear(); classes.clear(); malformed = true }
    }
    private fun persist() {
        fun rows(map: Map<String, List<ControlBinding>>) = JSONArray().apply {
            map.forEach { (key, bindings) -> put(JSONObject().put("key", key).put("bindings", JSONArray().apply {
                bindings.forEach { b -> put(JSONObject().put("control", b.control.name).put("gesture", b.gesture.name)
                    .put("action", b.selection.action.name).apply { b.selection.routineId?.let { put("routineId", it) } }) }
            })) }
        }
        storage.putString(KEY, JSONObject().put("version", 1).put("devices", rows(durable)).put("classes", rows(classes)).toString())
        revision.value++
    }
    companion object { const val KEY = "accessory_control_mappings_v1" }
}
