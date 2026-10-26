package com.halo.app.ui.screens.notebook

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.halo.app.R
import com.halo.app.language.uiText

internal const val NOTEBOOK_BATCH_SOURCES_CHANGED =
    "Selected sources are unavailable or changed. Refresh the Library before trying again."

@Composable
internal fun notebookNoticeText(message: String): String =
    if (message == NOTEBOOK_BATCH_SOURCES_CHANGED) stringResource(R.string.interface4_library_batch_changed)
    else uiText(message)

/** Counts come from the canonical receipt; source IDs/content never become user copy. */
@Composable
internal fun notebookBatchNotice(receipt: NotebookBatchReceipt): String = when {
    receipt.failed.isNotEmpty() || receipt.unknown.isNotEmpty() -> stringResource(
        R.string.interface4_library_batch_partial, receipt.succeeded.size, receipt.failed.size, receipt.unknown.size)
    receipt.action == NotebookBatchAction.CONTEXT -> stringResource(
        R.string.interface4_library_batch_context, receipt.succeeded.size)
    receipt.action == NotebookBatchAction.RESTORE -> stringResource(
        R.string.interface4_library_batch_restored, receipt.succeeded.size)
    else -> stringResource(R.string.interface4_library_batch_updated, receipt.succeeded.size)
}
