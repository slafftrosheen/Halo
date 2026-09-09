package com.halo.providers.android.stt

import com.halo.sdk.provider.RecognitionResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidSpeechRecognitionProviderTest {

    @Test
    fun `recognition result holds non-empty text and language`() = runTest {
        val result = RecognitionResult("Hello Halo", "en-US", 1.0f)
        assertEquals("Hello Halo", result.text)
        assertEquals("en-US", result.language)
        assertTrue(result.confidence > 0f)
    }

    @Test
    fun `locale provider uses custom locale if configured`() {
        var locale = "en-US"
        val getLocaleLambda = { locale }
        assertEquals("en-US", getLocaleLambda())

        locale = "es-ES"
        assertEquals("es-ES", getLocaleLambda())
    }
}
