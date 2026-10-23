package com.halo.app.ai

import com.halo.app.online.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.math.BigDecimal

class WorkspaceImageUnavailable(message: String) : Exception(message)

data class WorkspaceImageChoice(val providerId: String, val providerName: String, val model: AiImageModel)
data class WorkspaceImageCatalogue(val models: List<WorkspaceImageChoice>, val errors: List<String>)
data class ImageSpendPreview(val challenge: OnlineConsentChallenge, val providerName: String, val modelName: String, val hasReference: Boolean)

class WorkspaceImageService(private val router: AiProviderRouter, private val storage: Storage,
    private val allowImages: () -> Boolean = { true },
    private val allowReference: suspend (String) -> Boolean = { false },
    private val privacyModes: kotlinx.coroutines.flow.StateFlow<PrivacyMode>? = null) {
    private val consent = OneShotConsent()
    private val ledger = OnlineOperationLedger(storage)
    val providers get() = router.getAllProviders().filterIsInstance<AiImageProvider>()
    fun lastChoice(): Pair<String?, String?> = storage.getString("workspace_image_provider") to storage.getString("workspace_image_model")
    suspend fun catalogue(providerId: String? = null): WorkspaceImageCatalogue = withContext(Dispatchers.IO) {
        if (!storage.remoteFeatureEnabled(OnlineFeature.IMAGES)) return@withContext WorkspaceImageCatalogue(emptyList(),
            listOf("This online feature is off in the active mode profile. Review Mode profiles."))
        val choices = mutableListOf<WorkspaceImageChoice>(); val failures = mutableListOf<String>()
        for (provider in providers.filter { providerId == null || it.id == providerId }) {
            try {
                val models = withTimeout(20_000) { provider.listImageModels() }
                choices += models.map { WorkspaceImageChoice(provider.id, provider.displayName, it) }
                if (models.isEmpty()) failures += "${provider.displayName}: configure credentials or refresh model access in Providers."
            } catch (cancelled: kotlinx.coroutines.TimeoutCancellationException) { failures += "${provider.displayName}: model lookup timed out." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failures += "${provider.displayName}: models unavailable. Check its setup in Providers." }
        }
        WorkspaceImageCatalogue(choices, failures)
    }
    fun select(choice: WorkspaceImageChoice) {
        storage.putString("workspace_image_provider", choice.providerId); storage.putString("workspace_image_model", choice.model.id)
    }

    /** Local-only preflight: it neither generates an image nor grants spending permission. */
    suspend fun prepareSpend(choice: WorkspaceImageChoice, prompt: String, reference: ByteArray?, mime: String,
        referenceObjectId: String? = null): ImageSpendPreview = withContext(Dispatchers.IO) {
        val provider = validate(choice, prompt, reference, mime, referenceObjectId)
        ImageSpendPreview(consent.prepare(fingerprint(provider, choice, prompt, reference, mime, referenceObjectId)),
            provider.displayName, choice.model.displayName, reference != null)
    }
    fun discardSpend(preview: ImageSpendPreview?) { consent.discard(preview?.challenge?.id) }

    /** Old/direct callers fail closed: privacy consent and a saved model are not payment consent. */
    suspend fun generate(choice: WorkspaceImageChoice, prompt: String, reference: ByteArray?, mime: String,
        approval: ImageSpendPreview? = null, reservationUsd: BigDecimal? = null,
        acknowledgeUnknownCost: Boolean = false, referenceObjectId: String? = null): AiImageResult = withContext(Dispatchers.IO) {
        if (approval == null || reservationUsd == null || !acknowledgeUnknownCost)
            throw OnlineOperationRejected(OnlineProblem.CONSENT_REQUIRED)
        val bytes = reference?.copyOf()
        val provider = validate(choice, prompt, bytes, mime, referenceObjectId)
        val approvedFingerprint = fingerprint(provider, choice, prompt, bytes, mime, referenceObjectId)
        consent.consume(approval.challenge.id, approvedFingerprint)
        currentCoroutineContext().ensureActive()
        validate(choice, prompt, bytes, mime, referenceObjectId) // Privacy / Kids / local-only are re-read at dispatch.
        val record = ledger.start("IMAGE", provider.id, choice.model.id, reservationUsd)
        var completed = false
        try {
            // Do not send after privacy/credential changes during reservation persistence.
            currentCoroutineContext().ensureActive()
            validate(choice, prompt, bytes, mime, referenceObjectId)
            if (approvedFingerprint != fingerprint(provider, choice, prompt, bytes, mime, referenceObjectId))
                throw OnlineOperationRejected(OnlineProblem.CONSENT_EXPIRED)
            val result = whileCloudMode(privacyModes) { withTimeout(180_000) { provider.generateImage(AiImageRequest(choice.model.id, prompt.trim(), bytes, mime,
                contextCategories = if (bytes != null && referenceObjectId != null) setOf(RemoteDataCategory.WORKSPACE) else emptySet())) } }
            if (result.bytes.size !in 1..32_000_000)
                throw WorkspaceImageUnavailable("This model returned an unsupported image format or size. Choose a raster image model and retry.")
            val bounds = android.graphics.BitmapFactory.Options().also { it.inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size, bounds)
            if (bounds.outWidth !in 1..16384 || bounds.outHeight !in 1..16384)
                throw WorkspaceImageUnavailable("This model returned an unsupported image format or size. Choose a raster image model and retry.")
            // Remembering a preference is best-effort; never discard a paid result on this write.
            runCatching { storage.putBatch(strings = mapOf("workspace_image_provider" to choice.providerId,
                "workspace_image_model" to choice.model.id)) }
            completed = true
            result
        } finally {
            // Existing image adapters do not expose an authoritative billed amount. Never invent one.
            withContext(NonCancellable + Dispatchers.IO) {
                // A receipt update must not discard an image already paid for; the reservation remains.
                runCatching { ledger.finish(record, null, if (completed) "COMPLETED" else "UNCERTAIN") }
            }
        }
    }

    private suspend fun validate(choice: WorkspaceImageChoice, prompt: String, reference: ByteArray?, mime: String,
        referenceObjectId: String?): AiImageProvider {
        if (!allowImages()) throw WorkspaceImageUnavailable("Cloud images are off in Kids mode. Ask a grown-up, or draw in Canvas instead.")
        val policy = storage.readRuntimeAiContextPolicy()
        if (storage.savedPrivacyMode() == PrivacyMode.PRIVATE)
            throw WorkspaceImageUnavailable("This action needs Full Power mode. Use the switch at the top of Halo.")
        if (!storage.remoteFeatureEnabled(OnlineFeature.IMAGES))
            throw WorkspaceImageUnavailable("This online feature is off in the active mode profile. Review Mode profiles.")
        if (reference != null && referenceObjectId != null && !policy.allowCloudWorkspace ||
            storage !is ModeProfileStorage && router.routingPolicy.value == AiRoutingPolicy.LOCAL_ONLY)
            throw WorkspaceImageUnavailable("Enable cloud workspace sharing in Privacy and allow cloud routing before generating images.")
        if (reference != null && !policy.allowCloudImages)
            throw WorkspaceImageUnavailable("Enable cloud image sharing in Privacy before sending a reference image for editing.")
        // Deferred assistant proposals retain their original input authority, including policy revision.
        RemoteDispatchContext.provenance.get()?.let { origin ->
            val dispatch = (storage as? RemoteDispatchPolicySource)?.remoteDispatchPolicy
                ?: throw RemoteDispatchBlocked()
            val categories = origin.categories + RemoteDataCategory.TEXT +
                if (reference != null) setOf(RemoteDataCategory.IMAGES, RemoteDataCategory.WORKSPACE) else emptySet()
            dispatch.acquire(RemoteDispatchRequest(choice.providerId, RemoteOperation.IMAGE, categories),
                origin.revision, recordActivity = false, cancel = {}).use { it.requireCurrent() }
        }
        require(prompt.isNotBlank() && prompt.length <= 4000)
        if (reference == null && choice.model.requiresReference)
            throw WorkspaceImageUnavailable("This model needs a reference image. Open an image in Notebook or Canvas to edit it.")
        if (reference != null && referenceObjectId != null && !allowReference(referenceObjectId))
            throw WorkspaceImageUnavailable("Cloud sharing is not allowed for this image. Use the Private / Full Power switch at the top of Halo.")
        require(reference == null || (reference.size in 1..32_000_000 && mime in setOf("image/png", "image/jpeg", "image/webp")))
        if (reference != null && !choice.model.supportsEditing) throw WorkspaceImageUnavailable("Choose an image model that supports editing.")
        return providers.firstOrNull { it.id == choice.providerId } ?: throw WorkspaceImageUnavailable("Image provider is unavailable")
    }
    private fun fingerprint(provider: AiImageProvider, choice: WorkspaceImageChoice, prompt: String, reference: ByteArray?, mime: String,
        referenceObjectId: String?): String {
        val credential = (provider as? CredentialProvider)?.credential()?.value.orEmpty()
        return OneShotConsent.fingerprint(provider.id.toByteArray(), choice.model.id.toByteArray(), prompt.trim().toByteArray(),
            mime.toByteArray(), reference ?: ByteArray(0), credential.toByteArray(), referenceObjectId.orEmpty().toByteArray())
    }
}
