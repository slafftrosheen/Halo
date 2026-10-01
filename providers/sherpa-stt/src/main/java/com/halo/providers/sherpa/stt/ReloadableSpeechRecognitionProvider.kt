package com.halo.providers.sherpa.stt

import com.halo.sdk.provider.UnavailableSpeechRecognitionProvider
import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.provider.BufferedSpeechRecognitionProvider
import com.halo.sdk.provider.ProviderHealth
import com.halo.sdk.provider.ProviderMetadata
import com.halo.sdk.provider.RecognitionResult
import com.halo.sdk.provider.SpeechProviderSelector
import com.halo.sdk.provider.SpeechRecognitionMode
import com.halo.sdk.provider.SpeechRecognitionProvider
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.HaloDispatchers
import com.halo.sdk.runtime.productionHaloDispatchers
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect

/**
 * Thread-safe speech recognition provider wrapper that dynamically delegates
 * to the selected catalog recognizer when its verified model is installed, or
 * [UnavailableSpeechRecognitionProvider] when missing.
 *
 * Calling [refresh] re-evaluates the delegate on-the-fly without requiring
 * runtime or application restarts.
 */
class ReloadableSpeechRecognitionProvider(
    private val modelDirectory: File,
    private val getLanguage: () -> String = { "en" },
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null,
    private val dispatchers: HaloDispatchers = productionHaloDispatchers(),
    private val selector: SpeechProviderSelector? = null,
    private val languageChanges: StateFlow<String>? = null,
    private val prepareLanguage: suspend () -> Unit = {},
    private val installed: (File, SherpaSpeechModelCatalog.ModelSpec) -> Boolean = SherpaSpeechModelCatalog::isInstalled,
    private val delegateFactory: (File, SherpaSpeechModelCatalog.ModelSpec, String) -> BufferedSpeechRecognitionProvider = { directory, spec, language ->
        SherpaSpeechRecognizerFactory.create(directory, spec, language, nativeResourceCoordinator)
    }
) : BufferedSpeechRecognitionProvider {

    private val lifecycle = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val currentDelegate = AtomicReference<BufferedSpeechRecognitionProvider>(
        UnavailableSpeechRecognitionProvider("Checking local speech model availability")
    )
    private val _availability = MutableStateFlow(false)
    val availability: StateFlow<Boolean> = _availability.asStateFlow()

    private val languageReady = cleanupScope.async { prepareLanguage() }
    private data class RecognizerKey(val modelId: String?, val language: String, val config: String)
    @Volatile private var activeKey: RecognizerKey? = null

    init {
        if (selector == null) refresh()
        else cleanupScope.launch { selector.sttSelection.collect { refreshNow() } }
        languageChanges?.let { flow -> cleanupScope.launch { flow.collect { refreshNow() } } }
    }

    fun refresh(): Job = cleanupScope.launch { refreshNow() }

    private suspend fun refreshNow() = lifecycle.withLock {
        languageReady.await()
        val sttSelectedId = selector?.sttSelection?.value?.id ?: "AUTOMATIC"
        val spec = SherpaSpeechModelCatalog.resolve(sttSelectedId) {
            installed(directory(it), it)
        }
        val language = getLanguage()
        val key = RecognizerKey(spec?.id, language,
            "cpu:greedy:${spec?.revision}:${spec?.effectiveThreads()}:${spec?.whisperTailPaddings}")
        if (key == activeKey && currentDelegate.get() !is UnavailableSpeechRecognitionProvider) return@withLock
        val old = currentDelegate.get()
        if (activeKey != null) android.util.Log.i("HaloSTT",
            "STT_RECOGNIZER_INVALIDATED reason=${when {
                activeKey?.modelId != spec?.id -> "MODEL_CHANGED"
                activeKey?.language != language -> "LANGUAGE_CHANGED"
                else -> "CONFIG_CHANGED"
            }}")
        // Close before constructing the replacement. The resource coordinator also
        // refuses another heavy owner while asynchronous JNI cleanup remains busy.
        withContext(NonCancellable) { old.close(); old.awaitQuiescent() }
        val newDelegate: BufferedSpeechRecognitionProvider = when {
            spec == null -> UnavailableSpeechRecognitionProvider("Selected speech model is not installed.")
            !spec.supportsLanguage(language) -> UnavailableSpeechRecognitionProvider("Model does not support $language")
            else -> delegateFactory(directory(spec), spec, language)
        }
        currentDelegate.set(newDelegate)
        activeKey = key
        _availability.value = newDelegate !is UnavailableSpeechRecognitionProvider
    }

    private fun directory(spec: SherpaSpeechModelCatalog.ModelSpec): File =
        if (spec.id == SherpaSpeechModelCatalog.WHISPER_TINY_ID) modelDirectory
        else File(modelDirectory.parentFile, spec.directoryName)
    val selectedProviderId: String get() = selector?.sttSelection?.value?.id ?: "AUTOMATIC"
    fun selectModel(providerId: String) { selector?.selectStt(providerId) }
    val residentModelId: String? get() =
        (delegate as? SherpaOfflineSpeechRecognitionProvider)?.takeIf { it.isResident }?.let { activeKey?.modelId }

    val delegate: BufferedSpeechRecognitionProvider
        get() = currentDelegate.get()

    override val metadata: ProviderMetadata
        get() = currentDelegate.get().metadata

    override val mode: SpeechRecognitionMode
        get() = currentDelegate.get().mode

    override suspend fun health(): Boolean = withContext(dispatchers.io) {
        currentDelegate.get().health()
    }

    override suspend fun healthDetails(): ProviderHealth = withContext(dispatchers.io) {
        currentDelegate.get().healthDetails()
    }

    override suspend fun awaitQuiescent() = lifecycle.withLock { currentDelegate.get().awaitQuiescent() }

    override suspend fun recognize(audio: AudioBuffer): RecognitionResult = withContext(dispatchers.default) {
        lifecycle.withLock { currentDelegate.get().recognize(audio) }
    }

    override suspend fun close() {
        val jobs = cleanupScope.coroutineContext[Job]?.children?.toList().orEmpty()
        cleanupScope.cancel()
        jobs.joinAll()
        lifecycle.withLock { currentDelegate.get().close() }
    }
}
