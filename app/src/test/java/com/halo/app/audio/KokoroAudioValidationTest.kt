package com.halo.app.audio

import org.junit.Assert.assertThrows
import org.junit.Test

class KokoroAudioValidationTest {
    @Test fun `silence and invalid native samples cannot become ready speech`() {
        assertThrows(IllegalArgumentException::class.java) { KokoroAudioValidation.validate(FloatArray(240),24000) }
        assertThrows(IllegalArgumentException::class.java) { KokoroAudioValidation.validate(floatArrayOf(Float.NaN,0.2f),24000) }
        assertThrows(IllegalArgumentException::class.java) { KokoroAudioValidation.validate(floatArrayOf(Float.POSITIVE_INFINITY),24000) }
        KokoroAudioValidation.validate(floatArrayOf(0f,0.2f,-0.2f),24000)
    }
}
