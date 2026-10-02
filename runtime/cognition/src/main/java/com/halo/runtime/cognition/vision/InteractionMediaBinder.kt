package com.halo.runtime.cognition.vision

import com.halo.cognition.language.InteractionId
import com.halo.sdk.runtime.VisualFreshness
import com.halo.sdk.runtime.multimodal.MediaAssetRef
import com.halo.sdk.runtime.multimodal.MediaAssetStore
import com.halo.sdk.runtime.multimodal.MediaFailure
import com.halo.sdk.runtime.multimodal.MediaAcquisitionException
import com.halo.runtime.cognition.context.AssistantContextRepository
import com.halo.runtime.cognition.context.ImageContext

data class BoundInteractionImage(
    val ref: MediaAssetRef,
    val interactionId: InteractionId,
    val freshness: VisualFreshness
)

interface InteractionMediaBinder {
    suspend fun bindImage(
        source: MediaAssetRef,
        interactionId: InteractionId,
        freshness: VisualFreshness,
    ): BoundInteractionImage
}

class DefaultInteractionMediaBinder(
    private val store: MediaAssetStore,
    private val contextRepository: AssistantContextRepository
) : InteractionMediaBinder {

    override suspend fun bindImage(
        source: MediaAssetRef,
        interactionId: InteractionId,
        freshness: VisualFreshness
    ): BoundInteractionImage {
        val metadata = store.metadata(source) 
            ?: throw MediaAcquisitionException(MediaFailure.SOURCE_UNAVAILABLE)
        
        if (!metadata.ref.mimeType.startsWith("image/")) {
            throw MediaAcquisitionException(MediaFailure.UNSUPPORTED_FORMAT)
        }

        val ref = store.transferOwnership(source, interactionId.value)
        
        contextRepository.putImage(interactionId.value, ImageContext(
            mediaRef = ref,
            mimeType = ref.mimeType,
            timestampMs = ref.createdAtMs,
            assetId = ref.id,
            interactionId = interactionId.value,
            freshness = freshness
        ))
        
        return BoundInteractionImage(
            ref = ref,
            interactionId = interactionId,
            freshness = freshness
        )
    }
}
