package com.halo.providers.litert.runtime

import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

/**
 * AGENTIC-001 prompt-mediated tool protocol.
 *
 * LiteRT-LM has model-family level tool support, but a family is not an artifact, and
 * a 0.6B Qwen does not reliably emit native tool calls. Rather than lying about
 * `supportsTools` or letting the runtime execute anything, Halo advertises the exposed
 * actions as a bounded instruction and parses exactly one envelope format out of the
 * model's text.
 *
 * Rules enforced here (the provider adapter boundary, never inside ToolRegistry):
 *
 * - exactly one envelope format: `<halo_tool_call>{json}</halo_tool_call>`
 * - JSON only, with `name` and `arguments`
 * - the name must be a tool that was actually exposed for this turn
 * - arguments must be a JSON object; schema validation happens later in ToolRegistry
 * - a bounded number of calls per response
 * - ordinary prose, malformed JSON and partial envelopes are never treated as actions
 *
 * Halo remains the only tool executor.
 */
object HaloPromptToolProtocol {

    const val OPEN_TAG = "<halo_tool_call>"
    const val CLOSE_TAG = "</halo_tool_call>"
    const val MAX_CALLS_PER_RESPONSE = 2

    data class ParseResult(
        val visibleText: String,
        val toolCalls: List<ToolCall>,
        /** Bounded diagnostic evidence: what was dropped and why. Never model-visible. */
        val diagnostic: String? = null,
    )

    /** Compact, bounded instruction listing exactly the actions exposed for this turn. */
    fun instruction(tools: List<ToolDefinition>, maxCalls: Int = MAX_CALLS_PER_RESPONSE): String {
        if (tools.isEmpty()) return ""
        return buildString {
            append("You can ask Halo to perform an action on this phone.\n\n")
            append("To use one, reply with ")
            append(if (maxCalls == 1) "exactly one block" else "at most $maxCalls blocks")
            append(" and no other text:\n\n")
            append(OPEN_TAG).append('\n')
            append("{\"name\":\"<action>\",\"arguments\":{<arguments as JSON>}}\n")
            append(CLOSE_TAG).append("\n\n")
            append("Available actions:\n")
            tools.forEach { tool ->
                append("- ").append(tool.name).append('(')
                append(renderParameters(tool))
                append("): ").append(tool.description.lineSequence().first().take(160)).append('\n')
            }
            append("\nRules:\n")
            append("- Use only the action names listed above. Never invent an action.\n")
            append("- Arguments must be a JSON object with the exact parameter names listed above.\n")
            append("- Do not explain, apologise or add text around the block when you use an action.\n")
            append("- If no listed action fits, answer the user normally in plain text.\n")
        }
    }

    /**
     * Extract Halo tool calls from a raw model response and return the text a human
     * should actually see.
     */
    fun parse(
        rawText: String,
        exposed: List<ToolDefinition>,
        maxCalls: Int = MAX_CALLS_PER_RESPONSE,
        idFactory: () -> String = { "halo-prompt-${java.util.UUID.randomUUID()}" },
    ): ParseResult {
        if (!rawText.contains(OPEN_TAG)) {
            // Missing the opener is not permission to expose a partial/stray
            // tool marker to chat, speech or persisted conversation turns.
            return if ("halo_tool_call" in rawText) ParseResult("", emptyList(), "invalid_markup")
                else ParseResult(rawText, emptyList())
        }

        val visible = StringBuilder()
        val calls = mutableListOf<ToolCall>()
        val notes = mutableListOf<String>()
        val exposedNames = exposed.map { it.name }.toSet() + exposed.map { it.id }
        var cursor = 0

        while (true) {
            val open = rawText.indexOf(OPEN_TAG, cursor)
            if (open < 0) {
                visible.append(stripFences(rawText.substring(cursor)))
                break
            }
            // Text before the envelope is ordinary prose.
            visible.append(stripFences(rawText.substring(cursor, open)))

            val close = rawText.indexOf(CLOSE_TAG, open + OPEN_TAG.length)
            if (close < 0) {
                // Partial output: never execute it, and never show half an envelope.
                notes += "incomplete_envelope"
                break
            }

            val body = rawText.substring(open + OPEN_TAG.length, close)
            if (calls.size >= maxCalls) {
                notes += "too_many_calls"
            } else {
                when (val parsed = parseOne(body, exposedNames, idFactory)) {
                    is OneParseResult.Call -> calls += parsed.call
                    is OneParseResult.Invalid -> notes += parsed.reason
                }
            }
            cursor = close + CLOSE_TAG.length
        }

        // All-or-nothing: one malformed, truncated, unexposed or extra call
        // invalidates the *whole* provider output. Never execute the first
        // valid call when a later envelope is unsafe or incomplete.
        return if (notes.isNotEmpty()) ParseResult(
            visibleText = "",
            toolCalls = emptyList(),
            diagnostic = notes.joinToString(","),
        ) else ParseResult(
            visibleText = visible.toString().trim(),
            toolCalls = calls,
        )
    }

    private sealed interface OneParseResult {
        data class Call(val call: ToolCall) : OneParseResult
        data class Invalid(val reason: String) : OneParseResult
    }

    private fun parseOne(body: String, exposedNames: Set<String>, idFactory: () -> String): OneParseResult {
        if (body.length > 16_384) return OneParseResult.Invalid("oversized_envelope")
        val trimmed = body.trim().removeSurrounding("```json", "```").trim()
        if (trimmed.isEmpty()) return OneParseResult.Invalid("empty_envelope")

        val root = runCatching { parseJson(trimmed) }.getOrNull()
            ?: return OneParseResult.Invalid("unparseable_json")
        val objectRoot = root as? Map<*, *> ?: return OneParseResult.Invalid("not_an_object")

        val name = (objectRoot["name"] as? String)?.trim().orEmpty()
        if (name.isEmpty()) return OneParseResult.Invalid("missing_name")
        if (name !in exposedNames) return OneParseResult.Invalid("unexposed_tool")

        val rawArguments = objectRoot["arguments"] ?: objectRoot["args"] ?: emptyMap<String, Any?>()
        val arguments = rawArguments as? Map<*, *> ?: return OneParseResult.Invalid("arguments_not_an_object")
        if (arguments.keys.any { it !is String }) return OneParseResult.Invalid("non_string_argument_key")

        @Suppress("UNCHECKED_CAST")
        return OneParseResult.Call(
            ToolCall(
                id = idFactory(),
                functionName = name,
                arguments = arguments.entries.associate { (key, value) -> key as String to (value ?: "") },
            )
        )
    }

    private fun renderParameters(tool: ToolDefinition): String {
        val schema = tool.parameters
        val properties = schema["properties"] as? Map<*, *> ?: return ""
        if (properties.isEmpty()) return ""
        val required = (schema["required"] as? List<*>)?.filterIsInstance<String>().orEmpty().toSet()
        return properties.entries.joinToString(", ") { (key, value) ->
            val name = key.toString()
            val type = ((value as? Map<*, *>)?.get("type") as? String) ?: "string"
            if (name in required) "$name: $type (required)" else "$name: $type (optional)"
        }
    }

    private fun stripFences(text: String): String =
        text.replace("```json", "").replace("```", "")

    // ---------------------------------------------------------------------------------------
    // Minimal bounded JSON reader. Provider-local on purpose: the LiteRT module must not
    // depend on runtime:cognition just to read one object.
    // ---------------------------------------------------------------------------------------

    private fun parseJson(text: String): Any? = JsonReader(text).readRoot()

    private class JsonReader(private val input: String) {
        private var pos = 0

        fun readRoot(): Any? {
            val value = readValue()
            skipWhitespace()
            require(pos == input.length) { "trailing_bytes_after_json" }
            return value
        }

        fun readValue(): Any? {
            skipWhitespace()
            return when (val c = peek()) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> { expectWord("true"); true }
                'f' -> { expectWord("false"); false }
                'n' -> { expectWord("null"); null }
                else -> if (c == '-' || c.isDigit()) readNumber() else error("unexpected '$c' at $pos")
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') { pos++; return map }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                map[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return map }
                    else -> error("expected ',' or '}' at $pos")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') { pos++; return list }
            while (true) {
                list += readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return list }
                    else -> error("expected ',' or ']' at $pos")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = input[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> when (val esc = input[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> { sb.append(input.substring(pos, pos + 4).toInt(16).toChar()); pos += 4 }
                        else -> error("bad escape at $pos")
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            if (peek() == '-') pos++
            var integral = true
            while (pos < input.length && input[pos].isDigit()) pos++
            if (pos < input.length && input[pos] == '.') {
                integral = false
                pos++
                while (pos < input.length && input[pos].isDigit()) pos++
            }
            if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
                integral = false
                pos++
                if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
                while (pos < input.length && input[pos].isDigit()) pos++
            }
            val text = input.substring(start, pos)
            return if (integral) text.toLong() else text.toDouble()
        }

        private fun skipWhitespace() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
        private fun peek(): Char = input.getOrElse(pos) { error("unexpected end of JSON") }
        private fun expect(c: Char) {
            check(peek() == c) { "expected '$c' at $pos" }
            pos++
        }
        private fun expectWord(word: String) {
            check(input.regionMatches(pos, word, 0, word.length)) { "expected $word at $pos" }
            pos += word.length
        }
    }
}
