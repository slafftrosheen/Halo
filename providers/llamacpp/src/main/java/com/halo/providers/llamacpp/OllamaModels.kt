package com.halo.providers.llamacpp

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val options: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class OllamaGenerateResponse(
    val model: String,
    @field:Json(name = "created_at") val createdAt: String,
    val response: String,
    val done: Boolean,
    @field:Json(name = "total_duration") val totalDuration: Long? = null
)

@JsonClass(generateAdapter = true)
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean = false,
    val options: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class OllamaChatMessage(
    val role: String,
    val content: String,
    /** Base64 image payloads accepted by Ollama chat for vision-capable models. */
    val images: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class OllamaChatResponse(
    val model: String,
    @field:Json(name = "created_at") val createdAt: String,
    val message: OllamaChatMessage?,
    val done: Boolean,
    @field:Json(name = "total_duration") val totalDuration: Long? = null
)
