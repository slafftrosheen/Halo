package com.halo.app.data.workspace

import com.halo.app.context.CurrentCanvasContextOwner
import com.halo.app.di.tools.HaloToolDescriptors
import com.halo.app.di.tools.WorkspaceCanvasActions
import com.halo.runtime.cognition.context.CognitiveOperatingMode
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolCallProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Uses production descriptors and the actual dependency planner; never substitutes a dependency graph. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CanvasReadPrerequisiteExposureTest {
    private val edit = ToolId("halo_canvas_edit_text")
    private val current = ToolId("halo_canvas_current")
    private val get = ToolId("halo_canvas_get")
    private val create = ToolId("halo_canvas_create")

    private class CountingExecutor(private val delegate: ToolExecutor) : ToolExecutor {
        var executions = 0
        override fun definitions() = delegate.definitions()
        override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
            executions++
            return delegate.execute(toolCall)
        }
    }

    private class Catalogue(f: WorkspaceFileToolsFixture) {
        // A registered current-view query does not imply that a native view is attached.
        val actions = WorkspaceCanvasActions(f.database, f.canvases, CurrentCanvasContextOwner(f.database))
        val executor = CountingExecutor(actions)
        val descriptors = actions.definitions().map { requireNotNull(HaloToolDescriptors.workspace(it)) }
        val registry = ToolRegistry(listOf(executor), descriptors)
        val capabilities = MutableStateFlow(CapabilityCatalogueSnapshot(
            descriptors.flatMap { it.requirements.filterIsInstance<ToolRequirement.Capability>().map { requirement -> requirement.id } }
                .distinct().map { CapabilityInstance(it, it.value, "Fixture registered capability", ToolCategory.WORKSPACE) }))
        val availability = ToolAvailabilityResolver(registry, catalogueSource = object : CapabilityCatalogueSource {
            override val snapshot = capabilities
        })
        val planner = ToolExposurePlanner(registry, availability)
        fun descriptor(id: ToolId) = descriptors.single { it.id == id }
    }

    @Test fun discoveringEditExposesExactlyItsAvailableReadPrerequisitesWithoutExecutingAnyAction() {
        WorkspaceFileToolsFixture().use { f ->
            val c = Catalogue(f)
            val expanded = c.planner.expand(c.planner.initialExposure(), listOf(edit.value),
                DefaultToolExposureGate(), ToolExposureContext(policy = AiContextPolicy.CONSERVATIVE))
            assertEquals(setOf(current, get), c.descriptor(edit).dependencies)
            assertEquals(setOf(edit, current, get), expanded.effective)
            assertFalse(create in expanded.effective)
            assertEquals(0, c.executor.executions)
            assertEquals(ToolRiskClass.REVERSIBLE, c.descriptor(edit).security.risk)
            assertEquals(ConfirmationPolicy.USER_POLICY, c.descriptor(edit).security.confirmation)
            assertEquals(ReplaySafety.NEVER_REPLAY, c.descriptor(edit).security.replaySafety)
            listOf(current, get).forEach { id ->
                assertEquals(ToolRiskClass.READ_ONLY, c.descriptor(id).security.risk)
                assertEquals(ConfirmationPolicy.NONE, c.descriptor(id).security.confirmation)
                assertTrue(c.descriptor(id).requirements.contains(ToolRequirement.Capability(CapabilityId("workspace.read"))))
            }
        }
    }

    @Test fun readAvailabilityAndPerToolPolicyAreRecheckedWithoutMakingEditRequireAnAttachedView() {
        WorkspaceFileToolsFixture().use { f ->
            val c = Catalogue(f)
            c.capabilities.value = c.capabilities.value.copy(revision = 1,
                capabilities = c.capabilities.value.capabilities.map {
                    if (it.id == CapabilityId("workspace.read")) it.copy(availability = CapabilityAvailability.UNAVAILABLE) else it
                })
            assertEquals(setOf(edit), c.planner.expand(c.planner.initialExposure(), listOf(edit.value)).effective)
            c.capabilities.value = c.capabilities.value.copy(revision = 2,
                capabilities = c.capabilities.value.capabilities.map { it.copy(availability = CapabilityAvailability.AVAILABLE) })
            val gate = ToolExposureGate { descriptor, _ ->
                if (descriptor.id == current) ToolExposureDecision.Deny(ToolExposureDenyReason.POLICY_DENIED)
                else ToolExposureDecision.Allow
            }
            assertEquals(setOf(edit, get), c.planner.expand(c.planner.initialExposure(), listOf(edit.value), gate).effective)
            val localPolicy = LocalModelToolPolicy(ToolCallProtocol.NATIVE, emptySet(), setOf(edit.value, get.value))
            assertEquals(setOf(edit, get), c.planner.expand(c.planner.initialExposure(), listOf(edit.value),
                localPolicy = localPolicy).effective)
            assertEquals(0, c.executor.executions)
        }
    }

    @Test fun remoteWorkspacePrivacyModeAndToolDisableStillDenyExpansion() {
        WorkspaceFileToolsFixture().use { f ->
            val c = Catalogue(f)
            val gate = DefaultToolExposureGate()
            listOf(
                ToolExposureContext(policy = AiContextPolicy.CONSERVATIVE, isLocalTarget = false),
                ToolExposureContext(policy = AiContextPolicy(includeAvailableTools = false)),
                ToolExposureContext(policy = AiContextPolicy.CONSERVATIVE, mode = CognitiveOperatingMode.ASK),
            ).forEach { context ->
                assertTrue(c.planner.expand(c.planner.initialExposure(), listOf(edit.value), gate, context).effective.isEmpty())
            }
            assertEquals(0, c.executor.executions)
        }
    }

    @Test fun exactPinnedIdEditWorksWithoutACurrentViewAndCreateRemainsIndependent() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val c = Catalogue(f)
            assertTrue(c.descriptor(create).dependencies.isEmpty())
            assertEquals(setOf(create), c.planner.expand(c.planner.initialExposure(), listOf(create.value)).effective)
            val board = f.canvases.create("Explicit identity")
            val node = f.canvases.addTextNode(board, 0f, 0f, "Before")
            val version = "canvas:${f.database.notebookCanvasDao().getDocument(board)!!.revision}"
            val request = ToolCall("explicit", edit.value, mapOf("canvasId" to board, "nodeId" to node,
                "version" to version, "text" to "After"))
            assertNull("Registered read dependencies are not a demand to execute a view query", c.registry.validate(request))
            assertTrue(c.actions.execute(ToolCall("no-view", current.value, emptyMap())) is ToolExecutionResult.Rejected)
            assertTrue(c.actions.execute(request) is ToolExecutionResult.Success)
            assertEquals("After", f.database.notebookCanvasDao().getNode(board, node)!!.text)
            assertEquals(0, c.executor.executions)
        }
    }
}
