package com.halo.sdk.provider

import org.json.JSONArray
import org.json.JSONObject

/** Convert wire objects/arrays to the Maps/Lists required by the tool contract validator. */
object ToolArgumentJson {
    fun decode(value: Any?): Map<String, Any> = try {
        val root = when (value) {
            null -> JSONObject()
            is JSONObject -> value
            is String -> {
                require(value.length <= 1024 * 1024)
                JSONObject(value)
            }
            else -> error("Tool arguments must be an object")
        }
        var remainingNodes = 65_536
        var remainingCharacters = 1024 * 1024
        fun convert(item: Any, depth: Int): Any {
            require(depth <= 64 && --remainingNodes >= 0)
            return when (item) {
                is JSONObject -> buildMap<String, Any> {
                    item.keys().forEach { key ->
                        remainingCharacters -= key.length
                        require(remainingCharacters >= 0)
                        put(key, convert(item.get(key), depth + 1))
                    }
                }
                is JSONArray -> (0 until item.length()).map { convert(item.get(it), depth + 1) }
                is String -> item.also {
                    remainingCharacters -= it.length
                    require(remainingCharacters >= 0)
                }
                // Preserve numbers/booleans and the established JSONObject.NULL marker.
                // A string containing JSON remains text, never another implicit object.
                else -> item
            }
        }
        @Suppress("UNCHECKED_CAST")
        (convert(root, 0) as Map<String, Any>)
    } catch (error: Exception) {
        throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Provider returned invalid or oversized tool arguments", error)
    }
}
