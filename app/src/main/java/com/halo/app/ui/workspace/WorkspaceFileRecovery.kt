package com.halo.app.ui.workspace

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.halo.app.R

internal enum class FileToolOperation(val label: Int) {
    OPEN(R.string.file_recovery_open), CREATE(R.string.file_recovery_create),
    IMPORT(R.string.file_recovery_import), EDIT(R.string.file_recovery_edit),
    EXPORT(R.string.file_recovery_export), TRANSFORM(R.string.file_recovery_transform),
    BATCH(R.string.file_recovery_batch), ANNOTATE(R.string.file_recovery_annotate),
    INSERT(R.string.file_recovery_insert), PREVIEW(R.string.file_recovery_preview),
}

internal data class FileToolFailure(val operation: FileToolOperation,
    val accessUnavailable: Boolean = false, val interrupted: Boolean = false)

@Composable
internal fun fileToolFailureText(state: WorkspaceFileToolsState): String {
    val failure = state.failure
    return when {
        state.cancelled -> stringResource(R.string.file_recovery_cancelled)
        failure?.interrupted == true -> stringResource(R.string.file_recovery_interrupted)
        else -> stringResource(failure?.operation?.label ?: R.string.file_recovery_edit) + " " +
            stringResource(if (failure?.accessUnavailable == true) R.string.file_recovery_access else R.string.file_recovery_retry)
    }
}
