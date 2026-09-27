package com.halo.runtime.cognition.tools

object ToolResultBudgeter {

    fun budgetText(output: String, maxChars: Int): String {
        if (output.length <= maxChars) return output
        val notice = "\n... [result truncated: ${output.length - maxChars} chars omitted]"
        if (notice.length >= maxChars) return notice.takeLast(maxChars)
        return output.take(maxChars - notice.length) + notice
    }

    fun budgetResult(
        result: ToolExecutionResult,
        maxChars: Int = 4000,
    ): ToolExecutionResult {
        return when (result) {
            is ToolExecutionResult.Success -> {
                val output = result.output
                if (output.length <= maxChars) return result.copy(
                    outputMetadata = ToolOutputMetadata(false, output.length, output.toByteArray(Charsets.UTF_8).size)
                )

                val newOutput = budgetText(output, maxChars)
                result.copy(
                    output = newOutput,
                    outputMetadata = ToolOutputMetadata(true, output.length, output.toByteArray(Charsets.UTF_8).size),
                )
            }
            else -> result
        }
    }
}
