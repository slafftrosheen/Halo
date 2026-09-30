package com.halo.runtime.cognition.context

enum class ContextSegmentKind { SYSTEM_STABLE, CURRENT_REQUEST, RECENT_CONVERSATION, WORKING_SET, MEMORY, RETRIEVED_DOCUMENT, TOOL_RESULT, CHILD_AGENT_RESULT }
data class ContextProvenance(val sourceId: String, val trust: ContextTrust, val remoteAllowed: Boolean, val revision: String? = null)
sealed interface SegmentContent {
    data class InlineText(val text: String) : SegmentContent
    data class Payload(val ref: PayloadRef) : SegmentContent
    data class WorkspaceRef(val id: String, val revision: String?) : SegmentContent
    data class MemoryRef(val id: String, val revision: String?) : SegmentContent
}
data class ContextSegment(val id: String, val kind: ContextSegmentKind, val priority: Int,
    val tokenEstimate: Int, val content: SegmentContent, val provenance: ContextProvenance)
data class ContextWindowBudget(val modelContextTokens: Int, val reservedOutputTokens: Int,
    val reservedSystemTokens: Int, val availableDynamicTokens: Int)
data class ContextComposition(val budget: ContextWindowBudget, val requestTokens: Int,
    val segmentTokens: Map<ContextSegmentKind, Int>, val remainingTokens: Int, val compactedTurns: Int = 0)
object ContextDiagnostics {
    val composition = kotlinx.coroutines.flow.MutableStateFlow<ContextComposition?>(null)
    val compactionCount = java.util.concurrent.atomic.AtomicLong()
}
