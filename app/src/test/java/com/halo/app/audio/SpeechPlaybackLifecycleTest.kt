package com.halo.app.audio

import android.content.Context
import android.media.AudioAttributes
import androidx.test.core.app.ApplicationProvider
import com.halo.app.ai.SpeechSynthesisRegistry
import com.halo.runtime.kernel.resource.DefaultResourceLeaseManager
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class SpeechPlaybackLifecycleTest {
    @Test fun unavailableExactOutputDoesNotSynthesizeOrFallBackAndTestCanBeStopped() = runBlocking {
        val unavailable = Fixture(unavailable = true)
        try {
            unavailable.owner.start()
            unavailable.owner.testOutput(SpeechOutputTarget.Device("sony", 17, "Sony", AudioDeviceKind.HEADSET), "Sony")
            assertTrue(unavailable.owner.outputTest.value.outcome.startsWith("Failed:"))
            assertTrue(unavailable.first.spoken.isEmpty())
            assertEquals(1, unavailable.requests.size)
            assertEquals(SpeechOutputFallback.DISALLOW_PHONE, unavailable.requests.single().fallback)
            unavailable.owner.confirmOutputTest()
            assertFalse(unavailable.owner.outputTest.value.audibleConfirmed)
        } finally { unavailable.close() }
        val playing = Fixture(CompletableDeferred())
        try {
            playing.owner.start()
            playing.owner.testOutput(SpeechOutputTarget.Phone, "Phone")
            playing.owner.stopOutputTest()
            assertEquals("Stopped", playing.owner.outputTest.value.outcome)
            assertEquals(1, playing.terminals().size)
            assertTrue(playing.terminals().single() is TtsCancelled)
        } finally { playing.close() }
    }
    @Test fun oneTimeOutputTestUsesRoutedOwnerAndHumanConfirmationIsSeparate() = runBlocking {
        val f = Fixture(CompletableDeferred(), systemDefault = true)
        try {
            f.owner.start()
            f.owner.testOutput(SpeechOutputTarget.HostDefault, "Android default")
            val id = f.owner.outputTest.value.transactionId!!
            assertNotNull(f.owner.outputTest.value.observed)
            assertFalse(f.owner.outputTest.value.audibleConfirmed)
            f.owner.confirmOutputTest()
            assertFalse(f.owner.outputTest.value.audibleConfirmed)
            f.block!!.complete(Unit)
            assertEquals("Playback completed", f.owner.outputTest.value.outcome)
            f.owner.confirmOutputTest()
            assertTrue(f.owner.outputTest.value.audibleConfirmed)
            f.bus.publish(TtsCancelled(transactionId = id))
            assertEquals("Playback completed", f.owner.outputTest.value.outcome)
            assertEquals("first", f.selector.ttsSelection.value.id)
            assertEquals(SpeechOutputFallback.DISALLOW_PHONE, f.requests.single().fallback)
            assertEquals("en", f.first.lastLanguage)
        } finally { f.close() }
    }
    private class Bus : EventBus {
        val published = mutableListOf<HaloEvent>()
        private val flow = MutableSharedFlow<HaloEvent>(extraBufferCapacity = 64)
        override suspend fun publish(event: HaloEvent) { published += event; flow.emit(event) }
        override fun events() = flow
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = flow.filter { type.isInstance(it) }.map { type.cast(it) }
    }
    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
    private open class Provider(id: String) : PcmSpeechSynthesisProvider {
        override val metadata = ProviderMetadata(id)
        val spoken = mutableListOf<String>()
        var lastLanguage: String? = null
        override suspend fun synthesizeToPcm(request: SpeechRequest): PcmSpeech { lastLanguage = request.language; spoken += request.text; return PcmSpeech(24000, 1, PcmEncoding.SIGNED_16_LE, listOf(ByteArray(4))) }
        override suspend fun synthesize(request: SpeechRequest) = SpeechResult(1, true)
        override fun stream(text: String) = emptyFlow<com.halo.sdk.host.AudioFrame>()
        override suspend fun health() = true
    }
    private class Selector : SpeechProviderSelector {
        override val sttSelection = MutableStateFlow(SpeechProviderSelection("AUTOMATIC"))
        override val ttsSelection = MutableStateFlow(SpeechProviderSelection("first"))
        override fun selectStt(id: String) { sttSelection.value = SpeechProviderSelection(id) }
        override fun selectTts(id: String) { ttsSelection.value = SpeechProviderSelection(id) }
    }
    @Test fun stoppedPinnedResponseCannotStartSynthesisOrFallback() = runBlocking {
        val first = Provider("first")
        val android = Provider("provider.android.tts")
        val registry = SpeechSynthesisRegistry(listOf(first, android), Selector(), MemoryStorage())
        val (pinned, request) = registry.pin(SpeechRequest("Old sentence"))
        registry.stopCurrent()
        try {
            pinned.synthesizeToPcm(request)
            fail("A stopped response must stay cancelled")
        } catch (_: CancellationException) { }
        assertTrue(first.spoken.isEmpty())
        assertTrue(android.spoken.isEmpty())
        val (next, nextRequest) = registry.pin(SpeechRequest("New sentence"))
        assertNotNull(next.synthesizeToPcm(nextRequest))
        assertEquals(listOf("New sentence"), first.spoken)
    }
    @Test fun latePcmFromStoppedProviderIsDiscardedWithoutFallback() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()
        val first = object : Provider("first") {
            override suspend fun synthesizeToPcm(request: SpeechRequest): PcmSpeech {
                entered.complete(Unit)
                complete.await()
                return super.synthesizeToPcm(request)
            }
        }
        val android = Provider("provider.android.tts")
        val registry = SpeechSynthesisRegistry(listOf(first, android), Selector(), MemoryStorage())
        val (pinned, request) = registry.pin(SpeechRequest("Old sentence"))
        val pending = async { pinned.synthesizeToPcm(request) }
        entered.await()
        registry.stopCurrent()
        complete.complete(Unit)
        try { pending.await(); fail("Late PCM must be discarded") } catch (_: CancellationException) { }
        assertTrue(android.spoken.isEmpty())
    }
    private class Fixture(val block: CompletableDeferred<Unit>? = null, val systemDefault: Boolean = false, val unavailable: Boolean = false) {
        val bus = Bus(); val selector = Selector(); val first = Provider("first"); val second = Provider("second")
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).build()
        val requests = mutableListOf<SpeechRouteRequest>()
        val playbackEvidence = mutableListOf<SpeechRouteEvidence>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val storage = MemoryStorage()
        val player = object : SpeechChunkPlayback {
            override suspend fun synthesizeRouted(request: SpeechRequest, evidence: SpeechRouteEvidence, onStarted: (() -> Unit)?, outputTarget: SpeechOutputTarget?, generation: Long, acquireFocus: (() -> Boolean)?, provider: PcmSpeechSynthesisProvider, onRouteVerified: ((SpeechRouteEvidence) -> Unit)?): RoutedPlaybackResult {
                playbackEvidence += evidence
                val actual = if (evidence.mode == com.halo.sdk.runtime.SpeechRouteMode.SYSTEM_DEFAULT) evidence.copy(androidDeviceId = 17, androidDeviceType = 8, mode = com.halo.sdk.runtime.SpeechRouteMode.EXACT_DEVICE) else evidence
                provider.synthesizeToPcm(request); onRouteVerified?.invoke(actual); onStarted?.invoke(); block?.await()
                return RoutedPlaybackResult.Completed(1, actual.androidDeviceId ?: 1, actual)
            }
            override fun stopCurrent() = Unit
        }
        val owner = HaloSpeechOutputCoordinator(ApplicationProvider.getApplicationContext<Context>(), attributes, storage, bus,
            object : SpeechRouteAuthority {
                override suspend fun prepareRoute(request: SpeechRouteRequest): SpeechRoutePreparation {
                    requests += request
                    if (unavailable) return SpeechRoutePreparation.Unavailable(request.target, SpeechRouteFailureReason.AUDIO_TRANSPORT_NOT_CONNECTED)
                    return SpeechRoutePreparation.Ready(request.target, if (systemDefault) SpeechRouteEvidence(false, null, null, null, mode = com.halo.sdk.runtime.SpeechRouteMode.SYSTEM_DEFAULT) else SpeechRouteEvidence(false, 2, "Phone speaker", null, androidDeviceId = 1))
                }
            }, first, SpeechSynthesisRegistry(listOf(first, second, Provider("provider.android.tts")), selector, storage), player,
            resourceLeaseManager = DefaultResourceLeaseManager(), playbackScope = scope, acquireForeground = { {} })
        suspend fun prepare() { owner.start(); bus.publish(StreamingSpeechPrepared("request", "interaction", SpeechOutputTarget.Phone, SpeechOutputFallback.ALLOW_PHONE, "capture")) }
        suspend fun token(text: String) { bus.publish(CognitiveTokenReceived(requestId = "request", token = text, interactionId = "interaction")) }
        suspend fun finish(text: String) { bus.publish(TtsRequested(text = text, transactionId = "request", interactionId = "interaction")) }
        fun close() { owner.shutdown(); scope.cancel() }
        fun terminals() = bus.published.filter { it is TtsCompleted || it is TtsCancelled || it is TtsFailed }
    }
    @Test fun duplicateFinalWhilePlayingDoesNotRestartAudio() = runBlocking {
        val f = Fixture(CompletableDeferred())
        try {
            f.owner.start()
            f.finish("One answer.")
            f.finish("One answer.")
            assertEquals(listOf("One answer."), f.first.spoken)
            assertTrue(f.terminals().isEmpty())
            f.block!!.complete(Unit)
            assertEquals(1, f.terminals().size)
            assertTrue(f.terminals().single() is TtsCompleted)
        } finally { f.close() }
    }

    @Test fun duplicatePreparationDoesNotInterruptAudibleChunk() = runBlocking {
        val f = Fixture(CompletableDeferred())
        try {
            f.prepare(); f.token("This is the first sentence. ")
            f.prepare()
            assertEquals(listOf("This is the first sentence."), f.first.spoken)
            assertTrue(f.terminals().isEmpty())
            f.block!!.complete(Unit)
            f.finish("This is the first sentence.")
            assertEquals(1, f.terminals().size)
            assertTrue(f.terminals().single() is TtsCompleted)
        } finally { f.close() }
    }

    @Test fun systemDefaultPinsOneActualRouteAcrossResponseChunks() = runBlocking {
        val f = Fixture(systemDefault = true)
        try {
            f.prepare(); f.token("This is the first sentence. ")
            f.finish("This is the first sentence. This is the final sentence.")
            assertEquals(2, f.playbackEvidence.size)
            assertEquals(com.halo.sdk.runtime.SpeechRouteMode.SYSTEM_DEFAULT, f.playbackEvidence.first().mode)
            assertEquals(com.halo.sdk.runtime.SpeechRouteMode.EXACT_DEVICE, f.playbackEvidence.last().mode)
            assertEquals(17, f.playbackEvidence.last().androidDeviceId)
            assertEquals(8, f.playbackEvidence.last().androidDeviceType)
        } finally { f.close() }
    }
    @Test fun cloudFallbackIsStickyAndNeverRetriesOneFailedChunk() = runBlocking {
        var cloudCalls = 0
        var androidCalls = 0
        val cloud = object : PcmSpeechSynthesisProvider by Provider("cloud") {
            override suspend fun synthesizeToPcm(request: SpeechRequest): PcmSpeech? { cloudCalls++; return null }
        }
        val android = object : PcmSpeechSynthesisProvider by Provider("provider.android.tts") {
            override suspend fun synthesizeToPcm(request: SpeechRequest): PcmSpeech? { androidCalls++; return null }
        }
        val selector = Selector().apply { selectTts("cloud") }
        val request = SpeechRequest("First sentence.")
        val (pinned, _) = SpeechSynthesisRegistry(listOf(cloud, android), selector, MemoryStorage()).pin(request)
        assertNull(pinned.synthesizeToPcm(request))
        assertNull(pinned.synthesizeToPcm(request.copy(text = "Second sentence.")))
        assertEquals(1, cloudCalls)
        assertEquals(2, androidCalls)
    }
    @Test fun earlyAudioUsesOneProviderRouteAndTerminalWithoutPrefixReplay() = runBlocking {
        val f = Fixture()
        try {
            f.prepare(); f.token("This is the first sentence. ")
            assertEquals(listOf("This is the first sentence."), f.first.spoken)
            assertEquals(1, f.bus.published.filterIsInstance<TtsStarted>().size)
            assertTrue(f.terminals().isEmpty())
            f.selector.selectTts("second")
            f.finish("This is the first sentence. This is the final sentence.")
            assertEquals(listOf("This is the first sentence.", "This is the final sentence."), f.first.spoken)
            assertTrue(f.second.spoken.isEmpty()); assertEquals(1, f.requests.size)
            assertEquals(1, f.bus.published.filterIsInstance<TtsStarted>().size)
            assertEquals(1, f.terminals().size); assertTrue(f.terminals().single() is TtsCompleted)
        } finally { f.close() }
    }
    @Test fun shortBufferedAnswerSpeaksExactlyOnce() = runBlocking {
        val f = Fixture()
        try { f.prepare(); f.token("42"); assertTrue(f.first.spoken.isEmpty()); f.finish("42")
            assertEquals(listOf("42"), f.first.spoken); assertEquals(1, f.terminals().size)
        } finally { f.close() }
    }
    @Test fun stopClearsThreeChunksAndSuppressesLateFinal() = runBlocking {
        val f = Fixture(CompletableDeferred())
        try {
            f.prepare(); f.token("The first complete sentence. ")
            f.token("The second complete sentence. The third complete sentence. The fourth complete sentence.")
            f.owner.stop("Stop"); f.block!!.complete(Unit)
            f.finish("The first complete sentence. The second complete sentence. The third complete sentence. The fourth complete sentence.")
            assertEquals(listOf("The first complete sentence."), f.first.spoken)
            assertEquals(1, f.terminals().size); assertTrue(f.terminals().single() is TtsCancelled)
        } finally { f.close() }
    }
    @Test fun audibleHandoffStopsOldPlaybackAndSuppressesLateFinalWithoutReplay() = runBlocking {
        val f = Fixture(CompletableDeferred())
        try {
            f.owner.session.bindInteraction("interaction", SpeechInputRoute.Phone)
            f.prepare(); f.token("This first sentence is already audible. ")
            assertEquals(1, f.bus.published.filterIsInstance<TtsStarted>().size)
            f.owner.handoffInteraction("interaction")
            val device = SpeechOutputTarget.Device("exact", null, "Glasses", AudioDeviceKind.WEARABLE)
            assertTrue(f.owner.session.handoffInteraction("interaction", device, SpeechOutputFallback.DISALLOW_PHONE))
            f.block!!.complete(Unit)
            f.finish("This first sentence is already audible. More text follows.")
            assertEquals(listOf("This first sentence is already audible."), f.first.spoken)
            assertEquals(1, f.requests.size)
            assertEquals(SpeechInputRoute.Phone, f.owner.session.affinity.value!!.inputRoute)
            assertEquals(device, f.owner.session.affinity.value!!.intendedTarget)
            assertEquals(InteractionAudioRouteState.PENDING, f.owner.session.affinity.value!!.routeState)
            assertNull(f.owner.session.affinity.value!!.resolvedEndpointLabel)
        } finally { f.close() }
    }
    @Test fun unspokenHandoffAllowsFinalOnExactNewRouteOnce() = runBlocking {
        val f = Fixture()
        try {
            f.owner.session.bindInteraction("interaction", SpeechInputRoute.Phone)
            f.prepare(); f.token("42")
            f.owner.handoffInteraction("interaction")
            val device = SpeechOutputTarget.Device("exact", null, "Glasses", AudioDeviceKind.WEARABLE)
            assertTrue(f.owner.session.handoffInteraction("interaction", device, SpeechOutputFallback.DISALLOW_PHONE))
            f.bus.publish(TtsRequested(text = "42", transactionId = "request", interactionId = "interaction",
                target = device, fallback = SpeechOutputFallback.DISALLOW_PHONE))
            assertEquals(listOf("42"), f.first.spoken)
            assertEquals(device, f.requests.single().target)
            assertEquals(1, f.terminals().size)
            assertTrue(f.terminals().single() is TtsCompleted)
        } finally { f.close() }
    }
    @Test fun handoffCannotRewriteAnotherInteractionsAffinity() {
        val f = Fixture()
        try {
            f.owner.session.bindInteraction("original", SpeechInputRoute.Device("origin", null, "Glasses", AudioDeviceKind.WEARABLE))
            assertFalse(f.owner.session.handoffInteraction("different", SpeechOutputTarget.Phone, SpeechOutputFallback.ALLOW_PHONE))
            assertTrue(f.owner.session.handoffInteraction("original", SpeechOutputTarget.Phone, SpeechOutputFallback.ALLOW_PHONE))
            val affinity = f.owner.session.affinity.value!!
            assertEquals("origin", affinity.originDeviceId)
            assertTrue(affinity.inputRoute is SpeechInputRoute.Device)
            assertEquals(SpeechOutputTarget.Phone, affinity.intendedTarget)
            assertEquals(SpeechOutputFallback.ALLOW_PHONE, affinity.fallback)
        } finally { f.close() }
    }

    @Test fun settingsSurviveRecreationWithSafeFallbackDefault() {
        val storage = MemoryStorage(); val original = VoiceExperienceSettingsRepository(storage)
        assertFalse(original.state.value.wearablePhoneFallback)
        original.update(original.state.value.copy(streamingSpeechEnabled = false, interruptionSensitivity = InterruptionSensitivity.DELIBERATE,
            responseStyle = SpokenResponseStyle.DETAILED, wearablePhoneFallback = true))
        val restored = VoiceExperienceSettingsRepository(storage)
        assertEquals(original.state.value, restored.state.value)
    }
}
