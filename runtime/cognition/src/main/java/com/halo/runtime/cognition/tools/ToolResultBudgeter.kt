package com.halo.runtime.cognition.tools

object ToolResultBudgeter {

    fun budgetResult(
        result: ToolExecutionResult,
        maxChars: Int = 4000,
    ): ToolExecutionResult {
        return when (result) {
            is ToolExecutionResult.Success -> {
                val output = result.output
                if (output.length <= maxChars) return result

                val truncatedNotice = "\n... [result truncated: ${output.length - maxChars} chars omitted]"
                val keepLen = (maxChars - truncatedNotice.length).coerceAtLeast(100)
                val newOutput = output.take(keepLen) + truncatedNotice
                result.copy(output = newOutput)
            }
            else -> result
        }
    }
}
