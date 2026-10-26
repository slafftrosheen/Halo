package com.halo.app.ui.screens.notebook

import com.halo.app.di.tools.host.NotebookLibraryTools
import com.halo.app.di.tools.host.NotebookOrganizationTools
import com.halo.app.di.tools.host.NotebookBatchTools
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
        val batch = NotebookBatchTools(session)
        val descriptors = view.definitions().map { requireNotNull(NotebookLibraryTools.descriptor(it)) } +
            organization.definitions().map { requireNotNull(NotebookOrganizationTools.descriptor(it)) } +
            batch.definitions().map { requireNotNull(NotebookBatchTools.descriptor(it)) }
        val registry = ToolRegistry(listOf(view, organization, batch), descriptors)
        assertTrue(registry.operabilityReport(listOf(ToolFeatureOperability("library-view-and-organization",
            NotebookLibraryTools.NAMES + NotebookOrganizationTools.NAMES + NotebookBatchTools.NAMES))).isEmpty())
        val write = descriptors.single { it.name == NotebookOrganizationTools.WRITE }
        assertEquals(ConfirmationPolicy.ALWAYS, write.security.confirmation)
        assertEquals(ReplaySafety.NEVER_REPLAY, write.security.replaySafety)
        assertEquals(ReplaySafety.NEVER_REPLAY, descriptors.single { it.name == NotebookLibraryTools.SET }.security.replaySafety)
        val batchWrite = descriptors.single { it.name == NotebookBatchTools.WRITE }
        assertEquals(ToolRiskClass.DESTRUCTIVE, batchWrite.security.risk)
        assertEquals(ConfirmationPolicy.ALWAYS, batchWrite.security.confirmation)
        assertEquals(ReplaySafety.NEVER_REPLAY, batchWrite.security.replaySafety)
        assertEquals(ToolRiskClass.READ_ONLY, descriptors.single { it.name == NotebookBatchTools.READ }.security.risk)
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
        val executors = listOf(NotebookLibraryTools(session), NotebookOrganizationTools(session), NotebookBatchTools(session))
        for (executor in executors) {
            val call = ToolCall("read", executor.definitions().first().name, mapOf("expectedSnapshot" to "stale"))
            assertTrue(executor.execute(call) is ToolExecutionResult.Rejected)
            withContext(RunToolScope(AgentRunId("remote-library"), targetIsLocal = false)) {
                assertTrue(executor.execute(call) is ToolExecutionResult.Rejected)
            }
        }
    }

    @Test fun batchWriteRequiresBothDirectAuthorityAndAnInteractionBeforeOwnerAccess() = runBlocking {
        val executor = NotebookBatchTools(NotebookLibrarySession { error("Must not inspect owner") })
        val call = ToolCall("batch", NotebookBatchTools.WRITE, emptyMap())
        withContext(RunToolScope(AgentRunId("batch-authority"), targetIsLocal = true)) {
            assertTrue(executor.execute(call, ToolExecutionContext()) is ToolExecutionResult.Rejected)
            assertTrue(executor.execute(call, ToolExecutionContext(interactionId = "interaction")) is ToolExecutionResult.Rejected)
            assertTrue(executor.execute(call, ToolExecutionContext(directUserAuthority = true)) is ToolExecutionResult.Rejected)
        }
    }
}
