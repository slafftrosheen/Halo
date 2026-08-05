package com.halo.sdk.provider

import com.halo.sdk.runtime.HaloEvent

data class AIRequestEvent(
    val prompt: String,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent

data class AIResponseEvent(
    val response: AIResponse,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent
