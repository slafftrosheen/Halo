package com.halo.sdk.provider

/**
 * Assembles incremental [AiStreamEvent.ToolCallDelta] events into complete [ToolCall]s.
 */
class StreamingToolCallAssembler {
    private class InProgressCall(
        var name: String = "",
        val argumentsBuilder: StringBuilder = StringBuilder()
    )

    private val callsByIndex = mutableMapOf<Int, InProgressCall>()

    fun appendDelta(delta: AiStreamEvent.ToolCallDelta) {
        val call = callsByIndex.getOrPut(delta.index) { InProgressCall() }
        delta.name?.let { call.name = it }
        delta.argumentsDelta?.let { call.argumentsBuilder.append(it) }
    }

    fun assemble(requestId: String = "stream"): List<ToolCall> {
        if (callsByIndex.isEmpty()) return emptyList()
        return callsByIndex.entries.sortedBy { it.key }.mapIndexed { ordinal, entry ->
            val call = entry.value
            val rawArgs = call.argumentsBuilder.toString().trim()
            val argsMap = parseSimpleJsonMap(rawArgs)

            ToolCall(
                id = "$requestId:assembled:${ordinal + 1}",
                functionName = call.name,
                arguments = argsMap
            )
        }
    }

    fun clear() {
        callsByIndex.clear()
    }

    private fun parseSimpleJsonMap(raw: String): Map<String, Any> {
        if (raw.isBlank() || !raw.startsWith("{") || !raw.endsWith("}")) return emptyMap()
        val map = mutableMapOf<String, Any>()
        val kvPairRegex = Regex("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|true|false|null|\\d+(?:\\.\\d+)?)")
        kvPairRegex.findAll(raw).forEach { match ->
            val key = match.groupValues[1]
            val valueStr = match.groupValues[2]
            val value: Any = when {
                valueStr == "true" -> true
                valueStr == "false" -> false
                valueStr == "null" -> ""
                valueStr.startsWith("\"") -> valueStr.removeSurrounding("\"")
                valueStr.contains(".") -> valueStr.toDoubleOrNull() ?: valueStr
                else -> valueStr.toLongOrNull() ?: valueStr
            }
            map[key] = value
        }
        return map
    }
}
