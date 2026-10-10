package com.halo.runtime.cognition.tools

/**
 * Bounds untrusted tool output without silently hiding the ending of multi-line results.
 * This is a presentation budget only: action truth and the original byte-count metadata
 * remain owned by the execution result.
 */
object ToolResultBudgeter {

    fun budgetText(output: String, maxChars: Int): String {
        if (maxChars <= 0) return ""
        if (output.length <= maxChars) return output

        // The notice explicitly says that this is incomplete. Never emit a fragment of JSON
        // or a log as though it were the complete tool response.
        var estimatedOmitted = (output.length - maxChars).coerceAtLeast(1)
        repeat(8) {
            val notice = "\n... [tool result truncated: $estimatedOmitted chars omitted; content incomplete] ...\n"
            if (notice.length >= maxChars) return "…[truncated]".take(maxChars)

            val available = maxChars - notice.length
            var headEnd = available - available / 3
            var tailStart = output.length - available / 3

            // Logs and pretty-printed documents read better when the retained slices
            // start/end on whole lines. Avoid a large extra scan or dropping most context.
            if (available >= 96) {
                val headBreak = output.lastIndexOf('\n', headEnd - 1)
                if (headBreak >= headEnd - minOf(80, headEnd / 3)) headEnd = headBreak + 1
                val tailBreak = output.indexOf('\n', tailStart)
                if (tailBreak >= 0 && tailBreak < tailStart + minOf(80, (output.length - tailStart) * 2 / 3)) {
                    tailStart = tailBreak + 1
                }
            }

            // A Kotlin String is UTF-16: never strand half an emoji at either cut.
            if (headEnd > 0 && headEnd < output.length &&
                Character.isHighSurrogate(output[headEnd - 1]) && Character.isLowSurrogate(output[headEnd])
            ) headEnd--
            if (tailStart > 0 && tailStart < output.length &&
                Character.isHighSurrogate(output[tailStart - 1]) && Character.isLowSurrogate(output[tailStart])
            ) tailStart++

            val omitted = tailStart - headEnd
            if (omitted == estimatedOmitted) {
                return output.substring(0, headEnd) + notice + output.substring(tailStart)
            }
            estimatedOmitted = omitted
        }

        // Defensive fallback if digit-width/line-boundary changes fail to converge.
        val notice = "\n... [tool result truncated; content incomplete] ..."
        if (notice.length >= maxChars) return "…[truncated]".take(maxChars)
        return output.take(maxChars - notice.length) + notice
    }

    fun budgetResult(
        result: ToolExecutionResult,
        maxChars: Int = 4000,
    ): ToolExecutionResult {
        return when (result) {
            is ToolExecutionResult.Success -> {
                val output = result.output
                val bounded = budgetText(output, maxChars)
                result.copy(
                    output = bounded,
                    outputMetadata = ToolOutputMetadata(
                        truncated = bounded != output,
                        originalChars = output.length,
                        originalUtf8Bytes = output.toByteArray(Charsets.UTF_8).size,
                    ),
                )
            }
            else -> result
        }
    }
}
