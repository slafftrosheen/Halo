package com.halo.providers.llamacpp

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.util.Log
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.nehuatl.llamacpp.LlamaHelper
import java.util.UUID

class NativeLlamaProvider(
    private val context: Context,
    private val downloadManager: ModelDownloadManager,
    private val storage: Storage
) : AiInferenceProvider {
    private var engine: LlamaHelper? = null
    private var engineEvents: MutableSharedFlow<LlamaHelper.LLMEvent>? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var loadedModelId: String? = null
    private val modelLoadMutex = Mutex()
    private val inferenceMutex = Mutex()

    private val _status = MutableStateFlow(ProviderStatus.UNAVAILABLE)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    private val systemPrompt: String get() = storage.getString("ai_system_prompt") ?: "You are Halo, a helpful AI."
    private val contextSize: Int get() = storage.getInt("llamacpp_context", 4096)

    override val id: String = "provider.llamacpp.native"
    override val displayName: String = "llama.cpp (native)"
    override val isLocal: Boolean = true

    override suspend fun getCapabilities(): Set<AiCapability> = setOf(AiCapability.TEXT, AiCapability.STREAMING)

    override suspend fun getStatus(): ProviderStatus {
        val activeId = downloadManager.activeModelId.value
        val current = when {
            activeId == null -> ProviderStatus.UNCONFIGURED
            engine != null && loadedModelId == activeId -> ProviderStatus.READY
            downloadManager.getModelFile(activeId) != null ->
                ProviderStatus(ProviderStatus.State.READY, "Model downloaded; ready to load: $activeId")
            else -> ProviderStatus(ProviderStatus.State.UNAVAILABLE, "Model not downloaded: $activeId")
        }
        _status.value = current
        return current
    }

    override suspend fun testConnection(): Boolean = try {
        loadModelIfNeeded()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "testConnection error", e)
        false
    }

    suspend fun loadModelIfNeeded(): Boolean = modelLoadMutex.withLock {
        val activeId = downloadManager.activeModelId.value
        if (activeId == null) {
            _status.value = ProviderStatus.UNCONFIGURED
            return@withLock false
        }
        if (loadedModelId == activeId && engine != null) {
            _status.value = ProviderStatus.READY
            return@withLock true
        }

        val file = downloadManager.getModelFile(activeId)
        if (file == null || !file.exists() || !file.isFile || file.length() <= 0L) {
            _status.value = ProviderStatus(ProviderStatus.State.UNAVAILABLE, "Model file not found: $activeId")
            return@withLock false
        }

        val modelBytes = file.length()
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        activityManager?.getMemoryInfo(memoryInfo)
        val memorySnapshot = ModelMemoryAdmission.Snapshot(
            totalBytes = memoryInfo.totalMem,
            availableBytes = memoryInfo.availMem,
            lowMemory = memoryInfo.lowMemory
        )
        val admission = ModelMemoryAdmission.evaluate(modelBytes, memorySnapshot)

        if (admission is ModelMemoryAdmission.Decision.Reject) {
            val modelMb = modelBytes / BYTES_PER_MB
            val totalMb = memorySnapshot.totalBytes / BYTES_PER_MB
            val availableMb = memorySnapshot.availableBytes / BYTES_PER_MB
            val message = when (admission.reason) {
                ModelMemoryAdmission.Reason.SYSTEM_LOW_MEMORY ->
                    "Native model load blocked: Android reports low memory (${availableMb}MB available). Close other apps and retry."
                ModelMemoryAdmission.Reason.TOTAL_MEMORY_LIMIT -> {
                    val totalFractionLimit = (memorySnapshot.totalBytes / 20L) * 11L +
                        (memorySnapshot.totalBytes % 20L) * 11L / 20L
                    val maxMb = minOf(
                        totalFractionLimit,
                        (memorySnapshot.totalBytes - ModelMemoryAdmission.RESERVED_MEMORY_BYTES).coerceAtLeast(0L)
                    ) / BYTES_PER_MB
                    "Model $activeId is ${modelMb}MB but this device has ${totalMb}MB RAM; " +
                        "native loading is limited to ${maxMb}MB to avoid process kills. Select a smaller GGUF model."
                }
                ModelMemoryAdmission.Reason.AVAILABLE_MEMORY_LIMIT ->
                    "Native model load blocked: $modelMb MB model needs at least " +
                        "${ModelMemoryAdmission.RESERVED_MEMORY_BYTES / BYTES_PER_MB}MB additional RAM headroom, " +
                        "but only ${availableMb}MB is currently available. Close other apps and retry."
                ModelMemoryAdmission.Reason.INVALID_MEMORY_SNAPSHOT ->
                    "Native model load blocked: Android returned invalid memory information."
            }
            Log.e(TAG, message)
            _status.value = ProviderStatus(ProviderStatus.State.ERROR, message)
            return@withLock false
        }

        _status.value = ProviderStatus.State.LOADING.let { ProviderStatus(it, "Loading $activeId...") }
        releaseCurrentModel()
        return@withLock try {
            val modelUri = Uri.fromFile(file).toString()
            Log.i(TAG, "Loading GGUF model: uri=$modelUri scheme=${Uri.parse(modelUri).scheme} (${file.length()} bytes)")

            val events = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 64)
            val loadResult = CompletableDeferred<String?>()
            val loadCollector = scope.launch {
                events.collect { event ->
                    when (event) {
                        is LlamaHelper.LLMEvent.Error -> {
                            Log.e(TAG, "Native llama.cpp model load error: ${event.message}")
                            loadResult.complete(event.message ?: "Native llama.cpp model initialization failed")
                        }
                        is LlamaHelper.LLMEvent.Loaded -> {
                            Log.i(TAG, "Native llama.cpp model load event received: ${event.path}")
                        }
                        else -> Unit
                    }
                }
            }

            var helper: LlamaHelper? = null
            var installed = false
            try {
                helper = LlamaHelper(context.contentResolver, scope, events)
                helper.load(modelUri, contextSize, null) { contextId ->
                    Log.i(TAG, "Native llama.cpp model ready contextId=$contextId model=$activeId")
                    loadResult.complete(null)
                }

                val loadOutcome = withTimeoutOrNull(MODEL_LOAD_TIMEOUT_MS) { loadResult.await() }
                if (loadOutcome == null && !loadResult.isCompleted) {
                    Log.e(TAG, "Native llama.cpp model load timed out after ${MODEL_LOAD_TIMEOUT_MS}ms model=$activeId")
                    _status.value = ProviderStatus(
                        ProviderStatus.State.ERROR,
                        "Native llama.cpp model load timed out after ${MODEL_LOAD_TIMEOUT_MS / 1000}s"
                    )
                    false
                } else if (loadOutcome != null) {
                    _status.value = ProviderStatus(
                        ProviderStatus.State.ERROR,
                        "Native llama.cpp model initialization failed: $loadOutcome"
                    )
                    false
                } else if (downloadManager.activeModelId.value != activeId) {
                    Log.w(TAG, "Discarding stale llama.cpp engine: requested=$activeId active=${downloadManager.activeModelId.value}")
                    _status.value = ProviderStatus(
                        ProviderStatus.State.UNAVAILABLE,
                        "Model selection changed while loading"
                    )
                    false
                } else {
                    engine = helper
                    engineEvents = events
                    loadedModelId = activeId
                    installed = true
                    _status.value = ProviderStatus.READY
                    true
                }
            } finally {
                loadCollector.cancel()
                if (!installed) {
                    runCatching { helper?.release() }
                }
            }
        } catch (error: CancellationException) {
            releaseCurrentModel()
            _status.value = ProviderStatus(ProviderStatus.State.UNAVAILABLE, "Model loading cancelled")
            throw error
        } catch (error: Throwable) {
            releaseCurrentModel()
            Log.e(TAG, "Failed loading model ${file.absolutePath}", error)
            _status.value = ProviderStatus(ProviderStatus.State.ERROR, "Model load failed: ${error.message ?: error::class.simpleName}")
            false
        }
    }

    private fun releaseCurrentModel() {
        runCatching { engine?.release() }
        engine = null
        engineEvents = null
        loadedModelId = null
    }

    suspend fun health(): Boolean =
        downloadManager.activeModelId.value?.let { downloadManager.getModelFile(it) }?.let { it.exists() && it.length() > 0 } == true

    private fun buildPrompt(request: AiRequest, context: AiContext): String {
        val history = context.conversation.joinToString("\n") { "${it.role.name.lowercase()}: ${it.content}" }
        return buildString {
            append("<|im_start|>system\n")
            append(systemPrompt)
            append("<|im_end|>\n")
            if (history.isNotBlank()) append(history).append("\n")
            append("<|im_start|>user\n")
            append(request.text!!.trim())
            append("<|im_end|>\n<|im_start|>assistant\n")
        }
    }

    private suspend fun requireSelectedModel(request: AiRequest): String {
        val activeModelId = downloadManager.activeModelId.value
            ?: throw AiProviderException(AiErrorType.MODEL_NOT_SELECTED, "No local llama.cpp model is selected")
        if (request.modelId != null && request.modelId != activeModelId) {
            throw AiProviderException(
                AiErrorType.MODEL_NOT_FOUND,
                "Selected llama.cpp model '${request.modelId}' is not the active local model '$activeModelId'"
            )
        }
        return activeModelId
    }

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
        request.validateForTextGeneration()
        val modelId = requireSelectedModel(request)
        if (!loadModelIfNeeded()) {
            throw AiProviderException(AiErrorType.MODEL_LOAD_FAILED, status.value.message ?: "Unable to load local model")
        }
        val startedAt = System.currentTimeMillis()
        val prompt = buildPrompt(request, context)
        return inferenceMutex.withLock {
            val transactionId = UUID.randomUUID().toString().take(8)
            try {
                Log.i(TAG, "[$transactionId] generate start model=$modelId promptChars=${prompt.length} contextSize=$contextSize")
                val text = buildString { streamInternal(prompt, transactionId).collect { append(it) } }.trim()
                if (text.isBlank()) throw AiProviderException(AiErrorType.EMPTY_RESPONSE, "llama.cpp returned an empty response")
                Log.i(TAG, "[$transactionId] generate complete outputChars=${text.length} durationMs=${System.currentTimeMillis() - startedAt}")
