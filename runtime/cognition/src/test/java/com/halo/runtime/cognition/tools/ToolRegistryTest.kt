package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToolRegistryTest {

    @Test
    fun `registry contains definitions from executors`() {
        val registry = ToolRegistry(
            listOf(EchoToolExecutor(), CurrentTimeToolExecutor())
        )
        val definitions = registry.definitions()
        assertEquals(2, definitions.size)
        assertTrue(definitions.any { it.id == "halo.echo" })
        assertTrue(definitions.any { it.id == "halo.current_time" })
    }

    @Test
    fun `unknown tool function name is rejected`() = runTest {
        val registry = ToolRegistry(listOf(EchoToolExecutor()))
        val toolCall = ToolCall(id = "c1", functionName = "unknown.tool", arguments = emptyMap())

        val result = registry.execute(toolCall)
        assertTrue(result is ToolExecutionResult.Rejected)
        val rejected = result as ToolExecutionResult.Rejected
        assertEquals("c1", rejected.toolCallId)
        assertTrue(rejected.reason.contains("Unknown tool"))
    }

    @Test
    fun `echo tool executes successfully`() = runTest {
        val registry = ToolRegistry(listOf(EchoToolExecutor()))
        val toolCall = ToolCall(
            id = "c1",
            functionName = "halo.echo",
            arguments = mapOf("text" to "Hello Halo")
        )

        val result = registry.execute(toolCall)
        assertTrue(result is ToolExecutionResult.Success)
        val success = result as ToolExecutionResult.Success
        assertEquals("c1", success.toolCallId)
        assertEquals("Echo: Hello Halo", success.output)
    }

    @Test
    fun `current time tool executes successfully`() = runTest {
        val registry = ToolRegistry(listOf(CurrentTimeToolExecutor { 1700000000000L }))
        val toolCall = ToolCall(
            id = "c1",
            functionName = "halo.current_time",
            arguments = emptyMap()
        )

        val result = registry.execute(toolCall)
        assertTrue(result is ToolExecutionResult.Success)
        val success = result as ToolExecutionResult.Success
        assertEquals("c1", success.toolCallId)
        assertTrue(success.output.startsWith("Current time:"))
    }

    @Test
    fun `oversized string argument is rejected`() = runTest {
        val registry = ToolRegistry(listOf(EchoToolExecutor()))
        val hugeString = "a".repeat(3000)
        val toolCall = ToolCall(
            id = "c1",
            functionName = "halo.echo",
            arguments = mapOf("text" to hugeString)
        )

        val result = registry.execute(toolCall)
        assertTrue(result is ToolExecutionResult.Rejected)
        val rejected = result as ToolExecutionResult.Rejected
        assertTrue(rejected.reason.contains("exceeds maximum length"))
    }
}
