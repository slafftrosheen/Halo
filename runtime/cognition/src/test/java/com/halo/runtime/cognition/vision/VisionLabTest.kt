package com.halo.runtime.cognition.vision

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.multimodal.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class VisionLabTest {
    private val bytes = byteArrayOf(1, 2, 3)
    private val ref = MediaAssetRef("image", MediaKind.IMAGE, "image/png", 3, null, 1, MediaProvenance.SHARED_FILE)
    private class Provider(override val id: String) : AiInferenceProvider {
        override val displayName = id
        override val isLocal = false
        override val status = MutableStateFlow(ProviderStatus.READY)
        val requests = mutableListOf<AiRequest>()
        val contexts = mutableListOf<AiContext>()
        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT, AiCapability.IMAGE)
        override suspend fun listModels() = emptyList<AiModel>()
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            requests += request; contexts += context
            return AiResponse(text = "HALO 42", providerId = id, modelId = request.modelId)
        }
    }
    private fun model(provider: Provider, evidence: CapabilityEvidence = CapabilityEvidence.AUTHORITATIVE_METADATA) =
        AiModel("vision", "Vision", provider.id, setOf(AiCapability.TEXT, AiCapability.IMAGE),
            contextLength = 4096, supportsStreaming = false, supportsTools = false,
            executionLocation = AiExecutionLocation.REMOTE, capabilityEvidence = mapOf(AiCapability.IMAGE to evidence))

    @Test fun samePreparedSourceUsedOnceNoHistoryAndProbePersistsEvidence() = runTest {
        var opens = 0; var prepares = 0
        val media = object : MediaAssetResolver {
            override suspend fun metadata(ref: MediaAssetRef) = null
            override suspend fun open(ref: MediaAssetRef): MediaAssetContent { opens++; return MediaAssetContent(bytes, "image/png") }
        }
        val preparer = object : ImageInputPreparer {
            override suspend fun prepare(content: MediaAssetContent, sourceRef: MediaAssetRef?, limits: ImageInputLimits): PreparedImageInput {
                prepares++
                return PreparedImageInput(MediaAssetContent(bytes, "image/png"),
                    ImagePreparationSummary(sourceRef, 2, 2, 2, 2, 3, 3, "image/png", false, "test"))
            }
        }
        val storage = MemoryStorage().apply { putBoolean(PaidMediaPolicy.CONSENT_KEY, true) }
        val lab = VisionLab(media, preparer, MediaObjectPolicyResolver { _, _ -> true }, storage)
        val providers = listOf(Provider("gemini"), Provider("openrouter"), Provider("openai"))
        val rows = lab.compare(ref, providers.map { VisionCandidate(it, model(it)) }, AiContextPolicy(allowCloudImages = true), "Read the image")
        assertEquals(1, opens); assertEquals(1, prepares)
        assertTrue(rows.all { it.stage == VisionStage.COMPLETE && it.preparedBytes == 3 })
        providers.forEach { provider ->
            assertSame(bytes, provider.requests.single().image)
            assertEquals("image/png", provider.requests.single().imageMimeType())
            assertEquals(ToolUseMode.NONE, provider.requests.single().toolUseMode)
            assertEquals(AiContext.EMPTY, provider.contexts.single())
        }
        val probe = lab.compare(ref, listOf(VisionCandidate(providers[0], model(providers[0], CapabilityEvidence.HEURISTIC))),
            AiContextPolicy(allowCloudImages = true), "Read the image", true, listOf("HALO", "42"))
        assertEquals(VisionStage.COMPLETE, probe.single().stage)
        assertNotNull(storage.getString(VisionLab.probeKey("gemini", "vision")))
        val changed = lab.compare(ref, listOf(VisionCandidate(providers[0], model(providers[0], CapabilityEvidence.HEURISTIC))),
            AiContextPolicy(allowCloudImages = true), "Read the image again")
        assertEquals(VisionStage.CAPABILITY, changed.single().stage)
        assertEquals(2, providers[0].requests.size) // Initial comparison and explicit probe only.
    }
    @Test fun policyAndEvidenceRejectionsNeverAcquireOrSendImage() = runTest {
        val media = object : MediaAssetResolver {
            override suspend fun metadata(ref: MediaAssetRef) = null
            override suspend fun open(ref: MediaAssetRef): MediaAssetContent = error("Must not acquire")
        }
        val preparer = object : ImageInputPreparer {
            override suspend fun prepare(content: MediaAssetContent, sourceRef: MediaAssetRef?, limits: ImageInputLimits): PreparedImageInput = error("Must not prepare")
        }
        val provider = Provider("openai")
        val lab = VisionLab(media, preparer, MediaObjectPolicyResolver { _, _ -> true }, MemoryStorage())
        val blocked = lab.compare(ref, listOf(VisionCandidate(provider, model(provider))), AiContextPolicy(), "Read")
        assertEquals(VisionStage.POLICY, blocked.single().stage)
        val unverified = lab.compare(ref, listOf(VisionCandidate(provider, model(provider, CapabilityEvidence.HEURISTIC))), AiContextPolicy(allowCloudImages = true), "Read")
        assertEquals(VisionStage.CAPABILITY, unverified.single().stage)
        val free = lab.compare(ref, listOf(VisionCandidate(provider, model(provider).copy(id = "openrouter/free"))), AiContextPolicy(allowCloudImages = true), "Read")
        assertEquals(VisionStage.CAPABILITY, free.single().stage)
        assertTrue(provider.requests.isEmpty())
    }
    @Test fun importedLocalVisionCanRunItsFirstProbeButNotPrivateImagesUntilQualified() = runTest {
        val provider = Provider("provider.litert")
        val candidate = VisionCandidate(provider, model(provider, CapabilityEvidence.HEURISTIC)
            .copy(executionLocation = AiExecutionLocation.ON_DEVICE))
        var checked = 0
        var approved = 0
        val media = object : MediaAssetResolver {
            override suspend fun metadata(ref: MediaAssetRef) = null
            override suspend fun open(ref: MediaAssetRef) = MediaAssetContent(bytes, "image/png")
        }
        val imagePreparer = object : ImageInputPreparer {
            override suspend fun prepare(content: MediaAssetContent, sourceRef: MediaAssetRef?, limits: ImageInputLimits) =
                PreparedImageInput(content, ImagePreparationSummary(sourceRef, 2, 2, 2, 2, 3, 3, "image/png", false, "test"))
        }
        val lab = VisionLab(media, imagePreparer, MediaObjectPolicyResolver { _, _ -> true },
            MemoryStorage(), localQualified = { checked++; false },
            onLocalProbePassed = { _, _ -> approved++ })
        val rows = lab.compare(ref, listOf(candidate), AiContextPolicy(), "Read", true, listOf("HALO", "42"))
        assertEquals(VisionStage.COMPLETE, rows.single().stage)
        assertEquals(1, approved)
        assertEquals(0, checked) // qualification cannot be required before obtaining the first proof.
        val normal = lab.compare(ref, listOf(candidate), AiContextPolicy(), "Describe")
        assertEquals(VisionStage.CAPABILITY, normal.single().stage)
        assertEquals(1, checked)
        assertEquals(1, provider.requests.size)
    }

    private class MemoryStorage : Storage {
        val data = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = data[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { data[key] = value }
        override fun getInt(key: String, defaultValue: Int) = data[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = data[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }
}
