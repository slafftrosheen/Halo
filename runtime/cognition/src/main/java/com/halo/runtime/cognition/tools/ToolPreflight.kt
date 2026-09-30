package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.ui.*
import com.halo.sdk.provider.ToolCall

/** Optional, read-only preparation. Validates evidence; never dispatches an action. */
interface ToolPreflightProvider {
    suspend fun preflight(toolCall: ToolCall): ToolPreflightResult
    /** Process-local, exact-identity restoration only; an absent value can never be fabricated. */
    fun restoreTransientCall(toolCall: ToolCall): ToolCall = toolCall
}
sealed interface ToolPreflightResult {
    data class Validated(val sanitizedCall: ToolCall, val preview: UiTargetPreview? = null,
        val intent: UiActionIntent? = null) : ToolPreflightResult
    data class Rejected(val failure: UiFailure) : ToolPreflightResult
}
/** Set-text values remain transient: durable provider/tool arguments contain only length/digest. */
fun journalToolArguments(call: ToolCall): Map<String, Any> = if (call.functionName == "halo_ui_set_text") {
    val text = call.arguments["text"] as? String
    call.arguments.filterKeys { it != "text" } + if (text != null)
        mapOf("textLength" to text.length, "textDigest" to uiDigest(text), "uiProtocol" to "V1") else emptyMap()
} else call.arguments
