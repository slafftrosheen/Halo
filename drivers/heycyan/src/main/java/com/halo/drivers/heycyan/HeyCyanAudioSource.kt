package com.halo.drivers.heycyan

import com.halo.drivers.heycyan.codec.RawOpusAudioDecoder
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.sdk.host.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Passive BC59 source. The host owns physical capture boundaries and arbitration. */
class HeyCyanAudioSource(
    private val driver: RealHeyCyanDriver,
    private val externallyManaged: Boolean = false
) : AudioSource {
    private val decoder = RawOpusAudioDecoder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(AudioSourceState.IDLE)
    override val state: StateFlow<AudioSourceState> = _state.asStateFlow()
    override val frames: Flow<AudioFrame> get() = decoder.frames
    private var observer: Job? = null
    private var startedAt = 0L
    override suspend fun start() {
        if (_state.value == AudioSourceState.ACTIVE) return
        decoder.initialize()
        startedAt = System.currentTimeMillis()
        _state.value = AudioSourceState.ACTIVE
        android.util.Log.i("WEARABLE_AUDIO_START", "source=heycyan inputRate=48000 normalizedRate=16000")
        if (!externallyManaged) observer = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            driver.heyCyanEvents.filterIsInstance<HeyCyanEvent.RawAudioFrame>().collect { accept(it.payload) }
        }
    }
    suspend fun accept(payload: ByteArray) {
        if (_state.value == AudioSourceState.ACTIVE) decoder.decodePacket(payload, System.nanoTime() / 1000)
    }
    suspend fun finish() { decoder.finish() }
    override suspend fun stop() {
        if (_state.value == AudioSourceState.IDLE) return
        observer?.cancelAndJoin(); observer = null
        decoder.release()
        _state.value = AudioSourceState.IDLE
        val stats = decoder.stats.value
        android.util.Log.i("WEARABLE_AUDIO_STATS", "durationMs=${System.currentTimeMillis() - startedAt} packets=${stats.packets} frames=${stats.decodedFrames} drops=${stats.droppedFrames} errors=${stats.decodeErrors} inputRate=${stats.sampleRate} channels=${stats.channels} normalizedRate=16000")
    }
    override suspend fun close() { stop(); scope.cancel() }
}
