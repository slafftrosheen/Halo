package com.halo.app.ui.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Ephemeral IME echo, not a draft/persistence owner. Canonical revisions remain authoritative. */
internal class HomeComposerEditBuffer(text: String, revision: Long?) {
    var value by mutableStateOf(TextFieldValue(text, TextRange(text.length)))
        private set
    private var acceptedRevision = revision
    private var editedLiteral = false

    fun edit(next: TextFieldValue) {
        if (next.text != value.text) editedLiteral = true
        value = next
    }
    fun acknowledge(state: ComposerDraftState?) {
        // A redirected startup draft or a concurrent external replacement is not our edit's acknowledgement.
        if (state != null && state.text == value.text && (acceptedRevision == null || state.revision >= acceptedRevision!!))
            acceptedRevision = state.revision
    }
    fun reconcile(text: String, revision: Long?) {
        if (revision != null && acceptedRevision != null) {
            if (revision < acceptedRevision!!) return
            // Canonical disk load/read-error recovery publishes revision zero without an edit.
            // Once the user edits, only a newer owner revision may replace the synchronous echo.
            if (revision == acceptedRevision && (revision != 0L || editedLiteral)) return
        }
        acceptedRevision = revision
        // Matching canonical text must not reset IME selection/composition on every save acknowledgement.
        if (value.text != text) value = TextFieldValue(text, TextRange(text.length))
    }
}
