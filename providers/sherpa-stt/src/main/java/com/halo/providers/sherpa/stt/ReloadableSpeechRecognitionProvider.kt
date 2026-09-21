package com.halo.providers.sherpa.stt

import com.halo.sdk.provider.UnavailableSpeechRecognitionProvider
import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.provider.BufferedSpeechRecognitionProvider
import com.halo.sdk.provider.ProviderHealth
import com.halo.sdk.provider.ProviderMetadata
import com.halo.sdk.provider.RecognitionResult
import com.halo.sdk.provider.SpeechRecognitionMode
import com.halo.sdk.provider.SpeechRecognitionProvider
import com.halo.sdk.runtime.NativeResourceCoordinator
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe speech recognition provider wrapper that dynamically delegates
 * to [SherpaOfflineSpeechRecognitionProvider] when Whisper Tiny is installed, or
 * [UnavailableSpeechRecognitionProvider] when missing.
 *
 * Calling [refresh] re-evaluates the delegate on-the-fly without requiring
 * runtime or application restarts.
 */
class ReloadableSpeechRecognitionProvider(
    private val modelDirectory: File,
    private val getLanguage: () -> String = { "" },
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null
) : BufferedSpeechRecognitionProvider {

    private val lifecycle = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val currentDelegate = AtomicReference<BufferedSpeechRecognitionProvider>()
    private val _availability = MutableStateFlow(false)
    val availability: StateFlow<Boolean> = _availability.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val newDelegate: BufferedSpeechRecognitionProvider = if (SherpaOfflineSpeechRecognitionProvider.isInstalled(modelDirectory)) {
            SherpaOfflineSpeechRecognitionProvider(
                modelDirectory = modelDirectory,
                language = getLanguage(),
                nativeResourceCoordinator = nativeResourceCoordinator
            )
        } else {
            UnavailableSpeechRecognitionProvider(
                "Install the local Whisper tiny model in Settings > Voice to enable private voice input"
            )
        }
        val old = currentDelegate.getAndSet(newDelegate)
        _availability.value = newDelegate !is UnavailableSpeechRecognitionProvider
        if (old != null && old != newDelegate) {
            cleanupScope.launch {
                lifecycle.withLock { old.close() }
            }
        }
    }

    val delegate: BufferedSpeechRecognitionProvider
        get() = currentDelegate.get()

    override val metadata: ProviderMetadata
        get() = currentDelegate.get().metadata

    override val mode: SpeechRecognitionMode
        get() = currentDelegate.get().mode

    override suspend fun health(): Boolean =
        currentDelegate.get().health()

    override suspend fun healthDetails(): ProviderHealth =
        currentDelegate.get().healthDetails()

    override suspend fun recognize(audio: AudioBuffer): RecognitionResult =
        lifecycle.withLock { currentDelegate.get().recognize(audio) }

    override suspend fun close() {
        lifecycle.withLock { currentDelegate.get()?.close() }
        cleanupScope.coroutineContext[Job]?.children?.toList()?.joinAll()
        cleanupScope.cancel()
    }
}
