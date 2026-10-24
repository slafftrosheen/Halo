package com.halo.app.data.workspace

/** Internal metadata snapshot; filesystem paths never enter assistant receipts. */
data class NotebookAttachmentState(
    val noteId: String,
    val version: String,
    val lifecycle: String,
    val writable: Boolean,
    val hasRecoveryDraft: Boolean,
    val attachments: List<NoteAttachment>,
)

data class NotebookAttachmentMutation(
    val state: NotebookAttachmentState,
    val attachmentId: String,
    val changed: Boolean,
)
