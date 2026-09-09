package com.halo.runtime.cognition.tools

object ToolExecutionLimits {
    const val MAX_TOOL_ROUNDS = 4
    const val MAX_TOOL_CALLS_PER_ROUND = 8
    const val MAX_TOTAL_TOOL_CALLS = 16
    const val MAX_TOOL_OUTPUT_CHARS = 4000
    const val MAX_TOOL_ARGUMENT_STRING_CHARS = 2000
}
