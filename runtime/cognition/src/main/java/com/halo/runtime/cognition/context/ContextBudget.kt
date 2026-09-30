package com.halo.runtime.cognition.context

data class ContextBudget(
    val maximumTokens: Int,

    val systemReserve: Int = 200,
    val requestReserve: Int = 100,
    val responseReserve: Int = 128,

    val conversationBudget: Int = (maximumTokens * 0.35).toInt(),
    val workspaceBudget: Int = (maximumTokens * 0.35).toInt(),
    val memoryBudget: Int = (maximumTokens * 0.15).toInt(),
    val toolBudget: Int = (maximumTokens * 0.15).toInt(),
    val imageSlots: Int = 1,
    val audioDurationMs: Long = 30_000,
) {
    companion object {
        fun forModelCapacity(contextSize: Int, isSpeech: Boolean = false): ContextBudget {
            val maxTokens = contextSize.coerceAtLeast(512)
            val responseReserve = if (isSpeech) 64 else 256
            val available = maxTokens - 200 - 100 - responseReserve
            val safeAvailable = available.coerceAtLeast(100)

            return ContextBudget(
                maximumTokens = maxTokens,
                systemReserve = 200,
                requestReserve = 100,
                responseReserve = responseReserve,
                conversationBudget = (safeAvailable * 0.35).toInt(),
                workspaceBudget = (safeAvailable * 0.35).toInt(),
                memoryBudget = (safeAvailable * 0.15).toInt(),
                toolBudget = (safeAvailable * 0.15).toInt()
            )
        }
    }
}
