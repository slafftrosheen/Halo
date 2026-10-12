from pathlib import Path
p=Path('app/src/main/java/com/halo/app/ui/screens/notebook/NoteEditorScreen.kt')
s=p.read_text(encoding='utf-8')
s=s.replace('onTextChange = { viewModel.updateBlock(block.id, it) },','onTextChange = { viewModel.updateBlock(block.id, it) },\n                currentText = { viewModel.uiState.value.blocks.firstOrNull { it.id == block.id }?.text },',1)
s=s.replace('onTextChange: (String) -> Unit,\n    onSelectionChange:', 'onTextChange: (String) -> Unit,\n    currentText: () -> String?,\n    onSelectionChange:',1)
s=s.replace('''        if (fieldValue.text != block.text) {
            val caret = fieldValue.selection.end.coerceIn(0, block.text.length)
            fieldValue = TextFieldValue(block.text, TextRange(caret))
        }''','''        // Lifecycle collection may deliver an older local edit after the next keystroke.
        // Only the current owner value can replace text; undo and AI edits remain authoritative.
        fieldValue = reconcileNoteField(fieldValue, block.text, currentText())''',1)
s=s.replace('''            fieldValue = TextFieldValue(
                block.text,
                TextRange(request.offset.coerceIn(0, block.text.length)),
            )''','''            val text = currentText() ?: fieldValue.text
            fieldValue = TextFieldValue(text, TextRange(request.offset.coerceIn(0, text.length)))''',1)
p.write_text(s,encoding='utf-8')
p=Path('app/src/main/java/com/halo/app/ui/screens/notebook/NoteFieldSynchronization.kt')
p.write_text('''package com.halo.app.ui.screens.notebook

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Ignore a delayed render echo without blocking current owner replacements such as undo. */
internal fun reconcileNoteField(
    field: TextFieldValue,
    renderedText: String,
    ownerText: String?,
): TextFieldValue {
    if (renderedText != ownerText || field.text == renderedText) return field
    return TextFieldValue(renderedText, TextRange(field.selection.end.coerceIn(0, renderedText.length)))
}
''',encoding='utf-8')
p=Path('app/src/main/java/com/halo/app/ui/screens/notebook/NotebookScreen.kt')
s=p.read_text(encoding='utf-8')
s=s.replace('''                Text(uiText("Notes are documents. Canvas connects them. Inbox holds new captures. Memory stores information you approve."),''','''                if (showTools || (state.isLoaded && visibleItems.isEmpty() && state.query.isBlank())) Text(uiText("Notes are documents. Canvas connects them. Inbox holds new captures. Memory stores information you approve."),''',1)
s=s.replace('''                Text(uiText("Saved on this device. Adding context does not change your sharing permissions."),''','''                if (showTools || (state.isLoaded && visibleItems.isEmpty() && state.query.isBlank())) Text(uiText("Saved on this device. Adding context does not change your sharing permissions."),''',1)
p.write_text(s,encoding='utf-8')
p=Path('app/src/test/java/com/halo/app/ui/screens/notebook/NoteFieldSynchronizationTest.kt')
p.parent.mkdir(parents=True,exist_ok=True)
p.write_text('''package com.halo.app.ui.screens.notebook

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
''',encoding='utf-8')
