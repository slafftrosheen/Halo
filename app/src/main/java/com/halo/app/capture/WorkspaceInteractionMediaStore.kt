package com.halo.app.capture

import com.halo.sdk.runtime.multimodal.*

/** Adapts existing durable resolution to interaction binding; owns no bytes or index. */
class WorkspaceInteractionMediaStore(private val temporary: MediaAssetStore, private val resolver: MediaAssetResolver) : MediaAssetStore by temporary {
    override suspend fun metadata(ref: MediaAssetRef) = resolver.metadata(ref)
    override suspend fun open(ref: MediaAssetRef) = resolver.open(ref)
    override suspend fun transferOwnership(ref: MediaAssetRef, interactionId: String): MediaAssetRef {
        if(ref.lifetime == MediaLifetime.EPHEMERAL_INTERACTION) return temporary.transferOwnership(ref, interactionId)
        if(resolver.metadata(ref) == null) throw MediaAcquisitionException(MediaFailure.SOURCE_UNAVAILABLE)
        return ref.copy(interactionId=interactionId)
    }
}
