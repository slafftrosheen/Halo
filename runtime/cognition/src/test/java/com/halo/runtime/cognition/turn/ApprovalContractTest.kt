package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentInputResolution
import com.halo.runtime.cognition.agent.AgentInputRequestId
import com.halo.runtime.cognition.agent.AgentRequest
import com.halo.runtime.cognition.agent.AgentRun
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.runtime.cognition.agent.AgentRunStore
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.AgentStepId
import com.halo.runtime.cognition.agent.DefaultAgentRunCoordinator
import com.halo.runtime.cognition.tools.ConfirmationPolicy
import com.halo.runtime.cognition.tools.PolicyToolAuthorizer
import com.halo.runtime.cognition.tools.ToolApprovalPolicy
import com.halo.runtime.cognition.tools.ToolCategory
import com.halo.runtime.cognition.tools.ToolDescriptor
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.runtime.cognition.tools.ToolId
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.runtime.cognition.tools.ToolSecurityPolicy
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolRiskClass
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HaloEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

private class TestEventBus : EventBus {
    val published = mutableListOf<HaloEvent>()
    override suspend fun publish(event: HaloEvent) { published.add(event) }
    override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = flow {}
    override fun events(): Flow<HaloEvent> = flow {}
}

private val AgentRunState.isTerminalState: Boolean
    get() = this in setOf(AgentRunState.COMPLETED, AgentRunState.FAILED, AgentRunState.CANCELLED, AgentRunState.INTERRUPTED)

private class TestRunStore : AgentRunStore {
    val rows = MutableStateFlow<List<AgentRun>>(emptyList())
    override fun observeRuns(): Flow<List<AgentRun>> = rows
    override suspend fun get(runId: AgentRunId): AgentRun? = rows.value.find { it.id == runId }
    override suspend fun create(run: AgentRun) { rows.value += run }
    override suspend fun update(run: AgentRun) { rows.value = rows.value.map { if (it.id == run.id) run else it } }
    override suspend fun nonTerminalRuns(): List<AgentRun> = rows.value.filterNot { it.state.isTerminalState }
    override suspend fun claimResume(runId: AgentRunId, expectedState: AgentRunState): Boolean {
        val run = rows.value.find { it.id == runId } ?: return false
        if (run.state != expectedState) return false
        update(run.copy(state = AgentRunState.EXECUTING))
        return true
    }
    override suspend fun appendStep(step: AgentStep): Boolean {
        val run = requireNotNull(get(step.runId))
        if (run.steps.any { it.id == step.id }) return false
        update(run.copy(steps = run.steps + step, currentStepId = step.id))
        return true
    }
    override suspend fun resolveInputIfPending(
        requestId: AgentInputRequestId,
        resolution: AgentInputResolution,
    ): Boolean {
        val run = rows.value.find { r -> r.steps.any { it is AgentStep.InputRequested && it.requestId == requestId } } ?: return false
        val now = Instant.now()
        val step = AgentStep.InputResolved(
            id = AgentStepId(UUID.randomUUID().toString()),
            runId = run.id,
            createdAt = now,
            requestId = requestId,
            resolution = when (resolution) {
                AgentInputResolution.Approved -> "Approved"
                AgentInputResolution.Denied -> "Denied"
                AgentInputResolution.Cancelled -> "Cancelled"
                AgentInputResolution.Expired -> "Expired"
                is AgentInputResolution.Clarified -> resolution.value
            },
        )
        val updatedRun = run.copy(steps = run.steps + step, currentStepId = step.id, state = AgentRunState.EXECUTING, updatedAt = now)
        update(updatedRun)
        return true
    }
}

class ApprovalContractTest {
    @Test fun directCommandAuthorityRespectsDescriptorConfirmationAndRisk() = runTest {
        val store = TestRunStore()
        val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        val interaction = InteractionId("direct-policy")
        val runId = coordinator.start(AgentRequest(interactionId = interaction, text = "set volume"))
        coordinator.recordStep(AgentStep.DirectCommandAuthorityRecorded(
            AgentStepId("authority"), runId, Instant.now(),
            com.halo.runtime.cognition.agent.DirectUserCommandAuthority(
                interaction, "halo_volume", "matching-digest",
                com.halo.runtime.cognition.blackboard.InteractionOrigin(com.halo.sdk.runtime.Source.USER), Instant.now())))
        val call = ToolCall("direct", "halo_volume", mapOf("percent" to 30))
        suspend fun authorize(risk: ToolRiskClass, confirmation: ConfirmationPolicy, digest: String = "matching-digest"):
            com.halo.runtime.cognition.tools.AuthorizationDecision {
            val authorizer = PolicyToolAuthorizer(agentRunStore = store, descriptorProvider = {
                ToolDescriptor(ToolId("halo_volume"), ToolDefinition("halo_volume", "halo_volume", "Volume"),
                    ToolCategory.DEVICE_CONTROL, security = ToolSecurityPolicy(risk, confirmation))
            })
            return authorizer.authorizeDurable(call, runId, interaction,
                com.halo.runtime.cognition.agent.ToolInvocationId("invocation"), digest, newRequestId = { "approval" })
        }
        assertTrue(authorize(ToolRiskClass.REVERSIBLE, ConfirmationPolicy.USER_POLICY) is com.halo.runtime.cognition.tools.AuthorizationDecision.Authorized)
        assertTrue(authorize(ToolRiskClass.REVERSIBLE, ConfirmationPolicy.ALWAYS) is com.halo.runtime.cognition.tools.AuthorizationDecision.NeedsUserInput)
        assertTrue(authorize(ToolRiskClass.SENSITIVE, ConfirmationPolicy.USER_POLICY) is com.halo.runtime.cognition.tools.AuthorizationDecision.NeedsUserInput)
        assertTrue(authorize(ToolRiskClass.DESTRUCTIVE, ConfirmationPolicy.USER_POLICY) is com.halo.runtime.cognition.tools.AuthorizationDecision.NeedsUserInput)
        assertTrue(authorize(ToolRiskClass.REVERSIBLE, ConfirmationPolicy.USER_POLICY, "different") is com.halo.runtime.cognition.tools.AuthorizationDecision.NeedsUserInput)
    }

    @Test fun alwaysConfirmCannotReuseLegacyScopeApproval() = runTest {
        var approvals = 0
        val authorizer = PolicyToolAuthorizer(
            coordinator = object : com.halo.runtime.cognition.tools.ToolAuthorizationCoordinator {
                override suspend fun authorizeRequest(toolCall: ToolCall, preview: com.halo.runtime.cognition.tools.ToolActionPreview): com.halo.runtime.cognition.tools.ToolAuthorization {
                    approvals++
                    return com.halo.runtime.cognition.tools.ToolAuthorization.Authorized(toolCall)
                }
            }, descriptorProvider = {
                ToolDescriptor(ToolId("test_action"), ToolDefinition("test_action", "test_action", "Action"),
                    ToolCategory.SYSTEM, security = ToolSecurityPolicy(ToolRiskClass.EXTERNAL_SIDE_EFFECT, ConfirmationPolicy.ALWAYS))
            })
        val call = ToolCall("one", "test_action", emptyMap())
        authorizer.authorize(call, "scope")
        authorizer.authorize(call.copy(id = "two"), "scope")
        assertEquals(2, approvals)
    }

    private fun createTestTool(onExecute: () -> Unit): ToolExecutor {
        return object : ToolExecutor {
            override fun definitions(): List<ToolDefinition> {
                return listOf(
                    ToolDefinition(
                        id = "test_action",
                        name = "test_action",
                        description = "Test action needing approval"
                    )
                )
            }

            override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
                onExecute()
                return ToolExecutionResult.Success(
                    toolCallId = toolCall.id,
                    toolId = toolCall.functionName,
                    output = "Action executed successfully"
                )
            }
        }
    }

    private fun createTestRegistry(toolExecutor: ToolExecutor): ToolRegistry {
        val definition = toolExecutor.definitions().first()
        val descriptor = ToolDescriptor(
            id = ToolId(definition.id),
            definition = definition,
            category = ToolCategory.SYSTEM,
            security = ToolSecurityPolicy(
                risk = ToolRiskClass.EXTERNAL_SIDE_EFFECT,
                confirmation = ConfirmationPolicy.ALWAYS
            )
        )
        return ToolRegistry(listOf(toolExecutor), descriptors = listOf(descriptor))
    }

    @Test
    fun `approval flow - needs approval then approves and executes exactly once`() = runTest {
        var executionCount = 0
        val toolExecutor = createTestTool { executionCount++ }
        val registry = createTestRegistry(toolExecutor)
        val store = TestRunStore()
        val eventBus = TestEventBus()
        val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)

        val authorizer = PolicyToolAuthorizer(
            descriptorProvider = { name -> registry.descriptor(name) }
        )

        val executor = DefaultToolTurnExecutor(
            toolRegistry = registry,
            toolAuthorizer = authorizer,
            toolResourceCoordinator = null,
            agentRunCoordinator = coordinator,
            agentRunStore = store,
            eventBus = eventBus
        )

        val interactionId = InteractionId("interaction-1")
        val runId = coordinator.start(
            AgentRequest(interactionId = interactionId, text = "do action")
        )
        coordinator.transition(runId, AgentRunState.PLANNING)
        coordinator.transition(runId, AgentRunState.EXECUTING)

        val toolCall = ToolCall(id = "call-1", functionName = "test_action", arguments = mapOf("param" to "val"))

        // Turn 1: Asks for approval
        val result1 = executor.execute(
            runId = runId,
            interactionId = interactionId,
            requestId = "req-1",
            toolCalls = listOf(toolCall),
            workspaceReadAllowed = true
        )

        println("DEBUG_RESULT1 = $result1")
        assertTrue("Expected WaitingForInput but got $result1", result1 is ToolTurnResult.WaitingForInput)
        assertEquals(0, executionCount)

        val runAfterTurn1 = store.get(runId)!!
        assertEquals(AgentRunState.WAITING_FOR_APPROVAL, runAfterTurn1.state)
        val inputReqStep = runAfterTurn1.steps.filterIsInstance<AgentStep.InputRequested>().firstOrNull()
        assertNotNull(inputReqStep)

        // User approves
        store.resolveInputIfPending(inputReqStep!!.requestId, AgentInputResolution.Approved)

        // Turn 2: Continuation after approval
        val result2 = executor.execute(
            runId = runId,
            interactionId = interactionId,
            requestId = "req-2",
            toolCalls = listOf(toolCall),
            workspaceReadAllowed = true
        )

        println("DEBUG_RESULT2 = $result2")
        assertTrue("Expected Completed but got $result2", result2 is ToolTurnResult.Completed)
        assertEquals(1, executionCount)

        val runAfterTurn2 = store.get(runId)!!
        val claims = runAfterTurn2.steps.filterIsInstance<AgentStep.ToolExecutionClaimed>()
        assertEquals(1, claims.size)
        val outcomes = runAfterTurn2.steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>()
        assertEquals(1, outcomes.size)
    }

    @Test
    fun `approval flow - needs approval then denies and executes zero times`() = runTest {
        var executionCount = 0
        val toolExecutor = createTestTool { executionCount++ }
        val registry = createTestRegistry(toolExecutor)
        val store = TestRunStore()
        val eventBus = TestEventBus()
        val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)

        val authorizer = PolicyToolAuthorizer(
            descriptorProvider = { name -> registry.descriptor(name) }
        )

        val executor = DefaultToolTurnExecutor(
            toolRegistry = registry,
            toolAuthorizer = authorizer,
            toolResourceCoordinator = null,
            agentRunCoordinator = coordinator,
            agentRunStore = store,
            eventBus = eventBus
        )

        val interactionId = InteractionId("interaction-2")
        val runId = coordinator.start(
            AgentRequest(interactionId = interactionId, text = "do action")
        )
        coordinator.transition(runId, AgentRunState.PLANNING)
        coordinator.transition(runId, AgentRunState.EXECUTING)

        val toolCall = ToolCall(id = "call-1", functionName = "test_action", arguments = mapOf("param" to "val"))

        // Turn 1: Asks for approval
        val result1 = executor.execute(runId, interactionId, "req-1", listOf(toolCall), true)
        assertTrue(result1 is ToolTurnResult.WaitingForInput)

        val inputReqStep = store.get(runId)!!.steps.filterIsInstance<AgentStep.InputRequested>().first()

        // User denies
        store.resolveInputIfPending(inputReqStep.requestId, AgentInputResolution.Denied)

        // Turn 2: Continuation after denial
        val result2 = executor.execute(runId, interactionId, "req-2", listOf(toolCall), true)

        assertTrue(result2 is ToolTurnResult.Completed)
        assertEquals(0, executionCount)

        val runAfterTurn2 = store.get(runId)!!
        val claims = runAfterTurn2.steps.filterIsInstance<AgentStep.ToolExecutionClaimed>()
        assertEquals(0, claims.size)
        val rejections = runAfterTurn2.steps.filterIsInstance<AgentStep.ToolRejected>()
        assertEquals(1, rejections.size)
        assertEquals("USER_REJECTED", rejections.first().reasonCode.name)
    }
}
