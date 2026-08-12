package com.halo.drivers.heycyan

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeyCyanVoiceSessionTest {

    @Test
    fun `voice session transitions through state lifecycle on BC73 events`() = runTest {
        val eventFlow = MutableSharedFlow<HeyCyanEvent>()
        val session = HeyCyanVoiceSession(
            events = eventFlow,
            scope = this
        )
        // Run initial launch so events.collect starts before any emissions
        testScheduler.runCurrent()

        assertEquals(HeyCyanVoiceSession.State.IDLE, session.state.value)
        assertFalse(session.isActive)

        // Emit VoiceActivationStarted (BC 73 payload 03)
        eventFlow.emit(HeyCyanEvent.VoiceActivationStarted)
        testScheduler.runCurrent()

        assertEquals(HeyCyanVoiceSession.State.ACTIVE, session.state.value)
        assertTrue(session.isActive)

        // Emit VoiceActivationEnded (BC 73 payload 0A)
        eventFlow.emit(HeyCyanEvent.VoiceActivationEnded)
        testScheduler.runCurrent()

        assertEquals(HeyCyanVoiceSession.State.IDLE, session.state.value)
        assertFalse(session.isActive)
    }
}
