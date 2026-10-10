package com.halo.app.capture

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.halo.providers.sherpa.stt.RecognitionReadiness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LocalMemoTranscriberTest {
    @Test fun missingProviderIsExplicitAndNeverReadsAudioOrFallsBackToChat() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val readiness = MutableStateFlow(RecognitionReadiness(phase = "Missing", language = "lv"))
        val transcriber = LocalMemoTranscriber(MemoPcmDecoder(context), { null }, readiness)
        val failure = runCatching { transcriber.transcribe(Uri.parse("content://unreadable/source"), "original") }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure!!.message!!.contains("installed model"))
        assertEquals("lv", transcriber.readiness.value.language)
        assertEquals("Missing", transcriber.readiness.value.phase)
    }
    @Test fun cancellationIsNotConvertedIntoRecognitionFailure() = runTest {
        val transcriber = LocalMemoTranscriber(MemoPcmDecoder(ApplicationProvider.getApplicationContext()),
            { throw CancellationException("Canceled") }, MutableStateFlow(RecognitionReadiness()))
        assertTrue(runCatching { transcriber.transcribe(Uri.EMPTY, "source") }.exceptionOrNull() is CancellationException)
    }
}
