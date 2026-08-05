package com.halo.provider.openai

import com.halo.core.provider.*

class OpenAIProvider : Provider {
    override suspend fun chat(request: ChatRequest): ChatResponse { TODO("Not yet implemented") }
    override suspend fun stream(request: ChatRequest): StreamResponse { TODO("Not yet implemented") }
    override suspend fun vision(request: VisionRequest): VisionResponse { TODO("Not yet implemented") }
    override suspend fun embed(request: EmbedRequest): EmbedResponse { TODO("Not yet implemented") }
    override suspend fun metadata(): ProviderMetadata { TODO("Not yet implemented") }
    override fun cancel() { TODO("Not yet implemented") }
    override suspend fun health(): ProviderHealth { TODO("Not yet implemented") }
}
