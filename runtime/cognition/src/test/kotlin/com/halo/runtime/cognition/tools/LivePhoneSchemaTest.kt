package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import org.junit.Assert.*
import org.junit.Test

class LivePhoneSchemaTest {
    private class Phone : ToolExecutor {
        var enabled = true
        var actions = listOf("back")
        override fun definitions() = if (!enabled) emptyList() else listOf(ToolDefinition("phone", "phone", "Phone action",
            mapOf("type" to "object", "properties" to mapOf("action" to mapOf("type" to "string", "enum" to actions)),
                "required" to listOf("action"), "additionalProperties" to false)))
        override suspend fun execute(toolCall: ToolCall) = ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "dispatched")
    }
    @Test fun discoveryUpdatesSchemaWithoutChangingMandatoryAuthorization() {
        val phone = Phone()
        val security = ToolSecurityPolicy(risk = ToolRiskClass.EXTERNAL_SIDE_EFFECT, confirmation = ConfirmationPolicy.ALWAYS, replaySafety = ReplaySafety.NEVER_REPLAY)
        val descriptor = ToolDescriptor(id = ToolId("phone"), definition = phone.definitions().single(), category = ToolCategory.HOST_CONTROL, security = security)
        val registry = ToolRegistry(listOf(phone), listOf(descriptor))
        phone.actions = listOf("back", "menu")
        assertEquals(phone.definitions().single(), registry.descriptor("phone")!!.definition)
        assertEquals(phone.definitions().single(), registry.descriptors().single().definition)
        assertEquals(security, registry.descriptor("phone")!!.security)
        assertNull(registry.validate(ToolCall("call", "phone", mapOf("action" to "menu"))))
    }
    @Test fun permissionGrantedAfterConstructionCanExposeAnOwnedTool() {
        val phone = Phone().apply { enabled = false }
        val registry = ToolRegistry(listOf(phone))
        assertFalse(registry.contains("phone"))
        phone.enabled = true
        assertTrue(registry.contains("phone"))
        assertSame(phone, registry.executorFor("phone"))
        phone.enabled = false
        assertNull(registry.descriptor("phone"))
        assertNotNull(registry.validate(ToolCall("call", "phone", mapOf("action" to "back"))))
    }
}
