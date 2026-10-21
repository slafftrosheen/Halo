package com.halo.app.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.ai.readRuntimeAiContextPolicy
import com.halo.app.ai.KEY_CLOUD_IMAGES
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.runtime.cognition.vision.VisionCandidate
import com.halo.runtime.cognition.vision.VisionStage
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Explicit device-only synthetic image checks. Never sends user media or invokes tools. */
@RunWith(AndroidJUnit4::class)
class ImageQuestionPhoneTest {
    @Test fun localGemmaImageQuestions() = runBlocking { questions(local = true) }
    @Test fun cloudImageQuestions() = runBlocking { questions(local = false) }
    @Test fun localGemmaChatImageQuestion() = runBlocking { questions(local = true, chatOnly = true) }
    @Test fun cloudChatImageQuestion() = runBlocking { questions(local = false, chatOnly = true) }

    private suspend fun questions(local: Boolean, chatOnly: Boolean = false) {
        val args = InstrumentationRegistry.getArguments()
        require(args.getString("allowImageInference") == "true") { "Explicit image inference opt-in required" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val run = requireNotNull(args.getString("runId"))
        val output = File(context.filesDir, "image-question-phone.jsonl")
        fun record(stage: String, fill: JSONObject.() -> Unit = {}) {
            val row = JSONObject().put("runId", run).put("stage", stage).put("local", local)
                .put("sourceStamp", args.getString("sourceStamp")).put("chat", chatOnly).put("atMs", System.currentTimeMillis())
            row.fill(); output.appendText(row.toString() + "\n")
        }
        val interaction = "image-phone:$run:${UUID.randomUUID()}"
        val previousMode = Graph.privacyModes.mode.value
        val remoteGrants = mutableListOf<String>()
        val previousConsent = Graph.storage.getBoolean(PaidMediaPolicy.CONSENT_KEY, false)
        val previousSharing = Graph.storage.getBoolean(KEY_CLOUD_IMAGES, false)
        val selectedBefore = Graph.aiProviderRouter.selectedProviderId.value to Graph.aiProviderRouter.selectedModelId.value
        record("STARTED")
        try {
            check(!Graph.kidsPreferences.getEnabled()) { "Restricted mode blocks this diagnostic" }
            val providerId = args.getString("providerId") ?: if (local) "provider.litert.local" else "openrouter"
            val provider = Graph.aiProviderRouter.getAllProviders().filterIsInstance<AiInferenceProvider>()
                .single { it.id == providerId }
            check(provider.isLocal == local)
            val modelId = if (local) args.getString("modelId", "gemma-4-e2b")!! else {
                check(args.getString("allowCloudMedia") == "true") { "Explicit remote media consent required" }
                Graph.setPrivacyMode(com.halo.app.ai.PrivacyMode.CLOUD)
                val models = provider.listModels()
                record("CATALOG") { put("models", models.size); put("imageModels", models.count { it.hasVerifiedImageInput() }) }
                args.getString("modelId") ?: models.firstOrNull {
                    it.id.endsWith(":free") && it.hasVerifiedImageInput()
                }?.id ?: error("No advertised free image model; supply an explicit modelId")
            }
            record("TARGET") { put("providerId", provider.id); put("modelId", modelId) }
            if (local) {
                record("ARTIFACT_VERIFICATION_STARTED")
                val descriptor = Graph.providerGraph.modelManager.verifyPhysicalArtifact(modelId)
                requireNotNull(descriptor) { "Installed Gemma artifact unavailable" }
                record("ARTIFACT_VERIFIED") { put("sha256", descriptor.sha256); put("bytes", descriptor.bytes) }
                val candidate = VisionCandidate(provider, provider.listModels().single { it.id == modelId })
                if (chatOnly) assertTrue("Restarted model must retain vision qualification", candidate.model.hasVerifiedImageInput())
                else {
                    val token = (1000 + java.security.SecureRandom().nextInt(9000)).toString()
                    val ref = Graph.mediaAssetStore.ingest(MediaAssetContent(image(Color.WHITE, "HALO $token"), "image/png"),
                        MediaProvenance.GENERATED, interaction)
                    record("QUALIFICATION_STARTED")
                    val row = Graph.visionLab.compare(ref, listOf(candidate), Graph.storage.readRuntimeAiContextPolicy(),
                        "Read the word and number printed in this image.", true, listOf("HALO", token)).single()
                    record("QUALIFICATION_FINISHED") { put("result", row.stage.name); put("failure", row.failure) }
                    assertEquals("Local synthetic vision qualification failed: ${row.failure}", VisionStage.COMPLETE, row.stage)
                }
            }
            for ((color, expected) in listOf(Color.RED to "red", Color.BLUE to "blue").take(if (chatOnly) 1 else 2)) {
                record("QUESTION_STARTED") { put("case", expected) }
                val bytes = image(color)
                val ref = Graph.mediaAssetStore.ingest(MediaAssetContent(bytes, "image/png"), MediaProvenance.GENERATED, interaction)
                if (!local) {
                    // Cloud mode is the only sharing grant; no per-image approval is installed.
                    Graph.workspaceMediaAiPolicy.allowTemporaryCloud(ref.id, false)
                    remoteGrants += ref.id
                }
                assertTrue(Graph.workspaceMediaAiPolicy.permits(ref, local))
                val prepared = Graph.imageInputPreparer.prepare(Graph.mediaAssetResolver.open(ref), ref)
                val request = AiRequest(text = "What color fills this image? Reply with exactly red or blue in English.",
                    image = prepared.content.bytes, mimeTypes = listOf(prepared.content.mimeType), modelId = modelId,
                    maxOutputTokens = 128, toolUseMode = ToolUseMode.NONE, reasoningMode = AiReasoningMode.DISABLED)
                val workload = AiRoleRequirements.workload(AiModelRole.VISION).copy(
                    pinnedTarget = AiModelRef(provider.id, modelId), allowFallback = false)
                val response = withTimeout(if (local) 420_000L else 90_000L) {
                    if (chatOnly) chat(request, provider.id, modelId, run, local)
                    else Graph.aiProviderRouter.execute(request, workload, AiContext.EMPTY)
                }
                val correct = Regex("\\b$expected\\b", RegexOption.IGNORE_CASE).containsMatchIn(response.text) &&
                    !Regex("\\b${if (expected == "red") "blue" else "red"}\\b", RegexOption.IGNORE_CASE).containsMatchIn(response.text)
                record("QUESTION_FINISHED") {
                    put("case", expected); put("correct", correct); put("providerId", response.providerId)
                    put("modelId", response.resolvedModelId); put("backend", response.executionBackend)
                    put("responseLength", response.text.length)
                }
                assertTrue("Image answer was incorrect for $expected", correct)
                assertEquals(provider.id, response.providerId)
                assertEquals(modelId, response.modelId)
                if (local) assertEquals(modelId, response.resolvedModelId)
                else assertFalse(response.resolvedModelId.isNullOrBlank())
                assertTrue(response.toolCalls.isEmpty())
            }
            assertEquals(selectedBefore, Graph.aiProviderRouter.selectedProviderId.value to Graph.aiProviderRouter.selectedModelId.value)
            record("COMPLETED")
        } catch (failure: Throwable) {
            record("FAILED") { put("exception", failure.javaClass.simpleName)
                if (failure is AiProviderException) { put("type", failure.type.name); put("code", failure.details.providerCode) } }
            throw failure
        } finally {
            withContext(NonCancellable) {
                if (!local) Graph.setPrivacyMode(previousMode)
                if (!local) Graph.contextPrivacyViewModel.refresh()
                remoteGrants.forEach(Graph.workspaceMediaAiPolicy::releaseTemporary)
                Graph.mediaAssetStore.releaseInteraction(interaction)
            }
        }
    }

    private suspend fun chat(request: AiRequest, providerId: String, modelId: String, run: String, local: Boolean): AiResponse = coroutineScope {
        val conversations = Graph.conversationStore
        conversations.awaitReady()
        val previousConversation = conversations.activeConversationId()
        val previousBinding = Graph.aiProviderRouter.roleBinding(AiModelRole.VISION)
        val identity = "image-chat:$run"
        val session = Graph.cognitiveSession
        check(session.state.value.let { it is CognitiveSessionState.Idle || it is CognitiveSessionState.Completed || it is CognitiveSessionState.Failed })
        try {
            conversations.newConversation()
            val conversation = requireNotNull(conversations.activeConversationId())
            conversations.renameConversation(conversation, "QA image question $run")
            Graph.aiProviderRouter.setRoleBinding(AiRoleBinding(AiModelRole.VISION, providerId, modelId, AiRoleSelection.EXPLICIT))
            val finished = async(start = CoroutineStart.UNDISPATCHED) {
                session.state.drop(1).first { it is CognitiveSessionState.Completed || it is CognitiveSessionState.Failed }
            }
            session.submit(request.copy(interactionId = identity), AiContextPolicy(includeConversation = false,
                includeDeviceState = false, includeRecentEvents = false, includeMemories = false,
                includeAvailableTools = false, includeImages = true, includeWorkspace = false,
                allowCloudImages = !local), metadata = mapOf("conversationId" to conversation, "interactionId" to identity))
            when (val result = finished.await()) {
                is CognitiveSessionState.Completed -> result.response
                is CognitiveSessionState.Failed -> error("Chat image request failed: ${result.message}")
                else -> error("Unexpected chat state")
            }
        } finally {
            withContext(NonCancellable) {
                session.cancelInteraction(identity)
                Graph.aiProviderRouter.setRoleBinding(previousBinding)
                previousConversation?.let { conversations.activateExistingConversation(it) }
            }
        }
    }

    private fun image(color: Int, label: String? = null): ByteArray {
        val bitmap = Bitmap.createBitmap(384, 192, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap); canvas.drawColor(color)
            label?.let { canvas.drawText(it, 20f, 110f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.BLACK; textSize = 48f }) }
            ByteArrayOutputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)); it.toByteArray() }
        } finally { bitmap.recycle() }
    }
}
