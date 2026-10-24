package com.halo.app.di.tools

import android.app.Application
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SpeechInventoryToolsTest {
    private class Owner : SpeechInventoryControl {
        var calls = 0
        val snapshot = SpeechInventorySnapshot("speech-inventory:" + "a".repeat(64), "AUTOMATIC", null, "en",
            false, null, false, false, 0, 0, null, false, listOf(SpeechAssetMetadata("whisper-tiny", "Whisper Tiny",
                "speech.sherpa_onnx.whisper_tiny", "speech-asset:" + "b".repeat(64), false, 1000, true,
                "speech-artifact:" + "c".repeat(64))))
        override suspend fun current() = snapshot
        override suspend fun dispatch(modelId: String, sourceVersion: String, assetVersion: String, action: String,
            admitted: () -> Boolean, artifactVersion: String?): String {
            check(admitted() && sourceVersion == snapshot.sourceVersion && assetVersion == snapshot.assets.single().assetVersion)
            if (action == "remove") check(artifactVersion == snapshot.assets.single().artifactVersion)
            calls++; return "native-request"
        }
    }
    @Test fun readDoesNotInstallAndActionReportsDispatchWithoutQualification() = runBlocking {
        val owner = Owner(); val tools = SpeechInventoryTools(owner) { true }
        val read = tools.execute(ToolCall("read", "halo_speech_inventory", emptyMap())) as ToolExecutionResult.Success
        assertEquals("NOT_TESTED", JSONObject(read.output).getString("qualification"))
        assertFalse(read.output.contains("https://")); assertEquals(0, owner.calls)
        val args = mapOf("modelId" to "whisper-tiny", "sourceVersion" to owner.snapshot.sourceVersion,
            "assetVersion" to owner.snapshot.assets.single().assetVersion, "action" to "install")
        val result = tools.execute(ToolCall("install", "halo_speech_model_action", args), ToolExecutionContext(interactionId = "current")) as ToolExecutionResult.Success
        assertEquals(ActionTruth.DISPATCHED, result.actionTruth)
        assertFalse(JSONObject(result.output).getBoolean("completionConfirmed")); assertEquals(1, owner.calls)
    }
    @Test fun noInteractionUnknownUrlsAndRemovalWithoutExactArtifactCannotReachOwner() = runBlocking {
        val owner = Owner(); var admitted = false
        val tools = SpeechInventoryTools(owner) { admitted }
        val args = mapOf("modelId" to "whisper-tiny", "sourceVersion" to owner.snapshot.sourceVersion,
            "assetVersion" to owner.snapshot.assets.single().assetVersion, "action" to "remove")
        val context = ToolExecutionContext(interactionId = "current")
        assertTrue(tools.execute(ToolCall("denied", "halo_speech_model_action", args), context) is ToolExecutionResult.Rejected)
        admitted = true
        assertTrue(tools.execute(ToolCall("missing", "halo_speech_model_action", args), context) is ToolExecutionResult.Rejected)
        val remove = args + ("artifactVersion" to owner.snapshot.assets.single().artifactVersion!!)
        assertTrue(tools.execute(ToolCall("missing-interaction", "halo_speech_model_action", remove)) is ToolExecutionResult.Rejected)
        assertTrue(tools.execute(ToolCall("url", "halo_speech_model_action", remove + ("url" to "https://untrusted.test/model")), context) is ToolExecutionResult.Rejected)
        assertTrue(tools.execute(ToolCall("unknown", "halo_speech_model_action", remove + ("modelId" to "unknown")), context) is ToolExecutionResult.Rejected)
        assertEquals(0, owner.calls)
        assertTrue(tools.execute(ToolCall("remove", "halo_speech_model_action", remove), context) is ToolExecutionResult.Success)
        tools.definitions().forEach { assertNotNull(SpeechInventoryOperability.forDefinition(it)) }
    }
}
