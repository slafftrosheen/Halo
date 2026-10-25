package com.halo.runtime.cognition.tools

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.ToolInvocationId
import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.context.DefaultContextAssembler
import com.halo.runtime.cognition.conversation.InMemoryConversationStore
import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.halo.runtime.cognition.skills.SkillAvailabilityResolver
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.skills.HostPermission
import com.halo.sdk.runtime.skills.SkillCategory
import com.halo.sdk.runtime.skills.SkillAvailability
import com.halo.sdk.runtime.skills.SkillDescriptor
import com.halo.sdk.runtime.skills.SkillId
import com.halo.sdk.runtime.skills.SkillRisk
import com.halo.sdk.runtime.skills.SkillRequirement
import com.halo.sdk.runtime.skills.SkillSource
import com.halo.sdk.runtime.skills.SkillToolDefinition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Operation counts expose nested schema rebuilds without timing or device-speed assumptions. */
class ToolDiscoverySnapshotRegressionTest {
    private class MemoryStorage : Storage {
        private val data = mutableMapOf<String, Any>()
        override fun getString(key: String, defaultValue: String?) = data[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) data.remove(key) else data[key] = value }
        override fun getInt(key: String, defaultValue: Int) = data[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = data[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }

    private class CountingExecutor(private val tools: List<ToolDefinition>) : ToolExecutor {
        var enumerations = 0
        var executions = 0
        override fun definitions(): List<ToolDefinition> { enumerations++; return tools }
        override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
            executions++
            error("Catalogue inspection must never execute an action")
        }
    }

    private class Fixture {
        val names = (0 until 8).map { "fixture_tool_$it" }
        val executor = CountingExecutor(names.map { ToolDefinition(it, it, "Fixture action") })
        val capId = CapabilityId("fixture.capability")
        val descriptors = names.map { name ->
            ToolDescriptor(ToolId(name), ToolDefinition(name, name, "Fixture action"), ToolCategory.WORKSPACE,
                requirements = setOf(ToolRequirement.Capability(capId)),
                security = if (name == names.first())
                    ToolSecurityPolicy(ToolRiskClass.SENSITIVE, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY)
                else ToolSecurityPolicy.READ_ONLY)
        }
        val registry = ToolRegistry(listOf(executor), descriptors)
        var microphoneGranted = true
        var permissionReads = 0
        var afterPermissionRead: (() -> Unit)? = null
        val skills = DefaultSkillRegistry(MemoryStorage(), initialBuiltIns = (0 until 3).map { group ->
            val owned = names.filterIndexed { index, _ -> index % 3 == group }.toSet()
            SkillDescriptor(SkillId("fixture.group$group"), "Fixture group $group", "Local fixture actions",
                SkillCategory.WORKSPACE, tools = owned, toolDefinitions = owned.map {
                    SkillToolDefinition(it, SkillRisk.READ_ONLY, setOf(HostPermission.MICROPHONE))
                })
        }, availabilityResolver = SkillAvailabilityResolver(
            permissionGranted = {
                permissionReads++
                val result = microphoneGranted
                afterPermissionRead?.invoke()
                result
            },
            // Exactly the current host callback's registered-name semantics (both id and name).
            registeredToolNames = { registry.definitions().flatMap { listOf(it.id, it.name) }.toSet() },
        ))
        val capabilities = MutableStateFlow(CapabilityCatalogueSnapshot(listOf(
            CapabilityInstance(capId, "Fixture capability", "Local fixture", ToolCategory.WORKSPACE)
        )))
        val availability = ToolAvailabilityResolver(registry, skills, object : CapabilityCatalogueSource {
            override val snapshot = capabilities
        })
        val discovery = ToolDiscoveryService(registry, availability, maxExposedSchemasUpperBound = 2)
        init { resetCounters() }
        fun resetCounters() { executor.enumerations = 0; permissionReads = 0 }
        fun search() = discovery.search("fixture").map { it.name }
    }

    @Test fun eightCandidatesAcrossThreeSkillsEnumerateTheRegistryOncePerSearch() {
        val f = Fixture()
        assertEquals(f.names, f.search())
        // RED baseline: 1 catalogue enumeration + 8 candidates * 3 skill refresh enumerations = 25.
        assertEquals("A search must reuse one operation-local registry snapshot", 1, f.executor.enumerations)
        assertEquals("One coherent permission answer per operation", 1, f.permissionReads)
        assertEquals(0, f.executor.executions)
    }

    @Test fun summaryAndRecommendationEachUseOneFreshAvailabilityScan() {
        val f = Fixture()
        val summary = f.discovery.catalogueSummary()
        f.names.forEach { assertTrue(summary.contains(it)) }
        assertEquals("Summary must not refresh every skill for every candidate", 1, f.executor.enumerations)
        assertEquals(1, f.permissionReads)
        f.resetCounters()
        assertTrue(f.discovery.discoveryRecommended())
        assertEquals(1, f.executor.enumerations)
        assertEquals(1, f.permissionReads)
    }

    @Test fun initialDiscoverFirstContextDoesNotScanAvailabilityTwice() = runTest {
        val f = Fixture()
        val assembler = DefaultContextAssembler(InMemoryConversationStore(), DefaultBlackboard(),
            f.registry, skillRegistry = f.skills, discoveryProvider = { f.discovery })
        val result = assembler.assemble(AiContextPolicy(includeAvailableTools = true), setOf(AiCapability.TEXT, AiCapability.TOOLS))
        assertEquals(listOf(ToolDiscoveryService.DISCOVER_TOOL), result.context.availableTools.map { it.name })
        f.names.forEach { assertTrue(result.context.availableTools.single().description.contains(it)) }
        // One ordinary exposure projection plus one discovery snapshot. Current baseline is 51.
        assertEquals("Bootstrap decision and summary must share their discovery scan", 2, f.executor.enumerations)
        assertEquals(1, f.permissionReads)
        assertEquals(0, f.executor.executions)
    }

    @Test fun oneOperationUsesACoherentPermissionAnswerAndTheNextSeesRevocation() {
        val f = Fixture()
        f.afterPermissionRead = { f.microphoneGranted = false }
        assertEquals("A permission transition must not mix candidates from different scans", f.names, f.search())
        assertEquals(1, f.permissionReads)
        f.afterPermissionRead = null
        assertTrue("Revocation must apply to the very next operation", f.search().isEmpty())
        f.microphoneGranted = true
        assertEquals(f.names, f.search())
    }

    @Test fun skillApprovalPermissionAndCapabilityChangesAreFreshBetweenOperations() = runTest {
        val f = Fixture()
        assertEquals(f.names, f.search())
        f.skills.setEnabled(SkillId("fixture.group0"), false)
        assertEquals(f.names.filterIndexed { index, _ -> index % 3 != 0 }, f.search())
        f.skills.setEnabled(SkillId("fixture.group0"), true)
        f.skills.setApprovalPolicy(SkillId("fixture.group1"), ToolApprovalPolicy.DENY)
        assertEquals(f.names.filterIndexed { index, _ -> index % 3 != 1 }, f.search())
        f.skills.setApprovalPolicy(SkillId("fixture.group1"), ToolApprovalPolicy.ALWAYS_ALLOW)
        assertEquals(f.names, f.search())
        f.microphoneGranted = false
        assertTrue(f.search().isEmpty())
        f.microphoneGranted = true
        val available = f.capabilities.value
        f.capabilities.value = available.copy(revision = 1, capabilities = available.capabilities.map {
            it.copy(availability = CapabilityAvailability.BLOCKED)
        })
        assertTrue(f.search().isEmpty())
        f.capabilities.value = available.copy(revision = 2)
        assertEquals(f.names, f.search())
        assertEquals(0, f.executor.executions)
    }

    @Test fun dynamicRegistrationRemovalAndReadditionAreVisibleOnTheNextOperation() {
        val f = Fixture()
        val dynamicName = "fixture_dynamic"
        f.skills.replaceCustomSkills(listOf(SkillDescriptor(SkillId("fixture.dynamic"), "Dynamic fixture", "Dynamic fixture actions",
            SkillCategory.WORKSPACE, tools = setOf(dynamicName), source = SkillSource.CUSTOM)))
        assertTrue(f.discovery.search("dynamic").isEmpty())
        val dynamic = CountingExecutor(listOf(ToolDefinition(dynamicName, dynamicName, "Dynamic fixture action")))
        f.registry.registerDynamic(dynamic)
        assertEquals(listOf(dynamicName), f.discovery.search("dynamic").map { it.name })
        f.registry.unregisterDynamic(dynamic)
        assertTrue(f.discovery.search("dynamic").isEmpty())
        f.registry.registerDynamic(dynamic)
        assertEquals(listOf(dynamicName), f.discovery.search("dynamic").map { it.name })
        assertEquals(0, dynamic.executions)
        assertEquals(0, f.executor.executions)
    }

    @Test fun registeredNamesKeepDefinitionIdAndNameButExcludeDescriptorMetadataId() {
        val definition = ToolDefinition("fixture_internal_id", "fixture_public_name", "Fixture alias action")
        val metadataId = "fixture_metadata_id"
        val executor = CountingExecutor(listOf(definition))
        val registry = ToolRegistry(listOf(executor), listOf(
            ToolDescriptor(ToolId(metadataId), definition, ToolCategory.WORKSPACE)
        ))
        val owner = SkillId("fixture.alias")
        val skills = DefaultSkillRegistry(MemoryStorage(), initialBuiltIns = listOf(
            SkillDescriptor(owner, "Alias fixture", "Definition aliases stay registered", SkillCategory.WORKSPACE,
                tools = setOf(definition.name, definition.id, metadataId),
                requirements = listOf(SkillRequirement.ToolRegistered(definition.id)))
        ), availabilityResolver = SkillAvailabilityResolver(
            registeredToolNames = { registry.definitions().flatMap { listOf(it.id, it.name) }.toSet() }
        ))
        val discovery = ToolDiscoveryService(registry, ToolAvailabilityResolver(registry, skills))
        assertEquals(listOf(definition.name), discovery.search("fixture").map { it.name })
        val availability = skills.get(owner)!!.availability as SkillAvailability.PartiallyAvailable
        assertEquals("Host registration includes definition id/name, not descriptor metadata IDs",
            setOf(metadataId), availability.unavailableTools)
        assertEquals(0, executor.executions)
    }

    @Test fun discoveredMetadataDoesNotCacheAuthorityOrOverrideAlwaysConfirmation() = runTest {
        val f = Fixture()
        val name = f.names.first()
        assertTrue(name in f.search())
        f.skills.setApprovalPolicy(SkillId("fixture.group0"), ToolApprovalPolicy.ALWAYS_ALLOW)
        val authorizer = PolicyToolAuthorizer(defaultPolicyProvider = f.skills::approvalPolicyForTool,
            descriptorProvider = f.registry::descriptor)
        val call = ToolCall("fixture-call", name, emptyMap())
        val approval = authorizer.authorizeDurable(call, AgentRunId("fixture-run"), InteractionId("fixture-interaction"),
            ToolInvocationId("fixture-invocation"), "fixture-digest", newRequestId = { "fixture-approval" })
        assertTrue("Discovery and ALWAYS_ALLOW must not bypass ALWAYS confirmation", approval is AuthorizationDecision.NeedsUserInput)
        f.microphoneGranted = false
        assertTrue("Execution authorization must recheck current permissions", authorizer.authorize(call) is ToolAuthorization.Rejected)
        f.microphoneGranted = true
        f.skills.setEnabled(SkillId("fixture.group0"), false)
        assertTrue(authorizer.authorize(call) is ToolAuthorization.Rejected)
        assertEquals(0, f.executor.executions)
    }

    @Test fun discoveredWorkspaceToolsStillPassTheFreshRemoteDisclosureGate() {
        val f = Fixture()
        val name = f.names.first()
        assertTrue(name in f.search())
        val planner = ToolExposurePlanner(f.registry, f.availability, f.skills)
        val exposure = ToolExposureSet(discovered = setOf(ToolId(name)))
        val gate = DefaultToolExposureGate(f.skills)
        fun exposed(sharing: Boolean) = planner.effectiveDefinitions(exposure, gate = gate,
            exposureContext = ToolExposureContext(enabledSkillIds = f.skills.skills.value.map { it.id }.toSet(),
                policy = AiContextPolicy(includeAvailableTools = true, allowCloudWorkspace = sharing), isLocalTarget = false)).map { it.name }
        assertTrue(name in exposed(true))
        assertFalse("Previous discovery cannot grant cloud Workspace disclosure", name in exposed(false))
        assertTrue(name in exposed(true))
        assertEquals(0, f.executor.executions)
    }
}
