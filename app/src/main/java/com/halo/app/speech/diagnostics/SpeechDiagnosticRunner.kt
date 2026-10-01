package com.halo.app.speech.diagnostics

import com.halo.engines.speech.*
import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.util.UUID
import kotlin.math.sqrt

data class SpeechDiagnosticTarget(val modelId: String, val displayName: String, val language: String)
data class SpeechDiagnosticResult(val target: SpeechDiagnosticTarget, val temperature: String,
    val repetition: Int, val recognition: RecognitionResult?, val gate: String?, val score: SpeechScore?,
    val stage: SpeechDiagnosticStage, val failure: String? = null)
data class SpeechDiagnosticSession(val capture: DiagnosticAudioCapture, val language: String,
    val reference: String = "", val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(), val activity: SpeechActivityEstimate,
    val rms: Double, val warnings: List<String>, val results: MutableList<SpeechDiagnosticResult> = mutableListOf()) : AutoCloseable {
    override fun close() { capture.close(); results.clear() }
    fun summary(includeTranscripts: Boolean = false): String = buildString {
        appendLine("Speech session $id @ $timestamp; source=${capture.source}; language=$language")
        appendLine("PCM ${capture.originalSampleRate} → ${capture.processedSampleRate} Hz mono; audio=${activity.durationMs}ms; peak=${activity.peakAmplitude}; RMS=$rms")
        appendLine("voiced=${activity.voicedDurationMs}ms; ratio=${activity.voicedRatio}; segments=${activity.segmentCount}; longest=${activity.longestSegmentMs}ms; estimator=${activity.estimator}; preRollMsUsed=0 (explicit capture)")
        warnings.forEach { appendLine("Warning: $it") }
        if (includeTranscripts) appendLine("Reference: $reference")
        results.forEach { row ->
            val m = row.recognition?.metrics
            appendLine("${m?.modelDisplayName ?: row.target.displayName}/${row.target.language} ${row.temperature} #${row.repetition}: stage=${row.stage}; error=${row.failure}; gate=${row.gate}; provider=${m?.providerId}; model=${m?.modelId}; configuredLanguage=${m?.language}; load=${m?.loadMs}ms; decode=${m?.decodeMs}ms; total=${m?.totalMs}ms; RTF=${m?.realTimeFactor}; WER=${row.score?.wer}; CER=${row.score?.cer}")
            if (includeTranscripts) appendLine("Transcript: ${row.recognition?.text.orEmpty()}")
        }
        results.filter { it.recognition?.metrics?.decodeMs != null }.groupBy { it.target to it.temperature }.forEach { (key, rows) ->
            val times = rows.map { it.recognition!!.metrics!!.decodeMs!! }.sorted()
            val median = if (times.size % 2 == 1) times[times.size / 2].toDouble() else (times[times.size / 2 - 1] + times[times.size / 2]) / 2.0
            appendLine("${key.first.displayName}/${key.first.language} ${key.second}: decode median=$median ms; min=${times.first()}; max=${times.last()}; n=${times.size}")
        }
    }
}

/** Composition-only diagnostics: no bus, cognition, selector, tools or storage dependency. */
class SpeechDiagnosticRunner(
    private val capture: suspend (Long, Deferred<Unit>?) -> DiagnosticAudioCapture,
    private val selected: BufferedSpeechRecognitionProvider,
    private val estimator: SpeechActivityEstimator,
    private val factory: (SpeechDiagnosticTarget) -> BufferedSpeechRecognitionProvider,
    val availableTargets: () -> List<SpeechDiagnosticTarget>,
    val language: () -> String,
    val detectorReady: () -> Boolean,
    private val engine: AudioPerceptionEngine? = null
) {
    private val transaction = Mutex()
    suspend fun selectedHealth() = selected.healthDetails()
    suspend fun captureSession(finish: Deferred<Unit>? = null, reference: String = "", maxDurationMs: Long = 15_000): SpeechDiagnosticSession {
        if (!transaction.tryLock()) throw SpeechDiagnosticException(SpeechDiagnosticStage.CAPTURE, IllegalStateException("Diagnostic transaction busy"))
        var sample: DiagnosticAudioCapture? = null
        var stage = SpeechDiagnosticStage.CAPTURE
        try {
            sample = capture(maxDurationMs, finish)
            stage = SpeechDiagnosticStage.PREPROCESS
            check(sample.audio.frames.all { it.sampleRate == 16000 && it.channels == 1 }) { "Expected 16 kHz mono PCM" }
            stage = SpeechDiagnosticStage.VAD
            val activity = withContext(Dispatchers.Default) { estimator.estimate(sample.audio) }
            val frames = sample.audio.frames
            val count = frames.sumOf { it.samples.size }
            val rms = sqrt(frames.sumOf { frame -> frame.samples.sumOf { it.toDouble() * it } } / count.coerceAtLeast(1))
            return SpeechDiagnosticSession(sample, language(), reference, activity = activity, rms = rms,
                warnings = buildList {
                    if (activity.peakAmplitude < 1000) add("Low amplitude")
                    if (frames.any { f -> f.samples.any { kotlin.math.abs(it.toInt()) >= 32760 } }) add("Possible clipping")
                    if (activity.voicedDurationMs < 120) add("Little detected voice")
                    if (activity.estimator.name == "SIMPLE_ENERGY") add("Energy fallback; VAD evidence is approximate")
                })
        } catch (error: Throwable) {
            sample?.close()
            if (error is CancellationException) throw error
            throw if (error is SpeechDiagnosticException) error else SpeechDiagnosticException(stage, error)
        } finally { transaction.unlock() }
    }
    suspend fun recognizeSelected(session: SpeechDiagnosticSession): SpeechDiagnosticResult = withTransaction {
        decode(session, selected, SpeechDiagnosticTarget(selected.metadata.id, selected.metadata.displayName, session.language), "selected", 1)
    }
    suspend fun benchmark(session: SpeechDiagnosticSession, targets: List<SpeechDiagnosticTarget>, warmRepetitions: Int = 1) = withTransaction {
        require(warmRepetitions == 1 || warmRepetitions == 3)
        for (target in targets.distinct()) {
            currentCoroutineContext().ensureActive()
            var provider: BufferedSpeechRecognitionProvider? = null
            try {
                provider = factory(target)
                val cold = decode(session, provider, target, "cold", 1)
                if (cold.recognition == null) continue
                provider.awaitQuiescent()
                repeat(warmRepetitions) { index ->
                    currentCoroutineContext().ensureActive()
                    decode(session, provider, target, "warm", index + 1)
                    provider.awaitQuiescent()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                session.results.add(SpeechDiagnosticResult(target, "cold", 1, null, null, null,
                    (error as? SpeechDiagnosticException)?.stage ?: SpeechDiagnosticStage.MODEL_RESOLVE, error.message))
            } finally {
                withContext(NonCancellable + Dispatchers.Default) { provider?.close(); provider?.awaitQuiescent() }
            }
        }
    }
    private suspend fun <T> withTransaction(block: suspend () -> T): T {
        if (!transaction.tryLock()) throw SpeechDiagnosticException(SpeechDiagnosticStage.RESOURCE_ACQUIRE, IllegalStateException("Diagnostic transaction busy"))
        try { return if (engine != null) engine.withDiagnosticRecognitionOwnership(block) else block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { throw if (error is SpeechDiagnosticException) error else SpeechDiagnosticException(SpeechDiagnosticStage.RESOURCE_ACQUIRE, error) }
        finally { transaction.unlock() }
    }
    private suspend fun decode(session: SpeechDiagnosticSession, provider: BufferedSpeechRecognitionProvider,
        target: SpeechDiagnosticTarget, temperature: String, repetition: Int): SpeechDiagnosticResult {
        var stage = SpeechDiagnosticStage.MODEL_RESOLVE
        try {
            check(provider.health()) { provider.healthDetails().message ?: "Verified model unavailable" }
            stage = SpeechDiagnosticStage.DECODE
            // JNI cannot be interrupted. Do not free native state or PCM until this returns.
            val result = withContext(Dispatchers.Default) { provider.recognize(session.capture.audio) }
            currentCoroutineContext().ensureActive()
            stage = SpeechDiagnosticStage.TRANSCRIPT_GATE
            val a = session.activity
            val gate = SpeechTranscriptGate().evaluate(result.text, SpeechRecognitionQuality(confidence = result.confidence,
                audioDurationMs = a.durationMs, voicedDurationMs = a.voicedDurationMs, voicedRatio = a.voicedRatio,
                peakAmplitude = a.peakAmplitude, language = result.language, qualityEstimator = a.estimator.name))
            val measuredTemperature = if (temperature == "warm" && (result.metrics?.loadMs ?: 0) > 0) "cold (reloaded before warm run)" else temperature
            return SpeechDiagnosticResult(target, measuredTemperature, repetition, result,
                if (gate is SpeechGateResult.Rejected) gate.reason.name else "Accepted",
                session.reference.takeIf { it.isNotBlank() }?.let { SpeechScoring.score(it, result.text) },
                SpeechDiagnosticStage.RESULT).also { session.results.add(it) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            return SpeechDiagnosticResult(target, temperature, repetition, null, null, null,
                (error as? SpeechDiagnosticException)?.stage ?: stage, error.message).also { session.results.add(it) }
        } finally { withContext(NonCancellable + Dispatchers.Default) { provider.awaitQuiescent() } }
    }
}
