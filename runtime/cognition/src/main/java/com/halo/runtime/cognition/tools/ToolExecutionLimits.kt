package com.halo.runtime.cognition.tools

object ToolExecutionLimits {
    const val MAX_TOOL_ROUNDS = 4
    const val MAX_TOOL_CALLS_PER_ROUND = 8
    const val MAX_TOTAL_TOOL_CALLS = 16
    const val MAX_TOOL_OUTPUT_CHARS = 4000
    const val MAX_TOOL_ARGUMENT_STRING_CHARS = 2000
    const val MAX_TOOL_ARGUMENT_DEPTH = 8
    const val MAX_TOOL_ARGUMENT_COLLECTION_ITEMS = 100
    const val MAX_TOOL_ARGUMENT_TOTAL_CHARS = 8_000
    const val MAX_TOOL_EXECUTION_TIME_MS = 10_000L
}
