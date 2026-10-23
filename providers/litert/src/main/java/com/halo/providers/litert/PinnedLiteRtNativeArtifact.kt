package com.halo.providers.litert

import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.LocalModelInventoryRepository
import java.io.Closeable

/** File protection outlives every native handle, including a handle whose close must be retried. */
internal class PinnedLiteRtNativeArtifact private constructor(val artifact: LocalModelDescriptor,
    private val pin: Closeable, private val factory: LiteRtNativeArtifactFactory) : Closeable {
    private var session: LiteRtNativeArtifactSession? = null
    private var closed = false

    fun native(): LiteRtNativeArtifactSession {
        check(!closed)
        return session ?: factory.open(artifact.path).also { session = it }
    }

    override fun close() {
        if (closed) return
        session?.close()
        session = null
        pin.close()
        closed = true
    }

    companion object {
        suspend fun acquire(inventory: LocalModelInventoryRepository, modelId: String, sha: String,
            factory: LiteRtNativeArtifactFactory, requireVerified: Boolean = true): PinnedLiteRtNativeArtifact? {
            val (artifact, pin) = (if (requireVerified) inventory.acquireVerifiedArtifactLease(modelId, sha)
                else inventory.acquirePhysicalArtifactLease(modelId, sha)) ?: return null
            if (!artifact.valid || requireVerified && !artifact.verified || artifact.id != modelId || artifact.sha256 != sha) {
                pin.close()
                return null
            }
            return PinnedLiteRtNativeArtifact(artifact, pin, factory)
        }
    }
}
