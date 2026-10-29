package com.halo.app.data.workspace

import com.halo.app.di.tools.HaloToolDescriptors
import com.halo.app.di.tools.WorkspaceCanvasActions
import com.halo.app.di.tools.WorkspaceContinuityActions
import com.halo.app.di.tools.WorkspaceFileEditingActions
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real competing Canvas/file/handoff definitions and host descriptors, not a hand-ranked fake catalogue. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CanvasReferenceDiscoveryTest {
    private val attach = "halo_file_attach_canvas"

    private class DefinitionOnly(private val owner: ToolExecutor) : ToolExecutor {
        var executions = 0
        override fun definitions() = owner.definitions()
        override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
            executions++
            error("Discovery must not execute a workspace action")
        }
    }

    private class Catalogue(f: WorkspaceFileToolsFixture) {
        val executors = listOf(
            DefinitionOnly(WorkspaceCanvasActions(f.database, f.canvases)),
            DefinitionOnly(WorkspaceFileEditingActions(f.tools)),
            DefinitionOnly(WorkspaceContinuityActions(f.continuity())),
        )
        val descriptors = executors.flatMap { it.definitions() }.map {
            requireNotNull(HaloToolDescriptors.workspace(it) ?: HaloToolDescriptors.file(it))
        }
        val registry = ToolRegistry(executors, descriptors)
        val capabilities = MutableStateFlow(CapabilityCatalogueSnapshot(
            descriptors.flatMap { it.requirements.filterIsInstance<ToolRequirement.Capability>() }
                .map { it.id }.distinct().map { CapabilityInstance(it, it.value, "Registered fixture capability", ToolCategory.WORKSPACE) }))
        val availability = ToolAvailabilityResolver(registry, catalogueSource = object : CapabilityCatalogueSource {
            override val snapshot = capabilities
        })
        val discovery = ToolDiscoveryService(registry, availability)
    }

    @Test fun canvasAndReferenceQueriesFindTheRealAttachOperationWithinTheBoundedResults() {
        WorkspaceFileToolsFixture().use { f ->
            val catalogue = Catalogue(f)
            assertTrue("Use enough actual competitors to exercise the cutoff", catalogue.descriptors.size > ToolDiscoveryService.MAX_SEARCH_MATCHES)
            listOf("canvas create board", "linked note card", "attach existing note to canvas").forEach { query ->
                val matches = catalogue.discovery.search(query)
                assertTrue("$query: ${matches.map { it.name }}", matches.any { it.name == attach })
                assertTrue(matches.size <= ToolDiscoveryService.MAX_SEARCH_MATCHES)
            }
            assertEquals("halo_canvas_create", catalogue.discovery.search("canvas create board").first().name)
            assertTrue(catalogue.executors.all { it.executions == 0 })
        }
    }

    @Test fun unavailableReferenceOperationIsNotRecoveredByKeywordsOrExactIdExpansion() {
        WorkspaceFileToolsFixture().use { f ->
            val catalogue = Catalogue(f)
            catalogue.capabilities.value = catalogue.capabilities.value.copy(revision = 1,
                capabilities = catalogue.capabilities.value.capabilities.map {
                    if (it.id == CapabilityId("workspace.write")) it.copy(availability = CapabilityAvailability.UNAVAILABLE) else it
                })
            assertFalse(catalogue.discovery.search("linked note card").any { it.name == attach })
            assertNull(catalogue.discovery.registryDescriptorOrNull(attach))
            assertFalse(catalogue.discovery.expand(listOf(attach)).any { it.name == attach })
            assertTrue(catalogue.executors.all { it.executions == 0 })
        }
    }

    @Test fun referenceDiscoveryPreservesBothVersionGuardsAndNeverReplayPolicy() {
        WorkspaceFileToolsFixture().use { f ->
            val catalogue = Catalogue(f)
            val descriptor = catalogue.descriptors.single { it.name == attach }
            val expanded = catalogue.discovery.expand(listOf(attach)).single { it.name == attach }
            assertEquals(descriptor.definition.parameters, expanded.parameters)
            assertEquals(setOf("canvasId", "canvasVersion", "objectId", "version"),
                (expanded.parameters["required"] as List<*>).toSet())
            assertEquals(false, expanded.parameters["additionalProperties"])
            assertEquals(ToolRiskClass.REVERSIBLE, descriptor.security.risk)
            assertEquals(ConfirmationPolicy.USER_POLICY, descriptor.security.confirmation)
            assertEquals(ReplaySafety.NEVER_REPLAY, descriptor.security.replaySafety)
            assertEquals(setOf("version", "canvasVersion"), requireNotNull(descriptor.operability).sourceVersionArguments)
            val summary = catalogue.discovery.describe(listOf(descriptor))
            assertTrue(summary.contains("linked reference card"))
            val copy = catalogue.descriptors.single { it.name == "halo_canvas_add_text" }.definition.description
            assertTrue(copy.contains("independent text"))
            assertTrue(copy.contains("Does not create a live source link"))
        }
    }

    @Test fun discoveryKeepsStrictToolIdArrayAndAllowsRefiningIncompleteMatches() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val catalogue = Catalogue(f)
            val executor = ToolDiscoveryExecutor(catalogue.discovery)
            val registry = ToolRegistry(listOf(executor))
            assertNotNull(registry.validate(ToolCall("invalid", ToolDiscoveryService.DISCOVER_TOOL, mapOf("toolIds" to attach))))
            assertNull(registry.validate(ToolCall("valid", ToolDiscoveryService.DISCOVER_TOOL, mapOf("toolIds" to listOf(attach)))))
            val result = executor.execute(ToolCall("discover", ToolDiscoveryService.DISCOVER_TOOL, mapOf("query" to "canvas create board")))
                as ToolExecutionResult.Success
            assertTrue(result.output.contains(attach))
            assertTrue(result.output.contains("USE_MATCHING_ACTION_OR_REFINE_SEARCH"))
            assertFalse(result.output.contains("CALL_THE_ACTION_NOW"))
            assertTrue(catalogue.executors.all { it.executions == 0 })
        }
    }
}
