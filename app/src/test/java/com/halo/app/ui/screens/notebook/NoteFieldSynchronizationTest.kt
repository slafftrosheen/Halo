package com.halo.app.ui.screens.notebook

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class NoteFieldSynchronizationTest {
    @Test fun delayedTypingEchoCannotRemoveNewerCharactersOrMoveCaret() {
        val typed = TextFieldValue("Basil in sunlight", TextRange(17), TextRange(9, 17))
        assertSame(typed, reconcileNoteField(typed, "Basil in sun", "Basil in sunlight"))
        assertSame(typed, reconcileNoteField(typed, typed.text, typed.text))
    }

    @Test fun currentUndoAndExternalReplacementStillReachTheEditor() {
        val typed = TextFieldValue("Basil in sunlight", TextRange(17))
        val undone = reconcileNoteField(typed, "Basil", "Basil")
        assertEquals("Basil", undone.text)
        assertEquals(TextRange(5), undone.selection)
        val applied = reconcileNoteField(undone, "A reviewed source", "A reviewed source")
        assertEquals("A reviewed source", applied.text)
        assertNull(applied.composition)
    }
}
