package com.halo.app.ui.screens.settings

import com.halo.app.speech.diagnostics.*
import com.halo.engines.speech.*
import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.host.AudioFrame
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SpeechRecognitionProbeTest {
    private fun sample() = DiagnosticAudioCapture(AudioBuffer(listOf(AudioFrame(ShortArray(8000) { 1234 }, 16000, 1, 0))), "phone", 48000, 16000)
    private fun provider(text: String = "Atver Spotify") = object : BufferedSpeechRecognitionProvider {
        override suspend fun health() = true
        override suspend fun recognize(audio: AudioBuffer) = RecognitionResult(text, "lv", metrics = SpeechRecognitionMetrics(modelDisplayName = "Whisper Base", decodeMs = 100, audioDurationMs = 500))
    }
    private fun runner(capture: suspend (Long, Deferred<Unit>?) -> DiagnosticAudioCapture = { _, _ -> sample() },
        factory: (SpeechDiagnosticTarget) -> BufferedSpeechRecognitionProvider = { provider() },
        selected: BufferedSpeechRecognitionProvider = provider()) = SpeechDiagnosticRunner(capture, selected,
        EnergySpeechActivityEstimator(), factory, { emptyList() }, { "lv" }, { false })

    @Test fun probeUsesSharedCaptureAndClearsPcm() = runBlocking {
        val pcm = sample()
        val probe = SpeechRecognitionProbe(runner(capture = { _, _ -> pcm }))
        val result = probe.run {}
        assertTrue(result.contains("Atver Spotify")); assertTrue(result.contains("Whisper Base"))
        assertTrue(result.contains("SIMPLE_ENERGY")); assertTrue(result.contains("Accepted"))
        assertTrue(pcm.audio.frames.all { frame -> frame.samples.all { it == 0.toShort() } })
    }
    @Test fun samePcmSerialColdWarmCleanupAndPrivateSummary() = runBlocking {
        val pcm = sample(); val seen = mutableListOf<AudioBuffer>(); val lifecycle = mutableListOf<String>()
        var resident = false; var selectedCalled = false
        val runner = runner(factory = { target ->
            assertFalse(resident); resident = true; lifecycle.add("open:${target.modelId}")
            object : BufferedSpeechRecognitionProvider {
                var pendingCleanup = false
                override suspend fun health() = true
                override suspend fun recognize(audio: AudioBuffer): RecognitionResult {
                    assertFalse(pendingCleanup)
                    assertEquals(8000, audio.frames.sumOf { it.samples.size })
                    assertTrue(audio.frames.all { it.sampleRate == 16000 && it.channels == 1 && it.samples.all { sample -> sample == 1234.toShort() } })
                    pendingCleanup = true; seen.add(audio)
                    return RecognitionResult("Atver Spotify", "lv", metrics = SpeechRecognitionMetrics(decodeMs = 10, audioDurationMs = 500))
                }
                override suspend fun awaitQuiescent() { pendingCleanup = false; lifecycle.add("quiescent") }
                override suspend fun close() { assertFalse(pendingCleanup); resident = false; lifecycle.add("close:${target.modelId}") }
            }
        }, selected = object : BufferedSpeechRecognitionProvider {
            override suspend fun health() = true
            override suspend fun recognize(audio: AudioBuffer): RecognitionResult { selectedCalled = true; error("No selected-provider call") }
        })
        val session = runner.captureSession(reference = "Atver Spotify")
        runner.benchmark(session, listOf(SpeechDiagnosticTarget("a", "A", "lv"), SpeechDiagnosticTarget("b", "B", "lv")), 3)
        assertEquals(8, seen.size); seen.forEach { assertSame(session.capture.audio, it) }
        assertFalse(resident); assertFalse(selectedCalled)
        assertTrue(lifecycle.indexOf("close:a") < lifecycle.indexOf("open:b"))
        assertEquals(2, session.results.count { it.temperature == "cold" })
        assertEquals(6, session.results.count { it.temperature == "warm" })
        assertTrue(session.summary().contains("confidence=not provided"))
        assertFalse(session.summary().contains("Atver Spotify")); assertTrue(session.summary(true).contains("Atver Spotify"))
        session.close(); pcm.close()
    }
    @Test fun reloadedRunDoesNotContaminateWarmStatistics() = runBlocking {
        val runner = runner(factory = {
            object : BufferedSpeechRecognitionProvider {
                var run = 0
                override suspend fun health() = true
                override suspend fun recognize(audio: AudioBuffer): RecognitionResult {
                    run++
                    val reload = run == 3
                    return RecognitionResult("Atver Spotify", "lv", metrics = SpeechRecognitionMetrics(
                        loadMs = if (run == 1 || reload) 50 else 0, decodeMs = if (reload) 100 else 10, audioDurationMs = 500))
                }
            }
        })
        val sample = runner.captureSession()
        runner.benchmark(sample, listOf(SpeechDiagnosticTarget("a", "A", "lv")), 3)
        assertEquals(2, sample.results.count { it.temperature == "warm" })
        assertEquals(1, sample.results.count { it.temperature.contains("reloaded") })
        assertTrue(sample.summary().contains("A/lv warm: decode median=10.0 ms; min=10; max=10; n=2"))
        sample.close()
    }
    @Test fun cancellationWaitsForBlockingDecodeAndDiscardsResult() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        var closed = false; var opens = 0
        val runner = runner(factory = {
            opens++
            object : BufferedSpeechRecognitionProvider {
                override suspend fun health() = true
                override suspend fun recognize(audio: AudioBuffer): RecognitionResult {
                    entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); return RecognitionResult("late result", "lv")
                }
                override suspend fun close() { closed = true }
            }
        })
        val session = runner.captureSession()
        val work = launch(Dispatchers.Default) { runner.benchmark(session, listOf(SpeechDiagnosticTarget("a", "A", "lv"), SpeechDiagnosticTarget("b", "B", "lv"))) }
        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
        work.cancel(); assertFalse(closed); release.countDown(); withTimeout(5000) { work.join() }
        assertTrue(closed); assertEquals(1, opens); assertTrue(session.results.isEmpty()); session.close()
    }
    @Test fun rawDecodeAndGateRemainSeparate() = runBlocking {
        val runner = runner(selected = provider(""))
        val session = runner.captureSession()
        val row = runner.recognizeSelected(session)
        assertNotNull(row.recognition); assertEquals("EMPTY", row.gate)
        session.close()
    }
    @Test fun systemCheckRequiresDecodeNotJustPcmAndKeepsGateSeparate() = runBlocking {
        val failed = runner(selected = object : BufferedSpeechRecognitionProvider {
            override suspend fun health() = true
            override suspend fun recognize(audio: AudioBuffer): RecognitionResult = throw SpeechDiagnosticException(SpeechDiagnosticStage.MODEL_LOAD, IllegalStateException("load failed"))
        })
        val captured = failed.captureSession()
        assertTrue(captured.capture.audio.frames.isNotEmpty())
        val loadFailure = failed.recognizeSelected(captured)
        assertFalse(SpeechProbeVerdict.recognitionPassed(loadFailure)); assertEquals(SpeechDiagnosticStage.MODEL_LOAD, loadFailure.stage)
        val blank = runner(selected = provider("")).recognizeSelected(captured)
        assertFalse(SpeechProbeVerdict.recognitionPassed(blank))
        val rejection = SpeechDiagnosticResult(SpeechDiagnosticTarget("a", "A", "lv"), "selected", 1,
            RecognitionResult("decoded text", "lv"), "INSUFFICIENT_VOICE", null, SpeechDiagnosticStage.RESULT)
        assertTrue(SpeechProbeVerdict.recognitionPassed(rejection)); captured.close()
    }
    @Test fun capturePassesIndependentlyOfSelectedModelAvailability() = runBlocking {
        val runner = runner(selected = object : BufferedSpeechRecognitionProvider {
            override suspend fun health() = false
            override suspend fun recognize(audio: AudioBuffer): RecognitionResult = error("No decode without model")
        })
        val sample = runner.captureSession()
        assertTrue(sample.capture.audio.frames.any { it.samples.isNotEmpty() })
        val result = runner.recognizeSelected(sample)
        assertFalse(SpeechProbeVerdict.recognitionPassed(result)); assertEquals(SpeechDiagnosticStage.MODEL_RESOLVE, result.stage)
        sample.close()
    }
    @Test fun knownStageFailures() = runBlocking {
        val runner = runner(capture = { _, _ -> error("microphone busy") })
        try { runner.captureSession(); fail("Expected capture failure") }
        catch (error: SpeechDiagnosticException) { assertEquals(SpeechDiagnosticStage.CAPTURE, error.stage) }
    }
    @Test fun scoringNormalizesPunctuationButPreservesDiacritics() {
        assertEquals(SpeechScore(0.0, 0.0), SpeechScoring.score("  Kāds šodien IR laiks?", "kāds šodien ir laiks"))
        assertEquals(0.0, SpeechScoring.score("What's the weather?", "Whats the weather").wer, 0.001)
        val accents = SpeechScoring.score("Jānim mūziku", "Janim muziku")
        assertEquals(1.0, accents.wer, 0.001); assertTrue(accents.cer > 0)
        assertEquals(0.0, SpeechScoring.score("", "").wer, 0.001)
        assertEquals(1.0, SpeechScoring.score("one two", "").wer, 0.001)
    }
}
