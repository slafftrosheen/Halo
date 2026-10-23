package com.halo.app.backup

import com.halo.app.data.workspace.WorkspaceTextFormat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33], application = android.app.Application::class)
class NoteBlockArchiveTest {
    @Test fun unfinishedLargeAndFutureBlocksRemainOpaqueDuringRestoreAndDuplicate() {
        for ((type, language, text) in listOf(Triple("CODE", WorkspaceTextFormat.CSV.blockLanguage, "\""),
            Triple("CODE", WorkspaceTextFormat.TXT.blockLanguage, "x".repeat(210_000)),
            Triple("FUTURE_BLOCK", "future-language", "opaque body"),
            Triple("CODE", "halo-file-batch-v1", "unfinished receipt"))) {
            val block = NoteBlockSnapshot("block", 0, type, text, false, 0, null, language, 1, 1)
            val note = NoteSnapshot("note", "exact legacy projection", 2, listOf(block))
            NoteBlockArchive.validate(emptySnapshot().copy(notes = listOf(note)))
            val copy = NoteBlockArchive.remap(note, mapOf("note" to "copy"), mapOf("block" to "new-block"))
            assertEquals("copy", copy.objectId)
            assertEquals("new-block", copy.blocks!!.single().id)
            assertEquals(text, copy.blocks.single().text)
            assertEquals(type, copy.blocks.single().type)
            assertEquals(note.body, copy.body)
        }
    }
    @Test fun duplicateBlockIdentityIsRejectedAndLegacyArchivesRemainSupported() {
        val block = NoteBlockSnapshot("same", 0, "PARAGRAPH", "raw", false, 0, null, null, 1, 1)
        assertTrue(runCatching { NoteBlockArchive.validate(emptySnapshot().copy(notes = listOf(
            NoteSnapshot("a", "raw", 1, listOf(block)), NoteSnapshot("b", "raw", 1, listOf(block))))) }.isFailure)
        NoteBlockArchive.validate(emptySnapshot().copy(notes = listOf(NoteSnapshot("old", "old body", 1))))
    }
    private fun emptySnapshot() = BrainSnapshot("snapshot", 1, emptyList(), emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), UserSettingsSnapshot(emptyMap(), emptyMap()))
}
