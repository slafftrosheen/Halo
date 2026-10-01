package com.halo.engines.speech

import com.halo.sdk.host.AudioSource
import com.halo.sdk.host.AudioSourceState
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class DiagnosticCaptureTest {
    private class Fixture(val scope: CoroutineScope) {
        val emitted = mutableListOf<HaloEvent>()
        val ready = CompletableDeferred<Unit>()
        var starts = 0; var stops = 0; var resets = 0; var processed = 0
        val bus = object : EventBus {
            override suspend fun publish(event: HaloEvent) { emitted.add(event) }
            override fun events(): Flow<HaloEvent> = emptyFlow()
            override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
        }
        val commands = object : CommandBus {
            override suspend fun dispatch(command: RuntimeCommand) = Unit
            override fun commands(): Flow<RuntimeCommand> = emptyFlow()
        }
        val source = object : AudioSource {
            override val state = MutableStateFlow(AudioSourceState.IDLE)
            override suspend fun start() { starts++; state.value = AudioSourceState.ACTIVE }
            override suspend fun stop() { stops++; state.value = AudioSourceState.IDLE }
            override val frames = flow {
                emit(AudioFrame(ShortArray(9600) { 1234 }, 48000, 2, 0))
                ready.complete(Unit); awaitCancellation()
            }
        }
        val preprocessor = object : AudioPreprocessor {
            val delegate = DefaultAudioPreprocessor()
            override fun process(frame: AudioFrame): AudioFrame { processed++; return delegate.process(frame) }
            override fun reset() { resets++; delegate.reset() }
        }
        val pipeline = AudioPipeline(object : VoiceActivityDetector { override fun isSpeech(frame: AudioFrame) = true },
            object : WakeWordDetector {
                override fun process(frame: AudioFrame) = WakeWordResult(false)
                override suspend fun health() = true
            }, object : BufferedSpeechRecognitionProvider {
                override suspend fun health() = true
                override suspend fun recognize(audio: com.halo.sdk.host.AudioBuffer): RecognitionResult = error("Diagnostic capture must not decode")
            }, bus, scope)
        val engine = AudioPerceptionEngine(source, preprocessor, pipeline, bus, commands)
        suspend fun start() {
            val runtime = object : RuntimeContext {
                override val eventBus = bus
                override val commandBus = commands
                override val queryBus: QueryBus get() = error("unused")
                override val registry: ServiceRegistry get() = error("unused")
                override val scheduler = object : RuntimeScheduler {
                    override val io = Dispatchers.Default; override val compute = Dispatchers.Default
                    override val realtime = Dispatchers.Default; override val ui = Dispatchers.Default
                }
                override val logger = object : RuntimeLogger {
                    override fun trace(category: String, message: String) = Unit
                    override fun debug(category: String, message: String) = Unit
                    override fun info(category: String, message: String) = Unit
                    override fun warn(category: String, message: String) = Unit
                    override fun error(category: String, message: String, throwable: Throwable?) = Unit
                }
            }
            val registry = object : ServiceRegistry {
                override fun <T : Any> register(key: ServiceKey<T>, service: T) = Unit
                override fun <T : Any> resolve(key: ServiceKey<T>): T = error("unused")
            }
            engine.initialize(EngineContext(registry, runtime)); engine.start()
        }
    }
    @Test fun diagnosticsUseSoleOwnerPreprocessAndPublishNothing() = runBlocking {
        val f = Fixture(this); f.start()
        val finish = CompletableDeferred<Unit>()
        val capture = async { f.engine.captureDiagnosticAudio(finish = finish) }
        withTimeout(5000) { f.ready.await() }
        assertFalse(f.engine.startCapture(SpeechInputTurn("normal")))
        try { f.engine.captureDiagnosticAudio(); fail("Expected busy input") }
        catch (expected: IllegalStateException) { assertEquals(0, f.stops) }
        finish.complete(Unit)
        val sample = withTimeout(5000) { capture.await() }
        assertEquals(1, f.starts); assertTrue(f.stops > 0); assertEquals(1, f.processed)
        assertEquals(48000, sample.originalSampleRate)
        assertTrue(sample.audio.frames.all { it.sampleRate == 16000 && it.channels == 1 })
        assertTrue(f.emitted.isEmpty()); assertEquals(2, f.resets)
        sample.close(); f.engine.dispose()
    }
    @Test fun diagnosticCancellationAlwaysStopsMicrophone() = runBlocking {
        val f = Fixture(this); f.start()
        val capture = launch { f.engine.captureDiagnosticAudio() }
        withTimeout(5000) { f.ready.await() }; capture.cancelAndJoin()
        assertTrue(f.stops > 0); assertEquals(AudioSourceState.IDLE, f.source.state.value)
        assertTrue(f.emitted.isEmpty()); f.engine.dispose()
    }
    @Test fun diagnosticDecodeReservationRejectsNormalCapture() = runBlocking {
        val f = Fixture(this); f.start()
        val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val native = launch { f.engine.withDiagnosticRecognitionOwnership { entered.complete(Unit); finish.await() } }
        entered.await(); assertFalse(f.engine.startCapture(SpeechInputTurn("normal"))); assertEquals(0, f.starts)
        native.cancelAndJoin(); f.engine.dispose()
    }
    @Test fun activeNormalCaptureRejectsDiagnosticWithoutStoppingIt() = runBlocking {
        val f = Fixture(this); f.start()
        assertTrue(f.engine.startCapture(SpeechInputTurn("normal")))
        withTimeout(5000) { f.ready.await() }
        try { f.engine.captureDiagnosticAudio(); fail("Expected busy input") }
        catch (expected: IllegalStateException) { assertEquals(0, f.stops); assertEquals(1, f.starts) }
        f.engine.dispose()
    }
}
