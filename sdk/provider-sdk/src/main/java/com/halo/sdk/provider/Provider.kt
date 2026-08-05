package com.halo.sdk.provider

interface Provider {
    suspend fun complete(request: AIRequest): AIResponse
}
