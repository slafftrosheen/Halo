package com.halo.app.ui.fx

import com.halo.app.ui.presence.HaloSignalState
import org.junit.Assert.assertEquals
import org.junit.Test

class HaloLightStateContractTest {
    @Test fun completedAndStoppedStatesNeverKeepAnIdleBreath() {
        assertEquals(HaloLight.SUCCESS, HaloSignalState.SUCCESS.light())
        assertEquals(0, HaloSignalState.SUCCESS.light().period)
        assertEquals(HaloLight.OFF, HaloSignalState.STOPPED.light())
        assertEquals(0f, HaloSignalState.STOPPED.light().strength, 0f)
    }

    @Test fun activeStateLightFollowsTheCanonicalPresentation() {
        val expected = mapOf(
            HaloSignalState.IDLE to HaloLight.IDLE,
            HaloSignalState.LISTENING to HaloLight.LISTENING,
            HaloSignalState.CAPTURING to HaloLight.LISTENING,
            HaloSignalState.THINKING to HaloLight.THINKING,
            HaloSignalState.ACTING to HaloLight.ACTING,
            HaloSignalState.SPEAKING to HaloLight.IDLE,
            HaloSignalState.WAITING to HaloLight.NEEDS_YOU,
            HaloSignalState.WARNING to HaloLight.NEEDS_YOU,
            HaloSignalState.ERROR to HaloLight.ERROR,
            HaloSignalState.OFFLINE to HaloLight.ERROR,
            HaloSignalState.SUCCESS to HaloLight.SUCCESS,
            HaloSignalState.STOPPED to HaloLight.OFF,
        )
        assertEquals(HaloSignalState.entries.toSet(), expected.keys)
        expected.forEach { (state, light) -> assertEquals(state.name, light, state.light()) }
    }
}
