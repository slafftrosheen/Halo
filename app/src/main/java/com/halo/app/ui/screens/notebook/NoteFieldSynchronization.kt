package com.halo.app.ui.screens.notebook

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
