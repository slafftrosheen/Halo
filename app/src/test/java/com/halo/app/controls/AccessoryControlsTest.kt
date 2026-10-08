package com.halo.app.controls

import com.halo.sdk.host.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccessoryControlsTest {
    private class Memory : Storage {
        val values = mutableMapOf<String, String>()
        override fun getString(key: String, defaultValue: String?) = values[key] ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key]?.toIntOrNull() ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = "$value" }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key]?.toBooleanStrictOrNull() ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = "$value" }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
    private val endpoint = ControlEndpoint("opaque-device", "HEYCYAN", "epoch", true, true,
        setOf(AccessoryControl.CAMERA to AccessoryControlAction.SINGLE), AccessoryControls.managedActions)
    private fun binding(action: AccessoryAction) = ControlBinding(AccessoryControl.CAMERA, AccessoryControlAction.SINGLE, AccessoryActionSelection(action))
    private fun event(reserved: Boolean = false, evidence: ControlEvidence = ControlEvidence.VERIFIED_VENDOR) =
        AccessoryControlEvent.Button(AccessoryControl.CAMERA, AccessoryControlAction.SINGLE, 0,
            ControlDelivery(endpoint.id, endpoint.epoch, "event", ControlOrigin.MANAGED_DEVICE, evidence, reserved = reserved))
    @Test fun deviceMappingBeatsClass() {
        val repo = AccessoryControlMappingRepository(Memory())
        repo.save(endpoint, binding(AccessoryAction.StopSpeaking), true); repo.save(endpoint, binding(AccessoryAction.TakePhoto))
        assertEquals(AccessoryAction.TakePhoto, ConfigurableAccessoryControlPolicy.select(event(), endpoint, repo.device(endpoint), repo.defaults(endpoint.deviceClass)).action)
    }
    @Test fun classMappingBeatsSafeSystemDefault() {
        assertEquals(AccessoryAction.StopSpeaking, ConfigurableAccessoryControlPolicy.select(event(), endpoint, emptyList(), listOf(binding(AccessoryAction.StopSpeaking))).action)
    }
    @Test fun reservedPlatformInputCannotBeOverridden() {
        assertEquals(AccessoryAction.SystemDefault, ConfigurableAccessoryControlPolicy.select(event(true), endpoint, listOf(binding(AccessoryAction.TakePhoto)), emptyList()).action)
    }
    @Test fun unknownControlCannotExecuteMapping() {
        assertEquals(AccessoryAction.DoNothing, ConfigurableAccessoryControlPolicy.select(event(evidence = ControlEvidence.UNKNOWN), endpoint, listOf(binding(AccessoryAction.TakePhoto)), emptyList()).action)
    }
    @Test fun disconnectedAndOldEpochFailClosed() {
        assertEquals(AccessoryAction.DoNothing, ConfigurableAccessoryControlPolicy.select(event(), endpoint.copy(connected = false), listOf(binding(AccessoryAction.TakePhoto)), emptyList()).action)
        assertEquals(AccessoryAction.DoNothing, ConfigurableAccessoryControlPolicy.select(event(), endpoint.copy(epoch = "next"), listOf(binding(AccessoryAction.TakePhoto)), emptyList()).action)
    }
    @Test fun persistenceRoundTripAndReset() {
        val memory = Memory(); val repo = AccessoryControlMappingRepository(memory)
        assertTrue(repo.save(endpoint, binding(AccessoryAction.StopSpeaking)))
        assertEquals(listOf(binding(AccessoryAction.StopSpeaking)), AccessoryControlMappingRepository(memory).device(endpoint))
        repo.reset(endpoint); assertTrue(AccessoryControlMappingRepository(memory).device(endpoint).isEmpty())
    }
    @Test fun connectionOnlyMappingIsNotDurable() {
        val memory = Memory(); val repo = AccessoryControlMappingRepository(memory); val transient = endpoint.copy(durable = false)
        repo.save(transient, binding(AccessoryAction.StopSpeaking))
        assertEquals(1, repo.device(transient).size)
        assertTrue(AccessoryControlMappingRepository(memory).device(transient).isEmpty())
        assertTrue(repo.device(transient.copy(epoch = "new")).isEmpty())
        repo.disconnect(transient); assertEquals(0, repo.count())
    }
    @Test fun malformedAndUnknownActionsRejectWholeDocument() {
        val memory = Memory(); val repo = AccessoryControlMappingRepository(memory)
        repo.save(endpoint, binding(AccessoryAction.StopSpeaking))
        memory.values[AccessoryControlMappingRepository.KEY] = memory.values[AccessoryControlMappingRepository.KEY]!!.replace("StopSpeaking", "RunShell")
        val broken = AccessoryControlMappingRepository(memory)
        assertTrue(broken.malformed); assertEquals(0, broken.count())
        memory.values[AccessoryControlMappingRepository.KEY] = "{"
        assertTrue(AccessoryControlMappingRepository(memory).malformed)
    }
    @Test fun futureSchemaDoesNotExecuteOldMappings() {
        val memory = Memory(); AccessoryControlMappingRepository(memory).save(endpoint, binding(AccessoryAction.StopSpeaking))
        memory.values[AccessoryControlMappingRepository.KEY] = memory.values[AccessoryControlMappingRepository.KEY]!!.replace("\"version\":1", "\"version\":2")
        assertEquals(0, AccessoryControlMappingRepository(memory).count())
    }
    @Test fun schemaVersionMustBeExactInteger() {
        for (version in listOf("1.5", "\"1\"", "null")) {
            val memory = Memory()
            memory.values[AccessoryControlMappingRepository.KEY] = "{\"version\":$version,\"devices\":[],\"classes\":[]}"
            assertTrue(AccessoryControlMappingRepository(memory).malformed)
        }
    }
    @Test fun unavailableActionsAndUndeliverableControlsCannotBeSaved() {
        val repo = AccessoryControlMappingRepository(Memory())
        assertFalse(repo.save(endpoint, binding(AccessoryAction.TalkToHalo)))
        assertFalse(repo.save(endpoint, binding(AccessoryAction.StopSpeaking).copy(control = AccessoryControl.VOLUME_UP)))
        assertFalse(repo.save(endpoint.copy(connected = false), binding(AccessoryAction.StopSpeaking)))
    }
    @Test fun resetAllClearsClassAndDeviceMappings() {
        val repo = AccessoryControlMappingRepository(Memory()); repo.save(endpoint, binding(AccessoryAction.StopSpeaking), true)
        repo.save(endpoint, binding(AccessoryAction.TakePhoto)); repo.resetAll(); assertEquals(0, repo.count())
    }
    @Test fun unavailableAndDisconnectedNeverCallOwner() = runTest {
        var calls = 0; val dispatcher = AccessoryActionDispatcher { _, _ -> calls++; AccessoryActionResult(AccessoryActionOutcome.EXECUTED, "done") }
        assertEquals(AccessoryActionOutcome.UNAVAILABLE, dispatcher.dispatch(AccessoryActionSelection(AccessoryAction.TalkToHalo), endpoint).outcome)
        assertEquals(AccessoryActionOutcome.BLOCKED, dispatcher.dispatch(AccessoryActionSelection(AccessoryAction.TakePhoto), endpoint.copy(connected = false)).outcome)
        assertEquals(0, calls)
    }
    @Test fun systemDefaultAndDoNothingHaveNoOwnerSideEffects() = runTest {
        var calls = 0; val dispatcher = AccessoryActionDispatcher { _, _ -> calls++; AccessoryActionResult(AccessoryActionOutcome.EXECUTED, "done") }
        for (action in listOf(AccessoryAction.SystemDefault, AccessoryAction.DoNothing))
            assertEquals(AccessoryActionOutcome.IGNORED, dispatcher.dispatch(AccessoryActionSelection(action), endpoint).outcome)
        assertEquals(0, calls)
    }
    @Test fun failedOwnerIsNeverReportedExecuted() = runTest {
        val dispatcher = AccessoryActionDispatcher { _, _ -> error("sensitive payload") }
        assertEquals(AccessoryActionResult(AccessoryActionOutcome.FAILED, "Control action failed"), dispatcher.dispatch(AccessoryActionSelection(AccessoryAction.TakePhoto), endpoint))
    }
    @Test fun typedNavigationDoesNotAcquireExecutionAuthority() = runTest {
        val dispatcher = AccessoryActionDispatcher { selection, _ ->
            assertEquals(AccessoryAction.OpenStudy, selection.action)
            AccessoryActionResult(AccessoryActionOutcome.OPENED_FOR_REVIEW, "Study")
        }
        assertEquals(AccessoryActionOutcome.OPENED_FOR_REVIEW, dispatcher.dispatch(AccessoryActionSelection(AccessoryAction.OpenStudy), endpoint).outcome)
        assertFalse(AccessoryAction.entries.any { it.name.contains("Approve") || it.name.contains("ExecuteRoutine") || it.name.contains("Shell") })
    }
    private class Events : EventBus {
        val flow = MutableSharedFlow<HaloEvent>(); val published = mutableListOf<HaloEvent>()
        override fun events(): Flow<HaloEvent> = flow
        override suspend fun publish(event: HaloEvent) {
            published += event; flow.emit(event)
            if (event is TtsRequested && event.transactionId !in setOf("completed", "stream", "old"))
                flow.emit(TtsCompleted(transactionId = event.transactionId, target = event.target))
        }
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
    }
    @Test fun repeatUsesOnlyCompletedVisibleTextAndExactSpeechPolicy() = runTest {
        val events = Events(); val replay = CompletedAnswerReplay(events, backgroundScope)
        events.publish(AssistantResponsePublished(text = "Completed answer", delivery = ResponseDelivery.SPEECH_AND_VISUAL, transactionId = "completed")); runCurrent()
        val target = SpeechOutputTarget.Device("exact", 9, "Device", AudioDeviceKind.WEARABLE)
        events.publish(TtsRequested(text = "Completed answer", transactionId = "completed", target = target, fallback = SpeechOutputFallback.DISALLOW_PHONE)); runCurrent()
        events.published.clear(); replay.repeat(); runCurrent()
        val repeated = events.published.single() as TtsRequested
        assertEquals(target, repeated.target); assertEquals(SpeechOutputFallback.DISALLOW_PHONE, repeated.fallback)
        assertEquals("Completed answer", repeated.text); assertNotEquals("completed", repeated.transactionId)
    }
    @Test fun partialAndSupersededSpeechCannotBeReplayed() = runTest {
        val events = Events(); val replay = CompletedAnswerReplay(events, backgroundScope)
        events.publish(TtsRequested(text = "partial", transactionId = "stream")); runCurrent()
        assertEquals(AccessoryActionOutcome.UNAVAILABLE, replay.repeat().outcome)
        events.publish(AssistantResponsePublished(text = "Visual only", delivery = ResponseDelivery.VISUAL_ONLY, transactionId = "new")); runCurrent()
        events.publish(TtsRequested(text = "old", transactionId = "old")); runCurrent()
        assertEquals(AccessoryActionOutcome.UNAVAILABLE, replay.repeat().outcome)
    }
    @Test fun idleMediaHasNoSessionEvenWithCustomMapping() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val memory = Memory(); val controls = AccessoryControls(memory, backgroundScope, AccessoryActionDispatcher { _, _ -> error("must not execute") })
            val speech = MutableStateFlow(false)
            val media = ScopedSpeechMediaControls(org.robolectric.RuntimeEnvironment.getApplication(), controls, memory, backgroundScope, speech)
            media.configure(AccessoryAction.StopSpeaking); runCurrent()
            assertFalse(media.active); assertTrue(controls.endpoints.value.none { it.connected })
        } finally { Dispatchers.resetMain() }
    }
    @Test fun scopedMediaSessionExistsOnlyDuringActualSpeech() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val memory = Memory(); val controls = AccessoryControls(memory, backgroundScope, AccessoryActionDispatcher { _, _ -> error("must not execute") })
            val speech = MutableStateFlow(false)
            val media = ScopedSpeechMediaControls(org.robolectric.RuntimeEnvironment.getApplication(), controls, memory, backgroundScope, speech)
            media.configure(AccessoryAction.StopSpeaking); runCurrent()
            speech.value = true; runCurrent(); assertTrue(media.active)
            assertEquals(setOf(AccessoryControl.PLAY_PAUSE to AccessoryControlAction.SINGLE), controls.endpoints.value.single().supported)
            speech.value = false; runCurrent(); assertFalse(media.active); assertFalse(controls.endpoints.value.single().connected)
        } finally { Dispatchers.resetMain() }
    }
}
