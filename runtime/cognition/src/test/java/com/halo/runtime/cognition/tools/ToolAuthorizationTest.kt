package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolAuthorizationTest {

    private val registry =
        ToolRegistry(
            listOf(
                EchoToolExecutor(),
                CurrentTimeToolExecutor()
            )
        )

    private val authorizer =
        InMemoryToolAuthorizer(registry)

    @Test
    fun `registered tool is authorized`() = runTest {

        val call =
            ToolCall(
                id = "1",
                functionName = "halo.echo",
                arguments =
                    mapOf(
                        "text" to "hello"
                    )
            )

        val result =
            authorizer.authorize(call)

        assertTrue(
            result is ToolAuthorization.Authorized
        )
    }

    @Test
    fun `unknown tool is rejected`() = runTest {

        val call =
            ToolCall(
                id = "2",
                functionName = "halo.not_real",
                arguments = emptyMap()
            )

        val result =
            authorizer.authorize(call)

        assertTrue(
            result is ToolAuthorization.Rejected
        )
    }

    @Test
    fun `oversized argument is rejected`() = runTest {

        val call =
            ToolCall(
                id = "3",
                functionName = "halo.echo",
                arguments =
                    mapOf(
                        "text" to
                            "x".repeat(
                                ToolExecutionLimits
                                    .MAX_TOOL_ARGUMENT_STRING_CHARS + 1
                            )
                    )
            )

        val result =
            authorizer.authorize(call)

        assertTrue(
            result is ToolAuthorization.Rejected
        )
    }
}
