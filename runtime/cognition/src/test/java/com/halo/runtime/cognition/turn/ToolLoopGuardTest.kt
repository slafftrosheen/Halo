package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentInputRequestId
import com.halo.runtime.cognition.agent.AgentInputResolution
import com.halo.runtime.cognition.agent.AgentRequest
import com.halo.runtime.cognition.agent.AgentRun
import com.halo.runtime.cognition.agent.AgentRunCoordinator
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.runtime.cognition.agent.AgentRunStore
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.DefaultAgentRunCoordinator
import com.halo.runtime.cognition.device.CapabilityAffinity
import com.halo.runtime.cognition.device.DefaultDeviceCapabilityResolver
import com.halo.runtime.cognition.device.DefaultResourcePlanner
import com.halo.runtime.cognition.tools.ConfirmationPolicy
import com.halo.runtime.cognition.tools.PolicyToolAuthorizer
import com.halo.runtime.cognition.tools.ReplaySafety
import com.halo.runtime.cognition.tools.ResourceRequirement
import com.halo.runtime.cognition.tools.ToolApprovalPolicy
import com.halo.runtime.cognition.tools.ToolCategory
import com.halo.runtime.cognition.tools.ToolDescriptor
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.runtime.cognition.tools.ToolId
import com.halo.runtime.cognition.tools.ToolLoopLimits
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.runtime.cognition.tools.ToolRejectionKind
import com.halo.runtime.cognition.tools.ToolResourceCoordinator
import com.halo.runtime.cognition.tools.ToolRiskClass
import com.halo.runtime.cognition.tools.ToolSecurityPolicy
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HaloEvent
import com.halo.sdk.runtime.resource.ResourceAccess
import com.halo.sdk.runtime.resource.ResourceAcquireResult
import com.halo.sdk.runtime.resource.ResourceClaim
import com.halo.sdk.runtime.resource.ResourceEndpointId
import com.halo.sdk.runtime.resource.ResourceHolder
import com.halo.sdk.runtime.resource.ResourceId
import com.halo.sdk.runtime.resource.ResourceKey
import com.halo.sdk.runtime.resource.ResourceLease
import com.halo.sdk.runtime.resource.ResourceLeaseId
import com.halo.sdk.runtime.resource.ResourceLeaseManager
import com.halo.sdk.runtime.resource.ResourceLeaseRequest
import com.halo.sdk.runtime.resource.ResourceLeaseSnapshot
import com.halo.sdk.runtime.resource.ResourceYieldHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class LoopTestEventBus : EventBus {
    override suspend fun publish(event: HaloEvent) = Unit
    override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = flow {}
    override fun events(): Flow<HaloEvent> = flow {}
}

private val AgentRunState.isTerminal: Boolean
    get() = this in setOf(AgentRunState.COMPLETED, AgentRunState.FAILED, AgentRunState.CANCELLED, AgentRunState.INTERRUPTED)

private class LoopTestRunStore : AgentRunStore {
    val rows = MutableStateFlow<List<AgentRun>>(emptyList())
    override fun observeRuns(): Flow<List<AgentRun>> = rows
    override suspend fun get(runId: AgentRunId): AgentRun? = rows.value.find { it.id == runId }
    override suspend fun create(run: AgentRun) { rows.value += run }
    override suspend fun update(run: AgentRun) { rows.value = rows.value.map { if (it.id == run.id) run else it } }
    override suspend fun nonTerminalRuns(): List<AgentRun> = rows.value.filterNot { it.state.isTerminal }
    override suspend fun claimResume(runId: AgentRunId, expectedState: AgentRunState): Boolean = false
    override suspend fun appendStep(step: AgentStep): Boolean {
        val run = requireNotNull(get(step.runId))
        if (run.steps.any { it.id == step.id }) return false
        update(run.copy(steps = run.steps + step, currentStepId = step.id))
        return true
    }
    override suspend fun resolveInputIfPending(
        requestId: AgentInputRequestId,
        resolution: AgentInputResolution,
    ): Boolean = false
}

/** Records release() so a leaked lease shows up as a failing assertion. */
private class RecordingLease : ResourceLease {
    override val leaseId = ResourceLeaseId("lease-test")
    override val request = ResourceLeaseRequest(
        requestId = "tool:test",
        holder = ResourceHolder("holder", "interaction"),
        claims = setOf(
            ResourceClaim(
                key = ResourceKey(ResourceId("device.mic"), ResourceEndpointId("host:primary")),
                access = ResourceAccess.EXCLUSIVE,
            )
        ),
    )
    override val acquiredAtMs: Long = System.currentTimeMillis()
    var released = false
        private set
    override val isReleased: Boolean get() = released
    override fun release() { released = true }
}

private class GrantingLeaseManager(private val lease: ResourceLease) : ResourceLeaseManager {
    override val snapshotState = MutableStateFlow(ResourceLeaseSnapshot(emptyList(), emptyList()))
    override fun tryAcquire(request: ResourceLeaseRequest, ownerYieldHandler: ResourceYieldHandler?): ResourceAcquireResult =
        ResourceAcquireResult.Granted(lease)
    override suspend fun acquire(request: ResourceLeaseRequest, ownerYieldHandler: ResourceYieldHandler?): ResourceAcquireResult =
        ResourceAcquireResult.Granted(lease)
    override fun snapshot(): ResourceLeaseSnapshot = ResourceLeaseSnapshot(emptyList(), emptyList())
    override fun release(leaseId: ResourceLeaseId) { lease.release() }
}

class ToolLoopGuardTest {

    private val definition = ToolDefinition(
        id = "halo_flashlight",
        name = "halo_flashlight",
        description = "Set phone flashlight state.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf("state" to mapOf("type" to "string")),
            "required" to listOf("state"),
            "additionalProperties" to false,
        ),
    )

    private fun descriptor(): ToolDescriptor = ToolDescriptor(
        id = ToolId(definition.id),
        definition = definition,
        category = ToolCategory.DEVICE_CONTROL,
        security = ToolSecurityPolicy(
            risk = ToolRiskClass.REVERSIBLE,
            confirmation = ConfirmationPolicy.NONE,
            replaySafety = ReplaySafety.SAFE,
        ),
        resources = setOf(
            ResourceRequirement(ResourceId("device.mic"), ResourceAccess.EXCLUSIVE, CapabilityAffinity.HOST),
        ),
    )

    private class Harness(
        val executor: DefaultToolTurnExecutor,
        val coordinator: AgentRunCoordinator,
        val store: LoopTestRunStore,
    )

    private fun harness(
        onExecute: () -> Unit = {},
        limits: ToolLoopLimits = ToolLoopLimits(),
        lease: RecordingLease? = null,
        throwing: Boolean = false,
        security: ToolSecurityPolicy = descriptor().security,
        onContext: (com.halo.runtime.cognition.tools.ToolExecutionContext) -> Unit = {},
        approvalPolicy: ToolApprovalPolicy = ToolApprovalPolicy.ALWAYS_ALLOW,
    ): Harness {
        val toolExecutor = object : ToolExecutor {
            override fun definitions(): List<ToolDefinition> = listOf(definition)
            override suspend fun execute(toolCall: ToolCall, context: com.halo.runtime.cognition.tools.ToolExecutionContext): ToolExecutionResult {
                onContext(context)
                return execute(toolCall)
            }
            override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
                if (throwing) throw IllegalStateException("native failure")
                onExecute()
                return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "DISPATCHED")
            }
        }
        val registry = ToolRegistry(listOf(toolExecutor), descriptors = listOf(descriptor().copy(security = security)))
        val store = LoopTestRunStore()
        val coordinator = DefaultAgentRunCoordinator(store, CoroutineScope(Dispatchers.Unconfined))
        // Explicit always-allow policy: these tests are about the loop guards, not approval.
        val resourceCoordinator = lease?.let {
            ToolResourceCoordinator(
                capabilityResolver = DefaultDeviceCapabilityResolver(),
                resourcePlanner = DefaultResourcePlanner(),
                resourceLeaseManager = GrantingLeaseManager(it),
            )
        }
        return Harness(
            executor = DefaultToolTurnExecutor(
                toolRegistry = registry,
                toolAuthorizer = PolicyToolAuthorizer(
                    defaultPolicyProvider = { approvalPolicy },
                    agentRunStore = store,
                    descriptorProvider = { name -> registry.descriptor(name) },
                ),
                toolResourceCoordinator = resourceCoordinator,
                agentRunCoordinator = coordinator,
                agentRunStore = store,
                eventBus = LoopTestEventBus(),
                limits = limits,
            ),
            coordinator = coordinator,
            store = store,
        )
    }

    private suspend fun Harness.startRun(): AgentRunId {
        val runId = coordinator.start(AgentRequest(InteractionId("interaction"), "turn the flashlight on"))
        coordinator.transition(runId, AgentRunState.PLANNING)
        coordinator.transition(runId, AgentRunState.EXECUTING)
        return runId
    }

    private fun call(id: String) = ToolCall(id, "halo_flashlight", mapOf("state" to "on"))

    @Test
    fun `a tool the model was never shown cannot execute`() = runTest {
        var executions = 0
        val harness = harness(onExecute = { executions++ })
        val runId = harness.startRun()

        val result = harness.executor.execute(
            runId = runId,
            interactionId = InteractionId("interaction"),
            requestId = "req-1",
            toolCalls = listOf(call("call-1")),
            workspaceReadAllowed = true,
            exposedToolIds = setOf("halo_current_time"),
        )

        assertTrue(result is ToolTurnResult.Completed)
        assertEquals(0, executions)
        val rejected = harness.store.get(runId)!!.steps.filterIsInstance<AgentStep.ToolRejected>().single()
        assertEquals("TOOL_NOT_EXPOSED", rejected.reasonCode.name)
    }

    @Test
    fun `an identical action cannot repeat past the limit`() = runTest {
        var executions = 0
        val harness = harness(
            onExecute = { executions++ },
            limits = ToolLoopLimits(maxRepeatedIdenticalCalls = 1),
        )
        val runId = harness.startRun()

        repeat(3) { index ->
            harness.executor.execute(
                runId = runId,
                interactionId = InteractionId("interaction"),
                requestId = "req-$index",
                toolCalls = listOf(call("call-$index")),
                workspaceReadAllowed = true,
            )
        }

        assertEquals("only the first call may run", 1, executions)
        val codes = harness.store.get(runId)!!.steps.filterIsInstance<AgentStep.ToolRejected>().map { it.reasonCode.name }
        assertTrue(codes.contains("REPEATED_TOOL_CALL"))
    }

    @Test
    fun `a permission rejection keeps its own category`() = runTest {
        val blocked = object : ToolExecutor {
            override fun definitions(): List<ToolDefinition> = listOf(definition)
            override suspend fun execute(toolCall: ToolCall): ToolExecutionResult =
                ToolExecutionResult.Rejected(
                    toolCallId = toolCall.id,
                    toolId = toolCall.functionName,
                    reason = "Halo needs contacts permission",
                    kind = ToolRejectionKind.PERMISSION_REQUIRED,
                )
        }
        val registry = ToolRegistry(listOf(blocked), descriptors = listOf(descriptor()))
        val store = LoopTestRunStore()
        val coordinator = DefaultAgentRunCoordinator(store, CoroutineScope(Dispatchers.Unconfined))
        val executor = DefaultToolTurnExecutor(
            toolRegistry = registry,
            toolAuthorizer = PolicyToolAuthorizer(
                defaultPolicyProvider = { ToolApprovalPolicy.ALWAYS_ALLOW },
                descriptorProvider = { name -> registry.descriptor(name) },
            ),
            toolResourceCoordinator = null,
            agentRunCoordinator = coordinator,
            agentRunStore = store,
            eventBus = LoopTestEventBus(),
        )
        val runId = coordinator.start(AgentRequest(InteractionId("interaction"), "call Alice"))
        coordinator.transition(runId, AgentRunState.PLANNING)
        coordinator.transition(runId, AgentRunState.EXECUTING)

        executor.execute(
            runId = runId,
            interactionId = InteractionId("interaction"),
            requestId = "req-1",
            toolCalls = listOf(call("call-1")),
            workspaceReadAllowed = true,
        )

        val rejected = store.get(runId)!!.steps.filterIsInstance<AgentStep.ToolRejected>().single()
        assertEquals("PERMISSION_REQUIRED", rejected.reasonCode.name)
        assertTrue(rejected.requiresUserAction)
    }

    @Test
    fun `the resource lease is released after a successful execution`() = runTest {
        val lease = RecordingLease()
        val harness = harness(lease = lease)
        val runId = harness.startRun()

        harness.executor.execute(
            runId = runId,
            interactionId = InteractionId("interaction"),
            requestId = "req-1",
            toolCalls = listOf(call("call-1")),
            workspaceReadAllowed = true,
        )

        assertTrue("a lease held after the tool finished blocks every later tool", lease.released)
    }

    @Test
    fun `the resource lease is released when the executor throws`() = runTest {
        val lease = RecordingLease()
        val harness = harness(lease = lease, throwing = true)
        val runId = harness.startRun()

        val result = harness.executor.execute(
            runId = runId,
            interactionId = InteractionId("interaction"),
            requestId = "req-1",
            toolCalls = listOf(call("call-1")),
            workspaceReadAllowed = true,
        )

        assertTrue(result is ToolTurnResult.Completed)
        assertTrue("a throwing executor must not leak its lease", lease.released)
    }

    @Test fun durableAuthorityPropagatesOnlyThroughAllowedSecurity() = runTest {
        suspend fun exercise(authority: Boolean, security: ToolSecurityPolicy, policy: ToolApprovalPolicy): Pair<ToolTurnResult, List<Boolean>> {
            val contexts = mutableListOf<Boolean>()
            val harness = harness(security = security, approvalPolicy = policy, onContext = { contexts += it.directUserAuthority })
            val runId = harness.startRun()
            val call = call("authority-call")
            if (authority) {
                val now = java.time.Instant.now()
                val proof = com.halo.runtime.cognition.agent.DirectUserCommandAuthority(InteractionId("interaction"), call.functionName,
                    com.halo.runtime.cognition.agent.ToolArgumentCanonicalizer.digest(com.halo.runtime.cognition.agent.ToolArgumentCanonicalizer.canonicalize(call.arguments)),
                    com.halo.runtime.cognition.blackboard.InteractionOrigin(com.halo.sdk.runtime.Source.USER), now)
                harness.coordinator.recordStep(AgentStep.DirectCommandAuthorityRecorded(com.halo.runtime.cognition.agent.AgentStepId("authority"), runId, now, proof))
            }
            return harness.executor.execute(runId, InteractionId("interaction"), "request", listOf(call), true) to contexts
        }
        val reversible = descriptor().security
        assertEquals(listOf(true), exercise(true, reversible, ToolApprovalPolicy.ASK_EVERY_TIME).second)
        assertEquals(listOf(false), exercise(false, reversible, ToolApprovalPolicy.ALWAYS_ALLOW).second)
        for (security in listOf(reversible.copy(confirmation = ConfirmationPolicy.ALWAYS),
            reversible.copy(risk = ToolRiskClass.SENSITIVE), reversible.copy(risk = ToolRiskClass.DESTRUCTIVE))) {
            val (result, contexts) = exercise(true, security, ToolApprovalPolicy.ASK_EVERY_TIME)
            assertTrue(result is ToolTurnResult.WaitingForInput)
            assertTrue(contexts.isEmpty())
        }
    }
}
