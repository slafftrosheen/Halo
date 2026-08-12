package com.halo.providers.android.assistant

import android.content.Context
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiProvider
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse

/**
 * AI Provider that integrates with the Android system assistant.
 *
 * This implementation acts as a bridge to the default assistant app configured on the device.
 */
class AndroidAssistantProvider(
    private val context: Context
) : AiProvider {

    override val id: String = "android.system.assistant"
    override val name: String = "Phone Assistant"

    override val capabilities: Set<AiCapability> = setOf(
        AiCapability.TEXT,
        AiCapability.IMAGE,
        AiCapability.AUDIO,
        AiCapability.MULTIMODAL
    )

    override fun isAvailable(): Boolean {
        // Basic check for assistant availability via Intent resolution
        val intent = android.content.Intent(android.content.Intent.ACTION_ASSIST)
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        return resolveInfo != null
    }

    override suspend fun execute(request: AiRequest): AiResponse {
        // Implementation note: Android's public Assistant integration is primarily
        // based on intents and assist context. This provider encapsulates that logic.

        android.util.Log.i("HALO-AI", "provider=$id operation=execute capability=TEXT availability=${isAvailable()}")

        // Launch assistant intent for handoff if text is provided
        if (request.text != null) {
            val intent = android.content.Intent(android.content.Intent.ACTION_ASSIST).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(android.content.Intent.EXTRA_ASSIST_CONTEXT, android.os.Bundle())
                // Note: Standard ACTION_ASSIST doesn't always take text directly, 
                // but some implementations/OEMs support it via extras.
                // For a true "handoff", we are launching the system assistant.
            }
            context.startActivity(intent)
            
            return AiResponse(
                text = "HANDOFF_STARTED",
                metadata = mapOf(
                    "status" to "HANDOFF",
                    "provider_id" to id
                )
            )
        }

        return AiResponse(
            text = "UNSUPPORTED",
            metadata = mapOf(
                "status" to "FAILED",
                "reason" to "No supported modality for handoff"
            )
        )
    }
}
