package com.halo.app.di.tools

import android.app.Application
import com.halo.app.ai.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ProviderConfigurationToolsTest {
    private class Owner : ProviderConfigurationControl {
        var current = ProviderConfigurationSnapshot("fixture", "Provider https://private.test/v1", "REMOTE", true,
            false, "CONFIGURED", "CACHED", "provider-configuration:" + "a".repeat(64),
            listOf(ProviderModelMetadata("text", "Text", "REMOTE_CACHED", listOf("TEXT")),
                ProviderModelMetadata("https://private.test/model", "Unsafe address", "REMOTE_CACHED", emptyList())))
        var writes = 0
        override suspend fun providerConfigurations() = listOf(current)
        override suspend fun providerConfiguration(providerId: String) = current.takeIf { it.providerId == providerId }
        override suspend fun requestProviderCheck(providerId: String, expectedVersion: String,
            command: ProviderConfigurationCommand, admitted: () -> Boolean): ProviderConfigurationSnapshot? {
            if (!admitted() || providerId != current.providerId || current.sourceVersion != expectedVersion) return null
            writes++
            current = current.copy(connection = "AUTH_FAILED", diagnosticStage = "AUTHENTICATE",
                diagnosticOutcome = "AUTH_FAILED", sourceVersion = "provider-configuration:" + "b".repeat(64))
            return current
        }
    }
    @Test fun readsAreBoundedMetadataAndNeverExposeEndpointFieldsOrInvokeChecks() = runBlocking {
        val owner = Owner(); val tools = ProviderConfigurationTools(owner) { true }
        val result = tools.execute(ToolCall("read", "halo_provider_state", mapOf("providerId" to "fixture"))) as ToolExecutionResult.Success
        val json = JSONObject(result.output)
        assertEquals(ActionTruth.ACKNOWLEDGED, result.actionTruth)
        assertFalse(json.getBoolean("networkRequested"))
        assertEquals(1, json.getJSONArray("models").length())
        assertFalse(result.output.contains("private.test"))
        assertFalse(json.has("fields")); assertFalse(json.has("credential")); assertFalse(json.has("endpoint"))
        assertEquals(0, owner.writes)
    }
    @Test fun writesNeedAdmissionInteractionAndExactObservedVersion() = runBlocking {
        val owner = Owner(); var allowed = false
        val tools = ProviderConfigurationTools(owner) { allowed }
        val args = mapOf("providerId" to "fixture", "sourceVersion" to owner.current.sourceVersion)
        val call = ToolCall("check", "halo_provider_test_connection", args)
        val context = ToolExecutionContext(interactionId = "explicit-current-interaction")
        assertTrue(tools.execute(call, context) is ToolExecutionResult.Rejected)
        allowed = true
        assertTrue(tools.execute(call) is ToolExecutionResult.Rejected)
        assertTrue(tools.execute(call.copy(arguments = args + ("sourceVersion" to "provider-configuration:${"c".repeat(64)}")), context) is ToolExecutionResult.Rejected)
        assertEquals(0, owner.writes)
        val result = tools.execute(call, context) as ToolExecutionResult.Success
        val output = JSONObject(result.output)
        assertEquals("AUTH_FAILED", output.getString("diagnosticOutcome"))
        assertEquals("AUTH_FAILED", output.getString("connection"))
        assertTrue(output.getBoolean("checkCompleted")); assertFalse(output.getBoolean("generationTested"))
        assertFalse(output.getBoolean("selectionChanged"))
        assertTrue(tools.execute(call, context) is ToolExecutionResult.Rejected)
        assertEquals(1, owner.writes)
    }
    @Test fun closedArgumentsCannotInjectCredentialsOrGenerationConsent() = runBlocking {
        val owner = Owner(); val tools = ProviderConfigurationTools(owner) { true }
        val args = mapOf("providerId" to "fixture", "sourceVersion" to owner.current.sourceVersion)
        listOf("endpoint", "apiKey", "allowBillable", "modelId").forEach { key ->
            assertTrue(tools.execute(ToolCall("bad", "halo_provider_refresh_models", args + (key to "injected")),
                ToolExecutionContext(interactionId = "current")) is ToolExecutionResult.Rejected)
        }
        assertEquals(0, owner.writes)
        tools.definitions().forEach { assertNotNull(ProviderConfigurationOperability.forDefinition(it)) }
    }
}
