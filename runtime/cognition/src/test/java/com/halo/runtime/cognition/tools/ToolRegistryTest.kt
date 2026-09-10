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
}
