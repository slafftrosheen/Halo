package com.halo.runtime.cognition.apps

import com.halo.sdk.provider.ToolCall
import java.util.concurrent.ConcurrentHashMap

/** Argument capsules carry no digest of private text. A process restart requires fresh user input. */
class TransientSemanticArguments(private val now: () -> Long = System::currentTimeMillis) {
    private data class Pending(val call: ToolCall, val expiresAt: Long)
    private val pending = ConcurrentHashMap<String, Pending>()
    fun prepare(call: ToolCall): ToolCall {
        pending.entries.removeIf { it.value.expiresAt <= now() }
        if (pending.size >= 64) pending.clear()
        pending[call.id] = Pending(call, now() + 120_000)
        return call.copy(arguments = capsule(call))
    }
    fun restore(call: ToolCall): ToolCall {
        if (call.arguments["transientCallId"] != call.id) return call
        return pending[call.id]?.takeIf { it.expiresAt > now() && it.call.functionName == call.functionName }?.call ?: call
    }
    fun consume(call: ToolCall): ToolCall? = pending.remove(call.id)?.takeIf {
        it.expiresAt > now() && it.call.functionName == call.functionName }?.call
    companion object {
        /** Conservative loop identity: no call ID and no private value hashes. */
        fun loopIdentity(arguments: Map<String, Any?>): Map<String, Any?> =
            if (arguments.containsKey("transientCallId")) arguments - "transientCallId" else arguments
        fun capsule(call: ToolCall): Map<String, Any> = mapOf("transientCallId" to call.id,
            "argumentShape" to call.arguments.entries.take(24).map { (key, v) -> mapOf("field" to key.take(64),
                "type" to when (v) { is String -> "STRING"; is Boolean -> "BOOLEAN"; is Number -> "NUMBER"; else -> "UNSUPPORTED" },
                "present" to true, "length" to if (v is String) v.length else 1) })
    }
}
