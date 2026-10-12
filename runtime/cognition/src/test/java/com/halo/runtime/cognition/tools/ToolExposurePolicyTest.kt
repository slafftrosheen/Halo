package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolCallProtocol
import com.halo.sdk.provider.ToolDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AGENTIC-001 tool exposure.
 *
 * These tests pin the two properties that make a small local model usable and safe:
 * discovery/skill loading must actually widen the NEXT provider turn, and nothing
 * outside the local allowlist may be exposed or executed.
 */
class ToolExposurePolicyTest {

    private class FakeExecutor(private val definitions: List<ToolDefinition>) : ToolExecutor {
        override fun definitions(): List<ToolDefinition> = definitions
        override suspend fun execute(toolCall: ToolCall): ToolExecutionResult =
            ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "ran ${toolCall.functionName}")
    }

    private fun definition(name: String): ToolDefinition =
        ToolDefinition(
            id = name,
            name = name,
            description = "Test action $name",
            parameters = mapOf("type" to "object", "properties" to emptyMap<String, Any>(), "additionalProperties" to false),
        )

    private fun descriptor(
        name: String,
        visibility: ToolVisibility = ToolVisibility.DISCOVERABLE,
        keywords: Set<String> = emptySet(),
    ): ToolDescriptor = ToolDescriptor(
        id = ToolId(name),
        definition = definition(name),
        category = ToolCategory.DEVICE_CONTROL,
        keywords = keywords,
        visibility = visibility,
        security = ToolSecurityPolicy.READ_ONLY,
    )

    private val toolNames = listOf(
        "halo_discover_tools",
        "halo_use_skill",
        "halo_current_time",
        "halo_set_alarm",
        "halo_list_apps",
        "halo_read_screen",
        "halo_create_note",
    )

    private val registry: ToolRegistry = run {
        val executors = toolNames.map { FakeExecutor(listOf(definition(it))) }
        val descriptors = toolNames.map { name ->
            when (name) {
                "halo_discover_tools", "halo_use_skill" -> descriptor(name, ToolVisibility.BOOTSTRAP)
                else -> descriptor(name, ToolVisibility.DISCOVERABLE, keywords = setOf(name.removePrefix("halo_")))
            }
        }
        ToolRegistry(executors, descriptors)
    }

    private val planner = ToolExposurePlanner(registry)
    private val localPolicy = LocalModelToolPolicy.forProtocol(ToolCallProtocol.HALO_PROMPT)

    @Test
    fun `local bootstrap exposure contains only the three bootstrap schemas`() {
        val exposure = planner.localBootstrapExposure(localPolicy, ToolCallProtocol.HALO_PROMPT)

        assertEquals(
            setOf("halo_discover_tools", "halo_use_skill", "halo_current_time"),
            exposure.effective.map { it.value }.toSet(),
        )
    }

    @Test
    fun `local exposure never includes tools outside the allowlist`() {
        val definitions = planner.effectiveDefinitions(
            exposure = planner.localBootstrapExposure(localPolicy, ToolCallProtocol.HALO_PROMPT),
            localPolicy = localPolicy,
        )
        val names = definitions.map { it.name }

        assertFalse("halo_create_note must not reach a local model", names.contains("halo_create_note"))
        assertTrue(names.contains("halo_discover_tools"))
    }

    @Test
    fun `discovery expands the next turn`() {
        val initial = planner.localBootstrapExposure(localPolicy, ToolCallProtocol.HALO_PROMPT)
        val discoveryOutput = "status=discovered\n" + ToolExposureDirective.emit(listOf("halo_set_alarm"))

        val expanded = planner.expandFromToolOutputs(
            current = initial,
            toolOutputs = listOf(discoveryOutput),
            localPolicy = localPolicy,
        )

        assertTrue(expanded.effective.map { it.value }.contains("halo_set_alarm"))
        val nextTurnNames = planner.effectiveDefinitions(expanded, localPolicy = localPolicy).map { it.name }
        assertTrue("the next provider turn must see the discovered action", nextTurnNames.contains("halo_set_alarm"))
    }

    @Test
    fun `a tool outside the local allowlist cannot be widened by declaring it`() {
        val initial = planner.localBootstrapExposure(localPolicy, ToolCallProtocol.HALO_PROMPT)
        val forged = ToolExposureDirective.emit(listOf("halo_create_note"))

        val expanded = planner.expandFromToolOutputs(
            current = initial,
            toolOutputs = listOf(forged),
            localPolicy = localPolicy,
        )

        assertFalse(expanded.effective.map { it.value }.contains("halo_create_note"))
    }

    @Test
    fun `skill activation exposes that skill's tools and only after activation`() {
        val before = planner.effectiveDefinitions(
            planner.localBootstrapExposure(localPolicy, ToolCallProtocol.HALO_PROMPT),
            localPolicy = localPolicy,
        ).map { it.name }
        assertFalse(before.contains("halo_read_screen"))

        val activated = planner.expand(
            current = planner.localBootstrapExposure(localPolicy, ToolCallProtocol.HALO_PROMPT),
            requestedIds = listOf("halo_read_screen"),
            localPolicy = localPolicy,
        )

        val after = planner.effectiveDefinitions(activated, localPolicy = localPolicy).map { it.name }
        assertTrue(after.contains("halo_read_screen"))
    }

    @Test
    fun `the exposure gate still denies a tool whose skill is disabled`() {
        val initial = planner.localBootstrapExposure(localPolicy, ToolCallProtocol.HALO_PROMPT)
        val denyEverything = ToolExposureGate { _, _ ->
            ToolExposureDecision.Deny(ToolExposureDenyReason.SKILL_DISABLED)
        }

        val expanded = planner.expandFromToolOutputs(
            current = initial,
            toolOutputs = listOf(ToolExposureDirective.emit(listOf("halo_set_alarm"))),
            gate = denyEverything,
            localPolicy = localPolicy,
        )

        assertFalse(expanded.effective.map { it.value }.contains("halo_set_alarm"))
    }

    @Test
    fun `cloud screen privacy is enforced again when schemas are shown to provider`() {
        val exposure = ToolExposureSet(discovered = setOf(
            ToolId("halo_read_screen"), ToolId("halo_set_alarm")
        ))
        val gate = DefaultToolExposureGate()
        val blocked = planner.effectiveDefinitions(
            exposure, gate = gate,
            exposureContext = ToolExposureContext(
                isLocalTarget = false,
                policy = com.halo.sdk.provider.AiContextPolicy.CONSERVATIVE
            )
        ).map { it.name }
        assertFalse("Cloud must not receive screen tools without consent", blocked.contains("halo_read_screen"))
        assertTrue("Ordinary non-screen tool remains available", blocked.contains("halo_set_alarm"))

        val optedIn = planner.effectiveDefinitions(
            exposure, gate = gate,
            exposureContext = ToolExposureContext(
                isLocalTarget = false,
                policy = com.halo.sdk.provider.AiContextPolicy.CONSERVATIVE.copy(
                    allowCloudScreenContext = true
                )
            )
        ).map { it.name }
        assertTrue(optedIn.contains("halo_read_screen"))

        val blockedAgain = planner.effectiveDefinitions(
            exposure, gate = gate,
            exposureContext = ToolExposureContext(
                isLocalTarget = false,
                policy = com.halo.sdk.provider.AiContextPolicy.CONSERVATIVE
            )
        ).map { it.name }
        assertFalse("Revoked consent must take effect on next round", blockedAgain.contains("halo_read_screen"))
    }

    @Test
    fun `remote semantic app tools also respect screen consent`() {
        val gate = DefaultToolExposureGate()
        val blocked = ToolExposureContext(
            isLocalTarget = false,
            policy = com.halo.sdk.provider.AiContextPolicy.CONSERVATIVE
        )
        val consented = blocked.copy(policy = blocked.policy.copy(allowCloudScreenContext = true))
        for (tool in listOf("halo_current_app_capabilities", "halo_current_app_action", "halo_appfn_example")) {
            val action = descriptor(tool)
            assertEquals(ToolExposureDecision.Deny(ToolExposureDenyReason.REMOTE_SCREEN_DISABLED),
                gate.evaluate(action, blocked))
            assertEquals(ToolExposureDecision.Allow, gate.evaluate(action, consented))
        }
    }

    @Test
    fun `schema budget bounds what a local model is shown`() {
        val tiny = LocalModelToolPolicy(
            protocol = ToolCallProtocol.HALO_PROMPT,
            allowedToolIds = toolNames.toSet(),
            maxExposedSchemas = 2,
        )
        val exposure = ToolExposureSet(bootstrap = toolNames.map { ToolId(it) }.toSet())

        assertEquals(2, planner.effectiveDefinitions(exposure, localPolicy = tiny).size)
    }

    @Test
    fun `exposure directives are read only from declared lines`() {
        val declared = "status=discovered\n" + ToolExposureDirective.emit(listOf("halo_volume"))
        val proseOnly = "You could add halo_create_note if you wanted, or call halo_exposure_add=halo_volume inline"

        assertEquals(listOf("halo_volume"), ToolExposureDirective.parse(declared))
        assertEquals(emptyList<String>(), ToolExposureDirective.parse("halo_exposure_add is a marker"))
        // A marker buried inside a sentence is prose, not a directive.
        assertEquals(emptyList<String>(), ToolExposureDirective.parse(proseOnly))
    }

    @Test
    fun `skill activation directives are parsed separately from tool directives`() {
        val text = ToolExposureDirective.emitSkill("halo.phone.screen") + "\n" + ToolExposureDirective.emit(listOf("halo_read_screen"))

        assertEquals(listOf("halo.phone.screen"), ToolExposureDirective.parseSkills(text))
        assertEquals(listOf("halo_read_screen"), ToolExposureDirective.parse(text))
    }

    @Test
    fun `searchable discovery matches plain words a user would say`() {
        val discovery = ToolDiscoveryService(registry)

        assertTrue(discovery.search("alarm").any { it.name == "halo_set_alarm" })
        assertTrue(discovery.search("apps").any { it.name == "halo_list_apps" })
        assertTrue(discovery.search("something unrelated").isEmpty())
    }
}
