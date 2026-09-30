package com.halo.providers.litert.runtime

import android.os.Build
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.halo.sdk.provider.LocalComputeBackend
import kotlinx.coroutines.CancellationException
import java.io.File

/** Per-accelerator cache directories for one artifact, separated so a failed GPU cannot poison CPU. */
data class LiteRtCacheDirs(
    val root: File,
    val gpu: File,
    val cpu: File,
)

/**
 * MINING-P0-001 the single owner of LiteRT engine construction rules.
 *
 * `LiteRtRuntime` and the explicit `LiteRtControlProbeRunner` must not slowly diverge into
 * different backend mapping, context ladders, cache-path rules or ABI gates. Both consume this
 * factory, so a raw-SDK probe failure and a provider-path failure are comparable.
 *
 * Safety rules live here too: the probe is "direct" only in the sense that it bypasses Halo's
 * inference *orchestration*. It never bypasses ABI preflight or artifact verification.
 */
object LiteRtEngineConfigFactory {

    const val GPU_CACHE_SUBDIR = "gpu"
    const val CPU_CACHE_SUBDIR = "cpu"
    const val MINIMUM_CONTEXT_TOKENS = 1024
    const val SUPPORTED_ABI = "arm64-v8a"

    /** Bounded ladder used only for memory/resource allocation failures under AUTO. */
    private val CONTEXT_LADDER = listOf(2048, 1024)

    private const val CACHE_ROOT_DIR = "litertlm_cache"

    /**
     * LiteRT Android local inference only ships arm64-v8a native libraries. Entering
     * `Engine.initialize()` on an unsupported ABI produces an opaque native abort instead of a
     * diagnosable failure, so it is rejected before any allocation.
     */
    fun abiBlocker(): String? {
        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        return if (abis.any { it.equals(SUPPORTED_ABI, ignoreCase = true) }) {
            null
        } else {
            "This device does not support arm64-v8a native inference (SUPPORTED_ABIS=${abis.joinToString(",")})"
        }
    }

    fun backendName(backend: LocalComputeBackend): String = when (backend) {
        LocalComputeBackend.GPU -> "GPU"
        LocalComputeBackend.CPU -> "CPU"
        LocalComputeBackend.AUTO -> "AUTO"
    }

    fun backendFor(backend: LocalComputeBackend): Backend = when (backend) {
        LocalComputeBackend.GPU -> Backend.GPU()
        LocalComputeBackend.CPU -> Backend.CPU()
        LocalComputeBackend.AUTO -> Backend.GPU()
    }

    /**
     * The exact `EngineConfig` Halo has always built. Extracted so the probe and the production
     * runtime share one set of rules; do not fork this.
     */
    fun buildEngineConfig(
        modelPath: String,
        backend: LocalComputeBackend,
        contextTokens: Int?,
        cacheDir: File,
        visionEnabled: Boolean = false,
    ): EngineConfig = EngineConfig(
        modelPath = modelPath,
        backend = backendFor(backend),
        visionBackend = if (visionEnabled) Backend.CPU() else null,
        maxNumImages = if (visionEnabled) 1 else null,
        // null => the native engine applies the model's own default context window.
        maxNumTokens = contextTokens,
        // Inference compile/weight caches live under filesDir: the OS may purge cacheDir.
        cacheDir = cacheDir.absolutePath,
    )

    /** Content-addressed cache root for one artifact, with separate GPU/CPU subdirectories. */
    fun cacheDirsFor(filesDir: File, modelId: String, artifactFingerprint: String): LiteRtCacheDirs {
        val root = File(filesDir, "$CACHE_ROOT_DIR/${modelId.replace('/', '_')}-$artifactFingerprint")
        val gpu = File(root, GPU_CACHE_SUBDIR).also { if (!it.exists()) it.mkdirs() }
        val cpu = File(root, CPU_CACHE_SUBDIR).also { if (!it.exists()) it.mkdirs() }
        return LiteRtCacheDirs(root, gpu, cpu)
    }

    /**
     * Bounded context ladder. Only an AUTO policy may downshift, and only through the declared
     * 4096 -> 2048 -> 1024 steps. Explicit GPU/CPU never silently change the allocation.
     */
    fun contextLadder(requestedContext: Int?, backend: LocalComputeBackend): List<Int?> {
        if (requestedContext == null) return listOf(null)
        if (backend != LocalComputeBackend.AUTO) return listOf(requestedContext)
        val candidates = mutableListOf(requestedContext)
        CONTEXT_LADDER.forEach { candidate ->
            if (candidate < requestedContext && candidate >= MINIMUM_CONTEXT_TOKENS) candidates.add(candidate)
        }
        return candidates.distinct()
    }

    /**
     * Creates and initializes one engine. On failure the partially created engine is closed here so
     * callers never have to reason about a leaked native allocation.
     */
    fun createAndInitialize(
        modelPath: String,
        backend: LocalComputeBackend,
        contextTokens: Int?,
        cacheDir: File,
        visionEnabled: Boolean = false,
    ): Result<Engine> {
        var created: Engine? = null
        return try {
            created = Engine(buildEngineConfig(modelPath, backend, contextTokens, cacheDir, visionEnabled))
            created.initialize()
            Result.success(created)
        } catch (cancelled: CancellationException) {
            created?.let { runCatching { it.close() } }
            throw cancelled
        } catch (error: Throwable) {
            created?.let { runCatching { it.close() } }
            Result.failure(error)
        }
    }

    fun bounded(error: Throwable?): String = error?.let {
        "${it::class.java.simpleName}: ${it.message?.lineSequence()?.firstOrNull()?.take(300)}"
    } ?: "unknown"

    /**
     * True only for failures that a smaller context allocation could plausibly fix.
     *
     * A corrupt artifact, an unsupported op, an ABI mismatch or an invalid-metadata error must
     * never trigger a context downshift: retrying would just hide a real defect.
     */
    fun isMemoryAllocationFailure(error: Throwable?): Boolean {
        var current = error
        var depth = 0
        while (current != null && depth < 4) {
            if (current is OutOfMemoryError) return true
            val message = current.message?.lowercase().orEmpty()
            if (MEMORY_MARKERS.any { message.contains(it) }) return true
            current = current.cause
            depth++
        }
        return false
    }

    private val MEMORY_MARKERS = listOf(
        "out of memory",
        "oom",
        "memory",
        "alloc",
        "insufficient",
        "resource exhausted",
    )
}
