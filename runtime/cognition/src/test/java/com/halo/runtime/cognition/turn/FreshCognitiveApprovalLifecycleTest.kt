package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.context.ContextAcquisitionPlan
import com.halo.runtime.cognition.tools.*
import com.halo.runtime.cognition.workload.CognitiveWorkloadPlan
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** Real fresh cognitive caller and approval lifecycle; no provider, database or Canvas writes. */
class FreshCognitiveApprovalLifecycleTest {
    private class Store : AgentRunStore {
        val rows = MutableStateFlow<List<AgentRun>>(emptyList())
        override fun observeRuns() = rows
        override suspend fun get(runId: AgentRunId) = rows.value.singleOrNull { it.id == runId }
        override suspend fun create(run: AgentRun) { rows.value += run }
        override suspend fun update(run: AgentRun) { rows.value = rows.value.map { if (it.id == run.id) run else it } }
        override suspend fun nonTerminalRuns() = rows.value.filterNot { it.state.isTerminal }
        override suspend fun appendStep(step: AgentStep): Boolean {
            val run = get(step.runId) ?: return false
            if (run.steps.any { it.id == step.id }) return false
            update(run.copy(steps = run.steps + step, currentStepId = step.id))
            return true
        }
        override suspend fun claimResume(runId: AgentRunId, expectedState: AgentRunState): Boolean {
            val run = get(runId) ?: return false
            if (run.state != expectedState || run.state.isTerminal) return false
            update(run.copy(state = AgentRunState.EXECUTING))
            return true
        }
        override suspend fun resolveInputIfPending(requestId: AgentInputRequestId, resolution: AgentInputResolution): Boolean {
            val run = rows.value.singleOrNull { row -> row.steps.any { it is AgentStep.InputRequested && it.requestId == requestId } } ?: return false
            if (run.state.isTerminal || run.steps.any { it is AgentStep.InputResolved && it.requestId == requestId }) return false
            return appendStep(AgentStep.InputResolved(AgentStepId(UUID.randomUUID().toString()), run.id, Instant.now(),
                requestId, AgentInputResolutions.encode(resolution)))
        }
    }

    private class Fixture(scope: CoroutineScope) {
        val store = Store()
        val coordinator = DefaultAgentRunCoordinator(store, scope)
        val interaction = InteractionId("synthetic-fresh-export")
        val definition = ToolDefinition("halo_canvas_export_note", "halo_canvas_export_note", "Synthetic approval-bound export",
            mapOf("type" to "object", "properties" to mapOf(
                "canvasId" to mapOf("type" to "string"), "version" to mapOf("type" to "string"),
                "startStudy" to mapOf("type" to "boolean")), "required" to listOf("canvasId", "version"), "additionalProperties" to false))
        val call = ToolCall("synthetic-export-call", definition.name,
            mapOf("canvasId" to "00000000-0000-4000-8000-000000000021", "version" to "canvas:3", "startStudy" to false))
        var executions = 0
        var providerCalls = 0
        val bus = object : EventBus {
            override suspend fun publish(event: HaloEvent) {}
            override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
            override fun events(): Flow<HaloEvent> = emptyFlow()
        }
        val registry = ToolRegistry(listOf(object : ToolExecutor {
            override fun definitions() = listOf(definition)
            override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
                executions++
                return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Synthetic confirmed receipt", ActionTruth.CONFIRMED)
            }
        }), listOf(ToolDescriptor(ToolId(definition.id), definition, ToolCategory.HOST_CONTROL,
            security = ToolSecurityPolicy(ToolRiskClass.SENSITIVE, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY))))
        val tools = DefaultToolTurnExecutor(registry, PolicyToolAuthorizer(
            defaultPolicyProvider = { ToolApprovalPolicy.ALWAYS_ALLOW }, descriptorProvider = registry::descriptor,
            agentRunStore = store), null, coordinator, store, bus)
        private val provider = object : AiInferenceProvider {
            override val id = "synthetic-provider"
            override val displayName = "Synthetic provider"
            override val isLocal = false
            override val status = MutableStateFlow(ProviderStatus.READY)
            override suspend fun getStatus() = status.value
            override suspend fun getCapabilities() = setOf(AiCapability.TEXT, AiCapability.TOOLS)
            override suspend fun listModels() = emptyList<AiModel>()
            override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = error("No provider execution")
            override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = error("No provider execution")
        }
        private val target = AiExecutionTarget(provider, AiModel(id = "synthetic-model", displayName = "Synthetic model",
            providerId = provider.id, inputCapabilities = setOf(AiCapability.TEXT), contextLength = 4096,
            supportsTools = true, supportsStreaming = false, executionLocation = AiExecutionLocation.REMOTE), setOf(AiCapability.TEXT, AiCapability.TOOLS))
        private val inference = object : ProviderTurnExecutor {
            override val streamingText = MutableStateFlow<String?>(null)
            override suspend fun execute(target: AiExecutionTarget, request: AiRequest, context: AiContext,
                requestId: String, interactionId: InteractionId): ProviderTurnResult {
                providerCalls++
                return ProviderTurnResult.Response(AiResponse("", listOf(call), "tool_calls", provider.id), target)
            }
        }
        private val cognitive = CognitiveTurnExecutor(object : CognitiveTurnPreparer {
            override suspend fun prepare(turn: CognitiveTurnRequest) = PreparedCognitiveTurn(turn,
                CognitiveWorkloadPlan(AiWorkloadSpec(kind = AiWorkloadKind.TOOL_ASSISTED, tools = AiFeatureRequirement.REQUIRED),
                    ContextAcquisitionPlan(interaction, emptyList())), target,
                AiContext(availableTools = listOf(definition)), turn.request, emptySet(), emptySet())
        }, inference, tools, coordinator, store, bus)

        suspend fun freshApproval(): AgentStep.InputRequested {
            // Deliberately no test-side start/transition: the real cognitive caller owns admission.
            val commit = cognitive.execute(CognitiveTurnRequest(interaction, "synthetic-request",
                AiRequest(text = "Export the exact synthetic Canvas", toolUseMode = ToolUseMode.REQUIRED),
                AiContextPolicy(includeAvailableTools = true, allowCloudWorkspace = true), ResponseDelivery.VISUAL_ONLY, null, emptyMap()))
            assertFalse("A pending approval is not a terminal response", commit.terminal)
            assertEquals(0, executions)
            assertEquals(1, providerCalls)
            val run = requireNotNull(store.get(requireNotNull(commit.runId)))
            assertEquals(AgentRunState.WAITING_FOR_APPROVAL, run.state)
            val pending = run.steps.filterIsInstance<AgentStep.InputRequested>().single()
            val approval = pending.input as AgentInputRequest.Approval
            assertEquals(ToolId(definition.name), approval.toolId)
            assertEquals("SENSITIVE", approval.risk)
            assertTrue(run.steps.none { it is AgentStep.ToolExecutionClaimed || it is AgentStep.ToolDispatched })
            return pending
        }
        suspend fun continueCall(run: AgentRunId, request: ToolCall = call) =
            tools.execute(run, interaction, "synthetic-resume", listOf(request), true, setOf(definition.name))
    }

    @Test fun freshCognitiveRunCanWaitForAlwaysApprovalWithoutExecutingOrRelaxingAlwaysAllow() = runTest {
        Fixture(backgroundScope).freshApproval()
    }

    @Test fun actualApprovalAllowsOneExecutionAndDuplicateResolutionOrDispatchDoesNotRepeatIt() = runTest {
        val f = Fixture(backgroundScope)
        val pending = f.freshApproval()
        assertTrue(f.store.resolveInputIfPending(pending.requestId, AgentInputResolution.Approved))
        assertFalse(f.store.resolveInputIfPending(pending.requestId, AgentInputResolution.Approved))
        f.coordinator.resume(pending.runId)
        assertTrue(f.continueCall(pending.runId) is ToolTurnResult.Completed)
        assertEquals(1, f.executions)
        f.continueCall(pending.runId)
        assertEquals("NEVER_REPLAY must retain the completed execution claim", 1, f.executions)
    }

    @Test fun denialCancellationAndExpiryNeverExecuteThePreparedAction() = runTest {
        listOf(AgentInputResolution.Denied, AgentInputResolution.Cancelled, AgentInputResolution.Expired).forEach { resolution ->
            val f = Fixture(backgroundScope)
            val pending = f.freshApproval()
            assertTrue(f.store.resolveInputIfPending(pending.requestId, resolution))
            val resumed = f.continueCall(pending.runId)
            assertTrue(resumed is ToolTurnResult.Completed)
            assertEquals(0, f.executions)
        }
    }

    @Test fun approvalForOriginalRevisionDoesNotAuthorizeChangedArguments() = runTest {
        val f = Fixture(backgroundScope)
        val pending = f.freshApproval()
        assertTrue(f.store.resolveInputIfPending(pending.requestId, AgentInputResolution.Approved))
        f.coordinator.resume(pending.runId)
        val changed = f.call.copy(id = "synthetic-changed-call", arguments = f.call.arguments + ("version" to "canvas:4"))
        val result = f.continueCall(pending.runId, changed)
        assertTrue("A different observed source version needs its own approval", result is ToolTurnResult.WaitingForInput)
        assertEquals(0, f.executions)
    }

    @Test fun coordinatorStillRejectsShortcutFromUnadmittedCreatedRunToApproval() = runTest {
        val f = Fixture(backgroundScope)
        val run = f.coordinator.start(AgentRequest(f.interaction, "Synthetic unadmitted run"))
        val failure = runCatching { f.coordinator.transition(run, AgentRunState.WAITING_FOR_APPROVAL) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(AgentRunState.CREATED, f.store.get(run)!!.state)
        assertEquals(0, f.executions)
    }
}
