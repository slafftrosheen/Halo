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
        val intent: UiActionIntent? = null,
        val semanticPreview: com.halo.runtime.cognition.apps.SemanticOperationReceipt? = null,
        val semanticArgumentShapes: List<com.halo.runtime.cognition.apps.SemanticArgumentShape> = emptyList()) : ToolPreflightResult
    data class Rejected(val failure: UiFailure) : ToolPreflightResult
}
/** Set-text values remain transient: durable provider/tool arguments contain only length/digest. */
fun journalToolArguments(call: ToolCall): Map<String, Any> = if (call.functionName == "halo_ui_set_text") {
    val text = call.arguments["text"] as? String
    call.arguments.filterKeys { it != "text" } + if (text != null)
        mapOf("textLength" to text.length, "textDigest" to uiDigest(text), "uiProtocol" to "V1") else emptyMap()
} else if (call.functionName.startsWith("halo_appfn_") || call.functionName.startsWith("halo_compose_") || call.functionName == "halo_current_app_action") {
    if (call.arguments["transientCallId"] != null) call.arguments.filterKeys { it in setOf("transientCallId", "argumentShape", "semanticGeneration") }
    else com.halo.runtime.cognition.apps.TransientSemanticArguments.capsule(call)
} else call.arguments
