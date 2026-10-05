package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    private val registry =
        ToolRegistry(
            listOf(
                EchoToolExecutor(),
                CurrentTimeToolExecutor()
            )
        )

    @Test
    fun `contains registered tool by name`() {
        assertTrue(
            registry.contains("halo.echo")
        )
    }

    @Test
    fun `validate rejects unknown tool`() {

        val call =
            ToolCall(
                id = "unknown-1",
                functionName = "halo.unknown",
                arguments = emptyMap()
            )

        val result =
            registry.validate(call)

        assertEquals(
            "Unknown tool: halo.unknown",
            result?.reason
        )
    }

    @Test
    fun `authorized execution executes registered tool`() = runTest {

        val call =
            ToolCall(
                id = "echo-1",
                functionName = "halo.echo",
                arguments =
                    mapOf(
                        "text" to "hello"
                    )
            )

        val result =
            registry.executeAuthorized(
                ToolAuthorization.Authorized(call)
            )

        assertTrue(
            result is ToolExecutionResult.Success
        )
    }

    @Test fun rejectionTaxonomyDistinguishesUnavailableFromMalformed() = runTest {
        assertEquals(ToolRejectionKind.TOOL_UNKNOWN, registry.validate(ToolCall("unknown", "missing", emptyMap()))!!.kind)
        assertEquals(ToolRejectionKind.INVALID_ARGUMENTS, registry.validate(ToolCall("bad", "halo.echo", mapOf("text" to listOf(Any()))))!!.kind)
        val skills = UseSkillToolExecutor(com.halo.runtime.cognition.skills.BundledSkillInstructionSource(), null)
        assertEquals(ToolRejectionKind.CAPABILITY_UNAVAILABLE, (skills.execute(ToolCall("missing", "halo_use_skill", mapOf("skillId" to "absent"))) as ToolExecutionResult.Rejected).kind)
        assertEquals(ToolRejectionKind.INVALID_ARGUMENTS, (skills.execute(ToolCall("bad", "halo_use_skill", emptyMap())) as ToolExecutionResult.Rejected).kind)
    }

}
