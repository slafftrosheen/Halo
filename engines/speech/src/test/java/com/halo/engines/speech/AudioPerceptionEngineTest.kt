package com.halo.engines.speech

import com.halo.runtime.services.Services
import com.halo.sdk.runtime.CommandBus
import com.halo.sdk.runtime.EngineContext
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HaloEvent
import com.halo.sdk.runtime.HaloLogger
import com.halo.sdk.runtime.ObservationCreated
import com.halo.sdk.runtime.Query
import com.halo.sdk.runtime.QueryBus
import com.halo.sdk.runtime.RuntimeCommand
import com.halo.sdk.runtime.RuntimeContext
import com.halo.sdk.runtime.RuntimeScheduler
import com.halo.sdk.runtime.ServiceKey
import com.halo.sdk.runtime.ServiceRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class AudioPerceptionEngineTest {
    @Test
    fun recognitionWorkerPublishesSpeechAndObservation() = runBlocking {
        val source = TestAudioSource()
        val recognizer = RecordingRecognizer()
        val eventBus = RecordingEventBus()
        val engine = engine(source, recognizer)

        engine.initialize(engineContext(eventBus))
        engine.start()
        source.awaitStarted()

        try {
            source.emit(speechFrame(100L))
            source.emit(silenceFrame(200L))

            val segment = withTimeout(1_000) {
                recognizer.segments.receive()
            }
            assertArrayEquals(shortArrayOf(1, 1), segment.pcm)
            awaitCondition {
                eventBus.published.any { it is SpeechRecognized && it.text == "recognized" } &&
                    eventBus.published.any { it is ObservationCreated }
            }
        } finally {
            engine.stop()
        }

        assertEquals(1, source.stopCalls.get())
    }

    @Test
    fun captureContinuesWhileRecognitionIsBlocked() = runBlocking {
        val source = TestAudioSource()
        val recognizer = BlockingRecognizer()
        val eventBus = RecordingEventBus()
        val engine = engine(source, recognizer)

        engine.initialize(engineContext(eventBus))
        engine.start()
        source.awaitStarted()

        try {
            source.emit(speechFrame(100L))
            source.emit(silenceFrame(200L))
            withTimeout(1_000) {
                recognizer.started.receive()
            }

            source.emit(speechFrame(300L))
            source.emit(silenceFrame(400L))
            awaitCondition {
                eventBus.published.count { it is SpeechEnded } == 2
            }

            recognizer.release.send(Unit)
            withTimeout(1_000) {
                recognizer.completed.receive()
            }
            withTimeout(1_000) {
                recognizer.started.receive()
            }
        } finally {
            engine.stop()
        }
    }

    @Test
    fun stopCancelsRecognitionAndStopsCapture() = runBlocking {
        val source = TestAudioSource()
        val recognizer = CancellingRecognizer()
        val eventBus = RecordingEventBus()
        val engine = engine(source, recognizer)

        engine.initialize(engineContext(eventBus))
        engine.start()
        source.awaitStarted()
        source.emit(speechFrame(100L))
        source.emit(silenceFrame(200L))
        withTimeout(1_000) {
            recognizer.started.await()
        }

        engine.stop()

        withTimeout(1_000) {
            recognizer.cancelled.await()
        }
        assertEquals(1, source.stopCalls.get())
    }

    @Test
    fun recognitionTimeoutPublishesEventAndCaptureContinues() = runBlocking {
        val source = TestAudioSource()
        val eventBus = RecordingEventBus()
        val engine = engine(
            source = source,
            recognizer = TimeoutRecognizer(),
            recognitionTimeoutMillis = 20L
        )

        engine.initialize(engineContext(eventBus))
        engine.start()
        source.awaitStarted()

        try {
            source.emit(speechFrame(100L))
            source.emit(silenceFrame(200L))
            awaitCondition {
                eventBus.published.any { it is RecognitionTimeoutEvent }
            }

            source.emit(speechFrame(300L))
            source.emit(silenceFrame(400L))
            awaitCondition {
                eventBus.published.count { it is SpeechEnded } == 2
            }
        } finally {
            engine.stop()
        }
    }

    private fun engine(
        source: AudioSource,
        recognizer: SpeechRecognizer,
        recognitionTimeoutMillis: Long = 30_000L
    ): AudioPerceptionEngine = AudioPerceptionEngine(
        audioSource = source,
        vad = object : VoiceActivityDetector {
            override fun isSpeech(frame: AudioFrame): Boolean =
                frame.samples.any { it != 0.toShort() }
        },
        preprocessor = DefaultAudioPreprocessor(),
        recognizer = recognizer,
        recognitionTimeoutMillis = recognitionTimeoutMillis
    )

    private fun speechFrame(timestamp: Long) = AudioFrame(
        samples = shortArrayOf(1, 1),
        sampleRate = 16_000,
        channels = 1,
        timestamp = timestamp
    )

    private fun silenceFrame(timestamp: Long) = AudioFrame(
        samples = shortArrayOf(0, 0),
        sampleRate = 16_000,
        channels = 1,
        timestamp = timestamp
    )

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(1_000) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    private class TestAudioSource : AudioSource {
        private val frameChannel = Channel<AudioFrame>(Channel.UNLIMITED)
        private val started = CompletableDeferred<Unit>()

        val stopCalls = AtomicInteger()

        override val frames: Flow<AudioFrame> = frameChannel.receiveAsFlow()

        override suspend fun start() {
            started.complete(Unit)
        }

        override suspend fun stop() {
            stopCalls.incrementAndGet()
        }

        suspend fun awaitStarted() {
            started.await()
        }

        suspend fun emit(frame: AudioFrame) {
            frameChannel.send(frame)
        }
    }

    private class RecordingRecognizer : SpeechRecognizer {
        val segments = Channel<SpeechSegment>(Channel.UNLIMITED)

        override suspend fun transcribe(segment: SpeechSegment): SpeechRecognitionResult {
            segments.send(segment)
            return SpeechRecognitionResult(
                text = "recognized",
                language = "en",
                confidence = 0.9f
            )
        }
    }

    private class BlockingRecognizer : SpeechRecognizer {
        val started = Channel<SpeechSegment>(Channel.UNLIMITED)
        val release = Channel<Unit>(Channel.UNLIMITED)
        val completed = Channel<SpeechSegment>(Channel.UNLIMITED)

        override suspend fun transcribe(segment: SpeechSegment): SpeechRecognitionResult {
            started.send(segment)
            release.receive()
            completed.send(segment)
            return SpeechRecognitionResult(
                text = "recognized",
                language = "en",
                confidence = 0.9f
            )
        }
    }

    private class CancellingRecognizer : SpeechRecognizer {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()

        override suspend fun transcribe(segment: SpeechSegment): SpeechRecognitionResult {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
    }

    private class TimeoutRecognizer : SpeechRecognizer {
        override suspend fun transcribe(segment: SpeechSegment): SpeechRecognitionResult {
            awaitCancellation()
        }
    }

    private class RecordingEventBus : EventBus {
        private val events = Collections.synchronizedList(mutableListOf<HaloEvent>())

        val published: List<HaloEvent>
            get() = synchronized(events) { events.toList() }

        override suspend fun publish(event: HaloEvent) {
            events.add(event)
        }

        override fun events(): Flow<HaloEvent> = emptyFlow()

        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
    }

    private fun engineContext(bus: EventBus): EngineContext {
        val serviceRegistry = object : ServiceRegistry {
            override fun <T : Any> register(key: ServiceKey<T>, service: T) = Unit

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> resolve(key: ServiceKey<T>): T {
                check(key == Services.EventBus) { "Unexpected service: ${key.name}" }
                return bus as T
            }
        }
        val runtime = object : RuntimeContext {
            override val eventBus: EventBus = bus
            override val commandBus: CommandBus = object : CommandBus {
                override suspend fun dispatch(command: RuntimeCommand) = Unit
                override fun commands(): Flow<RuntimeCommand> = emptyFlow()
            }
            override val queryBus: QueryBus = object : QueryBus {
                override suspend fun <R> execute(query: Query<R>): R =
                    error("Queries are not used by this test")
            }
            override val scheduler: RuntimeScheduler = object : RuntimeScheduler {
                override val io = Dispatchers.Default
                override val compute = Dispatchers.Default
                override val realtime = Dispatchers.Default
                override val ui = Dispatchers.Default
            }
            override val registry: ServiceRegistry = serviceRegistry
            override val logger: HaloLogger = object : HaloLogger {
                override fun trace(category: String, message: String) = Unit
                override fun debug(category: String, message: String) = Unit
                override fun info(category: String, message: String) = Unit
                override fun warn(category: String, message: String) = Unit
                override fun error(category: String, message: String, throwable: Throwable?) = Unit
            }
        }
        return EngineContext(serviceRegistry, runtime)
    }
}
