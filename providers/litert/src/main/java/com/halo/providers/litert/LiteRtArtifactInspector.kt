package com.halo.providers.litert

import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

data class LiteRtArtifactMetadata(val task: String,val textBackends: List<String>,val vision: Boolean,
    val nativeTools: Boolean,val embeddingDimension: Int?)

/** Explicit inspection pins exact physical bytes without promoting an arbitrary import. */
class LiteRtArtifactInspector(private val resources: NativeResourceCoordinator,
    private val inventory: LocalModelInventoryRepository,
    private val nativeFactory: LiteRtNativeArtifactFactory = SdkLiteRtNativeArtifactFactory) {
    suspend fun inspect(artifact: LocalModelDescriptor): LiteRtArtifactMetadata = withContext(Dispatchers.Default) {
        require(artifact.valid && artifact.sha256?.matches(Regex("[a-f0-9]{64}")) == true)
        val pinned = PinnedLiteRtNativeArtifact.acquire(inventory, artifact.id, requireNotNull(artifact.sha256),
            nativeFactory, requireVerified = false) ?: throw EmbeddingUnavailableException("ARTIFACT_LEASE_UNAVAILABLE")
        val busy = AtomicBoolean(true)
        val cleanup = Mutex()
        val owner = object : NativeResourceOwner {
            override val nativeResourceId = "workload.manual-inspection:${java.util.UUID.randomUUID()}"
            override val nativeResourceKind = NativeResourceKind.HEAVY_LLM
            override val defaultResourcePolicy = WorkloadResourcePolicy(RuntimeWorkloadClass.TEXT_CHAT,
                residencyPreference = ResidencyPreference.ON_DEMAND)
            override suspend fun releaseIfIdle(): Boolean {
                if (busy.get() || !cleanup.tryLock()) return false
                return try { pinned.close(); true }
                catch (_: Exception) { false }
                finally { cleanup.unlock() }
            }
        }
        var primaryFailure: Throwable? = null
        try {
            if (!resources.acquire(owner, diagnostic = true)) throw EmbeddingUnavailableException("NATIVE_RESOURCE_BUSY")
            resources.mark(owner, NativeResourceState.LOADING, "Native metadata initialization")
            currentCoroutineContext().ensureActive()
            val native = pinned.native()
            resources.mark(owner, NativeResourceState.IN_USE, "Native metadata inspection")
            currentCoroutineContext().ensureActive()
            val info = native.inspect()
            LiteRtArtifactMetadata(info.task, info.textBackends, info.vision, info.nativeTools, info.embeddingDimension)
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            busy.set(false)
            withContext(NonCancellable) { cleanup.withLock {
                val closeFailure = try { pinned.close(); null } catch (failure: Throwable) { failure }
                if (closeFailure == null) resources.unregister(owner)
                else {
                    // The coordinator retains this owner (and its file pin) until close succeeds.
                    resources.mark(owner, NativeResourceState.RESIDENT, "Native metadata teardown pending")
                    val primary = primaryFailure
                    if (primary != null) primary.addSuppressed(closeFailure)
                    else throw EmbeddingUnavailableException("NATIVE_TEARDOWN_FAILED", closeFailure)
                }
            } }
        }
    }
}
