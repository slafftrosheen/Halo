package com.halo.app.di.tools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HostToolsContextTest {
    @Test fun canonicalGrantPreviewSurvivesBothHostAdapters() = runBlocking {
        var previews = 0
        val executor = object : ToolExecutor, ToolGrantPreviewProvider {
            override fun definitions() = listOf(ToolDefinition("grant_probe", "grant_probe", "Test", emptyMap()))
            override suspend fun grantSummary(toolCall: ToolCall): String { previews++; return "Exact canonical consequences" }
            override suspend fun execute(toolCall: ToolCall) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Probe")
        }
        val host = HostTools(ApplicationProvider.getApplicationContext<Context>(), additionalExecutors = listOf(executor))
        val provider = host as? ToolGrantPreviewProvider
        assertNotNull("Canonical approval preview must not be lost by host wrappers", provider)
        assertEquals("Exact canonical consequences", provider!!.grantSummary(ToolCall("grant", "grant_probe", emptyMap())))
        assertEquals(1, previews)
        assertEquals("", provider.grantSummary(ToolCall("read", "halo_get_volume", emptyMap())))
        assertEquals(1, previews)
    }
    @Test fun runtimeContextSurvivesBothHostAdaptersWithoutMintingAuthority() = runBlocking {
        val received = mutableListOf<ToolExecutionContext?>()
        val executor = object : ToolExecutor {
            override fun definitions() = listOf(ToolDefinition("context_probe", "context_probe", "Test", emptyMap()))
            override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
                received += null
                return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "No context", ToolRejectionKind.PERMISSION_REQUIRED)
            }
            override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
                received += context
                return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Probe", ToolRejectionKind.PERMISSION_REQUIRED)
            }
        }
        val host = HostTools(ApplicationProvider.getApplicationContext<Context>(), additionalExecutors = listOf(executor))
        val call = ToolCall("probe", "context_probe", emptyMap())
        val approved = ToolExecutionContext("runtime-interaction", directUserAuthority = false)
        host.execute(call, approved)
        host.execute(call)
        assertSame(approved, received[0])
        assertNull(received[1])
    }
}
