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

data class RecognitionReadiness(val modelId: String? = null, val modelName: String = "No active model",
    val language: String = "auto", val phase: String = "Loading", val message: String? = null)

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
    val externalProviders: List<BufferedSpeechRecognitionProvider> = emptyList(),
    private val allowExternal: () -> Boolean = { false },
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
    private val _readiness = MutableStateFlow(RecognitionReadiness())
    val readiness: StateFlow<RecognitionReadiness> = _readiness.asStateFlow()

    private var languageReady = false
    private data class RecognizerKey(val modelId: String?, val language: String, val config: String)
    @Volatile private var activeKey: RecognizerKey? = null
    private var pendingCleanup: BufferedSpeechRecognitionProvider? = null

    init {
        if (selector == null) refresh()
        else cleanupScope.launch { selector.sttSelection.collect { refreshNow() } }
        languageChanges?.let { flow -> cleanupScope.launch { flow.collect { refreshNow() } } }
    }

    fun refresh(): Job = cleanupScope.launch { refreshNow() }

    private suspend fun refreshNow() = lifecycle.withLock {
        if (!languageReady) {
            try { prepareLanguage(); languageReady = true }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _availability.value = false
                _readiness.value = RecognitionReadiness(phase = "Failed", message = "Could not load speech language settings. Try selecting a language again.")
                return@withLock
            }
        }
        val sttSelectedId = selector?.sttSelection?.value?.id ?: "AUTOMATIC"
        val external = externalProviders.firstOrNull { it.metadata.id == sttSelectedId && allowExternal() }
        val localId = if (!allowExternal() && externalProviders.any { it.metadata.id == sttSelectedId }) "AUTOMATIC" else sttSelectedId
        val spec = SherpaSpeechModelCatalog.resolve(localId) {
            installed(directory(it), it)
        }
        val language = getLanguage().trim().lowercase(java.util.Locale.ROOT).ifBlank { "auto" }
        val key = RecognizerKey(external?.metadata?.id ?: spec?.id, language,
            "cpu:greedy:${spec?.revision}:${spec?.effectiveThreads()}:${spec?.whisperTailPaddings}")
        if (key == activeKey && currentDelegate.get() !is UnavailableSpeechRecognitionProvider && currentDelegate.get().health()) {
            updateReadiness(spec, language, external)
            return@withLock
        }
        _availability.value = false
        _readiness.value = RecognitionReadiness(spec?.id, spec?.displayName ?: "No active model", language, "Loading")
        val old = pendingCleanup ?: currentDelegate.get()
        pendingCleanup = old
        if (activeKey != null) android.util.Log.i("HaloSTT",
            "STT_RECOGNIZER_INVALIDATED reason=${when {
                activeKey?.modelId != spec?.id -> "MODEL_CHANGED"
                activeKey?.language != language -> "LANGUAGE_CHANGED"
                else -> "CONFIG_CHANGED"
            }}")
        // Close before constructing the replacement. The resource coordinator also
        // refuses another heavy owner while asynchronous JNI cleanup remains busy.
        currentDelegate.set(UnavailableSpeechRecognitionProvider("Speech model is loading"))
        try { withContext(NonCancellable) { old.close(); old.awaitQuiescent() } }
        catch (_: Exception) {
            _readiness.value = RecognitionReadiness(spec?.id, spec?.displayName ?: "No active model", language,
                "Failed", "Previous recognizer cleanup failed. Recognition remains unavailable until cleanup succeeds.")
            return@withLock
        }
        pendingCleanup = null
        val newDelegate: BufferedSpeechRecognitionProvider = try { when {
            external != null -> external
            spec == null -> UnavailableSpeechRecognitionProvider("Selected speech model is not installed.")
            !spec.supportsLanguage(language) -> UnavailableSpeechRecognitionProvider("Model does not support $language")
            else -> delegateFactory(directory(spec), spec, language)
        } } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { UnavailableSpeechRecognitionProvider("Speech model initialization failed. Select a supported installed model and try again.") }
        currentDelegate.set(newDelegate)
        activeKey = key
        _availability.value = newDelegate.health()
        updateReadiness(spec, language, external)
    }

    private suspend fun updateReadiness(spec: SherpaSpeechModelCatalog.ModelSpec?, language: String,
        external: BufferedSpeechRecognitionProvider? = null) {
        val provider = currentDelegate.get()
        val healthy = provider.health()
        _availability.value = healthy
        _readiness.value = RecognitionReadiness(external?.metadata?.id ?: spec?.id, external?.metadata?.displayName ?: spec?.displayName ?: "No active model", language,
            when { external != null -> if (healthy) "Ready" else "Missing"; spec == null -> "Missing"; !healthy -> "Failed";
                (provider as? SherpaOfflineSpeechRecognitionProvider)?.isResident == true -> "Ready"; else -> "Installed" },
            provider.healthDetails().message)
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
        lifecycle.withLock { currentDelegate.get().health().also { _availability.value = it } }
    }

    override suspend fun healthDetails(): ProviderHealth = withContext(dispatchers.io) {
        lifecycle.withLock { currentDelegate.get().healthDetails() }
    }

    override suspend fun awaitQuiescent() = lifecycle.withLock { currentDelegate.get().awaitQuiescent() }

    override suspend fun recognize(audio: AudioBuffer): RecognitionResult = withContext(dispatchers.default) {
        lifecycle.withLock { currentDelegate.get().recognize(audio) }
    }

    override suspend fun recognizeInLanguage(audio: AudioBuffer, language: String): RecognitionResult =
        withContext(dispatchers.default) {
            lifecycle.withLock {
                val selected = currentDelegate.get()
                if (!selected.metadata.local) return@withLock selected.recognizeInLanguage(audio, language)
                val key = activeKey ?: error("Choose an installed speech model in Voice settings")
                if (key.language == language && selected.health()) return@withLock selected.recognize(audio)
                val spec = key.modelId?.let(SherpaSpeechModelCatalog::byId)
                    ?: error("Choose an installed speech model in Voice settings")
                require(spec.supportsLanguage(language) && installed(directory(spec), spec)) {
                    "Choose an installed speech model that supports $language"
                }
                val recognizer = delegateFactory(directory(spec), spec, language)
                try {
                    recognizer.recognize(audio)
                } finally {
                    withContext(NonCancellable) { recognizer.close(); recognizer.awaitQuiescent() }
                }
            }
        }

    override fun captureContext() = currentDelegate.get().captureContext()

    /** Memo transcription is explicitly local, even if settings change while decoding the file. */
    suspend fun recognizeLocal(audio: AudioBuffer): RecognitionResult = withContext(dispatchers.default) {
        lifecycle.withLock {
            check(currentDelegate.get().metadata.local) { "Choose an installed local speech model for memo transcription" }
            currentDelegate.get().recognize(audio)
        }
    }

    override suspend fun close() {
        val jobs = cleanupScope.coroutineContext[Job]?.children?.toList().orEmpty()
        cleanupScope.cancel()
        jobs.joinAll()
        lifecycle.withLock {
            pendingCleanup?.let { it.close(); it.awaitQuiescent(); pendingCleanup = null }
            currentDelegate.get().close()
            _availability.value = false
        }
    }
}
