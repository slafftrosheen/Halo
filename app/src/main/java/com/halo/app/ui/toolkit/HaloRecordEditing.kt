package com.halo.app.ui.toolkit

import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class HaloRecordWindow(val startMs: Int, val endMs: Int) {
    val durationMs: Int get() = endMs - startMs
}

internal data class HaloRecordAudio(val file: File, val modified: Long, val sampleCount: Int) {
    val durationMs: Int get() = (sampleCount + SAMPLES_PER_MS - 1) / SAMPLES_PER_MS
    val fullWindow: HaloRecordWindow get() = HaloRecordWindow(0, durationMs)
    val bytes: Long get() = WAV_HEADER_BYTES.toLong() + sampleCount * 2L

    fun checkedWindow(startMs: Int, endMs: Int): HaloRecordWindow {
        val minimum = minOf(MIN_RECORD_TRIM_MS, durationMs)
        val end = endMs.coerceIn(minimum, durationMs)
        return HaloRecordWindow(startMs.coerceIn(0, end - minimum), end)
    }

    fun isCurrent(): Boolean = file.isFile && file.length() == bytes && file.lastModified() == modified
}

internal const val MIN_RECORD_TRIM_MS = 250
private const val WAV_HEADER_BYTES = 44
private const val PCM_RATE = 16_000
private const val SAMPLES_PER_MS = PCM_RATE / 1000

/** The editor accepts only the foreground recorder's exact mono, 16-bit local WAV. */
internal suspend fun inspectHaloRecordAudio(draft: HaloRecordDraft, filesDir: File): HaloRecordAudio = withContext(Dispatchers.IO) {
    val staging = File(filesDir, recordDraftFolder).canonicalFile
    val file = draft.file.canonicalFile
    require(file.parentFile == staging && file.name.matches(Regex("voice-[0-9]+\\.wav")))
    val modified = file.lastModified()
    RandomAccessFile(file, "r").use { reader ->
        val length = reader.length()
        require(length in WAV_HEADER_BYTES.toLong()..(WAV_HEADER_BYTES + RECORD_MAX_SECONDS * PCM_RATE * 2).toLong())
        fun fourCC(expected: String) { require(ByteArray(4).also(reader::readFully).decodeToString() == expected) }
        fun int() = Integer.reverseBytes(reader.readInt())
        fun short() = java.lang.Short.reverseBytes(reader.readShort()).toInt()
        fourCC("RIFF")
        require(int() + 8L == length)
        fourCC("WAVE")
        fourCC("fmt ")
        require(int() == 16 && short() == 1 && short() == 1)
        require(int() == PCM_RATE && int() == PCM_RATE * 2 && short() == 2 && short() == 16)
        fourCC("data")
        val size = int()
        require(size > 0 && size % 2 == 0 && WAV_HEADER_BYTES + size.toLong() == length)
        HaloRecordAudio(file, modified, size / 2).also { check(it.isCurrent()) }
    }
}

internal suspend fun readHaloRecordWaveform(audio: HaloRecordAudio): List<Float> = withContext(Dispatchers.IO) {
    check(audio.isCurrent())
    val buffer = ByteArray(4000)
    val coroutine = currentCoroutineContext()
    RandomAccessFile(audio.file, "r").use { reader ->
        List(96) { bucket ->
            coroutine.ensureActive()
            val start = audio.sampleCount.toLong() * bucket / 96
            val end = audio.sampleCount.toLong() * (bucket + 1) / 96
            val count = minOf((end - start).toInt(), buffer.size / 2)
            reader.seek(WAV_HEADER_BYTES + (start + (end - start - count) / 2) * 2)
            reader.readFully(buffer, 0, count * 2)
            var peak = 0
            repeat(count) { index ->
                val low = buffer[index * 2].toInt() and 255
                val high = buffer[index * 2 + 1].toInt() shl 8
                peak = maxOf(peak, abs(high or low))
            }
            peak / 32768f
        }.also { check(audio.isCurrent()) }
    }
}

internal suspend fun writeHaloRecordWave(audio: HaloRecordAudio, selection: HaloRecordWindow, target: OutputStream,
    allowed: () -> Unit) = withContext(Dispatchers.IO) {
    allowed()
    check(audio.isCurrent() && selection == audio.checkedWindow(selection.startMs, selection.endMs))
    val firstSample = selection.startMs * SAMPLES_PER_MS
    val lastSample = minOf(selection.endMs * SAMPLES_PER_MS, audio.sampleCount)
    var remaining = (lastSample - firstSample) * 2
    val output = DataOutputStream(target)
    writeRecordHeader(output, remaining)
    RandomAccessFile(audio.file, "r").use { input ->
        input.seek(WAV_HEADER_BYTES + firstSample * 2L)
        val buffer = ByteArray(262_144)
        while (remaining > 0) {
            allowed()
            currentCoroutineContext().ensureActive()
            val count = minOf(remaining, buffer.size)
            input.readFully(buffer, 0, count)
            output.write(buffer, 0, count)
            remaining -= count
        }
    }
    allowed()
    check(audio.isCurrent())
    output.flush()
}

internal enum class HaloRecordExportFormat(val nameWithExtension: String, val mimeType: String) {
    AUDIO("Voice recording.wav", "audio/wav"),
    TRANSCRIPT("Voice recording.txt", "text/plain"),
    SUBTITLES("Voice recording.srt", "application/x-subrip"),
}

internal data class HaloRecordExportReview(val audio: HaloRecordAudio, val window: HaloRecordWindow,
    val format: HaloRecordExportFormat, val payload: String? = null)

internal fun haloRecordCaptionLines(captions: List<HaloRecordCaption>, window: HaloRecordWindow): List<HaloRecordCaption> =
    captions.filter { it.startMs >= window.startMs && it.endMs > it.startMs && it.endMs <= window.endMs }.sortedBy { it.startMs }

internal fun haloRecordTextExport(captions: List<HaloRecordCaption>, window: HaloRecordWindow,
    format: HaloRecordExportFormat, speakerPrefix: String): String {
    require(format != HaloRecordExportFormat.AUDIO)
    val lines = haloRecordCaptionLines(captions, window)
    return buildString {
        var index = 0
        lines.forEachIndexed { position, caption ->
            val start = caption.startMs - window.startMs
            val end = minOf(caption.endMs, lines.getOrNull(position + 1)?.startMs ?: window.endMs) - window.startMs
            val words = caption.text.replace(Regex("\\s+"), " ").trim()
            if (words.isBlank() || end <= start) return@forEachIndexed
            val line = caption.speaker?.let { "$speakerPrefix $it: $words" } ?: words
            if (format == HaloRecordExportFormat.SUBTITLES) {
                append(++index).append('\n')
                append(haloSubtitleClock(start)).append(" --> ").append(haloSubtitleClock(end)).append('\n')
                append(line).append("\n\n")
            } else append('[').append(haloSubtitleClock(start)).append("] ").append(line).append('\n')
        }
    }
}

internal fun haloSubtitleClock(milliseconds: Int): String = String.format(Locale.ROOT, "%02d:%02d:%02d,%03d",
    milliseconds / 3_600_000, milliseconds / 60_000 % 60, milliseconds / 1000 % 60, milliseconds % 1000)

internal fun haloRecordControlClock(milliseconds: Int): String = String.format(Locale.ROOT, "%02d:%02d.%01d",
    milliseconds / 60_000, milliseconds / 1000 % 60, milliseconds / 100 % 10)

internal fun haloRecordTrimHandle(fraction: Float, duration: Int): Int =
    ((fraction.coerceIn(0f, duration.toFloat()) / 100).roundToInt() * 100).coerceIn(0, duration)

internal data class HaloRecordChapter(val startMs: Int, val endMs: Int)

/** Deterministic local suggestions follow pauses or elapsed time. Captions never imply topic detection. */
internal fun haloRecordChapters(captions: List<HaloRecordCaption>, window: HaloRecordWindow): List<HaloRecordChapter> {
    val lines = haloRecordCaptionLines(captions, window)
    val first = lines.firstOrNull() ?: return emptyList()
    val chapters = mutableListOf<HaloRecordChapter>()
    var start = first.startMs
    var end = first.endMs
    lines.drop(1).forEach { next ->
        if (chapters.size < 31 && (next.startMs - end >= 8_000 || next.startMs - start >= 120_000)) {
            chapters += HaloRecordChapter(start, end)
            start = next.startMs
            end = next.endMs
        } else end = maxOf(end, next.endMs)
    }
    chapters += HaloRecordChapter(start, end)
    return chapters
}
