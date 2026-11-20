package com.halo.app.study

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudySpeechModeTest {
    private class Bus : EventBus {
        val sent = mutableListOf<HaloEvent>()
        private val events = MutableSharedFlow<HaloEvent>(extraBufferCapacity = 8)
        override suspend fun publish(event: HaloEvent) { sent += event; events.emit(event) }
        override fun events(): Flow<HaloEvent> = events
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = events.filter { type.isInstance(it) }.map { type.cast(it)!! }
    }
    @Test fun localAndPowerUseTheSameReaderWithCurrentSourceProvenance() = runTest {
        val bus = Bus()
        var remote: RemoteDispatchProvenance? = null
        val owner = SpeechOutputStudyNarration(bus, {}, backgroundScope) { remote }
        owner.narrate("Local sentence", "lesson", "lesson:reader:1"); runCurrent()
        assertTrue((bus.sent.single() as TtsRequested).localOnly)
        remote = RemoteDispatchProvenance(setOf(RemoteDataCategory.TEXT, RemoteDataCategory.WORKSPACE), 47)
        owner.narrate("Power sentence", "lesson", "lesson:reader:2"); runCurrent()
        val request = bus.sent.last() as TtsRequested
        assertFalse(request.localOnly)
        assertEquals(setOf("TEXT", "WORKSPACE"), request.remoteDataCategories)
        assertEquals(47L, request.remoteDispatchRevision)
        owner.shutdown()
    }
    @Test fun failedSourceCheckEndsNarrationWithoutUploadingOrHanging() = runTest {
        val bus = Bus()
        val owner = SpeechOutputStudyNarration(bus, {}, backgroundScope) { error("Source unavailable") }
        val terminal = mutableListOf<NarrationEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { owner.events.collect { terminal += it } }
        owner.narrate("Must not upload", "lesson", "lesson:reader:1"); runCurrent()
        assertTrue(bus.sent.isEmpty())
        assertTrue(terminal.single() is NarrationEvent.Failed)
        owner.shutdown()
    }
    @Test fun stoppingWhileCheckingSourceCannotPublishAnOldRequest() = runTest {
        val bus = Bus()
        val check = CompletableDeferred<RemoteDispatchProvenance?>()
        val owner = SpeechOutputStudyNarration(bus, {}, backgroundScope) { check.await() }
        owner.narrate("Obsolete", "lesson", "lesson:reader:1"); runCurrent()
        owner.stopSession("lesson", "Stop")
        check.complete(RemoteDispatchProvenance(setOf(RemoteDataCategory.TEXT), 1)); runCurrent()
        assertTrue(bus.sent.isEmpty())
        owner.shutdown()
    }
}
