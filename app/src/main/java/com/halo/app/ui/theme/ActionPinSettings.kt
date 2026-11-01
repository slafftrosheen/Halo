package com.halo.app.ui.theme

import org.json.JSONArray
import org.json.JSONObject

/** Stable presentation IDs only: no object identity, arguments, labels or executable commands. */
data class ActionPinSettings(
    val families: Map<String, List<String>> = emptyMap(),
    val unreadable: Boolean = false,
)

internal object ActionPinCodec {
    const val MAX_PER_FAMILY = 4
    private val familyId = Regex("[A-Za-z0-9_-]{1,64}")
    private val actionId = Regex("[A-Za-z0-9_.:-]{1,128}")

    fun encode(families: Map<String, List<String>>): String {
        require(families.size <= 32)
        val json = JSONObject()
        families.forEach { (family, ids) ->
            require(familyId.matches(family))
            require(ids.size <= MAX_PER_FAMILY && ids.distinct().size == ids.size && ids.all(actionId::matches))
            if (ids.isNotEmpty()) json.put(family, JSONArray(ids))
        }
        return json.toString().also { require(it.length <= 20_000) }
    }

    fun decode(raw: String?): ActionPinSettings {
        if (raw == null) return ActionPinSettings()
        require(raw.length <= 20_000)
        val json = JSONObject(raw)
        require(json.length() <= 32)
        val families = linkedMapOf<String, List<String>>()
        json.keys().forEach { family ->
            val ids = json.getJSONArray(family)
            families[family] = (0 until ids.length()).map { ids.getString(it) }
        }
        encode(families) // Stored input obeys the same bounded ID-only contract as writes.
        return ActionPinSettings(families.filterValues { it.isNotEmpty() })
    }
}
