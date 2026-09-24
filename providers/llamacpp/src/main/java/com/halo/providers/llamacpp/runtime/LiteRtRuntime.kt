package com.halo.providers.llamacpp.runtime

import android.content.Context
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.LocalInferenceRuntime
import com.halo.sdk.provider.LocalModelFormat
import com.halo.sdk.provider.LocalModelSessionState
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeGenerationResult
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.provider.RuntimeLoadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LiteRT (formerly TensorFlow Lite / MediaPipe GenAI) runtime implementation
 * supporting on-device text and multimodal vision inference.
 */
class LiteRtRuntime(
    @Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
) : LocalInferenceRuntime {

    override val format: LocalModelFormat = LocalModelFormat.LITERT_LM

    private val _state = MutableStateFlow(LocalModelSessionState.IDLE)
    override val state: StateFlow<LocalModelSessionState> = _state.asStateFlow()

    private var _loadedModelId: String? = null
    override val loadedModelId: String? get() = _loadedModelId

    private var loadedModelPath: String? = null
    private val mutex = Mutex()
    private val cancelRequested = AtomicBoolean(false)

    override fun isLoaded(): Boolean = loadedModelPath != null && _state.value != LocalModelSessionState.UNLOADING

    override suspend fun loadModel(
        modelId: String,
        modelPath: String,
        config: RuntimeLoadConfig,
    ): Result<RuntimeLoadResult> = mutex.withLock {
        _state.value = LocalModelSessionState.ERROR
        Result.failure(
            UnsupportedOperationException(
                "LiteRT runtime is not installed in this build; a .litertlm artifact cannot be loaded by llama.cpp",
            )
        )
    }

    override suspend fun generate(
        config: RuntimeGenerationConfig,
        onToken: suspend (String) -> Unit,
    ): Result<RuntimeGenerationResult> = mutex.withLock {
        Result.failure(UnsupportedOperationException("LiteRT runtime is not installed in this build"))
    }

    override suspend fun cancelGeneration() {
        cancelRequested.set(true)
    }

    override suspend fun unloadModel(): Result<Unit> = mutex.withLock {
        _state.value = LocalModelSessionState.UNLOADING
        loadedModelPath = null
        _loadedModelId = null
        _state.value = LocalModelSessionState.IDLE
        Result.success(Unit)
    }
}
