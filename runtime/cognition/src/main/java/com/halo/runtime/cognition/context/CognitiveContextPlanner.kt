package com.halo.runtime.cognition.context

import com.halo.sdk.provider.AiContextPolicy

data class CognitiveContextPlan(
    val totalTokens: Int,
    val responseReserveTokens: Int,
    val framingReserveTokens: Int,
    val toolBudgetTokens: Int,
    val explicitContextBudgetTokens: Int,
    val memoryBudgetTokens: Int,
    val conversationBudgetTokens: Int,
)

class CognitiveContextPlanner {
    /** Applied at every provider boundary, including journal reconstruction and tool continuations. */
    fun fitProviderContext(window: Int, request: com.halo.sdk.provider.AiRequest, context: com.halo.sdk.provider.AiContext): com.halo.sdk.provider.AiContext {
        val estimator = ConservativeContextTokenEstimator()
        val schemaTokens = context.availableTools.sumOf { estimator.estimate(it.name + it.description + it.parameters.toString()) }
        val extraTokens = estimator.estimate(context.screenContext?.text.orEmpty()) +
            context.memories.sumOf { estimator.estimate(it.content) } +
            context.recentEvents.sumOf { estimator.estimate(it.toString()) } +
            estimator.estimate(context.deviceState?.toString().orEmpty())
        val output = (request.maxOutputTokens ?: 768).coerceAtLeast(0)
        val budget = windowBudget(window, output, 256 + schemaTokens + extraTokens)
        val requestTokens = if (request.appendTextAsUserMessage) estimator.estimate(request.text.orEmpty()) else 0
        if (window < output + 256 + schemaTokens + extraTokens + requestTokens) exceeded()
        val groups = mutableListOf<List<com.halo.sdk.provider.AIMessage>>()
        var index = 0
        while (index < context.conversation.size) {
            val message = context.conversation[index++]
            if (message.role == com.halo.sdk.provider.Role.TOOL) exceeded()
            val group = mutableListOf(message)
            if (message.toolCalls.isNotEmpty()) {
                while (index < context.conversation.size && context.conversation[index].role == com.halo.sdk.provider.Role.TOOL)
                    group += context.conversation[index++]
                val expectedIds = message.toolCalls.map { it.id }.toSet()
                val resultIds = group.drop(1).map { it.toolCallId }
                if (resultIds.size != expectedIds.size || resultIds.toSet() != expectedIds) exceeded()
            } else if (message.role == com.halo.sdk.provider.Role.USER && index < context.conversation.size &&
                context.conversation[index].role == com.halo.sdk.provider.Role.ASSISTANT && context.conversation[index].toolCalls.isEmpty()) {
                group += context.conversation[index++]
            }
            groups += group
        }
        fun cost(group: List<com.halo.sdk.provider.AIMessage>) = group.sumOf {
            estimator.estimate(it.content + it.toolCalls.joinToString { call -> call.functionName + call.arguments.toString() }) + 8
        }
        val originalRequest = groups.lastOrNull { group -> group.first().role == com.halo.sdk.provider.Role.USER &&
            !group.first().content.startsWith("User-selected reference") && !group.first().content.startsWith("Untrusted reference") &&
            !group.first().content.startsWith("Reference data") && !group.first().content.startsWith("Prior conversation summary") }
        val required = groups.filter { group ->
            val first = group.first()
            first.role == com.halo.sdk.provider.Role.SYSTEM ||
                (!request.appendTextAsUserMessage && (first.content == request.text || group === originalRequest)) ||
                (first.toolCalls.isNotEmpty() && first.toolCalls.any { call -> group.none { it.toolCallId == call.id } })
        }
        var remaining = budget.availableDynamicTokens - requestTokens - required.sumOf(::cost)
        if (remaining < 0) exceeded()
        val segments = groups.withIndex().filter { it.value !in required }.map { (ordinal, group) ->
            val first = group.first()
            val kind = when {
                first.toolCalls.isNotEmpty() || first.role == com.halo.sdk.provider.Role.TOOL -> ContextSegmentKind.TOOL_RESULT
                first.content.startsWith("User-selected reference") -> ContextSegmentKind.WORKING_SET
                first.content.startsWith("Reference data") -> ContextSegmentKind.RETRIEVED_DOCUMENT
                first.content.contains("CHILD_AGENT_RESULT") -> ContextSegmentKind.CHILD_AGENT_RESULT
                else -> ContextSegmentKind.RECENT_CONVERSATION
            }
            ContextSegment(ordinal.toString(), kind, if (kind == ContextSegmentKind.WORKING_SET) 10000 + ordinal else ordinal,
                cost(group), SegmentContent.InlineText(first.content), ContextProvenance("provider-context", ContextTrust.EXTERNAL_UNTRUSTED, false))
        }
        val admitted = allocate(ContextWindowBudget(window, output, budget.reservedSystemTokens, remaining), 0, segments)
        val selectedIds = admitted.map { it.id.toInt() }.toSet()
        val selected = groups.withIndex().filter { it.index in selectedIds || it.value in required }.flatMap { it.value }
        remaining -= admitted.sumOf { it.tokenEstimate }
        val segmentTokens = admitted.groupBy { it.kind }.mapValues { (_, values) -> values.sumOf { it.tokenEstimate } }.toMutableMap()
        segmentTokens[ContextSegmentKind.MEMORY] = context.memories.sumOf { estimator.estimate(it.content) }
        segmentTokens[ContextSegmentKind.CURRENT_REQUEST] = requestTokens + required.filter { it.first().role != com.halo.sdk.provider.Role.SYSTEM }.sumOf(::cost)
        segmentTokens[ContextSegmentKind.SYSTEM_STABLE] = 256 + schemaTokens + required.filter { it.first().role == com.halo.sdk.provider.Role.SYSTEM }.sumOf(::cost)
        ContextDiagnostics.composition.value = ContextComposition(budget, requestTokens, segmentTokens, remaining,
            if (selected.any { it.content.startsWith("Prior conversation summary") }) ContextDiagnostics.composition.value?.compactedTurns ?: 0 else 0)
        return context.copy(conversation = selected)
    }
    private fun exceeded(): Nothing = throw com.halo.sdk.provider.AiProviderException(
        com.halo.sdk.provider.AiErrorType.UNSUPPORTED_CAPABILITY, "CONTEXT_BUDGET_EXCEEDED: explicit request, pending tools or schemas exceed the pinned model window")

    fun windowBudget(window: Int, output: Int, system: Int): ContextWindowBudget {
        val total = window.coerceAtLeast(1)
        val reservedOutput = output.coerceIn(0, total)
        val reservedSystem = system.coerceIn(0, total - reservedOutput)
        return ContextWindowBudget(total, reservedOutput, reservedSystem, total - reservedOutput - reservedSystem)
    }

    /** Request/system reservations precede all dynamic context; explicit Working Set wins ties. */
    fun allocate(budget: ContextWindowBudget, requestTokens: Int, segments: List<ContextSegment>): List<ContextSegment> {
        var remaining = (budget.availableDynamicTokens - requestTokens.coerceAtLeast(0)).coerceAtLeast(0)
        return segments.sortedWith(compareByDescending<ContextSegment> { it.priority }.thenBy { it.kind.ordinal })
            .filter { segment ->
                val tokens = segment.tokenEstimate.coerceAtLeast(0)
                if (tokens <= remaining) { remaining -= tokens; true } else false
            }
    }

    fun computePlan(
        modelContextWindowTokens: Int,
        mode: CognitiveOperatingMode,
        policy: AiContextPolicy,
    ): CognitiveContextPlan {
        val total = modelContextWindowTokens.coerceAtLeast(1)
        val responseReserve = (total * 0.20).toInt().coerceAtMost(2048)
        val framingReserve = (total * 0.10).toInt().coerceAtMost(1024)

        val available = total - responseReserve - framingReserve

        val toolBudget = if (policy.includeAvailableTools) (available * 0.20).toInt().coerceAtMost(2048) else 0
        val explicitContextBudget = (available * 0.25).toInt().coerceAtMost(3072)
        val memoryBudget = if (policy.includeMemories) (available * 0.15).toInt().coerceAtMost(1500) else 0
        val conversationBudget = (available - toolBudget - explicitContextBudget - memoryBudget).coerceAtLeast(0)

        return CognitiveContextPlan(
            totalTokens = total,
            responseReserveTokens = responseReserve,
            framingReserveTokens = framingReserve,
            toolBudgetTokens = toolBudget,
            explicitContextBudgetTokens = explicitContextBudget,
            memoryBudgetTokens = memoryBudget,
            conversationBudgetTokens = conversationBudget,
        )
    }
}
