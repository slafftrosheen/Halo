package com.halo.app.ai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class WorkspaceImageUnavailable(message: String) : Exception(message)

data class WorkspaceImageChoice(val providerId: String, val providerName: String, val model: AiImageModel)
data class WorkspaceImageCatalogue(val models: List<WorkspaceImageChoice>, val errors: List<String>)
class WorkspaceImageService(private val router: AiProviderRouter, private val storage: Storage) {
    val providers get() = router.getAllProviders().filterIsInstance<AiImageProvider>()
    fun lastChoice(): Pair<String?, String?> = storage.getString("workspace_image_provider") to storage.getString("workspace_image_model")
    suspend fun catalogue(providerId: String? = null): WorkspaceImageCatalogue = withContext(Dispatchers.IO) {
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
    suspend fun generate(choice: WorkspaceImageChoice, prompt: String, reference: ByteArray?, mime: String): AiImageResult {
        val policy = storage.readRuntimeAiContextPolicy()
        if (!policy.allowCloudWorkspace || router.routingPolicy.value == AiRoutingPolicy.LOCAL_ONLY)
            throw WorkspaceImageUnavailable("Enable cloud workspace sharing in Privacy and allow cloud routing before generating images.")
        if (reference != null && !policy.allowCloudImages)
            throw WorkspaceImageUnavailable("Enable cloud image sharing in Privacy before sending a reference image for editing.")
        require(prompt.isNotBlank() && prompt.length <= 4000)
        if (reference != null && !choice.model.supportsEditing) throw WorkspaceImageUnavailable("Choose an image model that supports editing.")
        val provider = providers.firstOrNull { it.id == choice.providerId } ?: error("Image provider is unavailable")
        val result = withTimeout(180_000) { provider.generateImage(AiImageRequest(choice.model.id, prompt.trim(), reference, mime)) }
        val bounds = android.graphics.BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size, bounds) }
        if (result.bytes.size !in 1..32_000_000 || bounds.outWidth !in 1..16384 || bounds.outHeight !in 1..16384)
            throw WorkspaceImageUnavailable("This model returned an unsupported image format or size. Choose a raster image model and retry.")
        storage.putString("workspace_image_provider", choice.providerId); storage.putString("workspace_image_model", choice.model.id)
        return result
    }
}
