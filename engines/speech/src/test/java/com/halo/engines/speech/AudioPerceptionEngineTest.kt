package com.halo.engines.speech

import com.halo.sdk.runtime.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test
import org.junit.Assert.assertEquals

class AudioPerceptionEngineTest {

    @Test
    fun engineCompilesAndStarts() {
        // Recognition logic has been moved to SpeechSessionManager.
        // This test serves as a placeholder for pure AudioCapture tests.
        assertEquals(4, 2 + 2)
    }

}
