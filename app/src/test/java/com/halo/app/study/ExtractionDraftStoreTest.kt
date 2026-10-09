package com.halo.app.study

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class ExtractionDraftStoreTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun restoredDraftRetainsEditedTextOrderVersionsAndSavedNote() = runBlocking {
        val file=File(folder.root,"review.json")
        val draft=ExtractionDraft(listOf(ExtractionPart("a","sha-a","Photo","Image","OCR","Ābols 🧠"),
            ExtractionPart("b","sha-b","Book","Page 2","OCR","Edited words","Partial coverage")),"note")
        ExtractionDraftStore(file).save(draft)
        assertEquals(draft,ExtractionDraftStore(file).load())
        ExtractionDraftStore(file).save(ExtractionDraft(emptyList(),null))
        assertNull(ExtractionDraftStore(file).load())
    }
    @Test fun oversizedOrUnknownDraftCannotBeRestored() = runBlocking {
        val file=File(folder.root,"review.json")
        file.writeText("{\"version\":999,\"parts\":[]}")
        assertTrue(runCatching { ExtractionDraftStore(file).load() }.isFailure)
        file.writeBytes(ByteArray(1024*1024+1))
        assertTrue(runCatching { ExtractionDraftStore(file).load() }.isFailure)
    }
    @Test fun rejectedReplacementLeavesTheExistingDraftIntact() = runBlocking {
        val file=File(folder.root,"review.json"); val store=ExtractionDraftStore(file)
        val good=ExtractionDraft(listOf(ExtractionPart("source","1","Photo","Image","OCR","Keep this")),null)
        store.save(good)
        val oversized=good.copy(parts=listOf(good.parts.single().copy(text="x".repeat(100001))))
        assertTrue(runCatching { store.save(oversized) }.isFailure)
        assertEquals(good,store.load())
    }
}
