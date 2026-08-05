package com.halo.core.provider

interface Provider {
    suspend fun chat(request: ChatRequest): ChatResponse
    suspend fun stream(request: ChatRequest): StreamResponse
    suspend fun vision(request: VisionRequest): VisionResponse
    suspend fun embed(request: EmbedRequest): EmbedResponse
    suspend fun metadata(): ProviderMetadata
    fun cancel()
    suspend fun health(): ProviderHealth
}

interface ChatRequest
interface ChatResponse
interface StreamResponse
interface VisionRequest
interface VisionResponse
interface EmbedRequest
interface EmbedResponse
interface ProviderMetadata
interface ProviderHealth
