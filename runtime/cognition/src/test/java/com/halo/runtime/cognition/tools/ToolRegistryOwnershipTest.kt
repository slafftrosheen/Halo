package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import org.junit.Assert.*
import org.junit.Test

class ToolRegistryOwnershipTest {
    private class Source(val definition: ToolDefinition) : ToolExecutor {
        var enabled = true
        override fun definitions() = if (enabled) listOf(definition) else emptyList()
        override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = error("Lookup tests must not dispatch")
    }

    private fun descriptor(source: Source, risk: ToolRiskClass) = ToolDescriptor(
        ToolId(source.definition.id), source.definition, ToolCategory.SYSTEM,
        security = ToolSecurityPolicy(risk = risk, confirmation = ConfirmationPolicy.ALWAYS),
    )

    @Test fun duplicateDynamicToolsKeepSecurityAndDispatchWithTheSameOwner() {
        val first = Source(ToolDefinition("source-a", "shared", "First"))
        val second = Source(ToolDefinition("source-b", "shared", "Second"))
        val registry = ToolRegistry(emptyList())
        registry.registerDynamic(first, listOf(descriptor(first, ToolRiskClass.SENSITIVE)))
        registry.registerDynamic(second, listOf(descriptor(second, ToolRiskClass.READ_ONLY)))
        assertEquals(listOf(first.definition), registry.definitions())
        assertSame(first, registry.executorFor("shared"))
        assertSame(first, registry.executorFor("source-a"))
        assertFalse(registry.contains("source-b"))
        assertEquals(ToolRiskClass.SENSITIVE, registry.descriptor("shared")!!.security.risk)
        assertEquals(registry.descriptor("shared"), registry.descriptor("source-a"))
        registry.unregisterDynamic(second)
        assertEquals(ToolRiskClass.SENSITIVE, registry.descriptor("shared")!!.security.risk)
    }

    @Test fun removingDisabledOwnerPromotesRemainingSourceWithItsOwnDescriptor() {
        val first = Source(ToolDefinition("source-a", "shared", "First"))
        val second = Source(ToolDefinition("source-b", "shared", "Second"))
        val registry = ToolRegistry(emptyList())
        registry.registerDynamic(first, listOf(descriptor(first, ToolRiskClass.READ_ONLY)))
        registry.registerDynamic(second, listOf(descriptor(second, ToolRiskClass.SENSITIVE)))
        first.enabled = false
        registry.unregisterDynamic(first, setOf("source-a", "shared"))
        assertEquals(1, registry.dynamicSourceCount())
        assertSame(second, registry.executorFor("shared"))
        assertEquals(ToolRiskClass.SENSITIVE, registry.descriptor("shared")!!.security.risk)
    }

    @Test fun staticToolsAndAliasesCannotBeShadowedOrDuplicated() {
        val first = Source(ToolDefinition("static-id", "static-name", "Static"))
        val duplicate = Source(ToolDefinition("other-id", "static-name", "Duplicate"))
        val aliasCollision = Source(ToolDefinition("static-name", "dynamic-name", "Alias collision"))
        val registry = ToolRegistry(listOf(first, duplicate), listOf(descriptor(first, ToolRiskClass.SENSITIVE)))
        registry.registerDynamic(aliasCollision, listOf(descriptor(aliasCollision, ToolRiskClass.READ_ONLY)))
        assertEquals(listOf(first.definition), registry.definitions())
        assertFalse(registry.contains("other-id"))
        assertFalse(registry.contains("dynamic-name"))
        assertSame(first, registry.executorFor("static-name"))
        assertEquals(ToolRiskClass.SENSITIVE, registry.descriptor("static-id")!!.security.risk)
    }

    @Test fun reregisteringSourceUpdatesMetadataWithoutCreatingAnotherOwner() {
        val source = Source(ToolDefinition("dynamic-id", "dynamic-name", "Dynamic"))
        val registry = ToolRegistry(emptyList())
        registry.registerDynamic(source, listOf(descriptor(source, ToolRiskClass.READ_ONLY)))
        registry.registerDynamic(source, listOf(descriptor(source, ToolRiskClass.SENSITIVE)))
        assertEquals(1, registry.dynamicSourceCount())
        assertEquals(1, registry.definitions().size)
        assertEquals(ToolRiskClass.SENSITIVE, registry.descriptor("dynamic-name")!!.security.risk)
    }

    @Test fun dynamicDependenciesAreAvailableUntilTheirSourceIsRemoved() {
        val dependency = Source(ToolDefinition("dependency-id", "dependency", "Dependency"))
        val source = Source(ToolDefinition("action-id", "action", "Action"))
        val registry = ToolRegistry(emptyList())
        registry.registerDynamic(dependency)
        registry.registerDynamic(source, listOf(descriptor(source, ToolRiskClass.SENSITIVE).copy(
            dependencies = setOf(ToolId("dependency-id")),
        )))
        val call = ToolCall("call", "action-id", emptyMap())
        assertNull(registry.validate(call))
        registry.unregisterDynamic(dependency)
        assertEquals("Tool dependency not registered: dependency-id", registry.validate(call)?.reason)
    }
}
