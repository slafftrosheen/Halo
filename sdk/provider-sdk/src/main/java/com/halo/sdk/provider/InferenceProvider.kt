package com.halo.sdk.provider

import kotlinx.coroutines.flow.Flow

interface InferenceProvider {
    suspend fun complete(request: AIRequest): AIResponse
    fun stream(prompt: String): Flow<String>
    suspend fun health(): Boolean
}
