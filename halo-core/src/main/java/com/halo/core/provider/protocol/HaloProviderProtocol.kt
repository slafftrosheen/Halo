package com.halo.core.provider.protocol

import kotlinx.coroutines.flow.Flow

/**
 * Halo Provider Protocol (HPP).
 * The universal contract for all intelligence providers (local or cloud).
 */
interface HaloProviderProtocol {
    val id: String
    val isReady: Boolean
    
    suspend fun metadata(): ProviderMetadata
    
    suspend fun generate(request: ProviderRequest): Flow<ProviderResponseChunk>
}

data class ProviderMetadata(
    val name: String,
    val isLocal: Boolean,
    val supportedCapabilities: List<String>,
    val latencyClass: LatencyClass,
    val memoryFootprintMb: Int
)

enum class LatencyClass {
    REALTIME, FAST, STANDARD, BATCH
}

data class ProviderRequest(
    val context: List<Message>,
    val attachments: List<Attachment>,
    val availableTools: List<ToolDefinition>,
    val parameters: GenerationParameters
)

data class Message(val role: String, val content: String)
data class Attachment(val uri: String, val mimeType: String)
data class ToolDefinition(val name: String, val description: String)
data class GenerationParameters(val temperature: Float, val maxTokens: Int)

sealed class ProviderResponseChunk {
    data class Content(val text: String) : ProviderResponseChunk()
    data class ToolCall(val name: String, val arguments: String) : ProviderResponseChunk()
    data class Error(val reason: String) : ProviderResponseChunk()
    object Done : ProviderResponseChunk()
}
