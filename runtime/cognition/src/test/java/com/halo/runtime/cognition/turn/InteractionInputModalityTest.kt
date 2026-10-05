package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.blackboard.InteractionOrigin
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import org.junit.Assert.*
import org.junit.Test

class InteractionInputModalityTest {
    private fun turn(metadata: Map<String, String> = emptyMap(), origin: InteractionOrigin? = null,
        delivery: ResponseDelivery = ResponseDelivery.SPEECH_AND_VISUAL) = CognitiveTurnRequest(
        InteractionId("test"), "test", AiRequest(text = "hello"), AiContextPolicy.CONSERVATIVE, delivery, origin, metadata)
    @Test fun outputNeverDeterminesInput() {
        assertEquals(InteractionInputModality.UNKNOWN, turn().inputModality())
        assertEquals(InteractionInputModality.TEXT, turn(mapOf("input" to "keyboard")).inputModality())
        assertEquals(InteractionInputModality.TEXT, turn(mapOf("inputModality" to "TEXT")).inputModality())
        for (delivery in ResponseDelivery.entries) {
            assertEquals(InteractionInputModality.VOICE, turn(origin = InteractionOrigin(Source.MICROPHONE), delivery = delivery).inputModality())
        }
        assertEquals(InteractionInputModality.VOICE, turn(origin = InteractionOrigin(Source.OS, speechRoute = SpeechInputRoute.Phone)).inputModality())
        assertEquals(InteractionInputModality.TEXT, turn(mapOf("inputModality" to "TEXT"), InteractionOrigin(Source.MICROPHONE)).inputModality())
    }
}
