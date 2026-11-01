package com.halo.app.everyday.collectors

import org.json.JSONObject

/** Validate resource bounds before Android's recursive JSON parser sees untrusted provider/import data. */
internal object SafeCollectorJson {
    fun objectValue(body: String, maximumCharacters: Int = CollectorRefreshPolicy.MAX_BODY_BYTES, maximumTokens: Int = 100_000): JSONObject {
        require(maximumCharacters in 1..8 * 1024 * 1024 && maximumTokens in 1..1_000_000)
        require(body.length <= maximumCharacters) { "JSON exceeds the size limit" }
        val stack = ArrayDeque<Char>()
        var quoted = false
        var escaped = false
        var tokens = 0
        for (char in body) {
            if (escaped) { escaped = false; continue }
            if (quoted && char == '\\') { escaped = true; continue }
            if (char == '"') { quoted = !quoted; continue }
            if (!quoted) when (char) {
                '{', '[' -> { stack.addLast(char); require(stack.size <= 64); tokens++ }
                '}', ']' -> require(stack.removeLastOrNull() == if (char == '}') '{' else '[')
                ',', ':' -> tokens++
            }
            require(tokens <= maximumTokens) { "JSON exceeds the item limit" }
        }
        require(stack.isEmpty() && !quoted && !escaped) { "JSON is incomplete" }
        return JSONObject(body)
    }
}
