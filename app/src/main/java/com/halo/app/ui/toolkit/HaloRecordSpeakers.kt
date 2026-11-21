package com.halo.app.ui.toolkit

import android.content.Context
import com.halo.providers.sherpa.stt.SherpaModelInstaller
import com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog
import com.halo.providers.sherpa.stt.SpeechModelInstaller
import com.halo.sdk.provider.RemoteDispatchPolicy
import com.halo.sdk.provider.modelAcquisitionRevision
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

internal object HaloRecordSpeakerModels {
    // Official Sherpa model distributions pinned by commit, size and documented LFS SHA256.
    private val segmentation = model("record-segmentation", "sherpa-onnx-pyannote-segmentation-3-0",
        "340b52f1f5cd12d45a30fa284691417eaad2ff92", "model.int8.onnx", 1_540_514L,
        "10a438c2e0d90ed5f5da545cec2244d887315f6dbbbf1d3d564d00745b01952e")
    private val embedding = model("record-speaker-embedding", "speaker-embedding-models",
        "70503b6968275c19ba5bdcba8da82023ee73d9d8", "wespeaker_en_voxceleb_resnet34_LM.onnx", 26_530_550L,
        "e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012")
    private val specs = listOf(segmentation, embedding)
    private fun root(context: Context) = File(context.noBackupFilesDir, "record-speakers-v1")

    private fun model(id: String, repo: String, revision: String, name: String, bytes: Long, digest: String) =
        SherpaSpeechModelCatalog.ModelSpec(id, listOf(SherpaSpeechModelCatalog.FileSpec(name, bytes, bytes, digest)),
            sourceUrl = "https://huggingface.co/csukuangfj/$repo/tree/$revision",
            repository = "csukuangfj/$repo", revision = revision)

    fun installed(context: Context) = specs.all { SherpaSpeechModelCatalog.isInstalled(root(context), it) }

    suspend fun install(context: Context, policy: RemoteDispatchPolicy, onProgress: (Float) -> Unit) {
        val revision = policy.modelAcquisitionRevision()
        val total = specs.sumOf { it.expectedBytes }
        var saved = 0L
        for (spec in specs) {
            val before = saved
            SpeechModelInstaller.install(spec, root(context), dispatchPolicy = policy, expectedRevision = revision) {
                onProgress(((before + it.downloadedBytes).toDouble() / total).coerceIn(0.0, 1.0).toFloat())
            }.getOrThrow()
            saved += spec.expectedBytes
        }
    }

    suspend fun open(context: Context): HaloLocalSpeakerLabels? = withContext(Dispatchers.Default) {
        if (!installed(context)) return@withContext null
        val work = currentCoroutineContext()
        try {
            val directory = root(context)
            specs.forEach { spec -> SherpaModelInstaller.verifyPinnedFiles(directory, spec) { work.ensureActive() } }
            HaloLocalSpeakerLabels(File(directory, segmentation.files.single().name), File(directory, embedding.files.single().name))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        catch (_: LinkageError) { null }
    }
}

internal data class HaloRecordSpeechSpan(val start: Int, val end: Int, val speaker: Char? = null)

/** Anonymous per-recording clustering; voice vectors stay in memory and are cleared at stop. */
internal class HaloLocalSpeakerLabels(segmentationModel: File, embeddingModel: File) : AutoCloseable {
    private val embeddingConfig = SpeakerEmbeddingExtractorConfig(model = embeddingModel.absolutePath, numThreads = 1)
    private val diarizer = OfflineSpeakerDiarization(config = OfflineSpeakerDiarizationConfig(
        segmentation = OfflineSpeakerSegmentationModelConfig(
            pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(model = segmentationModel.absolutePath), numThreads = 1),
        embedding = embeddingConfig,
        clustering = FastClusteringConfig(threshold = .54f, computeConfidence = true),
    ))
    private val embedder = try { SpeakerEmbeddingExtractor(config = embeddingConfig) }
        catch (failure: Throwable) { diarizer.release(); throw failure }
    private val speakers = mutableListOf<FloatArray>()

    suspend fun spans(samples: ShortArray): List<HaloRecordSpeechSpan> = withContext(Dispatchers.Default) {
        val work = currentCoroutineContext()
        val rate = diarizer.sampleRate()
        check(rate == 16_000)
        if (samples.size < rate * 3 / 4) return@withContext unknown(samples.size)
        val audio = FloatArray(maxOf(samples.size, rate * 10)) { i -> if (i < samples.size) samples[i] / 32768f else 0f }
        try {
            val regions = diarizer.processWithCallback(audio, { _, _, _ -> if (work.isActive) 0 else 1 })
                .filter { it.start.isFinite() && it.end.isFinite() && it.end > it.start && it.speaker >= 0 }
                .mapNotNull { observed ->
                    val start = (observed.start * rate).toInt().coerceIn(0, samples.size)
                    val end = (observed.end * rate).toInt().coerceIn(start, samples.size)
                    if (end - start < rate / 5) null else Region(start, end, observed.speaker,
                        observed.confidence.takeIf(Float::isFinite) ?: -2f)
                }
            work.ensureActive()
            if (regions.isEmpty() || regions.size > 16) return@withContext unknown(samples.size)
            val points = regions.flatMap { listOf(it.start, it.end) }.distinct().sorted()
            val parts = points.zipWithNext().mapNotNull { (start, end) ->
                val covered = regions.filter { it.start < end && it.end > start }
                if (covered.isEmpty()) return@mapNotNull null
                val owner = covered.singleOrNull()?.takeIf { it.confidence >= .55f }
                Region(start, end, owner?.group, owner?.confidence ?: 0f)
            }.fold(mutableListOf<Region>()) { combined, item ->
                val last = combined.lastOrNull()
                if (last != null && last.group == item.group && item.start - last.end <= rate / 5) {
                    combined[combined.lastIndex] = last.copy(end = item.end)
                } else combined.add(item)
                combined
            }
            if (parts.isEmpty() || parts.size > 6) return@withContext unknown(samples.size)
            val labels = parts.filter { it.group != null }.groupBy { it.group }.mapValues { (_, group) ->
                val best = group.maxBy { it.end - it.start }
                if (best.end - best.start < rate * 12 / 10) null else identify(audio, best, rate)
            }
            parts.map { span ->
                HaloRecordSpeechSpan(span.start, span.end, labels[span.group]).let { range ->
                    if (range.end - range.start >= rate / 4) range else range.copy(speaker = null)
                }
            }.filter { it.end - it.start >= rate / 4 }.ifEmpty { unknown(samples.size) }
        } finally { audio.fill(0f) }
    }

    private fun identify(audio: FloatArray, part: Region, rate: Int): Char? {
        val excerpt = audio.copyOfRange(part.start, part.end)
        val stream = embedder.createStream()
        var vector: FloatArray? = null
        try {
            stream.acceptWaveform(excerpt, rate)
            stream.inputFinished()
            if (!embedder.isReady(stream)) return null
            val embedding = embedder.compute(stream)
            vector = embedding
            val norm = sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
            if (embedding.isEmpty() || !norm.isFinite() || norm < .001f) return null
            for (i in embedding.indices) embedding[i] /= norm
            val scores = speakers.mapIndexed { index, known ->
                index to embedding.indices.sumOf { i -> (known[i] * embedding[i]).toDouble() }.toFloat()
            }.sortedByDescending { it.second }
            val first = scores.firstOrNull()
            val runnerUp = scores.getOrNull(1)?.second ?: -1f
            if (first != null && first.second >= .69f && first.second - runnerUp >= .09f) return ('A' + first.first)
            if ((first == null || first.second < .43f) && speakers.size < 12) {
                speakers.add(embedding.copyOf())
                return ('A' + speakers.lastIndex)
            }
            return null
        } finally {
            vector?.fill(0f)
            excerpt.fill(0f)
            stream.release()
        }
    }

    override fun close() {
        speakers.forEach { it.fill(0f) }
        speakers.clear()
        try { embedder.release() } finally { diarizer.release() }
    }

    private fun unknown(sampleCount: Int) = listOf(HaloRecordSpeechSpan(0, sampleCount))
    private data class Region(val start: Int, val end: Int, val group: Int?, val confidence: Float)
}
