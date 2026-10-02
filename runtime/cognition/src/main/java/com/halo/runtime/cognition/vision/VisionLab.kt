package com.halo.runtime.cognition.vision

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.multimodal.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

/** Metrics contain no source bytes, base64, private text or full model responses. */
enum class VisionStage { CAPABILITY, POLICY, ACQUIRE, PREPARE, SERIALIZE, REQUEST, HTTP, RESPONSE_PARSE, COMPLETE }
data class VisionCandidate(val provider: AiInferenceProvider, val model: AiModel)
data class VisionLabRow(val providerId: String, val modelId: String, val role: AiModelRole = AiModelRole.VISION,
    val evidence: CapabilityEvidence, val local: Boolean, val sourceBytes: Int = 0, val preparedBytes: Int = 0,
    val width: Int = 0, val height: Int = 0, val mimeType: String = "", val preparationMs: Long = 0,
    val requestMs: Long = 0, val totalMs: Long = 0, val responseLength: Int = 0,
    val stage: VisionStage, val failure: String? = null)

/** Explicit diagnostic lane. No conversation, memory, tools, system instruction or provider fallback. */
class VisionLab(private val media: MediaAssetResolver, private val preparer: ImageInputPreparer,
    private val objectPolicy: MediaObjectPolicyResolver, private val storage: com.halo.sdk.runtime.Storage,
    private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun compare(ref: MediaAssetRef, candidates: List<VisionCandidate>, policy: AiContextPolicy,
        prompt: String, probe: Boolean = false, expectedProbeTokens: List<String> = emptyList()): List<VisionLabRow> {
        require(candidates.size in 1..8 && prompt.isNotBlank() && prompt.length <= 1000)
        var prepared: PreparedImageInput? = null
        var preparationMs = 0L
        val rows = mutableListOf<VisionLabRow>()
        for (candidate in candidates) {
            val model = candidate.model
            val provider = candidate.provider
            val started = clock()
            val local = model.executionLocation == AiExecutionLocation.ON_DEVICE
            var stage = VisionStage.CAPABILITY
            var requestMs = 0L
            var responseLength = 0
            var failure: String? = null
            try {
                require(model.providerId == provider.id && model.id != "openrouter/free" && AiCapability.IMAGE in model.inputCapabilities) { "Concrete image model required" }
                require(probe || model.hasVerifiedImageInput() || (!local && storage.getString(probeKey(provider.id, model.id))?.toLongOrNull() != null)) { "Image capability is unverified" }
                stage = VisionStage.POLICY
                require(policy.includeImages && (local || policy.allowCloudImages) &&
                    (local || ref.provenance != MediaProvenance.SCREEN_CAPTURE || policy.allowCloudScreenContext) && objectPolicy.permits(ref, local)) { "Image sharing blocked by privacy policy" }
                if (prepared == null) {
                    stage = VisionStage.ACQUIRE
                    val source = media.open(ref)
                    stage = VisionStage.PREPARE
                    val prepareStarted = clock()
                    prepared = preparer.prepare(source, ref)
                    preparationMs = clock() - prepareStarted
                }
                val input = requireNotNull(prepared)
                stage = VisionStage.SERIALIZE
                val request = AiRequest(text = prompt, modelId = model.id, image = input.content.bytes,
                    mimeTypes = listOf(input.content.mimeType), maxOutputTokens = 256,
                    toolUseMode = ToolUseMode.NONE)
                stage = VisionStage.REQUEST
                val requestStarted = clock()
                val response = withTimeout(60_000) { provider.generate(request, AiContext.EMPTY) }
                requestMs = clock() - requestStarted
                stage = VisionStage.RESPONSE_PARSE
                require(response.text.isNotBlank() && response.toolCalls.isEmpty() && response.providerId == provider.id &&
                    (response.modelId == null || response.modelId == model.id)) { "Invalid image response" }
                responseLength = response.text.length
                if (probe) require(expectedProbeTokens.isNotEmpty() && expectedProbeTokens.all { response.text.contains(it, ignoreCase = true) }) { "Probe did not recognize expected image content" }
                if (probe && !local) storage.putString(probeKey(provider.id, model.id), clock().toString())
                stage = VisionStage.COMPLETE
            } catch (cancelled: CancellationException) {
                if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                failure = "TIMEOUT"
            } catch (error: AiProviderException) {
                stage = if (error.details?.httpStatus != null) VisionStage.HTTP else when (error.type) { AiErrorType.AUTHENTICATION_FAILED, AiErrorType.RATE_LIMITED -> VisionStage.HTTP; else -> stage }
                failure = error.type.name
            } catch (_: Exception) { failure = "${stage.name}_FAILED" }
            val summary = prepared?.summary
            rows += VisionLabRow(provider.id, model.id, evidence = if (probe && stage == VisionStage.COMPLETE) CapabilityEvidence.HALO_PROBE_SUCCESS else model.capabilityEvidence[AiCapability.IMAGE] ?: CapabilityEvidence.UNKNOWN,
                local = local, sourceBytes = summary?.sourceBytes ?: 0, preparedBytes = summary?.outputBytes ?: 0,
                width = summary?.outputWidth ?: 0, height = summary?.outputHeight ?: 0, mimeType = summary?.mimeType.orEmpty(),
                preparationMs = preparationMs, requestMs = requestMs, totalMs = clock() - started,
                responseLength = responseLength, stage = stage, failure = failure)
        }
        return rows.toList()
    }
    companion object { fun probeKey(provider: String, model: String) = "vision_probe_${provider}_${model}" }
}
