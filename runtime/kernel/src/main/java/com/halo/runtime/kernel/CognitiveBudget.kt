package com.halo.runtime.kernel

data class CognitiveBudget(
    val tokensRemaining: Int,
    val timeLimitMs: Long,
    val maxMemoryBytes: Long,
    val maxLlmCalls: Int,
    val maxNetworkBytes: Long
) {
    fun consumeTokens(amount: Int): CognitiveBudget = copy(tokensRemaining = tokensRemaining - amount)
    fun consumeLlmCall(): CognitiveBudget = copy(maxLlmCalls = maxLlmCalls - 1)
}
