package com.halo.app.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.sdk.provider.PcmEncoding
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android decoder only. A generated test tone is never played or sent over the network. */
@RunWith(AndroidJUnit4::class)
class SpeechMp3DecoderPhysicalTest {
    @Test fun mp3FormatIsReadFromTheAudioInsteadOfAssumingAPcmSampleRate() = runBlocking {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("speech-decoder-24000.mp3").use { it.readBytes() }
        val pcm = SpeechMp3Decoder().decode(bytes)
        assertEquals(24000, pcm.sampleRateHz)
        assertEquals(1, pcm.channelCount)
        assertTrue(pcm.encoding in setOf(PcmEncoding.SIGNED_16_LE, PcmEncoding.FLOAT_32_LE))
        val sampleCount = pcm.chunks.sumOf { it.size } / pcm.encoding.bytesPerSample
        assertTrue(sampleCount in 4000..9000)
        assertTrue(pcm.chunks.any { chunk -> chunk.any { it != 0.toByte() } })
    }
}
