package com.halo.app.ui.toolkit

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HaloRecordEditingTest {
    @Test fun selectedPcmSamplesAreCopiedWithoutEditingTheOriginal() = runBlocking {
        val root = Files.createTempDirectory("halo-record-editing").toFile()
        val staging = File(root, recordDraftFolder).apply { check(mkdirs()) }
        val file = File(staging, "voice-1.wav")
        try {
            DataOutputStream(file.outputStream()).use { output ->
                writeRecordHeader(output, 32_000)
                repeat(16_000) { output.writeShort(java.lang.Short.reverseBytes(it.toShort()).toInt()) }
            }
            val original = file.readBytes()
            val audio = inspectHaloRecordAudio(HaloRecordDraft(file, 1), root)
            assertEquals(1_000, audio.durationMs)
            assertEquals(HaloRecordWindow(0, 250), audio.checkedWindow(500, 40))
            val copy = ByteArrayOutputStream()
            writeHaloRecordWave(audio, HaloRecordWindow(250, 750), copy) { }
            val bytes = copy.toByteArray()
            assertEquals(44 + 16_000, bytes.size)
            val samples = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(4_000, samples.getShort(44).toInt())
            assertEquals(11_999, samples.getShort(bytes.size - 2).toInt())
            assertArrayEquals(original, file.readBytes())
        } finally {
            file.delete()
            staging.delete()
            staging.parentFile?.delete()
            staging.parentFile?.parentFile?.delete()
            root.delete()
        }
    }

    @Test fun captionsOutsideTheTrimAreOmittedAndKeptSubtitlesAreRebased() {
        val captions = listOf(
            HaloRecordCaption(1_000, 2_600, "Outside at the start"),
            HaloRecordCaption(2_750, 3_900, "Keep this", 'A'),
            HaloRecordCaption(5_000, 6_500, "Outside at the end"),
        )
        val range = HaloRecordWindow(2_500, 6_000)
        assertEquals("1\n00:00:00,250 --> 00:00:01,400\nSpeaker A: Keep this\n\n",
            haloRecordTextExport(captions, range, HaloRecordExportFormat.SUBTITLES, "Speaker"))
        val text = haloRecordTextExport(captions, range, HaloRecordExportFormat.TRANSCRIPT, "Speaker")
        assertEquals("[00:00:00,250] Speaker A: Keep this\n", text)
        assertFalse(text.contains("Outside"))
    }

    @Test fun chapterSuggestionsUseCueBoundsAndSplitOnPauses() {
        val captions = listOf(HaloRecordCaption(1_200, 2_500, "First"), HaloRecordCaption(3_000, 4_900, "Second"),
            HaloRecordCaption(14_000, 16_800, "Third"))
        assertEquals(listOf(HaloRecordChapter(1_200, 4_900), HaloRecordChapter(14_000, 16_800)),
            haloRecordChapters(captions, HaloRecordWindow(0, 20_000)))
    }
}
