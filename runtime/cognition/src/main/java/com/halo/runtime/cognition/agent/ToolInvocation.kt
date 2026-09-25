package com.halo.runtime.cognition.agent

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.tools.ToolId
import java.security.MessageDigest
import java.time.Instant

/**
 * RUNTIME-018 durable execution identities.
 *
 * A [ToolInvocation] is the Halo-owned durable identity of one exact tool
 * execution. Provider tool-call ids are NOT durable identity: they can be
 * regenerated during continuation. The invocation identity below persists before
 * any side effect and survives process death.
 */

@JvmInline
value class ToolInvocationId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
value class ProviderTurnId(val value: String) {
    override fun toString(): String = value
}

/**
 * Deterministic canonicalization of tool arguments.
 *
 * Rules: map keys sorted lexicographically, arrays preserve order, integers and
 * floating point numbers with integral values normalize to plain integers,
 * booleans/null are stable, strings use JSON escaping. Identical semantic
 * arguments always produce identical text and therefore identical digests.
 *
 * Pure Kotlin: no Android JSON dependencies.
 */
object ToolArgumentCanonicalizer {

    /** Accepts Map<String, Any> too: Map's value type is covariant in Kotlin. */
    fun canonicalize(arguments: Map<String, Any?>): String =
        StringBuilder().also { writeValue(arguments, it) }.toString()

    fun digest(canonicalJson: String): String = sha256Hex(canonicalJson)

    fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun writeValue(value: Any?, sb: StringBuilder) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(value, sb)
            is Boolean -> sb.append(if (value) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(value.toString())
            is Double -> writeNumber(value, sb)
            is Float -> writeNumber(value.toDouble(), sb)
            is Map<*, *> -> {
                sb.append('{')
                value.entries
                    .sortedWith(compareBy { it.key.toString() })
                    .forEachIndexed { index, (key, nested) ->
                        if (index > 0) sb.append(',')
                        writeString(key.toString(), sb)
                        sb.append(':')
                        writeValue(nested, sb)
                    }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) sb.append(',')
                    writeValue(item, sb)
                }
                sb.append(']')
            }
            is Array<*> -> writeValue(value.asList(), sb)
            is IntArray -> writeValue(value.toList(), sb)
            is LongArray -> writeValue(value.toList(), sb)
            else -> writeString(value.toString(), sb)
        }
    }

    private fun writeNumber(value: Double, sb: StringBuilder) {
        require(value.isFinite()) { "Tool arguments must contain finite numbers" }
        if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 9.007199254740992E15) {
            sb.append(value.toLong().toString())
        } else {
            sb.append(value.toString())
        }
    }

    private fun writeString(value: String, sb: StringBuilder) {
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else ->
                    if (ch < ' ') sb.append("\\u").append("%04x".format(ch.code))
                    else sb.append(ch)
            }
        }
        sb.append('"')
    }

    /**
     * Minimal JSON object parser for resuming a journaled invocation. Accepts the
     * canonical output of [canonicalize] (flat string/number/boolean/object/array
     * values, which is all tool arguments allow).
     */
    fun parseToArgumentsMap(json: String): Map<String, Any?> {
        val parser = JsonParser(json)
        return parser.parseObject()
    }

    private class JsonParser(private val input: String) {
        private var pos = 0

        fun parseObject(): Map<String, Any?> {
            skipWhitespace()
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') { pos++; return map }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                map[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> { pos++ }
                    '}' -> { pos++; return map }
                    else -> error("Malformed canonical JSON at $pos")
                }
            }
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            return when (val c = peek()) {
                '"' -> parseString()
                '{' -> parseObject()
                '[' -> parseArray()
                't' -> { expectWord("true"); true }
                'f' -> { expectWord("false"); false }
                'n' -> { expectWord("null"); null }
                else -> if (c == '-' || c.isDigit()) parseNumber() else error("Malformed canonical JSON at $pos")
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') { pos++; return list }
            while (true) {
                list += parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return list }
                    else -> error("Malformed canonical JSON at $pos")
                }
            }
        }

        private fun parseNumber(): Any {
            val start = pos
            if (peek() == '-') pos++
            var integral = true
            while (pos < input.length && input[pos].isDigit()) pos++
            if (pos < input.length && input[pos] == '.') { integral = false; pos++
                while (pos < input.length && input[pos].isDigit()) pos++ }
            if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
                integral = false; pos++
                if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
                while (pos < input.length && input[pos].isDigit()) pos++
            }
            val text = input.substring(start, pos)
            return if (integral) text.toLong() else text.toDouble()
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = input[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        when (val esc = input[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                sb.append(input.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> error("Malformed escape at $pos")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun skipWhitespace() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
        private fun peek(): Char = input.getOrElse(pos) { error("Unexpected end of canonical JSON") }
        private fun expect(c: Char) { check(peek() == c) { "Malformed canonical JSON at $pos" }; pos++ }
        private fun expectWord(word: String) { check(input.regionMatches(pos, word, 0, word.length)) { "Malformed canonical JSON at $pos" }; pos += word.length }
    }
}

/**
 * Durable identity of one exact tool execution. Persisted (as
 * [AgentStep.ToolInvocationPrepared]) before authorization completes and before
 * any execution claim, so recovery can always reason about what was intended.
 */
data class ToolInvocation(
    val invocationId: ToolInvocationId,
    val runId: AgentRunId,
    val interactionId: InteractionId,
    val toolCallId: String,
    val toolId: ToolId,

    val argumentsJson: String,
    val argumentsDigest: String,

    val idempotencyKey: String,
    val descriptorVersion: Int,
    val replaySafety: com.halo.runtime.cognition.tools.ReplaySafety,

    val createdAt: Instant,
) {
    companion object {
        /**
         * Stable idempotency identity: run + provider call + tool + arguments.
         * Never regenerated merely because the process restarted.
         */
        fun idempotencyKey(
            runId: AgentRunId,
            toolCallId: String,
            toolId: ToolId,
            argumentsDigest: String,
        ): String = ToolArgumentCanonicalizer.sha256Hex(
            "${runId.value}|$toolCallId|${toolId.value}|$argumentsDigest"
        )
    }
}
