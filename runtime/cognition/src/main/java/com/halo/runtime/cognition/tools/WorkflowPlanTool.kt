package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.agent.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import java.time.Instant

/** A model can propose work, but only the existing execution journal can attest its outcomes. */
class WorkflowPlanTool(private val store: AgentRunStore, private val coordinator: AgentRunCoordinator) : ToolExecutor {
    override fun definitions() = listOf(DEFINITION)
    override suspend fun execute(toolCall: ToolCall) = execute(toolCall, ToolExecutionContext())
    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, ID, reason, ToolRejectionKind.INVALID_ARGUMENTS)
        if (toolCall.functionName != ID || toolCall.arguments.keys != setOf("summary", "steps")) return reject("Use summary and steps only")
        val summary = toolCall.arguments["summary"] as? String ?: return reject("A short plan summary is required")
        val steps = (toolCall.arguments["steps"] as? List<*>)?.map { it as? String ?: return reject("Steps must be text") }
            ?: return reject("Ordered steps are required")
        if (summary.isBlank() || summary.length > 240 || steps.size !in 1..12 || steps.any { it.isBlank() || it.length > 180 })
            return reject("Use 1–12 concise steps, each at most 180 characters")
        val run = store.nonTerminalRuns().singleOrNull { it.interactionId.value == context.interactionId && it.parentRunId == null }
            ?: return ToolExecutionResult.Rejected(toolCall.id, ID, "No unique active user workflow", ToolRejectionKind.PERMISSION_REQUIRED)
        val planId = "workflow:${run.id.value}:${toolCall.id}"
        val existing = run.steps.filterIsInstance<AgentStep.PlanCreated>().find { it.planId == planId }
        if (existing != null && (existing.summary != summary || existing.steps != steps)) return reject("A recorded plan cannot change under the same call identity")
        if (existing == null) check(coordinator.recordStep(AgentStep.PlanCreated(AgentStepId(planId), run.id, Instant.now(), planId, summary, steps)))
        return ToolExecutionResult.Success(toolCall.id, ID, ToolArgumentCanonicalizer.canonicalize(mapOf(
            "planId" to planId, "recorded" to true, "stepCount" to steps.size,
            "executionStatus" to "PLANNED", "instruction" to "Now execute these steps with registered tools. Report only observed receipts. Planning grants no approval and completes no action.")))
    }
    companion object {
        const val ID = "halo_workflow_plan"
        val DEFINITION = ToolDefinition(ID, ID,
            "Before changing anything, record a short ordered workflow visible to the user, then execute it through the appropriate tools and verify receipts. Also use for multi-step research or a full-day recap. Simple text answers need no plan. This records intended steps only; it never executes them or marks them complete. Amend with a new call when the remaining plan changes. Do not include private reasoning, credentials or secrets.",
            mapOf("type" to "object", "properties" to mapOf(
                "summary" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 240),
                "steps" to mapOf("type" to "array", "minItems" to 1, "maxItems" to 12,
                    "items" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 180))),
                "required" to listOf("summary", "steps"), "additionalProperties" to false))
        fun descriptor() = ToolDescriptor(ToolId(ID), DEFINITION, ToolCategory.SYSTEM,
            keywords = setOf("workflow", "plan", "steps", "progress"), visibility = ToolVisibility.BOOTSTRAP,
            security = ToolSecurityPolicy(ToolRiskClass.READ_ONLY, ConfirmationPolicy.NONE, ReplaySafety.NEVER_REPLAY),
            presentation = ToolPresentation("Plan your request", sensitiveArguments = setOf("summary", "steps")),
            operability = ToolOperability(capabilityId = CapabilityId("runtime.workflow"), owner = "AgentRun journal", operation = ToolOperation.PRESENT,
                resultEncoding = ToolResultEncoding.JSON, resultSchema = mapOf("type" to "object", "properties" to mapOf(
                    "planId" to mapOf("type" to "string"), "recorded" to mapOf("type" to "boolean"), "stepCount" to mapOf("type" to "integer"),
                    "executionStatus" to mapOf("type" to "string"), "instruction" to mapOf("type" to "string"))),
                resultIdFields = setOf("planId"), limitations = setOf("Plan is intended work, not execution evidence or approval.")))
    }
}
