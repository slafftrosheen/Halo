package com.halo.app.di.tools

import com.halo.app.di.tools.host.ExternalDisplayTools
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.display.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Source/desktop contracts only; neither physical output nor spoken acceptance. */
@RunWith(RobolectricTestRunner::class)
class ExternalDisplayToolsTest {
    private class Output : ExternalDisplayOutput {
        val session = ExternalDisplaySession { "live-token" }.apply {
            refresh(true, listOf(ExternalDisplayConnection(7, "Display", 1920, 1080)))
        }
        override val state = MutableStateFlow(session.state)
        var dispatched = 0
        override suspend fun snapshot() = state.value
        override suspend fun show(endpointToken: String, text: String, expectedRevision: Long?): ExternalDisplayReceipt {
            if (session.resolve(endpointToken, expectedRevision) == null) return session.rejected("Stale display")
            dispatched++
            return session.presented(endpointToken).also { state.value = session.state }
        }
        override suspend fun clear(expectedRevision: Long?, endpointToken: String?): ExternalDisplayReceipt {
            if (expectedRevision != session.state.revision || endpointToken != session.state.activeToken)
                return session.rejected("Stale output")
            return session.cleared().also { state.value = session.state }
        }
    }
    private val output = Output()
    private var access = true
    private var remote = false
    private val tools = ExternalDisplayTools(output, { access }, { remote })
    private fun call(name: String, args: Map<String, Any> = emptyMap()) = ToolCall("display-test", name, args)
    private fun show(version: Long = output.state.value.revision, text: String = "Test card") = call(ExternalDisplayTools.SHOW,
        mapOf("endpointToken" to "live-token", "sourceVersion" to version, "text" to text))

    @Test fun registryHasClosedContractsAndNeverReplaysOutput() {
        val descriptors = DescriptorCatalog.buildDescriptors(HaloToolDescriptors.ExecutorKind.DEVICE to tools)
        val registry = ToolRegistry(listOf(tools), descriptors)
        assertEquals(emptyList<ToolOperabilityProblem>(), registry.operabilityReport(listOf(
            ToolFeatureOperability("external-display", ExternalDisplayTools.NAMES))))
        assertEquals(ExternalDisplayTools.NAMES, descriptors.map { it.name }.toSet())
        descriptors.filter { it.name != ExternalDisplayTools.LIST }.forEach {
            assertEquals(ReplaySafety.NEVER_REPLAY, it.security.replaySafety)
            assertEquals(ConfirmationPolicy.USER_POLICY, it.security.confirmation)
        }
    }
    @Test fun missingRunScopeAndDeniedAccessNeverDispatch() = runBlocking {
        assertTrue(tools.execute(show()) is ToolExecutionResult.Rejected)
        withContext(RunToolScope(AgentRunId("test"))) {
            access = false
            assertTrue(tools.execute(show()) is ToolExecutionResult.Rejected)
        }
        assertEquals(0, output.dispatched)
    }
    @Test fun remoteListingRequiresExistingDisclosurePolicy() = runBlocking {
        withContext(RunToolScope(AgentRunId("test"), targetIsLocal = false)) {
            assertTrue(tools.execute(call(ExternalDisplayTools.LIST)) is ToolExecutionResult.Rejected)
            remote = true
            val result = tools.execute(call(ExternalDisplayTools.LIST)) as ToolExecutionResult.Success
            assertEquals("live-token", JSONObject(result.output).getJSONArray("endpoints").getJSONObject(0).getString("endpointToken"))
        }
        assertEquals(0, output.dispatched)
    }
    @Test fun staleOversizeAndExtraArgumentsNeverDispatch() = runBlocking {
        withContext(RunToolScope(AgentRunId("test"))) {
            assertTrue(tools.execute(show(version = 0)) is ToolExecutionResult.Rejected)
            assertTrue(tools.execute(show(text = "x".repeat(401))) is ToolExecutionResult.Rejected)
            val extra = show().let { it.copy(arguments = it.arguments + ("autoReconnect" to true)) }
            assertTrue(tools.execute(extra) is ToolExecutionResult.Rejected)
        }
        assertEquals(0, output.dispatched)
    }
    @Test fun submissionTruthIsDispatchedAndOldStopCannotClearNewCard() = runBlocking {
        withContext(RunToolScope(AgentRunId("test"))) {
            val result = tools.execute(show()) as ToolExecutionResult.Success
            assertEquals(ActionTruth.DISPATCHED, result.actionTruth)
            val oldRevision = output.state.value.revision
            tools.execute(show())
            assertTrue(tools.execute(call(ExternalDisplayTools.CLEAR, mapOf("endpointToken" to "live-token", "sourceVersion" to oldRevision))) is ToolExecutionResult.Rejected)
            assertEquals("live-token", output.state.value.activeToken)
            val stopped = tools.execute(call(ExternalDisplayTools.CLEAR,
                mapOf("endpointToken" to "live-token", "sourceVersion" to output.state.value.revision))) as ToolExecutionResult.Success
            assertEquals(ActionTruth.CONFIRMED, stopped.actionTruth)
            assertNull(output.state.value.activeToken)
        }
    }
}
