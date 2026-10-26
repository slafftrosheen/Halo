package com.halo.app.ui.screens.home

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class HomeComposerEditBufferTest {
    @Test fun staleCollectedEchoCannotRemoveOrReorderAcknowledgedBurstEdits() {
        val editor = HomeComposerEditBuffer("", 0)
        val text = "QA62-display-draft"
        text.indices.forEach { index ->
            val next = text.take(index + 1)
            editor.edit(TextFieldValue(next, TextRange(next.length)))
            editor.acknowledge(ComposerDraftState(next, index + 1L))
            editor.reconcile(text.take(index), index.toLong())
            assertEquals(next, editor.value.text)
            assertEquals(TextRange(next.length), editor.value.selection)
        }
        assertEquals(text, editor.value.text)
    }
    @Test fun newerCanonicalClearOrReplacementWinsButEqualTextKeepsImeSelection() {
        val editor = HomeComposerEditBuffer("abcde", 8)
        editor.edit(TextFieldValue("abcde", TextRange(2), TextRange(1, 4)))
        editor.reconcile("abcde", 9)
        assertEquals(TextRange(2), editor.value.selection)
        assertEquals(TextRange(1, 4), editor.value.composition)
        editor.reconcile("", 10)
        assertEquals("", editor.value.text)
        editor.reconcile("restored draft", 11)
        assertEquals("restored draft", editor.value.text)
        editor.reconcile("old", 8)
        assertEquals("restored draft", editor.value.text)
    }
    @Test fun differentCanonicalTextCannotBeAcknowledgedAsTheLocalEdit() {
        val editor = HomeComposerEditBuffer("", 1)
        editor.edit(TextFieldValue("typing"))
        editor.acknowledge(ComposerDraftState("external replacement", 2))
        editor.reconcile("external replacement", 2)
        assertEquals("external replacement", editor.value.text)
    }
    @Test fun persistedRevisionZeroLoadAndReadRecoveryCanPopulateAnUntouchedEditor() {
        val editor = HomeComposerEditBuffer("", 0)
        editor.reconcile("persisted unsent draft", 0)
        assertEquals("persisted unsent draft", editor.value.text)
        val recovery = HomeComposerEditBuffer("", 0)
        recovery.reconcile("", 0)
        recovery.reconcile("recovered after storage read failure", 0)
        assertEquals("recovered after storage read failure", recovery.value.text)
    }
    @Test fun equalRevisionZeroLoadCannotOverwriteAnUnacknowledgedLocalEdit() {
        val editor = HomeComposerEditBuffer("", 0)
        editor.edit(TextFieldValue("typed now", TextRange(4)))
        editor.reconcile("old saved draft", 0)
        assertEquals("typed now", editor.value.text)
        assertEquals(TextRange(4), editor.value.selection)
        editor.acknowledge(ComposerDraftState("typed now", 1))
        editor.reconcile("stale same revision", 1)
        editor.reconcile("old saved draft", 0)
        assertEquals("typed now", editor.value.text)
        editor.reconcile("new canonical replacement", 2)
        assertEquals("new canonical replacement", editor.value.text)
    }

}
