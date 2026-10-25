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
    private class FakeRunClock : java.time.Clock(), AgentRunClock {
        var timeMs = 1_800_000_000_000L
        override fun nowMs() = timeMs
        override fun instant(): Instant = Instant.ofEpochMilli(timeMs)
        override fun getZone(): java.time.ZoneId = java.time.ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): java.time.Clock = this
        fun advance(ms: Long) { require(ms >= 0); timeMs += ms }
    }
    private class Store(private val clock: FakeRunClock) : AgentRunStore {
        val rows = MutableStateFlow<List<AgentRun>>(emptyList())
        override fun observeRuns() = rows
        override suspend fun get(runId: AgentRunId) = rows.value.singleOrNull { it.id == runId }
        override suspend fun create(run: AgentRun) { rows.value += run }
        override suspend fun update(run: AgentRun) { rows.value = rows.value.map { if (it.id == run.id) run else it } }
        override suspend fun nonTerminalRuns() = rows.value.filterNot { it.state.isTerminal }
        override suspend fun appendStep(step: AgentStep): Boolean {
            val run = get(step.runId) ?: return false
            if (run.steps.any { it.id == step.id }) return false
            // Correlate durable approval boundaries with the same injected run clock.
            val timed = if (step is AgentStep.InputRequested) step.copy(createdAt = clock.instant(),
                input = (step.input as? AgentInputRequest.Approval)?.copy(createdAt = clock.instant()) ?: step.input) else step
            update(run.copy(steps = run.steps + timed, currentStepId = step.id))
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
            return appendStep(AgentStep.InputResolved(AgentStepId(UUID.randomUUID().toString()), run.id, clock.instant(),
                requestId, AgentInputResolutions.encode(resolution)))
        }
    }

    private class Fixture(scope: CoroutineScope, val restoreExposure: Boolean = false) {
        val clock = FakeRunClock()
        val store = Store(clock)
        val coordinator = DefaultAgentRunCoordinator(store, scope, clock = clock)
        val guards = AgentRunGuardRegistry(limits = { AgentRunLimits.INTERACTIVE.copy(maxWallClockMs = 1000L) }, clock = clock)
        val interaction = InteractionId("synthetic-fresh-export")
        val definition = ToolDefinition("halo_canvas_export_note", "halo_canvas_export_note", "Synthetic approval-bound export",
            mapOf("type" to "object", "properties" to mapOf(
                "canvasId" to mapOf("type" to "string"), "version" to mapOf("type" to "string"),
                "startStudy" to mapOf("type" to "boolean")), "required" to listOf("canvasId", "version"), "additionalProperties" to false))
        val call = ToolCall("synthetic-export-call", definition.name,
            mapOf("canvasId" to "00000000-0000-4000-8000-000000000021", "version" to "canvas:3", "startStudy" to false))
        var executions = 0
        var providerCalls = 0
        var providerActiveMs = 0L
        var executionActiveMs = 0L
        var exposeOnResume = true
        val bus = object : EventBus {
            override suspend fun publish(event: HaloEvent) {}
            override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
            override fun events(): Flow<HaloEvent> = emptyFlow()
        }
        val registry = ToolRegistry(listOf(object : ToolExecutor {
            override fun definitions() = listOf(definition)
            override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
                executions++
                clock.advance(executionActiveMs)
                return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Synthetic confirmed receipt", ActionTruth.CONFIRMED)
            }
        }), listOf(ToolDescriptor(ToolId(definition.id), definition, ToolCategory.HOST_CONTROL,
            security = ToolSecurityPolicy(ToolRiskClass.SENSITIVE, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY))))
        val tools = DefaultToolTurnExecutor(registry, PolicyToolAuthorizer(
            defaultPolicyProvider = { ToolApprovalPolicy.ALWAYS_ALLOW }, descriptorProvider = registry::descriptor,
            agentRunStore = store), null, coordinator, store, bus, runGuards = guards)
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
                clock.advance(providerActiveMs)
                val response = if (request.turnKind == ProviderTurnKind.TOOL_CONTINUATION)
                    AiResponse("Synthetic final response", providerId = provider.id)
                else AiResponse("", listOf(call), "tool_calls", provider.id)
                return ProviderTurnResult.Response(response, target)
            }
        }
        private val cognitive = CognitiveTurnExecutor(object : CognitiveTurnPreparer {
            override suspend fun prepare(turn: CognitiveTurnRequest) = PreparedCognitiveTurn(turn,
                CognitiveWorkloadPlan(AiWorkloadSpec(kind = AiWorkloadKind.TOOL_ASSISTED, tools = AiFeatureRequirement.REQUIRED),
                    ContextAcquisitionPlan(interaction, emptyList())), target,
                AiContext(availableTools = if (turn.mode == CognitiveTurnMode.FRESH || (exposeOnResume && !restoreExposure)) listOf(definition) else emptyList()),
                turn.request, emptySet(), emptySet())
        }, inference, tools, coordinator, store, bus, toolExposurePlanner = if (restoreExposure) ToolExposurePlanner(registry) else null,
            toolExposureGate = if (restoreExposure) ToolExposureGate { _, _ ->
                if (exposeOnResume) ToolExposureDecision.Allow else ToolExposureDecision.Deny(ToolExposureDenyReason.POLICY_DENIED)
            } else null, runGuards = guards)

        suspend fun startFresh() = cognitive.execute(CognitiveTurnRequest(interaction, "synthetic-request",
            AiRequest(text = "Export the exact synthetic Canvas", toolUseMode = ToolUseMode.REQUIRED),
            AiContextPolicy(includeAvailableTools = true, allowCloudWorkspace = true), ResponseDelivery.VISUAL_ONLY, null, emptyMap()))

        suspend fun resumeCognitive(runId: AgentRunId) = cognitive.execute(CognitiveTurnRequest(interaction, "synthetic-resumed-request",
            AiRequest(text = "", toolUseMode = ToolUseMode.REQUIRED),
            AiContextPolicy(includeAvailableTools = true, allowCloudWorkspace = true), ResponseDelivery.VISUAL_ONLY, null, emptyMap(),
            runId = runId, mode = CognitiveTurnMode.CONTINUE_AFTER_TOOL))

        suspend fun freshApproval(): AgentStep.InputRequested {
            // Deliberately no test-side start/transition: the real cognitive caller owns admission.
            val commit = startFresh()
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

    @Test fun approvedPendingDeclarationRecoversOnlyItsPreviouslyOfferedSchemaThroughFreshPlanner() = runTest {
        val f = Fixture(backgroundScope, restoreExposure = true)
        val pending = f.freshApproval()
        assertTrue(f.store.resolveInputIfPending(pending.requestId, AgentInputResolution.Approved))
        f.coordinator.resume(pending.runId)
        f.resumeCognitive(pending.runId)
        assertEquals("Bootstrap-only preparation must recover the exact pending offered schema", 1, f.executions)
        assertTrue(f.store.get(pending.runId)!!.steps.none { it is AgentStep.ToolRejected && it.reasonCode == ToolRejectionCode.TOOL_NOT_EXPOSED })
    }

    @Test fun recoveringPendingExposureStillHonorsFreshPolicyDenialAndMissingOriginalOffer() = runTest {
        for (denyPolicy in listOf(true, false)) {
            val f = Fixture(backgroundScope, restoreExposure = true)
            val pending = f.freshApproval()
            assertTrue(f.store.resolveInputIfPending(pending.requestId, AgentInputResolution.Approved))
            if (denyPolicy) f.exposeOnResume = false else {
                val run = f.store.get(pending.runId)!!
                f.store.update(run.copy(steps = run.steps.map {
                    if (it is AgentStep.ProviderTurnStarted) it.copy(exposedToolIds = emptySet()) else it
                }))
            }
            f.coordinator.resume(pending.runId)
            f.resumeCognitive(pending.runId)
            assertEquals(0, f.executions)
            assertTrue(f.store.get(pending.runId)!!.steps.filterIsInstance<AgentStep.ToolRejected>()
                .any { it.reasonCode == ToolRejectionCode.TOOL_NOT_EXPOSED })
        }
    }

    @Test fun incompleteAmbiguousAndMismatchedApprovalBoundariesCannotReplenishTime() = runTest {
        for (mutation in listOf("missing", "duplicate", "digest", "future", "reversed")) {
            val f = Fixture(backgroundScope)
            f.providerActiveMs = 200L
            val pending = f.freshApproval()
            f.clock.advance(600_000L)
            assertTrue(f.store.resolveInputIfPending(pending.requestId, AgentInputResolution.Approved))
            val run = f.store.get(pending.runId)!!
            val resolved = run.steps.filterIsInstance<AgentStep.InputResolved>().single()
            val steps = when (mutation) {
                "missing" -> run.steps.filterNot { it is AgentStep.InputResolved }
                "duplicate" -> run.steps + resolved.copy(id = AgentStepId("duplicate"))
                "digest" -> run.steps.map { if (it is AgentStep.InputRequested) it.copy(input =
                    (it.input as AgentInputRequest.Approval).copy(argumentsDigest = "different")) else it }
                "future" -> run.steps.map { if (it == resolved) resolved.copy(createdAt = f.clock.instant().plusSeconds(1)) else it }
                else -> run.steps.map { if (it == resolved) resolved.copy(createdAt = pending.createdAt.minusMillis(1)) else it }
            }
            val guard = f.guards.peek(pending.runId.value)!!
            guard.synchronizeApprovalWait(run.copy(steps = steps))
            assertEquals(mutation, 600_200L, guard.elapsedMs)
        }
    }

    @Test fun expiredApprovalCannotExcludeTimeAfterItsDurableExpiry() = runTest {
        val f = Fixture(backgroundScope)
        f.providerActiveMs = 200L
        val pending = f.freshApproval()
        f.clock.advance(600_000L)
        assertTrue(f.store.resolveInputIfPending(pending.requestId, AgentInputResolution.Expired))
        val run = f.store.get(pending.runId)!!
        val steps = run.steps.map { if (it is AgentStep.InputRequested) it.copy(input =
            (it.input as AgentInputRequest.Approval).copy(expiresAt = pending.createdAt.plusMillis(100))) else it }
        val guard = f.guards.peek(pending.runId.value)!!
        guard.synchronizeApprovalWait(run.copy(steps = steps))
        assertEquals(600_100L, guard.elapsedMs)
    }

    @Test fun longDurableApprovalWaitDoesNotSpendActiveBudgetOrResetCountersBeforeExactApprovedResume() = runTest {
        val f = Fixture(backgroundScope)
        f.providerActiveMs = 200L
        f.executionActiveMs = 300L
        val pending = f.freshApproval()
        val guard = requireNotNull(f.guards.peek(pending.runId.value))
        val rounds = guard.currentToolRounds
        val inputTokens = guard.tokenSnapshot.estimatedInputTokens
        assertEquals(200L, guard.elapsedMs)
        f.clock.advance(600_000L)
        assertTrue(f.store.resolveInputIfPending(pending.requestId, AgentInputResolution.Approved))
        f.coordinator.resume(pending.runId)
        val result = f.resumeCognitive(pending.runId)
        assertEquals("Human review is not active inference or tool execution", 1, f.executions)
        assertTrue(result.terminal)
        assertSame("Approval cannot replenish by replacing the run guard", guard, f.guards.peek(pending.runId.value))
        assertEquals(700L, guard.elapsedMs)
        assertEquals(300L, guard.remainingMs)
        assertEquals(rounds + 1, guard.currentToolRounds)
        assertTrue(guard.tokenSnapshot.estimatedInputTokens >= inputTokens)
        assertTrue(f.store.get(pending.runId)!!.steps.none { it is AgentStep.RunGuardTriggered })
    }

    @Test fun activeTimeBeforeAndAfterApprovalIsRetainedAndRealExhaustionStillBlocksTheEffect() = runTest {
        val f = Fixture(backgroundScope)
        f.providerActiveMs = 800L
        val pending = f.freshApproval()
        val guard = requireNotNull(f.guards.peek(pending.runId.value))
        f.clock.advance(600_000L)
        assertTrue(f.store.resolveInputIfPending(pending.requestId, AgentInputResolution.Approved))
        // Work after the response boundary counts even if resume is scheduled later.
        f.clock.advance(300L)
        f.coordinator.resume(pending.runId)
        f.resumeCognitive(pending.runId)
        assertEquals(0, f.executions)
        assertEquals("Neither prior active spend nor post-resolution delay may be waived", 1100L, guard.elapsedMs)
        assertTrue(f.store.get(pending.runId)!!.steps.filterIsInstance<AgentStep.RunGuardTriggered>()
            .any { it.reason == RunGuardReason.DEADLINE_EXCEEDED })
    }

    @Test fun actualActiveExhaustionBeforeApprovalRemainsAStopWithoutAnyPendingGrant() = runTest {
        val f = Fixture(backgroundScope)
        f.providerActiveMs = 1100L
        val result = f.startFresh()
        val run = f.store.get(requireNotNull(result.runId))!!
        assertEquals(0, f.executions)
        assertTrue(run.steps.none { it is AgentStep.InputRequested })
        assertTrue(run.steps.filterIsInstance<AgentStep.RunGuardTriggered>().any { it.reason == RunGuardReason.DEADLINE_EXCEEDED })
    }

    @Test fun approvedPendingCallStillCannotExecuteWhenFreshExposurePolicyWithholdsItsSchema() = runTest {
        val f = Fixture(backgroundScope)
        val pending = f.freshApproval()
        assertTrue(f.store.resolveInputIfPending(pending.requestId, AgentInputResolution.Approved))
        f.coordinator.resume(pending.runId)
        f.exposeOnResume = false
        f.resumeCognitive(pending.runId)
        assertEquals(0, f.executions)
        assertTrue(f.store.get(pending.runId)!!.steps.filterIsInstance<AgentStep.ToolRejected>()
            .any { it.toolCallId == f.call.id && it.reasonCode == ToolRejectionCode.TOOL_NOT_EXPOSED })
    }
}
