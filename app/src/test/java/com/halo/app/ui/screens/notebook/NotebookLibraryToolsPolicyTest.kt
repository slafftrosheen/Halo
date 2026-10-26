package com.halo.app.ui.screens.notebook

import com.halo.app.di.tools.host.NotebookLibraryTools
import com.halo.app.di.tools.host.NotebookOrganizationTools
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class NotebookLibraryToolsPolicyTest {
    @Test fun registryContractsRequireApprovalAndForbidReplayingMutations() {
        val session = NotebookLibrarySession { true }
        val view = NotebookLibraryTools(session)
        val organization = NotebookOrganizationTools(session)
        val descriptors = view.definitions().map { requireNotNull(NotebookLibraryTools.descriptor(it)) } +
            organization.definitions().map { requireNotNull(NotebookOrganizationTools.descriptor(it)) }
        val registry = ToolRegistry(listOf(view, organization), descriptors)
        assertTrue(registry.operabilityReport(listOf(ToolFeatureOperability("library-view-and-organization",
            NotebookLibraryTools.NAMES + NotebookOrganizationTools.NAMES))).isEmpty())
        val write = descriptors.single { it.name == NotebookOrganizationTools.WRITE }
        assertEquals(ConfirmationPolicy.ALWAYS, write.security.confirmation)
        assertEquals(ReplaySafety.NEVER_REPLAY, write.security.replaySafety)
        assertEquals(ReplaySafety.NEVER_REPLAY, descriptors.single { it.name == NotebookLibraryTools.SET }.security.replaySafety)
        descriptors.forEach {
            assertEquals(false, it.definition.parameters["additionalProperties"])
            assertTrue(ToolRequirement.Capability(requireNotNull(it.operability).capabilityId) in it.requirements)
        }
    }

    @Test fun persistentWriteWithoutRuntimeInteractionIsDeniedBeforeOwnerAccess() = runBlocking {
        val executor = NotebookOrganizationTools(NotebookLibrarySession { error("Must not inspect owner") })
        withContext(RunToolScope(AgentRunId("local-no-authority"), targetIsLocal = true)) {
            val call = ToolCall("write", NotebookOrganizationTools.WRITE, emptyMap())
            assertTrue(executor.execute(call) is ToolExecutionResult.Rejected)
            assertTrue(executor.execute(call, ToolExecutionContext()) is ToolExecutionResult.Rejected)
        }
    }

    @Test fun unscopedAndRemoteCallsCannotReachTheLibraryOwner() = runBlocking {
        val session = NotebookLibrarySession { error("Denied calls must not inspect the owner") }
        val executors = listOf(NotebookLibraryTools(session), NotebookOrganizationTools(session))
        for (executor in executors) {
            val call = ToolCall("read", executor.definitions().first().name, mapOf("expectedSnapshot" to "stale"))
            assertTrue(executor.execute(call) is ToolExecutionResult.Rejected)
            withContext(RunToolScope(AgentRunId("remote-library"), targetIsLocal = false)) {
                assertTrue(executor.execute(call) is ToolExecutionResult.Rejected)
            }
        }
    }
}
