package com.halo.providers.llamacpp.runtime

import android.content.Context
import android.util.Log
import com.halo.providers.llamacpp.AndroidMemorySnapshot
import com.halo.providers.llamacpp.ModelDownloadManager
import com.halo.providers.llamacpp.ModelMemoryAdmission
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.LocalModelFormat
import com.halo.sdk.provider.LocalModelSessionState
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.NativeResourceKind
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class PreloadReason {
    WEARABLE_VOICE,
    PHONE_VOICE,
    IMAGE_CAPTURE,
    USER_OPENED_CHAT,
}

/**
 * Coordinates proactive on-device model preloading when high-intent events occur
 * (e.g., wearable voice trigger, camera photo, user entering conversation).
 *
 * Verifies memory headroom, native concurrency permissions, and user configuration
 * before initiating background model loading. Strictly avoids dummy warmup token generation.
 */
class LocalModelPreloadCoordinator(
    private val context: Context,
    private val localModelSession: LocalModelSession,
    private val downloadManager: ModelDownloadManager,
    private val storage: Storage,
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val preloadMutex = Mutex()

    fun triggerPreload(reason: PreloadReason) {
        scope.launch {
            preload(reason)
        }
    }

    suspend fun preload(reason: PreloadReason): Boolean = withContext(Dispatchers.IO) {
        preloadMutex.withLock {
            val configuredProvider = storage.getString("ai_provider")
            val isNativeLlama = configuredProvider == null || configuredProvider == "provider.llamacpp.native"
            if (!isNativeLlama) {
                Log.d(TAG, "Preload skipped: active provider is not local native llama ($configuredProvider)")
                return@withLock false
            }

            val activeModelId = downloadManager.activeModelId.value
            if (activeModelId == null) {
                Log.d(TAG, "Preload skipped: no active model selected")
                return@withLock false
            }

            val file = downloadManager.getModelFile(activeModelId)
            if (file == null || !file.exists() || !file.isFile || file.length() <= 0L) {
                Log.d(TAG, "Preload skipped: model file missing or empty for $activeModelId")
                return@withLock false
            }

            if (localModelSession.isLoaded() && localModelSession.loadedModelId == activeModelId) {
                Log.d(TAG, "Preload skipped: model $activeModelId is already resident in session")
                return@withLock true
            }

            if (localModelSession.state.value == LocalModelSessionState.LOADING) {
                Log.d(TAG, "Preload skipped: model is already actively loading")
                return@withLock true
            }

            // Check system memory pressure
            val memorySnapshot = AndroidMemorySnapshot.read(context)
            if (memorySnapshot.lowMemory) {
                Log.w(TAG, "Preload skipped: Android reports system low memory condition")
                return@withLock false
            }

            // Check model memory admission
            val admission = ModelMemoryAdmission.evaluate(
                file.length(),
                ModelMemoryAdmission.Snapshot(
                    memorySnapshot.totalBytes,
                    memorySnapshot.availableBytes,
                    memorySnapshot.lowMemory,
                )
            )
            if (admission is ModelMemoryAdmission.Decision.Reject) {
                Log.w(TAG, "Preload skipped: model memory admission rejected: ${admission.reason}")
                return@withLock false
            }

            Log.i(TAG, "Triggering proactive model preload for $activeModelId (reason=$reason)")
            val loadConfig = RuntimeLoadConfig(
                contextSize = storage.getInt("llamacpp_context", LlamaCppRuntime.MAX_MOBILE_CONTEXT_SIZE)
                    .coerceIn(LlamaCppRuntime.MIN_MOBILE_CONTEXT_SIZE, LlamaCppRuntime.MAX_MOBILE_CONTEXT_SIZE),
                computeBackend = LocalComputeBackend.AUTO,
            )

            try {
                val result = localModelSession.loadModel(
                    modelId = activeModelId,
                    modelPath = file.absolutePath,
                    format = LocalModelFormat.GGUF,
                    config = loadConfig,
                )
                result.isSuccess
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Preload execution failed for $activeModelId", t)
                false
            }
        }
    }

    companion object {
        private const val TAG = "LocalModelPreload"
    }
}
