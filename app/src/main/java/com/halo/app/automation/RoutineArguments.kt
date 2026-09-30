package com.halo.app.automation

import com.halo.runtime.cognition.routine.*
import com.halo.sdk.provider.ToolCall
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import com.halo.app.data.routine.RoutineCodec

/** Bounds are checked before parsing; depth scan prevents recursive parser exhaustion. */
object RoutineArguments {
    fun decode(raw: String): Map<String, Any> {
        checkBounds(raw)
        val tokener = org.json.JSONTokener(raw)
        val j = tokener.nextValue() as? JSONObject ?: error("Arguments must be an object")
        require(tokener.nextClean() == '\u0000') { "Trailing argument data" }
        fun convert(v: Any?, level: Int): Any? {
            require(level <= 8)
            return when (v) {
                null, JSONObject.NULL -> null
                is JSONObject -> { require(v.length() <= 64); v.keys().asSequence().toList().sorted().associateWith { require(it.length <= 128); convert(v.get(it), level + 1) } }
                is JSONArray -> { require(v.length() <= 64); (0 until v.length()).map { convert(v.get(it), level + 1) } }
                is String -> v.also { require(it.length <= 2048) }
                is Boolean -> v
                is Number -> v.also { require(it.toDouble().isFinite()) }
                else -> error("Unsupported argument value")
            }
        }
        @Suppress("UNCHECKED_CAST")
        val values = convert(j, 0) as Map<String, Any?>
        // SDK ToolCall has non-null top-level values; reject null there instead of guessing a default.
        return values.mapValues { requireNotNull(it.value) { "Top-level null arguments are unsupported" } }
    }
    fun checkBounds(raw: String, maxBytes: Int = 8192, maxDepth: Int = 8) {
        require(raw.toByteArray(Charsets.UTF_8).size <= maxBytes) { "Arguments too large" }
        var depth = 0; var quoted = false; var escaped = false
        raw.forEach { c ->
            if (escaped) escaped = false
            else if (quoted && c == '\\') escaped = true
            else if (c == '"') quoted = !quoted
            else if (!quoted && (c == '{' || c == '[')) { depth++; require(depth <= maxDepth) { "Arguments too deep" } }
            else if (!quoted && (c == '}' || c == ']')) depth--
        }
        require(depth == 0 && !quoted) { "Malformed arguments" }
    }
    fun canonical(raw: String): String {
        fun encode(v: Any?): String = when(v) {
            null -> "null"
            is Map<*, *> -> v.entries.sortedBy { it.key.toString() }.joinToString(",", "{", "}") { JSONObject.quote(it.key.toString()) + ":" + encode(it.value) }
            is List<*> -> v.joinToString(",", "[", "]") { encode(it) }
            is String -> JSONObject.quote(v)
            is Number -> java.math.BigDecimal(v.toString()).stripTrailingZeros().toPlainString()
            else -> v.toString()
        }
        return encode(decode(raw))
    }
    fun call(action: RoutineAction, id: String) = ToolCall(id, action.toolId, decode(action.argumentsJson))
    fun grant(r: Routine): String {
        val codec = RoutineCodec()
        val definition = JSONObject().put("trigger", JSONObject(codec.encodeTrigger(r.trigger)))
            .put("conditions", JSONArray(codec.encodeConditions(r.conditions)))
            .put("actions", JSONArray(r.actions.map { JSONObject().put("tool", it.toolId).put("arguments", JSONObject(canonical(it.argumentsJson))) }))
            .put("delivery", JSONObject(codec.encodeDelivery(r.delivery))).put("minimumInterval", r.minimumIntervalSeconds)
        val canonical = canonicalDefinition(definition)
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    private fun canonicalDefinition(j: JSONObject): String {
        fun encode(v: Any?): String = when(v) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> v.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + encode(v.get(it)) }
            is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { encode(v.get(it)) }
            is Number -> java.math.BigDecimal(v.toString()).stripTrailingZeros().toPlainString()
            is Boolean -> v.toString()
            else -> JSONObject.quote(v.toString())
        }
        return encode(j)
    }
}
